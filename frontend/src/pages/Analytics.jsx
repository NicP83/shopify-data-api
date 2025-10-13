import { useState, useEffect } from 'react'
import api from '../services/api'
import AnalyticsCard from '../components/AnalyticsCard'

function Analytics() {
  const [analyticsData, setAnalyticsData] = useState(null)
  const [channelData, setChannelData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [channelLoading, setChannelLoading] = useState(true)
  const [error, setError] = useState(null)
  const [channelError, setChannelError] = useState(null)
  const [selectedPeriod, setSelectedPeriod] = useState('7d')
  const [selectedTab, setSelectedTab] = useState('online') // 'online' or 'channels'

  const periods = [
    { id: '1d', label: '1 Day', icon: '📅' },
    { id: '7d', label: '7 Days', icon: '📊' },
    { id: '30d', label: '30 Days', icon: '📈' },
    { id: '90d', label: '90 Days', icon: '📉' }
  ]

  const tabs = [
    { id: 'online', label: 'Online Sales', icon: '🛒' },
    { id: 'channels', label: 'All Channels', icon: '📊' }
  ]

  useEffect(() => {
    fetchAllAnalytics()
    fetchAllChannelAnalytics()
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

  const fetchAllChannelAnalytics = async () => {
    setChannelLoading(true)
    setChannelError(null)

    try {
      const response = await api.getAllChannelSalesAnalytics()
      setChannelData(response.data.data)
    } catch (err) {
      console.error('Error fetching channel analytics:', err)
      setChannelError('Failed to load channel analytics data. Please try again.')
    } finally {
      setChannelLoading(false)
    }
  }

  const refreshAll = () => {
    fetchAllAnalytics()
    fetchAllChannelAnalytics()
  }

  const getCurrentAnalytics = () => {
    if (selectedTab === 'channels') {
      if (!channelData || !channelData[selectedPeriod]) {
        return null
      }
      return channelData[selectedPeriod]
    } else {
      if (!analyticsData || !analyticsData[selectedPeriod]) {
        return null
      }
      return analyticsData[selectedPeriod]
    }
  }

  const isLoading = selectedTab === 'online' ? loading : channelLoading
  const currentError = selectedTab === 'online' ? error : channelError
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
          onClick={refreshAll}
          disabled={loading || channelLoading}
          className="btn-secondary"
        >
          {(loading || channelLoading) ? 'Refreshing...' : '🔄 Refresh'}
        </button>
      </div>

      {/* Tab Selector */}
      <div className="card">
        <div className="flex gap-2">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setSelectedTab(tab.id)}
              className={`flex-1 px-6 py-3 rounded-lg font-medium transition-all ${
                selectedTab === tab.id
                  ? 'bg-primary-600 text-white shadow-md'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              }`}
            >
              <span className="text-xl mr-2">{tab.icon}</span>
              {tab.label}
            </button>
          ))}
        </div>
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
      {currentError && (
        <div className="card bg-red-50 border-red-200">
          <div className="flex items-center gap-3">
            <span className="text-3xl">⚠️</span>
            <div>
              <h3 className="font-semibold text-red-900">Error Loading Analytics</h3>
              <p className="text-red-700 text-sm">{currentError}</p>
            </div>
          </div>
        </div>
      )}

      {/* Loading State */}
      {isLoading && (
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
      {!isLoading && !currentError && currentData && (
        <>
          {/* Period Info */}
          <div className="card bg-blue-50 border-blue-200">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="font-semibold text-gray-900">
                  {periods.find(p => p.id === selectedPeriod)?.label} Report
                </h3>
                <p className="text-sm text-gray-600 mt-1">
                  {selectedTab === 'channels'
                    ? `${currentData.totalOrders || 0} orders processed`
                    : `${currentData.orderCount || 0} orders processed`}
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
          {selectedTab === 'online' ? (
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
          ) : (
            /* Channel Breakdown */
            <div className="space-y-6">
              {/* Grand Total */}
              <div>
                <h3 className="text-lg font-semibold text-gray-900 mb-3">📊 Grand Total (No Double-Counting)</h3>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                  <AnalyticsCard
                    title="Total Revenue"
                    value={currentData.totalRevenue || 0}
                    currency="$"
                    comparisonData={currentData.yearOverYearComparison}
                    icon="💰"
                    type="sales"
                  />
                  <AnalyticsCard
                    title="Total Orders"
                    value={currentData.totalOrders || 0}
                    currency=""
                    comparisonData={null}
                    icon="📦"
                    type="orders"
                  />
                  <AnalyticsCard
                    title="Total Items Sold"
                    value={currentData.totalItems || 0}
                    currency=""
                    comparisonData={null}
                    icon="📋"
                    type="orders"
                  />
                </div>
              </div>

              {/* Pure In-Store Sales Section */}
              <div className="card bg-blue-50 border-blue-200">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">🏪 Pure In-Store Sales (Walk-in Customers)</h3>

                <div className="mb-4">
                  <h4 className="text-md font-semibold text-gray-800 mb-2">The Hobbyman (Narre Warren)</h4>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <AnalyticsCard
                      title="Revenue"
                      value={currentData.hobbyman?.revenue || 0}
                      currency="$"
                      comparisonData={null}
                      icon="💵"
                      type="sales"
                    />
                    <AnalyticsCard
                      title="Orders"
                      value={currentData.hobbyman?.orderCount || 0}
                      currency=""
                      comparisonData={null}
                      icon="🛍️"
                      type="orders"
                    />
                    <AnalyticsCard
                      title="Items Sold"
                      value={currentData.hobbyman?.itemCount || 0}
                      currency=""
                      comparisonData={null}
                      icon="📦"
                      type="orders"
                    />
                  </div>
                </div>

                <div>
                  <h4 className="text-md font-semibold text-gray-800 mb-2">Hearns Hobbies (Melbourne CBD)</h4>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <AnalyticsCard
                      title="Revenue"
                      value={currentData.hearnsHobbies?.revenue || 0}
                      currency="$"
                      comparisonData={null}
                      icon="💵"
                      type="sales"
                    />
                    <AnalyticsCard
                      title="Orders"
                      value={currentData.hearnsHobbies?.orderCount || 0}
                      currency=""
                      comparisonData={null}
                      icon="🛍️"
                      type="orders"
                    />
                    <AnalyticsCard
                      title="Items Sold"
                      value={currentData.hearnsHobbies?.itemCount || 0}
                      currency=""
                      comparisonData={null}
                      icon="📦"
                      type="orders"
                    />
                  </div>
                </div>
              </div>

              {/* Online Fulfillment Section */}
              <div className="card bg-green-50 border-green-200">
                <h3 className="text-lg font-semibold text-gray-900 mb-2">📦 Online Orders Fulfilled In-Store</h3>
                <p className="text-sm text-gray-600 mb-4">Online orders processed and invoiced at physical store locations</p>

                <div className="mb-4">
                  <h4 className="text-md font-semibold text-gray-800 mb-2">The Hobbyman (Fulfillment)</h4>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <AnalyticsCard
                      title="Revenue"
                      value={currentData.hobbymanFulfillment?.revenue || 0}
                      currency="$"
                      comparisonData={null}
                      icon="💵"
                      type="sales"
                    />
                    <AnalyticsCard
                      title="Orders"
                      value={currentData.hobbymanFulfillment?.orderCount || 0}
                      currency=""
                      comparisonData={null}
                      icon="📦"
                      type="orders"
                    />
                    <AnalyticsCard
                      title="Items"
                      value={currentData.hobbymanFulfillment?.itemCount || 0}
                      currency=""
                      comparisonData={null}
                      icon="📋"
                      type="orders"
                    />
                  </div>
                </div>

                <div>
                  <h4 className="text-md font-semibold text-gray-800 mb-2">Hearns Hobbies (Fulfillment)</h4>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <AnalyticsCard
                      title="Revenue"
                      value={currentData.hearnsFulfillment?.revenue || 0}
                      currency="$"
                      comparisonData={null}
                      icon="💵"
                      type="sales"
                    />
                    <AnalyticsCard
                      title="Orders"
                      value={currentData.hearnsFulfillment?.orderCount || 0}
                      currency=""
                      comparisonData={null}
                      icon="📦"
                      type="orders"
                    />
                    <AnalyticsCard
                      title="Items"
                      value={currentData.hearnsFulfillment?.itemCount || 0}
                      currency=""
                      comparisonData={null}
                      icon="📋"
                      type="orders"
                    />
                  </div>
                </div>
              </div>

              {/* Online Sales Summary */}
              <div className="card bg-purple-50 border-purple-200">
                <h3 className="text-lg font-semibold text-gray-900 mb-2">🛒 Online Sales Summary (Shopify)</h3>
                <p className="text-sm text-gray-600 mb-4">Total online orders placed through Shopify</p>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
                  <AnalyticsCard
                    title="Total Online Revenue"
                    value={currentData.shopify?.revenue || 0}
                    currency="$"
                    comparisonData={null}
                    icon="💰"
                    type="sales"
                  />
                  <AnalyticsCard
                    title="Total Online Orders"
                    value={currentData.shopify?.orderCount || 0}
                    currency=""
                    comparisonData={null}
                    icon="🛍️"
                    type="orders"
                  />
                  <AnalyticsCard
                    title="Items"
                    value={currentData.shopify?.itemCount || 0}
                    currency=""
                    comparisonData={null}
                    icon="📦"
                    type="orders"
                  />
                </div>

                {/* Fulfillment Status Breakdown */}
                <div className="border-t pt-4">
                  <h4 className="text-md font-semibold text-gray-800 mb-3">Fulfillment Status</h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="bg-white rounded-lg p-4 border border-green-200">
                      <div className="flex items-center justify-between mb-2">
                        <span className="text-sm font-medium text-gray-700">Fulfilled & Invoiced</span>
                        <span className="text-2xl">✅</span>
                      </div>
                      <div className="text-2xl font-bold text-green-600">
                        ${(currentData.onlineFulfilledRevenue || 0).toLocaleString()}
                      </div>
                      <div className="text-sm text-gray-600 mt-1">
                        {currentData.onlineFulfilledOrders || 0} orders
                      </div>
                    </div>
                    <div className="bg-white rounded-lg p-4 border border-yellow-200">
                      <div className="flex items-center justify-between mb-2">
                        <span className="text-sm font-medium text-gray-700">Pending/Unfulfilled</span>
                        <span className="text-2xl">⏳</span>
                      </div>
                      <div className="text-2xl font-bold text-yellow-600">
                        ${(currentData.onlinePendingRevenue || 0).toLocaleString()}
                      </div>
                      <div className="text-sm text-gray-600 mt-1">
                        {currentData.onlinePendingOrders || 0} orders
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Additional Insights */}
          {currentData.orderCount > 0 && selectedTab === 'online' && (
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
          {((selectedTab === 'online' && currentData.orderCount === 0) ||
            (selectedTab === 'channels' && currentData.totalOrders === 0)) && (
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
