import { Link, useLocation } from 'react-router-dom'
import { useState, useEffect, useRef } from 'react'
import api from '../services/api'

function Navigation() {
  const location = useLocation()
  const [approvalCount, setApprovalCount] = useState(0)
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const dropdownRef = useRef(null)

  useEffect(() => {
    loadApprovalCount()
    // Refresh count every 30 seconds
    const interval = setInterval(loadApprovalCount, 30000)
    return () => clearInterval(interval)
  }, [])

  // Click outside to close dropdown
  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const loadApprovalCount = async () => {
    try {
      const response = await api.getApprovalCount()
      setApprovalCount(response.data.count)
    } catch (error) {
      console.error('Error loading approval count:', error)
    }
  }

  const regularNavItems = [
    { path: '/', label: 'Dashboard', icon: '🏠' },
    { path: '/products', label: 'Product Search', icon: '🔍' },
    { path: '/chat', label: 'AI Chat Agent', icon: '💬' },
    { path: '/fulfillment', label: 'Orders to Fulfill', icon: '📦' },
    { path: '/agents', label: 'Agents', icon: '🤖' },
    { path: '/seo-agent', label: 'SEO Agent', icon: '🎯' },
    { path: '/settings', label: 'Settings', icon: '⚙️' },
    { path: '/analytics', label: 'Analytics', icon: '📊' },
    { path: '/market-intel', label: 'Market Intel', icon: '💰' },
  ]

  const tempDevItems = [
    { path: '/workflows', label: 'Workflows', icon: '🔄' },
    { path: '/workflow-gallery', label: 'Workflow Gallery', icon: '🎨' },
    { path: '/executions', label: 'Executions', icon: '📈' },
    { path: '/approvals', label: 'Approvals', icon: '✅', badge: approvalCount },
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
    if (path === '/seo-agent') {
      return location.pathname.startsWith('/seo-agent')
    }
    return location.pathname === path
  }

  const isTempDevActive = () => {
    return tempDevItems.some(item => isActive(item.path))
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
            {regularNavItems.map((item) => (
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

            {/* Temp Dev Dropdown */}
            <div ref={dropdownRef} className="relative">
              <button
                onClick={() => setDropdownOpen(!dropdownOpen)}
                className={`px-3 py-2 rounded-md text-sm font-medium transition-colors duration-200 flex items-center ${
                  isTempDevActive()
                    ? 'bg-primary-100 text-primary-700'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <span className="mr-2">🚧</span>
                Temp Dev
                <svg
                  className={`ml-1 h-4 w-4 transition-transform ${dropdownOpen ? 'rotate-180' : ''}`}
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </button>

              {dropdownOpen && (
                <div className="absolute top-full right-0 mt-2 w-56 bg-white rounded-md shadow-lg py-1 z-50 border border-gray-200">
                  {tempDevItems.map((item) => (
                    <Link
                      key={item.path}
                      to={item.path}
                      onClick={() => setDropdownOpen(false)}
                      className={`block px-4 py-2 text-sm transition-colors relative ${
                        isActive(item.path)
                          ? 'bg-primary-100 text-primary-700'
                          : 'text-gray-700 hover:bg-gray-100'
                      }`}
                    >
                      <span className="mr-2">{item.icon}</span>
                      {item.label}
                      {item.badge > 0 && (
                        <span className="absolute top-1/2 right-3 transform -translate-y-1/2 bg-red-500 text-white text-xs rounded-full h-5 w-5 flex items-center justify-center font-bold">
                          {item.badge}
                        </span>
                      )}
                    </Link>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </nav>
  )
}

export default Navigation
