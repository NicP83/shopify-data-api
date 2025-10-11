import { BrowserRouter as Router, Routes, Route } from 'react-router-dom'
import Navigation from './components/Navigation'
import Dashboard from './pages/Dashboard'
import ProductSearch from './pages/ProductSearch'

function App() {
  return (
    <Router>
      <div className="min-h-screen bg-gray-50">
        <Navigation />
        <main className="container mx-auto px-4 py-8">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/products" element={<ProductSearch />} />
            <Route path="/chat" element={<div className="text-center py-20 text-gray-500">AI Chat Agent - Coming Soon</div>} />
            <Route path="/analytics" element={<div className="text-center py-20 text-gray-500">Analytics Dashboard - Coming Soon</div>} />
            <Route path="/market-intel" element={<div className="text-center py-20 text-gray-500">Market Intelligence - Coming Soon</div>} />
          </Routes>
        </main>
      </div>
    </Router>
  )
}

export default App
