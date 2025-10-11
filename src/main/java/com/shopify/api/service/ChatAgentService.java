package com.shopify.api.service;

import com.shopify.api.model.ChatMessage;
import com.shopify.api.model.ChatRequest;
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
    private final ObjectMapper objectMapper;
    private final String shopUrl;
    private final ResourceLoader resourceLoader;
    private String systemPromptTemplate;

    @Autowired
    public ChatAgentService(WebClient.Builder webClientBuilder,
                           ProductService productService,
                           ResourceLoader resourceLoader,
                           @Value("${shopify.shop.url}") String shopUrl) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.anthropic.com/v1")
                .build();
        this.productService = productService;
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

        // Create the request body for Claude API
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", anthropicModel);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);
        requestBody.put("system", systemPrompt);
        requestBody.set("messages", messages);

        // Call Claude API
        return webClient.post()
                .uri("/messages")
                .header("x-api-key", anthropicApiKey)
                .header("anthropic-version", anthropicApiVersion)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::extractAssistantMessage)
                .onErrorResume(error -> {
                    logger.error("Error calling Claude API: {}", error.getMessage());
                    return Mono.just(createErrorResponse());
                });
    }

    /**
     * Build system prompt that defines the AI's role and capabilities
     */
    private String buildSystemPrompt() {
        // Replace placeholders in the template with actual values
        return systemPromptTemplate.replace("{SHOP_URL}", shopUrl);
    }

    /**
     * Get the system prompt template (for configuration UI)
     */
    public String getSystemPromptTemplate() {
        return systemPromptTemplate;
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
     */
    private ChatMessage extractAssistantMessage(JsonNode response) {
        try {
            JsonNode content = response.get("content");
            if (content != null && content.isArray() && content.size() > 0) {
                String text = content.get(0).get("text").asText();
                return new ChatMessage("assistant", text);
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
