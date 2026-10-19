# Plan 3 — Answer-quality feedback (shopper 👍/👎 + Nic's review of bad answers)

Status: **PLAN ONLY, nothing built.** Written 2026-10-19 against branch `claude/admin-api-auth`.
Part of the five-part chatbot improvement set (reliability, speed, smarter answers, feedback, …); the parent session merges them.

---

## 0. Read this first — questions and risks for Nic

### Questions (need your answer before building)

1. **How was the widget's JavaScript last deployed?** There is no `shopify.app.toml` in the repo, and `snippets/ai-search-assets.liquid` loads `ai-search-client.js` with `asset_url`. That means the file lives in your **theme's Assets** and is pasted in through *Online Store → Themes → Edit code*, not pushed with the Shopify CLI. Is that how the July 2026 streaming changes went live? If someone else (e.g. Naihra) does theme edits, they need to be in the loop for step 4.
2. **Thumbs only, or thumbs + an optional comment?** My recommendation: 👍/👎 on every answer, and after a 👎 a small optional box ("What went wrong?", 300 characters max). Comments are the most useful part for you, but customers may type personal details (email, phone, order number) into them. That's personal information under the Privacy Act. It sits beside the IP address the table already stores, so it is not a new *kind* of risk, but it adds to it.
3. **Do you want an email or Slack alert on 👎?** Not in this plan, which is review-in-admin only. It's easy to add later.
4. **Is Railway running one copy of the app (one replica)?** The rate limit below is kept in memory. With more than one replica each copy counts separately. That's still fine, just looser.

### Risks (and what the plan does about each)

| # | Risk | Severity | Mitigation |
|---|------|----------|------------|
| R1 | **The streaming chat path records nothing in `chat_analytics` today.** The widget streams by default (`/message/stream`), and `ShopifyChatController.streamMessage` never calls `trackInteraction`. Only the rarer blocking fallback is logged. So most real conversations since the July 2026 streaming launch are missing from Logs/Chat Analytics, and a 👎 would have no row to attach to. | High (blocks the feature; also means current analytics under-count) | Step 1 adds stream logging in the **controller only**, as a side-effect after the stream finishes. The stream frames sent to the shopper are unchanged. A DB failure is caught and logged, never surfaced. |
| R2 | Touching the live chat endpoints at all. | High impact, low likelihood | Backend changes are additive: one new optional request field, a new route, and logging after the fact. Response shapes are **unchanged** (see §2 for why). Each stage is a separate deploy that can be reverted. |
| R3 | Abuse of a public, unauthenticated endpoint: spam ratings, enumerating rows, flooding comments. | Medium | The feedback must match **shop + sessionId + messageId** of an existing row. Two random UUIDs held only in that shopper's browser can't be guessed. Rows older than 24h are rejected, input is validated, and requests are rate-limited per IP and per session. See §3. |
| R4 | Extra DB write per streamed turn. The Sep–Oct 2026 Hikari pool outage makes this sensitive. | Medium | One `INSERT` per turn, the same as the blocking path already does. It runs on `Schedulers.boundedElastic()` **after** the `done` frame is sent, so it can't delay the answer. No new long-held connections. |
| R5 | Theme JS edit breaks the widget (a JS error stops chat on the storefront). | Medium | Edit a **duplicate theme**, preview, then publish. The rating buttons are behind a new block setting (default **off**), which is a no-code kill switch in the theme customizer. All new widget code is wrapped in `try/catch`, so a feedback failure can't affect sending or rendering messages. |
| R6 | Merge collision with sibling plans (reliability / speed) that also edit `ShopifyChatController.streamMessage`, `ai-search-client.js`, and take Flyway numbers. | Medium | See §8 "Coordination". Build this after, or rebased onto, those changes. Claim the migration number at build time, not now. |
| R7 | The existing `PUT /api/analytics/chat/{id}/feedback` has no shop check, so any admin key can rate any row. | Low (admin-only on this branch) | Left as-is and admin-only. The widget never uses it. Noted as a follow-up. |

---

## 1. What exists today (verified in code)

