# Multi-Agent System - Frontend Components

**Stack**: React 18 + React Router + Tailwind CSS + Axios
**Source**: `frontend/src/pages/`, `frontend/src/components/workflow/`, `frontend/src/services/api.js`

---

## Table of Contents

1. [Routes](#routes)
2. [Agent Pages](#agent-pages)
3. [Workflow Pages](#workflow-pages)
4. [Execution & Approval Pages](#execution--approval-pages)
5. [Workflow Components](#workflow-components)
6. [API Client](#api-client)
7. [Known Issues](#known-issues)

---

## Routes

Defined in `frontend/src/App.jsx`. Multi-agent routes only (the app also has dashboard, inventory, SEO, and analytics routes):

| Route | Component | Purpose |
|-------|-----------|---------|
| `/agents` | `AgentManagement` | Agent list + quick actions |
| `/agents/new` | `AgentEditor` | Create agent |
| `/agents/:id` | `AgentEditor` | Edit agent |
| `/workflows` | `WorkflowManagement` | Workflow list + quick actions |
| `/workflows/new` | `WorkflowEditor` | Create workflow |
| `/workflows/:id` | `WorkflowEditor` | Edit workflow + steps |
| `/workflow-gallery` | `WorkflowGallery` | End-user launcher for public workflows |
| `/workflow/execute/:id` | `WorkflowFormExecutor` | Form-style workflow runner |
| `/workflow/chat/:id` | `WorkflowChatExecutor` | Chat-style workflow runner |
| `/executions` | `WorkflowExecutions` | Execution history (all workflows) |
| `/executions/:workflowId` | `WorkflowExecutions` | Execution history (one workflow) |
| `/approvals` | `ApprovalQueue` | Pending approval queue |

> **Note**: `WorkflowEditorVisual.jsx` (a React Flow drag-and-drop visual builder) exists in `frontend/src/pages/` but is **not wired into any route** — the list-based `WorkflowEditor` is the one in use.

---

## Agent Pages

### `AgentManagement.jsx` (`/agents`)

Agent list with filtering and inline test execution.

- **State**: `agents`, `loading`, `error`, `filter` (`all` | `active` | `inactive`), `testingAgent`, `testInput`, `testResult`
- **API**: `api.getAgents(activeOnly)`, `api.activateAgent(id)`, `api.deactivateAgent(id)`, `api.deleteAgent(id)`, `api.executeAgent(id, inputData)`
- **Behavior**: filter tabs; cards navigate to `/agents/:id`; a test modal lets you run an agent with ad-hoc JSON input and see the result inline.

### `AgentEditor.jsx` (`/agents/new`, `/agents/:id`)

Create/edit form for a single agent.

- **State**: `agent` (name, description, modelProvider, modelName, systemPrompt, temperature, maxTokens, isActive, ...), `allTools`, `selectedToolIds`, `loading`, `saving`, `error`
- **API**: `api.getTools()`, `api.getAgent(id)`, `api.createAgent(agentData)`, `api.updateAgent(id, agentData)`
- **Behavior**: edit mode is detected via the `:id` route param. Tools are shown as toggleable checkboxes (`handleToolToggle`). On save it sends `{ ...agent, toolIds: selectedToolIds }` — but see [Known Issues](#known-issues): the backend ignores `toolIds`.

---

## Workflow Pages

### `WorkflowManagement.jsx` (`/workflows`)

Workflow list, mirror of `AgentManagement`.

- **State**: `workflows`, `loading`, `error`, `filter` (`all` | `active` | `inactive`)
- **API**: `api.getWorkflows(activeOnly)`, `api.activateWorkflow(id)`, `api.deactivateWorkflow(id)`, `api.deleteWorkflow(id)`, `api.executeWorkflow(id, {})`
- **Behavior**: "Run" executes a workflow immediately with empty trigger data; cards navigate to `/workflows/:id`.

### `WorkflowEditor.jsx` (`/workflows/new`, `/workflows/:id`)

The main (list-based) workflow builder — workflow metadata plus an ordered step list.

- **State**: `workflow` (name, description, triggerType, executionMode, isActive, inputSchemaJson, interfaceType, isPublic, ...), `steps`, `agents`, `showStepForm`, `editingStep`, `stepForm` (stepOrder, stepType, agentId, name, inputMappingJson, outputVariable, conditionExpression, timeoutSeconds, ...), `loading`, `saving`, `error`
- **API**: `api.getAgents()`, `api.getWorkflow(id)` + `api.getWorkflowSteps(id)` (loaded in parallel), `api.createWorkflow` / `api.updateWorkflow`, `api.createWorkflowStep` / `api.updateWorkflowStep` / `api.deleteWorkflowStep`
- **Behavior**: creating a workflow first navigates to `/workflows/:id` so steps can then be added. Embeds `InputSchemaBuilder` for the workflow's `inputSchemaJson`. Steps are added/edited via a modal step form; after each mutation the step list is re-fetched.

### `WorkflowEditorVisual.jsx` (NOT ROUTED)

React Flow drag-and-drop builder. Kept in the codebase but unreachable from the UI.

- Uses `reactflow` (`ReactFlow`, nodes/edges state) with a custom node type `workflowStep` → `WorkflowStepNode`.
- **State**: same workflow/steps/agents state as `WorkflowEditor`, plus `selectedNode` and `viewMode` (`visual` | `list`).
- **API**: identical set to `WorkflowEditor` (get/create/update workflow, CRUD steps).
- Selecting a node opens `StepConfigPanel` for editing.

### `WorkflowGallery.jsx` (`/workflow-gallery`)

End-user facing catalogue of runnable workflows.

- **State**: `workflows`, `loading`, `error`, `filter` (`all` | `form` | `chat`)
- **API**: `api.getWorkflows(true)` — fetches active workflows, then filters client-side to `isPublic === true`
- **Behavior**: cards show an icon per `interfaceType` (`FORM`, `CHAT`, `API`). Launch navigates to `/workflow/chat/:id` for `CHAT` workflows, otherwise `/workflow/execute/:id`.

### `WorkflowFormExecutor.jsx` (`/workflow/execute/:id`)

Renders a form generated from the workflow's `inputSchemaJson` (JSON Schema) and executes the workflow.

- **State**: `workflow`, `formData`, `loading`, `executing`, `result`, `error`
- **API**: `api.getWorkflow(id)`; on submit `api.executePublicWorkflow(id, formData)` if `workflow.isPublic`, else `api.executeWorkflow(id, formData)`
- **Behavior**: form fields are derived from `inputSchemaJson.properties` (type, title, description, enum, required). Result context is displayed after execution.

### `WorkflowChatExecutor.jsx` (`/workflow/chat/:id`)

Conversational executor: collects the same schema fields one question at a time.

- **State**: `workflow`, `messages`, `currentInput`, `collectedData`, `currentField`, `loading`, `executing`, `error`
- **API**: `api.getWorkflow(id)`; execution uses `api.executePublicWorkflow(id, collectedData)` / `api.executeWorkflow(id, collectedData)` depending on `isPublic`
- **Behavior**: greets the user, prompts each `inputSchemaJson` property in turn (with enum options and yes/no hints), stores answers in `collectedData`, then runs the workflow when all fields are collected (or when the user types `execute` for schema-less workflows).

---

## Execution & Approval Pages

### `WorkflowExecutions.jsx` (`/executions`, `/executions/:workflowId`)

Execution history browser with a detail panel.

- **State**: `executions`, `workflows`, `selectedExecution`, `loading`, `error`, `filter` (includes `workflowId`)
- **API**: `api.getWorkflows()` for the filter dropdown; `api.getWorkflowExecutions(workflowId)` or `api.getAllExecutions()`
- **Warning**: both execution-list calls target backend endpoints that **do not exist** (`GET /api/workflows/{id}/executions`, `GET /api/executions`) — see [Known Issues](#known-issues).

### `ApprovalQueue.jsx` (`/approvals`)

Human-in-the-loop approval inbox.

- **State**: `approvals`, `loading`, `error`, `selectedApproval`, `actionInProgress`, `approverName` (defaults to `admin@example.com`), `comments`
- **API**: `api.getPendingApprovals()`, `api.approveRequest(id, approverName, comments)`, `api.rejectRequest(id, approverName, reason)`
- **Behavior**: approving/rejecting resumes or fails the paused workflow execution on the backend.

---

## Workflow Components

Located in `frontend/src/components/workflow/`.

### `InputSchemaBuilder.jsx`

Visual JSON Schema builder used by `WorkflowEditor` for `workflow.inputSchemaJson`.

- **Props**: `schema` (existing JSON Schema), `onChange(schema)`
- **State**: `fields` (array of `{ name, type, title, description, required, default, options }` derived from `schema.properties` + `schema.required`), `showPreview`
- **Behavior**: add/remove/edit fields, supports select/enum options, and emits a valid JSON Schema (`{ type: "object", properties, required }`). Includes a live preview toggle.

### `StepConfigPanel.jsx`

Side panel for editing a single step (used by the unrouted `WorkflowEditorVisual`).

- **Props**: `step`, `agents`, `onUpdate(updatedStep)`, `onClose`, `onDelete`
- **State**: `formData` (name, stepType, agentId, inputMappingJson, outputVariable, conditionExpression, timeoutSeconds, retryConfigJson), plus raw-JSON text buffers `inputMappingText` and `retryConfigText`
- **Behavior**: JSON textareas are parsed on submit; invalid JSON triggers an alert. Renders an empty "Select a step to configure" state when no step is selected.

### `WorkflowStepNode.jsx`

Custom React Flow node (used by the unrouted `WorkflowEditorVisual`).

- **Props**: `data` (step data), `selected`
- **Behavior**: color-coded and icon-coded by step type — `AGENT_EXECUTION` (blue), `CONDITION` (yellow), `APPROVAL` (purple), `PARALLEL` (green). Has top (target) and bottom (source) connection handles.

---

## API Client

`frontend/src/services/api.js` — a single Axios instance (`baseURL: '/api'`) exporting a flat method map. Multi-agent methods:

| Group | Methods |
|-------|---------|
| Agents | `getAgents`, `getAgent`, `createAgent`, `updateAgent`, `deleteAgent`, `activateAgent`, `deactivateAgent`, `executeAgent` |
| Workflows | `getWorkflows`, `getWorkflow`, `createWorkflow`, `updateWorkflow`, `deleteWorkflow`, `activateWorkflow`, `deactivateWorkflow`, `executeWorkflow`, `executePublicWorkflow` |
| Steps | `getWorkflowSteps`, `createWorkflowStep`, `updateWorkflowStep`, `deleteWorkflowStep`, `reorderWorkflowSteps` |
| Executions | `getWorkflowExecutions`, `getAllExecutions`, `getExecutionDetails` (no matching backend endpoints — see below) |
| Approvals | `getPendingApprovals`, `getApprovalsByExecution`, `getApprovalCount`, `approveRequest`, `rejectRequest` |
| Tools | `getTools`, `getTool`, `createTool`, `updateTool`, `deleteTool`, `getAgentTools` (alias of `getTools`) |
| Schedules | `getSchedules`, `getSchedulesForWorkflow`, `createSchedule`, `cancelSchedule`, `activateSchedule`, `updateScheduleCron`, `updateScheduleTriggerData` |

---

## Known Issues

1. **Execution history endpoints are missing on the backend.** `getAllExecutions()`, `getExecutionDetails(id)` and `getWorkflowExecutions(workflowId)` call `/api/executions*` and `/api/workflows/{id}/executions`, which no controller serves. `WorkflowExecutions.jsx` therefore cannot load data until an executions controller is implemented.
2. **Agent tool selection does not persist.** `AgentEditor` sends `toolIds` in the agent payload, but `CreateAgentRequest` on the backend has no such field. Persisting selections requires calling `POST /api/agents/{agentId}/tools/{toolId}` (endpoint exists; `api.js` has no wrapper for it yet).
3. **`WorkflowEditorVisual.jsx` is orphaned** — fully implemented (React Flow, `StepConfigPanel`, `WorkflowStepNode`) but no route renders it.
4. **No UI for schedules or tool management.** The schedule and tool API methods exist in `api.js`, but there is no dedicated page for creating schedules or registering tools.

---

**Last Updated**: 2026-07-23
