package com.shopify.api.service;

import com.shopify.api.config.ModelConfig;
import com.shopify.api.model.ChatMessage;
import com.shopify.api.model.ChatRequest;
import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.model.SystemPrompt;
import com.shopify.api.model.agent.Agent;
import com.shopify.api.repository.agent.AgentRepository;
import com.shopify.api.service.tool.AgentDelegationToolHandler;
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
import java.util.Optional;

@Service
public class ChatAgentService {

    private static final Logger logger = LoggerFactory.getLogger(ChatAgentService.class);

    @Value("${anthropic.api.key:}")
    private String anthropicApiKey;

    @Value("${anthropic.api.version:2023-06-01}")
    private String anthropicApiVersion;

    @Value("${anthropic.model:claude-3-7-sonnet-20250219}")
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
    private final SystemPromptService systemPromptService;
    private final ModelValidationService modelValidationService;
    private final AgentRepository agentRepository;
    private final AgentDelegationToolHandler agentDelegationHandler;
    private final ObjectMapper objectMapper;
    private final String shopUrl;
    private final ResourceLoader resourceLoader;
    private String systemPromptTemplate;

    // Shop context for dynamic prompts
    private ShopifyShop currentShop;

    @Autowired
    public ChatAgentService(WebClient.Builder webClientBuilder,
                           ProductService productService,
                           ChatbotConfigService chatbotConfigService,
                           SystemPromptService systemPromptService,
                           ModelValidationService modelValidationService,
                           AgentRepository agentRepository,
                           AgentDelegationToolHandler agentDelegationHandler,
                           ResourceLoader resourceLoader,
                           @Value("${shopify.shop-url}") String shopUrl) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.anthropic.com/v1")
                .build();
        this.productService = productService;
        this.chatbotConfigService = chatbotConfigService;
        this.systemPromptService = systemPromptService;
        this.modelValidationService = modelValidationService;
        this.agentRepository = agentRepository;
        this.agentDelegationHandler = agentDelegationHandler;
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

