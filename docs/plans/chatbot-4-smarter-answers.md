# Chatbot plan 4: smarter answers

**Status:** plan only, no code changed. Written 2026-10-19 against branch `claude/admin-api-auth` (HEAD `0199e85`).
**Scope:** the three agreed "Planned evolution" items from CLAUDE.md:

- **A.** Workflow awareness in the chatbot's system prompt (item 1)
- **B.** Shop context passed into the chat tools (item 3)
- **C.** Long workflows run from chat in the background, with approval-gated workflows allowed (item 4)

Sibling plans cover reliability, speed and feedback. Merge notes for the parent session are in §6.

---

## 0. Read this first: questions and risks for Nic

### Questions only you can answer

1. **Is any workflow linked to the chatbot today?** (Settings → Automated Workflows, i.e. `chatbot_configs.linked_workflow_ids`.) If none is linked, A and C change nothing for customers until one is. That makes them safe to ship, but they can't be tested on the live site until you link a workflow you're happy for customers to trigger.
2. **Which workflows do you want customers to run from chat, and are their outputs safe for customers to see?** Under C, the customer sees the workflow's final step output. Workflows built for staff could expose internal data such as cost prices, supplier names or stock notes. Before linking a workflow, you decide whether its output is customer-safe.
3. **Does any live workflow have an APPROVAL step, and does anyone rely on how it behaves today?** I found that approval steps don't actually pause anything (see risk R1). Fixing that changes how those workflows behave in the admin and on schedules, not just in chat.
4. **When a customer's request is waiting for approval, how should staff find out?** Today it only shows up in the admin Approval Queue page. Nobody gets an email or notification. If nobody checks that page, a customer could wait for days.
5. **Does the live DB have an active `system_prompts` row (type PRODUCT_SEARCH) for hearnshobbies?** If so, production uses that prompt, and the built-in "specialist agents" list is never added (see risk R4). That affects where the A text has to go. One read-only SQL query answers this: `SELECT id, prompt_name, version, is_active FROM system_prompts WHERE prompt_type='PRODUCT_SEARCH';`
6. **Is agent 23 (Product Enrichment) in `linked_agent_ids`, or used as a step in any workflow?** It shouldn't be either. See risk R3.

**Nic's answers (2026-10-19):** 1–2 not known: check the live config read-only before building A/C. 3: he didn't know approval steps existed; he thinks a review was done by an *agent* step, so it's probably safe to assume nobody relies on today's approval behaviour (confirm in the live DB). 4: **keep C4 as a later evolution**: notify Angie directly in Slack, collect the customer's details, and turn it into lead generation (see §4.7). 5: not known: check read-only. 6: confirmed, agent 23 is the HR app's agent; it must stay untouched.

### Risks found while planning (all in today's code, not caused by this plan)

- **R1. Approval steps don't pause workflows.** `WorkflowOrchestratorService.executeApprovalStep` creates the approval request, sets the run to `PAUSED`, returns `{"status":"PENDING"}`, and then the step loop **carries straight on to the next step**. When the run finishes it's marked `COMPLETED`, which overwrites the `PAUSED` status. On approval, `resumeWorkflowAfterApproval` only changes the status to `RUNNING` and runs no steps. So any step "after approval" today runs **before** anyone approves. Item C's "approval-gated workflows from chat" can't be built until this is fixed (stage C1). The fix also changes admin and scheduled workflows, which is why question 3 matters.
- **R2. The shop context is a shared field, which is a race condition.** `ShopifyChatController` calls `chatAgentService.setShopContext(shop)` / `setAnthropicModel(...)` / `setTemperature(...)` on a **singleton**. That means two simultaneous chats overwrite each other's settings. It's harmless today because there's only one shop, but **item B must not build on that field**. The plan passes the shop through the request's own Reactor context instead. (I'm not fixing the existing race here. It's listed as a follow-up in §7.)
- **R3. `delegate_to_agent` doesn't check the linked-agents list.** `AgentDelegationToolHandler` looks up **any** agent by name. Only `linked_agent_ids` gets advertised, but a confused model or a prompt-injection attempt ("ask the Product Enrichment agent to…") could reach agent 23 or any other DB agent from the public storefront. The workflow handler already has this gate. Stage B adds the same one for agents. It's a two-line check, and it's how this plan protects agent 23.
- **R4. The two prompt paths have drifted apart.** `buildSystemPrompt` either uses a DB `system_prompts` row and appends four helper blocks, or builds the whole prompt from `ChatbotConfig`. The specialist-agents list only exists in the second path. Any new prompt text (A) has to go in **both** paths, or it silently disappears whenever a DB prompt is active.
- **R5. A redeploy kills runs in progress.** A workflow running in the background (C) dies when Railway restarts the app, and its row stays `RUNNING` forever. Stage C2 includes a startup sweeper that marks those runs failed. Approval-paused runs survive restarts because, after C1, they are resumed from the DB.
- **R6. DB connection pool.** The September–October Hikari outage came from long requests holding connections. Background runs (C) must not hold a DB connection while waiting on Claude. They also need a hard concurrency cap (pool max is 10), so a flood of chat-triggered runs can't starve the storefront chat. The cap is part of C2.
- **R7. Cost and abuse.** C makes it possible for a public, anonymous visitor to start multi-step AI workflows. Limits are part of the plan: linked workflows only, at most one active run per chat session, a global cap, and a run expiry.

