import PropTypes from 'prop-types'

function AnalyticsCard({ title, value, currency, comparisonData, icon, type }) {
  const formatValue = (val) => {
    if (val === null || val === undefined) return '0.00'
    const num = parseFloat(val)
    return num.toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })
  }

  const getTrendIcon = (trend) => {
    switch (trend) {
      case 'up':
        return '↑'
      case 'down':
        return '↓'
      default:
        return '→'
    }
  }

  const getTrendColor = (trend) => {
    switch (trend) {
      case 'up':
        return 'text-green-600'
      case 'down':
        return 'text-red-600'
      default:
        return 'text-gray-600'
    }
  }

  const getBackgroundColor = (type) => {
    switch (type) {
      case 'sales':
        return 'bg-blue-50 border-blue-200'
      case 'average':
        return 'bg-purple-50 border-purple-200'
      case 'freight':
        return 'bg-orange-50 border-orange-200'
      case 'discount':
        return 'bg-pink-50 border-pink-200'
      default:
        return 'bg-gray-50 border-gray-200'
    }
  }

  return (
    <div className={`card ${getBackgroundColor(type)}`}>
      <div className="flex items-start justify-between mb-3">
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-1">
            {icon && <span className="text-2xl">{icon}</span>}
            <h3 className="text-sm font-medium text-gray-600">{title}</h3>
          </div>
          <div className="text-3xl font-bold text-gray-900">
            {currency && <span className="text-2xl">{currency}</span>}
            {formatValue(value)}
          </div>
        </div>
      </div>

      {comparisonData && (
        <div className="mt-4 pt-3 border-t border-gray-200">
          <div className="flex items-center justify-between text-sm">
            <span className="text-gray-600">vs. Last Year</span>
            <div className={`flex items-center gap-1 font-semibold ${getTrendColor(comparisonData.trend)}`}>
              <span>{getTrendIcon(comparisonData.trend)}</span>
              <span>
                {comparisonData.percentageChange > 0 ? '+' : ''}
                {formatValue(comparisonData.percentageChange)}%
              </span>
            </div>
          </div>
          <div className="text-xs text-gray-500 mt-1">
            Previous: {currency}{formatValue(comparisonData.previousValue)}
          </div>
        </div>
      )}
    </div>
  )
}

AnalyticsCard.propTypes = {
  title: PropTypes.string.isRequired,
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  currency: PropTypes.string,
  comparisonData: PropTypes.shape({
    previousValue: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    percentageChange: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    trend: PropTypes.oneOf(['up', 'down', 'neutral'])
  }),
  icon: PropTypes.string,
  type: PropTypes.oneOf(['sales', 'average', 'freight', 'discount'])
}

AnalyticsCard.defaultProps = {
  value: 0,
  currency: '$',
  comparisonData: null,
  icon: null,
  type: 'sales'
}

export default AnalyticsCard
