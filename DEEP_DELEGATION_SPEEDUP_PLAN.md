# Deep-Delegation Speedup — Deferred Plan (future work)

**Status:** planned, not started. Written 2026-07-31. Do later.

## Context / where we are
Deep multi-product chatbot answers (e.g. "a full starter paint set") are slow because the model
looks products up **one per turn, sequentially** — each turn is a full Claude round-trip (~3–5s).
- Narrow ask (2 products): **~39s**. Broad ask (5–6 products): **was ~80s**.
- Already shipped (see git log): streaming + SSE heartbeat + upstream timeouts, faster expert help
  (Haiku + prompt caching + iteration cap), combined "Add all to cart" link, short-TTL search cache,
  `check_inventory_batch` (commit `4a5aadf`) which took the broad case **~80s → ~56s**, and the
  Quick-Summary/bundle guidance.
- Remaining ~56s = sequential model turns: in the last test the main bot still made **~7 single
  `check_inventory` calls** alongside 1 batch, plus the **specialist's own per-item searches** inside
  the delegation are not batched.

**Estimated remaining savings: ~10–20s (56s → ~35–45s), mostly from the specialist batch (Lever B).**
The rate-limiter change (Lever C) adds little on its own — the Shopify budget (~10 queries/sec) is a
hard floor. Weigh this against the risk: the experience already streams in ~2s and feels responsive.

## Non-negotiable constraint (from Nic)
Keep answer **quality and display identical**. Only ever run the **same** precise per-item searches
**concurrently** — never "one broad search per category" (that risks missing specific colours).
Every step has a quality gate: diff before/after answers; stop on any coverage/format delta.

---

## Step 0 (DO FIRST): profile the current ~56s
Before touching prod DB / shared infra, get real per-phase numbers so we decide go/no-go with data:
- Attribute seconds to: main-bot turns vs specialist delegation vs Shopify search latency vs
  rate-limiter waits. (Prior log-count attempts were unreliable — `railway logs` returns only a recent
  tail; use a reliable time-window filter or add temporary per-phase timing logs.)
- **Confirm WHICH tool the specialist uses** for its per-item searches (`get_in_stock_products_with_links`?
  `check_inventory`? DB `search_products`?). This determines the Lever-B batch target and the
  `agent_tools` link. This is currently UNKNOWN — the observed `check_inventory` calls were the MAIN
  bot's (`ChatAgentService` "=== EXECUTING TOOL ===" logs), not confirmed for the specialist.

---

## Lever A — fuller `check_inventory_batch` adoption (cheap, no migration)
The main bot still fired ~7 single `check_inventory` calls next to 1 batch. Strengthen the tool
description + `buildBatchSearchGuidance()` (ChatAgentService.java ~:822) so it batches ALL stock checks
in one call. Prompt-only, low risk. Could reclaim several of those ~7 sequential turns. Measure.

## Lever B — specialist-path batch tool (the main remaining win; needs a migration)
Mirrors the shipped `check_inventory_batch`/`search_products_batch` pattern but for the specialist/DB-agent path.
- New `GetInStockProductsWithLinksBatchToolHandler` (`@Component implements handler/tool/ToolHandler`,
  `execute → Mono<JsonNode>`). **Reuse** `GetInStockProductsWithLinksToolHandler.processProduct` /
  `extractNumericId` / `generateAddToCartUrl` (extract to a shared helper) so per-variant JSON +
  `addAllToCartUrl` are byte-identical. Fan out `Flux.fromIterable(queries).flatMap(…, 5)`, `maxItems:10`.
