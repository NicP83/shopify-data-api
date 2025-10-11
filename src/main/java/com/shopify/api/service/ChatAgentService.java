package com.shopify.api.service;

import com.shopify.api.model.ChatMessage;
import com.shopify.api.model.ChatRequest;
import com.shopify.api.model.ChatbotConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class ChatAgentService {

    private static final Logger logger = LoggerFactory.getLogger(ChatAgentService.class);

    @Value("${anthropic.api.key:}")
    private String anthropicApiKey;

    @Value("${anthropic.api.version:2023-06-01}")
    private String anthropicApiVersion;

    @Value("${anthropic.model:claude-3-5-sonnet-20241022}")
    private String anthropicModel;

    @Value("${anthropic.max-tokens:1024}")
    private int maxTokens;

    @Value("${anthropic.temperature:0.7}")
    private double temperature;

    @Value("${anthropic.system-prompt-file:classpath:prompts/system-prompt.txt}")
    private String systemPromptFile;

    private final WebClient webClient;
    private final ProductService productService;
    private final ChatbotConfigService chatbotConfigService;
    private final ObjectMapper objectMapper;
    private final String shopUrl;
    private final ResourceLoader resourceLoader;
    private String systemPromptTemplate;

    @Autowired
    public ChatAgentService(WebClient.Builder webClientBuilder,
                           ProductService productService,
                           ChatbotConfigService chatbotConfigService,
                           ResourceLoader resourceLoader,
                           @Value("${shopify.shop-url}") String shopUrl) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.anthropic.com/v1")
                .build();
        this.productService = productService;
        this.chatbotConfigService = chatbotConfigService;
        this.resourceLoader = resourceLoader;
        this.objectMapper = new ObjectMapper();
        this.shopUrl = shopUrl;
    }

    @PostConstruct
    public void loadSystemPrompt() {
        try {
            Resource resource = resourceLoader.getResource(systemPromptFile);
            systemPromptTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            logger.info("Loaded system prompt from: {}", systemPromptFile);
        } catch (IOException e) {
            logger.error("Failed to load system prompt file: {}", e.getMessage());
            // Fall back to default prompt
            systemPromptTemplate = """
                    You are a helpful sales and customer support assistant for an online Gundam model kit store.
                    Store URL: {SHOP_URL}
                    """;
        }
    }

    /**
     * Process a chat message and return AI response
     * Supports Claude tool use for product search
     */
    public Mono<ChatMessage> processChat(ChatRequest chatRequest) {
        logger.info("Processing chat message: {}", chatRequest.getMessage());

        // Check if API key is configured
        if (anthropicApiKey == null || anthropicApiKey.trim().isEmpty()) {
            logger.warn("Anthropic API key not configured, returning mock response");
            return Mono.just(createMockResponse(chatRequest.getMessage()));
        }

        // Build the system prompt for the AI
        String systemPrompt = buildSystemPrompt();

        // Build the messages array for Claude API
        ArrayNode messages = buildMessagesArray(chatRequest);

        // Call Claude API with tool support
        return callClaudeWithTools(systemPrompt, messages, 0);
    }

    /**
     * Call Claude API with tool support (recursive for multi-turn conversations)
     * maxIterations prevents infinite loops
     */
    private Mono<ChatMessage> callClaudeWithTools(String systemPrompt, ArrayNode messages, int iteration) {
        if (iteration >= 5) {
            logger.warn("Max tool use iterations reached");
            return Mono.just(new ChatMessage("assistant", "I apologize, but I'm having trouble completing your request. Please try rephrasing."));
        }

        // Create the request body for Claude API
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", anthropicModel);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);
        requestBody.put("system", systemPrompt);
        requestBody.set("messages", messages);

        // Add tools array if product search is enabled
        ChatbotConfig config = chatbotConfigService.getConfig();
        if (config.isEnableProductSearch()) {
            requestBody.set("tools", buildToolsArray(config));
        }

        // Call Claude API
        return webClient.post()
                .uri("/messages")
                .header("x-api-key", anthropicApiKey)
                .header("anthropic-version", anthropicApiVersion)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(response -> handleClaudeResponse(response, systemPrompt, messages, iteration))
                .onErrorResume(error -> {
                    logger.error("Error calling Claude API: {}", error.getMessage());
                    return Mono.just(createErrorResponse());
                });
    }

    /**
     * Handle Claude API response - either extract message or handle tool use
     */
    private Mono<ChatMessage> handleClaudeResponse(JsonNode response, String systemPrompt, ArrayNode messages, int iteration) {
        try {
            String stopReason = response.get("stop_reason").asText();
            JsonNode content = response.get("content");

            if ("tool_use".equals(stopReason) && content != null && content.isArray()) {
                // Claude wants to use a tool
                logger.info("Claude requested tool use");
                return handleToolUseAndContinue(response, systemPrompt, messages, iteration);
            } else {
                // Regular text response
                return Mono.just(extractAssistantMessage(response));
            }
        } catch (Exception e) {
            logger.error("Error handling Claude response: {}", e.getMessage());
            return Mono.just(createErrorResponse());
        }
    }

    /**
     * Execute tool calls and continue conversation with results
     */
    private Mono<ChatMessage> handleToolUseAndContinue(JsonNode response, String systemPrompt, ArrayNode messages, int iteration) {
        try {
            // Add assistant's response with tool_use to messages
            ObjectNode assistantMessage = objectMapper.createObjectNode();
            assistantMessage.put("role", "assistant");
            assistantMessage.set("content", response.get("content"));
            messages.add(assistantMessage);

            // Execute all tool calls and build tool results
            JsonNode content = response.get("content");
            ArrayNode toolResults = objectMapper.createArrayNode();

            for (JsonNode block : content) {
                if ("tool_use".equals(block.get("type").asText())) {
                    String toolName = block.get("name").asText();
                    String toolUseId = block.get("id").asText();
                    JsonNode toolInput = block.get("input");

                    logger.info("Executing tool: {} with input: {}", toolName, toolInput);

                    // Execute the tool
                    String toolResult = executeToolCall(toolName, toolInput);

                    // Build tool_result block
                    ObjectNode toolResultBlock = objectMapper.createObjectNode();
                    toolResultBlock.put("type", "tool_result");
                    toolResultBlock.put("tool_use_id", toolUseId);
                    toolResultBlock.put("content", toolResult);
                    toolResults.add(toolResultBlock);
                }
            }

            // Add user message with tool results
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.set("content", toolResults);
            messages.add(userMessage);

            // Continue conversation with tool results
            return callClaudeWithTools(systemPrompt, messages, iteration + 1);

        } catch (Exception e) {
            logger.error("Error handling tool use: {}", e.getMessage());
            return Mono.just(createErrorResponse());
        }
    }

    /**
     * Execute a tool call and return results
     */
    private String executeToolCall(String toolName, JsonNode input) {
        try {
            if ("search_products".equals(toolName)) {
                String query = input.get("query").asText();
                int maxResults = chatbotConfigService.getConfig().getMaxSearchResults();

                logger.info("Searching products with query: {} (max: {})", query, maxResults);

                // Search products using ProductService
                Map<String, Object> results = productService.searchProducts(query, maxResults);

                // Format results for Claude
                return objectMapper.writeValueAsString(results.get("data"));
            }

            return "Unknown tool: " + toolName;

        } catch (Exception e) {
            logger.error("Error executing tool {}: {}", toolName, e.getMessage());
            return "Error executing search: " + e.getMessage();
        }
    }

    /**
     * Build tools array for Claude API
     */
    private ArrayNode buildToolsArray(ChatbotConfig config) {
        ArrayNode tools = objectMapper.createArrayNode();

        // Define search_products tool
        ObjectNode searchTool = objectMapper.createObjectNode();
        searchTool.put("name", "search_products");
        searchTool.put("description", "Search the product catalog to find items matching a query. " +
                "Use this to find specific products, check availability, get prices, or browse categories. " +
                "Returns product details including title, description, price, SKU, variants, and image URL.");

        // Define input schema
        ObjectNode inputSchema = objectMapper.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        ObjectNode queryProperty = objectMapper.createObjectNode();
        queryProperty.put("type", "string");
        queryProperty.put("description", "Search query to find products (searches title, description, tags, vendor)");
        properties.set("query", queryProperty);

        inputSchema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("query");
        inputSchema.set("required", required);

        searchTool.set("input_schema", inputSchema);
        tools.add(searchTool);

        return tools;
    }

    /**
     * Build system prompt that defines the AI's role and capabilities
     * Dynamically generates prompt from ChatbotConfig
     */
    private String buildSystemPrompt() {
        ChatbotConfig config = chatbotConfigService.getConfig();

        StringBuilder prompt = new StringBuilder();

        // Identity
        prompt.append("You are a helpful sales and customer support assistant for ");
        prompt.append(config.getStoreName());
        prompt.append(", ");
        prompt.append(config.getStoreDescription());
        prompt.append(".\n\n");

        // Store URL
        prompt.append("Store URL: https://").append(shopUrl).append("\n\n");

        // What we sell
        prompt.append("WHAT WE SELL:\n");
        prompt.append("We sell: ").append(config.getStoreCategories()).append("\n");
        prompt.append(config.getScopeInstructions()).append("\n\n");

        // Rules
        prompt.append("IMPORTANT RULES:\n");
        if (config.isRequireSearchBeforeRecommendation()) {
            prompt.append("- ALWAYS search the catalog before recommending products\n");
        }
        prompt.append("- ONLY recommend products found in our catalog\n");
        prompt.append("- When we don't carry something: ").append(config.getOutOfScopeResponse()).append("\n\n");

        // Tools (if enabled)
        if (config.isEnableProductSearch()) {
            prompt.append("AVAILABLE TOOLS:\n");
            prompt.append("You can search products by calling the search_products function.\n");
            prompt.append("Search returns up to ").append(config.getMaxSearchResults()).append(" results.\n\n");
        }

        // Response style
        prompt.append("RESPONSE STYLE:\n");
        prompt.append("- Tone: ").append(config.getToneOfVoice()).append("\n");
        if (config.isIncludeCartLinks()) {
            prompt.append("- Generate 'Add to Cart' links: https://").append(shopUrl).append("/cart/{VARIANT_ID}:1\n");
        }
        if (config.isShowPrices()) {
            prompt.append("- Always include product prices\n");
        }
        if (config.isShowSkus()) {
            prompt.append("- Include SKU information\n");
        }

        // Custom instructions
        if (config.getCustomInstructions() != null && !config.getCustomInstructions().isEmpty()) {
            prompt.append("\nADDITIONAL INSTRUCTIONS:\n");
            prompt.append(config.getCustomInstructions()).append("\n");
        }

        return prompt.toString();
    }

    /**
     * Get the currently generated system prompt (for preview/debugging)
     */
    public String getGeneratedSystemPrompt() {
        return buildSystemPrompt();
    }

    // Getters and setters for runtime configuration
    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
        logger.info("Max tokens updated to: {}", maxTokens);
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
        logger.info("Temperature updated to: {}", temperature);
    }

    public String getAnthropicModel() {
        return anthropicModel;
    }

    public void setAnthropicModel(String model) {
        this.anthropicModel = model;
        logger.info("Model updated to: {}", model);
    }

    /**
     * Build messages array from conversation history
     */
    private ArrayNode buildMessagesArray(ChatRequest chatRequest) {
        ArrayNode messages = objectMapper.createArrayNode();

        // Add conversation history
        if (chatRequest.getConversationHistory() != null) {
            for (ChatMessage msg : chatRequest.getConversationHistory()) {
                ObjectNode messageNode = objectMapper.createObjectNode();
                messageNode.put("role", msg.getRole());
                messageNode.put("content", msg.getContent());
                messages.add(messageNode);
            }
        }

        // Add current user message
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", chatRequest.getMessage());
        messages.add(userMessage);

        return messages;
    }

    /**
     * Extract assistant message from Claude API response
     * Handles both text-only and mixed content responses
     */
    private ChatMessage extractAssistantMessage(JsonNode response) {
        try {
            JsonNode content = response.get("content");
            if (content != null && content.isArray() && content.size() > 0) {
                // Build response from all text blocks
                StringBuilder fullText = new StringBuilder();
                for (JsonNode block : content) {
                    if ("text".equals(block.get("type").asText())) {
                        String text = block.get("text").asText();
                        fullText.append(text);
                    }
                }

                if (fullText.length() > 0) {
                    return new ChatMessage("assistant", fullText.toString());
                }
            }
            return createErrorResponse();
        } catch (Exception e) {
            logger.error("Error extracting assistant message: {}", e.getMessage());
            return createErrorResponse();
        }
    }

    /**
     * Create a mock response when API key is not configured
     */
    private ChatMessage createMockResponse(String userMessage) {
        String response = "Hello! I'm your Gundam store assistant. I'd love to help you find the perfect model kit! " +
                "I can search our catalog, provide product details, and generate direct purchase links. " +
                "To enable full AI capabilities, please configure the ANTHROPIC_API_KEY environment variable.";

        // If user is asking about a product, provide a helpful mock response
        if (userMessage.toLowerCase().contains("gundam") ||
            userMessage.toLowerCase().contains("model") ||
            userMessage.toLowerCase().contains("product")) {
            response = "I can help you search for Gundam models! However, the AI assistant is currently in demo mode. " +
                    "Please use the Product Search page to browse our full catalog, or configure the ANTHROPIC_API_KEY " +
                    "environment variable to enable full AI-powered product recommendations and cart link generation.";
        }

        return new ChatMessage("assistant", response);
    }

    /**
     * Create error response when API call fails
     */
    private ChatMessage createErrorResponse() {
        return new ChatMessage("assistant",
            "I apologize, but I'm having trouble processing your request at the moment. " +
            "Please try again or use the Product Search page to browse our catalog.");
    }

    /**
     * Search products based on AI analysis (called by AI if needed)
     * This will be enhanced in future to support function calling
     */
    public String searchProductsForAI(String query) {
        try {
            // Search products using existing ProductService
            Map<String, Object> results = productService.searchProducts(query, 5);

            // Format results for AI consumption
            if (results.get("data") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) results.get("data");
                return objectMapper.writeValueAsString(data);
            }
            return "No products found.";
        } catch (Exception e) {
            logger.error("Error searching products for AI: {}", e.getMessage());
            return "Error searching products.";
        }
    }
}
