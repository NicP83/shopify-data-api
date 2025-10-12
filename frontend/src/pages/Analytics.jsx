import { useState, useEffect } from 'react'
import api from '../services/api'
import AnalyticsCard from '../components/AnalyticsCard'

function Analytics() {
  const [analyticsData, setAnalyticsData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [selectedPeriod, setSelectedPeriod] = useState('7d')

  const periods = [
    { id: '1d', label: '1 Day', icon: '📅' },
    { id: '7d', label: '7 Days', icon: '📊' },
    { id: '30d', label: '30 Days', icon: '📈' },
    { id: '90d', label: '90 Days', icon: '📉' }
  ]

  useEffect(() => {
    fetchAllAnalytics()
  }, [])

  const fetchAllAnalytics = async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await api.getAllSalesAnalytics()
      setAnalyticsData(response.data.data)
    } catch (err) {
      console.error('Error fetching analytics:', err)
      setError('Failed to load analytics data. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const getCurrentAnalytics = () => {
    if (!analyticsData || !analyticsData[selectedPeriod]) {
      return null
    }
    return analyticsData[selectedPeriod]
  }

  const currentData = getCurrentAnalytics()

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Sales Analytics</h1>
          <p className="text-gray-600 mt-1">
            Track your sales performance with year-over-year comparisons
          </p>
        </div>
        <button
          onClick={fetchAllAnalytics}
          disabled={loading}
          className="btn-secondary"
        >
          {loading ? 'Refreshing...' : '🔄 Refresh'}
        </button>
      </div>

      {/* Period Selector */}
      <div className="card">
        <div className="flex gap-2 overflow-x-auto">
          {periods.map((period) => (
            <button
              key={period.id}
              onClick={() => setSelectedPeriod(period.id)}
              className={`flex-1 min-w-[120px] px-4 py-3 rounded-lg font-medium transition-all ${
                selectedPeriod === period.id
                  ? 'bg-primary-600 text-white shadow-md'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              }`}
            >
              <div className="text-2xl mb-1">{period.icon}</div>
              <div className="text-sm">{period.label}</div>
            </button>
          ))}
        </div>
      </div>

      {/* Error State */}
      {error && (
        <div className="card bg-red-50 border-red-200">
          <div className="flex items-center gap-3">
            <span className="text-3xl">⚠️</span>
            <div>
              <h3 className="font-semibold text-red-900">Error Loading Analytics</h3>
              <p className="text-red-700 text-sm">{error}</p>
            </div>
          </div>
        </div>
      )}

      {/* Loading State */}
      {loading && (
        <div className="card">
          <div className="flex items-center justify-center py-12">
            <div className="text-center">
              <div className="animate-spin text-5xl mb-4">⚙️</div>
              <p className="text-gray-600">Loading analytics data...</p>
            </div>
          </div>
        </div>
      )}

      {/* Analytics Cards */}
      {!loading && !error && currentData && (
        <>
          {/* Period Info */}
          <div className="card bg-blue-50 border-blue-200">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="font-semibold text-gray-900">
                  {periods.find(p => p.id === selectedPeriod)?.label} Report
                </h3>
                <p className="text-sm text-gray-600 mt-1">
                  {currentData.orderCount || 0} orders processed
                </p>
              </div>
              <div className="text-right text-sm text-gray-600">
                <div>
                  From: {new Date(currentData.periodStart).toLocaleDateString()}
                </div>
                <div>
                  To: {new Date(currentData.periodEnd).toLocaleDateString()}
                </div>
              </div>
            </div>
          </div>

          {/* Metrics Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {/* Total Sales */}
            <AnalyticsCard
              title="Total Sales"
              value={currentData.totalSales}
              currency={currentData.currencyCode === 'AUD' ? '$' : currentData.currencyCode}
              comparisonData={currentData.yearOverYearComparison}
              icon="💰"
              type="sales"
            />

            {/* Average Sale */}
            <AnalyticsCard
              title="Average Sale"
              value={currentData.averageSale}
              currency={currentData.currencyCode === 'AUD' ? '$' : currentData.currencyCode}
              comparisonData={null}
              icon="📊"
              type="average"
            />

            {/* Total Freight */}
            <AnalyticsCard
              title="Total Freight Paid"
              value={currentData.totalFreight}
              currency={currentData.currencyCode === 'AUD' ? '$' : currentData.currencyCode}
              comparisonData={null}
              icon="🚚"
              type="freight"
            />

            {/* Total Discounts */}
            <AnalyticsCard
              title="Total Discounts Given"
              value={currentData.totalDiscounts}
              currency={currentData.currencyCode === 'AUD' ? '$' : currentData.currencyCode}
              comparisonData={null}
              icon="🏷️"
              type="discount"
            />
          </div>

          {/* Additional Insights */}
          {currentData.orderCount > 0 && (
            <div className="card bg-green-50 border-green-200">
              <h3 className="font-semibold text-gray-900 mb-3">📈 Key Insights</h3>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
                <div>
                  <span className="text-gray-600">Orders:</span>
                  <span className="font-semibold text-gray-900 ml-2">
                    {currentData.orderCount}
                  </span>
                </div>
                <div>
                  <span className="text-gray-600">Avg Freight/Order:</span>
                  <span className="font-semibold text-gray-900 ml-2">
                    ${(parseFloat(currentData.totalFreight) / currentData.orderCount).toFixed(2)}
                  </span>
                </div>
                <div>
                  <span className="text-gray-600">Avg Discount/Order:</span>
                  <span className="font-semibold text-gray-900 ml-2">
                    ${(parseFloat(currentData.totalDiscounts) / currentData.orderCount).toFixed(2)}
                  </span>
                </div>
              </div>
            </div>
          )}

          {/* No Data Message */}
          {currentData.orderCount === 0 && (
            <div className="card bg-yellow-50 border-yellow-200">
              <div className="flex items-center gap-3">
                <span className="text-3xl">📭</span>
                <div>
                  <h3 className="font-semibold text-yellow-900">No Orders Found</h3>
                  <p className="text-yellow-700 text-sm">
                    There are no orders in the selected period.
                  </p>
                </div>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}

export default Analytics
