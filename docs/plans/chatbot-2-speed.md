# Chatbot plan 2 — Speed

**Status:** planning only. No code or production changes made. Written 2026-10-19.
**Scope:** why storefront chat replies take 4–47 s (about 40 chats a month end in HTTP 499 because the customer gives up), and a ranked list of fixes.
**Siblings:** reliability, feedback and smarter-answers plans are written in parallel; overlaps are flagged inline.

---

## 0. Read this first — questions and risks for Nic

1. **🔴 Live config was partly wiped on 2026-10-18 (found during this work, not a speed fix).**
   The `PUT /api/config/chatbot` at 16:48:17 UTC that switched the model to Sonnet 5.5 sent only some fields.
   `ChatbotConfigService.updateEntityFromConfig` copies number and true/false fields even when they were left out, so they became 0/false.
   The live config (read-only `GET /api/config/chatbot`, 2026-10-19) now has:
   `maxSearchResults=0`, `enableProductSearch=false`, `includeCartLinks=false`, `showPrices=false`, `requireSearchBeforeRecommendation=false`.
   Effect in the logs: `search_products` runs with "Max Results: 0" and returns an empty list, and the prompt has lost its Add-to-Cart link rules.
   Empty searches also make the bot retry with more tool calls, which is slower.
   **Decision needed:** restore the values (a production write; the parent session is taking this to Nic). The code fix that stops it happening again is **item R** below. Ship it before anyone else changes config through the API.
2. **Delegation is the biggest time cost, and most of it buys nothing.** The fix (§3, item 1) means un-linking or re-scoping some "expert" agents. Are you happy for the storefront bot to answer stock/price/specials questions itself, without the expert agents? (Agent 23 is not involved.)
3. **Streaming chats are not recorded in `chat_analytics`.** Only the blocking endpoint records analytics. Most customers now use the streaming endpoint, so the dashboard's response times (and any "average speed" you see) leave out most real chats. Item 0 fixes this; without it we cannot prove any speed gain.
4. **Data limits.** Railway keeps HTTP logs only for the current deployment (from 2026-10-18), and I could not read the database directly (that would need credentials). So the timings below come from app logs for 2026-10-16 → 2026-10-19 (about 12 real and test turns), not a full month. Item 0 gives us proper numbers going forward.
5. **Widget changes need a Shopify theme-extension deploy.** That is a separate release from Railway, with its own rollback (`shopify-theme-extension/EMERGENCY-ROLLBACK.md`). I keep those changes few and small.
6. **Shared mutable state** (`setAnthropicModel`, `setShopContext` on a singleton) means two customers chatting at once can affect each other. That belongs to the reliability plan, but it matters if item 4 (a per-question model choice) is ever built: that must not be done through the same shared setters.

---

## 1. Where the time goes (measured)

### End-to-end (Railway HTTP log, current deployment)

| When (UTC) | Endpoint | Who | Total |
|---|---|---|---|
| 10-18 02:03 | blocking | test (curl) | **31.2 s** |
| 10-18 02:03 | stream | test (curl) | **41.4 s** |
| 10-18 02:55 | stream | customer (Android) | **26.2 s** |
| 10-18 04:54 | stream | customer (Windows) | 4.5 s |
| 10-18 09:01 | stream | customer (iPhone) | 5.8 s |
| 10-18 09:02 | stream | customer (iPhone) | **46.6 s** |
| 10-18 16:49 | blocking | test, Sonnet 5.5 | 15.2 s |
| 10-18 16:49 | stream | test, Sonnet 5.5 | 7.0 s / 17.3 s |

Simple turns finish in about 5 s. The slow ones (25–47 s) all have the same shape: **5–7 Claude calls one after another, with 2–4 expert delegations in between.**

### One slow turn, step by step ("do you have any tamiya paint?", Sonnet 4.6, 31 s)

