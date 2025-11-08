package com.shopify.api.service.inventory;

import com.shopify.api.service.ChatAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * AI Agent for Inventory Management
 * Provides conversational interface for inventory analysis and order planning
 */
@Service
public class InventoryAnalysisAgent {

    private static final Logger logger = LoggerFactory.getLogger(InventoryAnalysisAgent.class);

    private final ChatAgentService chatAgentService;
    private final InventoryAnalysisTools tools;

    @Value("${inventory-management.ai.system-prompt-file:classpath:prompts/inventory-agent-prompt.txt}")
    private Resource systemPromptResource;

    @Value("${inventory-management.ai.enabled:true}")
    private boolean aiEnabled;

    private String systemPrompt;

    public InventoryAnalysisAgent(
            ChatAgentService chatAgentService,
            InventoryAnalysisTools tools) {
        this.chatAgentService = chatAgentService;
        this.tools = tools;
    }

    /**
     * Process user message and return AI response with tool executions
     */
    public Map<String, Object> processMessage(
            String userMessage,
            List<Map<String, String>> conversationHistory,
            Map<String, Object> context) {

        logger.info("Processing inventory agent message: {}", userMessage);

        Map<String, Object> result = new HashMap<>();

        if (!aiEnabled) {
            result.put("success", false);
            result.put("error", "AI analysis is disabled");
            return result;
        }

        try {
            // Load system prompt if not already loaded
            if (systemPrompt == null) {
                loadSystemPrompt();
            }

            // Analyze user intent and determine which tools to call
            List<String> detectedIntents = detectIntents(userMessage);
            logger.info("Detected intents: {}", detectedIntents);

            // Execute relevant tools
            List<Map<String, Object>> toolResults = new ArrayList<>();
            for (String intent : detectedIntents) {
                Map<String, Object> toolResult = executeTool(intent, userMessage, context);
                if (toolResult != null) {
                    toolResults.add(toolResult);
                }
            }

            // Build context for AI with tool results
            String contextMessage = buildContextFromToolResults(toolResults);

            // Generate AI response
            String fullPrompt = buildFullPrompt(userMessage, contextMessage, conversationHistory);
            String aiResponse = generateAIResponse(fullPrompt, conversationHistory);

            // Build result
            result.put("success", true);
            result.put("response", aiResponse);
            result.put("toolCalls", toolResults);
            result.put("conversationId", UUID.randomUUID().toString());

        } catch (Exception e) {
            logger.error("Error processing inventory agent message: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Load system prompt from file
     */
    private void loadSystemPrompt() {
        try {
            systemPrompt = FileCopyUtils.copyToString(
                    new InputStreamReader(systemPromptResource.getInputStream(), StandardCharsets.UTF_8)
            );
            logger.info("Loaded system prompt: {} characters", systemPrompt.length());
        } catch (Exception e) {
            logger.error("Failed to load system prompt: {}", e.getMessage());
            systemPrompt = "You are an inventory management assistant.";
        }
    }

    /**
     * Detect user intents from message
     */
    private List<String> detectIntents(String message) {
        List<String> intents = new ArrayList<>();
        String messageLower = message.toLowerCase();

        // Brand analysis
        if (messageLower.contains("brand") ||
            messageLower.contains("tamiya") ||
            messageLower.contains("bandai") ||
            messageLower.contains("gundam") ||
            messageLower.contains("vallejo")) {
            intents.add("analyzeByBrand");
        }

        // Category analysis
        if (messageLower.contains("category") ||
            messageLower.contains("paint") ||
            messageLower.contains("kit") ||
            messageLower.contains("model") ||
            messageLower.contains("tool")) {
            intents.add("analyzeByCategory");
        }

        // Supplier analysis
        if (messageLower.contains("supplier") ||
            messageLower.contains("order from") ||
            messageLower.contains("vendor")) {
            intents.add("analyzeBySupplier");
        }

        // Low stock
        if (messageLower.contains("low stock") ||
            messageLower.contains("running out") ||
            messageLower.contains("need") && messageLower.contains("order")) {
            intents.add("getLowStockFiltered");
        }

        // Order planning
        if (messageLower.contains("order") &&
            (messageLower.contains("days") || messageLower.contains("weeks") || messageLower.contains("month"))) {
            intents.add("generateOrderPlan");
        }

        // Stockout prediction
        if (messageLower.contains("stock out") ||
            messageLower.contains("run out") ||
            messageLower.contains("when will")) {
            intents.add("predictStockout");
        }

        // Comparison
        if (messageLower.contains("compare") ||
            messageLower.contains("versus") ||
            messageLower.contains("vs")) {
            intents.add("compareProducts");
        }

        // Default to low stock if no specific intent
        if (intents.isEmpty()) {
            intents.add("getLowStockFiltered");
        }

        return intents;
    }

    /**
     * Execute appropriate tool based on intent
     */
    private Map<String, Object> executeTool(String intent, String message, Map<String, Object> context) {
        Map<String, Object> toolResult = new HashMap<>();
        toolResult.put("tool", intent);
        toolResult.put("timestamp", System.currentTimeMillis());

        try {
            switch (intent) {
                case "analyzeByBrand":
                    String brand = extractBrand(message);
                    int days = extractDays(message, context);
                    toolResult.put("parameters", Map.of("brand", brand, "days", days));
                    toolResult.put("result", tools.analyzeByBrand(brand, days));
                    break;

                case "analyzeByCategory":
                    String category = extractCategory(message);
                    days = extractDays(message, context);
                    toolResult.put("parameters", Map.of("category", category, "days", days));
                    toolResult.put("result", tools.analyzeByCategory(category, days));
                    break;

                case "analyzeBySupplier":
                    String supplier = extractSupplier(message);
                    days = extractDays(message, context);
                    toolResult.put("parameters", Map.of("supplier", supplier, "days", days));
                    toolResult.put("result", tools.analyzeBySupplier(supplier, days));
                    break;

                case "getLowStockFiltered":
                    Map<String, String> filters = extractFilters(message);
                    toolResult.put("parameters", filters);
                    toolResult.put("result", tools.getLowStockFiltered(filters));
                    break;

                case "generateOrderPlan":
                    String sku = extractSku(message);
                    int targetDays = extractTargetDays(message, context);
                    toolResult.put("parameters", Map.of("sku", sku, "targetDays", targetDays));
                    toolResult.put("result", tools.generateOrderPlan(sku, targetDays));
                    break;

                case "predictStockout":
                    sku = extractSku(message);
                    int forecastDays = extractDays(message, context);
                    toolResult.put("parameters", Map.of("sku", sku, "forecastDays", forecastDays));
                    toolResult.put("result", tools.predictStockout(sku, forecastDays));
                    break;

                case "compareProducts":
                    List<String> skus = extractSkuList(message);
                    toolResult.put("parameters", Map.of("skus", skus));
                    toolResult.put("result", tools.compareProducts(skus));
                    break;

                default:
                    toolResult.put("result", Map.of("error", "Unknown intent: " + intent));
            }

        } catch (Exception e) {
            logger.error("Error executing tool {}: {}", intent, e.getMessage(), e);
            toolResult.put("result", Map.of("error", e.getMessage()));
        }

        return toolResult;
    }

    /**
     * Build context message from tool results
     */
    private String buildContextFromToolResults(List<Map<String, Object>> toolResults) {
        if (toolResults.isEmpty()) {
            return "No tool data available.";
        }

        StringBuilder context = new StringBuilder();
        context.append("TOOL EXECUTION RESULTS:\n\n");

        for (Map<String, Object> toolResult : toolResults) {
            String toolName = (String) toolResult.get("tool");
            Map<String, Object> result = (Map<String, Object>) toolResult.get("result");

            context.append("Tool: ").append(toolName).append("\n");

            if (result != null && result.get("success") == Boolean.TRUE) {
                // Format result data
                context.append(formatToolResult(toolName, result));
            } else {
                context.append("Error: ").append(result != null ? result.get("error") : "Unknown error");
            }

            context.append("\n\n");
        }

        return context.toString();
    }

    /**
     * Format tool result for context
     */
    private String formatToolResult(String toolName, Map<String, Object> result) {
        StringBuilder formatted = new StringBuilder();

        switch (toolName) {
            case "analyzeByBrand":
            case "analyzeByCategory":
                formatted.append("Total Products: ").append(result.get("totalProducts")).append("\n");
                formatted.append("Low Stock Count: ").append(result.get("lowStockCount")).append("\n");
                formatted.append("Average Velocity: ").append(result.get("averageVelocity")).append("\n");
                break;

            case "getLowStockFiltered":
                formatted.append("Alert Count: ").append(result.get("count")).append("\n");
                formatted.append("Alert Level: ").append(result.get("level")).append("\n");
                break;

            case "generateOrderPlan":
                formatted.append("Recommended Quantity: ").append(result.get("recommendedQuantity")).append("\n");
                formatted.append("Total Cost: $").append(result.get("totalCost")).append("\n");
                break;

            default:
                formatted.append(result.toString());
        }

        return formatted.toString();
    }

    /**
     * Build full prompt for AI
     */
    private String buildFullPrompt(String userMessage, String context, List<Map<String, String>> history) {
        StringBuilder prompt = new StringBuilder();

        // Add conversation history
        if (history != null && !history.isEmpty()) {
            prompt.append("CONVERSATION HISTORY:\n");
            for (Map<String, String> msg : history) {
                prompt.append(msg.get("role")).append(": ").append(msg.get("content")).append("\n");
            }
            prompt.append("\n");
        }

        // Add context from tools
        prompt.append(context).append("\n");

        // Add current user message
        prompt.append("USER: ").append(userMessage).append("\n\n");
        prompt.append("Provide a helpful, conversational response based on the tool results above.");

        return prompt.toString();
    }

    /**
     * Generate AI response using ChatAgentService
     */
    private String generateAIResponse(String prompt, List<Map<String, String>> history) {
        try {
            // Build conversation for ChatAgentService
            List<Map<String, String>> conversation = new ArrayList<>();

            // Add system prompt
            conversation.add(Map.of(
                    "role", "system",
                    "content", systemPrompt
            ));

            // Add history if available
            if (history != null) {
                conversation.addAll(history);
            }

            // Add current prompt
            conversation.add(Map.of(
                    "role", "user",
                    "content", prompt
            ));

            // Call ChatAgentService (this is simplified - adjust based on actual API)
            // For now, return a placeholder response
            return "Based on the analysis, I can help you with inventory management. The tool results show inventory data that I can use to provide recommendations.";

        } catch (Exception e) {
            logger.error("Error generating AI response: {}", e.getMessage(), e);
            return "I apologize, but I encountered an error generating a response. Please try again.";
        }
    }

    // ===== EXTRACTION HELPERS =====

    private String extractBrand(String message) {
        String messageLower = message.toLowerCase();
        if (messageLower.contains("tamiya")) return "Tamiya";
        if (messageLower.contains("bandai")) return "Bandai";
        if (messageLower.contains("gundam")) return "Gundam";
        if (messageLower.contains("vallejo")) return "Vallejo";
        if (messageLower.contains("citadel")) return "Citadel";
        return "Unknown";
    }

    private String extractCategory(String message) {
        String messageLower = message.toLowerCase();
        if (messageLower.contains("paint")) return "Paint";
        if (messageLower.contains("kit") || messageLower.contains("model")) return "Model Kits";
        if (messageLower.contains("tool")) return "Tools";
        return "Unknown";
    }

    private String extractSupplier(String message) {
        String messageLower = message.toLowerCase();
        if (messageLower.contains("bandai")) return "Bandai";
        if (messageLower.contains("tamiya")) return "Tamiya";
        return "Unknown";
    }

    private String extractSku(String message) {
        // Simple pattern: look for uppercase letters followed by dash and numbers
        // In production, use more sophisticated NLP
        String[] words = message.split("\\s+");
        for (String word : words) {
            if (word.matches("[A-Z]+-\\d+")) {
                return word;
            }
        }
        return "UNKNOWN";
    }

    private List<String> extractSkuList(String message) {
        List<String> skus = new ArrayList<>();
        String[] words = message.split("\\s+");
        for (String word : words) {
            if (word.matches("[A-Z]+-\\d+")) {
                skus.add(word);
            }
        }
        return skus.isEmpty() ? List.of("UNKNOWN") : skus;
    }

    private int extractDays(String message, Map<String, Object> context) {
        // Check context first
        if (context != null && context.containsKey("defaultDays")) {
            return (int) context.get("defaultDays");
        }

        String messageLower = message.toLowerCase();

        // Look for number + "days/weeks/months"
        if (messageLower.contains("30 days") || messageLower.contains("1 month")) return 30;
        if (messageLower.contains("14 days") || messageLower.contains("2 weeks")) return 14;
        if (messageLower.contains("7 days") || messageLower.contains("1 week")) return 7;
        if (messageLower.contains("60 days") || messageLower.contains("2 months")) return 60;
        if (messageLower.contains("90 days") || messageLower.contains("3 months")) return 90;

        return 30; // Default
    }

    private int extractTargetDays(String message, Map<String, Object> context) {
        return extractDays(message, context);
    }

    private Map<String, String> extractFilters(String message) {
        Map<String, String> filters = new HashMap<>();

        String messageLower = message.toLowerCase();

        // Brand filter
        String brand = extractBrand(message);
        if (!brand.equals("Unknown")) {
            filters.put("brand", brand);
        }

        // Category filter
        String category = extractCategory(message);
        if (!category.equals("Unknown")) {
            filters.put("category", category);
        }

        // Alert level
        if (messageLower.contains("critical")) {
            filters.put("level", "CRITICAL");
        } else if (messageLower.contains("warning")) {
            filters.put("level", "WARNING");
        } else {
            filters.put("level", "WARNING");
        }

        return filters;
    }

    /**
     * Get list of available tools
     */
    public List<Map<String, Object>> getAvailableTools() {
        List<Map<String, Object>> toolsList = new ArrayList<>();

        toolsList.add(Map.of(
                "name", "analyzeByBrand",
                "description", "Analyze inventory for a specific brand",
                "parameters", List.of("brand", "days")
        ));

        toolsList.add(Map.of(
                "name", "analyzeByCategory",
                "description", "Analyze inventory for a specific category",
                "parameters", List.of("category", "days")
        ));

        toolsList.add(Map.of(
                "name", "analyzeBySupplier",
                "description", "Analyze pending orders for a supplier",
                "parameters", List.of("supplier", "days")
        ));

        toolsList.add(Map.of(
                "name", "getLowStockFiltered",
                "description", "Get low stock items with filters",
                "parameters", List.of("filters")
        ));

        toolsList.add(Map.of(
                "name", "generateOrderPlan",
                "description", "Generate order plan for a product",
                "parameters", List.of("sku", "targetDays")
        ));

        toolsList.add(Map.of(
                "name", "getSupplierSummary",
                "description", "Get summary for a supplier",
                "parameters", List.of("supplier")
        ));

        toolsList.add(Map.of(
                "name", "predictStockout",
                "description", "Predict when product will stock out",
                "parameters", List.of("sku", "forecastDays")
        ));

        toolsList.add(Map.of(
                "name", "compareProducts",
                "description", "Compare multiple products",
                "parameters", List.of("skuList")
        ));

        return toolsList;
    }
}
