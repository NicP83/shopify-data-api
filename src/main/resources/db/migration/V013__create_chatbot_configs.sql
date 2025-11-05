-- Create chatbot_configs table for persistent chatbot configuration
-- Supports shop-specific and global configurations with full audit trail

CREATE TABLE chatbot_configs (
    id BIGSERIAL PRIMARY KEY,

    -- Shop association (NULL for global default config)
    shop_id BIGINT REFERENCES shopify_shops(id) ON DELETE CASCADE,

    -- Store Identity
    store_name VARCHAR(255),
    store_description TEXT,
    store_categories VARCHAR(500),

    -- Behavior Rules
    scope_instructions TEXT,
    out_of_scope_response TEXT,
    require_search_before_recommendation BOOLEAN DEFAULT true,

    -- Tool Configuration
    enable_product_search BOOLEAN DEFAULT true,
    max_search_results INTEGER DEFAULT 5,

    -- Response Style
    tone_of_voice VARCHAR(100) DEFAULT 'friendly',
    include_cart_links BOOLEAN DEFAULT true,
    show_prices BOOLEAN DEFAULT true,
    show_skus BOOLEAN DEFAULT false,

    -- Advanced
    custom_instructions TEXT,

    -- AI Model Settings
    model_name VARCHAR(100),
    temperature DECIMAL(3,2),
    max_tokens INTEGER,

    -- Agent Integration (comma-separated agent IDs)
    linked_agent_ids TEXT,

    -- Configuration metadata
    is_active BOOLEAN DEFAULT true,
    version INTEGER DEFAULT 1,

    -- Audit tracking
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),

    -- Constraints
    CONSTRAINT unique_shop_config UNIQUE (shop_id),
    CONSTRAINT check_temperature CHECK (temperature IS NULL OR (temperature >= 0.0 AND temperature <= 1.0)),
    CONSTRAINT check_max_tokens CHECK (max_tokens IS NULL OR (max_tokens >= 256 AND max_tokens <= 8192))
);

-- Indexes for performance
CREATE INDEX idx_chatbot_configs_shop ON chatbot_configs(shop_id) WHERE is_active = true;
CREATE INDEX idx_chatbot_configs_global ON chatbot_configs(shop_id) WHERE shop_id IS NULL AND is_active = true;

-- Trigger to auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_chatbot_configs_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_chatbot_configs_updated_at
    BEFORE UPDATE ON chatbot_configs
    FOR EACH ROW
    EXECUTE FUNCTION update_chatbot_configs_updated_at();

-- Comments for documentation
COMMENT ON TABLE chatbot_configs IS 'Persistent chatbot configuration with shop-specific and global support';
COMMENT ON COLUMN chatbot_configs.shop_id IS 'NULL for global default config, otherwise shop-specific';
COMMENT ON COLUMN chatbot_configs.linked_agent_ids IS 'Comma-separated list of agent IDs for delegation (e.g., "1,3,5")';
COMMENT ON COLUMN chatbot_configs.version IS 'Configuration version for tracking changes';
COMMENT ON COLUMN chatbot_configs.is_active IS 'Whether this configuration is currently active';
