import { useState, useEffect } from 'react'
import api from '../services/api'

function StockAlerts() {
  const [alerts, setAlerts] = useState([])
  const [level, setLevel] = useState('WARNING')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadAlerts()
  }, [level])

  const loadAlerts = async () => {
    setLoading(true)
    try {
      const response = await api.getLowStockAlerts(level, 100)
      if (response.data?.success) {
        setAlerts(response.data.data || [])
      }
    } catch (err) {
      console.error('Error loading alerts:', err)
    } finally {
      setLoading(false)
    }
  }

  const resolveAlert = async (id) => {
    try {
      await api.resolveStockAlert(id)
      loadAlerts()
    } catch (err) {
      console.error('Error resolving alert:', err)
    }
  }

  return (
    <div className="max-w-full mx-auto">
      <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 mb-2">🚨 Stock Alerts</h1>
            <p className="text-gray-600">Products requiring immediate attention</p>
          </div>
          <div>
            <select
              value={level}
              onChange={(e) => setLevel(e.target.value)}
              className="border border-gray-300 rounded-lg px-4 py-2"
            >
              <option value="CRITICAL">Critical</option>
              <option value="WARNING">Warning</option>
              <option value="INFO">Info</option>
            </select>
          </div>
        </div>
      </div>

      {loading ? (
        <div className="bg-white rounded-lg shadow-sm p-6">Loading...</div>
      ) : (
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="space-y-3">
            {alerts.map((alert) => (
              <div
                key={alert.id}
                className={`p-4 rounded-lg border ${
                  alert.alertLevel === 'CRITICAL' ? 'bg-red-50 border-red-200' :
                  alert.alertLevel === 'WARNING' ? 'bg-yellow-50 border-yellow-200' :
                  'bg-blue-50 border-blue-200'
                }`}
              >
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="font-semibold text-gray-900">{alert.productTitle || alert.sku}</div>
                    <div className="text-sm text-gray-600 mt-1">{alert.message}</div>
                    <div className="mt-2 flex gap-4 text-xs text-gray-500">
                      <span>Current Stock: {alert.currentStock}</span>
                      <span>Reorder Point: {alert.reorderPoint}</span>
                      <span>Days Until Stockout: {alert.daysUntilStockout || 'N/A'}</span>
                    </div>
                  </div>
                  <button
                    onClick={() => resolveAlert(alert.id)}
                    className="ml-4 px-3 py-1 bg-green-600 text-white rounded hover:bg-green-700 text-sm"
                  >
                    Resolve
                  </button>
                </div>
              </div>
            ))}
            {alerts.length === 0 && (
              <div className="text-center text-gray-500 py-8">No alerts at this level</div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

export default StockAlerts
