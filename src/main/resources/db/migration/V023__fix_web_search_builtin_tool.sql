-- V023: Repair the web_search tool row so it is the native Anthropic server tool.
--
-- Why: older databases (including production) got a 'web_search' row from the
-- original DataInitializer seed: type 'API', handler_class
-- 'com.shopify.api.handler.tool.WebSearchToolHandler' (a class that never existed)
-- and a client-style {query} schema. V019 inserted the correct BUILTIN row with
-- ON CONFLICT (name) DO NOTHING, so on those databases the broken row survived.
-- Agents with web_search attached (e.g. agent 15, agent 23) then offered Claude a
-- client tool it could call but the app could not run:
--   "Could not load tool handler 'com.shopify.api.handler.tool.WebSearchToolHandler'"
--
-- Effect: rewrites that one row in place to match V019 (same id, so agent_tools
-- links are kept; is_active is left as it is). Guarded: a no-op if the row is
-- already BUILTIN with the native handler, or if no web_search row exists.
--
-- Rollback (data only, not normally needed): AgentExecutionService already falls
-- back to running without server tools if Anthropic rejects web_search. To disable
-- web search entirely: UPDATE tools SET is_active = false WHERE name = 'web_search';

UPDATE tools
SET type              = 'BUILTIN',
    description       = 'Native Anthropic web search. Researches real product details (brand, scale, specs, official description) and image URLs for products not found in the catalog.',
    input_schema_json = '{"type":"web_search_20250305","name":"web_search","max_uses":5}'::jsonb,
    handler_class     = 'native:web_search_20250305'
WHERE name = 'web_search'
  AND (type IS DISTINCT FROM 'BUILTIN'
       OR handler_class IS DISTINCT FROM 'native:web_search_20250305');