---

## 1. Ground rules this plan follows

- **Storefront chat must never break.** Every stage is off by default, or has no effect until you link something in Settings. Every stage can be rolled back with a config flag, without a redeploy.
- **Zero-touch on `ChatAgentService`.** In July 2026 you decided to leave `ChatAgentService` alone. This plan keeps it that way almost entirely:
  - **B: zero lines** in `ChatAgentService`. The shop rides on the Reactor context, which the controller sets once. Tools read it directly, so the chat loop never knows.
  - **C: zero lines** in `ChatAgentService`. The "workflow started" signal to the widget goes through a per-request channel that the controller owns, not through the chat loop.
  - **A: two one-line call sites** in `buildSystemPrompt` (one per prompt path, see R4). They call a new, separate class that returns an **empty string** unless a workflow is linked. With nothing linked, the prompt is byte-for-byte identical to today, and a unit test proves it. A truly zero-touch alternative is in §2.4.
- **Agent 23 is never on the changed path.** The HR app calls `POST /api/agents/23/execute`, which goes `AgentController → AgentExecutionService.executeAgent`. Nothing in this plan edits `AgentController` or `AgentExecutionService`, or changes what `executeAgent` receives. B's shop note is added only in the **chat** delegation handler. R3's gate actively blocks chat from reaching agent 23.
- **Staged deploys, per your 3-stage rule:** bug fixes first, then migrations alone, then features. The order is in §5.
- **New migrations take the next free number.** V022 is the latest today. Sibling plans may also want V023, so the parent session should assign numbers when merging.

---

## 2. Item A: workflow awareness in the system prompt

### 2.1 What changes for the customer

Today Claude only finds out about linked workflows from the `delegate_to_workflow` tool description, which is one dense sentence. In practice the model rarely reaches for it, and when it does, it doesn't explain the wait to the customer. After A, the system prompt gets a short section that says **when** to use a workflow, **what to tell the customer** first, and **what not to do**.

### 2.2 Prompt text (draft; you can edit it before build)

Added only when at least one active workflow is linked:

```
=== AUTOMATED WORKFLOWS ===
Some requests are best handled by one of the store's automated workflows (via the delegate_to_workflow tool):
- <Workflow name>: <workflow description>
- ...
When to use one:
- Only when the customer's request clearly matches a workflow above. For normal product questions, search the catalogue yourself; it is faster.
- Never run a workflow just to answer a question you can answer with your other tools.
How to use one:
- Use the exact workflow name. Put the customer's full request, with any details they gave, in "task".
- Before calling it, tell the customer in one short sentence what you are doing ("I'll run our <name> check for you, this can take a minute").
- When it returns, summarise the result in plain, friendly language. Do not paste raw data.
- If it fails or times out, apologise briefly, offer what you can do directly, and suggest contacting the store if needed.
- Never promise an outcome the workflow has not confirmed.
```

Stage C3 adds these extra lines (only when the background-runs flag is on):

```
- Some workflows run in the background. If the tool says the workflow has "started", tell the customer it is underway and that the result will appear in this chat; do not wait or call it again.
- If a workflow needs a staff member's approval, say so honestly: a team member will review it, and the update will appear here (or they can check back later).
```

### 2.3 Files

| File | Change |
|---|---|
| **new** `service/chat/WorkflowPromptSection.java` | `@Component` with `String build(ChatbotConfig config)`. Returns `""` when `linkedWorkflowIds` is empty. Otherwise it lists the active, chat-runnable linked workflows using the same filter as the tool description (active, and no approval step unless C3 is on). The whole body sits in `try/catch` and returns `""` on any error, so it can never break prompt building. Description length is capped at about 300 characters per workflow. |
| `service/ChatAgentService.java` | Two edits: (a) inject `WorkflowPromptSection`, through the constructor or field injection, whichever reads least invasively; (b) append `+ workflowPromptSection.build(config)` in the DB-prompt return (line ~779) and before `buildBatchSearchGuidance()` in `buildSystemPromptFromConfig` (line ~1102). Nothing else. |
| `handler/tool/DelegateToWorkflowChatToolHandler.java` | Keep the tool description, so it stays a backup if the prompt section is ever disabled, but shorten it to point at the prompt section. Optional. |
| `application.yml` | `chatbot.workflow-prompt.enabled: ${CHATBOT_WORKFLOW_PROMPT_ENABLED:false}`. When false, `build()` returns `""`. **This is the rollback switch.** |
| `service/ChatAgentService.java` `statusMessageFor` | **Not touched.** A nicer "Running our X workflow…" status line would be a third edit, so it's left out on purpose. The generic status line still shows. |