| Step | Time | What happened |
|---|---|---|
| Claude call 1 | ~3 s | picks `browse_products` + delegate to SPECIALS FINDER |
| tools | ~1 s | browse returns 0 results (`product_type:PAINT` mismatch); SPECIALS FINDER has **0 tools**, so it answers from general knowledge |
| Claude call 2 | 2.9 s | delegates to PAINT EXPERT |
| delegation | 4.5 s | PAINT EXPERT's `web_search` **fails to load** ("Could not load tool handler WebSearchToolHandler"), so it also answers from general knowledge |
| Claude call 3 | 1.9 s | runs workflow "Stock Check Assistant" |
| workflow | 4.0 s | its STOCK AGENT has **0 tools**, so it cannot check stock |
| Claude call 4 | 1.3 s | finally calls `check_inventory` (0.45 s, real data) |
| Claude call 5 | 9.5 s | writes the 713-token answer |

**~10 s of the 31 s was delegations that returned no store data.** The 46.6 s customer turn at 09:02 had the same pattern: SCALE MODEL KIT EXPERT twice (5 s each), the Stock Check workflow (4.5 s), then a 13 s final answer.

### The time split, across the slow turns observed

| Bucket | Typical cost | Share of a slow turn |
|---|---|---|
| Expert delegations (agent or workflow; each is a separate Claude call on Sonnet 4.5/4.6 or Haiku) | 4–8 s each, 2–4 per slow turn | **30–45 %** |
| Main-bot "which tool next?" Claude calls | 1.3–3.5 s each, 4–6 per turn | 25–35 % |
| Final answer writing (600–900 tokens) | 4.5 s (Sonnet 5.5) to 9–13 s (Sonnet 4.6) | 20–30 %, but streamed, so the customer sees text arriving |
| Shopify / ERP tool calls | 0.4–1 s, run in parallel | < 5 % |

From 2026-10-13 to 10-18 the bot made 65 tool calls. **28 were `delegate_to_agent`** and 2 were `delegate_to_workflow`. It delegates on nearly every product question.

### Things already in place (so not new work)

- **Progress events already exist.** The stream sends `status` events ("Searching our catalogue…", "Checking with our paint expert — this can take a few seconds…"), the widget shows them, and it rotates reassurance messages before the first one arrives. A 15 s heartbeat keeps the connection alive.
- **Prompt caching is already on and working** (commit 7c6a93c, July 2026). Logs show `Prompt Cache HIT - 5456 tokens`.
  Why the Nov 2025 attempt (4f53034) was reverted in ae8ff75: that change also bumped the `anthropic-version` header from `2023-06-01` to `2024-07-15`. That is not a valid API version, so every call got HTTP 400. The `cache_control` format was fine; the header was the bug. The July 2026 version kept `2023-06-01` and works.
- **A product-search cache already exists**: `ProductService`, 90 s TTL, max 500 entries (`shopify.search.cache-max`). Logs show "Search cache hit". Shopify calls are already a small share of the time.
- **Sonnet 5.5 is faster.** The same final answer took 4.5 s on Sonnet 5.5 against 9.5 s on Sonnet 4.6 (effort `low`).

### Why customers give up (499s)

- Many waits pass 25 s with no answer text, only status lines.
- **If the stream fails before 80 characters have arrived, the widget silently re-asks on the blocking endpoint**, which re-runs the whole turn from scratch (`ai-search-client.js` ~line 425). A customer who already waited 30 s then waits another 30 s. That is a likely 499 source.
- I could not split the 499s between the stream and blocking endpoints: older HTTP logs are gone. Item 0 records this from now on.

---

## 2. Ranking (impact against risk)

| # | Fix | Time saved | Risk to live chat | Size |
|---|---|---|---|---|
| R | Fix partial config updates wiping settings (prerequisite) | indirect (empty searches cause retries) | very low | S |
| 0 | Measure: timing log line plus analytics for streamed chats | none (enables proof) | very low | S |
| 1 | Stop needless delegations (config first, then a prompt rule) | **8–20 s on slow turns** | low (config, instant revert) | XS–S |
| 2 | Lower the storefront tool-loop ceiling and stream the wrap-up | caps the worst case (~40 s → ~20 s) | low–medium | S |
| 3 | Widget: honest long-wait message; no silent full re-run | fewer 499s; no doubled waits | low (separate theme deploy) | S |
| 4 | Faster model for delegated experts (Haiku 4.5) | 2–4 s per delegation that still happens | low (per-agent DB setting) | XS |
| 5 | Prompt caching of the conversation, not just the system prompt | 0.2–0.6 s per Claude call after the first | low | S |
| 6 | Faster model for simple questions (router) | 1–3 s on simple turns only | **medium–high** (quality) | M |
| 7 | Longer product-search cache | < 1 s per turn | low | XS |

