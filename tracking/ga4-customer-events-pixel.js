/* ============================================================================
 * Hearns Hobbies — GA4 Ecommerce Customer Events Pixel
 * ----------------------------------------------------------------------------
 * WHAT: Sends Google Analytics 4 recommended ecommerce events across the FULL
 *       funnel — including checkout & purchase, which theme/GTM tags can't
 *       reliably reach — with order value + transaction_id for clean dedup.
 *
 * WHY:  The current setup relies on tags auto-guessing (the dataLayer has no
 *       ecommerce events). This makes GA4 the single, reliable source of truth
 *       for on-site behaviour and purchases.
 *
 * DEPLOY (Shopify): Admin → Settings → Customer events → Add custom pixel →
 *       name it "GA4 Ecommerce" → paste this whole file → Save → Connect.
 *
 * ⚠️ SEQUENCING — avoid double-counting:
 *   This becomes the SINGLE GA4 source. Before/at deploy:
 *   1. Confirm the canonical GA4 is G-CMP1JSYT74 (it is — the live Hearns
 *      property with the traffic history).
 *   2. In GTM, set the "GA4 Configuration Tracking" tag (G-CMP1JSYT74) to
 *      send_page_view=false OR remove it, and remove any GA4 event tags there,
 *      so pageviews/events aren't sent twice (once by GTM, once by this pixel).
 *   3. Remove the Shopify-side duplicate G-36Z87CE8XD (Google & YouTube channel).
 *
 * VALIDATE: GA4 → Admin → DebugView (this pixel sets debug_mode via the query
 *   param ?ga_debug=1 on the storefront), and Reports → Realtime. Do one test
 *   purchase and confirm exactly ONE purchase event with the right value.
 * ==========================================================================*/

const GA4_MEASUREMENT_ID = 'G-CMP1JSYT74';

/* --- Load gtag.js in the pixel sandbox ------------------------------------ */
(function loadGtag() {
  const s = document.createElement('script');
  s.async = true;
  s.src = 'https://www.googletagmanager.com/gtag/js?id=' + GA4_MEASUREMENT_ID;
  document.head.appendChild(s);
})();
window.dataLayer = window.dataLayer || [];
function gtag() { window.dataLayer.push(arguments); }
gtag('js', new Date());
// send_page_view:false — we emit page_view ourselves from the Shopify event so
// it carries Shopify's page context; avoids a duplicate auto pageview.
gtag('config', GA4_MEASUREMENT_ID, { send_page_view: false });

/* --- Helpers -------------------------------------------------------------- */
function money(m) { return m && m.amount != null ? Number(m.amount) : undefined; }

// Map a Shopify line item (or cartLine / productVariant) to a GA4 item.
function toItem(li) {
  if (!li) return null;
  const v = li.variant || li.merchandise || li; // lineItem.variant | cartLine.merchandise | productVariant
  const product = v.product || li.product || {};
  return {
    item_id: v.sku || (v.id != null ? String(v.id) : undefined),
    item_name: product.title || v.title || li.title,
    item_variant: v.title,
    item_brand: product.vendor,
    item_category: product.type,
    price: money(v.price) ?? money(li.price),
    quantity: li.quantity != null ? li.quantity : 1,
  };
}
function toItems(list) { return (list || []).map(toItem).filter(Boolean); }

// Only fire when the shopper has allowed analytics (Consent Mode friendly).
function analyticsAllowed(init) {
  try {
    const c = init && init.customerPrivacy;
    // If the API isn't present, default to allowed (matches current behaviour).
    return !c || c.analyticsProcessingAllowed !== false;
  } catch (e) { return true; }
}

/* --- Event subscriptions -------------------------------------------------- */
analytics.subscribe('page_viewed', (event) => {
  if (!analyticsAllowed(init)) return;
  gtag('event', 'page_view', {
    page_location: event.context.document.location.href,
    page_title: event.context.document.title,
  });
});

analytics.subscribe('search_submitted', (event) => {
  if (!analyticsAllowed(init)) return;
  gtag('event', 'search', { search_term: event.data.searchResult.query });
});

analytics.subscribe('product_viewed', (event) => {
  if (!analyticsAllowed(init)) return;
  const pv = event.data.productVariant;
  gtag('event', 'view_item', {
    currency: pv.price && pv.price.currencyCode,
    value: money(pv.price),
    items: [toItem(pv)],
  });
});

analytics.subscribe('product_added_to_cart', (event) => {
  if (!analyticsAllowed(init)) return;
  const line = event.data.cartLine;
  gtag('event', 'add_to_cart', {
    currency: line.cost && line.cost.totalAmount && line.cost.totalAmount.currencyCode,
    value: money(line.cost && line.cost.totalAmount),
    items: [toItem(line)],
  });
});

analytics.subscribe('checkout_started', (event) => {
  if (!analyticsAllowed(init)) return;
  const c = event.data.checkout;
  gtag('event', 'begin_checkout', {
    currency: c.currencyCode,
    value: money(c.totalPrice),
    items: toItems(c.lineItems),
  });
});

analytics.subscribe('checkout_completed', (event) => {
  if (!analyticsAllowed(init)) return;
  const c = event.data.checkout;
  gtag('event', 'purchase', {
    // transaction_id: order id is the dedup key — GA4 dedupes purchases with
    // the same transaction_id, so a page refresh on the thank-you page won't
    // double-count.
    transaction_id: (c.order && c.order.id) || c.token,
    currency: c.currencyCode,
    value: money(c.totalPrice),
    tax: money(c.totalTax),
    shipping: money(c.shippingLine && c.shippingLine.price),
    items: toItems(c.lineItems),
  });
});
