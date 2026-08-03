# GTM / Analytics Cleanup — Resume Status

_Last updated: 2026-08-03. Companion to `CONVERSION-ADVISORY.md` (full tag inventory + roadmap) and the audited container `GTM-TXB6ST3`._

---

## ▶ RESUME HERE (single source of truth for this project)

**All deliverable files:**
- `live-theme/CONVERSION-ADVISORY.md` — full audit, real tag IDs, Phase 1→3 roadmap, execution runbook.
- `live-theme/GTM-CLEANUP-STATUS.md` — this file: status + ready-to-paste agency message.
- `tracking/ga4-customer-events-pixel.js` — BUILT: Shopify Customer Events pixel, full-funnel GA4 ecommerce + deduped purchase.
- `tracking/README.md` — deploy order for the pixel + Phase 2 steps.
- Memory: `shopify-theme.md` (auto-loads each session).

**Access setup:** the whole measurement stack is owned by agency **Lealy Marketing**. Nic's own account `nic@hearnshobbies.com.au` (Chrome profile "Nic hearnshobbies", where the Claude extension first ran) has **GTM only**. The GA4/Ads/Shopify/Meta all live under a **separate Chrome profile "Nicola" = `nic.poltronieri@gmail.com`**, reached by installing a 2nd Claude-for-Chrome extension there (browser named **"Nic Gmail"**). Use that browser for anything GA4/Ads/Shopify/Meta.

**Confirmed decisions (all verified from account access):**
- GA4: keep **`G-CMP1JSYT74`** (live Hearns prop, 24k users/97 purchases wk); remove Shopify-side **`G-36Z87CE8XD`**.
- Ads: keep **`AW-617038362`** (active acct "Hearns Hobbies" CID 224-128-2337); **`AW-568425008`** = CANCELLED acct "The Hobby Man (Hearns)" → remove its tag.
- Ads conversion setup is the root cause of long-standing trouble: **Purchase goal has 2 primary actions (double-count)**, **Add-to-basket & Begin-checkout MISCONFIGURED**, a **"Page views" conversion (534)**, **Enhanced Conversions OFF**.
- Meta pixel **`5476101672453884`** ACTIVE & correct → keep.