Recommendation: **R → 0 → 1 → 3 → 2 → 4**, then decide on 5. Put 6 and 7 on hold.
Items 0 and 1 together should bring the 25–47 s turns down to roughly 10–20 s.

---

## 3. The fixes in detail

### Item R — Partial config updates must not wipe settings (do before anything else)

- **The bug:** `PUT /api/config/chatbot` (`ConfigController.updateChatbotConfig`, ~line 229) turns the JSON body straight into a `ChatbotConfig`. In that class `maxSearchResults` is a plain `int`, and `requireSearchBeforeRecommendation`, `enableProductSearch`, `includeCartLinks`, `showPrices` and `showSkus` are plain `boolean`. Any field left out of the body arrives as 0 or `false`.
  `ChatbotConfigService.updateEntityFromConfig` (~lines 292–301) then copies those fields to the DB **without checking whether they were sent**. Text fields and the boxed ones (`modelName`, `temperature`, `maxTokens`, linked ids) are already null-guarded, which is why they survived.
  `includeProductLinks` only survived because the class defaults it to `true`.
  `saveProfile` (persona profiles, `POST`/`PUT` near line 203) goes through the same `updateEntityFromConfig`, so it has the same bug.
- **Recommended fix: merge the request onto the saved config.** In both controller methods, accept the raw JSON (`JsonNode`) instead of `ChatbotConfig`. Load the current config (`getConfig()` for global, the profile by slug for personas), then apply only the fields present in the request with Jackson's `objectMapper.readerForUpdating(current).readValue(body)`, and save the result.
  Fields not in the request keep their saved values. The admin SPA (Settings page) already sends the full object, so its behaviour does not change.
- **Why not boxed types:** changing `ChatbotConfig` to `Integer`/`Boolean` would mean null-checking every `isIncludeCartLinks()`-style call in the chatbot code (`ChatAgentService`, the tool handlers, `ChatbotConfigEntity.fromConfig`). That is a wider change touching the live prompt-building path. The merge approach touches only the two write endpoints.
- **Safety net:** in `updateConfig`, refuse to save `maxSearchResults < 1` (return 400 with a clear message). A search limit of 0 is never valid, and this would have blocked yesterday's wipe outright.
- **Files:** `controller/ConfigController.java` (the two methods); `service/ChatbotConfigService.java` (validation; `updateEntityFromConfig` unchanged); a new unit test (`src/test/java/.../ChatbotConfigServiceTest` or a `@WebMvcTest` for the controller).
- **Time saved:** indirect but real. With search limit 0, every `search_products` returns nothing and the bot burns extra tool calls retrying. It also protects every later config change in this plan (items 1a and 2 change config).
- **Risk to live chat:** very low. Only the admin write endpoints change; the chat path is not touched. The worst case is a config save that fails with an error, while the saved config stays as it was.
- **Local test:**
  1. Unit test: start from a full config, PUT `{"modelName":"claude-sonnet-5-5"}`, and assert every other field is unchanged.
  2. Assert that `{"maxSearchResults":0}` is rejected.
  3. Assert that a full-body PUT (what the SPA sends) still saves every field.
  4. Run the same three checks by hand against a local backend with curl, then save once from the local Settings page.
- **Post-deploy check:** `GET /api/config/chatbot` before and after one harmless save from Settings shows identical values; the logs show `search_products ... Max Results: 5` (or whatever the restored value is).
- **Rollback:** redeploy the previous Railway deployment. No schema change and no data change.
- **Size:** S (~40 lines plus tests, half a day).
- **Overlap:** the reliability plan may list the same bug. This is the agreed code fix; keep one copy when merging.

### Item 0 — Measure every turn (do first)

