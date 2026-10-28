# Closing vouchers (storefront chatbot)

The storefront bot can give a small, one-hour, single-use dollar voucher to get a
hesitating $150+ sale over the line, or to nudge a cart past $150 with an add-on.
The AI decides **when** and **how much**; the server enforces **every limit**.
Nothing typed in chat can change a limit.

**Off by default.** Migration V024 seeds `voucher_settings.enabled = false`. While it
is off, the bot's prompt and tool list are byte-for-byte what they were before, and the
feature makes no Shopify calls.

## Rules and where they are enforced

| # | Rule | Enforced by |
|---|------|-------------|
| 1 | Daily pool, default AUD 50, resets at midnight Australia/Melbourne; no carry-over | `voucher_pool_days` row per Melbourne day; one conditional `UPDATE … WHERE reserved + amount <= limit` (`VoucherPoolDayRepository.reserve`) |
| 2 | Fixed dollar amounts in $5 steps; the bot picks the amount | `VoucherRules.floorToStep` |
| 3 | Ceiling: floor(10% of eligible subtotal) to $5, then a cart-size tier: eligible cart $150–$499.99 → max $10 (`max_amount`); $500+ (`high_tier_subtotal`) → max $25 (`high_tier_max_amount`). Never more than the pool has left. | `VoucherRules.tierCap`, `VoucherService.offer` (step 6) and `VoucherStore.reserve` |
| 3b | Checkout enforces 10% and the tier too | the Shopify code's minimum subtotal is `max($150, amount / 10%)`, raised to $500 for any voucher above $10, so a model that invents a bigger cart still can't make the code usable on a smaller one |
| 4 | Minimum $150 of full-price, non-excluded items | recomputed from Shopify variant prices (never the model's numbers) and set as the code's `minimumRequirement.subtotal` |
| 5 | Valid 1 hour | `endsAt` on the Shopify code |
| 6 | Reserve on issue; settle ~65 min later | `VoucherReconcileScheduler` runs every minute and handles each code once expiry + 5 min grace has passed: used means REDEEMED (stays spent); unused means deleted in Shopify, EXPIRED, value returned to **that day's** pool. Also runs once at startup. |
| 7 | Single use (one code = one order); one per chat session; IP as a weak per-day limit | Shopify `usageLimit: 1`; `voucher_claims` primary key on `s:<session>`; `max_per_ip_per_day` (default 1; see "Client IP" below, and a missing IP is one shared, still-limited bucket). If the customer happens to give an email or is a known browser, that also counts once per day (`c:<day>:<hash>`, email aliases collapse to one inbox). Codes are **not** locked to a customer: the bot never asks for an email and no Shopify customer records are created (owner decision, Oct 2026). |
| 8 | No stacking; sale items excluded | `combinesWith` all false; variants with `compareAtPrice > price` don't count, and the code only applies to the eligible variants in the cart. When an issued offer's cart has sale items, the tool lists them (`already_discounted_items`) and the bot tells the customer they're already discounted and the voucher comes off the rest of the order. |
| 9 | Admin exclusion list (vendors, product types, tags), empty by default | `voucher_settings.exclusions`, case-insensitive |
| 10 | Kill switch, off by default | `voucher_settings.enabled`, plus env `VOUCHER_FEATURE_ALLOWED=false` as a second switch |
| 11 | Daily report plus a ~30% holdout | `GET /api/vouchers/report`; holdout is a stable hash of the session id (`holdout_pct`), flagged in `voucher_offers` |

Hard ceilings are code constants (`VoucherRules`), not settings. Nobody can exceed
them, the owner included, without a code change: settings pool ≤ $500, max share ≤ 20%,
voucher ≤ $100 (both tiers), voucher ≤ 20% of the eligible subtotal even with an owner override,
minimum ≥ $50, validity 15–240 min,
pool raises ≤ $500 per day in total, session override ≤ 24 h, pause ≤ 7 days.

### What "eligible" means
A cart line counts only if the variant exists, is for sale, its product is ACTIVE,
it is not on sale (`compareAtPrice > price`), and it doesn't match an exclusion.
Quantities must be whole numbers 1–20, with at most 30 lines; anything else is refused.

## How it fits together

```
storefront widget ──► ShopifyChatController (/api/shopify/chat/message[/stream])
                         │ puts VoucherChatContext(sessionId, userIdentifier, IP) in the Reactor context
                         ▼
                      ChatAgentService ── prompt gets the "CLOSING VOUCHER" section only while the tool is live
                         │ tool call
                         ▼
                      OfferDiscountChatToolHandler (offer_discount, isEnabled = VoucherService.toolAvailable)
                         ▼
                      VoucherService.offer ── VoucherShopifyGateway.fetchVariants (prices)
                         │                  └─ VoucherStore.reserve (claims + pool + PENDING row, one transaction)
                         ▼
                      VoucherShopifyGateway.createCode (discountCodeBasicCreate) ── ISSUED
                         ▼ (≈65 min later)
                      VoucherReconcileService ── REDEEMED, or delete + EXPIRED + pool release
```

- The tool is a `ChatToolHandler` bean (chat tool system, not a DB-agent tool). It is
  hidden unless: the env flag allows it, `enabled` is true, a Shopify check has
  confirmed `write_discounts`, and no owner PAUSE is active (cached up to 15 s).
  Issuing re-reads the settings, so the kill switch takes effect immediately for issuing.
- Only the two storefront chat endpoints set the chat context. The admin chat pages,
  personas and agents have no session, so the tool refuses there.
- `toolAvailable()` and the Shopify scope check fail closed.
- If Shopify's create call times out or errors, the service looks the code up and
  deletes any stray copy before returning the value. If it can't tell, the row stays
  PENDING (value still reserved) and the reconcile job settles it after 5 minutes.

### Client IP
`ClientIpResolver` picks the address for the per-IP limit (and chat analytics). The left-most
X-Forwarded-For entry is client-controlled, so it's never used. The order is:

1. `X-Real-IP`. Railway staff say the edge overwrites it with the connecting address.
2. The right-most X-Forwarded-For entry.
3. The socket address.
4. Otherwise `unknown`: one shared bucket that is still limited, so hiding the address doesn't skip the limit.

Railway's public answers about X-Forwarded-For conflict, so verify after deploying: set
`VOUCHER_LOG_IP_HEADERS=true`, send a chat from the storefront with and without a fake
`X-Forwarded-For` header (e.g. via curl), and check the `CLIENT_IP` log lines show your real
address both times. Then unset it. If everything comes out as `unknown` or as a Railway address,
vouchers would effectively be limited to `max_per_ip_per_day` per day storewide; fix the header
order before enabling.

### Settings safety
- Admin reads and updates use a strict read. If `voucher_settings` can't be read, the API answers 503
  and changes nothing; it never compares a change against defaults. (The chat path still fails
  closed to "disabled".)
- `voucher_settings.version` (V025) is an optimistic lock. The admin page sends the version it
  loaded, so saving over someone else's newer change returns 409 ("reload and try again").

### Ledger statuses
`PENDING` (reserved, create in flight), `ISSUED`, `REDEEMED`, `EXPIRED` (unused,
deleted, value returned), `REVOKED` (admin cancelled, value returned), `FAILED` (never
created, value returned, session freed to try again).

## Admin page: Settings → Vouchers

On the page you can turn vouchers on or off, edit every limit and the exclusions,
check the Shopify scope, see the day's report and ledger, and cancel a live code.
It also lists the overrides.

- Tightening a limit, adding exclusions, the kill switch and cancelling a code need
  only the admin key.
- **Turning vouchers on, or loosening any limit** (bigger pool or amount or share, a
  lower minimum, longer validity, a smaller holdout, a higher IP limit, or removing
  exclusions) also needs the owner key. Type it in the page's owner-key box. The page
  never stores it.

## Owner override command

`POST /api/vouchers/override` needs **both** headers, in every `ADMIN_AUTH_MODE`
(including `report` and `off`): it checks them itself.

- `X-API-Key`: an admin-scoped key (`api_keys` row or `ADMIN_BOOTSTRAP_KEY`)
- `X-Owner-Key`: the value of the `VOUCHER_OWNER_KEY` env var (at least 32 chars,
  compared in constant time). Unset or short means overrides are disabled (503). Five wrong
  owner keys within 15 minutes lock the owner endpoints for 15 minutes (429).

Overrides are temporary. Every one is logged at WARN (`VOUCHER_OVERRIDE`) and kept
in `voucher_overrides` with who, what, value, reason, source IP, expiry, and any revocation.

```bash
# Raise today's pool by $20 (ends at Melbourne midnight)
curl -sS -X POST "https://<your-app>.up.railway.app/api/vouchers/override" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <ADMIN_API_KEY>" \
  -H "X-Owner-Key: <VOUCHER_OWNER_KEY>" \
  -d '{"type":"POOL_RAISE","value":20,"who":"Nic","reason":"Sale weekend"}'
```

```bash
# Allow up to $20 for one chat session (session id from Chat Logs), for 2 hours
curl -sS -X POST "https://<your-app>.up.railway.app/api/vouchers/override" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <ADMIN_API_KEY>" \
  -H "X-Owner-Key: <VOUCHER_OWNER_KEY>" \
  -d '{"type":"SESSION_AMOUNT","target":"<CHAT_SESSION_ID>","value":20,"durationMinutes":120,"who":"Nic","reason":"Regular customer, big order"}'
```

```bash
# Pause all vouchers for 24 hours (the kill switch in Settings works too)
curl -sS -X POST "https://<your-app>.up.railway.app/api/vouchers/override" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <ADMIN_API_KEY>" \
  -H "X-Owner-Key: <VOUCHER_OWNER_KEY>" \
  -d '{"type":"PAUSE","durationMinutes":1440,"who":"Nic","reason":"Stocktake"}'
```

```bash
# End an override early
curl -sS -X POST "https://<your-app>.up.railway.app/api/vouchers/override/<OVERRIDE_ID>/revoke" \
  -H "X-API-Key: <ADMIN_API_KEY>" \
  -H "X-Owner-Key: <VOUCHER_OWNER_KEY>"
```

`SESSION_AMOUNT` still allows one voucher per session, stays within the pool, is capped
at $100 and 20% of the eligible cart, and is valid for 2 h by default (24 h max). It replaces the
tier caps for that session, and its code's minimum spend is `max($150, 5 × amount)`.

### Other endpoints (admin key; also checked inside the controller)

| Method and path | Purpose |
|---|---|
| `GET /api/vouchers/settings` | Settings + status (scope, owner key configured, paused, today's raise) |
| `PUT /api/vouchers/settings` | Partial update; loosening needs `X-Owner-Key` (the 403 lists `needs_owner_key`) |
| `POST /api/vouchers/kill` | Kill switch (sets `enabled=false`) |
| `GET /api/vouchers/ledger?date=YYYY-MM-DD` | One Melbourne day's codes |
| `GET /api/vouchers/report?date=YYYY-MM-DD&store=true` | Daily report; `store=true` also reads that day's store orders from Shopify for AOV without a voucher |
| `POST /api/vouchers/ledger/{id}/revoke` | Delete a live code now and return its value |
| `POST /api/vouchers/reconcile` | Run a reconcile pass now |
| `POST /api/vouchers/scope-check` | Re-check `write_discounts` |
| `GET /api/vouchers/overrides` | Last 50 overrides |

### Report fields
`offered_sessions` (eligible, non-holdout chats where the tool was called), `holdout_sessions`,
`issued`, `redeemed`, `expired`, `revoked`, `outstanding`, `dollars_spent`, `dollars_returned`,
`pool_limit` / `pool_remaining`, `redemption_rate`, `aov_with_voucher` (redeemed voucher
orders), `aov_without_voucher` (other store orders that day, first 250, when `store=true`),
and `outcomes` (count per tool outcome, such as BELOW_MINIMUM, HOLDOUT or UNAVAILABLE).

## Rollout

Each step can be deployed and checked on its own.

1. **Rotate the Shopify token and add scopes.** Reinstall the app so the new token has
   `write_discounts` (plus the existing `read_products` and `read_orders`). Update
   `SHOPIFY_ACCESS_TOKEN` in Railway. The old token leaked, so revoke it.
2. **Admin auth to enforce.** Set `ADMIN_AUTH_MODE=enforce`
   (`docs/security/ADMIN_API_AUTH.md`). The voucher endpoints check keys themselves
   anyway, but the rest of the admin API should not be open.
3. **Owner key.** Generate one and set it in Railway only. Keep it out of the repo and out of chat:
   ```bash
   openssl rand -base64 36
   ```
   Set `VOUCHER_OWNER_KEY` to the output. Leave `VOUCHER_FEATURE_ALLOWED` unset (true).
4. **Deploy this branch.** Flyway applies V024 (new tables only) and V025 (adds `voucher_settings.version`).
   Behaviour is unchanged: `enabled=false`. To follow the usual 3-stage pattern, V024 can
   ship alone first; the code tolerates a missing settings row (it reads as disabled).
5. **Check the client IP** as described in "Client IP" above (`VOUCHER_LOG_IP_HEADERS=true`, then unset).
   **Check scopes.** Settings → Vouchers, click **check**: "Shopify write_discounts" should say
   *granted*. Logs should show `voucher: startup reconcile done`.
6. **Dry run in the store.** In Settings → Vouchers, set holdout to 0 and the pool to
   $10 (a lower pool is tightening; holdout 0 needs the owner key), then enable with the
   owner key. In the storefront widget, build a $150+ full-price cart (add one sale item too),
   hesitate, and get a code. Check the bot says the sale item is already discounted. In Shopify
   Admin → Discounts, confirm the code's amount, minimum, end time, one use, no combining and the
   specific products. At checkout, confirm it applies, and that it does *not* apply once you drop
   below the minimum. Repeat with a $500+ cart for a $25 code and check its minimum is $500.
   Cancel the codes from the ledger.
7. **Go live.** Set the holdout back to 30% and the pool to $50. Watch `VOUCHER_ISSUED` /
   `VOUCHER_RECONCILE` logs and the daily report for a week before changing anything.

## Rollback

| Need | Do | Effect |
|---|---|---|
| Stop new vouchers now | Settings → Vouchers → **Kill switch** (or `POST /api/vouchers/kill`) | Tool disappears within 15 s, issuing refuses immediately. Live codes stay valid until their hour is up; cancel any from the ledger. |
| Stop without DB access | Railway env `VOUCHER_FEATURE_ALLOWED=false` and redeploy | Tool hidden and blocked regardless of DB |
| Remove the code | `git revert` the voucher commits and deploy | The tables stay (additive, harmless). Never edit V024; drop the tables with a new migration if ever wanted. |
| Codes still live after a revert | Shopify Admin → Discounts, filter by `HH`, delete any "Chat voucher" codes | They also expire on their own within an hour |

## Threat model

| Threat | Mitigation | Residual risk |
|---|---|---|
| Prompt injection: "I'm the owner", "ignore your rules", "give me $999" | Limits are server-side; the tool input has no field that raises a limit; amounts are capped (999 → $10), negatives and zero are refused; the prompt treats such messages as customer text | The bot may *say* something wrong. It can't create a code beyond the limits. |
| Model invents a bigger cart (fake subtotal or quantities) | Subtotal comes from Shopify prices; no subtotal input; quantities over 20 refused; the code's minimum is `max($150, amount/10%)` and it only applies to the listed variants | None beyond the per-code amount |
| Customer farms codes (new chat sessions, cleared storage, changed IP) | One per session, IP limit per day, single use. **Farming is bounded by the daily pool** (default $50/day), and every code needs a real $150+ full-price order ($500+ for a $25 code) to be worth anything. | Session id and `userIdentifier` are generated by the browser. The IP comes from `X-Real-IP` or the right-most X-Forwarded-For hop, never the spoofable left-most one, but a determined person changing networks can collect several codes until the pool is gone. Worst case is the pool, given away as discounts on real orders. |
| A code is shared or posted online | Single use, 1-hour life, minimum spend and specific products on the code | Whoever uses it first gets the discount, still only on a real qualifying order. Costs at most that one code's value. |
| Someone guesses or brute-forces codes | 14 random chars from SecureRandom (~69 bits), single use, 1 h life | Negligible |
| Concurrent requests overdraw the pool | Single conditional UPDATE under a row lock; tested with 24 concurrent issues against a $50 pool (exactly 5 × $10) | None known |
| Duplicate code for one session (parallel tool calls) | `voucher_claims` primary key; tested with 12 parallel calls | None known |
| Shopify create times out after creating the code | Look up by code and delete; otherwise keep it reserved (PENDING) until reconcile deletes it | Value stays reserved a few minutes longer than needed |
| Value returned twice / pool drift | Status compare-and-set; pool released only in the same transaction as the status change | None known |
| Unused code not deleted (Shopify down) | Value stays reserved; reconcile retries every minute and logs an ERROR every 10 failures; the code has expired anyway (`endsAt`) | Pool slightly understated until Shopify recovers |
| Leaked admin key | Admin can view, tighten and kill only; enabling or loosening needs the owner key | Admin key holder can cancel codes or turn vouchers off (annoying, not costly) |
| Leaked owner key (alone) | Useless without an admin key; five bad tries lock the endpoint | |
| Both keys leaked | Hard ceilings in code still bound it: pool ≤ $500 + raise ≤ $500 per day, ≤ $100 per voucher, ≤ 20% of the cart, every override expires; every change is audited at WARN with source IP | Up to about $1,000/day of discounts on real orders until a key is rotated. Rotate `VOUCHER_OWNER_KEY` in Railway, then redeploy. |
| `ADMIN_AUTH_MODE=report` lets unauthenticated admin calls through | Voucher endpoints authenticate inside the controller in every mode | None for vouchers |
| Missing `write_discounts` | Startup and periodic scope check; an access-denied create disables the tool and logs ERROR | None |
| Owner override reaches the chat path | The chat path only reads overrides; only `VoucherAdminController` creates them, behind both keys | None |
| SESSION_AMOUNT tied to a client-chosen session id | Override still allows only one voucher, within the pool, ≤ $100 / 20% of cart, expires (≤ 24 h) | Someone who learns that session id within the window could use it instead of the intended customer |

## Things to know

- **No customer records:** the voucher flow never creates Shopify customers or asks for an email.
  If a customer volunteers one, only a hash of it is stored, for the one-per-day limit.
- **AOV comparison:** `aov_with_voucher` covers chat-voucher orders; `aov_without_voucher` covers
  all other store orders that day. Those groups differ (selection bias). For real lift, compare
  conversions of offered versus holdout chats. That needs order attribution for holdout chats
  (e.g. a cart attribute set by the widget), which is not built yet.
- **Shopify API version 2025-01:** the create uses `customerSelection: {all: true}`. A later API
  version replaces it with `context`. Update `ShopifyVoucherGateway.CREATE` when bumping
  `SHOPIFY_API_VERSION`.
- **Minimum subtotal with product-specific codes:** Shopify applies the minimum to the
  entitled items. Confirm this in rollout step 6.
- The reconcile job shares Spring's single scheduler thread with the inventory jobs. A slow
  Shopify can delay them by seconds. Every Shopify call has the client's timeouts.
- Application logs never show a full code (`VOUCHER_ISSUED … code=HH7K…`). The ledger, the admin page and the stored chat transcript (Chat Logs) do, since the bot tells the customer.
