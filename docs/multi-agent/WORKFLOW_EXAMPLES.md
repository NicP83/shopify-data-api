# Multi-Agent System - Workflow Examples

Realistic workflow configurations using the **actual** schema and step types supported by `WorkflowOrchestratorService`.

**Source of truth**:
- `src/main/java/com/shopify/api/service/agent/WorkflowOrchestratorService.java`
- `docs/multi-agent/DATABASE_SCHEMA.md` (tables 4, 5, 10)
- `docs/multi-agent/API_SPECIFICATION.md` (request shapes)

---

## Table of Contents

1. [How Execution Actually Works](#how-execution-actually-works)
2. [Example 1: Product Content Enrichment (with Approval)](#example-1-product-content-enrichment-with-approval)
3. [Example 2: Scheduled Daily Sales Report](#example-2-scheduled-daily-sales-report)
4. [Example 3: Conditional Restock Advisor](#example-3-conditional-restock-advisor)
5. [Best Practices & Gotchas](#best-practices--gotchas)

---

## How Execution Actually Works

### Supported step types

The orchestrator's `executeStep()` switch supports exactly four values of `stepType`:

| Step Type | Behavior |
|-----------|----------|
| `AGENT_EXECUTION` | Runs the assigned agent with input built from `inputMappingJson`. Requires `agentId`. Honors `timeoutSeconds` (default 300). |
| `APPROVAL` | Creates an approval request, sets the execution status to `PAUSED`, and returns `{ "status": "PENDING", "message": "Waiting for approval" }`. |
| `CONDITION` | No-op marker step; the actual gating is done by `conditionExpression` on whichever step you want to skip. Returns `{ "skipped": true }`. |
| `PARALLEL` | **Placeholder — not fully implemented.** Returns `{ "parallel": "not_fully_implemented" }`. Avoid in real workflows. |

Any other value causes the workflow to fail with `Unknown step type`.

### Context

- Steps run **sequentially** by `stepOrder`.
- The trigger payload (`POST /api/workflows/{id}/execute` body) is stored at `context.trigger`.
- Each step's result is stored in the context under its `outputVariable` (if set).

### Input mapping (`inputMappingJson`)

- If `null`, the **entire context** is passed to the agent.
- Otherwise the mapping object is passed after variable substitution: any string value that is exactly `${variableName}` is replaced by that **top-level** context value.
- ⚠️ Substitution in input mappings resolves **top-level keys only** (`${trigger}`, `${analysis}`) — dotted paths like `${trigger.productId}` are *not* resolved in input mappings (the code does a flat `context.get(varPath)` there). Dotted paths **do** work in condition expressions, which use `resolveNestedPath()`.

### Condition expressions (`conditionExpression`)

A step **runs** when its condition evaluates truthy and is **skipped** otherwise. Supported forms:

| Form | Example |
|------|---------|
| Equality | `${analysis.needsRestock}==true` |
| Inequality | `${trigger.mode}!=dry-run` |
| Truthiness | `${analysis}` (non-null boolean true or non-empty string) |
| Negation | `!${analysis.skipReport}` |

### Retry (`retryConfigJson`)

```json
{ "maxRetries": 3, "initialDelayMs": 1000, "maxDelayMs": 30000, "multiplier": 2.0 }
```

Exponential backoff; if set, a failed step is retried before the workflow is marked `FAILED`.

### Approval configuration

⚠️ The orchestrator reads `requiredRole` and `timeoutMinutes` for `APPROVAL` steps from **`inputMappingJson`**, not from `approvalConfigJson` (which is stored but currently unused). Put the approval settings in the step's input mapping, as shown below.

---

## Example 1: Product Content Enrichment (with Approval)

A form-launched workflow: a copywriter agent drafts an improved product description, a human approves it, then a publisher agent applies it.

### 1. Create the workflow

`POST /api/workflows`

```json
{
  "name": "Product Content Enrichment",
  "description": "Draft improved product copy, get human sign-off, then publish",
  "triggerType": "MANUAL",
  "executionMode": "SYNC",
  "isActive": true,
  "interfaceType": "FORM",
  "isPublic": true,
  "inputSchemaJson": {
    "type": "object",
    "properties": {
      "productId": {
        "type": "string",
        "title": "Product ID",
        "description": "Shopify product ID to enrich"
      },
      "tone": {
        "type": "string",
        "title": "Tone of voice",
        "enum": ["professional", "playful", "technical"],
        "default": "professional"
      }
    },
    "required": ["productId"]
  }
}
```

Because `isPublic: true` and `interfaceType: "FORM"`, this workflow appears in the Workflow Gallery and can be run via `POST /api/workflows/public/{id}/execute`.

### 2. Add the steps

`POST /api/workflows/{workflowId}/steps` (one call per step)

**Step 1 — draft the copy** (agent, e.g. a "Product Copywriter" agent with product-search tools):

```json
{
  "stepOrder": 1,
  "stepType": "AGENT_EXECUTION",
  "agentId": 1,
  "name": "Draft product description",
  "inputMappingJson": {
    "task": "Write an improved SEO-friendly product description",
    "input": "${trigger}"
  },
  "outputVariable": "draft",
  "timeoutSeconds": 300,
  "retryConfigJson": { "maxRetries": 2, "initialDelayMs": 2000 }
}
```

**Step 2 — human approval** (pauses the workflow):

```json
{
  "stepOrder": 2,
  "stepType": "APPROVAL",
  "name": "Review drafted copy",
  "inputMappingJson": {
    "requiredRole": "content-manager",
    "timeoutMinutes": 1440
  },
  "outputVariable": "approval"
}
```

When this step runs, the execution status becomes `PAUSED` and a row appears in the Approval Queue (`/approvals`). Approving via `POST /api/approvals/{id}/approve` resumes the execution.

**Step 3 — publish**:

```json
{
  "stepOrder": 3,
  "stepType": "AGENT_EXECUTION",
  "agentId": 2,
  "name": "Publish approved description",
  "inputMappingJson": {
    "task": "Update the Shopify product with the approved description",
    "draft": "${draft}",
    "productInput": "${trigger}"
  },
  "outputVariable": "publishResult",
  "timeoutSeconds": 300
}
```

### 3. Run it

`POST /api/workflows/public/{id}/execute`

```json
{ "productId": "gid://shopify/Product/1234567890", "tone": "playful" }
```

Final context on success:

```json
{
  "trigger": { "productId": "gid://shopify/Product/1234567890", "tone": "playful" },
  "draft": { "...": "copywriter output" },
  "approval": { "status": "PENDING", "message": "Waiting for approval" },
  "publishResult": { "...": "publisher output" }
}
```

---

## Example 2: Scheduled Daily Sales Report

A single-agent workflow triggered every morning by the scheduler.

### 1. Create the workflow

`POST /api/workflows`

```json
{
  "name": "Daily Sales Report",
  "description": "Summarize yesterday's sales every morning at 8am",
  "triggerType": "SCHEDULED",
  "executionMode": "ASYNC",
  "isActive": true,
  "interfaceType": "API",
  "isPublic": false
}
```

### 2. Add the step

`POST /api/workflows/{workflowId}/steps`

```json
{
  "stepOrder": 1,
  "stepType": "AGENT_EXECUTION",
  "agentId": 3,
  "name": "Generate sales summary",
  "inputMappingJson": {
    "task": "Produce a daily sales report with top products and totals",
    "parameters": "${trigger}"
  },
  "outputVariable": "report",
  "timeoutSeconds": 600,
  "retryConfigJson": { "maxRetries": 3, "initialDelayMs": 5000, "maxDelayMs": 60000, "multiplier": 2.0 }
}
```

### 3. Schedule it

`POST /api/schedules`

```json
{
  "workflowId": 42,
  "cronExpression": "0 0 8 * * *",
  "triggerData": {
    "period": "1d",
    "channels": ["shopify", "instore"]
  }
}
```

The scheduler runs the workflow every day at 08:00 with `triggerData` as the trigger payload (available in steps as `${trigger}`). Manage the schedule with `PUT /api/schedules/{id}/cron`, `PUT /api/schedules/{id}/trigger-data`, and `DELETE /api/schedules/{id}`.

---

## Example 3: Conditional Restock Advisor

Two agents chained with a conditional third step that only runs when the analysis says a restock is needed.

### 1. Create the workflow

`POST /api/workflows`

```json
{
  "name": "Restock Advisor",
  "description": "Analyze stock for a SKU and draft a purchase order only if needed",
  "triggerType": "MANUAL",
  "executionMode": "SYNC",
  "isActive": true,
  "interfaceType": "CHAT",
  "isPublic": true,
  "inputSchemaJson": {
    "type": "object",
    "properties": {
      "sku": { "type": "string", "title": "SKU", "description": "Product SKU to analyze" }
    },
    "required": ["sku"]
  }
}
```

With `interfaceType: "CHAT"`, the gallery launches this via the chat executor, which asks for the SKU conversationally.

### 2. Add the steps

**Step 1 — analyze stock**. The agent is prompted to return JSON including a `needsRestock` field:

```json
{
  "stepOrder": 1,
  "stepType": "AGENT_EXECUTION",
  "agentId": 4,
  "name": "Analyze stock levels",
  "inputMappingJson": {
    "task": "Analyze current stock and sales velocity. Respond with JSON including a boolean field needsRestock.",
    "input": "${trigger}"
  },
  "outputVariable": "analysis",
  "timeoutSeconds": 300
}
```

**Step 2 — draft purchase order, only when needed**:

```json
{
  "stepOrder": 2,
  "stepType": "AGENT_EXECUTION",
  "agentId": 5,
  "name": "Draft purchase order",
  "conditionExpression": "${analysis.needsRestock}==true",
  "inputMappingJson": {
    "task": "Draft a purchase order recommendation",
    "analysis": "${analysis}"
  },
  "outputVariable": "purchaseOrder",
  "timeoutSeconds": 300
}
```

If `analysis.needsRestock` is not `true`, step 2 is skipped and the workflow completes with only the analysis in the context.

---

## Best Practices & Gotchas

1. **Use `AGENT_EXECUTION`, not `AGENT`.** DATABASE_SCHEMA.md lists the step type as `"AGENT"`, but the orchestrator only accepts `AGENT_EXECUTION`.
2. **Approval settings go in `inputMappingJson`** (`requiredRole`, `timeoutMinutes`), not `approvalConfigJson` — the latter is stored but never read by the orchestrator.
3. **Reference only top-level variables in input mappings** (`${trigger}`, `${analysis}`). Dotted paths (`${trigger.sku}`) work in `conditionExpression` but not in `inputMappingJson` substitution. If an agent needs one nested field, pass the whole parent object and let the agent's prompt pick the field out.
4. **Always set `outputVariable`** on agent steps whose results later steps (or conditions) need.
5. **Avoid `PARALLEL`** — it is a placeholder and returns `{ "parallel": "not_fully_implemented" }`.
6. **Set the workflow `isActive: true`** before executing; the orchestrator rejects inactive workflows with `Workflow is not active`.
7. **Approval resume is partial**: approving flips the execution from `PAUSED` back to `RUNNING` (`resumeWorkflowAfterApproval`), but the synchronous execute call has already returned by then — check execution status/context in the database or Approval Queue rather than expecting the original HTTP response to continue.
8. **Retry only what is safe to repeat.** `retryConfigJson` re-runs the whole step; avoid it on steps with non-idempotent side effects (e.g. publishing).

---

**Last Updated**: 2026-07-23
