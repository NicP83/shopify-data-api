-- ============================================
-- Add Input Schema Support to Workflows
-- Version: 4
-- Date: 2025-10-15
-- Description: Add input schema definition, interface type,
--              and public access support to workflows
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

-- Add comments for documentation
COMMENT ON COLUMN workflows.input_schema_json IS 'JSON Schema defining workflow inputs (e.g., {type: "object", properties: {...}})';
COMMENT ON COLUMN workflows.interface_type IS 'Execution interface type: FORM, CHAT, API, CUSTOM';
COMMENT ON COLUMN workflows.is_public IS 'Whether workflow can be executed without authentication';

-- Log migration completion
DO $$
BEGIN
    RAISE NOTICE 'Migration V004 completed: Added input schema support to workflows';
    RAISE NOTICE 'New columns added: input_schema_json, interface_type, is_public';
    RAISE NOTICE 'Indexes created: idx_workflows_public, idx_workflows_interface_type';
END $$;