        // Validate the configured model
        try {
            String validatedModel = modelValidationService.validateOrDefault(anthropicModel);
            if (!validatedModel.equals(anthropicModel)) {
                logger.warn("Configured model '{}' is invalid, using default: {}", anthropicModel, validatedModel);
                anthropicModel = validatedModel;
            } else {
                logger.info("Using validated model: {}", anthropicModel);
            }
        } catch (Exception e) {
            logger.error("Error validating model on startup: {}", e.getMessage());
            anthropicModel = ModelConfig.DEFAULT_MODEL;
            logger.warn("Falling back to default model: {}", anthropicModel);
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

        // Get chatbot config
        ChatbotConfig config = chatbotConfigService.getConfig();

        // Determine model to use (chatbot config overrides defaults)
        String modelToUse = config.getModelName() != null ? config.getModelName() : anthropicModel;

        // Determine temperature to use (chatbot config overrides defaults)
        double tempToUse = config.getTemperature() != null ? config.getTemperature() : temperature;

        // Determine max tokens to use (chatbot config overrides defaults)
        int tokensToUse = config.getMaxTokens() != null ? config.getMaxTokens() : maxTokens;

        logger.debug("Using AI settings - model: {}, temp: {}, maxTokens: {}", modelToUse, tempToUse, tokensToUse);

        // Create the request body for Claude API
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", modelToUse);
        requestBody.put("max_tokens", tokensToUse);
        requestBody.put("temperature", tempToUse);
        requestBody.put("system", systemPrompt);
        requestBody.set("messages", messages);

        // Add tools array if product search is enabled
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
     * Uses handler pattern for clean separation of concerns
     */
    private Mono<String> executeToolCallReactive(String toolName, JsonNode input) {
        logger.info("=== EXECUTING TOOL: {} ===", toolName);

        try {
            // Route to appropriate handler based on tool name
            if ("search_products".equals(toolName)) {
                return executeProductSearch(input);
            } else if ("delegate_to_agent".equals(toolName)) {
                return executeDelegateToAgent(input);
            }

            logger.warn("Unknown tool requested: {}", toolName);
            return Mono.just("{\"error\": \"Unknown tool: " + toolName + "\"}");

        } catch (Exception e) {
            logger.error("Error in executeToolCallReactive {}: {}", toolName, e.getMessage(), e);
            return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Execute product search tool
     */
    private Mono<String> executeProductSearch(JsonNode input) {
        String query = input.get("query").asText();
        int maxResults = chatbotConfigService.getConfig().getMaxSearchResults();

        logger.info("Query: '{}', Max Results: {}", query, maxResults);

        return productService.searchProductsReactive(query, maxResults)
                .map(results -> {
                    try {
                        logger.info("Search completed - Results type: {}",
                            results != null ? results.getClass().getSimpleName() : "null");

                        String jsonResult = objectMapper.writeValueAsString(results);
                        logger.debug("Returning {} characters of JSON to Claude", jsonResult.length());

                        return jsonResult;
                    } catch (Exception e) {
                        logger.error("Error formatting search results: {}", e.getMessage(), e);
                        return "{\"error\": \"Error formatting results: " + e.getMessage() + "\"}";
                    }
                })
                .onErrorResume(e -> {
                    logger.error("Error executing product search: {}", e.getMessage(), e);
                    return Mono.just("{\"error\": \"Error executing search: " + e.getMessage() + "\"}");
                });
    }

    /**
     * Execute agent delegation tool using handler
     */
    private Mono<String> executeDelegateToAgent(JsonNode input) {
        // Validate input first
        if (!agentDelegationHandler.validateInput(input)) {
            logger.warn("Invalid input for delegate_to_agent tool");
            return Mono.just("{\"error\": \"Invalid input: agent_name and task are required\"}");
        }

        // Execute using the dedicated handler
        return agentDelegationHandler.execute(input)
                .map(JsonNode::toString)
                .onErrorResume(e -> {
                    logger.error("Error delegating to agent: {}", e.getMessage(), e);
                    return Mono.just("{\"error\": \"Delegation failed: " + e.getMessage() + "\"}");
                });
    }

    /**
     * Build tools array for Claude API
     */
    private ArrayNode buildToolsArray(ChatbotConfig config) {
        ArrayNode tools = objectMapper.createArrayNode();

        // Add search_products tool if enabled
        if (config.isEnableProductSearch()) {
            ObjectNode searchTool = objectMapper.createObjectNode();
            searchTool.put("name", "search_products");
            searchTool.put("description", "Search the product catalog to find items matching a query. " +
                    "Use this to find specific products, check availability, get prices, or browse categories. " +
                    "Returns product details including title, description, price, SKU, variants, and image URL.");

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
        }

        // Add delegate_to_agent tool if agents are linked
        if (config.getLinkedAgentIds() != null && !config.getLinkedAgentIds().isEmpty()) {
            ObjectNode delegateTool = objectMapper.createObjectNode();
            delegateTool.put("name", "delegate_to_agent");
            delegateTool.put("description", "Delegate a task to a specialist agent. " +
                    "Use this when you need expert knowledge or specialized capabilities. " +
                    "The agent will process the task and return results.");

            ObjectNode delegateSchema = objectMapper.createObjectNode();
            delegateSchema.put("type", "object");

            ObjectNode delegateProps = objectMapper.createObjectNode();

            ObjectNode agentNameProp = objectMapper.createObjectNode();
            agentNameProp.put("type", "string");
            agentNameProp.put("description", "Name of the specialist agent to delegate to");
            delegateProps.set("agent_name", agentNameProp);

            ObjectNode taskProp = objectMapper.createObjectNode();
            taskProp.put("type", "string");
            taskProp.put("description", "The task or question to send to the specialist agent");
            delegateProps.set("task", taskProp);

            delegateSchema.set("properties", delegateProps);
            ArrayNode delegateRequired = objectMapper.createArrayNode();
            delegateRequired.add("agent_name");
            delegateRequired.add("task");
            delegateSchema.set("required", delegateRequired);

            delegateTool.set("input_schema", delegateSchema);
            tools.add(delegateTool);
        }

        return tools;
    }

    /**
     * Set the current shop context for dynamic prompts
     * Should be called before processChat for shop-scoped requests
     */
    public void setShopContext(ShopifyShop shop) {
        this.currentShop = shop;
        logger.debug("Shop context set to: {}", shop != null ? shop.getShopDomain() : "null");
    }

    /**
     * Clear the current shop context
     */
    public void clearShopContext() {
        this.currentShop = null;
    }

    /**
     * Build system prompt that defines the AI's role and capabilities
     * Dynamically generates prompt from database or ChatbotConfig
     */
    private String buildSystemPrompt() {
        // Try to get dynamic prompt from database if shop context is set
        if (currentShop != null && systemPromptService != null) {
            try {
                Optional<SystemPrompt> promptOpt = systemPromptService.getActivePromptByType(
                    SystemPrompt.PromptType.PRODUCT_SEARCH,
                    currentShop
                );

                if (promptOpt.isPresent()) {
                    SystemPrompt prompt = promptOpt.get();
                    logger.info("Using dynamic system prompt: {} (version {})",
                        prompt.getPromptName(), prompt.getVersion());
                    return prompt.getPromptText();
                }
            } catch (Exception e) {
                logger.warn("Failed to load dynamic prompt, falling back to default: {}", e.getMessage());
            }
        }

        // Fall back to building prompt from ChatbotConfig
        logger.debug("Using ChatbotConfig-based system prompt");
        return buildSystemPromptFromConfig();
    }

    /**
     * Build system prompt from ChatbotConfig
     * Includes list of available specialist agents if configured
     */
    private String buildSystemPromptFromConfig() {
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

        // Specialist Agents (if any are linked)
        if (config.getLinkedAgentIds() != null && !config.getLinkedAgentIds().isEmpty()) {
            prompt.append("=== SPECIALIST AGENTS AVAILABLE ===\n");
            prompt.append("You have access to the following specialist agents via the delegate_to_agent tool:\n\n");

            for (Long agentId : config.getLinkedAgentIds()) {
                try {
                    Optional<Agent> agentOpt = agentRepository.findById(agentId);
                    if (agentOpt.isPresent()) {
                        Agent agent = agentOpt.get();
                        prompt.append("- ").append(agent.getName());
                        if (agent.getDescription() != null && !agent.getDescription().isEmpty()) {
                            prompt.append(": ").append(agent.getDescription());
                        }
                        prompt.append("\n");
                    }
                } catch (Exception e) {
                    logger.warn("Failed to load agent ID {}: {}", agentId, e.getMessage());
                }
            }
            prompt.append("\n");
        }

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
            prompt.append("- ALWAYS generate 'Add to Cart' links for each product variant you recommend\n");
            prompt.append("- Extract the numeric ID from variant.id (e.g., 'gid://shopify/ProductVariant/12345' -> use '12345')\n");
            prompt.append("- Cart link format: https://").append(shopUrl).append("/cart/{NUMERIC_ID}:1\n");
            prompt.append("- Example: variant.id = 'gid://shopify/ProductVariant/44488028725445' -> use: https://").append(shopUrl).append("/cart/44488028725445:1\n");
            prompt.append("- Present as clickable markdown: [Add to Cart](https://").append(shopUrl).append("/cart/44488028725445:1)\n");
        }
        if (config.isShowPrices()) {
            prompt.append("- Always include product prices from search results\n");
        }
        if (config.isShowSkus()) {
            prompt.append("- Include SKU information from search results\n");
        }
        prompt.append("\n");

        // Custom instructions (including agent routing logic)
        if (config.getCustomInstructions() != null && !config.getCustomInstructions().isEmpty()) {
            prompt.append("=== ADDITIONAL INSTRUCTIONS ===\n");
            prompt.append(config.getCustomInstructions()).append("\n\n");
        }

        // Final reminder
        StringBuilder reminder = new StringBuilder("Remember: ");
        if (config.isEnableProductSearch()) {
            reminder.append("USE search_products for product-related questions");
        }
        if (config.getLinkedAgentIds() != null && !config.getLinkedAgentIds().isEmpty()) {
            if (config.isEnableProductSearch()) {
                reminder.append(", and ");
            }
            reminder.append("delegate to specialist agents when their expertise is needed");
        }
        prompt.append(reminder).append("!");

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
        // Validate model before setting
        String validatedModel = modelValidationService.validateOrDefault(model);
        this.anthropicModel = validatedModel;
        logger.info("Model updated to: {}", validatedModel);
        if (!validatedModel.equals(model)) {
            logger.warn("Requested model '{}' was invalid, using '{}' instead", model, validatedModel);
        }
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
     * Also captures token usage for cost tracking
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
                    ChatMessage message = new ChatMessage("assistant", fullText.toString());

                    // Extract token usage from response
                    JsonNode usage = response.get("usage");
                    if (usage != null) {
                        JsonNode inputTokens = usage.get("input_tokens");
                        JsonNode outputTokens = usage.get("output_tokens");

                        if (inputTokens != null && outputTokens != null) {
                            message.setInputTokens(inputTokens.asInt());
                            message.setOutputTokens(outputTokens.asInt());
                            logger.debug("Token usage - Input: {}, Output: {}",
                                inputTokens.asInt(), outputTokens.asInt());
                        }

                        // Log prompt caching metrics (API version 2024-07-15+)
                        JsonNode cacheCreationTokens = usage.get("cache_creation_input_tokens");
                        JsonNode cacheReadTokens = usage.get("cache_read_input_tokens");

                        if (cacheCreationTokens != null && cacheCreationTokens.asInt() > 0) {
                            logger.info("Prompt Cache CREATED - {} tokens cached for future requests",
                                cacheCreationTokens.asInt());
                        }
                        if (cacheReadTokens != null && cacheReadTokens.asInt() > 0) {
                            logger.info("Prompt Cache HIT - {} tokens read from cache (90% cost savings!)",
                                cacheReadTokens.asInt());
                        }
                    }

                    return message;
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
