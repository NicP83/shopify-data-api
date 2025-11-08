import { useState } from 'react'
import api from '../services/api'

function VelocityTracking() {
  const [sku, setSku] = useState('')
  const [velocity, setVelocity] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const handleSearch = async (e) => {
    e.preventDefault()
    if (!sku.trim()) return

    setLoading(true)
    setError(null)
    setVelocity(null)

    try {
      const response = await api.getProductVelocity(sku.trim())
      if (response.data?.success) {
        setVelocity(response.data.data)
      } else {
        setError('No velocity data found for this SKU')
      }
    } catch (err) {
      setError('Failed to load velocity data')
      console.error('Error loading velocity:', err)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="max-w-4xl mx-auto">
      <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">📈 Velocity Tracking</h1>
        <p className="text-gray-600">Track sales velocity for any product</p>
      </div>

      {/* Search Form */}
      <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
        <form onSubmit={handleSearch} className="flex gap-4">
          <input
            type="text"
            value={sku}
            onChange={(e) => setSku(e.target.value)}
            placeholder="Enter SKU..."
            className="flex-1 border border-gray-300 rounded-lg px-4 py-2"
          />
          <button
            type="submit"
            disabled={loading}
            className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-400"
          >
            {loading ? 'Searching...' : 'Search'}
          </button>
        </form>
      </div>

      {/* Results */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-6">
          <p className="text-red-800">{error}</p>
        </div>
      )}

      {velocity && (
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="mb-6">
            <h2 className="text-xl font-bold text-gray-900">SKU: {velocity.sku}</h2>
            <p className="text-sm text-gray-500">Last calculated: {new Date(velocity.lastCalculated).toLocaleString()}</p>
          </div>

          <div className="grid grid-cols-3 gap-6 mb-6">
            <div className="p-4 bg-blue-50 rounded-lg">
              <div className="text-sm text-gray-600 mb-1">Daily Average</div>
              <div className="text-3xl font-bold text-blue-600">{velocity.dailyAverage?.toFixed(2)}</div>
              <div className="text-xs text-gray-500 mt-1">units/day</div>
            </div>

            <div className="p-4 bg-green-50 rounded-lg">
              <div className="text-sm text-gray-600 mb-1">Weekly Average</div>
              <div className="text-3xl font-bold text-green-600">{velocity.weeklyAverage?.toFixed(2)}</div>
              <div className="text-xs text-gray-500 mt-1">units/week</div>
            </div>

            <div className="p-4 bg-purple-50 rounded-lg">
              <div className="text-sm text-gray-600 mb-1">Monthly Average</div>
              <div className="text-3xl font-bold text-purple-600">{velocity.monthlyAverage?.toFixed(2)}</div>
              <div className="text-xs text-gray-500 mt-1">units/month</div>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-6">
            <div className="p-4 border border-gray-200 rounded-lg">
              <div className="text-sm text-gray-600 mb-1">Trend</div>
              <div className={`text-xl font-bold ${
                velocity.trend === 'INCREASING' ? 'text-green-600' :
                velocity.trend === 'DECREASING' ? 'text-red-600' :
                'text-gray-600'
              }`}>
                {velocity.trend === 'INCREASING' ? '↗ Increasing' :
                 velocity.trend === 'DECREASING' ? '↘ Decreasing' :
                 '→ Stable'}
                {velocity.trendPercentage && ` (${velocity.trendPercentage > 0 ? '+' : ''}${velocity.trendPercentage.toFixed(1)}%)`}
              </div>
            </div>

            <div className="p-4 border border-gray-200 rounded-lg">
              <div className="text-sm text-gray-600 mb-1">Calculation Period</div>
              <div className="text-xl font-bold text-gray-900">{velocity.calculationPeriodDays} days</div>
              <div className="text-xs text-gray-500 mt-1">Sample size: {velocity.dataSampleSize} transactions</div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default VelocityTracking