### 2.4 The truly zero-touch alternative, and why I don't recommend it

You could put the text in `chatbot_configs.custom_instructions` (admin UI, no code). The config path appends it. But it would be missing whenever a DB `system_prompts` row is active (R4), it would go stale when workflows are renamed, and you'd have to maintain it by hand. The two-call-site version updates itself and works in both paths. **Recommendation:** go with the two call sites. If you'd rather keep ChatAgentService frozen, the custom-instructions route is a reasonable stopgap.

### 2.5 Risk to the live chat

- **Low.** With the flag off, or nothing linked, the prompt is unchanged (proven by test). With it on, the prompt grows by about 150–300 tokens, which costs roughly a fraction of a cent per conversation (the system prompt is cached). One behaviour risk: the model may start calling workflows more often. The prompt explicitly tells it not to, and the post-deploy check below watches for it.
- The prompt cache resets once when the text changes. That's harmless.

### 2.6 Testing without a local DB

- Unit test `WorkflowPromptSectionTest` (Mockito, no Spring context): empty list → `""`; flag off → `""`; inactive workflow omitted; approval workflow omitted; repository throws → `""`.
- **Golden test** `ChatAgentServicePromptGoldenTest`: build `ChatAgentService` with mocks and a fixed `ChatbotConfig` that has no linked workflows. Assert that `getGeneratedSystemPrompt()` is identical to a snapshot captured **before** the change. This is the proof that nothing changed for customers.
- `mvn -q test` and `mvn -q compile`.

### 2.7 Post-deploy check

1. Deploy with the flag **off**. Run one storefront chat ("do you have Tamiya paints?") and check you get a normal answer.
2. Admin → Prompt preview (`getGeneratedSystemPrompt`): no AUTOMATED WORKFLOWS section.
3. Set `CHATBOT_WORKFLOW_PROMPT_ENABLED=true` on Railway (restart). The preview now shows the section (if a workflow is linked). Run one chat that should trigger the workflow and one that shouldn't. Check the Railway logs for `=== DELEGATING TO WORKFLOW ===` only on the first.
4. For a day, check Chat Logs for unexpected workflow calls.

### 2.8 Rollback

Set the env flag to false and restart. That takes about a minute, with no redeploy. Full revert: revert the commit (two lines plus the new class).

### 2.9 Size

**Small:** ~80 lines of new class, 3 lines in ChatAgentService, 2 tests. About half a day including review.

---

## 3. Item B: shop context in chat tool inputs

### 3.1 What changes

Workflows and agents started from chat find out which shop the customer is on:
- Workflows get extra fields on their trigger data: `trigger.shop` (domain, e.g. `hearnshobbies.myshopify.com`), `trigger.shopId`, `trigger.sessionId`, `trigger.source = "storefront_chat"`. Existing `${trigger.message}` / `${trigger.task}` mappings keep working, because the new fields are additions. Workflow authors can use `${trigger.shop}` from now on.
- Delegated agents get one extra line at the end of the task text, after the product-first instruction: `Store: hearnshobbies.myshopify.com (customer is on the storefront chat).` A line in the text is needed because `AgentExecutionService` only reads `input.task` and ignores other fields (line ~215). This is added in the chat handler only, so agent 23's HR-app calls are untouched.
- **R3 fix:** `AgentDelegationToolHandler` refuses agents that aren't in `linkedAgentIds`. That's the same rule workflows already follow.

### 3.2 How the shop travels, with no ChatAgentService change

The chat loop is one reactive chain. Tool calls run inside it (`flatMap` / `Mono.zip` / `concatWith(Flux.defer(...))`), and **Reactor context** flows through all of those automatically. So:

1. `ShopifyChatController` (both `/message` and `/message/stream`) adds `.contextWrite(ChatRequestContext.of(shopConfig, request.getSessionId()))` to the chain it already returns. That's one line per endpoint.
2. **new** `service/chat/ChatRequestContext.java`: a small immutable holder (shop domain, shop id, session id) with static `of(...)` and `Mono<Optional<ChatRequestContext>> current()` helpers (`Mono.deferContextual`).
3. `DelegateToWorkflowChatToolHandler.execute` and `DelegateToAgentChatToolHandler.execute` wrap their call in `ChatRequestContext.current().flatMap(ctx -> ...)` and add the shop to the input they pass down. **If no context is present** (admin chat page, persona chats, tests), they behave exactly as today.

Other chat tools (search, inventory, orders) don't need the shop today, because they use the single global Shopify client. They're left alone. The helper is there for later.

### 3.3 Files

