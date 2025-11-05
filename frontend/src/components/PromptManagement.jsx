import React, { useState, useEffect } from 'react';
import {
  FileText,
  Edit,
  Save,
  X,
  Plus,
  Eye,
  TrendingUp,
  Loader2,
  AlertCircle
} from 'lucide-react';

/**
 * Prompt Management Component
 * Allows viewing and customizing system prompts
 */
const PromptManagement = ({ shopDomain }) => {
  const [prompts, setPrompts] = useState([]);
  const [selectedPrompt, setSelectedPrompt] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editMode, setEditMode] = useState(false);
  const [editedText, setEditedText] = useState('');

  useEffect(() => {
    fetchPrompts();
  }, [shopDomain]);

  const fetchPrompts = async () => {
    setIsLoading(true);
    setError(null);

    try {
      const response = await fetch(
        `http://localhost:8080/api/shopify/config/prompts?shop=${encodeURIComponent(shopDomain)}`,
        {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json'
          }
        }
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const data = await response.json();
      setPrompts(data.prompts || []);
    } catch (err) {
      console.error('Prompts error:', err);
      setError(err.message);
      // Mock data for development
      setPrompts([
        {
          id: 1,
          name: 'default_product_search',
          type: 'product_search',
          description: 'Default system prompt for product search assistant',
          version: 1,
          isGlobal: true,
          usageCount: 1247,
          successRate: 96.07,
          avgResponseTime: 2340
        }
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  const fetchPromptDetails = async (promptId) => {
    try {
      const response = await fetch(
        `http://localhost:8080/api/shopify/config/prompts/${promptId}?shop=${encodeURIComponent(shopDomain)}`,
        {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json'
          }
        }
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const data = await response.json();
      setSelectedPrompt(data);
      setEditedText(data.text || '');
    } catch (err) {
      console.error('Prompt details error:', err);
      setError(err.message);
    }
  };

  const handleCustomizePrompt = async () => {
    if (!selectedPrompt) return;

    try {
      const response = await fetch(
        `http://localhost:8080/api/shopify/config/prompts/customize?shop=${encodeURIComponent(shopDomain)}`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            globalPromptName: selectedPrompt.name,
            customizations: editedText
          })
        }
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      await fetchPrompts();
      setEditMode(false);
      alert('Prompt customized successfully!');
    } catch (err) {
      console.error('Customize error:', err);
      setError(err.message);
    }
  };

  if (isLoading) {
    return (
      <div className="flex justify-center items-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {/* Prompts List */}
      <div className="lg:col-span-1 space-y-4">
        <div className="bg-white rounded-lg shadow-md border border-gray-200 p-4">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">System Prompts</h3>

          {error && (
            <div className="mb-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
              <div className="flex items-center gap-2 text-yellow-800 text-sm">
                <AlertCircle className="h-4 w-4" />
                <span>Using demo data</span>
              </div>
            </div>
          )}

          <div className="space-y-2">
            {prompts.map((prompt) => (
              <button
                key={prompt.id}
                onClick={() => {
                  fetchPromptDetails(prompt.id);
                  setEditMode(false);
                }}
                className={`w-full text-left p-3 rounded-lg border transition-colors ${
                  selectedPrompt?.id === prompt.id
                    ? 'bg-blue-50 border-blue-500'
                    : 'bg-white border-gray-200 hover:bg-gray-50'
                }`}
              >
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <h4 className="text-sm font-medium text-gray-900">
                      {prompt.name}
                    </h4>
                    <p className="text-xs text-gray-500 mt-1">
                      {prompt.description}
                    </p>
                    <div className="flex items-center gap-2 mt-2">
                      <span
                        className={`text-xs px-2 py-0.5 rounded-full ${
                          prompt.isGlobal
                            ? 'bg-gray-100 text-gray-700'
                            : 'bg-blue-100 text-blue-700'
                        }`}
                      >
                        {prompt.isGlobal ? 'Global' : 'Custom'}
                      </span>
                      <span className="text-xs text-gray-500">
                        v{prompt.version}
                      </span>
                    </div>
                  </div>
                  <FileText className="h-5 w-5 text-gray-400 flex-shrink-0" />
                </div>
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Prompt Details */}
      <div className="lg:col-span-2">
        {selectedPrompt ? (
          <div className="bg-white rounded-lg shadow-md border border-gray-200">
            {/* Header */}
            <div className="p-6 border-b border-gray-200">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-xl font-semibold text-gray-900">
                    {selectedPrompt.name}
                  </h3>
                  <p className="text-sm text-gray-500 mt-1">
                    {selectedPrompt.description}
                  </p>
                </div>
                <div className="flex gap-2">
                  {!editMode ? (
                    <button
                      onClick={() => setEditMode(true)}
                      className="px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 flex items-center gap-2"
                    >
                      <Edit className="h-4 w-4" />
                      Customize
                    </button>
                  ) : (
                    <>
                      <button
                        onClick={handleCustomizePrompt}
                        className="px-4 py-2 bg-green-500 text-white rounded-lg hover:bg-green-600 flex items-center gap-2"
                      >
                        <Save className="h-4 w-4" />
                        Save
                      </button>
                      <button
                        onClick={() => {
                          setEditMode(false);
                          setEditedText(selectedPrompt.text || '');
                        }}
                        className="px-4 py-2 bg-gray-500 text-white rounded-lg hover:bg-gray-600 flex items-center gap-2"
                      >
                        <X className="h-4 w-4" />
                        Cancel
                      </button>
                    </>
                  )}
                </div>
              </div>

              {/* Stats */}
              <div className="grid grid-cols-3 gap-4 mt-4">
                <div className="bg-gray-50 rounded-lg p-3">
                  <p className="text-xs text-gray-600">Usage Count</p>
                  <p className="text-lg font-semibold text-gray-900">
                    {selectedPrompt.usageCount || 0}
                  </p>
                </div>
                <div className="bg-gray-50 rounded-lg p-3">
                  <p className="text-xs text-gray-600">Success Rate</p>
                  <p className="text-lg font-semibold text-gray-900">
                    {selectedPrompt.successRate?.toFixed(1) || 0}%
                  </p>
                </div>
                <div className="bg-gray-50 rounded-lg p-3">
                  <p className="text-xs text-gray-600">Avg Response</p>
                  <p className="text-lg font-semibold text-gray-900">
                    {selectedPrompt.avgResponseTime
                      ? `${(selectedPrompt.avgResponseTime / 1000).toFixed(1)}s`
                      : '0s'}
                  </p>
                </div>
              </div>
            </div>

            {/* Prompt Text */}
            <div className="p-6">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Prompt Text
              </label>
              {editMode ? (
                <textarea
                  value={editedText}
                  onChange={(e) => setEditedText(e.target.value)}
                  rows={20}
                  className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono text-sm"
                />
              ) : (
                <div className="bg-gray-50 border border-gray-200 rounded-lg p-4 font-mono text-sm whitespace-pre-wrap max-h-96 overflow-y-auto">
                  {selectedPrompt.text || 'No prompt text available'}
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="bg-white rounded-lg shadow-md border border-gray-200 p-12 text-center">
            <FileText className="h-12 w-12 text-gray-400 mx-auto mb-4" />
            <h3 className="text-lg font-semibold text-gray-900 mb-2">
              Select a Prompt
            </h3>
            <p className="text-gray-600">
              Choose a prompt from the list to view and customize
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default PromptManagement;
