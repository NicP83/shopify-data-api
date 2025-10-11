package com.shopify.api.controller;

import com.shopify.api.model.ChatbotConfig;
import com.shopify.api.service.ChatAgentService;
import com.shopify.api.service.ChatbotConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API for managing AI configuration settings
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final Logger logger = LoggerFactory.getLogger(ConfigController.class);

    private final ChatAgentService chatAgentService;
    private final ChatbotConfigService chatbotConfigService;

    @Autowired
    public ConfigController(ChatAgentService chatAgentService, ChatbotConfigService chatbotConfigService) {
        this.chatAgentService = chatAgentService;
        this.chatbotConfigService = chatbotConfigService;
    }

    /**
     * Get current AI configuration settings
     * GET /api/config/ai
     */
    @GetMapping("/ai")
    public ResponseEntity<Map<String, Object>> getAIConfig() {
        logger.info("Fetching AI configuration");

        Map<String, Object> config = new HashMap<>();
        config.put("model", chatAgentService.getAnthropicModel());
        config.put("maxTokens", chatAgentService.getMaxTokens());
        config.put("temperature", chatAgentService.getTemperature());

        return ResponseEntity.ok(config);
    }

    /**
     * Update AI configuration settings (runtime only, not persisted)
     * PUT /api/config/ai
     *
     * Request body example:
     * {
     *   "model": "claude-3-5-sonnet-20241022",
     *   "maxTokens": 2048,
     *   "temperature": 0.5
     * }
     */
    @PutMapping("/ai")
    public ResponseEntity<Map<String, Object>> updateAIConfig(@RequestBody Map<String, Object> config) {
        logger.info("Updating AI configuration: {}", config);

        try {
            // Update model if provided
            if (config.containsKey("model")) {
                String model = (String) config.get("model");
                chatAgentService.setAnthropicModel(model);
            }

            // Update maxTokens if provided
            if (config.containsKey("maxTokens")) {
                Integer maxTokens = ((Number) config.get("maxTokens")).intValue();
                chatAgentService.setMaxTokens(maxTokens);
            }

            // Update temperature if provided
            if (config.containsKey("temperature")) {
                Double temperature = ((Number) config.get("temperature")).doubleValue();
                chatAgentService.setTemperature(temperature);
            }

            // Return updated configuration
            Map<String, Object> updatedConfig = new HashMap<>();
            updatedConfig.put("model", chatAgentService.getAnthropicModel());
            updatedConfig.put("maxTokens", chatAgentService.getMaxTokens());
            updatedConfig.put("temperature", chatAgentService.getTemperature());
            updatedConfig.put("message", "Configuration updated successfully (runtime only)");

            return ResponseEntity.ok(updatedConfig);

        } catch (Exception e) {
            logger.error("Error updating AI configuration: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to update configuration");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Get current system prompt (dynamically generated from configuration)
     * GET /api/config/prompt
     */
    @GetMapping("/prompt")
    public ResponseEntity<Map<String, Object>> getSystemPrompt() {
        logger.info("Fetching system prompt");

        Map<String, Object> response = new HashMap<>();
        response.put("prompt", chatAgentService.getGeneratedSystemPrompt());
        response.put("editable", true);
        response.put("message", "System prompt is dynamically generated from chatbot configuration. Edit configuration to change.");

        return ResponseEntity.ok(response);
    }

    /**
     * Get available Claude models
     * GET /api/config/models
     */
    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> getAvailableModels() {
        logger.info("Fetching available Claude models");

        Map<String, Object> response = new HashMap<>();
        response.put("models", new String[]{
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022",
            "claude-3-opus-20240229",
            "claude-3-sonnet-20240229",
            "claude-3-haiku-20240307"
        });
        response.put("current", chatAgentService.getAnthropicModel());

        return ResponseEntity.ok(response);
    }

    /**
     * Get chatbot configuration
     * GET /api/config/chatbot
     */
    @GetMapping("/chatbot")
    public ResponseEntity<ChatbotConfig> getChatbotConfig() {
        logger.info("Fetching chatbot configuration");
        return ResponseEntity.ok(chatbotConfigService.getConfig());
    }

    /**
     * Update chatbot configuration (runtime only, not persisted)
     * PUT /api/config/chatbot
     */
    @PutMapping("/chatbot")
    public ResponseEntity<Map<String, Object>> updateChatbotConfig(@RequestBody ChatbotConfig config) {
        logger.info("Updating chatbot configuration");

        try {
            chatbotConfigService.updateConfig(config);

            Map<String, Object> response = new HashMap<>();
            response.put("config", chatbotConfigService.getConfig());
            response.put("message", "Chatbot configuration updated successfully (runtime only)");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating chatbot configuration: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to update chatbot configuration");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Reset chatbot configuration to defaults
     * POST /api/config/chatbot/reset
     */
    @PostMapping("/chatbot/reset")
    public ResponseEntity<Map<String, Object>> resetChatbotConfig() {
        logger.info("Resetting chatbot configuration to defaults");

        try {
            chatbotConfigService.resetToDefaults();

            Map<String, Object> response = new HashMap<>();
            response.put("config", chatbotConfigService.getConfig());
            response.put("message", "Chatbot configuration reset to defaults");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error resetting chatbot configuration: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to reset chatbot configuration");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Preview system prompt generated from current configuration
     * GET /api/config/chatbot/preview-prompt
     */
    @GetMapping("/chatbot/preview-prompt")
    public ResponseEntity<Map<String, Object>> previewSystemPrompt() {
        logger.info("Previewing system prompt");

        Map<String, Object> response = new HashMap<>();
        response.put("prompt", chatAgentService.getGeneratedSystemPrompt());
        response.put("config", chatbotConfigService.getConfig());
        response.put("message", "This is the system prompt that will be sent to Claude API based on current configuration");

        return ResponseEntity.ok(response);
    }
}
