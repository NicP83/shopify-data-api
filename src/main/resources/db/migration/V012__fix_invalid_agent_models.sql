-- V11__fix_invalid_agent_models.sql
-- Fix any invalid Claude model names in the agents table
--
-- Problem: Some agents may have been created with invalid model names like "claude-3-5-sonnet-20241022"
-- which don't exist in Anthropic's API, causing 404 errors.
--
-- Solution: Update all invalid model names to the default valid model

-- Update invalid model names to default
UPDATE agents
SET model_name = 'claude-3-7-sonnet-20250219'
WHERE model_name NOT IN (
  -- Claude 4.5 (Latest)
  'claude-sonnet-4-5-20250929',
  'claude-opus-4-1-20250805',
  'claude-haiku-4-5-20251001',

  -- Claude 4
  'claude-sonnet-4-20250514',
  'claude-opus-4-20250514',

  -- Claude 3.7 (Default)
  'claude-3-7-sonnet-20250219',

  -- Claude 3.5
  'claude-3-5-haiku-20241022',

  -- Claude 3
  'claude-3-opus-20240229',
  'claude-3-sonnet-20240229',
  'claude-3-haiku-20240307'
);

-- Log how many records were updated
-- This will be visible in Flyway migration logs
DO $$
DECLARE
    affected_count INTEGER;
BEGIN
    GET DIAGNOSTICS affected_count = ROW_COUNT;
    RAISE NOTICE 'Updated % agent(s) with invalid model names to claude-3-7-sonnet-20250219', affected_count;
END $$;
