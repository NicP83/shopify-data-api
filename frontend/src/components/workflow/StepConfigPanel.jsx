import { useState, useEffect } from 'react'

function StepConfigPanel({ step, agents, onUpdate, onClose, onDelete }) {
  const [formData, setFormData] = useState({
    name: '',
    stepType: 'AGENT_EXECUTION',
    agentId: null,
    inputMappingJson: {},
    outputVariable: '',
    conditionExpression: '',
    timeoutSeconds: 300,
    retryConfigJson: null,
    ...step
  })

  const [inputMappingText, setInputMappingText] = useState('')
  const [retryConfigText, setRetryConfigText] = useState('')

  useEffect(() => {
    if (step) {
      setFormData({ ...formData, ...step })
      setInputMappingText(JSON.stringify(step.inputMappingJson || {}, null, 2))
      setRetryConfigText(step.retryConfigJson ? JSON.stringify(step.retryConfigJson, null, 2) : '')
    }
  }, [step])

  const handleSubmit = () => {
    try {
      const updated = {
        ...formData,
        inputMappingJson: inputMappingText ? JSON.parse(inputMappingText) : {},
        retryConfigJson: retryConfigText ? JSON.parse(retryConfigText) : null
      }
      onUpdate(updated)
    } catch (error) {
      alert('Invalid JSON in input mapping or retry config: ' + error.message)
    }
  }

  if (!step) {
    return (
      <div className="w-96 bg-gray-100 border-l border-gray-300 p-4 overflow-y-auto">
        <div className="text-center text-gray-500 py-20">
          Select a step to configure
        </div>
      </div>
    )
  }

  return (
    <div className="w-96 bg-white border-l border-gray-300 p-4 overflow-y-auto">
      <div className="flex justify-between items-center mb-4">
        <h3 className="text-lg font-semibold">Configure Step</h3>
        <button
          onClick={onClose}
          className="text-gray-500 hover:text-gray-700"
        >
          ✕
        </button>
      </div>

      <div className="space-y-4">
        {/* Step Name */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Step Name *
          </label>
          <input
            type="text"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            placeholder="e.g., Classify Customer Inquiry"
          />
        </div>

        {/* Step Type */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Step Type
          </label>
          <select
            value={formData.stepType}
            onChange={(e) => setFormData({ ...formData, stepType: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          >
            <option value="AGENT_EXECUTION">Agent Execution</option>
            <option value="CONDITION">Condition</option>
            <option value="APPROVAL">Approval</option>
            <option value="PARALLEL">Parallel</option>
          </select>
        </div>

        {/* Agent Selection (only for AGENT_EXECUTION) */}
        {formData.stepType === 'AGENT_EXECUTION' && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Agent *
            </label>
            <select
              value={formData.agentId || ''}
              onChange={(e) => setFormData({ ...formData, agentId: parseInt(e.target.value) })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            >
              <option value="">Select an agent...</option>
              {agents.map((agent) => (
                <option key={agent.id} value={agent.id}>
                  {agent.name}
                </option>
              ))}
            </select>
          </div>
        )}

        {/* Input Mapping JSON */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Input Mapping (JSON)
          </label>
          <textarea
            value={inputMappingText}
            onChange={(e) => setInputMappingText(e.target.value)}
            rows="6"
            className="w-full px-3 py-2 border border-gray-300 rounded-lg font-mono text-sm"
            placeholder={'{\n  "message": "${trigger.message}",\n  "context": "${step1.result}"\n}'}
          />
          <p className="text-xs text-gray-500 mt-1">
            Use $&#123;variableName&#125; to reference context variables
          </p>
        </div>

        {/* Output Variable */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Output Variable
          </label>
          <input
            type="text"
            value={formData.outputVariable}
            onChange={(e) => setFormData({ ...formData, outputVariable: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            placeholder="e.g., step1"
          />
          <p className="text-xs text-gray-500 mt-1">
            Store result as $&#123;outputVariable&#125; for later steps
          </p>
        </div>

        {/* Condition Expression (for conditional execution) */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Condition Expression (optional)
          </label>
          <input
            type="text"
            value={formData.conditionExpression}
            onChange={(e) => setFormData({ ...formData, conditionExpression: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            placeholder="e.g., ${step1.status}==success"
          />
          <p className="text-xs text-gray-500 mt-1">
            Skip step if condition evaluates to false
          </p>
        </div>

        {/* Timeout */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Timeout (seconds)
          </label>
          <input
            type="number"
            value={formData.timeoutSeconds}
            onChange={(e) => setFormData({ ...formData, timeoutSeconds: parseInt(e.target.value) })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            min="10"
            max="3600"
          />
        </div>

        {/* Retry Config */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Retry Config (JSON, optional)
          </label>
          <textarea
            value={retryConfigText}
            onChange={(e) => setRetryConfigText(e.target.value)}
            rows="4"
            className="w-full px-3 py-2 border border-gray-300 rounded-lg font-mono text-sm"
            placeholder={'{\n  "maxRetries": 3,\n  "initialDelayMs": 1000,\n  "multiplier": 2.0\n}'}
          />
        </div>

        {/* Action Buttons */}
        <div className="flex gap-2 pt-4 border-t">
          <button
            onClick={handleSubmit}
            className="flex-1 btn-primary"
          >
            Save Changes
          </button>
          <button
            onClick={onDelete}
            className="px-4 py-2 bg-red-50 text-red-700 rounded-lg hover:bg-red-100"
          >
            Delete
          </button>
        </div>
      </div>
    </div>
  )
}

export default StepConfigPanel
