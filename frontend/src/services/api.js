import axios from 'axios'

// Create axios instance with base configuration
const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

// API service methods
const api = {
  // Health and Status
  getHealth: () => apiClient.get('/health'),
  getStatus: () => apiClient.get('/status'),

  // Products
  getProducts: (first = 10, after = null) => {
    const params = { first }
    if (after) params.after = after
    return apiClient.get('/products', { params })
  },

  getProductById: (id) => apiClient.get(`/products/${id}`),

  searchProducts: (query, first = 20) => {
    return apiClient.get('/products/search', {
      params: { q: query, first }
    })
  },

  // Orders
  getOrders: (first = 10, after = null) => {
    const params = { first }
    if (after) params.after = after
    return apiClient.get('/orders', { params })
  },

  getOrderById: (id) => apiClient.get(`/orders/${id}`),

  searchOrders: (query, first = 20) => {
    return apiClient.get('/orders/search', {
      params: { q: query, first }
    })
  },

  // Customers
  getCustomers: (first = 10, after = null) => {
    const params = { first }
    if (after) params.after = after
    return apiClient.get('/customers', { params })
  },

  getCustomerById: (id) => apiClient.get(`/customers/${id}`),

  searchCustomers: (query, first = 20) => {
    return apiClient.get('/customers/search', {
      params: { q: query, first }
    })
  },

  // Inventory
  getInventory: (first = 10, after = null) => {
    const params = { first }
    if (after) params.after = after
    return apiClient.get('/inventory', { params })
  },

  getInventoryLocations: () => apiClient.get('/inventory/locations'),
}

export default api
