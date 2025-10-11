function ProductCard({ product, onClick }) {
  const firstImage = product.images?.edges?.[0]?.node?.url
  const firstVariant = product.variants?.edges?.[0]?.node
  const price = firstVariant?.price || 'N/A'
  const sku = firstVariant?.sku || 'N/A'

  const statusColors = {
    ACTIVE: 'bg-green-100 text-green-800',
    DRAFT: 'bg-yellow-100 text-yellow-800',
    ARCHIVED: 'bg-gray-100 text-gray-800',
  }

  return (
    <div
      onClick={onClick}
      className="card hover:shadow-xl transition-all duration-200 cursor-pointer group"
    >
      {/* Product Image */}
      <div className="relative w-full h-48 bg-gray-100 rounded-lg overflow-hidden mb-4">
        {firstImage ? (
          <img
            src={firstImage}
            alt={product.title}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-200"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-gray-400 text-4xl">
            📦
          </div>
        )}
        <div className="absolute top-2 right-2">
          <span className={`px-2 py-1 rounded text-xs font-medium ${statusColors[product.status] || 'bg-gray-100 text-gray-800'}`}>
            {product.status}
          </span>
        </div>
      </div>

      {/* Product Info */}
      <div className="space-y-2">
        <h3 className="font-semibold text-gray-900 line-clamp-2 group-hover:text-primary-600 transition-colors">
          {product.title}
        </h3>

        <div className="flex items-center justify-between text-sm">
          <span className="text-gray-600">SKU: {sku}</span>
          <span className="text-lg font-bold text-primary-600">${price}</span>
        </div>

        {product.vendor && (
          <div className="text-sm text-gray-500">
            Vendor: {product.vendor}
          </div>
        )}

        {product.variants?.edges && (
          <div className="text-sm text-gray-500">
            {product.variants.edges.length} variant{product.variants.edges.length !== 1 ? 's' : ''}
          </div>
        )}
      </div>

      {/* Hover indicator */}
      <div className="mt-4 text-center text-sm text-primary-600 opacity-0 group-hover:opacity-100 transition-opacity">
        Click for details →
      </div>
    </div>
  )
}

export default ProductCard
