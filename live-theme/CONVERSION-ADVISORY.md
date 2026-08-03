# Conversion Advisory — Dashboard Tasks (no theme code)

These two items came out of the storefront audit but live in external dashboards, **not** the Shopify
theme. They can't be fixed by editing theme files.

Measured baseline (live product page): ~215 total requests, of which **~68 are marketing/analytics tags**.
The rest is Shopify platform (shop.app / Shop Pay / core) and can't be trimmed.

---

## #3 — Google Tag Manager cleanup  (GTM container `GTM-TXB6ST3`)

GTM loads first in `<head>` and is the gateway that pulls GA4, Google Ads and Floodlight. Live per-PDP
request counts: **GTM 14 · Google Ads/DoubleClick 11 · GA4 4 · Zip 8**.

**What's actually firing** (measured live, 2026-08-01 — 5 Google destinations through one container):

| Type | ID(s) firing | Issue |
|------|-------------|-------|
| GA4 | `G-CMP1JSYT74` **and** `G-36Z87CE8XD` | **Two properties** → double pageviews, split data |
| Google Ads | `AW-617038362` (firing conversion `617038362`) **and** `AW-568425008` | **Two ad accounts** → one is likely stale |
| Google Tag | `GT-55XZBPX` | Coexists with the direct GA4/Ads tags → probable double-tagging |
| Floodlight | DoubleClick remarketing | Keep only if actively used |

Clean of: Universal Analytics, Bing, TikTok (none found — good).

### Audited container inventory (2026-08-01) — for the agency (Lealy Marketing / Rey Mark Soriano)

