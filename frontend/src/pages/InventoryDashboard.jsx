import { useState, useEffect } from 'react'
import api from '../services/api'

function InventoryDashboard() {
  const [dashboard, setDashboard] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    loadDashboard()
    const interval = setInterval(loadDashboard, 60000) // Refresh every minute
    return () => clearInterval(interval)
  }, [])

  const loadDashboard = async () => {
    try {
      const response = await api.getInventoryDashboard()
      if (response.data?.success) {
        setDashboard(response.data.data)
      }
    } catch (err) {
      console.error('Error loading dashboard:', err)
      setError('Failed to load dashboard data')
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return <div className="p-6">Loading...</div>
  }

  if (error) {
    return <div className="p-6 text-red-600">{error}</div>
  }

  return (
    <div className="max-w-full mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">📊 Inventory Dashboard</h1>
        <p className="text-gray-600">Real-time overview of your inventory status</p>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-4 gap-6 mb-6">
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="text-sm font-medium text-gray-600 mb-1">Total Products</div>
          <div className="text-3xl font-bold text-gray-900">{dashboard?.totalProducts || 0}</div>
        </div>

        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="text-sm font-medium text-gray-600 mb-1">Critical Alerts</div>
          <div className="text-3xl font-bold text-red-600">{dashboard?.criticalAlerts || 0}</div>
        </div>

        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="text-sm font-medium text-gray-600 mb-1">Pending Recommendations</div>
          <div className="text-3xl font-bold text-blue-600">{dashboard?.pendingRecommendations || 0}</div>
        </div>

        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="text-sm font-medium text-gray-600 mb-1">Average Velocity</div>
          <div className="text-3xl font-bold text-green-600">{dashboard?.averageVelocity?.toFixed(2) || '0.00'}</div>
          <div className="text-xs text-gray-500 mt-1">units/day</div>
        </div>
      </div>

      {/* Top Alerts */}
      <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
        <h2 className="text-lg font-bold text-gray-900 mb-4">🚨 Recent Critical Alerts</h2>
        <div className="space-y-2">
          {dashboard?.recentAlerts?.slice(0, 5).map((alert, idx) => (
            <div key={idx} className="flex items-center justify-between p-3 bg-red-50 rounded-lg">
              <div>
                <div className="font-medium text-gray-900">{alert.productTitle || alert.sku}</div>
                <div className="text-sm text-gray-600">{alert.message}</div>
              </div>
              <div className="text-sm font-semibold text-red-600">
                {alert.daysUntilStockout} days
              </div>
            </div>
          )) || <div className="text-gray-500 text-sm">No critical alerts</div>}
        </div>
      </div>

      {/* Top Recommendations */}
      <div className="bg-white rounded-lg shadow-sm p-6">
        <h2 className="text-lg font-bold text-gray-900 mb-4">💡 Top Recommendations</h2>
        <div className="space-y-2">
          {dashboard?.topRecommendations?.slice(0, 5).map((rec, idx) => (
            <div key={idx} className="flex items-center justify-between p-3 bg-blue-50 rounded-lg">
              <div>
                <div className="font-medium text-gray-900">{rec.productTitle || rec.sku}</div>
                <div className="text-sm text-gray-600">Supplier: {rec.supplierName}</div>
              </div>
              <div className="text-right">
                <div className="font-semibold text-blue-600">{rec.recommendedQuantity} units</div>
                <div className="text-sm text-gray-600">${rec.totalCost?.toFixed(2)}</div>
              </div>
            </div>
          )) || <div className="text-gray-500 text-sm">No recommendations</div>}
        </div>
      </div>
    </div>
  )
}

export default InventoryDashboard
