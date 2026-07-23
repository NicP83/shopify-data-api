# Workflow Execution Interfaces - Implementation Plan

**Date**: 2025-10-15
**Status**: Ready for Implementation
**Approved**: Yes

---

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [Conversation History](#conversation-history)
3. [Current System Architecture](#current-system-architecture)
4. [Requirements & Goals](#requirements--goals)
5. [Implementation Plan](#implementation-plan)
6. [Technical Specifications](#technical-specifications)
7. [Example Use Cases](#example-use-cases)
8. [Testing Strategy](#testing-strategy)

---

## Executive Summary

This document outlines the approved plan to transform the workflow system from internal automation into **shareable, executable tools** with auto-generated user interfaces.

### Key Features to Build:
- **Input Schema Definition**: Workflows define what data they need via JSON Schema
- **Multiple Interface Types**: Form, Chat, API with auto-generated UIs
- **Public Access**: Shareable links for workflows (e.g., `/execute/123`)
- **Modular UI Builder**: Visual workflow creation without hardcoding
- **Dynamic UI Generation**: Automatically create forms/chats from input schema

### Business Value:
- ✅ Workflows become standalone applications
- ✅ No-code tool builder for business users
- ✅ Shareable public links for customers/partners
- ✅ Self-documenting APIs

---

## Conversation History

### Evolution of Understanding

The requirements evolved through several iterations of feedback:

#### Iteration 1: Hardcoded Product Description Workflow ❌
**Initial Suggestion**: Create specific agents for product fetching, category detection, description writing, HTML formatting with hardcoded workflow.

**User Feedback**:
> "we already have a shopify look up tool/api.."

**Lesson**: User already has Shopify integration; don't duplicate functionality.

---

#### Iteration 2: Simplified with Existing APIs ❌
**Second Suggestion**: Use existing Product APIs via MCP tools, but still with hardcoded workflow structure.

**User Feedback**:
> "the idea of the workflow page was to have a flexible system so we can manually create those flow and not have it hardcoded. what you suggested is great and correct but we have to be able to build this for specific situations. We have already ability to add agents so that part is easy, we can add agents now we must create the interface in a modular way"

**Lesson**: User wants **modular, visual workflow builder**, not hardcoded solutions.

---

#### Iteration 3: UI Enhancements Only ❌
**Third Suggestion**: Focus on improving WorkflowEditor UI with visual input mapping builder, context variable helper, execution interface.

**User Feedback**:
> "in the workflow we should have also inputs, example the input is a chat bot, then it should create a page link to that so we can use that function.. or something else to make it usable."

**Lesson**: Workflows need to be **executable with shareable interfaces**, not just internal tools.

---

#### Iteration 4: Workflow Execution Interfaces ✅
**Final Approved Plan**:
- Add input schema to workflows
- Support multiple interface types (Form, Chat, API)
- Auto-generate UIs from schemas
- Create public execution endpoints with shareable links
- Build workflow gallery for discovery

**User Response**:
> "this is a greaet plan. so let`s document this in detail so we can continue the development after compacting"

**Status**: ✅ **APPROVED**

---

## Current System Architecture

### Backend (Spring Boot + PostgreSQL)

#### Existing Entities

**Workflow.java** (`src/main/java/com/shopify/api/model/agent/Workflow.java`)
```java
@Entity
@Table(name = "workflows")
public class Workflow {
    private Long id;
    private String name;
    private String description;
    private String triggerType;           // MANUAL, SCHEDULED, EVENT
    private JsonNode triggerConfigJson;
    private String executionMode;         // SYNC or ASYNC
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Relationships
    private List<WorkflowStep> workflowSteps;
    private Set<WorkflowExecution> workflowExecutions;
    private Set<WorkflowSchedule> workflowSchedules;
}
```

**WorkflowStep.java** (`src/main/java/com/shopify/api/model/agent/WorkflowStep.java`)
```java
@Entity
@Table(name = "workflow_steps")
public class WorkflowStep {
    private Long id;
    private Integer stepOrder;
    private String stepType;              // AGENT_EXECUTION, APPROVAL, CONDITION, PARALLEL
    private Agent agent;
    private String name;
    private JsonNode inputMappingJson;    // Maps context vars to agent input
    private String outputVariable;        // Where to store result: ${stepName}
    private String conditionExpression;   // e.g., ${category}==tools
    private Integer[] dependsOn;
    private JsonNode approvalConfigJson;
    private JsonNode retryConfigJson;
    private Integer timeoutSeconds;
}
```

**WorkflowExecution.java**
```java
@Entity
@Table(name = "workflow_executions")
public class WorkflowExecution {
    private Long id;
    private Workflow workflow;
    private String status;                // PENDING, RUNNING, COMPLETED, FAILED
    private JsonNode triggerDataJson;     // Input data that started workflow
    private JsonNode contextDataJson;     // Accumulated context during execution
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
}
```

#### Existing Services

**WorkflowOrchestratorService.java** (`src/main/java/com/shopify/api/service/agent/WorkflowOrchestratorService.java` - 583 lines)

Sophisticated orchestration features already implemented:
- ✅ Sequential step execution with Reactive Mono chains
- ✅ Context passing between steps
- ✅ Variable substitution: `${trigger.field}`, `${stepName.output.path}`
- ✅ Conditional step execution: `shouldSkipStep()` evaluates expressions
- ✅ Retry logic with exponential backoff
- ✅ Timeout handling per step
- ✅ MCP tool execution integration
- ✅ Approval workflow support

Key methods:
```java
public Mono<WorkflowExecutionResult> executeWorkflow(Long workflowId, JsonNode triggerData)
private Mono<WorkflowExecutionResult> executeStepsSequentially(...)
private Mono<JsonNode> executeStep(WorkflowStep step, ObjectNode context, WorkflowExecution execution)
private JsonNode buildAgentInput(WorkflowStep step, ObjectNode context)
private JsonNode substituteVariables(JsonNode node, ObjectNode context)
private boolean shouldSkipStep(WorkflowStep step, ObjectNode context)
```

**ProductService.java** (`src/main/java/com/shopify/api/service/ProductService.java` - 542 lines)

Existing Shopify integration:
```java
public Map<String, Object> getProducts(int first)
public Map<String, Object> getProductById(String productId)
public Map<String, Object> searchProducts(String searchQuery, int first, boolean includeArchived)
public Mono<Map<String, Object>> searchProductsReactive(String searchQuery, int first)
```

Agents can access these via MCP `mcp_call` tool.

#### Existing Controllers

**WorkflowController.java** (`src/main/java/com/shopify/api/controller/agent/WorkflowController.java` - 309 lines)

Existing REST API endpoints:
```
POST   /api/workflows                        - Create workflow
GET    /api/workflows                        - Get all workflows
GET    /api/workflows/{id}                   - Get by ID
PUT    /api/workflows/{id}                   - Update workflow
DELETE /api/workflows/{id}                   - Delete workflow
POST   /api/workflows/{id}/activate
POST   /api/workflows/{id}/deactivate
POST   /api/workflows/{workflowId}/steps     - Add step
GET    /api/workflows/{workflowId}/steps     - Get steps
PUT    /api/workflows/{workflowId}/steps/{stepId}
DELETE /api/workflows/{workflowId}/steps/{stepId}
POST   /api/workflows/{workflowId}/steps/reorder
POST   /api/workflows/{id}/execute           - Execute workflow
GET    /api/agents/{agentId}/executions      - Get execution history
```

**AgentController.java** (`src/main/java/com/shopify/api/controller/agent/AgentController.java` - 268 lines)

Agent management:
```
POST   /api/agents                           - Create agent
GET    /api/agents                           - Get all agents
GET    /api/agents/{id}                      - Get by ID
GET    /api/agents/by-name/{name}            - Get by name
PUT    /api/agents/{id}                      - Update agent
DELETE /api/agents/{id}                      - Delete agent
POST   /api/agents/{id}/activate
POST   /api/agents/{id}/deactivate
POST   /api/agents/{agentId}/tools/{toolId}  - Assign tool
DELETE /api/agents/{agentId}/tools/{toolId}  - Remove tool
GET    /api/agents/{agentId}/tools           - Get agent tools
POST   /api/agents/{id}/execute              - Execute agent
GET    /api/agents/{agentId}/executions      - Get executions
```

### Frontend (React + Vite)

**WorkflowEditor.jsx** (`frontend/src/pages/WorkflowEditor.jsx` - 537 lines)

Current state:
- Two-step process: Save workflow first, then add steps
- Step form modal with fields:
  - name
  - stepOrder
  - stepType (AGENT_EXECUTION, APPROVAL, etc.)
  - agentId (dropdown)
  - inputMappingJson (raw JSON textarea) ⚠️ Not user-friendly
  - outputVariable
  - conditionExpression
  - timeoutSeconds
- Lists steps with edit/delete actions
- ❌ No input schema builder
- ❌ No interface type selector
- ❌ No execution interface
- ❌ No public link generation

### Database Schema (PostgreSQL)

**workflows** table (from `V002__multi_agent_system.sql`):
```sql
CREATE TABLE workflows (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    trigger_type VARCHAR(50) NOT NULL,
    trigger_config_json JSONB DEFAULT '{}'::jsonb,
    execution_mode VARCHAR(20) NOT NULL DEFAULT 'SYNC',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**workflow_steps** table:
```sql
CREATE TABLE workflow_steps (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    step_order INTEGER NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    agent_id BIGINT REFERENCES agents(id) ON DELETE SET NULL,
    name VARCHAR(255),
    input_mapping_json JSONB DEFAULT '{}'::jsonb,
    output_variable VARCHAR(100),
    condition_expression TEXT,
    depends_on INTEGER[],
    approval_config_json JSONB DEFAULT '{}'::jsonb,
    retry_config_json JSONB DEFAULT '{}'::jsonb,
    timeout_seconds INTEGER DEFAULT 300,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## Requirements & Goals

### Functional Requirements

1. **Input Schema Definition**
   - Workflows must define their input schema using JSON Schema
   - Support common field types: text, number, select, textarea, checkbox
   - Support field properties: title, description, required, default, options
   - Visual schema builder (no raw JSON editing)

2. **Multiple Interface Types**
   - **FORM**: Auto-generated web form from input schema
   - **CHAT**: Conversational interface for workflows
   - **API**: Programmatic access with documentation
   - **CUSTOM**: Reserved for future extensions

3. **Public Access**
   - Workflows can be marked as public (accessible without auth)
   - Generate shareable URLs: `/execute/{workflowId}`, `/chat/{workflowId}`
   - Public execution endpoint with validation

4. **Modular UI Builder**
   - Visual workflow creation without hardcoding
   - No JSON knowledge required for users
   - Drag-and-drop step reordering
   - Context variable helper panel
   - Variable autocomplete in input fields

5. **Dynamic UI Generation**
   - Parse input schema to auto-generate forms
   - Create appropriate input components based on field type
   - Client-side validation from schema
   - Real-time execution feedback

### Non-Functional Requirements

1. **Backward Compatibility**: Existing workflows must continue to work
2. **Security**: Public workflows must validate inputs and prevent abuse
3. **Performance**: Form generation should be instant (<100ms)
4. **Usability**: Non-technical users should be able to create workflows
5. **Maintainability**: Modular, reusable components

---

## Implementation Plan

### Phase 1: Backend - Input Schema Support (1 hour)

#### 1.1 Database Migration

**File**: `src/main/resources/db/migration/V004__add_workflow_input_schema.sql`

```sql
-- ============================================
-- Add Input Schema Support to Workflows
-- Version: 4
-- Date: 2025-10-15
-- ============================================

-- Add new columns to workflows table
ALTER TABLE workflows
ADD COLUMN input_schema_json JSONB,
ADD COLUMN interface_type VARCHAR(50) DEFAULT 'FORM',
ADD COLUMN is_public BOOLEAN DEFAULT false;

-- Add indexes for querying
CREATE INDEX idx_workflows_public
ON workflows(is_public)
WHERE is_public = true;

CREATE INDEX idx_workflows_interface_type
ON workflows(interface_type);

-- Add comments
COMMENT ON COLUMN workflows.input_schema_json IS 'JSON Schema defining workflow inputs (e.g., {type: "object", properties: {...}})';
COMMENT ON COLUMN workflows.interface_type IS 'Execution interface type: FORM, CHAT, API, CUSTOM';
COMMENT ON COLUMN workflows.is_public IS 'Whether workflow can be executed without authentication';

-- Log migration
DO $$
BEGIN
    RAISE NOTICE 'Migration V004 completed: Added input schema support to workflows';
END $$;
```

**Migration Checklist**:
- [ ] Create migration file
- [ ] Test migration on local database
- [ ] Verify backward compatibility (existing rows should work)
- [ ] Check indexes are created

---

#### 1.2 Update Workflow Entity

**File**: `src/main/java/com/shopify/api/model/agent/Workflow.java`

**Changes to make**:

```java
// Add these imports at the top
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;

// Add these fields to the Workflow class (after line 62, before createdAt)

/**
 * JSON Schema defining the input structure for this workflow
 * Example: {"type": "object", "properties": {"product_code": {"type": "string", ...}}}
 */
@Type(JsonBinaryType.class)
@Column(name = "input_schema_json", columnDefinition = "jsonb")
private JsonNode inputSchemaJson;

/**
 * Type of execution interface for this workflow
 * Values: FORM (web form), CHAT (chatbot), API (programmatic), CUSTOM
 */
@Column(name = "interface_type", length = 50)
@Builder.Default
private String interfaceType = "FORM";

/**
 * Whether this workflow can be executed publicly without authentication
 */
@Column(name = "is_public", nullable = false)
@Builder.Default
private Boolean isPublic = false;
```

**Lines to modify**: Insert after line 62 (after `isActive` field)

---

#### 1.3 Update CreateWorkflowRequest DTO

**File**: `src/main/java/com/shopify/api/dto/agent/CreateWorkflowRequest.java`

**Changes to make**:

```java
// Add after line 38 (after isActive field)

/**
 * JSON Schema for workflow inputs (optional)
 */
private JsonNode inputSchemaJson;

/**
 * Interface type for workflow execution
 */
@Pattern(regexp = "FORM|CHAT|API|CUSTOM", message = "Interface type must be FORM, CHAT, API, or CUSTOM")
@Builder.Default
private String interfaceType = "FORM";

/**
 * Whether workflow is publicly accessible
 */
@Builder.Default
private Boolean isPublic = false;
```

---

#### 1.4 Update WorkflowResponse DTO

**File**: `src/main/java/com/shopify/api/dto/agent/WorkflowResponse.java`

**Changes to make**:

```java
// Add after line 30 (after stepCount field)
private JsonNode inputSchemaJson;
private String interfaceType;
private Boolean isPublic;

// Update fromEntity() method (replace lines 35-47)
public static WorkflowResponse fromEntity(Workflow workflow) {
    return WorkflowResponse.builder()
        .id(workflow.getId())
        .name(workflow.getName())
        .description(workflow.getDescription())
        .triggerType(workflow.getTriggerType())
        .triggerConfigJson(workflow.getTriggerConfigJson())
        .executionMode(workflow.getExecutionMode())
        .isActive(workflow.getIsActive())
        .inputSchemaJson(workflow.getInputSchemaJson())
        .interfaceType(workflow.getInterfaceType())
        .isPublic(workflow.getIsPublic())
        .createdAt(workflow.getCreatedAt())
        .updatedAt(workflow.getUpdatedAt())
        .stepCount(workflow.getWorkflowSteps() != null ? workflow.getWorkflowSteps().size() : 0)
        .build();
}
```

---

#### 1.5 Update WorkflowController

**File**: `src/main/java/com/shopify/api/controller/agent/WorkflowController.java`

**Changes to make**:

1. **Update createWorkflow()** (lines 45-56):
```java
Workflow workflow = Workflow.builder()
    .name(request.getName())
    .description(request.getDescription())
    .triggerType(request.getTriggerType())
    .triggerConfigJson(request.getTriggerConfigJson())
    .executionMode(request.getExecutionMode())
    .isActive(request.getIsActive())
    .inputSchemaJson(request.getInputSchemaJson())      // ADD THIS
    .interfaceType(request.getInterfaceType())          // ADD THIS
    .isPublic(request.getIsPublic())                    // ADD THIS
    .build();
```

2. **Update updateWorkflow()** (lines 114-121):
```java
Workflow updatedWorkflow = Workflow.builder()
    .name(request.getName())
    .description(request.getDescription())
    .triggerType(request.getTriggerType())
    .triggerConfigJson(request.getTriggerConfigJson())
    .executionMode(request.getExecutionMode())
    .isActive(request.getIsActive())
    .inputSchemaJson(request.getInputSchemaJson())      // ADD THIS
    .interfaceType(request.getInterfaceType())          // ADD THIS
    .isPublic(request.getIsPublic())                    // ADD THIS
    .build();
```

3. **Add public execution endpoint** (add at end of class, before exception handler):
```java
/**
 * Execute a public workflow (no authentication required)
 * POST /api/workflows/public/{id}/execute
 */
@PostMapping("/public/{id}/execute")
public Mono<ResponseEntity<Object>> executePublicWorkflow(
    @PathVariable Long id,
    @RequestBody(required = false) com.fasterxml.jackson.databind.JsonNode input
) {
    log.info("Public execution request for workflow ID: {}", id);

    // Verify workflow exists and is public
    return workflowService.getWorkflowById(id)
        .map(workflow -> {
            if (!Boolean.TRUE.equals(workflow.getIsPublic())) {
                throw new IllegalArgumentException("Workflow is not public");
            }
            return workflow;
        })
        .flatMap(workflow -> {
            // Default to empty object if no input provided
            com.fasterxml.jackson.databind.JsonNode inputData = input != null
                ? input
                : new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();

            return workflowOrchestratorService.executeWorkflow(id, inputData)
                .map(result -> {
                    java.util.Map<String, Object> response = new java.util.HashMap<>();
                    response.put("success", result.isSuccess());
                    response.put("context", result.getContext());
                    if (result.getErrorMessage() != null) {
                        response.put("error", result.getErrorMessage());
                    }
                    return ResponseEntity.ok((Object) response);
                });
        })
        .onErrorResume(error -> {
            log.error("Public workflow execution error: {}", error.getMessage());
            java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", error.getMessage());
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body((Object) errorResponse));
        })
        .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
}
```

**Phase 1 Testing**:
```bash
# Test workflow creation with new fields
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Workflow",
    "description": "Test",
    "triggerType": "MANUAL",
    "executionMode": "SYNC",
    "inputSchemaJson": {
      "type": "object",
      "properties": {
        "message": {
          "type": "string",
          "title": "Message"
        }
      }
    },
    "interfaceType": "CHAT",
    "isPublic": true
  }'

# Verify response includes new fields
# Test backward compatibility - create workflow without new fields
```

---

### Phase 2: Frontend - Input Schema Builder (1.5 hours)

#### 2.1 Create InputSchemaBuilder Component

**File**: `frontend/src/components/InputSchemaBuilder.jsx` (NEW FILE)

```jsx
import React, { useState } from 'react';
import PropTypes from 'prop-types';

/**
 * Visual JSON Schema builder component
 * Allows users to define workflow input fields without writing JSON
 */
const InputSchemaBuilder = ({ schema, onChange }) => {
  // Parse existing schema or start with empty
  const [fields, setFields] = useState(() => {
    if (schema && schema.properties) {
      return Object.entries(schema.properties).map(([name, props]) => ({
        name,
        ...props,
        required: schema.required?.includes(name) || false
      }));
    }
    return [];
  });

  // Add new field
  const addField = () => {
    const newFields = [...fields, {
      name: '',
      type: 'string',
      title: '',
      description: '',
      required: false
    }];
    setFields(newFields);
  };

  // Update field
  const updateField = (index, updates) => {
    const newFields = [...fields];
    newFields[index] = { ...newFields[index], ...updates };
    setFields(newFields);
    generateSchema(newFields);
  };

  // Remove field
  const removeField = (index) => {
    const newFields = fields.filter((_, i) => i !== index);
    setFields(newFields);
    generateSchema(newFields);
  };

  // Generate JSON Schema from fields
  const generateSchema = (fieldsArray) => {
    const properties = {};
    const required = [];

    fieldsArray.forEach(field => {
      if (!field.name) return;

      properties[field.name] = {
        type: field.type,
        title: field.title || field.name,
        description: field.description || undefined,
        default: field.default || undefined,
        enum: field.enum || undefined,
        placeholder: field.placeholder || undefined
      };

      if (field.required) {
        required.push(field.name);
      }
    });

    const jsonSchema = {
      type: 'object',
      properties,
      required: required.length > 0 ? required : undefined
    };

    onChange(jsonSchema);
  };

  return (
    <div className="input-schema-builder">
      <div className="mb-4">
        <h3 className="text-lg font-medium mb-2">Define Workflow Inputs</h3>
        <p className="text-sm text-gray-600">
          Add input fields that users will provide when executing this workflow.
          These become available as <code>${'{trigger.fieldName}'}</code> in your steps.
        </p>
      </div>

      {fields.map((field, index) => (
        <div key={index} className="border rounded p-4 mb-3 bg-gray-50">
          <div className="grid grid-cols-2 gap-3">
            {/* Field Name */}
            <div>
              <label className="block text-sm font-medium mb-1">
                Field Name <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={field.name}
                onChange={(e) => updateField(index, { name: e.target.value })}
                placeholder="e.g., product_code"
                className="w-full border rounded px-3 py-2"
              />
              <p className="text-xs text-gray-500 mt-1">
                Access as: ${'{trigger.' + field.name + '}'}
              </p>
            </div>

            {/* Field Type */}
            <div>
              <label className="block text-sm font-medium mb-1">Type</label>
              <select
                value={field.type}
                onChange={(e) => updateField(index, { type: e.target.value })}
                className="w-full border rounded px-3 py-2"
              >
                <option value="string">Text</option>
                <option value="number">Number</option>
                <option value="boolean">Checkbox</option>
                <option value="array">Multiple Selection</option>
              </select>
            </div>

            {/* Display Title */}
            <div>
              <label className="block text-sm font-medium mb-1">Display Title</label>
              <input
                type="text"
                value={field.title || ''}
                onChange={(e) => updateField(index, { title: e.target.value })}
                placeholder="e.g., Product Code"
                className="w-full border rounded px-3 py-2"
              />
            </div>

            {/* Placeholder */}
            <div>
              <label className="block text-sm font-medium mb-1">Placeholder</label>
              <input
                type="text"
                value={field.placeholder || ''}
                onChange={(e) => updateField(index, { placeholder: e.target.value })}
                placeholder="e.g., NSI-SIDECUTTERS"
                className="w-full border rounded px-3 py-2"
              />
            </div>

            {/* Description */}
            <div className="col-span-2">
              <label className="block text-sm font-medium mb-1">Description</label>
              <input
                type="text"
                value={field.description || ''}
                onChange={(e) => updateField(index, { description: e.target.value })}
                placeholder="Help text for users"
                className="w-full border rounded px-3 py-2"
              />
            </div>

            {/* Options (for dropdowns) */}
            {field.type === 'string' && (
              <div className="col-span-2">
                <label className="block text-sm font-medium mb-1">
                  Options (comma-separated, for dropdown)
                </label>
                <input
                  type="text"
                  value={field.enum?.join(', ') || ''}
                  onChange={(e) => {
                    const options = e.target.value
                      .split(',')
                      .map(o => o.trim())
                      .filter(o => o);
                    updateField(index, { enum: options.length > 0 ? options : undefined });
                  }}
                  placeholder="Option 1, Option 2, Option 3"
                  className="w-full border rounded px-3 py-2"
                />
              </div>
            )}

            {/* Required checkbox */}
            <div className="col-span-2">
              <label className="flex items-center">
                <input
                  type="checkbox"
                  checked={field.required}
                  onChange={(e) => updateField(index, { required: e.target.checked })}
                  className="mr-2"
                />
                <span className="text-sm">Required field</span>
              </label>
            </div>
          </div>

          {/* Remove button */}
          <div className="mt-3 text-right">
            <button
              onClick={() => removeField(index)}
              className="text-red-600 hover:text-red-800 text-sm"
            >
              Remove Field
            </button>
          </div>
        </div>
      ))}

      {/* Add Field button */}
      <button
        onClick={addField}
        className="bg-blue-500 text-white px-4 py-2 rounded hover:bg-blue-600"
      >
        + Add Input Field
      </button>

      {/* Schema Preview */}
      {fields.length > 0 && (
        <div className="mt-6 p-4 bg-gray-100 rounded">
          <h4 className="font-medium mb-2">Generated Schema Preview:</h4>
          <pre className="text-xs overflow-auto">
            {JSON.stringify(schema, null, 2)}
          </pre>
        </div>
      )}
    </div>
  );
};

InputSchemaBuilder.propTypes = {
  schema: PropTypes.object,
  onChange: PropTypes.func.isRequired
};

export default InputSchemaBuilder;
```

---

#### 2.2 Update WorkflowEditor

**File**: `frontend/src/pages/WorkflowEditor.jsx`

**Changes to make**:

1. **Import InputSchemaBuilder** (add at top):
```jsx
import InputSchemaBuilder from '../components/InputSchemaBuilder';
```

2. **Update workflow state** (around line 45):
```jsx
const [workflow, setWorkflow] = useState({
  name: '',
  description: '',
  triggerType: 'MANUAL',
  executionMode: 'SYNC',
  isActive: false,
  triggerConfigJson: {},
  inputSchemaJson: null,          // ADD THIS
  interfaceType: 'FORM',          // ADD THIS
  isPublic: false                 // ADD THIS
});
```

3. **Add Workflow Inputs section** (insert after workflow form, before steps section):
```jsx
{/* Workflow Inputs Configuration */}
{workflow.id && (
  <div className="card mt-6">
    <h2 className="text-xl font-bold mb-4">Workflow Inputs</h2>

    {/* Interface Type Selector */}
    <div className="mb-4">
      <label className="block text-sm font-medium mb-2">Interface Type</label>
      <select
        value={workflow.interfaceType || 'FORM'}
        onChange={(e) => {
          const updated = { ...workflow, interfaceType: e.target.value };
          setWorkflow(updated);
          // Save to backend
          fetch(`${API_BASE_URL}/api/workflows/${workflow.id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(updated)
          });
        }}
        className="border rounded px-3 py-2"
      >
        <option value="FORM">Web Form</option>
        <option value="CHAT">Chat Interface</option>
        <option value="API">API Only</option>
      </select>
      <p className="text-sm text-gray-600 mt-1">
        {workflow.interfaceType === 'FORM' && 'Users will see a form with input fields'}
        {workflow.interfaceType === 'CHAT' && 'Users will chat with the workflow'}
        {workflow.interfaceType === 'API' && 'Only accessible via API calls'}
      </p>
    </div>

    {/* Public Access Toggle */}
    <div className="mb-4">
      <label className="flex items-center">
        <input
          type="checkbox"
          checked={workflow.isPublic || false}
          onChange={(e) => {
            const updated = { ...workflow, isPublic: e.target.checked };
            setWorkflow(updated);
            // Save to backend
            fetch(`${API_BASE_URL}/api/workflows/${workflow.id}`, {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(updated)
            });
          }}
          className="mr-2"
        />
        <span className="text-sm font-medium">Make this workflow publicly accessible</span>
      </label>
    </div>

    {/* Input Schema Builder */}
    <InputSchemaBuilder
      schema={workflow.inputSchemaJson}
      onChange={(newSchema) => {
        const updated = { ...workflow, inputSchemaJson: newSchema };
        setWorkflow(updated);
        // Save to backend
        fetch(`${API_BASE_URL}/api/workflows/${workflow.id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(updated)
        });
      }}
    />

    {/* Shareable Link */}
    {workflow.isPublic && (
      <div className="mt-6 p-4 bg-green-50 border border-green-200 rounded">
        <h4 className="font-medium text-green-800 mb-2">Shareable Link</h4>
        <div className="flex items-center gap-2">
          <input
            type="text"
            value={`${window.location.origin}/${workflow.interfaceType === 'CHAT' ? 'chat' : 'execute'}/${workflow.id}`}
            readOnly
            className="flex-1 border rounded px-3 py-2 bg-white"
          />
          <button
            onClick={() => {
              navigator.clipboard.writeText(`${window.location.origin}/${workflow.interfaceType === 'CHAT' ? 'chat' : 'execute'}/${workflow.id}`);
              alert('Link copied to clipboard!');
            }}
            className="bg-green-600 text-white px-4 py-2 rounded hover:bg-green-700"
          >
            Copy Link
          </button>
          <a
            href={`/${workflow.interfaceType === 'CHAT' ? 'chat' : 'execute'}/${workflow.id}`}
            target="_blank"
            rel="noopener noreferrer"
            className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
          >
            Open
          </a>
        </div>
      </div>
    )}
  </div>
)}
```

---

### Phase 3: Dynamic Execution Interfaces (2 hours)

#### 3.1 Form Executor Component

**File**: `frontend/src/pages/WorkflowFormExecutor.jsx` (NEW FILE)

```jsx
import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const WorkflowFormExecutor = () => {
  const { workflowId } = useParams();
  const [workflow, setWorkflow] = useState(null);
  const [loading, setLoading] = useState(true);
  const [executing, setExecuting] = useState(false);
  const [formData, setFormData] = useState({});
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  // Load workflow
  useEffect(() => {
    fetch(`${API_BASE_URL}/api/workflows/${workflowId}`)
      .then(res => res.json())
      .then(data => {
        setWorkflow(data);
        // Initialize form data with defaults
        if (data.inputSchemaJson?.properties) {
          const defaults = {};
          Object.entries(data.inputSchemaJson.properties).forEach(([key, prop]) => {
            defaults[key] = prop.default || '';
          });
          setFormData(defaults);
        }
        setLoading(false);
      })
      .catch(err => {
        setError('Failed to load workflow');
        setLoading(false);
      });
  }, [workflowId]);

  // Handle form submission
  const handleSubmit = async (e) => {
    e.preventDefault();
    setExecuting(true);
    setError(null);
    setResult(null);

    try {
      const response = await fetch(`${API_BASE_URL}/api/workflows/public/${workflowId}/execute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData)
      });

      const data = await response.json();

      if (data.success) {
        setResult(data.context);
      } else {
        setError(data.error || 'Execution failed');
      }
    } catch (err) {
      setError('Network error: ' + err.message);
    } finally {
      setExecuting(false);
    }
  };

  // Render form field based on schema
  const renderField = (fieldName, fieldSchema) => {
    const value = formData[fieldName] || '';
    const isRequired = workflow.inputSchemaJson?.required?.includes(fieldName);

    const handleChange = (e) => {
      setFormData({
        ...formData,
        [fieldName]: e.target.value
      });
    };

    // Select dropdown
    if (fieldSchema.enum) {
      return (
        <select
          value={value}
          onChange={handleChange}
          required={isRequired}
          className="w-full border rounded px-3 py-2"
        >
          <option value="">Select...</option>
          {fieldSchema.enum.map(option => (
            <option key={option} value={option}>{option}</option>
          ))}
        </select>
      );
    }

    // Number input
    if (fieldSchema.type === 'number') {
      return (
        <input
          type="number"
          value={value}
          onChange={handleChange}
          placeholder={fieldSchema.placeholder}
          required={isRequired}
          className="w-full border rounded px-3 py-2"
        />
      );
    }

    // Checkbox
    if (fieldSchema.type === 'boolean') {
      return (
        <input
          type="checkbox"
          checked={value}
          onChange={(e) => setFormData({ ...formData, [fieldName]: e.target.checked })}
          className="mr-2"
        />
      );
    }

    // Text input (default)
    return (
      <input
        type="text"
        value={value}
        onChange={handleChange}
        placeholder={fieldSchema.placeholder}
        required={isRequired}
        className="w-full border rounded px-3 py-2"
      />
    );
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="text-xl">Loading workflow...</div>
      </div>
    );
  }

  if (!workflow) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="text-xl text-red-600">Workflow not found</div>
      </div>
    );
  }

  const schema = workflow.inputSchemaJson;

  return (
    <div className="min-h-screen bg-gray-100 py-8">
      <div className="max-w-2xl mx-auto bg-white rounded-lg shadow-lg p-8">
        {/* Header */}
        <h1 className="text-3xl font-bold mb-2">{workflow.name}</h1>
        {workflow.description && (
          <p className="text-gray-600 mb-6">{workflow.description}</p>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit}>
          {schema?.properties && Object.entries(schema.properties).map(([fieldName, fieldSchema]) => (
            <div key={fieldName} className="mb-4">
              <label className="block text-sm font-medium mb-1">
                {fieldSchema.title || fieldName}
                {schema.required?.includes(fieldName) && (
                  <span className="text-red-500 ml-1">*</span>
                )}
              </label>
              {fieldSchema.description && (
                <p className="text-sm text-gray-500 mb-2">{fieldSchema.description}</p>
              )}
              {renderField(fieldName, fieldSchema)}
            </div>
          ))}

          {/* Submit button */}
          <button
            type="submit"
            disabled={executing}
            className={`w-full py-3 rounded font-medium ${
              executing
                ? 'bg-gray-400 cursor-not-allowed'
                : 'bg-blue-600 hover:bg-blue-700 text-white'
            }`}
          >
            {executing ? 'Executing...' : 'Execute Workflow'}
          </button>
        </form>

        {/* Error */}
        {error && (
          <div className="mt-6 p-4 bg-red-50 border border-red-200 rounded">
            <h3 className="font-medium text-red-800 mb-1">Error</h3>
            <p className="text-red-700">{error}</p>
          </div>
        )}

        {/* Results */}
        {result && (
          <div className="mt-6 p-4 bg-green-50 border border-green-200 rounded">
            <h3 className="font-medium text-green-800 mb-2">Results</h3>
            <pre className="text-sm overflow-auto bg-white p-3 rounded">
              {JSON.stringify(result, null, 2)}
            </pre>
          </div>
        )}
      </div>
    </div>
  );
};

