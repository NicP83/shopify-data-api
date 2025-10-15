-- ============================================
-- Multi-Agent System Database Schema
-- Version: 2.0
-- Date: 2025-10-14
-- Description: Complete multi-agent orchestration system
--              with workflows, approvals, and scheduling
-- ============================================

-- Note: This migration assumes PostgreSQL 12+
-- For pgvector support, run: CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================
-- TABLE 1: AGENTS
-- ============================================
CREATE TABLE IF NOT EXISTS agents (
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

CREATE UNIQUE INDEX IF NOT EXISTS idx_agents_name ON agents(name);
CREATE INDEX IF NOT EXISTS idx_agents_active ON agents(is_active) WHERE is_active = true;

-- ============================================
-- TABLE 2: TOOLS
-- ============================================
CREATE TABLE IF NOT EXISTS tools (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    description TEXT,
    input_schema_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    handler_class VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_tools_name ON tools(name);

-- ============================================
-- TABLE 3: AGENT_TOOLS (many-to-many)
-- ============================================
CREATE TABLE IF NOT EXISTS agent_tools (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    tool_id BIGINT NOT NULL REFERENCES tools(id) ON DELETE CASCADE,
    config_json JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_agent_tools_pair ON agent_tools(agent_id, tool_id);
CREATE INDEX IF NOT EXISTS idx_agent_tools_agent ON agent_tools(agent_id);

-- ============================================
-- TABLE 4: WORKFLOWS
-- ============================================
CREATE TABLE IF NOT EXISTS workflows (
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

CREATE INDEX IF NOT EXISTS idx_workflows_trigger_type ON workflows(trigger_type);
CREATE INDEX IF NOT EXISTS idx_workflows_active ON workflows(is_active) WHERE is_active = true;

-- ============================================
-- TABLE 5: WORKFLOW_STEPS
-- ============================================
CREATE TABLE IF NOT EXISTS workflow_steps (
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

CREATE INDEX IF NOT EXISTS idx_workflow_steps_workflow_order ON workflow_steps(workflow_id, step_order);
CREATE INDEX IF NOT EXISTS idx_workflow_steps_agent ON workflow_steps(agent_id);

-- ============================================
-- TABLE 6: WORKFLOW_EXECUTIONS
-- ============================================
CREATE TABLE IF NOT EXISTS workflow_executions (
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

CREATE INDEX IF NOT EXISTS idx_workflow_executions_workflow ON workflow_executions(workflow_id);
CREATE INDEX IF NOT EXISTS idx_workflow_executions_status ON workflow_executions(status);
CREATE INDEX IF NOT EXISTS idx_workflow_executions_created ON workflow_executions(created_at);

-- ============================================
-- TABLE 7: AGENT_EXECUTIONS
-- ============================================
CREATE TABLE IF NOT EXISTS agent_executions (
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

CREATE INDEX IF NOT EXISTS idx_agent_executions_workflow ON agent_executions(workflow_execution_id);
CREATE INDEX IF NOT EXISTS idx_agent_executions_agent ON agent_executions(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_executions_status ON agent_executions(status);

-- ============================================
-- TABLE 8: APPROVAL_REQUESTS
-- ============================================
CREATE TABLE IF NOT EXISTS approval_requests (
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

CREATE INDEX IF NOT EXISTS idx_approval_requests_execution ON approval_requests(workflow_execution_id);
CREATE INDEX IF NOT EXISTS idx_approval_requests_status ON approval_requests(status);
CREATE INDEX IF NOT EXISTS idx_approval_requests_timeout ON approval_requests(timeout_at);

-- ============================================
-- TABLE 9: KNOWLEDGE_BASES
-- ============================================
CREATE TABLE IF NOT EXISTS knowledge_bases (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    -- embedding_vector VECTOR(1536), -- Uncomment when pgvector is installed
    metadata_json JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_bases_agent ON knowledge_bases(agent_id);
-- CREATE INDEX IF NOT EXISTS idx_knowledge_bases_vector ON knowledge_bases USING ivfflat (embedding_vector); -- Uncomment for pgvector

-- ============================================
-- TABLE 10: WORKFLOW_SCHEDULES
-- ============================================
CREATE TABLE IF NOT EXISTS workflow_schedules (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    cron_expression VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    last_run_at TIMESTAMP,
    next_run_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_schedules_workflow ON workflow_schedules(workflow_id);
CREATE INDEX IF NOT EXISTS idx_workflow_schedules_enabled ON workflow_schedules(enabled) WHERE enabled = true;
CREATE INDEX IF NOT EXISTS idx_workflow_schedules_next_run ON workflow_schedules(next_run_at);

-- ============================================
-- UPDATE TRIGGERS FOR TIMESTAMPS
-- ============================================

-- Create update function if it doesn't exist
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Drop triggers if they exist (for idempotency)
DROP TRIGGER IF EXISTS update_agents_updated_at ON agents;
DROP TRIGGER IF EXISTS update_workflows_updated_at ON workflows;
DROP TRIGGER IF EXISTS update_knowledge_bases_updated_at ON knowledge_bases;
DROP TRIGGER IF EXISTS update_workflow_schedules_updated_at ON workflow_schedules;

-- Create triggers
CREATE TRIGGER update_agents_updated_at
    BEFORE UPDATE ON agents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_workflows_updated_at
    BEFORE UPDATE ON workflows
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_knowledge_bases_updated_at
    BEFORE UPDATE ON knowledge_bases
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_workflow_schedules_updated_at
    BEFORE UPDATE ON workflow_schedules
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- TABLE COMMENTS (Documentation)
-- ============================================

COMMENT ON TABLE agents IS 'Dynamic agent definitions with configurable LLMs and prompts';
COMMENT ON TABLE tools IS 'Registry of all available tools that agents can use';
COMMENT ON TABLE agent_tools IS 'Many-to-many relationship between agents and their assigned tools';
COMMENT ON TABLE workflows IS 'Workflow definitions with trigger configuration and execution mode';
COMMENT ON TABLE workflow_steps IS 'Ordered steps within workflows with conditionals and dependencies';
COMMENT ON TABLE workflow_executions IS 'Tracks workflow execution runs with full context';
COMMENT ON TABLE agent_executions IS 'Tracks individual agent runs with performance metrics';
COMMENT ON TABLE approval_requests IS 'Human-in-the-loop approval tracking with timeout support';
COMMENT ON TABLE knowledge_bases IS 'Agent-specific knowledge base content (RAG)';
COMMENT ON TABLE workflow_schedules IS 'Cron-based workflow scheduling configuration';

-- ============================================
-- SEED DATA (Optional - for testing)
-- ============================================

-- Insert sample product search tool
INSERT INTO tools (name, type, description, input_schema_json, handler_class, is_active, created_at)
VALUES (
    'product_search',
    'SHOPIFY',
    'Search for products in the Shopify catalog',
    '{"type": "object", "properties": {"query": {"type": "string", "description": "Search query"}, "maxResults": {"type": "integer", "description": "Maximum results to return"}}, "required": ["query"]}'::jsonb,
    'com.shopify.api.service.tool.ProductSearchToolHandler',
    true,
    CURRENT_TIMESTAMP
)
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- MIGRATION COMPLETE
-- ============================================

-- Log migration completion
DO $$
BEGIN
    RAISE NOTICE 'Multi-Agent System schema migration V002 completed successfully';
    RAISE NOTICE 'Tables created: 10';
    RAISE NOTICE 'Indexes created: 20+';
    RAISE NOTICE 'Triggers created: 4';
END $$;
