-- Migration: V006__create_shopify_shops.sql
-- Description: Create shopify_shops table for OAuth and shop-specific AI configuration

CREATE TABLE shopify_shops (
    id BIGSERIAL PRIMARY KEY,

    -- Shop identification
    shop_domain VARCHAR(255) NOT NULL UNIQUE,
    shop_name VARCHAR(255),
    shop_email VARCHAR(255),
    shop_owner VARCHAR(255),

    -- OAuth tokens (encrypt in production)
    access_token TEXT NOT NULL,
    scope TEXT,

    -- Shop metadata
    plan_name VARCHAR(100),
    currency VARCHAR(10) DEFAULT 'USD',
    timezone VARCHAR(100),

    -- AI configuration
    ai_enabled BOOLEAN DEFAULT true,
    ai_model VARCHAR(100) DEFAULT 'claude-sonnet-4-5-20250929',
    ai_temperature DECIMAL(3,2) DEFAULT 0.7,
    ai_max_tokens INTEGER DEFAULT 4096,
    ai_system_prompt TEXT,

    -- Analytics settings
    analytics_enabled BOOLEAN DEFAULT true,
    track_chat_usage BOOLEAN DEFAULT true,

    -- Installation tracking
    installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uninstalled_at TIMESTAMP,
    is_active BOOLEAN DEFAULT true,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_shopify_shops_domain ON shopify_shops(shop_domain);
CREATE INDEX idx_shopify_shops_active ON shopify_shops(is_active) WHERE is_active = true;
CREATE INDEX idx_shopify_shops_installed ON shopify_shops(installed_at);

-- Updated timestamp trigger
CREATE OR REPLACE FUNCTION update_shopify_shops_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_shopify_shops_updated_at
    BEFORE UPDATE ON shopify_shops
    FOR EACH ROW
    EXECUTE FUNCTION update_shopify_shops_updated_at();

-- Comments for documentation
COMMENT ON TABLE shopify_shops IS 'Stores Shopify shop OAuth tokens and AI configuration';
COMMENT ON COLUMN shopify_shops.shop_domain IS 'Unique Shopify domain (e.g., store.myshopify.com)';
COMMENT ON COLUMN shopify_shops.access_token IS 'OAuth access token for Shopify API (encrypt in production)';
COMMENT ON COLUMN shopify_shops.ai_system_prompt IS 'Shop-specific system prompt override (NULL uses default)';
COMMENT ON COLUMN shopify_shops.uninstalled_at IS 'Timestamp when app was uninstalled (NULL if active)';
