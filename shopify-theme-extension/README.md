# Ask Camilla - AI Search / Chat Theme Extension

**Status: LIVE in production (December 2025). This README is the single authoritative deployment doc.**
All previous guides (v2.0 - v2.5, snippet/header variants, fix notes) are superseded and moved to [`archive/`](archive/).

## What This Is

A storefront AI chat/search widget for Hearn's Hobbies. It adds a **dual search bar** to the theme header with two modes:

- **Standard search** - navigates to Shopify's native `/search?q=...&type=product`
- **Ask Camilla (AI search)** - opens the AI search modal (or falls back to `/search?...&ai=1`), which calls the Railway backend:
  - **Endpoint**: `POST /api/shopify/chat/message?shop=hearnshobbies.myshopify.com`
  - **Body**: `{ "message": "...", "conversationHistory": [...], "maxResults": 10 }`
  - **Response**: `{ "response": "...", "products": [...], "role": "assistant", "timestamp": "..." }`
  - The backend uses Claude AI plus the Shopify product catalog to return recommendations rendered as product cards in the modal.

## The Working Solution (why it looks the way it does)

Earlier versions (v2.0 - v2.5) rendered the search bar as a `<form action="/search">`. The theme's own SearchBar JavaScript detects any such form, tries `this.element.closest(".header")`, gets `null`, and crashes - breaking dropdown menus, gift card variant selection, and "View more" buttons. Multiple workarounds (timing delays, retries, `closest()` overrides) only partially helped.

The fix that finally worked and is live:

1. **No `<form>` tag** - the bar is a `<div class="dual-search-inner">`, so the theme's SearchBar never detects it. No conflict, no crash.
2. **JavaScript navigation** - submit is handled in JS via `window.location.href`.
3. **Unique input name** - `ai_q` instead of `q`, and all element IDs are `ai-` prefixed to avoid ID collisions with gift card / variant forms.
4. **CSS fixes** - theme popovers/mega-menus forced to `z-index: 99999`, and the loading overlay uses `pointer-events: none` so it never blocks clicks.
5. **Simple init** - plain DOM-ready initialization; no retry/timing logic is needed once the form conflict is gone.

## Key Files

| File | Purpose |
|------|---------|
| `Dual Search Naihra final files /dual-search-bar-snippet.liquid` | **Production snippet** - the non-form dual search bar (deploy this) |
| `Dual Search Naihra final files /dual-search.js` | **Production JS** - navigation, personalized loading messages, timer, analytics (deploy this) |
| `snippets/dual-search-bar-snippet-WORKING.liquid` | Commented copy of the working snippet (reference) |
| `dual-search-WORKING.js` | Commented copy of the working JS (reference) |
| `assets/ai-search-client.js` | AI modal API client - `fetch`es `POST {apiUrl}?shop=...` |
| `assets/ai-search-modal-styles.css` | Modal styles |
| `blocks/search-modal.liquid` | AI Search Modal block (holds the `api_url` setting, default `https://your-app.railway.app/api/shopify/chat/message`) |
| `blocks/floating-ai-button.liquid` | Optional floating "Ask Camilla" button |
| `snippets/ai-search-assets.liquid` | Loads modal assets from `theme.liquid` |
| `snippets/dual-search-bar-snippet-v2.3/2.4/2.5.liquid`, `dual-search-enhanced*.js`, `blocks/dual-search-bar.liquid`, `blocks/search-bar.liquid` | Superseded form-based attempts - **do not deploy** |

## Deployment (current working steps)

1. **Snippet**: Shopify Admin → Online Store → Themes → Edit code → **Snippets** → create `dual-search-bar-snippet.liquid` and paste the content of `Dual Search Naihra final files /dual-search-bar-snippet.liquid`.
2. **JavaScript**: **Assets** → create/replace `dual-search.js` with `Dual Search Naihra final files /dual-search.js`.
3. **Render the bar in the header** (e.g. the theme's header liquid file):
   ```liquid
   {% render 'dual-search-bar-snippet',
     primary_color: '#212b36',
     standard_placeholder: 'Search products...',
     camilla_placeholder: 'Ask Camilla to find products...'
   %}
   ```
   All parameters are optional; the defaults shown above are built in.
4. **Include the JS in `theme.liquid`** (before `</head>` or `</body>`):
   ```liquid
   {{ 'dual-search.js' | asset_url | script_tag }}
   ```
5. **AI modal**: add the "AI Search Modal" block via the theme customizer and set its **API URL** to the Railway backend, e.g. `https://<your-app>.railway.app/api/shopify/chat/message`. The shop domain is auto-detected from `{{ shop.permanent_domain }}`.

No timing hacks and no theme code modifications are required.

## Post-Deploy Test Checklist

- Search bar visible in header; Standard / Ask Camilla toggle works
- Standard search navigates to `/search?q=...`
- AI search opens the modal (or navigates with `ai=1` as fallback)
- **Gift card pages: variant selection works** (see troubleshooting below)
- Header dropdown/mega menus open; product "View more" buttons work
- Other forms (newsletter, contact) unaffected; no console errors
- Loading overlay shows personalized messages; timer turns orange at 10s, green at 20s
- Mobile layout OK

## Troubleshooting

**Gift card / product variant selection breaks after install**
Historic cause: duplicate generic IDs (`#search-input`, `#dual-search-form`) from the search bar loading in the header on every page - `getElementById` then targets the wrong element. The working snippet already uses `ai-` prefixed IDs and the `ai_q` input name, so this cannot recur unless an old form-based snippet (v2.x, `blocks/dual-search-bar.liquid`) is deployed. If it happens, confirm only the WORKING snippet is rendered and no v2.x file is still referenced.

**Dropdown menus / theme features break, or console shows `Cannot read properties of null` in SearchBar**
An old `<form action="/search">` variant is deployed. Replace it with the working non-form snippet. Do not try to fix this with load-order tricks - placing the code in a section instead of the header only masked the problem by loading after the theme JS; the non-form snippet removes the conflict entirely regardless of placement.

**Dropdowns render underneath the search bar** - the z-index CSS fix in the snippet is missing; redeploy the full snippet.

**Clicks on the page do nothing while/after AI search** - the loading overlay's `pointer-events: none` rule is missing; redeploy the full snippet.

**Modal / API errors**
1. Check the backend is up on Railway and the block's API URL is correct.
2. CORS must allow `hearnshobbies.com`; requests include `?shop=hearnshobbies.myshopify.com`.
3. Check the browser network tab; the response must contain a `products` array for cards to render.

## Emergency Rollback

If anything breaks the live site, follow [`EMERGENCY-ROLLBACK.md`](EMERGENCY-ROLLBACK.md) - it removes all Ask Camilla blocks/snippets in about 5 minutes via the theme customizer or code editor.

## History

The full write-up of the fix and the comparison of all v2.0 - v2.5 attempts is preserved in `archive/WORKING-SOLUTION-DOCUMENTED.md`. Everything else in `archive/` is obsolete.
