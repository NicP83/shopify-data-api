/**
 * URL construction utilities for Shopify products and cart permalinks
 */

const SHOP_URL = 'hearnshobbies.myshopify.com'

/**
 * Constructs a product page URL
 * @param {string} handle - Product handle (URL-friendly identifier)
 * @returns {string} Full product URL
 */
export const getProductUrl = (handle) => {
  if (!handle) return null
  return `https://${SHOP_URL}/products/${handle}`
}

/**
 * Constructs a cart permalink URL for adding a product to cart
 * @param {string} variantId - Product variant ID (can be GraphQL format or numeric)
 * @param {number} quantity - Quantity to add to cart (default: 1)
 * @returns {string} Cart permalink URL
 */
export const getAddToCartUrl = (variantId, quantity = 1) => {
  if (!variantId) return null

  // Extract numeric ID from GraphQL ID format (gid://shopify/ProductVariant/123456)
  const numericId = variantId.includes('/')
    ? variantId.substring(variantId.lastIndexOf('/') + 1)
    : variantId

  return `https://${SHOP_URL}/cart/${numericId}:${quantity}`
}

/**
 * Formats price for display
 * @param {string|number} price - Price value
 * @returns {string} Formatted price with $ symbol
 */
export const formatPrice = (price) => {
  if (price === null || price === undefined) return 'N/A'
  return `$${parseFloat(price).toFixed(2)}`
}

/**
 * Truncates text to specified length
 * @param {string} text - Text to truncate
 * @param {number} maxLength - Maximum length
 * @returns {string} Truncated text with ellipsis if needed
 */
export const truncateText = (text, maxLength = 100) => {
  if (!text) return ''
  if (text.length <= maxLength) return text
  return text.substring(0, maxLength) + '...'
}

/**
 * Extracts numeric ID from Shopify GraphQL ID
 * @param {string} gid - GraphQL ID (e.g., "gid://shopify/Product/123456")
 * @returns {string} Numeric ID
 */
export const extractNumericId = (gid) => {
  if (!gid) return null
  return gid.includes('/') ? gid.substring(gid.lastIndexOf('/') + 1) : gid
}