| File | Change |
|---|---|
| **new** `service/chat/ChatRequestContext.java` | holder and helpers (~50 lines) |
| `controller/ShopifyChatController.java` | `.contextWrite(...)` on both endpoints (2 lines) |
| `handler/tool/DelegateToWorkflowChatToolHandler.java` | read the context, copy `shop/shopId/sessionId` into the input under reserved keys `_shop`, `_shopId`, `_sessionId` (not in the schema Claude sees, so Claude can't fake them) |
| `service/tool/WorkflowDelegationToolHandler.java` | copy the `_shop*` keys from the input into `triggerData` as `shop`, `shopId`, `sessionId`; `source` becomes `"storefront_chat"` when a shop is present, else stays `"chatbot"` |
| `handler/tool/DelegateToAgentChatToolHandler.java` | same `_shop` pass-through |
| `service/tool/AgentDelegationToolHandler.java` | (a) **linked-agent gate** (R3): `chatbotConfigService.getConfig().getLinkedAgentIds().contains(agent.getId())` or refuse with a friendly error; (b) add the store line to `enrichedTask` when `_shop` is present |
| `application.yml` | `chatbot.shop-context.enabled: ${CHATBOT_SHOP_CONTEXT_ENABLED:true}` controls the pass-through. Separately, `chatbot.delegation.require-linked-agent: ${CHATBOT_REQUIRE_LINKED_AGENT:true}` is a rollback switch for the R3 gate, in case a live chat relies on an unlinked agent. |

`ChatAgentService`, `ChatToolRegistry`, `ChatToolHandler`, `AgentExecutionService`: **untouched**.

### 3.4 Risk to the live chat

- **The R3 gate is the one real behaviour change.** If the live custom instructions tell the bot to delegate to an agent that **isn't** in `linked_agent_ids`, that delegation starts returning "not available" and the bot answers on its own. Before deploying, compare `linked_agent_ids` against the agent names mentioned in `custom_instructions` (a read-only check). The flag lets you turn the gate off without a redeploy.
- `contextWrite` on the controller chain is very low risk: it only adds data. If a tool ever runs outside the chain, the context is simply missing and the tool falls back to today's behaviour.
- `/message/stream` merges the heartbeat after `content`. `contextWrite` goes at the very end of the returned Flux so that both see it.

### 3.5 Testing without a local DB

- `ChatRequestContextTest`: context written → `current()` returns it; none → empty.
- `WorkflowDelegationToolHandlerTest` (mock repository/orchestrator): with the context, `triggerData` contains `shop`, `message`, `task`; without it, `triggerData` equals today's exactly. Capture it with a Mockito `ArgumentCaptor`.
- `AgentDelegationToolHandlerTest`: unlinked agent refused; linked agent's task ends with the store line; **agent id 23 not linked → refused**; with the flag off → allowed (today's behaviour).
- A propagation test that runs a `Mono.zip` of two tool monos inside a `flatMap` with `contextWrite` outside, to prove the context reaches the tools the way `ChatAgentService` structures them. This needs `io.projectreactor:reactor-test` (test scope only) or plain `.block()`.

### 3.6 Post-deploy check

