import { Link, useLocation } from 'react-router-dom'

function Navigation() {
  const location = useLocation()

  const navItems = [
    { path: '/', label: 'Dashboard', icon: '🏠' },
    { path: '/products', label: 'Product Search', icon: '🔍' },
    { path: '/chat', label: 'AI Chat Agent', icon: '💬' },
    { path: '/fulfillment', label: 'Orders to Fulfill', icon: '📦' },
    { path: '/settings', label: 'Settings', icon: '⚙️' },
    { path: '/analytics', label: 'Analytics', icon: '📊' },
    { path: '/market-intel', label: 'Market Intel', icon: '💰' },
  ]

  const isActive = (path) => {
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
                className={`px-3 py-2 rounded-md text-sm font-medium transition-colors duration-200 ${
                  isActive(item.path)
                    ? 'bg-primary-100 text-primary-700'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <span className="mr-2">{item.icon}</span>
                {item.label}
              </Link>
            ))}
          </div>
        </div>
      </div>
    </nav>
  )
}

export default Navigation
