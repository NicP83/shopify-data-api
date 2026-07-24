-- API keys for authenticating external systems, embeddable widgets, and the
-- admin UI. The raw key is shown once at creation; only its SHA-256 hash is
-- stored. Scopes are a CSV of grants (e.g. 'agents:run,workflows:run,admin').
-- bound_agent_id / bound_workflow_id + referer_allowlist scope restricted
-- "embed" keys to a single target and set of referers.
CREATE TABLE IF NOT EXISTS api_keys (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    key_prefix         VARCHAR(16)  NOT NULL,
    key_hash           VARCHAR(128) NOT NULL UNIQUE,
    scopes             TEXT         NOT NULL DEFAULT '',
    rate_limit_per_min INTEGER      NOT NULL DEFAULT 60,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    referer_allowlist  TEXT,
    bound_agent_id     BIGINT,
    bound_workflow_id  BIGINT,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at       TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_api_keys_key_hash ON api_keys(key_hash);
CREATE INDEX IF NOT EXISTS idx_api_keys_active ON api_keys(active);
