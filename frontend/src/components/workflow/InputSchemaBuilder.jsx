import { useState } from 'react'

/**
 * InputSchemaBuilder - Visual JSON Schema builder for workflow inputs
 *
 * Allows users to define workflow input fields with:
 * - Field name, type, title, description
 * - Required/optional flags
 * - Default values
 * - Options for select/enum fields
 */
function InputSchemaBuilder({ schema, onChange }) {
  const [fields, setFields] = useState(
    schema?.properties ? Object.keys(schema.properties).map(key => ({
      name: key,
      ...schema.properties[key],
      required: schema.required?.includes(key) || false
    })) : []
  )
  const [showPreview, setShowPreview] = useState(false)

  // Add a new field
  const handleAddField = () => {
    const newFields = [...fields, {
      name: '',
      type: 'string',
      title: '',
      description: '',
      required: false,
      default: ''
    }]
    setFields(newFields)
  }

  // Remove a field
  const handleRemoveField = (index) => {
    const newFields = fields.filter((_, i) => i !== index)
    setFields(newFields)
    updateSchema(newFields)
  }

  // Update a field property
  const handleFieldChange = (index, property, value) => {
    const newFields = [...fields]
    newFields[index] = { ...newFields[index], [property]: value }
    setFields(newFields)
    updateSchema(newFields)
  }

  // Generate JSON Schema from fields
  const updateSchema = (currentFields) => {
    if (currentFields.length === 0) {
      onChange(null)
      return
    }

    const properties = {}
    const required = []

    currentFields.forEach(field => {
      if (!field.name.trim()) return

      const fieldSchema = {
        type: field.type,
        title: field.title || field.name
      }

      if (field.description) {
        fieldSchema.description = field.description
      }

      if (field.default !== undefined && field.default !== '') {
        fieldSchema.default = field.default
      }

      // Handle enum/select options
      if (field.type === 'enum' && field.options) {
        fieldSchema.enum = field.options.split(',').map(o => o.trim()).filter(o => o)
        fieldSchema.type = 'string' // enum is string type
      }

      properties[field.name] = fieldSchema

      if (field.required) {
        required.push(field.name)
      }
    })

    const jsonSchema = {
      type: 'object',
      properties
    }

    if (required.length > 0) {
      jsonSchema.required = required
    }

    onChange(jsonSchema)
  }

  // Get current JSON Schema
  const getCurrentSchema = () => {
    const properties = {}
    const required = []

    fields.forEach(field => {
      if (!field.name.trim()) return

      const fieldSchema = {
        type: field.type,
        title: field.title || field.name
      }

      if (field.description) {
        fieldSchema.description = field.description
      }

      if (field.default !== undefined && field.default !== '') {
        fieldSchema.default = field.default
      }

      if (field.type === 'enum' && field.options) {
        fieldSchema.enum = field.options.split(',').map(o => o.trim()).filter(o => o)
        fieldSchema.type = 'string'
      }

      properties[field.name] = fieldSchema

      if (field.required) {
        required.push(field.name)
      }
    })

    const jsonSchema = {
      type: 'object',
      properties
    }

    if (required.length > 0) {
      jsonSchema.required = required
    }

    return jsonSchema
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div>
          <h3 className="text-lg font-semibold text-gray-900">Input Fields</h3>
          <p className="text-sm text-gray-600">Define the inputs users will provide when executing this workflow</p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => setShowPreview(!showPreview)}
            className="px-3 py-1 text-sm border border-gray-300 rounded-lg hover:bg-gray-50"
          >
            {showPreview ? 'Hide' : 'Show'} JSON Schema
          </button>
          <button
            type="button"
            onClick={handleAddField}
            className="btn-primary text-sm"
          >
            + Add Field
          </button>
        </div>
      </div>

      {/* JSON Schema Preview */}
      {showPreview && fields.length > 0 && (
        <div className="bg-gray-50 border border-gray-200 rounded-lg p-4">
          <h4 className="text-sm font-semibold text-gray-700 mb-2">Generated JSON Schema</h4>
          <pre className="text-xs bg-white p-3 rounded border border-gray-200 overflow-x-auto">
            {JSON.stringify(getCurrentSchema(), null, 2)}
          </pre>
        </div>
      )}

      {/* Fields List */}
      {fields.length === 0 ? (
        <div className="text-center py-8 border-2 border-dashed border-gray-300 rounded-lg">
          <p className="text-gray-500 mb-2">No input fields defined</p>
          <p className="text-sm text-gray-400">Add fields to define what data users need to provide</p>
        </div>
      ) : (
        <div className="space-y-4">
          {fields.map((field, index) => (
            <div key={index} className="border border-gray-200 rounded-lg p-4 bg-white">
              <div className="flex justify-between items-start mb-4">
                <h4 className="font-medium text-gray-900">Field {index + 1}</h4>
                <button
                  type="button"
                  onClick={() => handleRemoveField(index)}
                  className="text-red-600 hover:text-red-800 text-sm"
                >
                  Remove
                </button>
              </div>

              <div className="grid grid-cols-2 gap-4">
                {/* Field Name */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Field Name (Key) *
                  </label>
                  <input
                    type="text"
                    value={field.name}
                    onChange={(e) => handleFieldChange(index, 'name', e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                    placeholder="e.g., product_code"
                  />
                  <p className="text-xs text-gray-500 mt-1">Unique identifier for this field</p>
                </div>

                {/* Field Type */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Type *
                  </label>
                  <select
                    value={field.type}
                    onChange={(e) => handleFieldChange(index, 'type', e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                  >
                    <option value="string">Text</option>
                    <option value="number">Number</option>
                    <option value="integer">Integer</option>
                    <option value="boolean">Boolean (Yes/No)</option>
                    <option value="enum">Select (Dropdown)</option>
                  </select>
                </div>

                {/* Field Title */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Display Label
                  </label>
                  <input
                    type="text"
                    value={field.title || ''}
                    onChange={(e) => handleFieldChange(index, 'title', e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                    placeholder="e.g., Product Code"
                  />
                  <p className="text-xs text-gray-500 mt-1">Human-readable label</p>
                </div>

                {/* Default Value */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Default Value
                  </label>
                  <input
                    type={field.type === 'number' || field.type === 'integer' ? 'number' : 'text'}
                    value={field.default || ''}
                    onChange={(e) => handleFieldChange(index, 'default', e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                    placeholder="Optional default value"
                  />
                </div>
              </div>

              {/* Description */}
              <div className="mt-4">
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Description
                </label>
                <textarea
                  value={field.description || ''}
                  onChange={(e) => handleFieldChange(index, 'description', e.target.value)}
                  rows="2"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                  placeholder="Help text for this field..."
                />
              </div>

              {/* Options for enum type */}
              {field.type === 'enum' && (
                <div className="mt-4">
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Options (comma-separated)
                  </label>
                  <input
                    type="text"
                    value={field.options || ''}
                    onChange={(e) => handleFieldChange(index, 'options', e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
                    placeholder="e.g., Option1, Option2, Option3"
                  />
                  <p className="text-xs text-gray-500 mt-1">
                    Separate multiple options with commas
                  </p>
                </div>
              )}

              {/* Required checkbox */}
              <div className="mt-4">
                <label className="flex items-center">
                  <input
                    type="checkbox"
                    checked={field.required}
                    onChange={(e) => handleFieldChange(index, 'required', e.target.checked)}
                    className="h-4 w-4 text-primary-600 focus:ring-primary-500 border-gray-300 rounded"
                  />
                  <span className="ml-2 text-sm text-gray-700">Required field</span>
                </label>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default InputSchemaBuilder
