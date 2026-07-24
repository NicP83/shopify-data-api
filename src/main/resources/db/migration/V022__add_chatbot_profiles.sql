-- Named chatbot personas ("assistants"). A profile is a chatbot_configs row
-- with a unique slug + display name, carrying its own prompt, linked agents,
-- linked workflows, and model. The global default row keeps slug NULL and is
-- unaffected (the storefront continues to use it).
ALTER TABLE chatbot_configs ADD COLUMN IF NOT EXISTS slug VARCHAR(100);
ALTER TABLE chatbot_configs ADD COLUMN IF NOT EXISTS display_name VARCHAR(255);

-- Unique slug among named profiles (NULLs allowed for the global/shop rows).
CREATE UNIQUE INDEX IF NOT EXISTS uq_chatbot_configs_slug
    ON chatbot_configs (slug) WHERE slug IS NOT NULL;
