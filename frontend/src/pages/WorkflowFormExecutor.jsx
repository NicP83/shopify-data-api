import { useState, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import api from '../services/api'

/**
 * WorkflowFormExecutor - Dynamic form-based workflow execution
 *
 * Generates a form from the workflow's JSON Schema and executes it
 * Supports public and authenticated workflows
 */
function WorkflowFormExecutor() {
  const { id } = useParams()

  const [workflow, setWorkflow] = useState(null)
  const [formData, setFormData] = useState({})
  const [loading, setLoading] = useState(true)
  const [executing, setExecuting] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    loadWorkflow()
  }, [id])

  const loadWorkflow = async () => {
    try {
      setLoading(true)
      const response = await api.getWorkflow(id)
      setWorkflow(response.data)

      // Initialize form data with defaults from schema
      if (response.data.inputSchemaJson?.properties) {
        const defaults = {}
        Object.entries(response.data.inputSchemaJson.properties).forEach(([key, field]) => {
          if (field.default !== undefined) {
            defaults[key] = field.default
          }
        })
        setFormData(defaults)
      }
    } catch (error) {
      console.error('Error loading workflow:', error)
      setError('Failed to load workflow. Please check if the workflow exists and is accessible.')
    } finally {
      setLoading(false)
    }
  }

  const handleInputChange = (fieldName, value) => {
    setFormData({ ...formData, [fieldName]: value })
  }

  const handleSubmit = async (e) => {
    e.preventDefault()

    try {
      setExecuting(true)
      setError(null)
      setResult(null)

      // Validate required fields
      if (workflow.inputSchemaJson?.required) {
        for (const requiredField of workflow.inputSchemaJson.required) {
          if (!formData[requiredField] || formData[requiredField] === '') {
            setError(`Field "${requiredField}" is required`)
            return
          }
        }
      }

      // Execute workflow
      const response = workflow.isPublic
        ? await api.executePublicWorkflow(id, formData)
        : await api.executeWorkflow(id, formData)

      setResult(response.data)
    } catch (error) {
      console.error('Error executing workflow:', error)
      setError(error.response?.data?.error || 'Failed to execute workflow')
    } finally {
      setExecuting(false)
    }
  }

  const renderField = (fieldName, fieldSchema) => {
    const value = formData[fieldName] || ''
    const isRequired = workflow.inputSchemaJson?.required?.includes(fieldName)

    const baseClassName = "w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"

    switch (fieldSchema.type) {
      case 'string':
        // Handle enum (select) type
        if (fieldSchema.enum) {
          return (
            <select
              value={value}
              onChange={(e) => handleInputChange(fieldName, e.target.value)}
              required={isRequired}
              className={baseClassName}
            >
              <option value="">Select an option...</option>
              {fieldSchema.enum.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          )
        }

        // Regular text input
        return (
          <input
            type="text"
            value={value}
            onChange={(e) => handleInputChange(fieldName, e.target.value)}
            required={isRequired}
            className={baseClassName}
            placeholder={fieldSchema.description || ''}
          />
        )

      case 'number':
      case 'integer':
        return (
          <input
            type="number"
            value={value}
            onChange={(e) => handleInputChange(fieldName, e.target.value)}
            required={isRequired}
            step={fieldSchema.type === 'integer' ? '1' : 'any'}
            className={baseClassName}
            placeholder={fieldSchema.description || ''}
          />
        )

      case 'boolean':
        return (
          <div className="flex items-center">
            <input
              type="checkbox"
              checked={value === true || value === 'true'}
              onChange={(e) => handleInputChange(fieldName, e.target.checked)}
              className="h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
            />
            <span className="ml-2 text-sm text-gray-600">
              {fieldSchema.description || 'Check to enable'}
            </span>
          </div>
        )

      default:
        return (
          <textarea
            value={value}
            onChange={(e) => handleInputChange(fieldName, e.target.value)}
            required={isRequired}
            rows="3"
            className={baseClassName}
            placeholder={fieldSchema.description || ''}
          />
        )
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto mb-4"></div>
          <p className="text-gray-600">Loading workflow...</p>
        </div>
      </div>
    )
  }

  if (!workflow) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="card max-w-md">
          <div className="text-center text-red-600">
            <svg className="w-16 h-16 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <h2 className="text-xl font-semibold mb-2">Workflow Not Found</h2>
            <p className="text-gray-600">The requested workflow could not be loaded.</p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50 py-8 px-4">
      <div className="max-w-2xl mx-auto">
        {/* Header */}
        <div className="card mb-6">
          <div className="flex items-start gap-4">
            <div className="flex-shrink-0">
              <div className="w-12 h-12 bg-primary-100 rounded-lg flex items-center justify-center">
                <svg className="w-6 h-6 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                </svg>
              </div>
            </div>
            <div className="flex-1">
              <h1 className="text-2xl font-bold text-gray-900 mb-2">{workflow.name}</h1>
              {workflow.description && (
                <p className="text-gray-600">{workflow.description}</p>
              )}
              <div className="flex gap-2 mt-3">
                {workflow.isPublic && (
                  <span className="px-2 py-1 bg-blue-100 text-blue-700 text-xs font-semibold rounded">
                    Public
                  </span>
                )}
                <span className="px-2 py-1 bg-gray-100 text-gray-700 text-xs font-semibold rounded">
                  Form Interface
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Form */}
        <div className="card">
          <form onSubmit={handleSubmit} className="space-y-6">
            <h2 className="text-lg font-semibold text-gray-900">Workflow Inputs</h2>

            {/* Dynamic form fields */}
            {workflow.inputSchemaJson?.properties ? (
              <div className="space-y-4">
                {Object.entries(workflow.inputSchemaJson.properties).map(([fieldName, fieldSchema]) => (
                  <div key={fieldName}>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                      {fieldSchema.title || fieldName}
                      {workflow.inputSchemaJson.required?.includes(fieldName) && (
                        <span className="text-red-500 ml-1">*</span>
                      )}
                    </label>
                    {renderField(fieldName, fieldSchema)}
                    {fieldSchema.description && fieldSchema.type !== 'boolean' && (
                      <p className="text-xs text-gray-500 mt-1">{fieldSchema.description}</p>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-8 bg-gray-50 rounded-lg border-2 border-dashed border-gray-300">
                <p className="text-gray-500">No input fields defined for this workflow</p>
              </div>
            )}

            {/* Error message */}
            {error && (
              <div className="bg-red-50 border border-red-200 rounded-lg p-4">
                <div className="flex">
                  <svg className="w-5 h-5 text-red-400 mr-2" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                  </svg>
                  <p className="text-sm text-red-700">{error}</p>
                </div>
              </div>
            )}

            {/* Submit button */}
            <button
              type="submit"
              disabled={executing}
              className="w-full btn-primary disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {executing ? (
                <span className="flex items-center justify-center">
                  <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  Executing...
                </span>
              ) : (
                'Execute Workflow'
              )}
            </button>
          </form>
        </div>

        {/* Result */}
        {result && (
          <div className={`card mt-6 ${result.success ? 'bg-green-50 border-green-200' : 'bg-red-50 border-red-200'}`}>
            <h3 className="text-lg font-semibold mb-4 flex items-center">
              {result.success ? (
                <>
                  <svg className="w-6 h-6 text-green-600 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <span className="text-green-900">Execution Successful</span>
                </>
              ) : (
                <>
                  <svg className="w-6 h-6 text-red-600 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <span className="text-red-900">Execution Failed</span>
                </>
              )}
            </h3>

            {result.error && (
              <div className="mb-4 p-3 bg-red-100 rounded text-red-800 text-sm">
                {result.error}
              </div>
            )}

            {result.context && Object.keys(result.context).length > 0 && (
              <div>
                <h4 className="text-sm font-semibold text-gray-700 mb-2">Result Data:</h4>
                <pre className="bg-white p-4 rounded-lg border border-gray-200 overflow-x-auto text-xs">
                  {JSON.stringify(result.context, null, 2)}
                </pre>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

export default WorkflowFormExecutor
