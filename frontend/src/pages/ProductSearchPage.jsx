import React, { useState } from 'react';
import { MessageSquare, Search, BarChart3, Settings } from 'lucide-react';
import ChatInterface from '../components/ChatInterface';
import DirectSearchInterface from '../components/DirectSearchInterface';
import AnalyticsDashboard from '../components/AnalyticsDashboard';
import PromptManagement from '../components/PromptManagement';

/**
 * Admin Product Search Assistant Page
 * Provides dual-mode interface: AI Chat and Direct Search
 */
const ProductSearchPage = () => {
  const [activeTab, setActiveTab] = useState('chat');
  const [shopDomain] = useState('hearnshobbies.myshopify.com');

  const tabs = [
    { id: 'chat', label: 'AI Chat', icon: MessageSquare },
    { id: 'search', label: 'Direct Search', icon: Search },
    { id: 'analytics', label: 'Analytics', icon: BarChart3 },
    { id: 'prompts', label: 'Prompt Management', icon: Settings }
  ];

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white border-b border-gray-200 shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-2xl font-bold text-gray-900">
                Product Search Assistant
              </h1>
              <p className="text-sm text-gray-500 mt-1">
                Shop: {shopDomain}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <span className="px-3 py-1 bg-green-100 text-green-800 text-sm font-medium rounded-full">
                AI Enabled
              </span>
            </div>
          </div>
        </div>
      </header>

      {/* Tab Navigation */}
      <div className="bg-white border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <nav className="flex -mb-px space-x-8">
            {tabs.map((tab) => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.id;

              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={`
                    group inline-flex items-center py-4 px-1 border-b-2 font-medium text-sm
                    transition-colors duration-200
                    ${isActive
                      ? 'border-blue-500 text-blue-600'
                      : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                    }
                  `}
                >
                  <Icon
                    className={`
                      mr-2 h-5 w-5
                      ${isActive ? 'text-blue-500' : 'text-gray-400 group-hover:text-gray-500'}
                    `}
                  />
                  {tab.label}
                </button>
              );
            })}
          </nav>
        </div>
      </div>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {activeTab === 'chat' && (
          <div className="space-y-4">
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
              <div className="flex">
                <div className="flex-shrink-0">
                  <MessageSquare className="h-5 w-5 text-blue-400" />
                </div>
                <div className="ml-3">
                  <h3 className="text-sm font-medium text-blue-800">
                    AI-Powered Product Search
                  </h3>
                  <div className="mt-2 text-sm text-blue-700">
                    <p>
                      Ask natural language questions to find products. The AI assistant
                      will search your catalog and provide relevant recommendations.
                    </p>
                  </div>
                </div>
              </div>
            </div>
            <ChatInterface shopDomain={shopDomain} />
          </div>
        )}

        {activeTab === 'search' && (
          <div className="space-y-4">
            <div className="bg-gray-50 border border-gray-200 rounded-lg p-4">
              <div className="flex">
                <div className="flex-shrink-0">
                  <Search className="h-5 w-5 text-gray-400" />
                </div>
                <div className="ml-3">
                  <h3 className="text-sm font-medium text-gray-800">
                    Direct Product Search
                  </h3>
                  <div className="mt-2 text-sm text-gray-600">
                    <p>
                      Search your product catalog directly without AI assistance.
                      Faster for exact matches and SKU lookups.
                    </p>
                  </div>
                </div>
              </div>
            </div>
            <DirectSearchInterface shopDomain={shopDomain} />
          </div>
        )}

        {activeTab === 'analytics' && (
          <AnalyticsDashboard shopDomain={shopDomain} />
        )}

        {activeTab === 'prompts' && (
          <PromptManagement shopDomain={shopDomain} />
        )}
      </main>

      {/* Footer */}
      <footer className="bg-white border-t border-gray-200 mt-12">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <div className="text-center text-sm text-gray-500">
            <p>
              Powered by Claude AI • Hearn's Hobbies Product Search Assistant
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
};

export default ProductSearchPage;
