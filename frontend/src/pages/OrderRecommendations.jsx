import { useState, useEffect } from 'react'
import api from '../services/api'

function OrderRecommendations() {
  const [recommendations, setRecommendations] = useState([])
  const [status, setStatus] = useState('PENDING')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadRecommendations()
  }, [status])

  const loadRecommendations = async () => {
    setLoading(true)
    try {
      const response = await api.getOrderRecommendations(status, null)
      if (response.data?.success) {
        setRecommendations(response.data.data || [])
      }
    } catch (err) {
      console.error('Error loading recommendations:', err)
    } finally {
      setLoading(false)
    }
  }

  const approveRecommendation = async (id) => {
    try {
      await api.approveOrderRecommendation(id, 'user')
      loadRecommendations()
    } catch (err) {
      console.error('Error approving recommendation:', err)
    }
  }

  const dismissRecommendation = async (id) => {
    try {
      await api.dismissOrderRecommendation(id, 'User dismissed', 'user')
      loadRecommendations()
    } catch (err) {
      console.error('Error dismissing recommendation:', err)
    }
  }

  return (
    <div className="max-w-full mx-auto">
      <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 mb-2">💡 Order Recommendations</h1>
            <p className="text-gray-600">AI-generated purchase suggestions</p>
          </div>
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            className="border border-gray-300 rounded-lg px-4 py-2"
          >
            <option value="PENDING">Pending</option>
            <option value="APPROVED">Approved</option>
            <option value="ORDERED">Ordered</option>
            <option value="DISMISSED">Dismissed</option>
          </select>
        </div>
      </div>

      {loading ? (
        <div className="bg-white rounded-lg shadow-sm p-6">Loading...</div>
      ) : (
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="space-y-4">
            {recommendations.map((rec) => (
              <div key={rec.id} className="p-4 border border-gray-200 rounded-lg hover:shadow-md transition-shadow">
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <div className="font-semibold text-gray-900">{rec.productTitle || rec.sku}</div>
                      <span className={`px-2 py-1 text-xs rounded ${
                        rec.urgency === 'CRITICAL' ? 'bg-red-100 text-red-800' :
                        rec.urgency === 'HIGH' ? 'bg-orange-100 text-orange-800' :
                        rec.urgency === 'MEDIUM' ? 'bg-yellow-100 text-yellow-800' :
                        'bg-green-100 text-green-800'
                      }`}>
                        {rec.urgency}
                      </span>
                    </div>

                    <div className="mt-2 grid grid-cols-2 gap-4 text-sm">
                      <div>
                        <span className="text-gray-600">Current Stock:</span>
                        <span className="ml-2 font-medium">{rec.currentStock}</span>
                      </div>
                      <div>
                        <span className="text-gray-600">Recommended Qty:</span>
                        <span className="ml-2 font-medium text-blue-600">{rec.recommendedQuantity}</span>
                      </div>
                      <div>
                        <span className="text-gray-600">Supplier:</span>
                        <span className="ml-2 font-medium">{rec.supplierName}</span>
                      </div>
                      <div>
                        <span className="text-gray-600">Total Cost:</span>
                        <span className="ml-2 font-medium text-green-600">${rec.totalCost?.toFixed(2)}</span>
                      </div>
                    </div>

                    {rec.aiReasoning && (
                      <div className="mt-3 p-3 bg-gray-50 rounded text-sm text-gray-700">
                        <strong>AI Reasoning:</strong> {rec.aiReasoning}
                      </div>
                    )}
                  </div>

                  {status === 'PENDING' && (
                    <div className="ml-4 flex flex-col gap-2">
                      <button
                        onClick={() => approveRecommendation(rec.id)}
                        className="px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700 text-sm"
                      >
                        Approve
                      </button>
                      <button
                        onClick={() => dismissRecommendation(rec.id)}
                        className="px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700 text-sm"
                      >
                        Dismiss
                      </button>
                    </div>
                  )}
                </div>
              </div>
            ))}
            {recommendations.length === 0 && (
              <div className="text-center text-gray-500 py-8">No recommendations found</div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

export default OrderRecommendations
