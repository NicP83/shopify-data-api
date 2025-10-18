import { BrowserRouter as Router, Routes, Route } from 'react-router-dom'
import Navigation from './components/Navigation'
import Dashboard from './pages/Dashboard'
import ProductSearch from './pages/ProductSearch'
import ChatAgent from './pages/ChatAgent'
import Settings from './pages/Settings'
import Analytics from './pages/Analytics'
import OrdersToFulfill from './pages/OrdersToFulfill'
import WorkflowManagement from './pages/WorkflowManagement'
import WorkflowEditor from './pages/WorkflowEditor'
import WorkflowExecutions from './pages/WorkflowExecutions'
import AgentManagement from './pages/AgentManagement'
import AgentEditor from './pages/AgentEditor'
import ApprovalQueue from './pages/ApprovalQueue'
import WorkflowFormExecutor from './pages/WorkflowFormExecutor'
import WorkflowChatExecutor from './pages/WorkflowChatExecutor'
import WorkflowGallery from './pages/WorkflowGallery'
import SeoAgent from './pages/SeoAgent'

function App() {
  return (
    <Router>
      <div className="min-h-screen bg-gray-50">
        <Navigation />
        <main className="container mx-auto px-4 py-8">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/products" element={<ProductSearch />} />
            <Route path="/chat" element={<ChatAgent />} />
            <Route path="/fulfillment" element={<OrdersToFulfill />} />
            <Route path="/agents" element={<AgentManagement />} />
            <Route path="/agents/new" element={<AgentEditor />} />
            <Route path="/agents/:id" element={<AgentEditor />} />
            <Route path="/seo-agent" element={<SeoAgent />} />
            <Route path="/workflows" element={<WorkflowManagement />} />
            <Route path="/workflows/new" element={<WorkflowEditor />} />
            <Route path="/workflows/:id" element={<WorkflowEditor />} />
            <Route path="/workflow-gallery" element={<WorkflowGallery />} />
            <Route path="/workflow/execute/:id" element={<WorkflowFormExecutor />} />
            <Route path="/workflow/chat/:id" element={<WorkflowChatExecutor />} />
            <Route path="/executions" element={<WorkflowExecutions />} />
            <Route path="/executions/:workflowId" element={<WorkflowExecutions />} />
            <Route path="/approvals" element={<ApprovalQueue />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="/analytics" element={<Analytics />} />
            <Route path="/market-intel" element={<div className="text-center py-20 text-gray-500">Market Intelligence - Coming Soon</div>} />
          </Routes>
        </main>
      </div>
    </Router>
  )
}

export default App
