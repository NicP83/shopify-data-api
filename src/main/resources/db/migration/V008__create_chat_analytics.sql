-- Create chat_analytics table for tracking AI interactions
-- Supports metrics, usage tracking, and performance monitoring

CREATE TABLE chat_analytics (
    id BIGSERIAL PRIMARY KEY,

    -- Shop association
    shop_id BIGINT NOT NULL REFERENCES shopify_shops(id) ON DELETE CASCADE,

    -- Session tracking
    session_id VARCHAR(255),
    user_identifier VARCHAR(255),

    -- Message details
    user_message TEXT NOT NULL,
    assistant_response TEXT,
    prompt_id BIGINT REFERENCES system_prompts(id) ON DELETE SET NULL,

    -- AI configuration used
    model_used VARCHAR(100),
    temperature_used DECIMAL(3,2),
    max_tokens_used INTEGER,

    -- Performance metrics
    response_time_ms INTEGER,
    tokens_used INTEGER,
    api_cost_usd DECIMAL(10,4),

    -- Tool usage
    tools_called TEXT[], -- Array of tool names called
    tool_results_count INTEGER DEFAULT 0,

    -- Quality metrics
    was_successful BOOLEAN DEFAULT true,
    error_message TEXT,
    user_feedback VARCHAR(50), -- 'positive', 'negative', 'neutral', null

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Additional metadata
    user_agent TEXT,
    ip_address VARCHAR(45),
    referer_url TEXT
);

-- Create indexes for analytics queries
CREATE INDEX idx_chat_analytics_shop ON chat_analytics(shop_id, created_at DESC);
CREATE INDEX idx_chat_analytics_session ON chat_analytics(session_id);
CREATE INDEX idx_chat_analytics_prompt ON chat_analytics(prompt_id);
CREATE INDEX idx_chat_analytics_model ON chat_analytics(model_used);
CREATE INDEX idx_chat_analytics_success ON chat_analytics(was_successful);
CREATE INDEX idx_chat_analytics_created ON chat_analytics(created_at DESC);

-- Create index for user feedback analysis
CREATE INDEX idx_chat_analytics_feedback ON chat_analytics(user_feedback) WHERE user_feedback IS NOT NULL;

-- Add comments
COMMENT ON TABLE chat_analytics IS 'Tracks AI chat interactions for analytics and monitoring';
COMMENT ON COLUMN chat_analytics.session_id IS 'Unique session identifier for grouping related conversations';
COMMENT ON COLUMN chat_analytics.user_identifier IS 'Anonymous user identifier (hashed)';
COMMENT ON COLUMN chat_analytics.tools_called IS 'Array of tool names (e.g., search_products) called during this interaction';
COMMENT ON COLUMN chat_analytics.api_cost_usd IS 'Estimated API cost for this interaction';
COMMENT ON COLUMN chat_analytics.user_feedback IS 'User feedback: positive, negative, neutral';