- **Table** `chat_analytics` (V008) already has `user_feedback VARCHAR(50)` ('positive' / 'negative' / 'neutral') and an index on it. There is no comment column, no feedback timestamp, and no per-message id.
- **Entity** `model/ChatAnalytics.java` maps `userFeedback`, and `ChatAnalytics.Feedback` holds the constants.
- **Service** `ChatAnalyticsService.updateUserFeedback(id, feedback)` (no shop check), `getFeedbackSummary(shop)`, `getNegativeFeedbackInteractions(shop)`; `getComprehensiveReport` already includes `feedbackSummary` + `recentNegativeFeedbackCount`.
- **Repository** `findFeedbackSummaryByShop`, `findByShopAndUserFeedbackOrderByCreatedAtDesc` (unpaged).
- **Admin endpoints** (all under `/api/analytics/**` → admin key required by `AdminAuthFilter`):
  `PUT /api/analytics/chat/{analyticsId}/feedback`, `GET /api/analytics/chat/feedback-summary?shop=`, `GET /api/analytics/chat/session/{sessionId}`.
  `GET /api/logs/chat?shop=&page=&size=` (`ChatLogsController`) returns paged rows, with no filters.
- **Chat responses do NOT return an analytics id.**
  - Blocking `POST /message` returns `{response, role, timestamp}`. `trackSuccessfulInteraction` saves the row but throws away the saved id.
  - Streaming `POST /message/stream` ends with `event: done` / `data: {}` (emitted inside `ChatAgentService.sseDone()`), and **saves no row at all** (R1).
- **Widget** `assets/ai-search-client.js`: `AISearchClient` already has a per-tab `sessionId` (UUID in `sessionStorage`, sent as `sessionId` in the request body) and a `userIdentifier` in `localStorage`. `AISearchModal.sendMessage` tries streaming first and falls back to blocking. The widget makes no feedback calls.
- **CORS** on `ShopifyChatController` allows `GET, POST, OPTIONS` only, from hearnshobbies.com, www., and `*.myshopify.com`. The new route must be a **POST** under the same controller to inherit it (PUT would be blocked by CORS).
- **Auth**: `AdminAuthFilter.PUBLIC_PREFIXES` already contains `/api/shopify/chat/`, so a new `/api/shopify/chat/feedback` is public with **no filter change**.
- **Admin UI**: `Logs.jsx` already shows "User Feedback" in each row's expanded detail. `ChatAnalytics.jsx` shows summary/daily/recent and session drill-down, but has no feedback card. Both use plain `fetch`, which `adminAuth.js` patches to send `X-API-Key`.

---

## 2. Design decision: how the widget identifies "this answer"