Read-only audit of container `GTM-TXB6ST3` (account "Hearns Hobbies - New" #6051089523, container #89819152).
Confirmed IDs: **GA4 `G-CMP1JSYT74`** (GTM tag "GA4 Configuration Tracking"); the 2nd GA4 `G-36Z87CE8XD`
is NOT in GTM → almost certainly **Shopify's native GA4** integration. **Google Ads `AW-617038362`** (primary).
**Meta Pixel `5476101672453884`**. 12 tags total:

| Tag | Type | Fires | Age | Recommendation |
|-----|------|-------|-----|----------------|
| ATC - Test (for FB Doublechecking) | Universal Analytics | ATC test | 1y | **DELETE** — UA shut down Jul 2023 (collects nothing); also a test tag |
| Google Ads Conversion Tracking | Ads Conversion (`617038362`) | **All Pages** | 1y | **FIX (high priority)** — a conversion firing on every page misreports conversions; restrict to purchase |
| Google Ads - Black Friday | Ads Conversion | Black Friday | 4y | **DELETE** — stale seasonal (agency can re-add each Nov) |
| FB Pixel - $10 Discount Button | Custom HTML | discount click | 4y | **DELETE** — expired promo |
| FB Pixel - Checkout Success | Custom HTML (`5476101672453884`) | thank-you | 4y | **KEEP** — pixel confirmed ACTIVE (events in last hour), correct current pixel |
| GA4 Configuration Tracking | Google Tag (`G-CMP1JSYT74`) | All Pages | 1y | **DEDUPE vs Shopify native GA4** (`G-36Z87CE8XD`) — pick one source |
| Google Tag AW-617038362 | Google Tag | All Pages | 1y | Keep (if `AW-617038362` is the live ad account) |
| GAds - Remarketing Tag | Ads Remarketing | add_to_cart | 2y | Keep (if Ads active) |
| Google Shopping App Purchase | Ads Conversion | purchase | 1y | Keep |
| Conversion Linker | Conversion Linker | All Pages | 4y | Keep (required) |
| Schema \| Category Pages | Custom HTML | category PV | 1y | **VERIFY** — may duplicate the theme's built-in structured data |
| Schema \| Product Pages | Custom HTML | view_item | 1y | **VERIFY** — may duplicate the theme's built-in structured data |

**RESOLVED via account access (2026-08-01, from the `nic.poltronieri@gmail.com` profile that owns the stack):**
- **GA4:** keep **`G-CMP1JSYT74`** — it's the live Hearns property (acct 17698786 / prop 320871681, ~24k users & 97 purchases/wk). **`G-36Z87CE8XD` is the duplicate** (fires Shopify-side, separate/orphan property) → remove that source.
- **Google Ads:** keep **`AW-617038362`** = active account "Hearns Hobbies" (CID 224-128-2337). **`AW-568425008` belongs to a CANCELLED account** ("The Hobby Man (Hearns)", CID 398-071-3039) → remove its tag. Also: the account has a **"Page views" conversion action that logged 534 pageviews-as-conversions** — this is the "fires on All Pages" tag; fix the trigger to purchase-only.
- **Meta:** pixel **`5476101672453884`** = "Hearns Hobbies - New Pixel 2022" is **ACTIVE** (events received in the last hour) and is the correct current pixel → **keep** the FB Checkout Success tag. (Several old pixels — "Old…Hearns", "Hobbyman" — exist in the Meta account but are NOT in GTM, so no action.)

**Deeper Google Ads conversion audit (active account 224-128-2337, 2026-08-01) — this is the crux of the long-standing conversion trouble:**
- **Purchase** conversion tracks (771 in period) BUT the goal is **"Needs attention" with 2 primary conversion actions** → almost certainly **double-counting purchases** (two purchase actions both set primary). Fix: one primary purchase action, dedup on `transaction_id`.
- **"Add to basket" and "Begin checkout" are MISCONFIGURED** (0 conversions, error state) → the mid-funnel signals Smart Bidding needs are dead.
- **"Page views" (534)** exists as a conversion goal — page views should never be a conversion; remove/retire it.
- **Enhanced Conversions** not evident (likely OFF) → losing purchases to ad-blockers/ITP that server-side matching would recover.
- ⚠️ This is a **live account Lea actively manages** — changing conversion actions affects her bidding. Coordinate; don't edit blind. Enhanced Conversions is the one purely-additive safe win.

Work through this in the GTM web UI (tagmanager.google.com):

- [ ] **Deduplicate GA4.** Both `G-CMP1JSYT74` and `G-36Z87CE8XD` are collecting. Pick the canonical
      property and remove the other. **Check this first:** the usual cause is Shopify's native GA4
      (Settings → Customer events) firing one **plus** a GA4 tag in GTM firing the other.
- [ ] **Deduplicate Google Ads.** Confirm which of `AW-617038362` / `AW-568425008` is the current ad
      account, then delete the stale account's tags (config + conversion + remarketing). `AW-617038362` is
      the one currently firing a conversion.
- [ ] **Resolve the Google Tag `GT-55XZBPX`.** A `GT-` tag can itself deliver GA4 + Ads. If it already
      covers your kept GA4/Ads destinations, remove the redundant standalone `G-`/`AW-` tags (or retire the
      `GT-`) so each destination loads exactly once.
- [ ] **Target:** collapse 5 Google destinations → **1 GA4 + 1 Ads** (+ Floodlight only if used). This alone
      should roughly halve the ~14 GTM + 11 Ads + 4 GA4 requests.
- [ ] **Move Zip / Afterpay messaging OUT of GTM.** On-site BNPL messaging should render via the theme's
      Afterpay/Zip app blocks (already installed — the `afterpay-on-site-messaging` block is on the PDP),
      not a GTM custom HTML tag. Delete the GTM version if present.
- [ ] **Remove unused/legacy tags** — old pixels, retired campaign tags, one-off marketing scripts.
- [ ] **Prefer Shopify Customer Events / server-side** for conversion tracking where possible (fewer
      client-side requests, more resilient to ad blockers).
- [ ] **Re-measure** PDP requests after cleanup and compare against the ~68 controllable baseline.

⚠️ **Data-quality note (not a speed issue):** the page's `dataLayer` carries only `gtm.js`/`gtm.dom`/`gtm.load`
— **no ecommerce or custom events** are pushed. Conversion tracking therefore relies on each tag's own
auto-collection. Before deleting any tag, confirm the conversion you care about isn't wired *only* to that
tag — dedupe carefully and verify a test purchase still records the Google Ads conversion afterwards.

---

## #4c — Klaviyo popup review  (Klaviyo dashboard, account `UGibUk`)

**No theme change needed.** The theme's newsletter surfaces are already well-placed and are staying:
footer signup, the collapsed "Subscribe & Save" header flyout, and the sold-out "Notify me when available"
back-in-stock form. Nothing sits visually above the Add-to-Cart. Klaviyo is 26 requests/PDP (the biggest
single controllable third party).

Measured live (2026-08-01): Klaviyo loads **25 scripts** on a single product page — the single biggest
controllable third party. The only thing that can interrupt the buy action is a **Klaviyo popup/flyout**,
configured in Klaviyo (Signup Forms), not the theme. Review:

- [ ] **Delay the signup popup** a few seconds (or trigger on exit-intent / scroll depth) instead of firing
      immediately on load.
- [ ] **Exclude the popup from cart and checkout** pages so it never interrupts someone buying.
- [ ] **Consider excluding the product page** (or at least delaying it there) so it doesn't cover the ATC.
- [ ] **One active popup at a time** — make sure multiple overlapping Klaviyo forms aren't all enabled.
- [ ] In Klaviyo's onsite settings, disable any onsite features you don't use to trim the 26 requests.

---

---

## Roadmap — reliable tracking of ALL conversions

The cleanups above stop *wrong* data. This is how to actually capture *every* conversion. Root cause of the
long-standing conversion trouble: overlapping, uncoordinated tracking (GTM tags + Shopify app pixels +
native), a `dataLayer` with no ecommerce events, misfiring conversion definitions, duplicate GA4, and a dead
Ads account. Sequence it:

**Phase 1 — Stop the bleeding (this week):**
- [ ] Dedupe GA4: keep `G-CMP1JSYT74`, remove Shopify-side `G-36Z87CE8XD` (check Sales channels → Google & YouTube → Settings).
- [ ] Remove the cancelled account tag `AW-568425008`; keep `AW-617038362`.
- [ ] Fix the "Page views" conversion → fire on **purchase only**, with order value + `transaction_id`.
- [ ] Delete the 3 dead GTM tags (UA test, $10 promo, Black Friday).
- [ ] Ensure only ONE source per destination fires `purchase` (GTM **or** the Shopify app pixel, not both) — audit for double-counting.

**Phase 2 — Make it reliable (the real win):**
- [ ] **Server-side purchase tracking:** turn on **Google Ads Enhanced Conversions** + **Meta Conversions API** (via the Meta Shopify app) so purchases send server-side with hashed customer data. Recovers the 10–30% lost to ad blockers / iOS ITP / checkout redirects — likely the biggest missing chunk.
- [ ] **Real ecommerce events:** emit standardized `view_item` / `add_to_cart` / `begin_checkout` / `purchase` (with value + `transaction_id`) via a **Shopify Customer Events custom pixel** or a proper dataLayer, so tags stop guessing.
- [ ] **Import GA4 key events into Google Ads** instead of parallel GTM conversion tags where possible.
- [ ] **Consent Mode v2** (EU + modeling) and **`transaction_id` dedup** everywhere.

**Phase 3 — Validate:** GA4 DebugView, Google Tag Assistant, Meta Test Events, Ads conversion diagnostics after each change. Confirm a single test purchase produces exactly one conversion per platform with the right value.

---

## Execution Runbook (for a future Claude Code session)

> Purpose: open this file, start, and complete the GTM cleanup (and optional Klaviyo review) end-to-end
> via browser automation. **This is a guided runbook, not a script** — Claude drives the GTM web UI in the
> user's logged-in browser. Two steps REQUIRE a human decision (Step 3) — do not guess past them.

### Preconditions
- The user is **logged into tagmanager.google.com** (container `GTM-TXB6ST3`) in the Chrome session that
  `claude-in-chrome` controls. Claude must **never** enter credentials — if not logged in, ask the user to
  log in first.
- (Optional, for #4c) user logged into **klaviyo.com** (account `UGibUk`).
- Load browser tools in one call:
  `ToolSearch "select:mcp__claude-in-chrome__tabs_context_mcp,mcp__claude-in-chrome__navigate,mcp__claude-in-chrome__computer,mcp__claude-in-chrome__read_page,mcp__claude-in-chrome__javascript_tool,mcp__claude-in-chrome__tabs_create_mcp"`
- Note: CDP screenshots freeze on this store — verify via `read_page` / `javascript_tool` DOM queries, not screenshots.

### Step 1 — Baseline measure (before)
Navigate to a live PDP (`https://www.hearnshobbies.com.au/products/tamiya-porsche-911-rsr-tt-02?_cb=pre`),
wait ~4s, and run the **Measurement Script** (bottom of this section). Record the GA4/Ads/GT IDs and the
Google request counts. Expected today: GA4 ×2, Ads ×2, GT ×1 (see table above).

### Step 2 — Read-only GTM audit (no changes)
In the GTM UI, open **Tags**, **Triggers**, **Variables** for `GTM-TXB6ST3`. Enumerate every tag: name,
type, destination ID (`G-`/`AW-`/`GT-`), and firing trigger. Build a keep/delete table mapped to the IDs
above. Identify: the two GA4 config tags, the two Ads config/conversion/remarketing tags, the `GT-55XZBPX`
Google tag, Floodlight, and any legacy/unused tags or a GTM-injected Zip/Afterpay HTML tag.

### Step 3 — DECISION GATE (ask the user — never guess)
Pause and get from the user (answers live in their GA4 / Google Ads accounts, not in GTM):
1. **Canonical GA4 property:** keep `G-CMP1JSYT74` or `G-36Z87CE8XD`? (Check which is used for reporting /
   has history — GA4 Admin → Data Streams / Realtime.)
2. **Current Google Ads account:** keep `AW-617038362` (currently firing conversion `617038362`) or
   `AW-568425008`? (Check the active account in Google Ads.)
3. Confirm whether Floodlight remarketing is still in use.
Do not proceed to Step 4 until answered.

### Step 4 — Make edits in a GTM **workspace** (not yet live)
Based on Step 3: pause/delete the redundant GA4 tag, the stale Ads account's tags (config + conversion +
remarketing), and resolve `GT-55XZBPX` vs the standalone tags so each kept destination loads **once**. Remove
legacy/unused tags. If a Zip/Afterpay HTML tag exists in GTM, delete it (the theme renders BNPL via app
blocks). Make no other changes.

### Step 5 — Verify in GTM Preview (before publishing)
Use GTM **Preview / Tag Assistant** on a PDP: confirm only the kept destinations fire, and that the Google
Ads conversion still triggers on a test add-to-cart / begin-checkout. ⚠️ Because the `dataLayer` has no
ecommerce events, a conversion may be wired only to the tag you're removing — verify it survives.

### Step 6 — Publish + record revert path
Publish the workspace with a clear version name (e.g. `dedupe GA4/Ads – <date>`). **Revert path:** GTM →
Versions → select the prior version → Publish. Every change is one click to undo.

### Step 7 — Re-measure (after)
Reload a PDP (`?_cb=post`) and re-run the Measurement Script. **Success =** a single `G-`, a single `AW-`,
no orphan `GT-`/duplicate, and a lower Google request count. Record before/after in this file.

### Step 8 — (Optional) Klaviyo #4c
If logged into Klaviyo: in **Signup Forms**, apply the popup rules from the section above (delay,
exclude cart/checkout, one active popup). No theme change.

### Measurement Script (paste into `javascript_tool` on a live PDP after ~4s wait)
```js
const res=performance.getEntriesByType('resource');const ids={ga4:new Set(),ua:new Set(),ads:new Set(),gt:new Set()};
res.forEach(r=>{const u=r.name;(u.match(/[?&]tid=(G-[A-Z0-9]+)/g)||[]).forEach(x=>ids.ga4.add(x.split('=')[1]));
(u.match(/[?&]tid=(UA-[0-9-]+)/g)||[]).forEach(x=>ids.ua.add(x.split('=')[1]));
(u.match(/(AW-[0-9]+)/g)||[]).forEach(x=>ids.ads.add(x));(u.match(/(GT-[A-Z0-9]+)/g)||[]).forEach(x=>ids.gt.add(x));});
const g=res.filter(r=>/google|doubleclick|gtm/.test(r.name)).length;
({GA4:[...ids.ga4],UA:[...ids.ua],Ads:[...ids.ads],GoogleTag:[...ids.gt],googleRequests:g,
gtagTargets:[...new Set((window.dataLayer||[]).filter(e=>e&&e[0]==='config').map(e=>e[1]))]});
```

---

*Generated from the theme conversion audit. Theme-code fixes (gallery image sizing, gated Shop Pay express
button, mobile sticky Add-to-Cart) were applied separately in this theme copy.*
