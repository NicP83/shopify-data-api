-- Add MCP tool for calling external MCP server
INSERT INTO agent_tools (name, type, description, input_schema_json, handler_class, is_active, created_at, updated_at)
VALUES (
    'mcp_call',
    'MCP',
    'Call a tool from the MCP (Model Context Protocol) server',
    '{
        "type": "object",
        "properties": {
            "tool_name": {
                "type": "string",
                "description": "Name of the MCP tool to call"
            },
            "arguments": {
                "type": "object",
                "description": "Arguments to pass to the MCP tool"
            }
        },
        "required": ["tool_name", "arguments"]
    }'::jsonb,
    'com.shopify.api.handler.tool.MCPCallToolHandler',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (name) DO NOTHING;
