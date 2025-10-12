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

            logger.debug("Claude response - stop_reason: {}, content blocks: {}",
                stopReason, content != null && content.isArray() ? content.size() : 0);

            if ("tool_use".equals(stopReason) && content != null && content.isArray()) {
                // Claude wants to use a tool
                logger.info("Claude requested tool use - processing tool calls");
                return handleToolUseAndContinue(response, systemPrompt, messages, iteration);
            } else if ("end_turn".equals(stopReason)) {
                // Regular text response
                logger.debug("Claude completed turn - extracting assistant message");
                return Mono.just(extractAssistantMessage(response));
            } else {
                // Unexpected stop reason
                logger.warn("Unexpected stop_reason from Claude: {}", stopReason);
                return Mono.just(extractAssistantMessage(response));
            }
        } catch (Exception e) {
            logger.error("Error handling Claude response: {}", e.getMessage(), e);
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

            // Execute all tool calls reactively and build tool results
            JsonNode content = response.get("content");
            ArrayNode toolResults = objectMapper.createArrayNode();

            // Collect all tool calls that need to be executed
            List<Mono<ObjectNode>> toolCallMonos = new java.util.ArrayList<>();

            for (JsonNode block : content) {
                if ("tool_use".equals(block.get("type").asText())) {
                    String toolName = block.get("name").asText();
                    String toolUseId = block.get("id").asText();
                    JsonNode toolInput = block.get("input");

                    logger.info("Executing tool: {} with input: {}", toolName, toolInput);

                    // Execute the tool reactively and map to tool_result block
                    Mono<ObjectNode> toolResultMono = executeToolCallReactive(toolName, toolInput)
                            .map(toolResult -> {
                                // Build tool_result block
                                ObjectNode toolResultBlock = objectMapper.createObjectNode();
                                toolResultBlock.put("type", "tool_result");
                                toolResultBlock.put("tool_use_id", toolUseId);
                                toolResultBlock.put("content", toolResult);
                                return toolResultBlock;
                            });

                    toolCallMonos.add(toolResultMono);
                }
            }

            // Execute all tool calls and combine results
            return Mono.zip(toolCallMonos, results -> {
                for (Object result : results) {
                    toolResults.add((ObjectNode) result);
                }
                return toolResults;
            }).flatMap(completedToolResults -> {
                // Add user message with tool results
                ObjectNode userMessage = objectMapper.createObjectNode();
                userMessage.put("role", "user");
                userMessage.set("content", completedToolResults);
                messages.add(userMessage);

                // Continue conversation with tool results
                return callClaudeWithTools(systemPrompt, messages, iteration + 1);
            });

        } catch (Exception e) {
            logger.error("Error handling tool use: {}", e.getMessage());
            return Mono.just(createErrorResponse());
        }
    }

    /**
     * Execute a tool call reactively and return results
     */
    private Mono<String> executeToolCallReactive(String toolName, JsonNode input) {
        try {
            if ("search_products".equals(toolName)) {
                String query = input.get("query").asText();
                int maxResults = chatbotConfigService.getConfig().getMaxSearchResults();

                logger.info("=== EXECUTING TOOL: search_products ===");
                logger.info("Query: '{}', Max Results: {}", query, maxResults);

                // Search products using ProductService reactively
                return productService.searchProductsReactive(query, maxResults)
                        .map(results -> {
                            try {
                                // Log results summary
                                logger.info("Search completed - Results type: {}",
                                    results != null ? results.getClass().getSimpleName() : "null");

                                // Format results for Claude
                                String jsonResult = objectMapper.writeValueAsString(results);
                                logger.debug("Returning {} characters of JSON to Claude", jsonResult.length());

                                return jsonResult;
                            } catch (Exception e) {
                                logger.error("Error formatting search results: {}", e.getMessage(), e);
                                return "Error formatting results: " + e.getMessage();
                            }
                        })
                        .onErrorResume(e -> {
                            logger.error("Error executing tool {}: {}", toolName, e.getMessage(), e);
                            String errorMsg = "Error executing search: " + e.getMessage();
                            logger.error("Returning error to Claude: {}", errorMsg);
                            return Mono.just(errorMsg);
                        });
            }

            logger.warn("Unknown tool requested: {}", toolName);
            return Mono.just("Unknown tool: " + toolName);

        } catch (Exception e) {
            logger.error("Error in executeToolCallReactive {}: {}", toolName, e.getMessage(), e);
            return Mono.just("Error: " + e.getMessage());
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

        // Tools FIRST - Make it crystal clear how to use them
        if (config.isEnableProductSearch()) {
            prompt.append("=== YOUR PRIMARY TOOL ===\n");
            prompt.append("You have access to a search_products function that searches our product catalog.\n");
            prompt.append("Search returns up to ").append(config.getMaxSearchResults()).append(" results with:\n");
            prompt.append("- Product title, description, and handle\n");
            prompt.append("- Price and SKU information\n");
            prompt.append("- Variant IDs for generating cart links\n");
            prompt.append("- Image URLs\n\n");

            prompt.append("WHEN TO USE search_products:\n");
            prompt.append("- User asks about ANY product (\"do you have...\", \"I need...\", \"show me...\")\n");
            prompt.append("- User asks for recommendations\n");
            prompt.append("- User asks about pricing or availability\n");
            prompt.append("- BEFORE making ANY product recommendation\n\n");

            prompt.append("HOW TO USE search_products:\n");
            prompt.append("1. Extract key terms from user's question (e.g., \"white paint\" -> query: \"white paint\")\n");
            prompt.append("2. Call search_products with the query\n");
            prompt.append("3. Wait for results\n");
            prompt.append("4. Present products from search results ONLY\n\n");

            prompt.append("EXAMPLE:\n");
            prompt.append("User: \"I need white acrylic paint\"\n");
            prompt.append("You: [Call search_products with query=\"white acrylic paint\"]\n");
            prompt.append("You: [Receive results and present them]\n\n");

            prompt.append("SHOWING MORE RESULTS:\n");
            prompt.append("- If user asks for \"more results\" or \"show me more\", call search_products again\n");
            prompt.append("- You can search with the same query or refine it based on user feedback\n");
            prompt.append("- Inform user: \"Here are ").append(config.getMaxSearchResults()).append(" more results...\"\n");
            prompt.append("- Consider asking if they want to narrow down the search\n\n");
        }

        // What we sell
        prompt.append("=== WHAT WE SELL ===\n");
        prompt.append("We sell: ").append(config.getStoreCategories()).append("\n");
        prompt.append(config.getScopeInstructions()).append("\n\n");

        // Rules
        prompt.append("=== IMPORTANT RULES ===\n");
        if (config.isRequireSearchBeforeRecommendation()) {
            prompt.append("1. ALWAYS use search_products BEFORE recommending any product\n");
            prompt.append("2. NEVER guess or make up product names - search first\n");
        }
        prompt.append("3. ONLY recommend products found in search results\n");
        prompt.append("4. When we don't carry something: ").append(config.getOutOfScopeResponse()).append("\n\n");

        // Response style
        prompt.append("=== RESPONSE STYLE ===\n");
        prompt.append("- Tone: ").append(config.getToneOfVoice()).append("\n");
        if (config.isIncludeCartLinks()) {
            prompt.append("- Generate 'Add to Cart' links: https://").append(shopUrl).append("/cart/{VARIANT_ID}:1\n");
            prompt.append("  (Replace {VARIANT_ID} with actual variant ID from search results)\n");
        }
        if (config.isShowPrices()) {
            prompt.append("- Always include product prices from search results\n");
        }
        if (config.isShowSkus()) {
            prompt.append("- Include SKU information from search results\n");
        }
        prompt.append("\n");

        // Custom instructions
        if (config.getCustomInstructions() != null && !config.getCustomInstructions().isEmpty()) {
            prompt.append("=== ADDITIONAL INSTRUCTIONS ===\n");
            prompt.append(config.getCustomInstructions()).append("\n\n");
        }

        prompt.append("Remember: USE search_products for ANY product-related question!");

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