export default WorkflowFormExecutor;
```

---

#### 3.2 Chat Executor Component

**File**: `frontend/src/pages/WorkflowChatExecutor.jsx` (NEW FILE)

```jsx
import React, { useState, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const WorkflowChatExecutor = () => {
  const { workflowId } = useParams();
  const [workflow, setWorkflow] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const messagesEndRef = useRef(null);

  // Auto-scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Load workflow
  useEffect(() => {
    fetch(`${API_BASE_URL}/api/workflows/${workflowId}`)
      .then(res => res.json())
      .then(data => {
        setWorkflow(data);
        setLoading(false);
        // Add welcome message
        setMessages([{
          role: 'assistant',
          content: `Hello! I'm ${data.name}. ${data.description || 'How can I help you?'}`,
          timestamp: new Date()
        }]);
      })
      .catch(err => {
        setLoading(false);
      });
  }, [workflowId]);

  // Send message
  const handleSend = async () => {
    if (!input.trim() || sending) return;

    const userMessage = {
      role: 'user',
      content: input,
      timestamp: new Date()
    };

    setMessages(prev => [...prev, userMessage]);
    setInput('');
    setSending(true);

    try {
      const response = await fetch(`${API_BASE_URL}/api/workflows/public/${workflowId}/execute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: input })
      });

      const data = await response.json();

      // Extract response from context
      let assistantContent;
      if (data.success && data.context) {
        // Try to find the final output
        const contextKeys = Object.keys(data.context);
        const lastKey = contextKeys[contextKeys.length - 1];
        assistantContent = typeof data.context[lastKey] === 'string'
          ? data.context[lastKey]
          : JSON.stringify(data.context, null, 2);
      } else {
        assistantContent = data.error || 'Sorry, I encountered an error.';
      }

      const assistantMessage = {
        role: 'assistant',
        content: assistantContent,
        timestamp: new Date()
      };

      setMessages(prev => [...prev, assistantMessage]);
    } catch (err) {
      const errorMessage = {
        role: 'assistant',
        content: 'Sorry, I encountered a network error.',
        timestamp: new Date()
      };
      setMessages(prev => [...prev, errorMessage]);
    } finally {
      setSending(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="text-xl">Loading chat...</div>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-screen bg-gray-100">
      {/* Header */}
      <div className="bg-blue-600 text-white p-4 shadow">
        <h1 className="text-xl font-bold">{workflow?.name}</h1>
        {workflow?.description && (
          <p className="text-sm text-blue-100">{workflow.description}</p>
        )}
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {messages.map((msg, index) => (
          <div
            key={index}
            className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
          >
            <div
              className={`max-w-xs md:max-w-md lg:max-w-lg xl:max-w-xl px-4 py-2 rounded-lg ${
                msg.role === 'user'
                  ? 'bg-blue-600 text-white'
                  : 'bg-white text-gray-800 shadow'
              }`}
            >
              <p className="whitespace-pre-wrap">{msg.content}</p>
              <p className="text-xs mt-1 opacity-70">
                {msg.timestamp.toLocaleTimeString()}
              </p>
            </div>
          </div>
        ))}
        {sending && (
          <div className="flex justify-start">
            <div className="bg-white text-gray-800 shadow px-4 py-2 rounded-lg">
              <div className="flex space-x-2">
                <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"></div>
                <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce delay-100"></div>
                <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce delay-200"></div>
              </div>
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Input */}
      <div className="bg-white border-t p-4">
        <div className="flex gap-2">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSend()}
            placeholder="Type your message..."
            className="flex-1 border rounded px-4 py-2"
            disabled={sending}
          />
          <button
            onClick={handleSend}
            disabled={sending || !input.trim()}
            className={`px-6 py-2 rounded font-medium ${
              sending || !input.trim()
                ? 'bg-gray-400 cursor-not-allowed'
                : 'bg-blue-600 hover:bg-blue-700 text-white'
            }`}
          >
            Send
          </button>
        </div>
      </div>
    </div>
  );
};

export default WorkflowChatExecutor;
```

---

#### 3.3 Update React Router

**File**: `frontend/src/App.jsx` (or wherever routes are defined)

**Add new routes**:

```jsx
import WorkflowFormExecutor from './pages/WorkflowFormExecutor';
import WorkflowChatExecutor from './pages/WorkflowChatExecutor';

// In your Routes component:
<Route path="/execute/:workflowId" element={<WorkflowFormExecutor />} />
<Route path="/chat/:workflowId" element={<WorkflowChatExecutor />} />
```

---

### Phase 4: Integration & Polish (1 hour)

#### 4.1 Workflow Gallery

**File**: `frontend/src/pages/WorkflowGallery.jsx` (NEW FILE)

```jsx
import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const WorkflowGallery = () => {
  const [workflows, setWorkflows] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Fetch public workflows only
    fetch(`${API_BASE_URL}/api/workflows`)
      .then(res => res.json())
      .then(data => {
        const publicWorkflows = data.filter(w => w.isPublic);
        setWorkflows(publicWorkflows);
        setLoading(false);
      })
      .catch(err => {
        console.error('Failed to load workflows:', err);
        setLoading(false);
      });
  }, []);

  const getInterfaceIcon = (type) => {
    switch (type) {
      case 'CHAT': return '💬';
      case 'FORM': return '📝';
      case 'API': return '🔌';
      default: return '⚙️';
    }
  };

  const getExecutionUrl = (workflow) => {
    return workflow.interfaceType === 'CHAT'
      ? `/chat/${workflow.id}`
      : `/execute/${workflow.id}`;
  };

  if (loading) {
    return <div className="p-8">Loading workflows...</div>;
  }

  return (
    <div className="min-h-screen bg-gray-100 p-8">
      <div className="max-w-6xl mx-auto">
        <h1 className="text-3xl font-bold mb-2">Workflow Gallery</h1>
        <p className="text-gray-600 mb-8">
          Explore and use publicly available workflows
        </p>

        {workflows.length === 0 ? (
          <div className="bg-white rounded-lg p-8 text-center">
            <p className="text-gray-600">No public workflows available yet.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {workflows.map(workflow => (
              <div key={workflow.id} className="bg-white rounded-lg shadow hover:shadow-lg transition p-6">
                <div className="flex items-start justify-between mb-3">
                  <h3 className="text-xl font-bold">{workflow.name}</h3>
                  <span className="text-3xl">{getInterfaceIcon(workflow.interfaceType)}</span>
                </div>

                <p className="text-gray-600 mb-4 line-clamp-3">
                  {workflow.description || 'No description available'}
                </p>

                <div className="flex items-center justify-between text-sm text-gray-500 mb-4">
                  <span className="bg-gray-100 px-2 py-1 rounded">
                    {workflow.interfaceType}
                  </span>
                  <span>{workflow.stepCount || 0} steps</span>
                </div>

                <Link
                  to={getExecutionUrl(workflow)}
                  className="block w-full text-center bg-blue-600 text-white py-2 rounded hover:bg-blue-700"
                >
                  Open Workflow
                </Link>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default WorkflowGallery;
```

---

## Example Use Cases

### Use Case 1: Product Description Generator

**Workflow Configuration**:
```json
{
  "name": "Product Description Generator",
  "description": "Generate enhanced product descriptions from Shopify data",
  "interfaceType": "FORM",
  "isPublic": true,
  "inputSchemaJson": {
    "type": "object",
    "properties": {
      "product_code": {
        "type": "string",
        "title": "Product Code or Name",
        "description": "Enter NSI product code or search term",
        "placeholder": "NSI-SIDECUTTERS"
      },
      "format": {
        "type": "string",
        "title": "Output Format",
        "enum": ["HTML", "Markdown", "Plain Text"],
        "default": "HTML"
      }
    },
    "required": ["product_code"]
  }
}
```

**Workflow Steps**:
1. **Product Fetcher Agent**: Uses `mcp_call` to search Shopify via `/api/products/search?query=${trigger.product_code}`
2. **Category Detector Agent**: Analyzes product data to classify category
3. **Description Writer Agent**: Conditional execution based on category
4. **HTML Formatter Agent**: Converts to requested format

**User Experience**:
- User visits: `https://yourapp.com/execute/1`
- Sees form with "Product Code" input and "Output Format" dropdown
- Enters "NSI-SIDECUTTERS" and selects "HTML"
- Clicks "Execute Workflow"
- Sees enhanced HTML description with images and formatting

---

### Use Case 2: Customer Support Bot

**Workflow Configuration**:
```json
{
  "name": "Customer Support Bot",
  "description": "AI-powered customer support for common questions",
  "interfaceType": "CHAT",
  "isPublic": true,
  "inputSchemaJson": {
    "type": "object",
    "properties": {
      "message": {
        "type": "string",
        "title": "Your Message"
      }
    },
    "required": ["message"]
  }
}
```

**Workflow Steps**:
1. **Query Classifier Agent**: Analyzes user message to detect intent (order status, product info, returns, etc.)
2. **Route to Specialist Agents**: Conditional execution based on classification
3. **Response Formatter Agent**: Formats response for chat interface

**User Experience**:
- User visits: `https://yourapp.com/chat/2`
- Sees chat interface with welcome message
- Types: "Where is my order #12345?"
- AI agents process query and respond with order status
- Continues conversation naturally

---

### Use Case 3: Order Processing API

**Workflow Configuration**:
```json
{
  "name": "Order Processing Workflow",
  "description": "Process orders with validation and fulfillment",
  "interfaceType": "API",
  "isPublic": false,
  "inputSchemaJson": {
    "type": "object",
    "properties": {
      "order_id": {
        "type": "string",
        "title": "Order ID"
      },
      "customer_email": {
        "type": "string",
        "title": "Customer Email"
      },
      "items": {
        "type": "array",
        "title": "Order Items"
      }
    },
    "required": ["order_id", "customer_email", "items"]
  }
}
```

**Usage**:
```bash
curl -X POST https://yourapp.com/api/workflows/public/3/execute \
  -H "Content-Type: application/json" \
  -d '{
    "order_id": "ORD-12345",
    "customer_email": "customer@example.com",
    "items": [{"sku": "NSI-123", "qty": 2}]
  }'
```

---

## Testing Strategy

### Backend Testing

**Phase 1 Tests**:
```bash
# 1. Test migration
mvn flyway:migrate

# 2. Test workflow creation with new fields
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Workflow",
    "description": "Test Description",
    "triggerType": "MANUAL",
    "executionMode": "SYNC",
    "inputSchemaJson": {
      "type": "object",
      "properties": {
        "test_field": {"type": "string", "title": "Test"}
      }
    },
    "interfaceType": "FORM",
    "isPublic": true
  }'

# 3. Test public execution
curl -X POST http://localhost:8080/api/workflows/public/1/execute \
  -H "Content-Type: application/json" \
  -d '{"test_field": "hello"}'

# 4. Test backward compatibility
curl -X GET http://localhost:8080/api/workflows
```

### Frontend Testing

**Phase 2 Tests**:
1. Create workflow in WorkflowEditor
2. Add input fields using InputSchemaBuilder
3. Save and verify schema in database
4. Toggle interface type and verify updates
5. Copy shareable link and open in new tab

**Phase 3 Tests**:
1. Visit `/execute/1` with FORM workflow
2. Fill form and submit
3. Verify execution and results display
4. Visit `/chat/2` with CHAT workflow
5. Send messages and verify responses

**Phase 4 Tests**:
1. Visit `/workflows/gallery`
2. Verify only public workflows shown
3. Click workflow card and verify navigation

### Integration Testing

1. **End-to-End Flow**:
   - Create agent in UI
   - Create workflow with input schema
   - Add workflow steps referencing `${trigger.*}` variables
   - Mark as public
   - Execute via public form
   - Verify results

2. **Variable Substitution**:
   - Create workflow with schema: `{"name": {"type": "string"}}`
   - Add step with input mapping: `{"name": "${trigger.name}"}`
   - Execute with `{"name": "John"}`
   - Verify agent receives correct input

3. **Error Handling**:
   - Try to execute non-public workflow via public endpoint
   - Submit form with invalid data
   - Test network failures

---

## Implementation Checklist

### Phase 1: Backend ✅
- [ ] Create V004__add_workflow_input_schema.sql migration
- [ ] Add 3 fields to Workflow.java entity
- [ ] Update CreateWorkflowRequest.java DTO
- [ ] Update WorkflowResponse.java DTO
- [ ] Update WorkflowController create/update methods
- [ ] Add public execution endpoint to WorkflowController
- [ ] Test migration and backward compatibility
- [ ] Test API endpoints with Postman/curl

### Phase 2: Frontend Input Builder ✅
- [ ] Create InputSchemaBuilder.jsx component
- [ ] Update WorkflowEditor.jsx with Inputs section
- [ ] Add interface type selector
- [ ] Add isPublic checkbox
- [ ] Display shareable link when public
- [ ] Test schema builder with various field types
- [ ] Verify auto-save on changes

### Phase 3: Execution Interfaces ✅
- [ ] Create WorkflowFormExecutor.jsx
- [ ] Implement dynamic form generation from schema
- [ ] Create WorkflowChatExecutor.jsx
- [ ] Implement chat UI and message handling
- [ ] Add routes to App.jsx
- [ ] Test form execution with various schemas
- [ ] Test chat interface with conversational workflows

### Phase 4: Polish ✅
- [ ] Create WorkflowGallery.jsx
- [ ] Add gallery route to App.jsx
- [ ] Add navigation links to gallery
- [ ] Add "Open Interface" button to WorkflowEditor
- [ ] Test full user journey
- [ ] Fix any UX issues

---

## Next Steps After Implementation

1. **Add Workflow Templates**
   - Pre-built workflow templates users can clone
   - Example: "Product Q&A Bot", "Order Status Checker"

2. **Enhanced Schema Builder**
   - Support for nested objects
   - Array/list inputs
   - File upload fields
   - Date/time pickers

3. **Workflow Analytics**
   - Track execution count
   - Monitor success/failure rates
   - Measure execution time
   - User engagement metrics

4. **Embed Support**
   - Generate iframe embed code
   - Customizable theme/styling
   - White-label options

5. **Advanced Features**
   - Workflow versioning
   - A/B testing different agents
   - Rate limiting for public workflows
   - User authentication for private workflows

---

## Technical Notes

### Context Variable Syntax

Variables available in workflow steps:

```javascript
${trigger.field_name}          // From input schema
${step_name.output.path}       // From previous step outputs
${step_name.nested.value}      // Deep property access
```

### JSON Schema Reference

Supported field types in `inputSchemaJson`:

```json
{
  "type": "object",
  "properties": {
    "text_field": {
      "type": "string",
      "title": "Display Title",
      "description": "Help text",
      "placeholder": "Hint text",
      "default": "Default value"
    },
    "number_field": {
      "type": "number",
      "minimum": 0,
      "maximum": 100
    },
    "dropdown": {
      "type": "string",
      "enum": ["Option1", "Option2", "Option3"]
    },
    "checkbox": {
      "type": "boolean",
      "default": false
    }
  },
  "required": ["text_field"]
}
```

### Security Considerations

1. **Public Workflow Validation**:
   - Verify `isPublic = true` before allowing execution
   - Implement rate limiting per IP
   - Sanitize user inputs before passing to agents

2. **Input Validation**:
   - Validate against JSON Schema on backend
   - Check required fields
   - Prevent injection attacks

3. **Error Messages**:
   - Don't expose internal errors to public users
   - Log detailed errors server-side only

---

## Conclusion

This implementation plan transforms the workflow system from internal automation into a powerful, flexible platform where workflows become shareable applications. Users can:

- ✅ Build workflows visually without coding
- ✅ Define input schemas without writing JSON
- ✅ Share workflows via public links
- ✅ Choose appropriate interface types (form/chat/API)
- ✅ Auto-generate UIs from schemas

**Total Implementation Time**: ~5-6 hours
**Impact**: High - enables non-technical users to create and share AI-powered tools
**Risk**: Low - backward compatible, modular architecture

Ready to begin implementation! 🚀