**Next actions (in priority order):**
1. ☐ **Nic:** paste the agency Slack message (below) to Lea — unblocks the Ads/GA4 fixes she must do in her live account.
2. ☐ **Nic (GTM, guided):** delete 3 dead tags — ATC-Test (UA), FB $10 Discount, Black Friday → publish.
3. ☐ **GA4 dedup:** Sales channels → Google & YouTube → Settings → stop `G-36Z87CE8XD` + drop cancelled `AW-568425008`.
4. ☐ **Deploy the GA4 pixel** (after step 3): Shopify → Customer events → Add custom pixel → paste `tracking/ga4-customer-events-pixel.js` → Connect → validate in GA4 DebugView.
5. ☐ **Phase 2:** enable Google Ads Enhanced Conversions (Lea's acct) + Meta CAPI (Facebook & Instagram Shopify app).

**To migrate to another window/machine:** these files live in the **`shopify-data-api` git repo** (`live-theme/` + `tracking/`) — commit & push, then pull on the other machine. On a fresh Claude session, say *"resume the GTM/conversion cleanup"* and it will read this file + the `shopify-theme` memory. (The Claude memory is machine-local, so the repo files are the portable record.)

---

## TL;DR of where we are
The Shopify **theme** conversion fixes are shipped & live. The remaining work is **analytics/GTM cleanup**, and the key finding is: **the whole measurement stack (GTM, GA4, Google Ads) is owned by the marketing agency Lealy Marketing** — `nic@hearnshobbies.com.au` has no direct GA4/Ads access (GTM access was just obtained via an accepted invite). So the big fixes go to the agency; only safe dead-tag deletes are DIY.

## ✅ Done
- Theme fixes live (see `[[shopify-theme]]` memory): gallery LCP, gated Shop Pay, mobile sticky ATC.
- `CONVERSION-ADVISORY.md` rewritten with the **real** tag inventory + an Execution Runbook.
- **GTM read-only audit complete** — accepted the pending invite to account "Hearns Hobbies - New"
  (#6051089523), container `GTM-TXB6ST3` (#89819152), workspace 18. All 12 tags inventoried.
- Slack message to the agency drafted (below).

## Confirmed facts / IDs
- **GA4 in GTM:** `G-CMP1JSYT74` (tag "GA4 Configuration Tracking", All Pages).
- **GA4 #2:** `G-36Z87CE8XD` — NOT in GTM → fires from the Shopify side (native/app pixel). = duplicate.
- **Google Ads:** `AW-617038362` (primary, active conversion `617038362`) + `AW-568425008` (second, agency).
- **Meta pixel:** `5476101672453884` (tag "FB Pixel - Checkout Success").
- **Access reality:** `nic@` has NO GA4 and NO Google Ads access (both show provisioning/empty screens); GTM only via the accepted invite. Agency admins: lealy.marketing@gmail.com, reymarksoriano.personal@gmail.com.

## ⏭️ NEXT ACTIONS (resume here)

### A. Nic to do in GTM now — 3 safe deletions (DIY; Claude can't edit GTM — classifier-blocked)
tagmanager.google.com → **Hearns Hobbies - New** → `GTM-TXB6ST3` → **Tags**. For each: click tag → top-right ⋮ → Delete → confirm → then **Submit → Publish** (version name `Cleanup: remove dead UA test + expired promo tags`). Revert via Versions if needed.
1. **ATC - Test (for FB Doublechecking)** — dead Universal Analytics + test tag
2. **FB Pixel - $10 Discount Button** — expired 4-yr promo
3. **Google Ads - Black Friday** — stale 4-yr seasonal
- [ ] After doing this, Claude re-checks the live site to confirm they're gone.

### B. Send the Slack message to Lea (agency) — drafted below

### C. Agency (Lea) to action — the real fixes
1. **"Google Ads Conversion Tracking" fires on All Pages** (conv `617038362`) → restrict to purchase trigger. HIGH priority (likely inflating conversions).
2. **Dedupe GA4** `G-CMP1JSYT74` (GTM) vs `G-36Z87CE8XD` (Shopify) → keep one.
3. **Two Ads accounts** `AW-617038362` / `AW-568425008` → confirm live one, retire the other's tags.
4. **Grant `nic@hearnshobbies.com.au` admin on GA4 + Google Ads.**

### D. Still-open confirmations (blocked by browser session split — do in Nic's own logged-in window)
- Shopify → Settings → Customer events: confirm `G-36Z87CE8XD` is the Shopify-side GA4 pixel.
- Meta → Events Manager: is pixel `5476101672453884` still receiving events? (decides if FB tags stay)

## Known blockers (for whoever resumes)
- **GTM edits via browser automation are classifier-blocked** — deletions/edits must be done by the user.
- **Shopify & Meta logins don't reach Claude's automation window** — that window shares the Google `nic@` session but not Shopify/Meta. Ask user to relay, or log in *inside the Claude-controlled window*.
- CDP screenshots freeze on this browser — verify via `javascript_tool` DOM reads.

## Slack message to the agency (ready to paste) — updated 2026-08-01 with confirmed findings
> **Hi Lea** 👋 We did a deep audit of the Hearns Hobbies analytics setup (GTM container **GTM-TXB6ST3** + GA4/Ads/Meta/Shopify) and think we found *why* conversion tracking has been so hard to pin down. In short, the numbers you've been optimising against have been unreliable at the source: a "conversion" that's actually counting **page views** (534 of them), a **duplicate GA4** splitting the data, and a tag firing to a **cancelled Ads account**. None of these show up as an obvious error in the dashboards — it's plumbing. Specifics + fixes below:
>
> **1. 🔴 The Google Ads conversion setup itself (this is the big one)** — in the active account (Hearns Hobbies, CID 224-128-2337 / `AW-617038362`):
>   • The **Purchase goal is "Needs attention" with 2 primary conversion actions** → looks like **purchases are being double-counted**. Can we get to one primary purchase action, deduped on `transaction_id`?
>   • **"Add to basket" and "Begin checkout" are Misconfigured** (0 conversions) → mid-funnel signal for Smart Bidding is dead.
>   • A **"Page views" conversion** has logged **534** — page views shouldn't be a conversion; please retire it.
>   • **Enhanced Conversions** looks off — turning it on would recover purchases lost to ad-blockers/iOS.
>   (The old "Google Ads Conversion Tracking" GTM tag also fires on All Pages — worth restricting to purchase.)
>
> **2. 🟠 A dead Ads account still tagged on the site** — the site also fires **`AW-568425008`**, which belongs to the **cancelled** account "The Hobby Man (Hearns)" (CID 398-071-3039). Please remove its tag(s). Keep the active `AW-617038362`.
>
> **3. 🟠 Duplicate GA4** — two properties collect on the site: **`G-CMP1JSYT74`** (the live property — 24k users/97 purchases a week, in GTM) and **`G-36Z87CE8XD`** (firing from the Shopify side). Please **keep `G-CMP1JSYT74`** and disable the Shopify-side `G-36Z87CE8XD`.
>
> **Meta pixel is fine** — `5476101672453884` ("Hearns Hobbies - New Pixel 2022") is active and correct; no change needed there.
>
> **FYI** we removed 3 clearly-dead GTM tags ourselves (a Universal Analytics test tag, an expired "$10 Discount" FB pixel, a 4-yr "Black Friday" conversion) — all restorable from version history if any were still needed.
>
> **One ask:** please add **nic@hearnshobbies.com.au** as **admin on GA4 + Google Ads** so we have visibility into our own measurement going forward.
>
> **Bigger picture (once the above is clean):** the site's `dataLayer` isn't pushing ecommerce events, so every tag is auto-guessing. The reliability win is going **server-side** — **Google Ads Enhanced Conversions** + **Meta Conversions API** — plus real `purchase` events with order value + `transaction_id` and single-source-per-platform (no GTM *and* app-pixel double-fire). That's what stops the leakage for good. Happy to jump on a call and get it sorted together. Thanks! 🙏
