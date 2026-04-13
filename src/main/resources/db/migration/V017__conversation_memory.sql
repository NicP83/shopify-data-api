-- V017: Conversation memory tables for multi-session customer experience

-- Conversation sessions track chat sessions across visits
CREATE TABLE IF NOT EXISTS conversation_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    customer_email VARCHAR(255),
    customer_first_name VARCHAR(255),
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_active_at TIMESTAMP NOT NULL DEFAULT NOW(),
    message_count INTEGER NOT NULL DEFAULT 0,
    summary_text TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_conv_sessions_session_id ON conversation_sessions(session_id);
CREATE INDEX IF NOT EXISTS idx_conv_sessions_customer_email ON conversation_sessions(customer_email);
CREATE INDEX IF NOT EXISTS idx_conv_sessions_last_active ON conversation_sessions(last_active_at);

-- Individual messages within a session
CREATE TABLE IF NOT EXISTS conversation_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL REFERENCES conversation_sessions(session_id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    tool_calls JSONB,
    tokens_used INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_conv_messages_session ON conversation_messages(session_id);
CREATE INDEX IF NOT EXISTS idx_conv_messages_created ON conversation_messages(created_at);

-- Customer preferences learned over time
CREATE TABLE IF NOT EXISTS customer_preferences (
    id BIGSERIAL PRIMARY KEY,
    customer_email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255),
    preferred_categories TEXT,
    skill_level VARCHAR(50),
    interests JSONB,
    total_sessions INTEGER NOT NULL DEFAULT 0,
    last_visit TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cust_prefs_email ON customer_preferences(customer_email);

-- Register Phase 2 tools
INSERT INTO tools (name, type, description, input_schema_json, handler_class, is_active, created_at)
VALUES
    ('get_stock_insights', 'CHAT', 'Get sales velocity and trend insights for a product by SKU',
     '{"type":"object","properties":{"sku":{"type":"string"}},"required":["sku"]}'::jsonb,
     'com.shopify.api.handler.tool.GetStockInsightsChatToolHandler',
     true, NOW()),
    ('get_complementary_products', 'CHAT', 'Get complementary product suggestions for a given product',
     '{"type":"object","properties":{"product_title":{"type":"string"},"product_type":{"type":"string"},"vendor":{"type":"string"}},"required":["product_title"]}'::jsonb,
     'com.shopify.api.handler.tool.GetComplementaryProductsChatToolHandler',
     true, NOW()),
    ('get_customer_history', 'CHAT', 'Look up customer purchase history by email',
     '{"type":"object","properties":{"email":{"type":"string"}},"required":["email"]}'::jsonb,
     'com.shopify.api.handler.tool.CustomerHistoryChatToolHandler',
     true, NOW()),
    ('get_promotions', 'CHAT', 'Find products currently on sale or promotion',
     '{"type":"object","properties":{"category":{"type":"string"},"limit":{"type":"integer"}},"required":[]}'::jsonb,
     'com.shopify.api.handler.tool.GetPromotionsChatToolHandler',
     true, NOW())
ON CONFLICT (name) DO NOTHING;
