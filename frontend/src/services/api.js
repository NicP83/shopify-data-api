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

  searchProducts: (query, first = 20, includeArchived = false) => {
    return apiClient.get('/products/search', {
      params: { q: query, first, includeArchived }
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

  // Chat
  sendChatMessage: (message, conversationHistory = []) => {
    return apiClient.post('/chat/message', {
      message,
      conversationHistory
    })
  },

  getChatStatus: () => apiClient.get('/chat/status'),

  // Configuration
  getAIConfig: () => apiClient.get('/config/ai'),

  updateAIConfig: (config) => apiClient.put('/config/ai', config),

  getSystemPrompt: () => apiClient.get('/config/prompt'),

  getAvailableModels: () => apiClient.get('/config/models'),

  // Chatbot Configuration
  getChatbotConfig: () => apiClient.get('/config/chatbot'),

  updateChatbotConfig: (config) => apiClient.put('/config/chatbot', config),

  resetChatbotConfig: () => apiClient.post('/config/chatbot/reset'),

  previewSystemPrompt: () => apiClient.get('/config/chatbot/preview-prompt'),

  // Analytics
  getSalesAnalytics: (period = '7d') => {
    return apiClient.get('/analytics/sales', {
      params: { period }
    })
  },

  getAllSalesAnalytics: () => apiClient.get('/analytics/sales/all'),

  // In-Store Analytics (CRS)
  getInstoreSalesAnalytics: (period = '7d') => {
    return apiClient.get('/analytics/instore/sales', {
      params: { period }
    })
  },

  getAllInstoreSalesAnalytics: () => apiClient.get('/analytics/instore/sales/all'),

  // Channel Sales Analytics (Hobbyman, Hearns, Shopify)
  getChannelSalesAnalytics: (period = '7d') => {
    return apiClient.get('/analytics/channels', {
      params: { period }
    })
  },

  getAllChannelSalesAnalytics: () => apiClient.get('/analytics/channels/all'),

  // Fulfillment
  getPendingFulfillments: (includeDiscounts = false, includeSalePrices = false) => {
    return apiClient.get('/fulfillment/pending', {
      params: { includeDiscounts, includeSalePrices }
    })
  },

  getFulfillmentDetails: (id) => apiClient.get(`/fulfillment/${id}`),

  // Multi-Agent System - Agents
  getAgents: (activeOnly = null) => {
    const params = {}
    if (activeOnly !== null) params.activeOnly = activeOnly
    return apiClient.get('/agents', { params })
  },

  getAgent: (id) => apiClient.get(`/agents/${id}`),

  createAgent: (agent) => apiClient.post('/agents', agent),

  updateAgent: (id, agent) => apiClient.put(`/agents/${id}`, agent),

  deleteAgent: (id) => apiClient.delete(`/agents/${id}`),

  activateAgent: (id) => apiClient.post(`/agents/${id}/activate`),

  deactivateAgent: (id) => apiClient.post(`/agents/${id}/deactivate`),

  executeAgent: (id, input) => apiClient.post(`/agents/${id}/execute`, input),

  // Multi-Agent System - Workflows
  getWorkflows: (activeOnly = null, triggerType = null) => {
    const params = {}
    if (activeOnly !== null) params.activeOnly = activeOnly
    if (triggerType !== null) params.triggerType = triggerType
    return apiClient.get('/workflows', { params })
  },

  getWorkflow: (id) => apiClient.get(`/workflows/${id}`),

  createWorkflow: (workflow) => apiClient.post('/workflows', workflow),

  updateWorkflow: (id, workflow) => apiClient.put(`/workflows/${id}`, workflow),

  deleteWorkflow: (id) => apiClient.delete(`/workflows/${id}`),

  activateWorkflow: (id) => apiClient.post(`/workflows/${id}/activate`),

  deactivateWorkflow: (id) => apiClient.post(`/workflows/${id}/deactivate`),

  executeWorkflow: (id, triggerData) => apiClient.post(`/workflows/${id}/execute`, triggerData),

  executePublicWorkflow: (id, input) => apiClient.post(`/workflows/public/${id}/execute`, input),

  // Multi-Agent System - Workflow Steps
  getWorkflowSteps: (workflowId) => apiClient.get(`/workflows/${workflowId}/steps`),

  createWorkflowStep: (workflowId, step) => apiClient.post(`/workflows/${workflowId}/steps`, step),

  updateWorkflowStep: (workflowId, stepId, step) =>
    apiClient.put(`/workflows/${workflowId}/steps/${stepId}`, step),

  deleteWorkflowStep: (workflowId, stepId) =>
    apiClient.delete(`/workflows/${workflowId}/steps/${stepId}`),

  reorderWorkflowSteps: (workflowId, stepIds) =>
    apiClient.post(`/workflows/${workflowId}/steps/reorder`, stepIds),

  // Multi-Agent System - Workflow Executions
  getWorkflowExecutions: (workflowId) => apiClient.get(`/workflows/${workflowId}/executions`),

  getAllExecutions: () => apiClient.get('/executions'),

  getExecutionDetails: (id) => apiClient.get(`/executions/${id}`),

  // Multi-Agent System - Approvals
  getPendingApprovals: (role = null) => {
    const params = {}
    if (role !== null) params.role = role
    return apiClient.get('/approvals/pending', { params })
  },

  getApprovalsByExecution: (executionId) => apiClient.get(`/approvals/execution/${executionId}`),

  getApprovalCount: () => apiClient.get('/approvals/count'),

  approveRequest: (id, approvedBy, comments) =>
    apiClient.post(`/approvals/${id}/approve`, { approvedBy, comments }),

  rejectRequest: (id, rejectedBy, reason) =>
    apiClient.post(`/approvals/${id}/reject`, { rejectedBy, reason }),

  // Multi-Agent System - Tools
  getTools: (activeOnly = null) => {
    const params = {}
    if (activeOnly !== null) params.activeOnly = activeOnly
    return apiClient.get('/tools', { params })
  },

  getTool: (id) => apiClient.get(`/tools/${id}`),

  createTool: (tool) => apiClient.post('/tools', tool),

  updateTool: (id, tool) => apiClient.put(`/tools/${id}`, tool),

  deleteTool: (id) => apiClient.delete(`/tools/${id}`),

  // Multi-Agent System - Schedules
  getSchedules: (active = true) => {
    const params = {}
    if (active !== null) params.active = active
    return apiClient.get('/schedules', { params })
  },

  getSchedulesForWorkflow: (workflowId) => apiClient.get(`/schedules/workflow/${workflowId}`),

  createSchedule: (workflowId, cronExpression, triggerData = null) =>
    apiClient.post('/schedules', { workflowId, cronExpression, triggerData }),

  cancelSchedule: (id) => apiClient.delete(`/schedules/${id}`),

  activateSchedule: (id) => apiClient.put(`/schedules/${id}/activate`),

  updateScheduleCron: (id, cronExpression) =>
    apiClient.put(`/schedules/${id}/cron`, { cronExpression }),

  updateScheduleTriggerData: (id, triggerData) =>
    apiClient.put(`/schedules/${id}/trigger-data`, triggerData),
}

export default api
