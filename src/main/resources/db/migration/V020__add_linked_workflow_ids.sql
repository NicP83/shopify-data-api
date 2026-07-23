-- Chatbot -> workflow delegation: comma-separated workflow IDs linked to the
-- chatbot, mirroring linked_agent_ids. Null/empty = feature disabled.
ALTER TABLE chatbot_configs ADD COLUMN IF NOT EXISTS linked_workflow_ids TEXT;
