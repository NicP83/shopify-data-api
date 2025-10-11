import { useState } from 'react'
import VariantSelector from './VariantSelector'

function ProductDetail({ product, onClose }) {
  const [selectedVariant, setSelectedVariant] = useState(
    product.variants?.edges?.[0]?.node || null
  )

  const shopUrl = 'hearnshobbies.myshopify.com'

  // Construct product URL
  const productUrl = product.onlineStoreUrl ||
    (product.handle ? `https://${shopUrl}/products/${product.handle}` : null)

  // Construct cart permalink
  const getCartUrl = (variantId, quantity = 1) => {
    if (!variantId) return null
    // Extract numeric ID from GraphQL ID
    const numericId = variantId.includes('/')
      ? variantId.substring(variantId.lastIndexOf('/') + 1)
      : variantId
    return `https://${shopUrl}/cart/${numericId}:${quantity}`
  }

  const cartUrl = selectedVariant ? getCartUrl(selectedVariant.id) : null

  const handleCopyLink = (url, type) => {
    navigator.clipboard.writeText(url)
    alert(`${type} link copied to clipboard!`)
  }

  const handleOpenLink = (url) => {
    window.open(url, '_blank')
  }

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg max-w-4xl w-full max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
          <h2 className="text-2xl font-bold text-gray-900">Product Details</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 text-2xl"
          >
            ✕
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-6">
          {/* Product Image and Basic Info */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Image */}
            <div>
              {product.images?.edges?.[0]?.node?.url ? (
                <img
                  src={product.images.edges[0].node.url}
                  alt={product.title}
                  className="w-full rounded-lg shadow-md"
                />
              ) : (
                <div className="w-full h-64 bg-gray-100 rounded-lg flex items-center justify-center text-gray-400 text-6xl">
                  📦
                </div>
              )}
            </div>

            {/* Basic Info */}
            <div className="space-y-4">
              <div>
                <h3 className="text-2xl font-bold text-gray-900 mb-2">
                  {product.title}
                </h3>
                <div className="flex items-center gap-2 text-sm">
                  <span className={`px-3 py-1 rounded-full ${
                    product.status === 'ACTIVE' ? 'bg-green-100 text-green-800' :
                    product.status === 'DRAFT' ? 'bg-yellow-100 text-yellow-800' :
                    'bg-gray-100 text-gray-800'
                  }`}>
                    {product.status}
                  </span>
                </div>
              </div>

              {product.description && (
                <div>
                  <h4 className="font-semibold text-gray-700 mb-1">Description</h4>
                  <p className="text-gray-600 text-sm">{product.description}</p>
                </div>
              )}

              {product.vendor && (
                <div>
                  <h4 className="font-semibold text-gray-700 mb-1">Vendor</h4>
                  <p className="text-gray-600">{product.vendor}</p>
                </div>
              )}

              {product.productType && (
                <div>
                  <h4 className="font-semibold text-gray-700 mb-1">Product Type</h4>
                  <p className="text-gray-600">{product.productType}</p>
                </div>
              )}

              {product.tags && product.tags.length > 0 && (
                <div>
                  <h4 className="font-semibold text-gray-700 mb-1">Tags</h4>
                  <div className="flex flex-wrap gap-2">
                    {product.tags.map((tag, index) => (
                      <span
                        key={index}
                        className="px-2 py-1 bg-gray-100 text-gray-700 text-xs rounded"
                      >
                        {tag}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Variant Selector */}
          {product.variants?.edges && product.variants.edges.length > 0 && (
            <div className="border-t border-gray-200 pt-6">
              <VariantSelector
                variants={product.variants.edges.map(edge => edge.node)}
                selectedVariant={selectedVariant}
                onSelectVariant={setSelectedVariant}
              />
            </div>
          )}

          {/* Action Buttons */}
          <div className="border-t border-gray-200 pt-6 space-y-4">
            <h4 className="font-semibold text-gray-900 text-lg">Actions</h4>

            {/* View Product */}
            {productUrl && (
              <div className="space-y-2">
                <label className="text-sm font-medium text-gray-700">Product Page</label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={productUrl}
                    readOnly
                    className="flex-1 px-3 py-2 border border-gray-300 rounded-lg bg-gray-50 text-sm"
                  />
                  <button
                    onClick={() => handleCopyLink(productUrl, 'Product')}
                    className="btn-secondary"
                  >
                    📋 Copy
                  </button>
                  <button
                    onClick={() => handleOpenLink(productUrl)}
                    className="btn-primary"
                  >
                    🔗 View
                  </button>
                </div>
              </div>
            )}

            {/* Add to Cart */}
            {cartUrl && (
              <div className="space-y-2">
                <label className="text-sm font-medium text-gray-700">
                  Add to Cart Link
                  {selectedVariant && (
                    <span className="ml-2 text-xs text-gray-500">
                      ({selectedVariant.title})
                    </span>
                  )}
                </label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={cartUrl}
                    readOnly
                    className="flex-1 px-3 py-2 border border-gray-300 rounded-lg bg-gray-50 text-sm"
                  />
                  <button
                    onClick={() => handleCopyLink(cartUrl, 'Cart')}
                    className="btn-secondary"
                  >
                    📋 Copy
                  </button>
                  <button
                    onClick={() => handleOpenLink(cartUrl)}
                    className="btn-primary"
                  >
                    🛒 Add to Cart
                  </button>
                </div>
                <p className="text-xs text-gray-500">
                  Share this link with customers. When clicked, the item will be automatically added to their cart.
                </p>
              </div>
            )}
          </div>

          {/* Close Button */}
          <div className="border-t border-gray-200 pt-6">
            <button
              onClick={onClose}
              className="w-full btn-secondary"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

export default ProductDetail
