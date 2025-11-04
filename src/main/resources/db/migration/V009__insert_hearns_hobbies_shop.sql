-- Register Hearn's Hobbies shop for AI chat functionality
-- This shop uses custom app authentication (not OAuth)

INSERT INTO shopify_shops (
    shop_domain,
    access_token,
    scope,
    is_active,
    ai_enabled,
    ai_model,
    ai_temperature,
    ai_max_tokens,
    analytics_enabled,
    installed_at,
    shop_name,
    shop_email,
    currency,
    timezone
) VALUES (
    'hearnshobbies.myshopify.com',
    '${SHOPIFY_ACCESS_TOKEN}',  -- Will be replaced by environment variable at runtime
    'read_products,write_script_tags',
    true,
    true,
    'claude-3-7-sonnet-20250219',
    0.7,
    1024,
    true,
    NOW(),
    'Hearn''s Hobbies',
    'info@hearnshobbies.com',
    'USD',
    'America/New_York'
)
ON CONFLICT (shop_domain)
DO UPDATE SET
    is_active = true,
    ai_enabled = true,
    uninstalled_at = NULL,
    access_token = EXCLUDED.access_token,
    scope = EXCLUDED.scope;
