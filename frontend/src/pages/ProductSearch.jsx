import { useState } from 'react'
import api from '../services/api'
import SearchBar from '../components/SearchBar'
import ProductCard from '../components/ProductCard'
import ProductDetail from '../components/ProductDetail'

function ProductSearch() {
  const [searchQuery, setSearchQuery] = useState('')
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [selectedProduct, setSelectedProduct] = useState(null)

  const handleSearch = async (query) => {
    if (!query.trim()) {
      setProducts([])
      return
    }

    setLoading(true)
    setError(null)

    try {
      const response = await api.searchProducts(query, 20)

      if (response.data.success && response.data.data) {
        const edges = response.data.data.products?.edges || []
        const productList = edges.map(edge => edge.node)
        setProducts(productList)

        if (productList.length === 0) {
          setError('No products found matching your search.')
        }
      } else {
        setError('Failed to fetch products. Please try again.')
      }
    } catch (err) {
      console.error('Search error:', err)
      setError('An error occurred while searching. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleProductClick = (product) => {
    setSelectedProduct(product)
  }

  const handleCloseDetail = () => {
    setSelectedProduct(null)
  }

  return (
    <div className="space-y-6">
      <div className="text-center">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">Product Search</h1>
        <p className="text-gray-600">
          Search for products and generate cart links for customers
        </p>
      </div>

      <SearchBar
        value={searchQuery}
        onChange={setSearchQuery}
        onSearch={handleSearch}
        loading={loading}
      />

      {error && (
        <div className="card bg-red-50 border border-red-200 text-red-700">
          {error}
        </div>
      )}

      {loading && (
        <div className="text-center py-12">
          <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
          <p className="mt-4 text-gray-600">Searching products...</p>
        </div>
      )}

      {!loading && products.length > 0 && (
        <div>
          <div className="mb-4 text-sm text-gray-600">
            Found {products.length} product{products.length !== 1 ? 's' : ''}
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {products.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                onClick={() => handleProductClick(product)}
              />
            ))}
          </div>
        </div>
      )}

      {!loading && !error && products.length === 0 && searchQuery && (
        <div className="text-center py-12 text-gray-500">
          <div className="text-6xl mb-4">🔍</div>
          <p>Enter a search term to find products</p>
        </div>
      )}

      {!loading && !searchQuery && (
        <div className="card bg-blue-50 border border-blue-200">
          <h3 className="text-lg font-semibold text-gray-900 mb-2">
            How to use Product Search
          </h3>
          <ul className="list-disc list-inside text-gray-700 space-y-1">
            <li>Enter product name, SKU, or keyword in the search box</li>
            <li>Click on a product card to view full details</li>
            <li>Use "View Product" to open the product page</li>
            <li>Use "Add to Cart" to generate a direct cart link</li>
            <li>Share cart links with customers via email, SMS, or chat</li>
          </ul>
        </div>
      )}

      {selectedProduct && (
        <ProductDetail
          product={selectedProduct}
          onClose={handleCloseDetail}
        />
      )}
    </div>
  )
}

export default ProductSearch
