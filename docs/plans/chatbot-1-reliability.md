# Chatbot plan 1 of 5: Reliability

**Status:** plan only, no code changed. Written 2026-10-19 from branch `claude/admin-api-auth`. That branch contains `main` plus the admin-auth work, and `main` already has the Oct 2026 Hikari fix (`538962b`).
**Scope:** three changes, each shipped as its own Railway deploy:

| Deploy | Item | What it fixes | Size | Risk to storefront chat |
|---|---|---|---|---|
| R1 | (b) Shopify call timeouts | A hung Shopify call can stall a chat for up to 180s, and an admin or scheduler call forever | Small (~60 lines, 3 files) | Low |
| R2 | (a) Spring Boot 3.2.0 → 3.2.12 | Spring bug fixes for clients that disconnect mid-request (the Oct 2026 leak path) | Tiny diff, broad effect (1–2 lines in `pom.xml`) | Low–medium |
| R3 | (c) Per-request chat context | Concurrent chats overwrite each other's shop, model and prompt settings | Medium (~200 lines, 4 files + 1 test) | Medium |

Why this order:
- **R1 first.** It is the smallest and most contained change, and it gives a low-stakes run through the deploy, verify and rollback routine.
- **R2 next.** The Hikari fix already removed the chat path from the leak (OSIV is off for `/api/shopify/chat/**`), so the upgrade is defence in depth for admin async routes rather than an emergency. Shipping it alone keeps any regression easy to attribute.
- **R3 last.** It is the only item that edits the chatbot brain (`ChatAgentService`), so it should land on top of a stable platform.

None of the three adds a Flyway migration, so every rollback is a plain redeploy of the previous build with no database step.

---

## Decisions (Nic, 2026-10-19)

- **Boot:** go to 3.2.12 now (R2) to get the async-disconnect fixes with almost no change. A move to the newest line (4.1.x: Spring 7, Hibernate 7, Jackson 3, Flyway 12) becomes its own later project, "R4". It needs a staging environment first, because Jackson 3 renames the JSON library used in about 65 source files. 3.5.16 is a possible middle step.
- **hypersistence-utils:** keep 3.7.0 and bump only if local testing shows a problem.
- **Timeouts:** agreed: 5s connect, 15s per attempt, 30s total.
- **Deploy order:** my call. R1 → R2 → R3, each at least 24h apart, and not on the same day as admin-auth. Admin-auth ships first because it is already built.
- **Local testing:** Docker Desktop is installed and running (28.3.2), so the Docker Postgres recipe at the end is usable.
- **R3 race: confirmed live.** Nic ran the query on 2026-10-19. Prod has active `product_search` prompts (ids 2 and 3, both version 2, both active; id 1 is inactive). Storefront chats normally use that database prompt, so the race can swap in the generic config-built prompt. It can also give a `/api/v1` persona chat the storefront prompt. R3 therefore fixes a real bug.
  - **Side finding (checked 2026-10-19):** rows 2 and 3 are both global (`shop_id` null), 4,476 characters each, and were saved 7 seconds apart on 2025-11-12. They look like a double-save of the same prompt, so the tie in `findActivePromptByTypeForShop` is harmless in practice. Optional tidy-up: deactivate row 2 on the admin prompt page. That is a data fix, not part of R1–R3.

## Questions and risks for Nic (please answer before R1 starts)

