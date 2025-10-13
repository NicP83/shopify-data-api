import { useState, useEffect } from 'react'
import api from '../services/api'
import OrderDetailCard from '../components/OrderDetailCard'

function OrdersToFulfill() {
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [expandedOrderId, setExpandedOrderId] = useState(null)
  const [searchTerm, setSearchTerm] = useState('')

  useEffect(() => {
    fetchOrders()
  }, [])

  const fetchOrders = async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await api.getPendingFulfillments()
      setOrders(response.data.data || [])
    } catch (err) {
      console.error('Error fetching orders:', err)
      setError('Failed to load orders. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const toggleOrderDetails = (orderId) => {
    setExpandedOrderId(expandedOrderId === orderId ? null : orderId)
  }

  const filteredOrders = orders.filter(order => {
    if (!searchTerm) return true
    const search = searchTerm.toLowerCase()
    return (
      order.orderName?.toLowerCase().includes(search) ||
      order.customerName?.toLowerCase().includes(search) ||
      order.customerEmail?.toLowerCase().includes(search)
    )
  })

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between no-print">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Orders to Fulfill</h1>
          <p className="text-gray-600 mt-1">
            Shopify orders pending fulfillment in CRS ERP
          </p>
        </div>
        <button
          onClick={fetchOrders}
          disabled={loading}
          className="btn-secondary"
        >
          {loading ? 'Refreshing...' : '🔄 Refresh'}
        </button>
      </div>

      {/* Search Bar */}
      <div className="card no-print">
        <input
          type="text"
          placeholder="Search by order #, customer name, or email..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
        />
      </div>

      {/* Error State */}
      {error && (
        <div className="card bg-red-50 border-red-200">
          <div className="flex items-center gap-3">
            <span className="text-3xl">⚠️</span>
            <div>
              <h3 className="font-semibold text-red-900">Error Loading Orders</h3>
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
              <p className="text-gray-600">Loading orders...</p>
            </div>
          </div>
        </div>
      )}

      {/* Orders Count */}
      {!loading && !error && (
        <div className="card bg-blue-50 border-blue-200 no-print">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="font-semibold text-gray-900">
                {filteredOrders.length} {filteredOrders.length === 1 ? 'Order' : 'Orders'} Pending Fulfillment
              </h3>
              <p className="text-sm text-gray-600 mt-1">
                {searchTerm && `Filtered from ${orders.length} total orders`}
              </p>
            </div>
            {filteredOrders.length > 0 && (
              <div className="text-right">
                <div className="text-2xl font-bold text-primary-600">
                  ${filteredOrders.reduce((sum, order) => sum + parseFloat(order.totalPrice || 0), 0).toFixed(2)}
                </div>
                <div className="text-sm text-gray-600">Total Value</div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Orders List */}
      {!loading && !error && filteredOrders.length > 0 && (
        <div className="space-y-4">
          {filteredOrders.map((order) => (
            <div key={order.orderId} className="card hover:shadow-lg transition-shadow">
              {/* Order Summary */}
              <div
                className="cursor-pointer no-print"
                onClick={() => toggleOrderDetails(order.orderId)}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-4 flex-1">
                    <div className="text-3xl">📦</div>
                    <div className="flex-1">
                      <h3 className="text-lg font-semibold text-gray-900">
                        Order {order.orderName}
                      </h3>
                      <p className="text-sm text-gray-600 mt-1">
                        {order.customerName} • {order.customerEmail}
                      </p>
                      <p className="text-xs text-gray-500 mt-1">
                        Created: {new Date(order.createdAt).toLocaleDateString()} at {new Date(order.createdAt).toLocaleTimeString()}
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <div className="text-xl font-bold text-primary-600">
                      ${parseFloat(order.totalPrice || 0).toFixed(2)}
                    </div>
                    <div className="text-sm text-gray-600">
                      {order.itemCount} {order.itemCount === 1 ? 'item' : 'items'}
                    </div>
                    <div className="mt-2">
                      <span className="inline-block px-2 py-1 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">
                        {order.displayFulfillmentStatus || 'UNFULFILLED'}
                      </span>
                    </div>
                  </div>
                  <div className="ml-4 text-2xl text-gray-400">
                    {expandedOrderId === order.orderId ? '▼' : '▶'}
                  </div>
                </div>
              </div>

              {/* Expanded Order Details */}
              {expandedOrderId === order.orderId && (
                <div className="mt-6 pt-6 border-t border-gray-200">
                  <OrderDetailCard order={order} />
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* No Orders State */}
      {!loading && !error && filteredOrders.length === 0 && (
        <div className="card bg-green-50 border-green-200">
          <div className="flex items-center gap-3 justify-center py-12">
            <span className="text-5xl">✅</span>
            <div className="text-center">
              <h3 className="text-xl font-semibold text-green-900">
                {searchTerm ? 'No matching orders found' : 'All orders fulfilled!'}
              </h3>
              <p className="text-green-700 text-sm mt-1">
                {searchTerm
                  ? 'Try adjusting your search terms'
                  : 'There are no pending orders to fulfill in CRS'
                }
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default OrdersToFulfill
