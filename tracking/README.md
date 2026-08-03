# Hearns Hobbies — Conversion Tracking Rebuild

Clean, reliable tracking of **all** conversions. Companion to
`live-theme/CONVERSION-ADVISORY.md` (audit + roadmap) and `live-theme/GTM-CLEANUP-STATUS.md`
(agency message + resume status).

## Files here
- **`ga4-customer-events-pixel.js`** — a Shopify Customer Events custom pixel that emits GA4
  recommended ecommerce events across the full funnel (view_item → add_to_cart → begin_checkout →
  purchase) with order value + `transaction_id`. This is the single, reliable GA4 event source.

## Deploy order (important — avoids double-counting)
Do these in sequence, not all at once:

1. **Dedupe GA4 first.** Keep `G-CMP1JSYT74` (the live Hearns property). Remove the Shopify-side
   duplicate `G-36Z87CE8XD` (Sales channels → Google & YouTube → Settings). Set GTM's "GA4
   Configuration Tracking" tag to `send_page_view=false` (or remove GA4 from GTM) so pageviews
   aren't sent twice once the pixel is live.
2. **Deploy the pixel.** Shopify admin → Settings → Customer events → **Add custom pixel** → name it
   "GA4 Ecommerce" → paste `ga4-customer-events-pixel.js` → Save → **Connect**.
3. **Validate.** GA4 → Admin → **DebugView** and Reports → **Realtime**. Do one test purchase; confirm
   exactly **one** `purchase` event with the correct value and a `transaction_id`.

## Still to do (Phase 2 — see the roadmap in CONVERSION-ADVISORY.md)
- **Google Ads Enhanced Conversions** — turn on in the active account (`224-128-2337`). Purely additive,
  recovers purchases lost to ad-blockers/ITP. (Coordinate with Lea — it's her live account.)
- **Meta Conversions API (CAPI)** — enable in the Facebook & Instagram Shopify app with event dedup
  (`event_id`) against the existing pixel `5476101672453884`.
- **Google Ads conversion cleanup** (Lea's account): one primary purchase action (fix the double-count),
  repair Add-to-cart & Begin-checkout, retire the "Page views" conversion.

## Why this matters
The old setup had tags auto-guessing with no ecommerce dataLayer, a duplicate GA4, a purchase
double-count, dead funnel events, and a "page views" conversion. This rebuild makes **GA4 the clean
source of truth** and (with Enhanced Conversions + CAPI) captures the conversions currently lost to
ad-blockers and checkout redirects.
