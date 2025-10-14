# Multi-Agent System Architecture

**Project:** Shopify Data API - Multi-Agent Orchestration System
**Created:** 2025-10-14
**Version:** 1.0

---

## Table of Contents

1. [Overview](#overview)
2. [Core Principles](#core-principles)
3. [System Components](#system-components)
4. [Database Schema](#database-schema)
5. [Services Architecture](#services-architecture)
6. [Workflow Execution Flow](#workflow-execution-flow)
7. [Agent Communication](#agent-communication)
8. [Features](#features)
9. [Technology Stack](#technology-stack)

---

## Overview

The Multi-Agent System extends the existing Shopify Data API with a flexible, database-driven framework for creating, managing, and orchestrating AI agents. This system enables:

- **Dynamic Agent Creation**: Define agents entirely through database records
- **Workflow Orchestration**: Chain multiple agents together with conditional logic
- **Human-in-the-Loop Approvals**: Pause workflows for human decision-making
- **Scheduled & Triggered Execution**: Run workflows via cron or external events
- **Tool Management**: Dynamically assign capabilities to agents
- **Knowledge Base Integration**: Provide agents with contextual information

### Key Design Goals

1. **Zero Hardcoding**: All agents, workflows, and tools defined in database
2. **Flexibility**: Support any number of agent types and workflows
3. **Reusability**: Share agents across multiple workflows
4. **Scalability**: Handle both real-time and async execution
5. **Maintainability**: Clear separation of concerns, testable services

---

## Core Principles

### 1. Database-Driven Configuration

**NO hardcoded agent types**. Every agent is a database record with:
- Custom name and description
- Configurable LLM (Claude, GPT, Gemini, etc.)
- Dynamic system prompt
- Tool permissions
- Knowledge base access

### 2. Dynamic Tool Registry

Tools are registered dynamically and assigned to agents via configuration:

```java
// Tools are not hardcoded into agents
Tool productSearchTool = toolRegistry.register("product_search", ProductSearchHandler.class);
agent.assignTool(productSearchTool, configJson);
```

### 3. Workflow as Data

Workflows are JSON configurations stored in database:

```json
{
  "name": "Customer Purchase Approval",
  "trigger": "EVENT",
  "steps": [
    {"order": 1, "agentId": 100, "condition": null},
    {"order": 2, "agentId": 101, "condition": "${step1.needsApproval}"},
    {"order": 3, "type": "APPROVAL", "approvalRequired": true}
  ]
}
```

### 4. Agent Isolation

Each agent execution is isolated:
- Independent context
- Traceable input/output
- Individual error handling
- Separate token usage tracking

---

## System Components

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Frontend (React)                      │
│  - Agent Management UI                                   │
│  - Workflow Builder (Visual Canvas)                      │
│  - Execution Monitoring                                  │
│  - Approval Queue                                        │
└────────────────────┬────────────────────────────────────┘
                     │ REST API
                     │
┌────────────────────▼────────────────────────────────────┐
│              Spring Boot Backend                         │
│                                                          │
│  Controllers:                                            │
│  ├─ AgentController          (CRUD for agents)          │
│  ├─ WorkflowController       (CRUD & execute workflows) │
│  ├─ ExecutionController      (Monitor runs)             │
│  ├─ ApprovalController       (Approve/reject)           │
│  └─ ToolController           (Tool registry)            │
│                                                          │
│  Services:                                               │
│  ├─ AgentService             (Agent management)         │
│  ├─ AgentExecutionService    (Execute single agent)     │
│  ├─ WorkflowOrchestratorService (Execute workflows)     │
│  ├─ ToolRegistryService      (Tool management)          │
│  ├─ ApprovalService          (HITL approval)            │
│  ├─ SchedulerService         (Cron & triggers)          │
│  └─ KnowledgeBaseService     (RAG context)              │
│                                                          │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│              PostgreSQL Database                         │
│  - agents, agent_tools, tools                           │
│  - workflows, workflow_steps                            │
│  - workflow_executions, agent_executions                │
│  - approval_requests, workflow_schedules                │
│  - knowledge_bases                                       │
└─────────────────────────────────────────────────────────┘
```

---

## Database Schema

See [DATABASE_SCHEMA.md](./DATABASE_SCHEMA.md) for complete SQL definitions.

### Core Tables

#### `agents`
Stores agent definitions (no hardcoded types).

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| name | VARCHAR(255) | Agent name (e.g., "Product Expert") |
| description | TEXT | What this agent does |
| model_provider | VARCHAR(50) | "ANTHROPIC", "OPENAI", "GOOGLE" |
| model_name | VARCHAR(100) | "claude-3-5-sonnet-20241022" |
| system_prompt | TEXT | Custom instructions |
| temperature | DECIMAL | 0.0-1.0 |
| max_tokens | INTEGER | Response length limit |
| config_json | JSONB | Additional config |
| created_at | TIMESTAMP | Creation time |
| updated_at | TIMESTAMP | Last modified |

#### `tools`
Registry of all available tools.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| name | VARCHAR(100) | "product_search" |
| type | VARCHAR(50) | "SHOPIFY", "MCP", "CUSTOM" |
| description | TEXT | What the tool does |
| input_schema_json | JSONB | Expected parameters |
| handler_class | VARCHAR(255) | Java class name |

#### `agent_tools`
Many-to-many relationship between agents and tools.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| agent_id | BIGINT | FK to agents |
| tool_id | BIGINT | FK to tools |
| config_json | JSONB | Tool-specific config |

#### `workflows`
Workflow definitions.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| name | VARCHAR(255) | Workflow name |
| description | TEXT | Purpose |
| trigger_type | VARCHAR(50) | "MANUAL", "SCHEDULED", "EVENT" |
| trigger_config_json | JSONB | Cron or event filters |
| execution_mode | VARCHAR(20) | "SYNC", "ASYNC" |
| is_active | BOOLEAN | Enabled/disabled |

#### `workflow_steps`
Ordered steps in a workflow.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| workflow_id | BIGINT | FK to workflows |
| step_order | INTEGER | Execution order |
| step_type | VARCHAR(50) | "AGENT", "APPROVAL", "CONDITION" |
| agent_id | BIGINT | FK to agents (if AGENT type) |
| input_mapping_json | JSONB | Maps context vars to inputs |
| output_variable | VARCHAR(100) | Store result as this var |
| condition_expression | TEXT | JS-like: ${step1.success} |
| depends_on | INTEGER[] | Array of step_order dependencies |
| approval_config_json | JSONB | Approval settings |
| timeout_seconds | INTEGER | Max execution time |

#### `workflow_executions`
Tracks workflow runs.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| workflow_id | BIGINT | FK to workflows |
| status | VARCHAR(50) | "PENDING", "RUNNING", "COMPLETED", "FAILED" |
| trigger_data_json | JSONB | Original trigger input |
| context_data_json | JSONB | Accumulated step outputs |
| started_at | TIMESTAMP | Start time |
| completed_at | TIMESTAMP | End time |
| error_message | TEXT | Error details |

---

## Services Architecture

### 1. AgentService

**Purpose**: CRUD operations for agents, dynamic prompt assembly.

**Key Methods**:
```java
Agent createAgent(AgentRequest request)
Agent updateAgent(Long id, AgentRequest request)
void deleteAgent(Long id)
List<Agent> getAllAgents()
Agent getAgentById(Long id)
```

**Responsibilities**:
- Store/retrieve agents from database
- Validate agent configuration
- Build complete system prompts from templates + config
- Handle multi-model support (Claude, GPT, Gemini)

---

### 2. ToolRegistryService

**Purpose**: Register tools and manage agent-tool relationships.

**Key Methods**:
```java
Tool registerTool(String name, String handlerClass, JsonNode inputSchema)
List<Tool> getToolsForAgent(Long agentId)
void assignToolToAgent(Long agentId, Long toolId, JsonNode config)
void removeToolFromAgent(Long agentId, Long toolId)
```

**Responsibilities**:
- Maintain registry of available tools
- Dynamically load tool handler classes
- Manage tool permissions per agent
- Inject tools into AI requests

---

### 3. AgentExecutionService

**Purpose**: Execute a single agent with tools.

**Key Methods**:
```java
Mono<AgentExecutionResult> executeAgent(Long agentId, JsonNode input)
```

**Execution Flow**:
1. Load agent configuration from database
2. Retrieve assigned tools
3. Build system prompt with knowledge base context
4. Call LLM API (Claude/GPT/Gemini)
5. Handle tool calls (recursive if multi-turn)
6. Return structured result
7. Log execution to `agent_executions` table

**Example**:
```java
AgentExecutionResult result = agentExecutionService
    .executeAgent(123L, input)
    .block();
```

---

### 4. WorkflowOrchestratorService

**Purpose**: Execute multi-step workflows with conditionals.

**Key Methods**:
```java
Mono<WorkflowExecutionResult> executeWorkflow(Long workflowId, JsonNode triggerData)
Mono<WorkflowExecutionResult> resumeWorkflow(Long executionId, JsonNode approvalData)
```

**Execution Flow**:
1. Load workflow definition from database
2. Create `workflow_execution` record (status: RUNNING)
3. Initialize context with trigger data
4. For each step (in order):
   a. Evaluate `depends_on` prerequisites
   b. Evaluate `condition_expression` (skip if false)
   c. If AGENT type: execute agent with input mapping
   d. If APPROVAL type: create approval request, pause
   e. Store output in context with `output_variable` name
   f. Log to `workflow_step_executions`
5. Mark workflow as COMPLETED or FAILED
6. Return final context

**Conditional Logic Example**:
```json
{
  "condition_expression": "${step1.sentiment == 'negative'}"
}
```

Evaluates against context:
```json
{
  "step1": {
    "sentiment": "negative",
    "confidence": 0.89
  }
}
```

---

### 5. ApprovalService

**Purpose**: Human-in-the-loop approval management.

**Key Methods**:
```java
ApprovalRequest createApprovalRequest(Long executionId, Long stepId, JsonNode config)
void approveRequest(Long requestId, String approvedBy, JsonNode data)
void rejectRequest(Long requestId, String rejectedBy, String reason)
List<ApprovalRequest> getPendingApprovals()
```

**Approval Flow**:
1. Workflow reaches APPROVAL step
2. Create `approval_request` record (status: PENDING)
3. Send WebSocket notification to frontend
4. Pause workflow execution
5. Admin approves/rejects via UI
6. Resume workflow with approval decision
7. Continue to next step

**Timeout Handling**:
- If approval not received within `timeout_minutes`
- Auto-reject or use default action (configurable)

---

### 6. SchedulerService

**Purpose**: Cron-based and event-triggered workflow execution.

**Key Methods**:
```java
void scheduleWorkflow(Long workflowId, String cronExpression)
void triggerWorkflowOnEvent(String eventType, JsonNode eventData)
void cancelSchedule(Long scheduleId)
```

**Scheduler Types**:

**Cron Scheduling**:
```java
// Every day at 8 AM
scheduleWorkflow(workflowId, "0 0 8 * * *");
```

**Event Triggers**:
```java
// Listen for Shopify webhook
@EventListener
void onOrderCreated(OrderCreatedEvent event) {
    List<Workflow> workflows = getWorkflowsForEvent("order.created");
    workflows.forEach(wf -> orchestrator.executeWorkflow(wf.getId(), event.getData()));
}
```

---

### 7. KnowledgeBaseService

**Purpose**: Store and retrieve agent knowledge (RAG).

**Key Methods**:
```java
void addKnowledge(Long agentId, String content, JsonNode metadata)
List<KnowledgeEntry> getKnowledgeForAgent(Long agentId)
String buildContextPrompt(Long agentId, String query)
```

**Usage**:
1. Store product manuals, policies, FAQs in `knowledge_bases` table
2. When executing agent, retrieve relevant knowledge
3. Inject into system prompt as context
4. (Future) Use vector embeddings for semantic search

---

## Workflow Execution Flow

### Example: Customer Purchase Approval Workflow

**Workflow Definition** (stored in database):

```json
{
  "id": 5,
  "name": "High-Value Order Approval",
  "trigger_type": "EVENT",
  "trigger_config": {
    "eventType": "order.created",
    "filters": {"totalAmount": {"greaterThan": 500}}
  },
  "steps": [
    {
      "order": 1,
      "agentId": 100,
      "name": "Verify Order Details",
      "inputMapping": {"orderId": "${trigger.orderId}"},
      "outputVariable": "verification"
    },
    {
      "order": 2,
      "agentId": 101,
      "name": "Check Inventory",
      "condition": "${step1.verification.valid == true}",
      "inputMapping": {"items": "${step1.verification.items}"},
      "outputVariable": "inventory"
    },
    {
      "order": 3,
      "type": "APPROVAL",
      "condition": "${step2.inventory.backorderRequired == true}",
      "approvalConfig": {
        "role": "MANAGER",
        "timeoutMinutes": 1440
      }
    },
    {
      "order": 4,
      "agentId": 102,
      "name": "Process Order",
      "inputMapping": {
        "orderId": "${trigger.orderId}",
        "approved": "${step3.approved}"
      }
    }
  ]
}
```

**Execution Steps**:

1. **Trigger**: Order #12345 created with $750 total
2. **Step 1** (Agent 100 "Verify Order Details"):
   - Input: `{"orderId": "12345"}`
   - Agent checks order validity
   - Output: `{"valid": true, "items": [...]}`
   - Context: `{"trigger": {...}, "step1": {"verification": {...}}}`

3. **Step 2** (Agent 101 "Check Inventory"):
   - Condition `${step1.verification.valid == true}` → **TRUE**
   - Input: Items from step 1
   - Agent checks stock levels
   - Output: `{"available": false, "backorderRequired": true}`
   - Context updated

4. **Step 3** (APPROVAL):
   - Condition `${step2.inventory.backorderRequired == true}` → **TRUE**
   - Create approval request
   - Send notification to manager
   - **PAUSE** workflow

5. **Manager Approves** via UI:
   - Approval recorded: `{"approved": true, "approvedBy": "john@example.com"}`
   - Context updated: `{"step3": {"approved": true}}`
   - **RESUME** workflow

6. **Step 4** (Agent 102 "Process Order"):
   - Input: Order ID + approval decision
   - Agent processes the order
   - Output: `{"status": "processed", "confirmationNumber": "..."}`

7. **Workflow Complete**: Final context returned

---

## Agent Communication

Agents communicate via **shared workflow context**:

```json
{
  "trigger": {
    "orderId": "12345",
    "customerId": "67890",
    "totalAmount": 750.00
  },
  "step1": {
    "verification": {
      "valid": true,
      "items": [{"sku": "ABC123", "qty": 5}]
    }
  },
  "step2": {
    "inventory": {
      "available": false,
      "backorderRequired": true
    }
  },
  "step3": {
    "approved": true,
    "approvedBy": "john@example.com"
  }
}
```

Each agent:
- Receives inputs mapped from context
- Returns outputs stored in context
- Next agents access previous outputs via variable references

**No direct agent-to-agent communication**. All state flows through orchestrator.

---

## Features

### 1. Zero Hardcoding
- All agents defined as database records
- No Java classes for specific agent types
- Tools registered dynamically
- Workflows created via API/UI

### 2. Human-in-the-Loop Approvals
- Workflows can pause for human decisions
- Approval requests tracked in database
- WebSocket notifications to admin UI
- Timeout handling with default actions

### 3. Scheduled & Triggered Workflows
- **Cron**: Daily reports, periodic tasks
- **Events**: Shopify webhooks, internal triggers
- **Manual**: API-triggered execution

### 4. Sync vs Async Execution
- **Sync**: Real-time chat responses (wait for result)
- **Async**: Background jobs (return execution ID, poll for status)

### 5. Tool Management
- Product search, order lookup, email send, MCP queries
- Tools assigned per agent with custom config
- Dynamic tool injection at runtime

### 6. Multi-Model Support
- Claude (Anthropic)
- GPT (OpenAI)
- Gemini (Google)
- Configurable per agent

### 7. Execution Tracking
- Complete audit trail
- Step-by-step input/output logging
- Token usage tracking
- Error details

### 8. Knowledge Base Integration
- Store agent-specific context
- Inject knowledge into prompts
- Future: Vector embeddings for semantic search

---

## Technology Stack

### Backend
- **Java 17** with Spring Boot 3.2.0
- **Spring WebFlux** for reactive programming
- **PostgreSQL** for data storage
- **JPA/Hibernate** for ORM
- **WebSocket** for real-time notifications
- **Quartz Scheduler** for cron jobs

### Frontend (to be built)
- **React 18** with React Router
- **Tailwind CSS** for styling
- **React Flow** for visual workflow builder
- **Axios** for HTTP requests
- **WebSocket client** for real-time updates

### AI Integration
- **Claude API** (Anthropic)
- **OpenAI API** (GPT models)
- **Google AI API** (Gemini)

### Deployment
- **Railway** (staging & production)
- **PostgreSQL** on Railway
- **Environment-based configuration**

---

## Next Steps

1. **Read** [DATABASE_SCHEMA.md](./DATABASE_SCHEMA.md) for complete SQL definitions
2. **Review** [IMPLEMENTATION_ROADMAP.md](./IMPLEMENTATION_ROADMAP.md) for step-by-step build plan
3. **Check** [API_SPECIFICATION.md](./API_SPECIFICATION.md) for REST endpoint details
4. **Explore** [FRONTEND_COMPONENTS.md](./FRONTEND_COMPONENTS.md) for UI components to build

---

**Document Version**: 1.0
**Last Updated**: 2025-10-14
**Next Review**: After Phase 1 completion
