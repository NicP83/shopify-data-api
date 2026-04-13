package com.shopify.api.service;

import com.shopify.api.config.ModelConfig;
import com.shopify.api.model.ChatMessage;
import com.shopify.api.model.ChatRequest;
import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.model.SystemPrompt;
import com.shopify.api.model.agent.Agent;
import com.shopify.api.repository.agent.AgentRepository;
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
    private final ChatToolRegistry chatToolRegistry;
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
                           ChatToolRegistry chatToolRegistry,
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
        this.chatToolRegistry = chatToolRegistry;
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
        if (iteration >= 15) {
            logger.warn("Max tool use iterations reached ({}), returning graceful response", iteration);
            // Instead of an error, ask Claude to summarize what it has so far without using tools
            return callClaudeWithoutTools(systemPrompt, messages,
                "You have used the maximum number of tool calls for this turn. " +
                "Based on whatever information you have gathered so far, give the customer a helpful response. " +
                "If you found products, show them. If not, suggest they refine their question or try a different search. " +
                "Do NOT say 'having trouble' — be helpful with what you have.");
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

        // Add tools array from the dynamic registry (each tool controls its own isEnabled)
        ArrayNode tools = buildToolsArray(config);
        if (tools.size() > 0) {
            requestBody.set("tools", tools);
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
     * Execute a tool call reactively via the dynamic tool registry.
     * No hardcoded routing — all tools are auto-discovered.
     */
    private Mono<String> executeToolCallReactive(String toolName, JsonNode input) {
        logger.info("=== EXECUTING TOOL: {} ===", toolName);
        return chatToolRegistry.executeTool(toolName, input);
    }

    /**
     * Call Claude WITHOUT tools to force a final text response.
     * Used when the iteration limit is reached — Claude summarizes
     * whatever it has gathered so far instead of returning an error.
     */
    private Mono<ChatMessage> callClaudeWithoutTools(String systemPrompt, ArrayNode messages, String instruction) {
        ChatbotConfig config = chatbotConfigService.getConfig();
        String modelToUse = config.getModelName() != null ? config.getModelName() : anthropicModel;
        double tempToUse = config.getTemperature() != null ? config.getTemperature() : temperature;
        int tokensToUse = config.getMaxTokens() != null ? config.getMaxTokens() : maxTokens;

        // Add instruction as a system-level nudge in the user messages
        ObjectNode instructionMessage = objectMapper.createObjectNode();
        instructionMessage.put("role", "user");
        instructionMessage.put("content", "[SYSTEM NOTE: " + instruction + "]");
        messages.add(instructionMessage);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", modelToUse);
        requestBody.put("max_tokens", tokensToUse);
        requestBody.put("temperature", tempToUse);
        requestBody.put("system", systemPrompt);
        requestBody.set("messages", messages);
        // No tools — forces a text-only response

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
                    logger.error("Error in fallback Claude call: {}", error.getMessage());
                    return Mono.just(createErrorResponse());
                });
    }

    /**
     * Build tools array for Claude API using the dynamic tool registry.
     * Tools are auto-discovered — no manual registration needed.
     */
    private ArrayNode buildToolsArray(ChatbotConfig config) {
        return chatToolRegistry.buildToolDefinitions(config);
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
        String basePrompt;

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
                    basePrompt = prompt.getPromptText();
                    return basePrompt + buildLinkInstructions();
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
     * Build mandatory link instructions appended to every prompt
     */
    private String buildLinkInstructions() {
        ChatbotConfig config = chatbotConfigService.getConfig();
        StringBuilder instructions = new StringBuilder();

        if (config.isIncludeCartLinks() || config.isIncludeProductLinks()) {
            instructions.append("\n\n=== MANDATORY PRODUCT LINK RULES ===\n");
            instructions.append("Store URL: https://").append(shopUrl).append("\n\n");
        }

        if (config.isIncludeCartLinks()) {
            instructions.append("- ALWAYS generate 'Add to Cart' links for each product you recommend\n");
            instructions.append("- Extract the numeric ID from variant.id (e.g., 'gid://shopify/ProductVariant/12345' -> use '12345')\n");
            instructions.append("- Cart link format: https://").append(shopUrl).append("/cart/{NUMERIC_ID}:1\n");
            instructions.append("- Present as clickable markdown: [Add to Cart](https://").append(shopUrl).append("/cart/VARIANT_ID:1)\n");
        }

        if (config.isIncludeProductLinks()) {
            instructions.append("- ALWAYS include a 'View Product' link for each product\n");
            instructions.append("- Use onlineStoreUrl from search results when available\n");
            instructions.append("- If onlineStoreUrl is empty, construct from handle: https://").append(shopUrl).append("/products/{HANDLE}\n");
            instructions.append("- Present as: [View Product](URL)\n");
        }

        if (config.isIncludeCartLinks() || config.isIncludeProductLinks()) {
            instructions.append("\n=== TABLE FORMAT WITH LINKS ===\n");
            instructions.append("When using a table, MUST include a Links column. Example:\n\n");
            instructions.append("| Product | Price | Links |\n");
            instructions.append("| --- | --- | --- |\n");
            instructions.append("| Product Name | $29.99 | [View Product](https://").append(shopUrl).append("/products/handle) [Add to Cart](https://").append(shopUrl).append("/cart/VARIANT_ID:1) |\n\n");
            instructions.append("ANY time you mention a product by name — in a table, list, paragraph, 'Current Specials', or anywhere else — you MUST include [View Product] and [Add to Cart] links. No exceptions.\n");
        }

        return instructions.toString();
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
            prompt.append("- onlineStoreUrl for direct product page links\n");
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
            prompt.append("- Even when presenting products in a table, include [Add to Cart] links for EVERY product — either as a column in the table or as links below each product entry\n");
        }
        if (config.isIncludeProductLinks()) {
            prompt.append("- ALWAYS include a 'View Product' link for each product you recommend\n");
            prompt.append("- Use onlineStoreUrl from search results when available\n");
            prompt.append("- If onlineStoreUrl is empty, construct from handle: https://").append(shopUrl).append("/products/{HANDLE}\n");
            prompt.append("- Present as: [View Product](URL)\n");
            prompt.append("- Even when presenting products in a table, include [View Product] links for EVERY product — either as a column in the table or as links below each product entry\n");
        }
        if (config.isShowPrices()) {
            prompt.append("- Always include product prices from search results\n");
        }
        if (config.isShowSkus()) {
            prompt.append("- Include SKU information from search results\n");
        }
        // Concrete table example so AI always includes clickable links
        if (config.isIncludeCartLinks() || config.isIncludeProductLinks()) {
            prompt.append("\n=== TABLE FORMAT WITH LINKS (MANDATORY) ===\n");
            prompt.append("When using a table to present products, you MUST include Links column with clickable markdown links. Example:\n\n");
            prompt.append("| Product | Price | Links |\n");
            prompt.append("| --- | --- | --- |\n");
            prompt.append("| Product Name | $29.99 | [View Product](https://").append(shopUrl).append("/products/handle) [Add to Cart](https://").append(shopUrl).append("/cart/VARIANT_ID:1) |\n\n");
            prompt.append("NEVER present a product table without a Links column. Every row MUST have clickable [View Product] and [Add to Cart] links.\n\n");
            prompt.append("=== ALL PRODUCT MENTIONS NEED LINKS (MANDATORY) ===\n");
            prompt.append("ANY time you mention a product by name — in a table, list, paragraph, \"Current Specials\" section, or anywhere else — you MUST include [View Product] and [Add to Cart] links.\n");
            prompt.append("No exceptions. Never mention a product without its links.\n");
        }
        prompt.append("\n");

        // === CONVERSION & CUSTOMER EXPERIENCE RULES ===
        prompt.append("=== CONVERSION & CUSTOMER EXPERIENCE ===\n");
        prompt.append("You are a knowledgeable hobby expert and sales assistant. Your goal is to help customers find exactly what they need and make purchasing easy.\n\n");

        prompt.append("SHOW PRODUCTS WHILE YOU ASK (CRITICAL RULE):\n");
        prompt.append("- ALWAYS search and show products within your FIRST response, even if you also ask a question\n");
        prompt.append("- You CAN ask ONE clarifying question, but include product examples alongside it\n");
        prompt.append("- Example: 'I need an RC car for my son' → search RC cars, show 3-5 beginner options, AND ask 'Is he into off-road or on-road?'\n");
        prompt.append("- Example: 'give me some options' or 'show me something' → STOP ASKING and SEARCH IMMEDIATELY\n");
        prompt.append("- NEVER respond with ONLY questions and no products — always include real product examples\n");
        prompt.append("- Make reasonable assumptions to show products: if they say 'beginner', show entry-level; if 'kid', show age-appropriate\n");
        prompt.append("- After showing products, you can offer to refine: 'Want me to narrow it down by budget or style?'\n");
        prompt.append("- Products on screen convert — questions without products lose customers\n\n");

        prompt.append("STOCK URGENCY:\n");
        prompt.append("- When a product has low inventory (5 or fewer), mention it naturally: 'This is a popular item — only 3 left in stock!'\n");
        prompt.append("- Use check_inventory to verify stock when customers ask about availability\n");
        prompt.append("- Never pressure, but do create honest urgency for genuinely low-stock items\n\n");

        prompt.append("COMPLEMENTARY SUGGESTIONS:\n");
        prompt.append("- After helping a customer find a model kit, ask if they need supplies: paints, cement, tools, primer, or a cutter\n");
        prompt.append("- For paints, suggest complementary colours or thinners\n");
        prompt.append("- For airbrushes, suggest compressors, cleaning kits, or paints\n");
        prompt.append("- Keep suggestions relevant — don't suggest random products\n\n");

        prompt.append("CLOSING & CONVERSION:\n");
        prompt.append("- After presenting products, ask a closing question: 'Would you like me to find anything else for your project?' or 'Shall I check if we have the matching paint set?'\n");
        prompt.append("- If a customer seems undecided, offer to compare options or check stock\n");
        prompt.append("- Make the path to purchase frictionless — always include Add to Cart links\n\n");

        prompt.append("BROWSING & DISCOVERY:\n");
        prompt.append("- Use browse_products when customers want to explore categories or filter by price\n");
        prompt.append("- If a customer seems to be browsing, offer to show new arrivals or popular items\n");
        prompt.append("- Present options from most affordable to premium, noting the value of each\n\n");

        prompt.append("ORDER SUPPORT:\n");
        prompt.append("- Use lookup_order when customers ask about order status — you need both order number AND email\n");
        prompt.append("- If they only provide one, politely ask for the other for security verification\n");
        prompt.append("- Be empathetic with order issues and suggest contacting support for complex problems\n\n");

        // Custom instructions (including agent routing logic)
        if (config.getCustomInstructions() != null && !config.getCustomInstructions().isEmpty()) {
            prompt.append("=== ADDITIONAL INSTRUCTIONS ===\n");
            prompt.append(config.getCustomInstructions()).append("\n\n");
        }

        // Proactive engagement
        prompt.append("=== PROACTIVE ENGAGEMENT ===\n");
        prompt.append("- Greet returning customers by name if known from customer context\n");
        prompt.append("- If a returning customer previously bought model kits, ask if they need supplies for it\n");
        prompt.append("- When appropriate, mention current promotions or new arrivals\n");
        prompt.append("- If a customer seems like a beginner, offer guidance and suggest starter kits or tools\n");
        prompt.append("- Be genuinely helpful, not pushy — build trust and the sales will follow\n\n");

        // Final reminder with all tools
        prompt.append("=== TOOL USAGE SUMMARY ===\n");
        prompt.append("- search_products: Find specific products by name, keyword, or description\n");
        prompt.append("- browse_products: Browse by category, vendor, price range, or sort order\n");
        prompt.append("- check_inventory: Check stock levels and availability\n");
        prompt.append("- lookup_order: Look up order status (requires order number + email)\n");
        prompt.append("- get_stock_insights: Get sales velocity and trend data for a product SKU (social proof)\n");
        prompt.append("- get_complementary_products: Suggest complementary items for a product\n");
        prompt.append("- get_customer_history: Look up customer purchase history (only when they provide email)\n");
        prompt.append("- get_promotions: Find products currently on sale or special\n");
        prompt.append("- compare_products: Compare 2-3 products side by side\n");
        prompt.append("Always use the right tool for the job. Search before recommending. Never make up products.");

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
