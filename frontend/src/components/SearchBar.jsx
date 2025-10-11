import { useState } from 'react'

function SearchBar({ value, onChange, onSearch, loading }) {
  const [localValue, setLocalValue] = useState(value)

  const handleSubmit = (e) => {
    e.preventDefault()
    onSearch(localValue)
  }

  const handleChange = (e) => {
    const newValue = e.target.value
    setLocalValue(newValue)
    onChange(newValue)
  }

  const handleClear = () => {
    setLocalValue('')
    onChange('')
    onSearch('')
  }

  return (
    <form onSubmit={handleSubmit} className="card">
      <div className="flex gap-2">
        <div className="flex-1 relative">
          <input
            type="text"
            value={localValue}
            onChange={handleChange}
            placeholder="Search by product name, SKU, or keyword..."
            className="input-field pr-10"
            disabled={loading}
          />
          {localValue && (
            <button
              type="button"
              onClick={handleClear}
              className="absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-400 hover:text-gray-600"
            >
              ✕
            </button>
          )}
        </div>
        <button
          type="submit"
          disabled={loading || !localValue.trim()}
          className="btn-primary disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {loading ? (
            <span className="flex items-center">
              <span className="animate-spin mr-2">⏳</span>
              Searching...
            </span>
          ) : (
            <span>🔍 Search</span>
          )}
        </button>
      </div>

      <div className="mt-3 text-sm text-gray-600">
        <strong>Search tips:</strong> Try "title:Gundam" for title search, or just "Gundam" for general search
      </div>
    </form>
  )
}

export default SearchBar