- **What:** in `ChatAgentService`, log one line per finished turn: path (stream or blocking), number of Claude calls, total Claude ms, tool ms by tool name, delegation ms, time to first token (stream), total ms, and cache-read tokens.
  In `ShopifyChatController.streamMessage`, record a `ChatAnalytics` row at the end of the stream (success or error), the same way the blocking path does, with `responseTimeMs`, and log client cancels (`doOnCancel`) so 499s are counted.
- **Files:** `service/ChatAgentService.java` (StreamState plus the end of `afterStream` and `handleClaudeResponse`); `controller/ShopifyChatController.java` (stream analytics plus a cancel hook).
- **Time saved:** none directly. It gives the before/after numbers for every other item.
- **Risk:** very low. These are logging and an analytics insert, both already wrapped in try/catch on the blocking path; the stream path will use the same pattern. Must not block the stream: insert after `done` and swallow errors.
- **Local test:** `mvn -q compile`; run locally against a local DB; send one blocking and one stream message with curl; check the log line and the new `chat_analytics` row. Kill a curl stream mid-way and check the cancel is logged.
- **Post-deploy check:** within 1 h of real traffic, `railway logs --filter "CHAT_TURN"` shows a line per chat; analytics rows appear for streamed chats.
- **Rollback:** redeploy the previous Railway deployment (one click). No schema change.
- **Size:** S (~80 lines, half a day).

### Item 1 — Stop needless delegations (biggest win)

The main bot calls "experts" for routine stock, price and specials questions. Those experts mostly **have no tools** (SPECIALS FINDER id 19; STOCK AGENT id 16 inside workflow 2), or their only tool is broken (PAINT EXPERT id 15: `web_search` fails to load). So each delegation costs 4–8 s and adds no store data. The bot then calls `check_inventory` anyway.

**Stage 1a — config only, no code (reversible in seconds):**
- In Settings, remove workflow 2 ("Stock Check Assistant") from linked workflows, and SPECIALS FINDER (19) from linked agents. `check_inventory` and `get_promotions` already do those jobs with real data.
- Live today: `linkedAgentIds=[15,17,18,19,20,21,22]`, `linkedWorkflowIds=[2]`.
- **Time saved:** removes 1–3 delegations from most slow turns: **~4–12 s.**
- **Risk:** low. Fewer "expert" paragraphs, but those were general knowledge, not store data.
- **Overlap:** smarter-answers plan (it may want experts kept and given real tools). Agree with that plan before un-linking.

**Stage 1b — prompt rule (small code change):**
- Add to the existing `buildBatchSearchGuidance()`-style appended guidance in `ChatAgentService`: "Use catalogue tools (search/browse/check_inventory/get_promotions) for stock, price, availability and specials. Delegate ONLY for advice the catalogue cannot answer (techniques, compatibility, how-to), at most ONE delegation per reply, and never in the same turn as a catalogue search for the same item."
- The rule is appended in code (the same pattern as the batch-search and preorder rules), so it applies to the DB prompt and the config prompt alike.
- **Files:** `service/ChatAgentService.java` (one new appended string). Possibly the `delegate_to_agent` tool description in `handler/tool/DelegateToAgentChatToolHandler.java`.
- **Time saved:** a further ~4–8 s on the turns that still delegate twice or more.
- **Risk:** low–medium. Prompt changes can shift answer style. Test with a fixed question set first.
- **Local test:** run a fixed set of 10 real questions (from chat logs: "tamiya paint", "sci-fi kits", "Yokomo GT1", "what thinner for airbrushing", "gundam kits in stock", a greeting, an order question) before and after. Compare the item-0 log lines (delegation count, total ms) and read the answers side by side.
- **Post-deploy check:** delegation share of tool calls drops from ~45 % toward under 15 %; median and p90 total ms from item 0; spot-read 10 real chats for answer quality and cart links.
- **Rollback:** 1a, re-link in Settings. 1b, redeploy the previous Railway deployment.
- **Size:** 1a XS (5 min, no deploy). 1b XS–S (~15 lines plus a test pass, 2 h).

### Item 2 — Lower the storefront tool-loop ceiling and stream the wrap-up

