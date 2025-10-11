import { Link } from 'react-router-dom'
import { useState, useEffect } from 'react'
import api from '../services/api'

function Dashboard() {
  const [status, setStatus] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const fetchStatus = async () => {
      try {
        const response = await api.getStatus()
        setStatus(response.data)
      } catch (error) {
        console.error('Error fetching status:', error)
      } finally {
        setLoading(false)
      }
    }
    fetchStatus()
  }, [])

  const features = [
    {
      title: 'Product Search',
      description: 'Search products, view details, and generate cart links',
      icon: '🔍',
      path: '/products',
      status: 'Active',
      color: 'bg-green-100 text-green-800'
    },
    {
      title: 'AI Chat Agent',
      description: 'Sales and customer support automation',
      icon: '💬',
      path: '/chat',
      status: 'Coming Soon',
      color: 'bg-yellow-100 text-yellow-800'
    },
    {
      title: 'Analytics Dashboard',
      description: 'Business insights and reporting',
      icon: '📊',
      path: '/analytics',
      status: 'Coming Soon',
      color: 'bg-yellow-100 text-yellow-800'
    },
    {
      title: 'Market Intelligence',
      description: 'Competitive pricing and discount tracking',
      icon: '💰',
      path: '/market-intel',
      status: 'Coming Soon',
      color: 'bg-yellow-100 text-yellow-800'
    }
  ]

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="text-center">
        <h1 className="text-4xl font-bold text-gray-900 mb-2">
          Welcome to Customer Service Hub
        </h1>
        <p className="text-lg text-gray-600">
          Your central tool for sales, support, and business intelligence
        </p>
      </div>

      {/* System Status */}
      <div className="card">
        <h2 className="text-xl font-semibold mb-4">System Status</h2>
        {loading ? (
          <div className="text-gray-500">Loading status...</div>
        ) : status ? (
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="text-center">
              <div className="text-2xl font-bold text-primary-600">
                {status.shopify_connected ? '✅' : '❌'}
              </div>
              <div className="text-sm text-gray-600">Shopify Connected</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-primary-600">
                {status.api_version}
              </div>
              <div className="text-sm text-gray-600">API Version</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-primary-600">
                {status.environment}
              </div>
              <div className="text-sm text-gray-600">Environment</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-green-600">UP</div>
              <div className="text-sm text-gray-600">Service Status</div>
            </div>
          </div>
        ) : (
          <div className="text-red-500">Unable to fetch status</div>
        )}
      </div>

      {/* Feature Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {features.map((feature) => (
          <Link
            key={feature.path}
            to={feature.path}
            className="card hover:shadow-lg transition-shadow duration-200"
          >
            <div className="flex items-start justify-between">
              <div className="flex-1">
                <div className="text-4xl mb-3">{feature.icon}</div>
                <h3 className="text-xl font-semibold text-gray-900 mb-2">
                  {feature.title}
                </h3>
                <p className="text-gray-600 mb-3">{feature.description}</p>
                <span className={`inline-block px-3 py-1 rounded-full text-xs font-medium ${feature.color}`}>
                  {feature.status}
                </span>
              </div>
            </div>
          </Link>
        ))}
      </div>

      {/* Quick Search */}
      <div className="card bg-primary-50 border border-primary-200">
        <div className="text-center">
          <h3 className="text-lg font-semibold text-gray-900 mb-2">
            Quick Product Search
          </h3>
          <p className="text-gray-600 mb-4">
            Search for products and generate cart links instantly
          </p>
          <Link to="/products" className="btn-primary inline-block">
            Go to Product Search →
          </Link>
        </div>
      </div>
    </div>
  )
}

export default Dashboard
