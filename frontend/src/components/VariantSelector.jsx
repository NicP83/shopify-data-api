function VariantSelector({ variants, selectedVariant, onSelectVariant }) {
  if (!variants || variants.length === 0) {
    return null
  }

  // If only one variant and it's a "Default Title", don't show selector
  if (variants.length === 1 && variants[0].title === 'Default Title') {
    return (
      <div className="space-y-2">
        <h4 className="font-semibold text-gray-900">Variant Information</h4>
        <div className="bg-gray-50 rounded-lg p-4">
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <span className="text-gray-600">SKU:</span>
              <span className="ml-2 font-medium">{variants[0].sku || 'N/A'}</span>
            </div>
            <div>
              <span className="text-gray-600">Price:</span>
              <span className="ml-2 font-medium text-primary-600">
                ${variants[0].price}
              </span>
            </div>
            {variants[0].inventoryQuantity !== undefined && (
              <div>
                <span className="text-gray-600">In Stock:</span>
                <span className="ml-2 font-medium">
                  {variants[0].inventoryQuantity} units
                </span>
              </div>
            )}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <h4 className="font-semibold text-gray-900">Select Variant</h4>

      <div className="grid grid-cols-1 gap-3">
        {variants.map((variant) => {
          const isSelected = selectedVariant?.id === variant.id

          return (
            <button
              key={variant.id}
              onClick={() => onSelectVariant(variant)}
              className={`text-left p-4 rounded-lg border-2 transition-all duration-200 ${
                isSelected
                  ? 'border-primary-500 bg-primary-50'
                  : 'border-gray-200 bg-white hover:border-gray-300'
              }`}
            >
              <div className="flex items-center justify-between">
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-gray-900">
                      {variant.title}
                    </span>
                    {isSelected && (
                      <span className="text-primary-600">✓</span>
                    )}
                  </div>
                  <div className="flex items-center gap-4 mt-1 text-sm text-gray-600">
                    {variant.sku && (
                      <span>SKU: {variant.sku}</span>
                    )}
                    {variant.inventoryQuantity !== undefined && (
                      <span>Stock: {variant.inventoryQuantity}</span>
                    )}
                  </div>
                </div>
                <div className="text-right">
                  <div className="text-lg font-bold text-primary-600">
                    ${variant.price}
                  </div>
                </div>
              </div>
            </button>
          )
        })}
      </div>

      {selectedVariant && (
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-3 text-sm text-blue-800">
          Selected: <strong>{selectedVariant.title}</strong> - ${selectedVariant.price}
        </div>
      )}
    </div>
  )
}

export default VariantSelector
