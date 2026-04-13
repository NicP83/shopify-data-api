-- V016: Register Phase 1 chat tools in the tools table
-- These tools are auto-discovered by ChatToolRegistry via Spring @Component scanning,
-- but registering them in the database enables admin visibility and future configuration.

-- Register check_inventory tool
INSERT INTO tools (name, description, handler_class, input_schema, is_active, created_at, updated_at)
VALUES (
    'check_inventory',
    'Check stock availability for a product. Returns inventory quantity and stock status with urgency cues.',
    'com.shopify.api.handler.tool.CheckInventoryChatToolHandler',
    '{"type":"object","properties":{"query":{"type":"string","description":"Product name or search term"},"sku":{"type":"string","description":"Optional specific SKU"}},"required":["query"]}',
    true,
    NOW(),
    NOW()
) ON CONFLICT (name) DO NOTHING;

-- Register lookup_order tool
INSERT INTO tools (name, description, handler_class, input_schema, is_active, created_at, updated_at)
VALUES (
    'lookup_order',
    'Look up order status. Requires both order number and customer email for security verification.',
    'com.shopify.api.handler.tool.OrderLookupChatToolHandler',
    '{"type":"object","properties":{"order_number":{"type":"string","description":"Order number"},"email":{"type":"string","description":"Customer email"}},"required":["order_number","email"]}',
    true,
    NOW(),
    NOW()
) ON CONFLICT (name) DO NOTHING;

-- Register browse_products tool
INSERT INTO tools (name, description, handler_class, input_schema, is_active, created_at, updated_at)
VALUES (
    'browse_products',
    'Browse and filter products by category, vendor, price range, or sort order.',
    'com.shopify.api.handler.tool.BrowseProductsChatToolHandler',
    '{"type":"object","properties":{"category":{"type":"string"},"vendor":{"type":"string"},"min_price":{"type":"number"},"max_price":{"type":"number"},"sort_by":{"type":"string"},"limit":{"type":"integer"}},"required":[]}',
    true,
    NOW(),
    NOW()
) ON CONFLICT (name) DO NOTHING;