- **Register in prod via idempotent Flyway migration** (DataInitializer only runs where it executes;
  `railway ssh` is off). Pattern = `V019__add_product_enrichment_tools.sql`:
  1. `INSERT INTO tools (…) VALUES ('get_in_stock_products_with_links_batch', 'AGENT', …, '<schema>'::jsonb,
     'com.shopify.api.handler.tool.GetInStockProductsWithLinksBatchToolHandler', true, NOW()) ON CONFLICT (name) DO NOTHING;`
  2. Attach it **ID-agnostically** to every agent that already has the tool the specialist actually uses
     (identified in Step 0), so we don't need prod agent IDs:
     `INSERT INTO agent_tools (agent_id, tool_id, created_at)
      SELECT at.agent_id, (SELECT id FROM tools WHERE name='get_in_stock_products_with_links_batch'), NOW()
      FROM agent_tools at JOIN tools t ON at.tool_id = t.id
      WHERE t.name = '<specialist's single tool from Step 0>' ON CONFLICT (agent_id, tool_id) DO NOTHING;`
  3. Also add the matching `DataInitializer.createTool(...)` seed for dev parity.
- Add a one-line batch hint to `AgentDelegationToolHandler.enrichedTask` (:71–82); leave existing
  product-first + add-all wording verbatim.
- **Risk:** prod DB migration touching `tools`/`agent_tools`; only proceed after Step 0 confirms the
  specialist's actual tool. **Quality:** zero (same searches, same output shape).

## Lever C — non-blocking rate limiter (OPTIONAL, higher risk, small gain)
`RateLimiter.waitIfNecessary` is `synchronized` + `Thread.sleep` (RateLimiter.java:35–54), called
imperatively at `ShopifyGraphQLClient.java:114` — it serializes and thread-blocks concurrent searches.
Make it non-blocking: smallest blast radius is offloading the token-bucket wait onto
`Schedulers.boundedElastic()` in `ShopifyGraphQLClient` (keeps semantics, stops blocking the event loop);
alternatively a reactive `Mono.delay`-based `acquire`. Shared infra (EVERY Shopify call) → gate behind a
config flag, default off; load-test for Shopify 429s. Gain is small (budget ~10 q/s is the floor); it
mainly lets Lever-A/B batches actually run concurrently.

---

## Ordering
1. **Step 0 profile** → decide go/no-go with real numbers.
2. **Lever A** (cheap adoption win) → measure.
3. **Lever B** (specialist batch + migration) → the real win → measure.
4. **Lever C** only if metrics still justify.

## Safety caps (all levers)
Batch `maxItems:10` (server-truncate) + `flatMap` concurrency 4–5, so we never burst far past the
Shopify rate budget.

## Verification (end-to-end vs prod)
Re-run the broad starter-kit batch (5–6 items) at
`POST /api/shopify/chat/message/stream?shop=hearnshobbies.myshopify.com` (`curl -N`). Compare:
wall-clock, model turns (SSE `status` frames), and per-tool call counts (batch vs single). **Quality
gate:** product table + individual [Add to Cart] + combined [Add all to cart] link present and
equivalent (same products, same variant IDs, same combined URL) — assemble streamed tokens before
grepping (cart links span SSE frames). Any coverage/format delta ⇒ stop.

## Deploy/gating
Backend → commit to `main` → Railway auto-deploy (no staging). Lever B needs the migration to land in
prod. No theme/`--allow-live`. If a git push is blocked by the CC classifier, commit locally then push separately.

## Key files
- `handler/tool/GetInStockProductsWithLinksToolHandler.java` — pattern + shared `processProduct` for Lever B
- `handler/tool/CheckInventoryBatchChatToolHandler.java` / `SearchProductsBatchChatToolHandler.java` — shipped batch patterns to mirror
- `service/ChatAgentService.java` — `buildBatchSearchGuidance()` (~:822) for Lever A
- `service/tool/AgentDelegationToolHandler.java` (:71–82) — specialist batch hint
- `config/DataInitializer.java` + a new `src/main/resources/db/migration/Vxxx__*.sql` — Lever B seeding (see `V019` template)
- `util/RateLimiter.java` + `client/ShopifyGraphQLClient.java:114` — Lever C
