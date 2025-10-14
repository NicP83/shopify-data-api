import { Link, useLocation } from 'react-router-dom'
import { useState, useEffect } from 'react'
import api from '../services/api'

function Navigation() {
  const location = useLocation()
  const [approvalCount, setApprovalCount] = useState(0)

  useEffect(() => {
    loadApprovalCount()
    // Refresh count every 30 seconds
    const interval = setInterval(loadApprovalCount, 30000)
    return () => clearInterval(interval)
  }, [])

  const loadApprovalCount = async () => {
    try {
      const response = await api.getApprovalCount()
      setApprovalCount(response.data.count)
    } catch (error) {
      console.error('Error loading approval count:', error)
    }
  }

  const navItems = [
    { path: '/', label: 'Dashboard', icon: '🏠' },
    { path: '/products', label: 'Product Search', icon: '🔍' },
    { path: '/chat', label: 'AI Chat Agent', icon: '💬' },
    { path: '/fulfillment', label: 'Orders to Fulfill', icon: '📦' },
    { path: '/agents', label: 'Agents', icon: '🤖' },
    { path: '/workflows', label: 'Workflows', icon: '🔄' },
    { path: '/executions', label: 'Executions', icon: '📈' },
    { path: '/approvals', label: 'Approvals', icon: '✅', badge: approvalCount },
    { path: '/settings', label: 'Settings', icon: '⚙️' },
    { path: '/analytics', label: 'Analytics', icon: '📊' },
    { path: '/market-intel', label: 'Market Intel', icon: '💰' },
  ]

  const isActive = (path) => {
    if (path === '/workflows') {
      return location.pathname.startsWith('/workflows')
    }
    if (path === '/agents') {
      return location.pathname.startsWith('/agents')
    }
    if (path === '/executions') {
      return location.pathname.startsWith('/executions')
    }
    if (path === '/approvals') {
      return location.pathname.startsWith('/approvals')
    }
    return location.pathname === path
  }

  return (
    <nav className="bg-white shadow-md">
      <div className="container mx-auto px-4">
        <div className="flex items-center justify-between h-16">
          <div className="flex items-center">
            <h1 className="text-xl font-bold text-primary-600">
              Customer Service Hub
            </h1>
          </div>
          <div className="flex space-x-4">
            {navItems.map((item) => (
              <Link
                key={item.path}
                to={item.path}
                className={`px-3 py-2 rounded-md text-sm font-medium transition-colors duration-200 relative ${
                  isActive(item.path)
                    ? 'bg-primary-100 text-primary-700'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <span className="mr-2">{item.icon}</span>
                {item.label}
                {item.badge > 0 && (
                  <span className="absolute -top-1 -right-1 bg-red-500 text-white text-xs rounded-full h-5 w-5 flex items-center justify-center font-bold">
                    {item.badge}
                  </span>
                )}
              </Link>
            ))}
          </div>
        </div>
      </div>
    </nav>
  )
}

export default Navigation
