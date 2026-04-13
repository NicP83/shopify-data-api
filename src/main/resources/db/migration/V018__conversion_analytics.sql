-- V018: Extend chat analytics with conversion tracking
-- and register Phase 3 tools

-- Add conversion tracking columns to chat_analytics
ALTER TABLE chat_analytics ADD COLUMN IF NOT EXISTS tools_used TEXT;
ALTER TABLE chat_analytics ADD COLUMN IF NOT EXISTS products_recommended TEXT;
ALTER TABLE chat_analytics ADD COLUMN IF NOT EXISTS add_to_cart_clicked BOOLEAN DEFAULT FALSE;
ALTER TABLE chat_analytics ADD COLUMN IF NOT EXISTS conversion_event VARCHAR(100);

-- Register Phase 3 tools
INSERT INTO tools (name, description, handler_class, input_schema, is_active, created_at, updated_at)
VALUES
    ('compare_products', 'Compare two or three products side by side',
     'com.shopify.api.handler.tool.CompareProductsChatToolHandler',
     '{"type":"object","properties":{"product_queries":{"type":"array","items":{"type":"string"}}},"required":["product_queries"]}',
     true, NOW(), NOW())
ON CONFLICT (name) DO NOTHING;
