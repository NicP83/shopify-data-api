# Multi-Agent System - Database Schema

**Project:** Shopify Data API - Multi-Agent Orchestration
**Created:** 2025-10-14
**Version:** 1.0
**Database:** PostgreSQL 15+

---

## Table of Contents

1. [Overview](#overview)
2. [Schema Diagram](#schema-diagram)
3. [Table Definitions](#table-definitions)
4. [Indexes](#indexes)
5. [Complete SQL DDL](#complete-sql-ddl)
6. [Migration Instructions](#migration-instructions)

---

## Overview

The multi-agent system requires 10 core tables to support:
- Dynamic agent definitions
- Tool registry and permissions
- Workflow orchestration
- Execution tracking
- Human approvals
- Scheduling
- Knowledge base (RAG)

**Design Principles**:
- ✅ All IDs are `BIGSERIAL` (auto-increment)
- ✅ Timestamps for auditing
- ✅ JSONB columns for flexibility
- ✅ Foreign key constraints
- ✅ Indexes on frequently queried columns
- ✅ No hardcoded enums (uses VARCHAR with validation in code)

---

## Schema Diagram

```
┌──────────────┐      ┌──────────────┐
│    agents    │      │    tools     │
│              │      │              │
│ id (PK)      │      │ id (PK)      │
│ name         │      │ name         │
│ description  │      │ type         │
│ model_...    │      │ handler_...  │
│ system_...   │      │ ...          │
└──────┬───────┘      └──────┬───────┘
       │                     │
       │  ┌──────────────────┘
       │  │
       ▼  ▼
┌──────────────┐
│ agent_tools  │ (many-to-many)
│              │
│ id (PK)      │
│ agent_id(FK) │
│ tool_id (FK) │
│ config_json  │
└──────────────┘

┌──────────────┐
│  workflows   │
│              │
│ id (PK)      │
│ name         │
│ trigger_type │
│ trigger_...  │
└──────┬───────┘
       │
       │ 1:N
       │
       ▼
┌──────────────┐
│workflow_steps│
│              │
│ id (PK)      │
│ workflow_id  │ FK
│ agent_id     │ FK (optional)
│ step_order   │
│ condition_...│
└──────┬───────┘
       │
       │ 1:N (when executed)
       │
       ▼
┌─────────────────┐      ┌──────────────────┐
│workflow_        │      │approval_requests │
│executions       │◄─────┤                  │
│                 │      │ id (PK)          │
│ id (PK)         │      │ execution_id(FK) │
│ workflow_id(FK) │      │ status           │
│ status          │      │ ...              │
│ context_json    │      └──────────────────┘
└────────┬────────┘
         │
         │ 1:N
         │
         ▼
┌──────────────────┐
│agent_executions  │
│                  │
│ id (PK)          │
│ execution_id(FK) │
│ agent_id (FK)    │
│ input_json       │
│ output_json      │
└──────────────────┘

┌──────────────────┐
│knowledge_bases   │
│                  │
│ id (PK)          │
│ agent_id (FK)    │
│ content          │
│ metadata_json    │
└──────────────────┘

┌──────────────────┐
│workflow_schedules│
│                  │
│ id (PK)          │
│ workflow_id (FK) │
│ cron_expression  │
│ enabled          │
└──────────────────┘
```

---

## Table Definitions

### 1. `agents`

Stores all agent definitions. **No hardcoded agent types**.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGSERIAL | NO | - | Primary key |
| name | VARCHAR(255) | NO | - | Agent name (e.g., "Product Expert") |
| description | TEXT | YES | NULL | What this agent does |
| model_provider | VARCHAR(50) | NO | - | "ANTHROPIC", "OPENAI", "GOOGLE" |
| model_name | VARCHAR(100) | NO | - | "claude-3-5-sonnet-20241022" |
| system_prompt | TEXT | NO | - | Base system instructions |
| temperature | DECIMAL(3,2) | NO | 0.7 | 0.0-1.0 creativity setting |
| max_tokens | INTEGER | NO | 1024 | Max response length |
| config_json | JSONB | YES | {} | Additional configuration |
| is_active | BOOLEAN | NO | true | Enabled/disabled |
| created_at | TIMESTAMP | NO | NOW() | Creation timestamp |
| updated_at | TIMESTAMP | NO | NOW() | Last update timestamp |

**Indexes**:
- Primary key on `id`
- Index on `name` (unique constraint)
- Index on `is_active` (filter active agents)

---

### 2. `tools`

Registry of all available tools that agents can use.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGSERIAL | NO | - | Primary key |
| name | VARCHAR(100) | NO | - | Tool identifier (e.g., "product_search") |
| type | VARCHAR(50) | NO | - | "SHOPIFY", "MCP", "CUSTOM" |
| description | TEXT | YES | NULL | What the tool does |
| input_schema_json | JSONB | NO | {} | Expected input parameters (JSON Schema) |
| handler_class | VARCHAR(255) | NO | - | Java class implementing tool |
| is_active | BOOLEAN | NO | true | Enabled/disabled |
| created_at | TIMESTAMP | NO | NOW() | Creation timestamp |

**Indexes**:
- Primary key on `id`
- Unique index on `name`

---

### 3. `agent_tools`

Many-to-many relationship between agents and tools.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGSERIAL | NO | - | Primary key |
| agent_id | BIGINT | NO | - | FK to `agents.id` |
| tool_id | BIGINT | NO | - | FK to `tools.id` |
| config_json | JSONB | YES | {} | Tool-specific configuration |
| created_at | TIMESTAMP | NO | NOW() | When tool was assigned |

**Foreign Keys**:
- `agent_id` → `agents(id)` ON DELETE CASCADE
- `tool_id` → `tools(id)` ON DELETE CASCADE

**Indexes**:
- Primary key on `id`
- Unique index on `(agent_id, tool_id)` (prevent duplicates)
- Index on `agent_id` (lookup tools for agent)

---

### 4. `workflows`

Workflow definitions with trigger configuration.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGSERIAL | NO | - | Primary key |
| name | VARCHAR(255) | NO | - | Workflow name |
| description | TEXT | YES | NULL | Purpose/documentation |
| trigger_type | VARCHAR(50) | NO | - | "MANUAL", "SCHEDULED", "EVENT" |
| trigger_config_json | JSONB | YES | {} | Cron or event filters |
| execution_mode | VARCHAR(20) | NO | "SYNC" | "SYNC", "ASYNC" |
| is_active | BOOLEAN | NO | true | Enabled/disabled |
| created_at | TIMESTAMP | NO | NOW() | Creation timestamp |
| updated_at | TIMESTAMP | NO | NOW() | Last update timestamp |

**Indexes**:
- Primary key on `id`
- Index on `trigger_type` (filter by trigger)
- Index on `is_active`

---

### 5. `workflow_steps`

Ordered steps within a workflow.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGSERIAL | NO | - | Primary key |
| workflow_id | BIGINT | NO | - | FK to `workflows.id` |
| step_order | INTEGER | NO | - | Execution order (1, 2, 3...) |
| step_type | VARCHAR(50) | NO | - | "AGENT", "APPROVAL", "CONDITION", "PARALLEL" |
| agent_id | BIGINT | YES | NULL | FK to `agents.id` (if AGENT type) |
| name | VARCHAR(255) | YES | NULL | Step name/description |
| input_mapping_json | JSONB | YES | {} | Map context vars to agent inputs |
| output_variable | VARCHAR(100) | YES | NULL | Store result as this variable |
| condition_expression | TEXT | YES | NULL | JS-like: `${step1.success}` |
| depends_on | INTEGER[] | YES | NULL | Array of step_order dependencies |
| approval_config_json | JSONB | YES | {} | Approval settings (if APPROVAL type) |
| retry_config_json | JSONB | YES | {} | Max retries, backoff strategy |
| timeout_seconds | INTEGER | YES | 300 | Max execution time (5 min default) |
| created_at | TIMESTAMP | NO | NOW() | Creation timestamp |

**Foreign Keys**:
- `workflow_id` → `workflows(id)` ON DELETE CASCADE
- `agent_id` → `agents(id)` ON DELETE SET NULL

**Indexes**:
- Primary key on `id`
- Index on `(workflow_id, step_order)` (ordered retrieval)
- Index on `agent_id`

---

### 6. `workflow_executions`

Tracks workflow execution runs.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGSERIAL | NO | - | Primary key |
| workflow_id | BIGINT | NO | - | FK to `workflows.id` |
| status | VARCHAR(50) | NO | "PENDING" | "PENDING", "RUNNING", "WAITING_APPROVAL", "COMPLETED", "FAILED", "CANCELLED" |
| trigger_data_json | JSONB | YES | {} | Original trigger input |
| context_data_json | JSONB | YES | {} | Accumulated step outputs |
| started_at | TIMESTAMP | YES | NULL | When execution began |
| completed_at | TIMESTAMP | YES | NULL | When execution finished |
| error_message | TEXT | YES | NULL | Error details if FAILED |
| created_at | TIMESTAMP | NO | NOW() | Record creation |

**Foreign Keys**:
- `workflow_id` → `workflows(id)` ON DELETE CASCADE

**Indexes**:
- Primary key on `id`
- Index on `workflow_id` (execution history)
- Index on `status` (filter by status)
- Index on `created_at` (time-based queries)

---

### 7. `agent_executions`

Tracks individual agent runs within workflows.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGSERIAL | NO | - | Primary key |
| workflow_execution_id | BIGINT | YES | NULL | FK to `workflow_executions.id` (NULL if standalone) |
| workflow_step_id | BIGINT | YES | NULL | FK to `workflow_steps.id` |
| agent_id | BIGINT | NO | - | FK to `agents.id` |
| status | VARCHAR(50) | NO | "PENDING" | "PENDING", "RUNNING", "COMPLETED", "FAILED" |
| input_data_json | JSONB | YES | {} | Input to agent |
| output_data_json | JSONB | YES | {} | Agent response |
| tokens_used | INTEGER | YES | NULL | Token count |
| execution_time_ms | INTEGER | YES | NULL | Duration in milliseconds |
| error_message | TEXT | YES | NULL | Error details |
| started_at | TIMESTAMP | YES | NULL | Start time |
| completed_at | TIMESTAMP | YES | NULL | End time |
| created_at | TIMESTAMP | NO | NOW() | Record creation |

**Foreign Keys**:
- `workflow_execution_id` → `workflow_executions(id)` ON DELETE CASCADE
- `workflow_step_id` → `workflow_steps(id)` ON DELETE SET NULL
- `agent_id` → `agents(id)` ON DELETE CASCADE

**Indexes**:
- Primary key on `id`
- Index on `workflow_execution_id`
- Index on `agent_id` (agent performance tracking)
- Index on `status`

---

### 8. `approval_requests`

Human-in-the-loop approval tracking.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGSERIAL | NO | - | Primary key |
| workflow_execution_id | BIGINT | NO | - | FK to `workflow_executions.id` |
| workflow_step_id | BIGINT | NO | - | FK to `workflow_steps.id` |
| status | VARCHAR(50) | NO | "PENDING" | "PENDING", "APPROVED", "REJECTED", "TIMEOUT" |
| required_role | VARCHAR(100) | YES | NULL | Who can approve (e.g., "MANAGER") |
| approved_by | VARCHAR(255) | YES | NULL | Email/ID of approver |
| approved_at | TIMESTAMP | YES | NULL | When approved/rejected |
| comments | TEXT | YES | NULL | Approval notes |
| timeout_at | TIMESTAMP | YES | NULL | Auto-reject time |
| requested_at | TIMESTAMP | NO | NOW() | When approval was requested |

**Foreign Keys**:
- `workflow_execution_id` → `workflow_executions(id)` ON DELETE CASCADE
- `workflow_step_id` → `workflow_steps(id)` ON DELETE CASCADE

**Indexes**:
- Primary key on `id`
- Index on `workflow_execution_id`
- Index on `status` (pending approvals)
- Index on `requested_at` (timeout checks)

---

### 9. `knowledge_bases`

Agent-specific knowledge (RAG content).

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGSERIAL | NO | - | Primary key |
| agent_id | BIGINT | NO | - | FK to `agents.id` |
| name | VARCHAR(255) | NO | - | Knowledge entry name |
| content | TEXT | NO | - | Knowledge text |
| embedding_vector | VECTOR(1536) | YES | NULL | Embedding for semantic search (pgvector) |
| metadata_json | JSONB | YES | {} | Tags, category, etc. |
| created_at | TIMESTAMP | NO | NOW() | Creation timestamp |
| updated_at | TIMESTAMP | NO | NOW() | Last update |

**Foreign Keys**:
- `agent_id` → `agents(id)` ON DELETE CASCADE

**Indexes**:
- Primary key on `id`
- Index on `agent_id`
- Index on `embedding_vector` (if using pgvector)

**Note**: `VECTOR` type requires pgvector extension. If not using embeddings initially, make this column NULL-able and add later.

---

### 10. `workflow_schedules`

Cron-based workflow scheduling.

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGSERIAL | NO | - | Primary key |
| workflow_id | BIGINT | NO | - | FK to `workflows.id` |
| cron_expression | VARCHAR(100) | NO | - | Cron schedule (e.g., "0 0 8 * * *") |
| enabled | BOOLEAN | NO | true | Active/inactive |
| last_run_at | TIMESTAMP | YES | NULL | Last execution time |
| next_run_at | TIMESTAMP | YES | NULL | Next scheduled run |
| created_at | TIMESTAMP | NO | NOW() | Creation timestamp |
| updated_at | TIMESTAMP | NO | NOW() | Last update |

**Foreign Keys**:
- `workflow_id` → `workflows(id)` ON DELETE CASCADE

**Indexes**:
- Primary key on `id`
- Index on `workflow_id`
- Index on `enabled` (active schedules)
- Index on `next_run_at` (scheduler polling)

---

## Indexes

All tables have their primary keys automatically indexed. Additional indexes:

```sql
-- Agents
CREATE UNIQUE INDEX idx_agents_name ON agents(name);
CREATE INDEX idx_agents_active ON agents(is_active) WHERE is_active = true;

-- Tools
CREATE UNIQUE INDEX idx_tools_name ON tools(name);

-- Agent Tools
CREATE UNIQUE INDEX idx_agent_tools_pair ON agent_tools(agent_id, tool_id);
CREATE INDEX idx_agent_tools_agent ON agent_tools(agent_id);

-- Workflows
CREATE INDEX idx_workflows_trigger_type ON workflows(trigger_type);
CREATE INDEX idx_workflows_active ON workflows(is_active) WHERE is_active = true;

-- Workflow Steps
CREATE INDEX idx_workflow_steps_workflow_order ON workflow_steps(workflow_id, step_order);
CREATE INDEX idx_workflow_steps_agent ON workflow_steps(agent_id);

-- Workflow Executions
CREATE INDEX idx_workflow_executions_workflow ON workflow_executions(workflow_id);
CREATE INDEX idx_workflow_executions_status ON workflow_executions(status);
CREATE INDEX idx_workflow_executions_created ON workflow_executions(created_at);

-- Agent Executions
CREATE INDEX idx_agent_executions_workflow ON agent_executions(workflow_execution_id);
CREATE INDEX idx_agent_executions_agent ON agent_executions(agent_id);
CREATE INDEX idx_agent_executions_status ON agent_executions(status);

-- Approval Requests
CREATE INDEX idx_approval_requests_execution ON approval_requests(workflow_execution_id);
CREATE INDEX idx_approval_requests_status ON approval_requests(status);
CREATE INDEX idx_approval_requests_timeout ON approval_requests(timeout_at);

-- Knowledge Bases
CREATE INDEX idx_knowledge_bases_agent ON knowledge_bases(agent_id);

-- Workflow Schedules
CREATE INDEX idx_workflow_schedules_workflow ON workflow_schedules(workflow_id);
CREATE INDEX idx_workflow_schedules_enabled ON workflow_schedules(enabled) WHERE enabled = true;
CREATE INDEX idx_workflow_schedules_next_run ON workflow_schedules(next_run_at);
```

---

## Complete SQL DDL

```sql
-- ============================================
-- Multi-Agent System Database Schema
-- Version: 1.0
-- Date: 2025-10-14
-- ============================================

-- 1. AGENTS TABLE
CREATE TABLE agents (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    model_provider VARCHAR(50) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    system_prompt TEXT NOT NULL,
    temperature DECIMAL(3,2) NOT NULL DEFAULT 0.7,
    max_tokens INTEGER NOT NULL DEFAULT 1024,
    config_json JSONB DEFAULT '{}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_agents_name ON agents(name);
CREATE INDEX idx_agents_active ON agents(is_active) WHERE is_active = true;

-- 2. TOOLS TABLE
CREATE TABLE tools (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    description TEXT,
    input_schema_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    handler_class VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_tools_name ON tools(name);

-- 3. AGENT_TOOLS TABLE (many-to-many)
CREATE TABLE agent_tools (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    tool_id BIGINT NOT NULL REFERENCES tools(id) ON DELETE CASCADE,
    config_json JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_agent_tools_pair ON agent_tools(agent_id, tool_id);
CREATE INDEX idx_agent_tools_agent ON agent_tools(agent_id);

-- 4. WORKFLOWS TABLE
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

CREATE INDEX idx_workflows_trigger_type ON workflows(trigger_type);
CREATE INDEX idx_workflows_active ON workflows(is_active) WHERE is_active = true;

-- 5. WORKFLOW_STEPS TABLE
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

CREATE INDEX idx_workflow_steps_workflow_order ON workflow_steps(workflow_id, step_order);
CREATE INDEX idx_workflow_steps_agent ON workflow_steps(agent_id);

-- 6. WORKFLOW_EXECUTIONS TABLE
CREATE TABLE workflow_executions (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    trigger_data_json JSONB DEFAULT '{}'::jsonb,
    context_data_json JSONB DEFAULT '{}'::jsonb,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_workflow_executions_workflow ON workflow_executions(workflow_id);
CREATE INDEX idx_workflow_executions_status ON workflow_executions(status);
CREATE INDEX idx_workflow_executions_created ON workflow_executions(created_at);

-- 7. AGENT_EXECUTIONS TABLE
CREATE TABLE agent_executions (
    id BIGSERIAL PRIMARY KEY,
    workflow_execution_id BIGINT REFERENCES workflow_executions(id) ON DELETE CASCADE,
    workflow_step_id BIGINT REFERENCES workflow_steps(id) ON DELETE SET NULL,
    agent_id BIGINT NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    input_data_json JSONB DEFAULT '{}'::jsonb,
    output_data_json JSONB DEFAULT '{}'::jsonb,
    tokens_used INTEGER,
    execution_time_ms INTEGER,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_executions_workflow ON agent_executions(workflow_execution_id);
CREATE INDEX idx_agent_executions_agent ON agent_executions(agent_id);
CREATE INDEX idx_agent_executions_status ON agent_executions(status);

-- 8. APPROVAL_REQUESTS TABLE
CREATE TABLE approval_requests (
    id BIGSERIAL PRIMARY KEY,
    workflow_execution_id BIGINT NOT NULL REFERENCES workflow_executions(id) ON DELETE CASCADE,
    workflow_step_id BIGINT NOT NULL REFERENCES workflow_steps(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    required_role VARCHAR(100),
    approved_by VARCHAR(255),
    approved_at TIMESTAMP,
    comments TEXT,
    timeout_at TIMESTAMP,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_approval_requests_execution ON approval_requests(workflow_execution_id);
CREATE INDEX idx_approval_requests_status ON approval_requests(status);
CREATE INDEX idx_approval_requests_timeout ON approval_requests(timeout_at);

-- 9. KNOWLEDGE_BASES TABLE
CREATE TABLE knowledge_bases (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    embedding_vector VECTOR(1536),  -- Requires pgvector extension
    metadata_json JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_knowledge_bases_agent ON knowledge_bases(agent_id);
-- Note: Add vector index if using pgvector: CREATE INDEX ON knowledge_bases USING ivfflat (embedding_vector);

-- 10. WORKFLOW_SCHEDULES TABLE
CREATE TABLE workflow_schedules (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    cron_expression VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    last_run_at TIMESTAMP,
    next_run_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_workflow_schedules_workflow ON workflow_schedules(workflow_id);
CREATE INDEX idx_workflow_schedules_enabled ON workflow_schedules(enabled) WHERE enabled = true;
CREATE INDEX idx_workflow_schedules_next_run ON workflow_schedules(next_run_at);

-- ============================================
-- UPDATE TRIGGER FOR updated_at COLUMNS
-- ============================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_agents_updated_at BEFORE UPDATE ON agents
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_workflows_updated_at BEFORE UPDATE ON workflows
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_knowledge_bases_updated_at BEFORE UPDATE ON knowledge_bases
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_workflow_schedules_updated_at BEFORE UPDATE ON workflow_schedules
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- COMMENTS (Documentation in Database)
-- ============================================

COMMENT ON TABLE agents IS 'Dynamic agent definitions with configurable LLMs';
COMMENT ON TABLE tools IS 'Registry of all available tools';
COMMENT ON TABLE agent_tools IS 'Many-to-many relationship between agents and tools';
COMMENT ON TABLE workflows IS 'Workflow definitions with trigger configuration';
COMMENT ON TABLE workflow_steps IS 'Ordered steps within workflows';
COMMENT ON TABLE workflow_executions IS 'Tracks workflow execution runs';
COMMENT ON TABLE agent_executions IS 'Tracks individual agent runs';
COMMENT ON TABLE approval_requests IS 'Human-in-the-loop approval tracking';
COMMENT ON TABLE knowledge_bases IS 'Agent-specific knowledge (RAG)';
COMMENT ON TABLE workflow_schedules IS 'Cron-based workflow scheduling';
```

---

## Migration Instructions

### For New Database (Fresh Install)

```bash
# 1. Connect to PostgreSQL
psql -U postgres -d shopify_data

# 2. Run the complete DDL
\i /path/to/multi-agent-schema.sql

# 3. Verify tables created
\dt

# 4. Optional: Install pgvector for embeddings
CREATE EXTENSION IF NOT EXISTS vector;
```

### For Existing Database (Add to Production)

**WARNING**: Always test in staging first!

```bash
# 1. Backup existing database
pg_dump -U postgres -d shopify_data > backup_before_migration.sql

# 2. Create migration file (separate from DDL)
# Save as: V001__add_multi_agent_system.sql

# 3. Run migration via Flyway/Liquibase or manually
psql -U postgres -d shopify_data -f V001__add_multi_agent_system.sql

# 4. Verify
\dt agents tools workflows
```

### Rollback Plan

If migration fails:

```sql
-- Drop all multi-agent tables (in reverse order of dependencies)
DROP TABLE IF EXISTS workflow_schedules CASCADE;
DROP TABLE IF EXISTS knowledge_bases CASCADE;
DROP TABLE IF EXISTS approval_requests CASCADE;
DROP TABLE IF EXISTS agent_executions CASCADE;
DROP TABLE IF EXISTS workflow_executions CASCADE;
DROP TABLE IF EXISTS workflow_steps CASCADE;
DROP TABLE IF EXISTS workflows CASCADE;
DROP TABLE IF EXISTS agent_tools CASCADE;
DROP TABLE IF EXISTS tools CASCADE;
DROP TABLE IF EXISTS agents CASCADE;

-- Restore from backup
-- psql -U postgres -d shopify_data < backup_before_migration.sql
```

---

## Database Size Estimates

Estimated storage per table (1000 workflows, 10000 executions):

| Table | Est. Rows | Est. Size |
|-------|-----------|-----------|
| agents | 100 | 50 KB |
| tools | 50 | 20 KB |
| agent_tools | 500 | 100 KB |
| workflows | 1000 | 500 KB |
| workflow_steps | 5000 | 2 MB |
| workflow_executions | 10000 | 50 MB |
| agent_executions | 50000 | 250 MB |
| approval_requests | 1000 | 500 KB |
| knowledge_bases | 1000 | 10 MB |
| workflow_schedules | 500 | 200 KB |
| **TOTAL** | - | **~313 MB** |

Add indexes: +50 MB
**Total Estimated**: ~400 MB for moderate usage.

---

## Next Steps

1. **Create Migration File**: Extract SQL to `src/main/resources/db/migration/V001__multi_agent_system.sql`
2. **Create JPA Entities**: See implementation roadmap for entity classes
3. **Test Schema**: Verify all foreign keys and indexes work correctly

---

**Document Version**: 1.0
**Last Updated**: 2025-10-14
**Next Review**: After schema implementation
