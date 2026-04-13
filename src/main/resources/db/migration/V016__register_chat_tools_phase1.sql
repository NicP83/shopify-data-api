-- V016: Register Phase 1 chat tools in the tools table
-- These tools are auto-discovered by ChatToolRegistry via Spring @Component scanning,
-- but registering them in the database enables admin visibility and future configuration.

-- Register check_inventory tool
INSERT INTO tools (name, type, description, input_schema_json, handler_class, is_active, created_at)
VALUES (
    'check_inventory',
    'CHAT',
    'Check stock availability for a product. Returns inventory quantity and stock status with urgency cues.',
    '{"type":"object","properties":{"query":{"type":"string","description":"Product name or search term"},"sku":{"type":"string","description":"Optional specific SKU"}},"required":["query"]}'::jsonb,
    'com.shopify.api.handler.tool.CheckInventoryChatToolHandler',
    true,
    NOW()
) ON CONFLICT (name) DO NOTHING;

-- Register lookup_order tool
INSERT INTO tools (name, type, description, input_schema_json, handler_class, is_active, created_at)
VALUES (
    'lookup_order',
    'CHAT',
    'Look up order status. Requires both order number and customer email for security verification.',
    '{"type":"object","properties":{"order_number":{"type":"string","description":"Order number"},"email":{"type":"string","description":"Customer email"}},"required":["order_number","email"]}'::jsonb,
    'com.shopify.api.handler.tool.OrderLookupChatToolHandler',
    true,
    NOW()
) ON CONFLICT (name) DO NOTHING;

-- Register browse_products tool
INSERT INTO tools (name, type, description, input_schema_json, handler_class, is_active, created_at)
VALUES (
    'browse_products',
    'CHAT',
    'Browse and filter products by category, vendor, price range, or sort order.',
    '{"type":"object","properties":{"category":{"type":"string"},"vendor":{"type":"string"},"min_price":{"type":"number"},"max_price":{"type":"number"},"sort_by":{"type":"string"},"limit":{"type":"integer"}},"required":[]}'::jsonb,
    'com.shopify.api.handler.tool.BrowseProductsChatToolHandler',
    true,
    NOW()
) ON CONFLICT (name) DO NOTHING;
