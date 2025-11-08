package com.shopify.api.controller;

import com.shopify.api.model.ApiResponse;
import com.shopify.api.service.inventory.InventoryAnalysisAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for Inventory Analysis AI Agent
 * Provides conversational interface for inventory management
 */
@RestController
@RequestMapping("/api/inventory-management/agent")
public class InventoryAnalysisAgentController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryAnalysisAgentController.class);

    private final InventoryAnalysisAgent agent;

    public InventoryAnalysisAgentController(InventoryAnalysisAgent agent) {
        this.agent = agent;
    }

    /**
     * POST /api/inventory-management/agent/chat
     * Conversational interface for inventory analysis
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<Map<String, Object>>> chat(
            @RequestBody Map<String, Object> request) {

        logger.info("POST /api/inventory-management/agent/chat");

        try {
            String message = (String) request.get("message");
            List<Map<String, String>> conversationHistory =
                    (List<Map<String, String>>) request.get("conversationHistory");
            Map<String, Object> context = (Map<String, Object>) request.get("context");

            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity
                        .badRequest()
                        .body(ApiResponse.error("Message is required", null));
            }

            Map<String, Object> result = agent.processMessage(message, conversationHistory, context);

            if (result.get("success") == Boolean.TRUE) {
                return ResponseEntity.ok(ApiResponse.success(result));
            } else {
                return ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("Agent processing failed",
                                (String) result.get("error")));
            }

        } catch (Exception e) {
            logger.error("Error in agent chat: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to process message", e.getMessage()));
        }
    }

    /**
     * GET /api/inventory-management/agent/tools
     * Get list of available tools
     */
    @GetMapping("/tools")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTools() {
        logger.info("GET /api/inventory-management/agent/tools");

        try {
            List<Map<String, Object>> tools = agent.getAvailableTools();
            return ResponseEntity.ok(ApiResponse.success(tools));

        } catch (Exception e) {
            logger.error("Error getting tools: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get tools", e.getMessage()));
        }
    }

    /**
     * POST /api/inventory-management/agent/reset
     * Reset conversation (clear history)
     */
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Map<String, String>>> reset() {
        logger.info("POST /api/inventory-management/agent/reset");

        try {
            // In a full implementation, this would clear session data
            Map<String, String> result = Map.of(
                    "status", "reset",
                    "message", "Conversation history cleared"
            );

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            logger.error("Error resetting conversation: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to reset conversation", e.getMessage()));
        }
    }
}