**Option A — return the DB id** (`analyticsId` in the blocking JSON and in the stream's `done` frame).
Against: the controller would have to *wait for the DB insert before sending `done`*, which adds latency and pool pressure to the live path (R4). It changes response shapes. The `done` frame is built inside `ChatAgentService`, so we'd have to rewrite it in the controller. Sequential ids are also guessable.

**Option B — the widget generates a `messageId` (UUID) per question and sends it in the request. Recommended.**
- The backend stores it in a new column `client_message_id` alongside the existing `session_id`.
- **No response shape changes, and `done` is not delayed.** The row can be written after the stream ends.
- Feedback is keyed by `(shop, sessionId, messageId)`. Both UUIDs live only in that shopper's tab, so there's nothing to enumerate.
- Old widget versions (no `messageId`) keep working; their rows just can't be rated.
- On stream→blocking fallback the widget reuses the same `messageId`. Feedback updates the most recent *successful* row with that id.

Minor edge: a shopper clicks 👍 within a few hundred ms of `done`, before the async insert commits. The endpoint returns 404, and the widget retries once after 1.5s, then gives up silently.

---

## 3. Backend changes

### 3.1 Migration `V0xx__chat_feedback_fields.sql` (next free number at build time; currently V023)
```sql
ALTER TABLE chat_analytics ADD COLUMN IF NOT EXISTS client_message_id VARCHAR(64);
ALTER TABLE chat_analytics ADD COLUMN IF NOT EXISTS feedback_comment  TEXT;
ALTER TABLE chat_analytics ADD COLUMN IF NOT EXISTS feedback_at       TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_chat_analytics_session_msg
    ON chat_analytics(session_id, client_message_id) WHERE client_message_id IS NOT NULL;
```
Purely additive and nullable, so it's safe on the live table. The partial index stays tiny. Old code ignores the new columns, so a code rollback doesn't need a DB rollback.

Entity: add `clientMessageId`, `feedbackComment`, `feedbackAt` to `ChatAnalytics.java`. `ChatRequest.java`: add an optional `messageId`.

### 3.2 Log streamed turns (fixes R1) — `ShopifyChatController.streamMessage` only
- Wrap the existing `content` flux with `doOnNext` that appends `token` text to a local `StringBuilder` (parse `{"text":…}` with the existing ObjectMapper). Use `doOnComplete` / `doOnCancel` / `doOnError` to fire **one** tracking call:
  - `done` seen → `wasSuccessful=true`, `assistantResponse=accumulated`.
  - `error` seen / exception → `wasSuccessful=false`, `errorMessage`.
  - Client cancelled (tab closed) → log as successful with whatever streamed, plus `errorMessage="client disconnected"`.
- The tracking call runs via `Mono.fromRunnable(...).subscribeOn(Schedulers.boundedElastic()).subscribe()` inside `try/catch`. It is fire-and-forget and **cannot** alter, delay, or error the stream the shopper sees.
- Token counts / cost: not available in the stream path (`ChatAgentService` doesn't surface them), so stored as null. Cost analytics stay under-counted for streamed turns. That's a separate, known gap, not fixed here.
- Also record `userAgent`, IP, and `Referer` (add the same `@RequestHeader` params the blocking method has).
- `ChatAgentService` stays **untouched**.

Blocking `sendMessage`: pass `request.getMessageId()` into `trackSuccessfulInteraction` / `trackFailedInteractionWithShop` (one extra builder line each). The response is unchanged.

### 3.3 New public route `POST /api/shopify/chat/feedback?shop=…`
In `ShopifyChatController`, which inherits its CORS, delegating to a new `service/ChatFeedbackService.java`.

Request body (anything else is ignored):
```json
{ "sessionId": "uuid", "messageId": "uuid", "rating": "positive|negative", "comment": "optional" }
```
Checks, in order. Each failure returns a short generic JSON error and never echoes internals:
1. `shopifyShopService.isShopActive(shop)` → else 404.
2. `sessionId`, `messageId` match a UUID pattern (≤64 chars) → else 400.
3. `rating` ∈ {positive, negative} → else 400. ("neutral" is not offered to shoppers.)
4. `comment`: trimmed, ≤ 300 chars (reject longer with 400 rather than truncate), control characters stripped. Only accepted with `negative`; ignored with positive.
5. Rate limit (below) → 429.
6. Look up the newest row where `shop = shop AND session_id = sessionId AND client_message_id = messageId AND was_successful = true AND created_at > now() − 24h` → else 404.
7. Update **only** `user_feedback`, `feedback_comment` (null on 👍), `feedback_at`. Changing your mind (👎→👍) is allowed; the last write wins.

Response: `200 {"ok":true}`. No row data is returned.

**Rate limit** — small in-memory limiter class `util/FeedbackRateLimiter` (fixed window, `ConcurrentHashMap` with expiry sweep; no new library):
- per client IP (first `X-Forwarded-For` hop, the same helper as the controller): 30 / 10 min
- per sessionId: 20 / 10 min
- per (sessionId, messageId): 5 changes total

The limits are generous for a real shopper and useless for spam. The IP header can be spoofed, but the session/message caps and the row-match requirement still hold. The map is bounded (e.g. evict entries older than 10 min, cap at 50k keys).

Logging: one INFO line per accepted feedback (`shop, analyticsId, rating, hasComment`). The comment text is never logged.

### 3.4 Admin review endpoints (admin key, already enforced by the filter)
- `GET /api/logs/chat?shop=&page=&size=&feedback=negative|positive|any` — add an optional `feedback` param. Backed by a new paged repository method `findByShopAndUserFeedback(shop, feedback, Pageable)` (and `…UserFeedbackIsNotNull` for `any`). Without the param, behavior is identical to today.
- No new endpoint is needed for the conversation view. `GET /api/analytics/chat/session/{sessionId}` already returns the whole session in order, which shows what led up to a bad answer.

---

## 4. Widget changes (`shopify-theme-extension/`)

`blocks/search-modal.liquid`
- New schema setting `{"type":"checkbox","id":"enable_feedback","label":"Show 👍/👎 on answers","default":false}`.
- New attribute `data-feedback-enabled="{{ block.settings.enable_feedback }}"`.

`assets/ai-search-client.js`
- `AISearchClient`: generate `messageId = this.generateUUID()` per question in both `sendMessage` and `streamMessage` (one shared id when the modal falls back). Add it to the request body, and add a `sendFeedback(messageId, rating, comment)` that POSTs to `apiUrl.replace(/\/message$/, '/feedback')?shop=…`, the same derivation as `streamUrl`. It swallows every error and retries once on 404 after 1.5s.
- `AISearchModal`: after an assistant bubble is final (stream `onDone`, the kept-partial branch, and the blocking `addMessage`), if the feature is enabled and a `messageId` exists, append a small row: "Was this helpful? 👍 👎".
  - Click → buttons disabled, selected one highlighted, "Thanks!" shown.
  - 👎 → reveals an optional textarea (maxlength 300) + "Send" button. Pressing Send makes a second call with the comment.
  - Not shown on error bubbles or on the welcome message.
  - All of it in a `try { … } catch (e) { console.warn(…) }` so it can never break message rendering.
- `assets/ai-search-modal-styles.css`: ~30 lines for `.ai-feedback-row` (muted, small, keyboard-focusable buttons with `aria-label`s).

Estimated diff: ~90 lines JS, ~6 lines liquid, ~30 lines CSS.

---

## 5. How Nic reviews bad answers

**Logs page (`frontend/src/pages/Logs.jsx`)** is the main place.
- Filter dropdown above the table: *All / 👎 Negative / 👍 Positive / Any rating* → passes `feedback=` to `/api/logs/chat`.
- Rating icon column in the table, and the shopper's comment shown in the expanded detail. The "User Feedback" field already exists there.
- "View whole conversation" link in the expanded row → calls `/api/analytics/chat/session/{sessionId}` and shows the turns in order. This is the same call `ChatAnalytics.jsx` already makes, so it can reuse that rendering.
- Support a `?feedback=negative` URL param so it can be linked to directly.

**Chat Analytics page (`ChatAnalytics.jsx`)**
- One new card: "Answer ratings — last 30 days: 👍 N / 👎 M (x% helpful)" from the existing `/api/analytics/chat/feedback-summary`. Its "Review 👎" button opens `Logs?feedback=negative`.

Suggested routine: once a week, open Logs → 👎, read each conversation, and decide whether it's a prompt fix (Settings → system prompt), a missing product data issue, or a tool problem. A field to mark a 👎 "reviewed" is a possible follow-up; it's not needed for v1.

Frontend deploy: `cd frontend && npm run build:deploy`, then commit the built static files, as in commit 585eb28.

Estimated diff: ~70 lines Logs.jsx, ~30 lines ChatAnalytics.jsx.

---

## 6. Staged rollout (each stage separately deployable and revertible)

| Stage | What ships | Visible to shoppers? | Verify | Rollback |
|-------|-----------|----------------------|--------|----------|
| **1. Backend: migration + stream logging + `messageId` field** | §3.1, §3.2 | No | Railway logs clean. Send a few storefront chats and see the rows appear in Logs with `client_message_id` null, since the old widget doesn't send it. Stream behavior identical (timing, fallback). Hikari metrics normal for 24h. | Railway → redeploy previous build. The migration columns stay; they're harmless. |
| **2. Backend: `/api/shopify/chat/feedback` + Logs filter param** | §3.3, §3.4 | No | `curl` tests against prod: wrong shop → 404, random ids → 404, bad rating → 400, burst → 429, valid id from a test chat → 200, then the row shows the rating. | Redeploy previous build. |
| **3. Admin UI** | §5 | No | Filter + card work with the stage-2 test rating. | Redeploy previous build. |
| **4. Widget** (theme Assets + block) | §4 | Only once the setting is ticked | (a) Duplicate the live theme. (b) Paste the new `ai-search-client.js`, CSS, and `search-modal.liquid` into the **duplicate**. (c) Preview the duplicate: chat works, streaming works, fallback works, thumbs post, 👎 comment posts, mobile layout OK, no console errors, plus the README post-deploy checklist (gift cards, menus). (d) Publish the duplicate with `enable_feedback` **off**, check for a day, then tick it on. | **Instant:** untick "Show 👍/👎" in the customizer. **Full:** re-publish the previous theme (Shopify keeps it under Themes), or restore the file from the code editor's "Older versions". Worst case: `EMERGENCY-ROLLBACK.md`. |

Order matters: the backend must be live before the widget sends `messageId`. Spring ignores unknown JSON fields, so the new widget is also safe against an *old* backend; feedback calls would just 404 silently.

---

## 7. Tests

Backend (JUnit 5 + Mockito, alongside the existing `src/test/java/com/shopify/api/...`):
- `ChatFeedbackServiceTest` — happy path updates only rating/comment/timestamp; wrong shop, wrong session, wrong messageId, row >24h old, unsuccessful row → not found; rating "neutral"/garbage → rejected; comment 301 chars → rejected; comment ignored on positive; 👎→👍 overwrite clears the comment.
- `FeedbackRateLimiterTest` — per-IP, per-session, and per-message caps; window expiry; map eviction bound.
- `ShopifyChatControllerFeedbackTest` (`@WebMvcTest` style, as in `WorkflowControllerPublicTest`) — status codes, no row data in the response, CORS preflight `OPTIONS` from `https://hearnshobbies.com` allowed for POST.
- `ShopifyChatControllerStreamTrackingTest` — fake `ChatAgentService.streamChat` flux (token, token, done): the frames reach the client **byte-identical and in order**. The tracking call receives the concatenated text. **If `trackInteraction` throws, the stream still completes normally** (the key safety test). Also cancel and error cases.
- `AdminAuthFilterTest` — add one assertion that `/api/shopify/chat/feedback` is public and `/api/logs/chat?feedback=negative` still needs admin.
- `mvn -q compile` + full `mvn test` before each stage.

Widget: no JS test harness exists in the repo. Use a manual checklist on the duplicate theme (stage 4c), including DevTools "offline" for the feedback call (chat must be unaffected) and the backend returning 500 for feedback.

---

## 8. Coordination with the sibling plans

- **Reliability / speed plans** are likely to edit `ShopifyChatController.streamMessage` (heartbeat, timeouts) and the widget's streaming/fallback code in `ai-search-client.js`. §3.2 and §4 touch the same spots. Recommendation: land those first, then rebase this onto them. Or have one session own all edits to `streamMessage`.
- **Flyway number:** pick the next free `V0xx` at build time, since several plans may each want "V023".
- **Smarter-answers plan:** once ratings exist, 👎 rows are a ready-made test set for prompt changes (Prompt Testing page). Worth linking.

---

## 9. Size

| Piece | Est. size |
|-------|-----------|
| Migration + entity/request fields | ~25 lines |
| Stream logging in controller | ~60 lines |
| Feedback route + service + rate limiter | ~180 lines |
| Logs filter param + repo methods | ~30 lines |
| Backend tests | ~300 lines |
| Widget JS/CSS/liquid | ~125 lines |
| Admin UI | ~100 lines |

**Overall: medium. About 1–1.5 days of build + test, plus ~1 day of soak between stages 1 and 4.** The only parts with live-chat risk are stage 1 (stream logging) and stage 4 (widget). Both have instant rollbacks.

## 10. Follow-ups (not in this plan)

- Admin-only `PUT /api/analytics/chat/{id}/feedback` should check the row's shop (R7). Or delete it if nothing uses it.
- Surface token counts/cost from the stream path so analytics cost is accurate.
- "Reviewed" flag / notes on 👎 rows; weekly 👎 digest email.
- Shop-context: `ChatLogsController` defaults `shop` to hearnshobbies — fine today, revisit for multi-shop.