1. **Boot target version.** I recommend **3.2.12**, the last 3.2.x release. It contains all three Spring fixes (#32042 in 6.1.4, #32342 backported around 6.1.5, #33421 in 6.1.13; 3.2.12 ships Spring 6.1.15). It keeps Flyway on the same version (9.22.3).
   - **3.3.13** would also work, but it moves to Flyway 10. Flyway 10 needs an extra `flyway-database-postgresql` dependency, and **without it the app fails to start** on Railway. 3.3 also brings Hibernate 6.5 and Jackson 2.17. That is more change for no extra benefit to this goal.
   - Both 3.2 and 3.3 are past their free support window, and the current lines are 3.5.x and 4.x. Moving to 3.5 or 4.x is a separate, larger project. 3.2.12 is the safe step now. **OK to target 3.2.12?**
2. **hypersistence-utils.** We are on 3.7.0. Its README says the `-hibernate-63` artifact supports Hibernate 6.3–6.6, so it should work with the Hibernate 6.4.10 that Boot 3.2.12 brings, but 3.7.0 was built against 6.3.1. The plan keeps 3.7.0 and tests JSONB read and write on agents, workflows, executions and schedules locally. If anything fails, the fallback is to bump to the latest 3.7.x or 3.9.x in the same deploy. **Are you happy to leave that call to testing?**
3. **Timeout values for Shopify (R1).** Proposed: connect 5s, per attempt 15s, whole call including retries 30s. Each value can be changed with an environment variable without a code change. Shopify Admin GraphQL normally answers in well under 3s. The risk is the hourly inventory analysis job, which pages through large product sets: a false timeout there would show up as a failed job, not a chat failure. **OK with these defaults?**
4. **Anthropic calls also have no timeout.** `ChatAgentService` builds its own WebClient for api.anthropic.com with no response timeout, so only the 180s MVC async limit bounds a stalled Claude call. That is outside this item's brief, and the speed session may touch the same code. **Should R1 also add a generous timeout there (for example 90s per Claude call), or leave it to the speed plan?** My recommendation is to leave it to the speed plan, to avoid two plans editing the same lines.
5. **Is the R3 race visible today?** The shop context only changes behaviour when `system_prompts` has an active `PRODUCT_SEARCH` prompt for hearnshobbies. A read-only query on prod settles it:
   ```sql
   select id, prompt_name, prompt_type, version, is_active, shop_id from system_prompts order by id;
   ```
   (Column names were checked against `V007__create_system_prompts.sql`.) If a prompt exists, a customer can sometimes get the fallback prompt today, and a `/api/v1` persona chat can sometimes get the storefront's prompt.
6. **R3 changes one admin display.** Today every storefront chat permanently overwrites the service's "default model" with the shop's model, so `GET /api/config` shows the last chat's model. After R3, `/api/config` shows the real default (from `application.yml` or the admin page). The model customers actually get does not change: `chatbot_configs.model_name` still wins, as now.
7. **Deploy spacing against admin-auth.** `claude/admin-api-auth` is not deployed yet. I suggest none of R1–R3 ships on the same day as admin-auth, and each lives at least 24h before the next one. **Which goes first: admin-auth or R1?**
8. **Local testing uses Docker Postgres.** There is no local Postgres, but Docker is installed. The plan uses a throwaway `postgres:16` container with an empty database, which Flyway fills on startup. **The local app must never point at the prod database:** Flyway `repair-on-migrate` and the schedulers would run against prod. Is Docker OK to use?
9. **Found in passing (not fixed by these items):**
   - `OrderLookupChatToolHandler` wraps the *blocking* `orderService.searchOrders` in `Mono.fromCallable` without `subscribeOn`. In the chat tool loop that runs on a Reactor Netty thread, where `.block()` is normally refused ("block()/blockFirst()/blockLast() are blocking, which is not supported in thread reactor-http-nio-…"). Order lookups from chat may therefore always return "Unable to look up order". To verify, search Railway logs for `not supported in thread reactor-http`.
   - `RateLimiter.waitIfNecessary` is `synchronized` and calls `Thread.sleep`. It runs at the start of `executeQueryReactive`, so under heavy load it can pause a Netty event loop thread.
   - Both are proposed as follow-ups. They are outside R1–R3.

---

## R1: Shopify response timeouts (item b)

### Plain-language summary
Today, if Shopify accepts a connection but never answers, nothing gives up. A chat waits until the 180s safety limit. An admin page or the hourly inventory job waits **forever**, because `executeQuery` calls `.block()` with no limit and holds a server thread the whole time. R1 makes every Shopify call give up after a bounded time and return the "error" response the code already knows how to handle.

### Files and changes
1. **`src/main/resources/application.yml`**: add under `shopify:`:
   ```yaml
   http:
     connect-timeout-ms: ${SHOPIFY_CONNECT_TIMEOUT_MS:5000}
     response-timeout-ms: ${SHOPIFY_RESPONSE_TIMEOUT_MS:15000}   # per attempt
     total-timeout-ms: ${SHOPIFY_TOTAL_TIMEOUT_MS:30000}         # whole call incl. retries
     max-idle-ms: ${SHOPIFY_MAX_IDLE_MS:20000}                   # drop pooled sockets before Shopify/edge does
   ```
2. **`config/ShopifyConfig.java`**: add a nested `Http` class (same pattern as the existing `RateLimit`) with those four fields.
3. **`config/WebClientConfig.java`**: give the builder an explicit Reactor Netty client:
   - `HttpClient.create(ConnectionProvider.builder("shopify").maxIdleTime(maxIdle).build())`
   - `.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)`
   - `.responseTimeout(Duration.ofMillis(responseTimeout))`
   - Wire it in with `.clientConnector(new ReactorClientHttpConnector(httpClient))`.
   - Headers, base URL and the 16MB codec limit stay unchanged.
   - `maxIdleTime` prevents the classic "Connection prematurely closed BEFORE response" error on sockets the far end has already dropped.
4. **`client/ShopifyGraphQLClient.java`**:
   - In both `executeQuery(GraphQLRequest)` and `executeQueryReactive(GraphQLRequest)`, add `.timeout(totalTimeout)` **after** `retryWhen` and **before** `onErrorResume`. A timeout then becomes the existing `createErrorResponse(...)` result, which callers already handle as "query returned errors".
   - In `executeQuery`, change `.block()` to `.block(totalTimeout + 5s)` as a hard backstop. If it fires, it throws `IllegalStateException`, which the existing `catch (Exception e)` already turns into an error response.
   - Make `isRetryableError` explicit: keep retrying 429, 502, 503 and "throttled", but **do not retry timeouts**. Today it matches the string "timeout", so a slow Shopify could cost 4 × 15s plus backoff. One bounded attempt is better for a waiting customer.
   - Log timeouts with a distinct message (`Shopify call timed out after {}ms`) so they are easy to find in Railway logs.

### Risk to the live storefront chat
- **Low.** Chat product tools all use `executeQueryReactive` through `ProductService.searchProductsReactive`. The only change on that path is that a call now fails after 15–30s instead of hanging. The tool returns an error JSON and Claude tells the customer it couldn't search, as it already does for Shopify 5xx errors.
- **False timeouts:** only if a legitimate Shopify response takes more than 15s. That is unlikely for chat-sized queries (5–20 products). The large-query risk is admin and inventory paths. Raise `SHOPIFY_RESPONSE_TIMEOUT_MS` on Railway to fix it without a redeploy of code (an env change triggers a restart).
- **Connection pool change:** setting an explicit `ConnectionProvider` replaces the shared default pool for this one client only. The Anthropic and MCP WebClients are unaffected.

### Local testing (no local Postgres)
1. `mvn -q compile`, then `mvn test` (existing tests must stay green).
2. **New unit test** `ShopifyGraphQLClientTimeoutTest`, needing no DB and no Shopify:
   - Start an `okhttp3.mockwebserver` server or a plain JDK `HttpServer` on localhost. Point a `WebClient` built by the new config at it.
   - Case 1: the server delays 20s. Assert `executeQuery` returns an error response within ~16s, and `executeQueryReactive` likewise.
   - Case 2: the server returns 503 twice, then 200. Assert it retries and succeeds.
   - Case 3: the server returns a slow response once. Assert there is **no** retry (request count = 1).
   - If adding MockWebServer is unwanted, use `com.sun.net.httpserver.HttpServer` from the JDK, which needs no new dependency.
3. **Full app run (optional):** see "Local run recipe" at the end. Send a chat that searches products to prove the normal path is unaffected.

### Verify after deploy
- Railway logs: the app starts and no `Shopify call timed out` lines appear in normal traffic. A few over days are fine; a steady stream means the timeout is too tight.
- Send a storefront chat ("do you have any Tamiya paint?") on both the normal widget (streaming) and with streaming disabled or forced fallback, if that is easy. Products and links appear.
- An admin products or orders page loads.
- The next hourly inventory analysis run completes. Look for its normal completion log and **no** timeout lines. This is the most likely place for a false timeout.

### Rollback
- Fast: Railway → service → Deployments → previous deployment → **Redeploy**.
- Without a code rollback: set `SHOPIFY_RESPONSE_TIMEOUT_MS` / `SHOPIFY_TOTAL_TIMEOUT_MS` to large values (for example 600000) to effectively disable the timeouts.
- Code: `git revert <R1 commit>` and push. No DB change.

### Size
About 60 changed lines plus ~80 lines of test. Roughly half a day including testing.

---

## R2: Spring Boot 3.2.0 → 3.2.12 (item a)

### Plain-language summary
Spring Boot is the framework the whole app runs on. Version 3.2.0 (Nov 2023) has known bugs in how it tidies up when a browser disconnects in the middle of a long, async request. That is exactly the situation our diagnosis linked to the database connections that leaked until the pool ran dry. 3.2.12 is the final bug-fix release of the same line: same features, same config, about a year of fixes. The Hikari fix already took the storefront chat off the leaking path, so this closes the same hole for admin and agent routes, and fixes other bugs.

### What actually changes (verified from Maven Central BOMs)

| Library | 3.2.0 (now) | 3.2.12 | Note |
|---|---|---|---|
| Spring Framework | 6.1.1 | **6.1.15** | Contains #32042 (6.1.4), #32342 (5.3.33 / 6.1.x backport), #33421 (6.1.13) |
| Hibernate ORM | 6.3.1 | **6.4.10** | Minor-version step; the main thing to test |
| Flyway | 9.22.3 | 9.22.3 | **Unchanged.** Migrations unaffected. |
| HikariCP | 5.0.1 | 5.0.1 | Unchanged |
| PostgreSQL JDBC | 42.6.0 | 42.6.2 | Includes the CVE-2024-1597 fix |
| Tomcat | 10.1.16 | 10.1.33 | Patch releases |
| Jackson | 2.15.3 | 2.15.4 | Patch |
| Reactor | 2023.0.0 | 2023.0.12 | Patch (WebClient, Netty) |
| Lombok | 1.18.30 | 1.18.36 | Patch |
| hypersistence-utils | 3.7.0 (pinned) | 3.7.0 (pinned) | See question 2 |

### Files and changes
1. **`pom.xml`**: `<parent><version>3.2.0</version>` → `3.2.12`.
2. **`pom.xml` (only if testing shows a JSONB problem):** `hypersistence-utils-hibernate-63` `3.7.0` → latest 3.7.x or 3.9.x.
3. Nothing else is expected. If `OpenEntityManagerInViewConfig` (from the Hikari fix) depends on spring-orm internals, it is covered by `OpenEntityManagerInViewConfigTest`, which must still pass.
4. Before and after, save `mvn dependency:tree > /tmp/deps-<ver>.txt` and attach the diff to the PR as a record.

### Risk to the live storefront chat
- **Startup failure** is the main risk, and it is the safe kind. Railway keeps the old deployment serving until the new one passes its health check. Spring Data validates every `@Query` (including the fetch-join queries in `AgentRepository` / `AgentToolRepository`) at startup, so Hibernate 6.4 parsing problems show up on the first local run, not in front of customers.
- **JSONB columns** (`Agent`, `Workflow`, `AgentExecution`, `WorkflowSchedule` via hypersistence `JsonBinaryType` / `JsonNodeBinaryType`) are the main runtime risk. They affect agents, workflows, schedules and chat→agent delegation (the "expert" answers). They are not used by plain product chat.
- **Async and SSE behaviour changes** in Spring 6.1.x (#33421 is specifically SSE plus disconnect). The storefront widget's streaming endpoint is SSE, so a changed edge case could alter how a disconnect or heartbeat ends. The widget already falls back to the blocking endpoint on any stream error, which limits customer impact. Still, streaming is the first thing to smoke-test.
- **WebClient / Netty patch changes**: low risk. They affect all outbound calls (Anthropic, Shopify, MCP).

### Local testing
1. `mvn -q compile && mvn test`.
2. **Full app on Docker Postgres** (recipe below). On first start, confirm:
   - Flyway applies V002–V020 cleanly on the empty DB.
   - The startup log shows `:: Spring Boot :: (v3.2.12)` and `Started ... in`.
3. Exercise the JSONB paths through the admin API (send `X-API-Key` with `ADMIN_BOOTSTRAP_KEY` if admin-auth is merged):
   - create an agent with a JSON config, read it back;
   - create a workflow with steps, execute it, read the execution history (`/api/executions`);
   - create a schedule (`/api/schedules`) and read it back.
4. Chat smoke test: send a message to `POST /api/shopify/chat/message?shop=…` and to `/message/stream`.
5. **Disconnect leak test (the reason for the upgrade):**
   - Run `curl -N --max-time 3` against `/message/stream` 20 times, and against an async admin route that still has OSIV (for example `POST /api/agents/{id}/execute` with a slow task) 20 times.
   - Then run the connection-age query from the incident note inside the container: `docker exec … psql -c "select state, count(*) from pg_stat_activity …"`.
   - Expect all app connections `idle` and returned, and no Hikari `Connection leak detection triggered` WARN after 200s.
   - Optional: run the same test on 3.2.0 first as an A/B. If 3.2.0 leaks there and 3.2.12 doesn't, that directly confirms the diagnosis.

### Verify after deploy
- Railway deploy logs show `v3.2.12`, and startup completes.
- Storefront chat, streaming and non-streaming. One "expert" question that triggers delegation to agent 23, to exercise the JSONB plus agent path.
- The admin SPA Agents, Workflows, Executions and Schedules pages load.
- Scheduler logs every minute with no errors.
- After 1h and 24h, run the incident note's leak check on prod (read-only):
  ```sql
  select count(*) from pg_stat_activity
  where application_name = 'PostgreSQL JDBC Driver' and backend_start < now() - interval '35 minutes';
  ```
  Expect 0. Also search logs for `Connection leak detection triggered`; expect none.
- Over the next week, the daily HTTP 499s in Railway logs should no longer coincide with any connection growth.

### Rollback
- Railway → Deployments → previous deployment → **Redeploy**. No DB migration changed and Flyway is the same version, so `flyway_schema_history` is untouched and the old build runs against the same DB.
- Code: revert the `pom.xml` commit.

### Size
A 1–2 line diff. About half a day, mostly the local test run.

---

## R3: Per-request chat context instead of singleton setters (item c)

### Plain-language summary
`ChatAgentService` is a single shared object that serves every chat at once. For each storefront message, the controller currently writes "this shop, this model, this temperature, this max tokens" into that shared object, then starts the chat, then wipes the shop when the chat ends. Two chats at the same moment can therefore overwrite each other. The visible consequence today: a customer's chat can start just as another customer's chat finishes, and the "wipe" lands between "set shop" and "build prompt". That customer then gets the generic prompt instead of the shop's database prompt. A `/api/v1` persona chat can also pick up the storefront shop's prompt. R3 passes these settings along with each request, so nothing is shared.

### Where the shared state is (verified)
- **Fields on the singleton:**
  - `currentShop` (`ChatAgentService.java:87`), read only in `buildSystemPrompt` (`:770`).
  - `anthropicModel` / `temperature` / `maxTokens` (`:53–59`), used as fallbacks after `ChatbotConfig` in `callClaudeWithTools` (`:530–536`), `buildStreamingRequestBody` (`:449–451`) and `callClaudeWithoutTools` (`:692–694`).
- **Writers:**
  - `ShopifyChatController.sendMessage` (`:106–111`, clear at `:137, :150`).
  - `ShopifyChatController.streamMessage` (`:196–199`, clear at `:213, :216`).
  - `ConfigController` (`:83–95`, admin "change default model/tokens/temperature").
- **Readers that need the singleton defaults:** `ConfigController` (`:55–57, :100–102, :144`).
- **Unaffected:** `effort`, `preorder*` and `systemPromptTemplate` are set once at startup and never written per request.

### Design
1. **New file `service/ChatRequestContext.java`.** A small immutable record:
   ```java
   public record ChatRequestContext(ShopifyShop shop, String model, Double temperature, Integer maxTokens) {
       public static ChatRequestContext none() { return new ChatRequestContext(null, null, null, null); }
   }
   ```
   A null field means "use the service default", exactly like `ChatbotConfig`'s null fields today.
2. **`ChatAgentService`:**
   - Add overloads `processChat(ChatRequest, ChatbotConfig, ChatRequestContext)` and `streamChat(ChatRequest, ChatbotConfig, ChatRequestContext)`. The existing 1- and 2-arg overloads delegate with `ChatRequestContext.none()`, so `ChatController`, `PersonaChatController` and anything else keep identical behaviour.
   - At entry, resolve once into a private immutable `ResolvedSettings(model, temperature, maxTokens)`, using today's precedence:
     `config.getModelName()` → `ctx.model()` (already validated) → `this.anthropicModel` default.
     The same applies to temperature and maxTokens.
   - Replace the `ChatbotConfig config` parameter that the private loop methods already pass along with a tiny per-request holder: `RequestState(ChatbotConfig config, ResolvedSettings settings, ShopifyShop shop)`. That means one parameter swap in each of `callClaudeWithTools`, `handleClaudeResponse`, `handleToolUseAndContinue`, `callClaudeWithoutTools`, `streamClaudeWithTools`, `afterStream`, `runToolsAndAppend`, `buildStreamingRequestBody`, `buildToolsArray` and `buildLinkInstructions`. Reusing the existing parameter keeps the diff mechanical and avoids adding a second argument everywhere.
   - `buildSystemPrompt(config, shop)` reads the shop from the argument, not the field.
   - **Delete** the `currentShop` field, `setShopContext` and `clearShopContext`. Nothing else calls them.
   - **Keep** `get/setAnthropicModel`, `get/setTemperature` and `get/setMaxTokens` as the *global defaults* for `ConfigController`. They are no longer touched per request.
3. **`ShopifyChatController`:**
   - In both endpoints, replace the four `set…` calls with
     `ChatRequestContext ctx = new ChatRequestContext(shopConfig, modelValidationService.validateOrDefault(shopConfig.getAiModel()), shopConfig.getAiTemperatureValue(), shopConfig.getAiMaxTokensValue());`
     then `chatAgentService.processChat(request, null, ctx)` / `streamChat(request, null, ctx)`.
   - Remove the `clearShopContext()` calls and the now-empty `doFinally`.
   - Inject `ModelValidationService`. Alternatively, ChatAgentService validates `ctx.model()` itself, which avoids touching the controller constructor; decide at implementation.
4. **Tools are unaffected.** `ChatToolRegistry.executeTool(name, input)` never read the shop context (CLAUDE.md's "shop context not injected into chat tools" stays a separate planned item, #3). The `RequestState` holder is a natural place to add that later.

### Behaviour that must stay identical (checklist for review)
- Model precedence: `chatbot_configs.model_name` > shop `ai_model` > `application.yml`/admin default. Prod uses `chatbot_configs.model_name`, so the live model is unchanged.
- An invalid shop model is still corrected by `ModelValidationService.validateOrDefault`. The difference is that it no longer rewrites the global default.
- The DB `system_prompts` prompt is used when the shop has an active `PRODUCT_SEARCH` prompt, with the same fallback as before.
- Streaming heartbeat, `takeUntil` and the error frames in `streamMessage` are untouched apart from removing `clearShopContext`.

### Risk to the live storefront chat
- **Medium**, because this edits the chatbot's core loop: about 10 method signatures in a 1,283-line class. The change is mechanical, though, and the compiler catches every missed call site because the parameter type changes.
- The **subtle risk** is a precedence slip, for example using the shop's temperature where `chatbot_configs` should win. The unit tests below pin the precedence.
- **Intentional behaviour changes**: the `/api/config` display (question 6), and the occasional wrong-prompt race goes away.

### Local testing
1. `mvn -q compile && mvn test`.
2. **New unit test `ChatAgentServiceContextTest`** (Mockito, no DB, no network):
   - Build `ChatAgentService` with a `WebClient.Builder` whose `exchangeFunction` captures the outgoing Anthropic request body and returns a canned `end_turn` response.
   - Mock `SystemPromptService` to return prompt "SHOP-A" for shop A and nothing for shop B, and mock `ChatbotConfigService.getConfig()`.
   - **Precedence:** with `ChatbotConfig.modelName=X` and ctx model Y, the body has X. With a null config model, it has Y. With both null, it has the default. Repeat for temperature and max_tokens.
   - **Isolation:** fire 200 interleaved `processChat` calls in parallel (`Flux.range(...).flatMap(..., 32)`) alternating shop A / shop B contexts. Assert every captured body for A has system prompt "SHOP-A…" and every B body has the ConfigController-built prompt. Before R3, the same test written against the setters should fail intermittently, which proves the race. Run it once on `main` to show that.
   - Repeat the isolation check for `streamChat` with a canned SSE body.
   - **Defaults untouched:** after the parallel run, `getAnthropicModel()` still returns the original default.
3. **Full app run** (recipe below): one blocking and one streaming chat, plus `GET /api/config` before and after a chat (the model should no longer change).

### Verify after deploy
- Storefront chat (streaming and blocking). Answers, product links and cart links are normal.
- Railway logs: for every chat, the same prompt-source line as before (`Using dynamic system prompt: …` if a DB prompt exists, otherwise `Using ChatbotConfig-based system prompt`). After R3 it should be consistent across all chats; before, it could flip.
- The `chat_analytics` rows for the next few chats show the expected model and token counts.
- Open two browser tabs and send messages at the same moment. Both answer normally.
- `/api/config` shows the default model, unchanged after chats.

### Rollback
- Railway → Deployments → previous deployment → **Redeploy**. No DB or config change.
- Code: revert the R3 commit.

### Size
About 150–250 changed lines in `ChatAgentService` (mostly signature swaps), ~30 in `ShopifyChatController`, a ~15-line new record, and ~200 lines of test. About 1 day including testing.

---

## Local run recipe (shared by R1–R3)

```bash
docker run -d --name hh-pg -e POSTGRES_PASSWORD=dev -e POSTGRES_DB=shopify -p 5433:5432 postgres:16
```

- Point `.env` `DATABASE_URL` / `SPRING_DATASOURCE_*` at `localhost:5433` (exact variable names per `.env.example`). Keep the real `SHOPIFY_*` (read-only queries) and `ANTHROPIC_API_KEY` (a few cents per test chat).
- **Never use the prod DB URL locally.**
- Run `mvn spring-boot:run`. Flyway builds the schema on the empty DB.
- Insert one active `shopify_shops` row for `hearnshobbies.myshopify.com` with `ai_enabled = true`, so the chat endpoints accept the shop.
- Agent 23 will not exist (it is DB-only in prod), so delegation tests need a stand-in agent created through `/api/agents`.
- Schedulers run locally against the empty DB. They have no workflows or schedules, so they do nothing. Confirm before running that no scheduled job writes to Shopify unconditionally.
- Clean up with `docker rm -f hh-pg`.

## Not in scope (follow-ups to propose separately)
- `OrderLookupChatToolHandler` blocking on a Netty thread (question 9).
- `RateLimiter` sleeping on Netty threads (question 9).
- Anthropic WebClient timeout (question 4).
- `spring.task.scheduling.pool.size` is 1, so all `@Scheduled` jobs share one thread (from the incident note).
- Turning OSIV off globally, and the later move to Boot 3.5.x / 4.x.
