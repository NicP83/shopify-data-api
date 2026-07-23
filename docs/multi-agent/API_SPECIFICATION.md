# Multi-Agent System - API Specification

**Base URL**: `/api`
**Content-Type**: `application/json`
**Source of truth**: `src/main/java/com/shopify/api/controller/agent/`

---

## Table of Contents

1. [Overview](#overview)
2. [Agents API](#agents-api) (`AgentController`)
3. [Tools API](#tools-api) (`ToolController`)
4. [Workflows API](#workflows-api) (`WorkflowController`)
5. [Approvals API](#approvals-api) (`ApprovalController`)
6. [Schedules API](#schedules-api) (`ScheduleController`)
7. [Error Handling](#error-handling)
8. [Known Gaps](#known-gaps)

---

## Overview

Five REST controllers expose the multi-agent system:

| Controller | Base Path | Purpose |
|------------|-----------|---------|
| `AgentController` | `/api/agents` | Agent CRUD, tool assignment, direct execution |
| `ToolController` | `/api/tools` | Tool registry CRUD, handler validation |
| `WorkflowController` | `/api/workflows` | Workflow + step CRUD, workflow execution |
| `ApprovalController` | `/api/approvals` | Human-in-the-loop approval requests |
| `ScheduleController` | `/api/schedules` | Cron-based workflow scheduling |

Execution endpoints (`/execute`) are reactive (`Mono`) and return asynchronously via Spring WebFlux. All other endpoints are synchronous.

---

## Agents API

### Request Body: `CreateAgentRequest`

Used by both create and update.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `name` | string | Yes | |
| `description` | string | No | |
| `modelProvider` | string | Yes | e.g. `CLAUDE` |
| `modelName` | string | Yes | e.g. `claude-sonnet-4-20250514` |
| `systemPrompt` | string | Yes | |
| `temperature` | decimal | No | |
| `maxTokens` | integer | No | |
| `configJson` | object | No | Free-form JSON config |
| `isActive` | boolean | No | Defaults to `true` |

### Response Body: `AgentResponse`

```json
{
  "id": 1,
  "name": "Product Expert",
  "description": "...",
  "modelProvider": "CLAUDE",
  "modelName": "claude-sonnet-4-20250514",
  "systemPrompt": "...",
  "temperature": 0.7,
  "maxTokens": 4096,
  "configJson": {},
  "isActive": true,
  "agentTools": 2,
  "tools": [{ "id": 1, "name": "product_search", "description": "..." }],
  "createdAt": "2025-10-14T10:00:00",
  "updatedAt": "2025-10-14T10:00:00"
}
```

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/agents` | Create agent. Body: `CreateAgentRequest`. Returns `201` with `AgentResponse`. |
| GET | `/api/agents` | List agents. Query params: `activeOnly` (boolean), `provider` (string). If `provider` is set it takes precedence over `activeOnly`. |
| GET | `/api/agents/{id}` | Get agent by ID (with tools loaded). `404` if not found. |
| GET | `/api/agents/by-name/{name}` | Get agent by name. `404` if not found. |
| PUT | `/api/agents/{id}` | Update agent. Body: `CreateAgentRequest`. |
| DELETE | `/api/agents/{id}` | Delete agent. Returns `204`. |
| POST | `/api/agents/{id}/activate` | Set `isActive = true`. Returns `200`, empty body. |
| POST | `/api/agents/{id}/deactivate` | Set `isActive = false`. Returns `200`, empty body. |
| POST | `/api/agents/{agentId}/tools/{toolId}` | Assign a tool to an agent. |
| DELETE | `/api/agents/{agentId}/tools/{toolId}` | Remove a tool from an agent. Returns `204`. |
| GET | `/api/agents/{agentId}/tools` | List tools assigned to an agent. |
| GET | `/api/agents/{agentId}/tool-count` | Count of tools assigned to an agent (integer). |
| POST | `/api/agents/{id}/execute` | Execute the agent directly. Body: arbitrary JSON input. Reactive. On failure returns `500` with `{ "error": "message" }`. |
| GET | `/api/agents/{agentId}/executions` | Execution history for an agent (list of `AgentExecutionResponse`). |

### `AgentExecutionResponse`

| Field | Type |
|-------|------|
| `id` | long |
| `agentId` | long |
| `agentName` | string |
| `status` | string |
| `inputDataJson` | object |
| `outputDataJson` | object |
| `tokensUsed` | integer |
| `executionTimeMs` | integer |
| `errorMessage` | string |
| `startedAt` / `completedAt` / `createdAt` | timestamp |

---

## Tools API

### Request Body: `CreateToolRequest`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `name` | string | Yes | Max 100 chars |
| `type` | string | Yes | Tool category |
| `description` | string | No | |
| `inputSchemaJson` | object | No | JSON Schema for tool parameters |
| `handlerClass` | string | Yes | Fully-qualified handler class, max 255 chars |
| `isActive` | boolean | No | Defaults to `true` |

### Response Body: `ToolResponse`

`id`, `name`, `type`, `description`, `inputSchemaJson`, `handlerClass`, `isActive`, `createdAt`.

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/tools` | Register a tool. Returns `201` with `ToolResponse`. |
| GET | `/api/tools` | List tools. Query params: `activeOnly` (boolean), `type` (string). Combinable. |
| GET | `/api/tools/{id}` | Get tool by ID. `404` if not found. |
| GET | `/api/tools/by-name/{name}` | Get tool by name. `404` if not found. |
| PUT | `/api/tools/{id}` | Update tool. Body: `CreateToolRequest`. |
| DELETE | `/api/tools/{id}` | Delete tool. Returns `204`. |
| POST | `/api/tools/{id}/activate` | Activate tool. |
| POST | `/api/tools/{id}/deactivate` | Deactivate tool. |
| GET | `/api/tools/validate-handler?handlerClass=...` | Returns `true`/`false` — whether the handler class is valid/loadable. |

---

## Workflows API

### Request Body: `CreateWorkflowRequest`

Used by both create and update.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `name` | string | Yes | |
| `description` | string | No | |
| `triggerType` | string | Yes | `MANUAL`, `SCHEDULED`, `EVENT` |
| `triggerConfigJson` | object | No | Cron / event filter config |
| `executionMode` | string | No | `SYNC` (default) or `ASYNC` |
| `isActive` | boolean | No | Defaults to `true` |
| `inputSchemaJson` | object | No | JSON Schema describing workflow inputs (drives the form/chat UI) |
| `interfaceType` | string | No | `FORM` (default), `CHAT`, or `API` |
| `isPublic` | boolean | No | Defaults to `false`. Public workflows are executable without auth via `/public/{id}/execute` |

### Response Body: `WorkflowResponse`

`id`, `name`, `description`, `triggerType`, `triggerConfigJson`, `executionMode`, `isActive`, `createdAt`, `updatedAt`, `stepCount`, `inputSchemaJson`, `interfaceType`, `isPublic`.

### Workflow Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/workflows` | Create workflow. Returns `201` with `WorkflowResponse`. |
| GET | `/api/workflows` | List workflows. Query params: `activeOnly` (boolean), `triggerType` (string). Combinable. |
| GET | `/api/workflows/{id}` | Get workflow by ID. `404` if not found. |
| PUT | `/api/workflows/{id}` | Update workflow. |
| DELETE | `/api/workflows/{id}` | Delete workflow. Returns `204`. |
| POST | `/api/workflows/{id}/activate` | Activate workflow. |
| POST | `/api/workflows/{id}/deactivate` | Deactivate workflow. |
| POST | `/api/workflows/{id}/execute` | Execute workflow (see below). |
| POST | `/api/workflows/public/{id}/execute` | Execute a **public** workflow (see below). |

### Workflow Step Endpoints

#### Request Body: `CreateWorkflowStepRequest`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `stepOrder` | integer | Yes | Execution order (1, 2, 3, ...) |
| `stepType` | string | Yes | `AGENT_EXECUTION`, `CONDITION`, `APPROVAL`, `PARALLEL` |
| `agentId` | long | No | Required for `AGENT_EXECUTION` steps |
| `name` | string | Yes | |
| `inputMappingJson` | object | No | Maps context variables to agent input, supports `${var.path}` substitution |
| `outputVariable` | string | No | Context key where the step result is stored |
| `conditionExpression` | string | No | e.g. `${step1.status}==success` |
| `dependsOn` | integer[] | No | Step-order dependencies |
| `approvalConfigJson` | object | No | Stored, but see note in [Known Gaps](#known-gaps) |
| `retryConfigJson` | object | No | `{ "maxRetries": 3, "initialDelayMs": 1000, "maxDelayMs": 30000, "multiplier": 2.0 }` |
| `timeoutSeconds` | integer | No | Defaults to `300` |

#### Response Body: `WorkflowStepResponse`

All request fields plus `id`, `workflowId`, `agentName`, `createdAt`.

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/workflows/{workflowId}/steps` | Add a step. Returns `201` with `WorkflowStepResponse`. |
| GET | `/api/workflows/{workflowId}/steps` | List steps for a workflow. |
| PUT | `/api/workflows/{workflowId}/steps/{stepId}` | Update a step. |
| DELETE | `/api/workflows/{workflowId}/steps/{stepId}` | Delete a step. Returns `204`. |
| POST | `/api/workflows/{workflowId}/steps/reorder` | Reorder steps. Body: array of step IDs in the desired order, e.g. `[3, 1, 2]`. |

### Executing a Workflow

`POST /api/workflows/{id}/execute`

- Body (optional): arbitrary JSON trigger data. Defaults to `{}` if omitted.
- The trigger data becomes `context.trigger` inside the workflow.
- Reactive endpoint; responds when the workflow completes (or pauses for approval).

Response:

```json
{
  "success": true,
  "context": {
    "trigger": { "productId": "123" },
    "analysis": { "..." : "..." }
  }
}
```

On failure (`500` or `success: false`):

```json
{
  "success": false,
  "error": "Workflow is not active: My Workflow"
}
```

### Executing a Public Workflow

`POST /api/workflows/public/{id}/execute`

Same request/response as above with additional checks:

- `404` if the workflow does not exist.
- `403 { "success": false, "error": "Workflow is not public" }` if `isPublic` is not `true`.
- Intended for unauthenticated use (public forms / chat interfaces launched from the Workflow Gallery).

---

## Approvals API

Approval endpoints return the `ApprovalRequest` **entity** directly (not a DTO):

| Field | Type | Notes |
|-------|------|-------|
| `id` | long | |
| `workflowExecution` | object | Parent execution |
| `workflowStep` | object | Step that requested approval |
| `status` | string | `PENDING`, `APPROVED`, `REJECTED`, `TIMEOUT` |
| `requiredRole` | string | Optional role restriction |
| `approvedBy` | string | |
| `approvedAt` | timestamp | |
| `comments` | string | |
| `timeoutAt` | timestamp | Auto-reject time |
| `requestedAt` | timestamp | |

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/approvals/pending` | List pending approvals. Query param: `role` (optional, filters by required role). |
| GET | `/api/approvals/execution/{executionId}` | List approvals for a workflow execution. |
| GET | `/api/approvals/count` | Pending approval count: `{ "count": 3 }`. |
| POST | `/api/approvals/{id}/approve` | Approve. Body: `{ "approvedBy": "user@example.com", "comments": "Looks good" }`. Reactive; resumes the paused workflow. `400` on error. |
| POST | `/api/approvals/{id}/reject` | Reject. Body: `{ "rejectedBy": "user@example.com", "reason": "Does not meet requirements" }`. Reactive. `400` on error. |
| POST | `/api/approvals` | Create an approval request (normally called by the orchestrator, not clients). Body: `{ "executionId": 123, "stepId": 456, "requiredRole": "manager", "timeoutMinutes": 60 }`. |

---

## Schedules API

Schedule endpoints return the `WorkflowSchedule` **entity** directly:

| Field | Type | Notes |
|-------|------|-------|
| `id` | long | |
| `workflow` | object | Parent workflow |
| `cronExpression` | string | Spring 6-field cron, e.g. `0 0 8 * * *` |
| `enabled` | boolean | |
| `lastRunAt` / `nextRunAt` | timestamp | |
| `triggerDataJson` | object | Trigger data passed to each scheduled run |
| `createdAt` / `updatedAt` | timestamp | |

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/schedules` | Create schedule. Body: `{ "workflowId": 1, "cronExpression": "0 0 * * * *", "triggerData": { ... } }`. |
| GET | `/api/schedules` | List schedules. Query param `active` (default `true`). Note: currently returns active schedules regardless of the flag (marked `TODO` in code). |
| GET | `/api/schedules/workflow/{workflowId}` | List schedules for a workflow. |
| DELETE | `/api/schedules/{id}` | Cancel (disable) a schedule. Returns `{ "message": "...", "scheduleId": "..." }`. |
| PUT | `/api/schedules/{id}/activate` | Reactivate a cancelled schedule. Returns `200` with empty body. |
| PUT | `/api/schedules/{id}/cron` | Update cron. Body: `{ "cronExpression": "0 0 12 * * *" }`. |
| PUT | `/api/schedules/{id}/trigger-data` | Replace trigger data. Body: arbitrary JSON. |

---

## Error Handling

- `AgentController`, `ToolController`, and `WorkflowController` each have an `@ExceptionHandler(IllegalArgumentException.class)` that returns `400` with the exception message as a plain string body.
- Validation failures on `@Valid` request bodies return Spring's default `400` response.
- Execution endpoints wrap errors into `{ "success": false, "error": "..." }` with status `500`.

---

## Known Gaps

Real discrepancies between the frontend client (`frontend/src/services/api.js`) and the backend:

1. **No executions controller exists.** The frontend calls `GET /api/executions`, `GET /api/executions/{id}` and `GET /api/workflows/{workflowId}/executions`, but no backend controller maps these paths. The only execution-history endpoint that exists is `GET /api/agents/{agentId}/executions`.
2. **Agent tool assignment via editor payload is ignored.** `AgentEditor.jsx` sends `toolIds` inside the create/update agent body, but `CreateAgentRequest` has no `toolIds` field. The working endpoints for tool assignment are `POST`/`DELETE /api/agents/{agentId}/tools/{toolId}`, which `api.js` does not currently wrap.
3. **`GET /api/schedules?active=false` still returns only active schedules** (unimplemented branch in `ScheduleController`).
4. **`PARALLEL` step type is a placeholder** — see `WorkflowOrchestratorService`; it returns `{ "parallel": "not_fully_implemented" }`.

---

**Last Updated**: 2026-07-23
