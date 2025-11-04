-- =====================================================
-- Admin Activity Logging and Audit Trail System
-- Created: 2025-11-04
-- Purpose: Track all admin activities, configuration changes, and prompt testing
-- =====================================================

-- =====================================================
-- Table 1: Admin Activity Log (Comprehensive Audit Trail)
-- =====================================================
CREATE TABLE IF NOT EXISTS admin_activity_log (
    id BIGSERIAL PRIMARY KEY,

    -- Who performed the action
    shop_id BIGINT REFERENCES shopify_shops(id) ON DELETE CASCADE,
    user_email VARCHAR(255) NOT NULL,
    user_ip VARCHAR(45),  -- Supports both IPv4 and IPv6
    user_agent TEXT,

    -- What action was performed
    action_type VARCHAR(50) NOT NULL,  -- 'CREATE', 'UPDATE', 'DELETE', 'TEST', 'ROLLBACK', 'VIEW'
    action_category VARCHAR(50) NOT NULL,  -- 'CONFIG', 'PROMPT', 'TESTING', 'ANALYTICS', 'SYSTEM'
    resource_type VARCHAR(100) NOT NULL,  -- 'system_prompt', 'shop_config', 'ai_model', etc.
    resource_id VARCHAR(255),  -- ID of the affected resource

    -- What changed
    previous_value JSONB,  -- State before change
    new_value JSONB,       -- State after change
    change_summary TEXT,   -- Human-readable description

    -- When it happened
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    duration_ms INTEGER,   -- How long the operation took

    -- Result
    success BOOLEAN DEFAULT true,
    error_message TEXT,

    -- Additional context
    metadata JSONB,  -- Additional contextual information

    -- GDPR Compliance
    anonymized BOOLEAN DEFAULT false,
    anonymized_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for query performance
CREATE INDEX idx_admin_activity_shop ON admin_activity_log(shop_id);
CREATE INDEX idx_admin_activity_user ON admin_activity_log(user_email);
CREATE INDEX idx_admin_activity_created ON admin_activity_log(created_at DESC);
CREATE INDEX idx_admin_activity_action ON admin_activity_log(action_category, action_type);
CREATE INDEX idx_admin_activity_resource ON admin_activity_log(resource_type, resource_id);
CREATE INDEX idx_admin_activity_success ON admin_activity_log(success);

-- Composite index for common queries
CREATE INDEX idx_admin_activity_shop_date ON admin_activity_log(shop_id, created_at DESC);

-- Comments for documentation
COMMENT ON TABLE admin_activity_log IS 'Comprehensive audit trail of all admin activities in the Shopify admin UI';
COMMENT ON COLUMN admin_activity_log.action_type IS 'Type of action: CREATE, UPDATE, DELETE, TEST, ROLLBACK, VIEW';
COMMENT ON COLUMN admin_activity_log.action_category IS 'Category: CONFIG, PROMPT, TESTING, ANALYTICS, SYSTEM';
COMMENT ON COLUMN admin_activity_log.previous_value IS 'JSONB snapshot of state before change';
COMMENT ON COLUMN admin_activity_log.new_value IS 'JSONB snapshot of state after change';
COMMENT ON COLUMN admin_activity_log.anonymized IS 'True if PII has been anonymized for GDPR compliance';


-- =====================================================
-- Table 2: Configuration Change History (Versioning & Rollback)
-- =====================================================
CREATE TABLE IF NOT EXISTS config_change_history (
    id BIGSERIAL PRIMARY KEY,

    -- What was changed
    shop_id BIGINT NOT NULL REFERENCES shopify_shops(id) ON DELETE CASCADE,
    config_type VARCHAR(50) NOT NULL,  -- 'AI_MODEL', 'BEHAVIOR', 'PROMPT', 'ANALYTICS', 'FULL_CONFIG'

    -- Full configuration snapshot
    config_snapshot JSONB NOT NULL,  -- Complete config state at this version

    -- Change metadata
    change_type VARCHAR(20) NOT NULL,  -- 'MANUAL', 'AUTOMATIC', 'ROLLBACK', 'MIGRATION'
    changed_by VARCHAR(255) NOT NULL,
    change_reason TEXT,

    -- Performance tracking
    performance_before JSONB,  -- Metrics before change (success_rate, avg_response_time, etc.)
    performance_after JSONB,   -- Metrics after change

    -- Version control
    version_number INTEGER NOT NULL,
    is_active BOOLEAN DEFAULT false,  -- Only one active version per shop+config_type

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP WITH TIME ZONE,
    deactivated_at TIMESTAMP WITH TIME ZONE,

    -- Rollback support
    can_rollback BOOLEAN DEFAULT true,
    rollback_notes TEXT,

    -- Reference to activity log
    activity_log_id BIGINT REFERENCES admin_activity_log(id) ON DELETE SET NULL
);

-- Indexes for query performance
CREATE INDEX idx_config_history_shop ON config_change_history(shop_id);
CREATE INDEX idx_config_history_active ON config_change_history(shop_id, is_active);
CREATE INDEX idx_config_history_type ON config_change_history(config_type);
CREATE INDEX idx_config_history_created ON config_change_history(created_at DESC);
CREATE INDEX idx_config_history_version ON config_change_history(shop_id, config_type, version_number DESC);

-- Unique constraint: Only one active version per shop+config_type
CREATE UNIQUE INDEX idx_config_history_unique_active
ON config_change_history(shop_id, config_type)
WHERE is_active = true;

-- Comments for documentation
COMMENT ON TABLE config_change_history IS 'Version history of all configuration changes with rollback support';
COMMENT ON COLUMN config_change_history.config_snapshot IS 'Complete configuration state snapshot (JSONB)';
COMMENT ON COLUMN config_change_history.is_active IS 'Only one active version per shop+config_type at a time';
COMMENT ON COLUMN config_change_history.performance_before IS 'Performance metrics before this change was applied';
COMMENT ON COLUMN config_change_history.performance_after IS 'Performance metrics after this change was applied';


-- =====================================================
-- Table 3: Prompt Test Results (Preview Testing)
-- =====================================================
CREATE TABLE IF NOT EXISTS prompt_test_results (
    id BIGSERIAL PRIMARY KEY,

    -- Test context
    shop_id BIGINT NOT NULL REFERENCES shopify_shops(id) ON DELETE CASCADE,
    prompt_id BIGINT REFERENCES system_prompts(id) ON DELETE SET NULL,  -- NULL if testing unsaved prompt

    -- Test input
    test_query TEXT NOT NULL,
    test_context JSONB,  -- Additional context (conversation history, filters, etc.)

    -- Test output
    ai_response TEXT NOT NULL,
    products_returned INTEGER DEFAULT 0,
    product_ids JSONB,  -- Array of product IDs returned

    -- Performance metrics
    response_time_ms INTEGER NOT NULL,
    tokens_used INTEGER,
    api_cost_usd NUMERIC(10, 6),

    -- Quality assessment
    tester_rating INTEGER CHECK (tester_rating >= 1 AND tester_rating <= 5),  -- 1-5 stars
    tester_notes TEXT,
    auto_quality_score NUMERIC(5, 2),  -- Automated quality metrics (0-100)

    -- Test metadata
    test_mode VARCHAR(20) NOT NULL,  -- 'PREVIEW', 'A_B_TEST', 'REGRESSION', 'MANUAL'
    tested_by VARCHAR(255) NOT NULL,
    tested_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Model configuration at time of test
    model_config JSONB,  -- Snapshot of AI model settings used

    -- Reference to activity log
    activity_log_id BIGINT REFERENCES admin_activity_log(id) ON DELETE SET NULL
);

-- Indexes for query performance
CREATE INDEX idx_prompt_test_shop ON prompt_test_results(shop_id);
CREATE INDEX idx_prompt_test_prompt ON prompt_test_results(prompt_id);
CREATE INDEX idx_prompt_test_date ON prompt_test_results(tested_at DESC);
CREATE INDEX idx_prompt_test_mode ON prompt_test_results(test_mode);
CREATE INDEX idx_prompt_test_rating ON prompt_test_results(tester_rating);

-- Composite index for performance analysis
CREATE INDEX idx_prompt_test_shop_prompt ON prompt_test_results(shop_id, prompt_id, tested_at DESC);

-- Comments for documentation
COMMENT ON TABLE prompt_test_results IS 'Results from admin UI prompt testing and preview functionality';
COMMENT ON COLUMN prompt_test_results.test_mode IS 'Testing mode: PREVIEW (admin UI), A_B_TEST, REGRESSION, MANUAL';
COMMENT ON COLUMN prompt_test_results.tester_rating IS 'Manual quality rating from 1 (poor) to 5 (excellent)';
COMMENT ON COLUMN prompt_test_results.auto_quality_score IS 'Automated quality score 0-100 based on relevance, response time, etc.';
COMMENT ON COLUMN prompt_test_results.model_config IS 'Snapshot of AI model settings at test time for reproducibility';


-- =====================================================
-- Utility Views for Common Queries
-- =====================================================

-- View: Recent Admin Activity (Last 100 actions per shop)
CREATE OR REPLACE VIEW v_recent_admin_activity AS
SELECT
    aal.id,
    ss.shop_domain,
    aal.user_email,
    aal.action_category,
    aal.action_type,
    aal.resource_type,
    aal.change_summary,
    aal.success,
    aal.created_at,
    aal.duration_ms
FROM admin_activity_log aal
JOIN shopify_shops ss ON aal.shop_id = ss.id
WHERE aal.created_at > CURRENT_TIMESTAMP - INTERVAL '30 days'
ORDER BY aal.created_at DESC;

COMMENT ON VIEW v_recent_admin_activity IS 'Recent admin activities for quick dashboard display';


-- View: Active Configurations
CREATE OR REPLACE VIEW v_active_configurations AS
SELECT
    cch.id,
    ss.shop_domain,
    cch.config_type,
    cch.version_number,
    cch.changed_by,
    cch.activated_at,
    cch.config_snapshot
FROM config_change_history cch
JOIN shopify_shops ss ON cch.shop_id = ss.id
WHERE cch.is_active = true;

COMMENT ON VIEW v_active_configurations IS 'Currently active configurations for all shops';


-- View: Prompt Test Summary
CREATE OR REPLACE VIEW v_prompt_test_summary AS
SELECT
    ptr.prompt_id,
    sp.prompt_name,
    COUNT(*) as total_tests,
    AVG(ptr.response_time_ms) as avg_response_time,
    AVG(ptr.tester_rating) as avg_rating,
    AVG(ptr.auto_quality_score) as avg_quality_score,
    SUM(ptr.api_cost_usd) as total_cost
FROM prompt_test_results ptr
LEFT JOIN system_prompts sp ON ptr.prompt_id = sp.id
WHERE ptr.tested_at > CURRENT_TIMESTAMP - INTERVAL '7 days'
GROUP BY ptr.prompt_id, sp.prompt_name;

COMMENT ON VIEW v_prompt_test_summary IS 'Summary of prompt testing results for the last 7 days';


-- =====================================================
-- Functions for Automation
-- =====================================================

-- Function: Auto-anonymize old activity logs (GDPR compliance)
CREATE OR REPLACE FUNCTION anonymize_old_activity_logs()
RETURNS INTEGER AS $$
DECLARE
    rows_updated INTEGER;
BEGIN
    UPDATE admin_activity_log
    SET
        user_email = 'anonymized@example.com',
        user_ip = NULL,
        user_agent = NULL,
        anonymized = true,
        anonymized_at = CURRENT_TIMESTAMP
    WHERE
        created_at < CURRENT_TIMESTAMP - INTERVAL '2 years'
        AND anonymized = false;

    GET DIAGNOSTICS rows_updated = ROW_COUNT;
    RETURN rows_updated;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION anonymize_old_activity_logs() IS 'Anonymize activity logs older than 2 years for GDPR compliance';


-- Function: Deactivate old configuration versions (keep only last 10 per shop+type)
CREATE OR REPLACE FUNCTION cleanup_old_config_versions()
RETURNS INTEGER AS $$
DECLARE
    rows_deleted INTEGER := 0;
BEGIN
    -- Delete old versions beyond the last 10 per shop+config_type
    WITH ranked_configs AS (
        SELECT
            id,
            ROW_NUMBER() OVER (
                PARTITION BY shop_id, config_type
                ORDER BY version_number DESC
            ) as rn
        FROM config_change_history
        WHERE is_active = false
    )
    DELETE FROM config_change_history
    WHERE id IN (
        SELECT id FROM ranked_configs WHERE rn > 10
    );

    GET DIAGNOSTICS rows_deleted = ROW_COUNT;
    RETURN rows_deleted;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_config_versions() IS 'Keep only the last 10 configuration versions per shop+type';


-- =====================================================
-- Grant Permissions
-- =====================================================

-- Grant access to application user (adjust username as needed)
-- GRANT SELECT, INSERT, UPDATE ON admin_activity_log TO shopify_app_user;
-- GRANT SELECT, INSERT, UPDATE ON config_change_history TO shopify_app_user;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON prompt_test_results TO shopify_app_user;
-- GRANT SELECT ON v_recent_admin_activity TO shopify_app_user;
-- GRANT SELECT ON v_active_configurations TO shopify_app_user;
-- GRANT SELECT ON v_prompt_test_summary TO shopify_app_user;


-- =====================================================
-- Migration Complete
-- =====================================================
-- This migration creates:
-- - 3 new tables for admin activity logging
-- - 3 utility views for common queries
-- - 2 utility functions for data maintenance
-- - Proper indexes for query performance
-- - GDPR-compliant anonymization support
-- =====================================================
