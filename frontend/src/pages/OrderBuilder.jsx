import { useState, useEffect } from 'react'
import api from '../services/api'

function OrderBuilder() {
  const [recommendations, setRecommendations] = useState([])
  const [selectedItems, setSelectedItems] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadRecommendations()
  }, [])

  const loadRecommendations = async () => {
    try {
      const response = await api.getOrderRecommendations('PENDING', null)
      if (response.data?.success) {
        setRecommendations(response.data.data || [])
      }
    } catch (err) {
      console.error('Error loading recommendations:', err)
    } finally {
      setLoading(false)
    }
  }

  const toggleItem = (rec) => {
    setSelectedItems(prev =>
      prev.find(item => item.id === rec.id)
        ? prev.filter(item => item.id !== rec.id)
        : [...prev, rec]
    )
  }

  const totalCost = selectedItems.reduce((sum, item) => sum + (item.totalCost || 0), 0)
  const totalUnits = selectedItems.reduce((sum, item) => sum + (item.recommendedQuantity || 0), 0)

  const groupBySupplier = () => {
    const groups = {}
    selectedItems.forEach(item => {
      const supplier = item.supplierName || 'Unknown'
      if (!groups[supplier]) {
        groups[supplier] = []
      }
      groups[supplier].push(item)
    })
    return groups
  }

  return (
    <div className="max-w-full mx-auto">
      <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">🛒 Order Builder</h1>
        <p className="text-gray-600">Build purchase orders from recommendations</p>
      </div>

      <div className="grid grid-cols-3 gap-6">
        {/* Available Recommendations */}
        <div className="col-span-2 bg-white rounded-lg shadow-sm p-6">
          <h2 className="text-lg font-bold text-gray-900 mb-4">Available Recommendations</h2>
          {loading ? (
            <div>Loading...</div>
          ) : (
            <div className="space-y-2 max-h-[600px] overflow-y-auto">
              {recommendations.map((rec) => (
                <label
                  key={rec.id}
                  className="flex items-start gap-3 p-3 border border-gray-200 rounded-lg hover:bg-gray-50 cursor-pointer"
                >
                  <input
                    type="checkbox"
                    checked={selectedItems.some(item => item.id === rec.id)}
                    onChange={() => toggleItem(rec)}
                    className="mt-1"
                  />
                  <div className="flex-1">
                    <div className="font-medium text-gray-900">{rec.productTitle || rec.sku}</div>
                    <div className="text-sm text-gray-600 mt-1">
                      {rec.supplierName} • {rec.recommendedQuantity} units • ${rec.totalCost?.toFixed(2)}
                    </div>
                  </div>
                </label>
              ))}
            </div>
          )}
        </div>

        {/* Order Cart */}
        <div className="bg-white rounded-lg shadow-sm p-6">
          <h2 className="text-lg font-bold text-gray-900 mb-4">Order Cart</h2>

          <div className="mb-4 p-4 bg-blue-50 rounded-lg">
            <div className="text-sm text-gray-600">Total Items</div>
            <div className="text-2xl font-bold text-gray-900">{selectedItems.length}</div>
            <div className="text-sm text-gray-600 mt-2">Total Units</div>
            <div className="text-2xl font-bold text-gray-900">{totalUnits}</div>
            <div className="text-sm text-gray-600 mt-2">Total Cost</div>
            <div className="text-3xl font-bold text-green-600">${totalCost.toFixed(2)}</div>
          </div>

          {Object.keys(groupBySupplier()).length > 0 && (
            <div className="mb-4">
              <h3 className="font-semibold text-gray-900 mb-2">By Supplier:</h3>
              <div className="space-y-2">
                {Object.entries(groupBySupplier()).map(([supplier, items]) => (
                  <div key={supplier} className="p-2 bg-gray-50 rounded text-sm">
                    <div className="font-medium">{supplier}</div>
                    <div className="text-gray-600">{items.length} items</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          <button
            disabled={selectedItems.length === 0}
            className="w-full px-4 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-300 font-semibold"
          >
            Create Purchase Orders
          </button>
        </div>
      </div>
    </div>
  )
}

export default OrderBuilder
