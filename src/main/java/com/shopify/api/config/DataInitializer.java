package com.shopify.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.model.agent.Tool;
import com.shopify.api.repository.agent.ToolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Data Initializer - Populates database with default tools on application startup
 *
 * Runs only once when tools table is empty
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final ToolRepository toolRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        // Only initialize if no tools exist
        if (toolRepository.count() == 0) {
            log.info("Initializing default tools...");

            initializeTools();

            log.info("Default tools initialized successfully");
        } else {
            log.info("Tools already exist in database, skipping initialization");
        }
    }

    private void initializeTools() {
        // Web Search Tool
        createTool(
            "web_search",
            "API",
            "Search the web for information using a search API",
            createSchema("query", "string", "Search query"),
            "com.shopify.api.handler.tool.WebSearchToolHandler"
        );

        // Get Products Tool
        createTool(
            "get_products",
            "SHOPIFY",
            "Retrieve product information from Shopify store",
            createSchema("limit", "integer", "Number of products to retrieve"),
            "com.shopify.api.handler.tool.GetProductsToolHandler"
        );

        // Search Products Tool
        createTool(
            "search_products",
            "SHOPIFY",
            "Search for products in Shopify store by query",
            createSchema("query", "string", "Product search query"),
            "com.shopify.api.handler.tool.SearchProductsToolHandler"
        );

        // Get Orders Tool
        createTool(
            "get_orders",
            "SHOPIFY",
            "Retrieve order information from Shopify store",
            createSchema("limit", "integer", "Number of orders to retrieve"),
            "com.shopify.api.handler.tool.GetOrdersToolHandler"
        );

        // Get Customers Tool
        createTool(
            "get_customers",
            "SHOPIFY",
            "Retrieve customer information from Shopify store",
            createSchema("limit", "integer", "Number of customers to retrieve"),
            "com.shopify.api.handler.tool.GetCustomersToolHandler"
        );

        // Database Query Tool
        createTool(
            "database_query",
            "DATABASE",
            "Execute a read-only database query",
            createSchema("sql", "string", "SQL query to execute (SELECT only)"),
            "com.shopify.api.handler.tool.DatabaseQueryToolHandler"
        );

        // HTTP Request Tool
        createTool(
            "http_request",
            "API",
            "Make HTTP requests to external APIs",
            createHttpRequestSchema(),
            "com.shopify.api.handler.tool.HttpRequestToolHandler"
        );

        // Text Analysis Tool
        createTool(
            "text_analysis",
            "SYSTEM",
            "Analyze text for sentiment, keywords, and entities",
            createSchema("text", "string", "Text to analyze"),
            "com.shopify.api.handler.tool.TextAnalysisToolHandler"
        );

        // MCP Tool - Call external MCP server tools
        createTool(
            "mcp_call",
            "MCP",
            "Call a tool from the MCP (Model Context Protocol) server",
            createMCPCallSchema(),
            "com.shopify.api.handler.tool.MCPCallToolHandler"
        );
    }

    private void createTool(String name, String type, String description, ObjectNode inputSchema, String handlerClass) {
        Tool tool = Tool.builder()
            .name(name)
            .type(type)
            .description(description)
            .inputSchemaJson(inputSchema)
            .handlerClass(handlerClass)
            .isActive(true)
            .build();

        toolRepository.save(tool);
        log.info("Created tool: {}", name);
    }

    private ObjectNode createSchema(String propertyName, String propertyType, String description) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        ObjectNode property = objectMapper.createObjectNode();
        property.put("type", propertyType);
        property.put("description", description);
        properties.set(propertyName, property);

        schema.set("properties", properties);

        var requiredArray = objectMapper.createArrayNode().add(propertyName);
        schema.set("required", requiredArray);

        return schema;
    }

    private ObjectNode createHttpRequestSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        // URL property
        ObjectNode urlProp = objectMapper.createObjectNode();
        urlProp.put("type", "string");
        urlProp.put("description", "The URL to make the request to");
        properties.set("url", urlProp);

        // Method property
        ObjectNode methodProp = objectMapper.createObjectNode();
        methodProp.put("type", "string");
        methodProp.put("description", "HTTP method (GET, POST, PUT, DELETE)");
        methodProp.put("enum", "GET");
        properties.set("method", methodProp);

        // Headers property
        ObjectNode headersProp = objectMapper.createObjectNode();
        headersProp.put("type", "object");
        headersProp.put("description", "HTTP headers");
        properties.set("headers", headersProp);

        schema.set("properties", properties);

        var requiredArray = objectMapper.createArrayNode().add("url").add("method");
        schema.set("required", requiredArray);

        return schema;
    }

    private ObjectNode createMCPCallSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        // Tool name property
        ObjectNode toolNameProp = objectMapper.createObjectNode();
        toolNameProp.put("type", "string");
        toolNameProp.put("description", "Name of the MCP tool to call");
        properties.set("tool_name", toolNameProp);

        // Arguments property
        ObjectNode argumentsProp = objectMapper.createObjectNode();
        argumentsProp.put("type", "object");
        argumentsProp.put("description", "Arguments to pass to the MCP tool");
        properties.set("arguments", argumentsProp);

        schema.set("properties", properties);

        var requiredArray = objectMapper.createArrayNode().add("tool_name").add("arguments");
        schema.set("required", requiredArray);

        return schema;
    }
}