- **What:** the storefront ceiling is 15 Claude calls per turn. Observed slow turns use 5–7, and every call adds 1.5–3.5 s. Make the ceiling a config value (default 15, unchanged), then set it to **6** for the storefront once item 0 shows the real distribution.
  At the ceiling the existing "summarize with what you have" call runs. Today, in the stream path that call is **not streamed**: the customer sees nothing until it finishes. Stream it like a normal turn (the same request without tools).
- **Files:** `service/ChatAgentService.java` (`iteration >= 15` in `streamClaudeWithTools` and `callClaudeWithTools`; the no-tools wrap-up), `application.yml` (`chatbot.max-tool-iterations: ${CHATBOT_MAX_TOOL_ITERATIONS:15}`).
- **Time saved:** caps the worst case. A 7-call turn becomes 6 plus a streamed wrap-up, and runaway turns stop at ~20 s instead of 40 s+. Small effect on the median.
- **Risk:** low–medium. Too low a cap gives thinner answers on big "shopping list" questions; batch tools reduce this. The env var lets you raise it without a deploy.
- **Local test:** set the env var to 2 locally and ask a multi-product question. Confirm the streamed wrap-up arrives token by token and still includes products and links.
- **Post-deploy check:** item-0 log lines show how many turns hit the cap (target < 5 %); look for any "having trouble" replies.
- **Rollback:** set `CHATBOT_MAX_TOOL_ITERATIONS=15` in Railway (restart only), or redeploy the previous deployment.
- **Size:** S (~40 lines, half a day).

### Item 3 — Widget: honest long-wait handling, no silent double wait

Status events already exist. The gap is what happens on long waits and on failures.
- **(a)** After ~15 s with no answer text, show "This one needs a few more seconds — still working on it" under the status line. Customers who see an explanation are less likely to close the chat (the 499s).
- **(b)** If the stream fails **after more than ~20 s**, do not silently re-run the whole question on the blocking endpoint (a second full wait). Show "That took too long — tap to try again" with a retry button. Keep the automatic fallback only for quick failures (under ~5 s, such as the stream being blocked by a network).
- **(c)** Server side (optional): emit one `status` event immediately when the stream opens, so the first status line comes from the server, not just the client rotation. Trivial.
- **Files:** `shopify-theme-extension/assets/ai-search-client.js` (`streamMessage` caller ~lines 365–440; typing/status helpers); for (c), `ChatAgentService.streamChat` (`startWith(sseStatus(...))`).
- **Time saved:** no faster answers. It removes the doubled 30 s + 30 s waits and should reduce 499s.
- **Risk:** low. It is client-side and the existing fallback stays for quick failures. But it ships through a Shopify extension deploy, not Railway.
- **Local test:** a theme preview on a dev or unpublished theme. Simulate a slow stream (point the widget at a local backend with an artificial delay) and a mid-stream failure (stop the local backend). Check the messages and the retry button on mobile width.
- **Post-deploy check:** fewer blocking `/message` calls that immediately follow a failed `/message/stream` from the same IP; the 499 count (item 0) trends down over 2 weeks.
- **Rollback:** `shopify-theme-extension/EMERGENCY-ROLLBACK.md` (previous extension version). Server part (c): redeploy the previous Railway deployment.
- **Size:** S (~60 lines JS, half a day, plus the extension release).

### Item 4 — Faster model for the experts that remain

- **What:** delegated agents run on Sonnet 4.5 / 4.6 (some Haiku). Each takes 4–8 s for a ~300–500-token note the main bot then rewrites. Switch the remaining linked storefront agents (15, 17, 18, 20, 21, 22, after item 1) to `claude-haiku-4-5-20251001` in the agent editor.
- **Files:** none. This is the per-agent `model` field in the DB, edited in the admin UI.
- **Time saved:** ~2–4 s per delegation (Haiku is roughly 2× faster for short notes).
- **Risk:** low. Shallower expert notes; the main bot (Sonnet 5.5) still writes the reply. **Do not touch agent 23** (Product Enrichment, production, DB-only).
- **Local test:** none possible without a DB clone. Test in prod one agent at a time with the item-1 question set.
- **Post-deploy check:** delegation ms in the item-0 lines; read 5 replies that used each expert.
- **Rollback:** set the model back in the agent editor (seconds).
- **Size:** XS (10 min).

