-- V019: Add research tools to the Product Enrichment Agent (id 23)
-- Adds a catalog lookup tool (real Shopify products incl. images/descriptions) and
-- the native Anthropic web_search server tool, then refines the agent prompt so it
-- grounds titles/descriptions/images on real data instead of inventing them.

-- 1. Catalog lookup tool. Reuses the existing ProductSearchToolHandler @Component,
-- which returns matching catalog products (title, description, vendor, variants, images).
INSERT INTO tools (name, type, description, input_schema_json, handler_class, is_active, created_at)
VALUES (
    'lookup_catalog_product',
    'AGENT',
    'Search the Hearns Hobbies Shopify catalog by product name or keywords. Returns matching products including title, description, vendor, variants/SKUs and image URLs. Use this to ground a product name/description and to source real image URLs.',
    '{"type":"object","properties":{"query":{"type":"string","description":"Product name or keywords to search the catalog (search by name, not the raw supplier code)"},"first":{"type":"integer","description":"Max results (default 5)"}},"required":["query"]}'::jsonb,
    'com.shopify.api.service.tool.ProductSearchToolHandler',
    true,
    NOW()
) ON CONFLICT (name) DO NOTHING;

-- 2. Native Anthropic web_search server tool. For BUILTIN tools the input_schema_json
-- holds the full server-tool definition, emitted verbatim to the Anthropic API;
-- handler_class is unused because Anthropic executes the tool itself.
INSERT INTO tools (name, type, description, input_schema_json, handler_class, is_active, created_at)
VALUES (
    'web_search',
    'BUILTIN',
    'Native Anthropic web search. Researches real product details (brand, scale, specs, official description) and image URLs for products not found in the catalog.',
    '{"type":"web_search_20250305","name":"web_search","max_uses":5}'::jsonb,
    'native:web_search_20250305',
    true,
    NOW()
) ON CONFLICT (name) DO NOTHING;

-- 3. Attach both tools to the Product Enrichment Agent (id 23).
INSERT INTO agent_tools (agent_id, tool_id, created_at)
SELECT 23, t.id, NOW()
FROM tools t
WHERE t.name = 'lookup_catalog_product'
  AND EXISTS (SELECT 1 FROM agents WHERE id = 23)
ON CONFLICT (agent_id, tool_id) DO NOTHING;

INSERT INTO agent_tools (agent_id, tool_id, created_at)
SELECT 23, t.id, NOW()
FROM tools t
WHERE t.name = 'web_search'
  AND EXISTS (SELECT 1 FROM agents WHERE id = 23)
ON CONFLICT (agent_id, tool_id) DO NOTHING;

-- 4. Refine the agent: more tokens for tool use + reasoning, grounded prompt with a
-- confidence flag and explicit image sourcing.
UPDATE agents SET
    max_tokens = 2500,
    updated_at = NOW(),
    system_prompt = 'You are the Product Enrichment Agent for Hearns Hobbies, a hobby retailer (paints, radio control, model railways, scale model kits, Warhammer, wargaming, modelling tools).

You enrich incoming stock products. The task is a JSON object with a "mode" of either "create" or "review".

You have two tools:
- lookup_catalog_product: search the Hearns Shopify catalog by product NAME or keywords (not the raw supplier code). Returns matching products with title, description, vendor and image URLs.
- web_search: research the real product on the web (brand, scale, specs, official description, image URLs).

For mode "create", given {code, description, qty, price, supplier}:
1. First call lookup_catalog_product using the product name/keywords from the description to check whether Hearns already stocks the same or a closely matching product. If a clear match is found, ground the name and description on it and take real image URLs from it.
2. If there is no catalog match or the product is unfamiliar, use web_search to identify the real product and find image URLs.
3. Never invent specifications. If you cannot verify a detail, leave it out, keep the description factual and general, and set confidence to "low".
4. Return ONLY this JSON:
{"name":"<clean product name>","full_description":"<rich 2-4 sentence marketing description>","category":"<one of: Paint, Radio Control, Model Railways, Scale Kits, Warhammer, Wargaming, Tools, Other>","specs":{"brand":"<brand if known>"},"suggested_images":["<real image url>"],"confidence":"<high|medium|low>"}

For mode "review", given {code, existing_record, incoming:{description,qty,price}}, compare and return ONLY this JSON:
{"suggested_changes":[{"field":"<field>","current":<current value>,"suggested":<suggested value>,"reason":"<short reason>"}]}

Rules: Your FINAL message must be ONLY valid minified JSON for the requested mode. No markdown, no code fences, no commentary before or after. Use your hobby expertise together with the tools for accurate names, descriptions, categories, specs and images. Only include image URLs you actually found via a tool; never fabricate URLs. If reviewing and nothing should change, return {"suggested_changes":[]}.'
WHERE id = 23;
