package com.shopify.api.controller;

import com.shopify.api.service.ChatAgentService;
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

    @Autowired
    public ConfigController(ChatAgentService chatAgentService) {
        this.chatAgentService = chatAgentService;
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
     * Get current system prompt template
     * GET /api/config/prompt
     */
    @GetMapping("/prompt")
    public ResponseEntity<Map<String, Object>> getSystemPrompt() {
        logger.info("Fetching system prompt");

        Map<String, Object> response = new HashMap<>();
        response.put("prompt", chatAgentService.getSystemPromptTemplate());
        response.put("editable", false); // Currently read-only
        response.put("message", "System prompt is loaded from file. Edit src/main/resources/prompts/system-prompt.txt to change.");

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
}