1. One normal storefront chat (search question): unchanged.
2. If an agent is linked: ask something that triggers it. Railway logs show the delegation, and the agent's task (logged in `agent_executions`) ends with the store line.
3. If a workflow is linked: trigger it. The newest `workflow_executions.trigger_data_json` contains `"shop":"hearnshobbies.myshopify.com"`.
4. HR app: run one product enrichment. It must still work. (It doesn't go through this code, but check anyway.)

### 3.7 Rollback

Set `CHATBOT_SHOP_CONTEXT_ENABLED=false` and/or `CHATBOT_REQUIRE_LINKED_AGENT=false`, then restart. Full revert: one commit.

### 3.8 Size

**Small:** about 150 lines plus tests, half a day to a day. **Ship it first.** It's the smallest change, it protects agent 23 (R3), and A and C build on it.

---

## 4. Item C: long workflows run in the background from chat (with approvals)

### 4.1 What the customer sees

> Customer: "Can you put together a full starter kit for 1/35 armour, with paints and tools, and check what's in stock?"
> Bot: "I've started our Starter-Kit Builder for you. It takes a minute or two, and the result will appear right here." *(a small "Working on it…" card appears under the message)*
> …the card updates by itself… → shows the result (products, Add all to cart link).

With an approval step:
> card: "A team member is reviewing this. We'll show the update here. You can close the chat and check back later."
> If the customer reopens the chat in the same browser session, the card picks the status back up.

### 4.2 Why it has to be staged

C sits on top of R1 (approvals don't pause). Bolting background runs onto that bug would let a customer's run carry on past the approval. So C is four stages, each deployable and reversible on its own:

| Stage | What | Touches storefront? |
|---|---|---|
| **C1** | Make approval steps **really pause and resume** (orchestrator fix) | No. Admin/scheduled workflows only, behind a flag |
| **C2** | Background start, run tokens, status endpoint, sweeper, caps (backend only) | Backend only; the widget ignores the new data |
| **C3** | Widget shows the card and polls; prompt lines from §2.2 | Yes: widget JS |
| **C4** | Allow approval-gated workflows from chat | Config flag |

### 4.3 C1: real pause and resume in `WorkflowOrchestratorService`

**Change**
- `executeStepsSequentially`: when an `APPROVAL` step returns, **stop** the loop. Save the context so far into `workflow_executions.context_data_json`, save the index of the next step (new column), and finish with status `PAUSED` instead of continuing. `executeWorkflow`'s `doOnSuccess` must not overwrite `PAUSED` with `COMPLETED`. It needs to check a "paused" marker on the result.
- `resumeWorkflowAfterApproval`: load the execution with its workflow, steps and agents (**fetch-join**: `findByIdWithStepsAndAgents`, as CLAUDE.md requires), rebuild the context from `context_data_json`, add `approval: {approvedBy, comments}` to the context, and continue `executeStepsSequentially` from the saved index **in the background** (`subscribeOn(boundedElastic)`). This must not hold the admin "approve" request open, and it must not run inside a `@Transactional` that holds a DB connection for the whole run (R6).
- `ApprovalService.approveRequest` is `@Transactional` and calls resume. The resume must only *schedule* the run, so the transaction commits fast.
- Rejection and timeout already mark the run `FAILED`. No change.

**Files:** `service/agent/WorkflowOrchestratorService.java`, `service/agent/ApprovalService.java` (small), `model/agent/WorkflowExecution.java` (+`resumeStepIndex`), **migration `V0xx__add_workflow_resume_step.sql`** (`ALTER TABLE workflow_executions ADD COLUMN resume_step_index INTEGER NULL;`, nullable and additive).

**Flag:** `workflow.approval.real-pause: ${WORKFLOW_APPROVAL_REAL_PAUSE:false}`. When off, today's behaviour stays exactly as is.

**Parallel steps:** an APPROVAL step **inside** a PARALLEL group isn't supported. Refuse that at execution time with a clear error, rather than half-pausing.

**Risk:** this changes behaviour for every workflow that has an approval step (question 3). The storefront chat isn't affected: chat refuses approval workflows until C4. **Agent 23 isn't affected** unless it's a workflow step after an approval step (question 6).

**Test without DB:** an orchestrator unit test with mocked repositories and a mocked `AgentExecutionService`. Workflow [agent A, approval, agent B]: A runs, B **does not** run, status is `PAUSED`, and the saved index is 2. Call resume: B runs and the status is `COMPLETED`, with A's output still in the context. Plus: rejection → `FAILED` and B never runs; flag off → today's behaviour (B runs immediately). Existing `WorkflowControllerPublicTest` still passes.

**Post-deploy:** with the flag on, run a throwaway admin test workflow ("Test approval": echo agent, approval, echo agent). Check that it shows `PAUSED` in Workflow Executions, then approve it in the Approval Queue. It should finish `COMPLETED` with both outputs. Reject a second run: `FAILED`.

**Rollback:** flag off. The migration column is harmless when unused.

**Size:** **Medium:** ~150 lines plus tests, 1–1.5 days. This is the trickiest piece of logic in the plan.

### 4.4 C2: background start, run tokens and status endpoint (backend only)

**New table** (migration `V0xx__create_chat_workflow_runs.sql`), kept separate so the core `workflow_executions` table isn't altered further:

```sql
CREATE TABLE chat_workflow_runs (
  id                     BIGSERIAL PRIMARY KEY,
  public_token           VARCHAR(64) NOT NULL UNIQUE,   -- random, unguessable; what the widget holds
  workflow_execution_id  BIGINT REFERENCES workflow_executions(id),
  workflow_name          VARCHAR(255) NOT NULL,
  shop_domain            VARCHAR(255),
  session_id             VARCHAR(255),
  customer_result        TEXT,                          -- customer-safe text, filled on completion
  created_at             TIMESTAMP NOT NULL DEFAULT NOW(),
  expires_at             TIMESTAMP NOT NULL
);
CREATE INDEX idx_chat_workflow_runs_session ON chat_workflow_runs(session_id);
```

**Why a random token and not the execution id:** `/api/executions/{id}` is admin-only (AdminAuthFilter), and the ids count up 1, 2, 3, so anyone could guess them. The public status endpoint must accept only an unguessable token, and must return only a customer-safe status and text, **never** the raw workflow context.

**Flow**
1. `WorkflowDelegationToolHandler`: when `chatbot.workflow-async.enabled` is on **and** the workflow is marked to run in the background (see "which workflows" below), call a new orchestrator method `startWorkflowDetached(workflowId, triggerData)`. It saves the execution row, returns its id **immediately**, and runs the steps on `boundedElastic`, detached from the chat request. Then insert the `chat_workflow_runs` row and return this to Claude:
   `{"success":true,"status":"started","run_token":"…","message":"Workflow started; the result will appear in the customer's chat."}`
   Otherwise it does today's synchronous 90-second run.
2. **Signal to the widget without touching ChatAgentService:** the controller puts a per-request `ChatSideChannel` (a small list or `Sinks.Many`) into the Reactor context, next to B's shop context. The handler pushes `{type:"workflow_run", token, workflowName}` into it.
   - `/message/stream`: the controller merges `sideChannel.asFlux()` into the SSE flux as `event: workflow_run`, the same way the heartbeat is merged today. The tool runs before the final answer streams, so the event arrives before `done`. **The current widget ignores unknown events** (`ai-search-client.js` only handles token/status/done/error), so this is safe to deploy before the widget change.
   - `/message` (blocking fallback): the controller adds `"workflowRuns":[…]` to the JSON response map. The widget ignores it until C3.
3. **Status endpoint** (new): `GET /api/shopify/chat/workflow-runs/{token}?shop=…` in a new `ShopifyChatWorkflowRunController`. It sits under `/api/shopify/chat/`, which is already on the AdminAuthFilter allowlist and outside OSIV, so no filter change is needed. Copy the CORS settings from `ShopifyChatController`. Response:
   `{"status":"RUNNING"|"WAITING_APPROVAL"|"COMPLETED"|"FAILED"|"EXPIRED","message":"<customer-safe text>","updatedAt":"…"}`
   It returns 404 if the token is unknown, or the `shop` doesn't match the row.
4. **Customer-safe result.** When the run completes, take the **last step's output `text`** (agents return `{text: …}`), cap it at about 4,000 characters, and store it in `customer_result`. No raw context, no earlier steps. Failures show a fixed friendly message, never the error text. (Question 2: you decide which workflows are safe to link.)
5. **Sweeper** (new `@Scheduled`, every 5 minutes, plus once at startup): `RUNNING` chat runs older than 15 minutes with no progress → mark the execution `FAILED` ("interrupted by restart"). Rows past `expires_at` (default 24 hours, or 7 days while waiting on approval) report `EXPIRED`. This handles R5.
6. **Caps (R6, R7):** at most 1 active run per `session_id` (a second start returns "already working on one"). At most `chatbot.workflow-async.max-concurrent` (default 3) running chat runs in total, beyond which the tool returns "busy, try again shortly" and Claude answers without the workflow. This keeps chat-triggered runs well under the pool size of 10.

**Which workflows run in the background:** simplest is a new optional column `workflows.run_async_from_chat BOOLEAN DEFAULT FALSE` (in the same migration) plus a checkbox in Settings → Automated Workflows. Alternative with no schema change: one config list `chatbot_configs.async_workflow_ids`. **Recommendation:** the column, since it lives with the workflow. Either way **nothing runs in the background until you tick a box**.

**Files:** `service/tool/WorkflowDelegationToolHandler.java`, `service/agent/WorkflowOrchestratorService.java` (+`startWorkflowDetached`, reusing the existing private step methods; `executeWorkflow` unchanged), **new** `model/ChatWorkflowRun.java`, `repository/ChatWorkflowRunRepository.java`, `service/chat/ChatWorkflowRunService.java` (start, record result, status, caps), `service/chat/ChatSideChannel.java`, `controller/ShopifyChatWorkflowRunController.java`, `scheduler/ChatWorkflowRunSweeper.java`, `controller/ShopifyChatController.java` (add the side channel to the context, merge it into SSE, add it to the blocking response: about 8 lines), two migrations, `application.yml` flags (`chatbot.workflow-async.enabled: false`, `max-concurrent: 3`, `ttl-hours: 24`), and optionally `frontend/src/pages/Settings.jsx` for the checkbox.

**Risk to the live chat:** it's off by default, and nothing is marked async. The only always-on change is the controller merging an extra empty Flux into the SSE stream and adding one key to the context. That's the same pattern as the heartbeat, and a unit test confirms the stream is unchanged when the side channel is empty. **This controller edit is the storefront-path change you need to sign off on.**

**Test without DB:**
- `ChatWorkflowRunServiceTest` (mocks): token is random and 32+ characters; caps enforced; status mapping (execution RUNNING → RUNNING; PAUSED with a pending approval → WAITING_APPROVAL; COMPLETED → COMPLETED with `customer_result`; expired → EXPIRED); a shop mismatch → not found.
- `ShopifyChatControllerSseTest`: call `streamMessage` with mocked `ChatAgentService.streamChat` returning token/done events. With an empty side channel the output frames are identical to today's. With one event pushed before done, the frame order is token… → workflow_run → done.
- `WorkflowDelegationToolHandlerTest`: flag off → synchronous path (today's); flag on and workflow async → returns `started` with the token, without waiting on the mocked orchestrator's slow Mono.
- `ShopifyChatWorkflowRunController` test: unknown token → 404; never returns `context_data_json`.
- `AdminAuthFilterTest`: add a case that `/api/shopify/chat/workflow-runs/abc` is public under `enforce`.

**Post-deploy:** (1) a normal storefront chat, streamed and blocking, is unchanged. (2) Turn on the flag, mark one throwaway test workflow async and link it, then trigger it from the storefront. In the browser devtools Network tab, the stream shows `event: workflow_run`. `curl` the status URL until it reports `COMPLETED`. (3) Redeploy mid-run, and within 5 minutes the run reports `FAILED` rather than hanging. (4) Check Railway logs for Hikari warnings.

**Rollback:** `CHATBOT_WORKFLOW_ASYNC_ENABLED=false` → back to the synchronous 90-second runs (today's behaviour). Runs already started finish or get swept. The migrations are additive and can stay.

**Size:** **Medium-large:** ~500 lines plus tests, 2–3 days.

### 4.5 C3: storefront widget polling (`shopify-theme-extension/assets/ai-search-client.js`)

**Change**
- `streamMessage`: handle `parsed.event === 'workflow_run'` → `onWorkflowRun(payload)`. `sendMessage` (blocking): read `data.workflowRuns`.
- UI: below the assistant bubble, add a small status card ("Working on it…", spinner) with a `data-run-token` attribute. Poll the status endpoint every 4 seconds for 2 minutes, then every 15 seconds up to 15 minutes, **only while the chat modal is open** (pause polling when the tab is hidden). Then stop with "Still working. Reopen the chat later to see the result."
- On `COMPLETED`, render `message` through the existing markdown/cart-link renderer, so cart buttons work. Push it into `messageHistory` as an assistant message, so the next turn knows the result.
- On `WAITING_APPROVAL`, show the reviewing text and poll every 60 seconds while open. On `FAILED`/`EXPIRED`, show a friendly line plus "contact us" text.
- Save active tokens in `sessionStorage` (already used for `sessionId`). When the modal opens, pick polling back up for any unfinished token.
- Any polling error just stops polling quietly. It never throws into the chat flow, so the main chat stays unaffected.
- Prompt lines from §2.2 (the C3 block) go into `WorkflowPromptSection`, shown when `workflow-async.enabled` is on.

**Deploy:** theme extension deploy (Shopify CLI, per `shopify-theme-extension/README.md`). Rollback means deploying the previous extension version. Backend C2 works fine with the old widget, so the widget can be rolled back independently.

**Risk:** this is the storefront JS, which matters most. Keep the change additive: new handler, new card component, no edits to the existing token/status/done handling or the blocking fallback. The sibling plans (speed, reliability, feedback) probably edit this same file. **The parent session should run the widget changes one after another, not in parallel.**

**Test without DB:** load the widget into a static HTML page in the built-in browser, with `fetch` stubbed to replay a recorded SSE stream that includes a `workflow_run` event, and a stubbed status endpoint that goes RUNNING → COMPLETED. Check the card renders, polling stops, and the cart link works. Also replay a stream **without** the event, to check that rendering is identical to today's.

**Post-deploy:** on the live storefront (desktop and phone), run one normal chat, then one async test workflow. Close and reopen the modal mid-run, and the card should resume.

**Size:** **Medium:** ~200 lines JS, 1–1.5 days.

### 4.6 C4: allow approval-gated workflows from chat

**Change:** in `WorkflowDelegationToolHandler` and the description/prompt filter, an approval workflow is allowed **only if** `workflow.approval.real-pause` (C1) **and** `chatbot.workflow-async.enabled` (C2) are both on **and** the workflow is marked async. Otherwise it's refused, as today. The status endpoint maps PAUSED with a pending approval to `WAITING_APPROVAL`. After approval the C1 resume runs the rest, and the sweeper leaves PAUSED runs alone until `expires_at`.

**Staff notification:** required. See §4.7 (Slack to Angie, plus lead capture). C4 should not go live without C5a.

**Approval content:** the Approval Queue page shows the execution, so staff should see the customer's request (`trigger.message`) and shop. Check `ApprovalQueue.jsx` shows the trigger data, and add a line if it doesn't.

**Risk:** low technically, since C1–C3 do the heavy lifting. The business risk is the customer experience if nobody approves (question 4).

**Size:** **Small:** ~40 lines plus tests, half a day.

---

### 4.7 C5: evolution, Slack alerts to Angie and lead generation (agreed 2026-10-19)

**Idea (Nic):** when a customer's chat request needs a staff decision, Angie gets a Slack message straight away, with the customer's details. Every such request also becomes a **lead** the team can follow up, even if it isn't approved or the customer leaves.

Nothing for Slack exists in the codebase today, so this is new.

**C5a: Slack notification (small, about 1 day)**
- New `service/notify/SlackNotifier.java`: a Slack app with a bot token (`SLACK_BOT_TOKEN`) posting with `chat.postMessage` to Angie's Slack user id (`SLACK_LEADS_USER_ID`), or to a `#leads` channel (`SLACK_LEADS_CHANNEL`). An *incoming webhook* would be simpler, but it can only post to one channel. A DM to Angie needs the bot token.
- Triggered when a chat run enters `WAITING_APPROVAL` (hooked from `ChatWorkflowRunService`) and when a lead is captured (C5b). The message contains: the customer's request, name, email/phone, the workflow, the shop, and a link to the Approval Queue page / chat log.
- Sent in the background, after the database write, with a 5-second timeout. A Slack failure is logged and never affects the chat or the workflow. Flag: `notify.slack.enabled` (default false).

**C5b: lead capture in chat (small-medium, about 1–2 days)**
- New chat tool `capture_customer_details` (a `ChatToolHandler` bean, so it's auto-registered with **no ChatAgentService change**). Inputs: name, email and/or phone, the request, and `consent: true`. Enabled only when `chatbot.leads.enabled` is on.
- Prompt lines (in `WorkflowPromptSection`, or the custom instructions): ask for details **only** when the request needs staff follow-up (approval workflow, special order, quote, out-of-stock wanted list). Say why: "so a team member can get back to you". Never push for them during a normal product chat.
- **Privacy (Australian Privacy Act):** before collecting, the bot tells the customer what the details are for, and links the store privacy policy. Details are stored only after the customer agrees. No marketing sign-up unless they say yes separately.
- New table `chat_leads` (migration): name, email, phone, request, session_id, shop, linked `chat_workflow_runs` id, status (NEW / CONTACTED / WON / LOST), created_at. New admin page or tab "Leads" listing them (frontend, behind admin auth).
- Optional later: create or update the Shopify customer record with a `chat-lead` tag (needs `write_customers`, which the app may not have yet; check scopes).

**C5c: approve in Slack (later, medium)**
- "Approve" / "Reject" buttons in Angie's Slack message, calling a new endpoint `/api/slack/interactions`. That endpoint must verify Slack's request signature and be added to the AdminAuthFilter allowlist. It calls the same `ApprovalService.approveRequest/rejectRequest` as the admin page.
- This needs C1's real pause/resume to be live.

**Order:** C5b (leads) is useful on its own, even without approval workflows: any "can you order this in for me?" chat becomes a lead. So it can ship right after A + B, before C1–C4. C5a ships with C5b. C5c comes last.

**What you'll need to set up:** a Slack app in your workspace (I'll give exact steps when we get there), Angie's Slack user id, and a decision on DM vs a `#leads` channel.

## 5. Recommended order and deploy stages

| # | Deploy | Contents | Flag default | Rollback |
|---|---|---|---|---|
| 1 | Fix | **B** (shop context + R3 linked-agent gate) | on (gates are safe) | env flags |
| 2 | Feature | **A** (workflow prompt section) | off → turn on after check | env flag |
| 3 | Migration only | `resume_step_index` column + `chat_workflow_runs` table + `workflows.run_async_from_chat` | n/a (unused) | leave in place |
| 4 | Fix | **C1** (real approval pause) | off → on after admin test | env flag |
| 5 | Feature | **C2** backend async | off | env flag |
| 6 | Feature (storefront JS) | **C3** widget | n/a | previous extension version |
| 7 | Feature | **C4** approval-from-chat (needs C5a live) | off | env flag |
| 2b | Feature (can go right after A) | **C5a + C5b** Slack alert to Angie + lead capture | off | env flags |
| 8 | Feature (later) | **C5c** approve/reject from Slack | off | env flag |

Total roughly **6–8 working days** of build and test, spread over 7 small deploys. A + B alone (about 1.5 days) give most of the "smarter answers" benefit. C is the bigger investment, and is only worth it if you have workflows you actually want customers to run (questions 1–2).

---

## 6. Notes for the parent session (merging the plans)

- **Shared files with sibling plans, likely conflicts:** `ShopifyChatController.java` (B, C2), `ai-search-client.js` (C3), and possibly `ChatAgentService.buildSystemPrompt` (A). Sequence those edits. Don't run them in parallel worktrees.
- **Migration numbers:** this plan needs 1–2 new migrations (C stages). Assign V023+ centrally across plans.
- **Reactor-context pattern:** B introduces `ChatRequestContext` and C2 a side channel, both carried on the controller's `contextWrite`. If the reliability or feedback plans need per-request data inside tools (request id, feedback ids), they should reuse this rather than add fields to `ChatAgentService`.
- B's R3 gate is a security fix in its own right, and can ship alone at any time.

## 7. Out of scope / follow-ups noticed

- **R2 race:** `setAnthropicModel/setTemperature/setMaxTokens/setShopContext` mutate the singleton `ChatAgentService` on every request. That's harmless with one shop, but wrong. The fix is to pass a per-request settings object, the same way `configOverride` already works. It touches ChatAgentService, so it's a separate decision.
- R4 drift: the specialist-agent list is missing from the DB-prompt path. That could be fixed with the same "section class" pattern as A.
- Calling the workflow outputs through a cheap "summarise for the customer" model call, instead of using the last step's text as-is.