### Item 5 — Extend prompt caching to the conversation

- **What:** today only tools plus system prompt (~5.5k tokens) are cached. Each later call in the same turn re-sends the growing conversation (earlier tool results, often 2–4k tokens) uncached. Add a `cache_control` breakpoint on the last content block of the latest message, so call N+1 reads calls 1..N from cache. Also log cache hits in the stream path; today only the blocking path logs them, and item 0 covers this.
- **Files:** `service/ChatAgentService.java` (`buildStreamingRequestBody`, `callClaudeWithTools`; a helper that marks the last block, removing earlier marks so at most 4 breakpoints are used).
- **Time saved:** ~0.2–0.6 s per Claude call after the first, so ~1–2 s on a 6-call turn. It also cuts input cost.
- **Risk:** low. The format is the same GA feature already in use. Keep `anthropic-version: 2023-06-01`; **do not repeat the ae8ff75 mistake** of changing the version header. A bad breakpoint gives a 400, which the stream path turns into the blocking fallback.
- **Local test:** a local run with a real API key; check the logs show `cache_read_input_tokens` growing across calls in one turn; run the item-1 question set.
- **Post-deploy check:** cache-read tokens per call in the item-0 lines; no new `400` in `Error calling Claude API`.
- **Rollback:** redeploy the previous Railway deployment.
- **Size:** S (~30 lines, 2–3 h).

### Item 6 — Faster model for simple questions (on hold)

- **What:** send greetings, store-info and other no-product questions to Haiku 4.5, and everything else to Sonnet 5.5 (`chatbot_configs.model_name`).
- **Why on hold:**
  - Simple turns already finish in 4–6 s, so the gain is 1–3 s on turns that are not the problem.
  - Deciding which questions are "simple" needs either another model call (which costs time) or keyword rules (which mis-route).
  - Haiku is noticeably weaker at following the long link and format rules, which risks missing cart links.
  - It would also have to avoid the shared `setAnthropicModel` state (§0, point 6).
- **If wanted later:** keyword pre-check, only on the first message of a chat, with no tools; ~M size (1–2 days with testing).
- **Rollback would be:** a config flag off.

### Item 7 — Longer product-search cache (low value)

- **What:** raise `shopify.search.cache-ttl-ms` from 90 s to 5–10 min for catalogue searches. Stock is already read live by `check_inventory` and is not cached.
- **Time saved:** under 1 s per turn. Shopify calls are 0.4–1 s and already run in parallel.
- **Risk:** low; new products and price changes show up a few minutes later.
- **Rollback:** an env var.
- **Size:** XS. Only worth it as a by-product.

---

## 4. Suggested rollout (staged, each one revertible on its own)

1. **Today, no deploy:** restore the wiped config (§0, point 1) by saving the **full** config from the Settings page, never with a partial API call until item R is live.
2. **Deploy R (Railway):** item R on its own, so it can be reverted on its own. After that, un-link workflow 2 and agent 19 (item 1a), once agreed with the smarter-answers plan.
3. **Deploy A (Railway):** item 0 (measurement), plus item 3c. Watch for 2–3 days to get a baseline.
4. **Deploy B (Railway):** item 1b (prompt rule) and item 2 (configurable ceiling, left at 15). Then set the env var to 6 once the data agrees.
5. **Extension release:** items 3a and 3b.
6. **Admin UI:** item 4 (expert models), one agent at a time.
7. **Deploy C (Railway):** item 5, if the item-0 data shows the calls after the first are input-heavy.

**Success measure (from item 0):** p90 turn time under 20 s (today's slow turns run 25–47 s), median under 8 s, under 15 % of tool calls being delegations, and 499s down by half within a month.

## 5. Handed to other plans

- Partial-update bug: now planned here as item R (agreed with the parent session); reliability plan should reference it, not duplicate it.
- `web_search` tool fails to load for the PAINT EXPERT agent ("Could not load tool handler WebSearchToolHandler") → **reliability / smarter answers**.
- `browse_products` with `category: "PAINT"` + vendor Tamiya returns 0 results (product_type naming mismatch), causing retry iterations → **smarter answers**.
- Shared singleton state for model and shop context → **reliability**.
