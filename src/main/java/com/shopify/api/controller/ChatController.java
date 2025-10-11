package com.shopify.api.controller;

import com.shopify.api.model.ApiResponse;
import com.shopify.api.model.ChatMessage;
import com.shopify.api.model.ChatRequest;
import com.shopify.api.service.ChatAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private ChatAgentService chatAgentService;

    /**
     * POST /api/chat/message - Send a message to the AI chat agent
     *
     * Request body:
     * {
     *   "message": "Tell me about Gundam models",
     *   "conversationHistory": [
     *     {"role": "user", "content": "Hello", "timestamp": 1234567890},
     *     {"role": "assistant", "content": "Hi! How can I help?", "timestamp": 1234567891}
     *   ]
     * }
     *
     * Response:
     * {
     *   "success": true,
     *   "message": "Message processed successfully",
     *   "data": {
     *     "role": "assistant",
     *     "content": "I'd be happy to help you find Gundam models...",
     *     "timestamp": 1234567892
     *   }
     * }
     */
    @PostMapping("/message")
    public Mono<ResponseEntity<ApiResponse<ChatMessage>>> sendMessage(@RequestBody ChatRequest chatRequest) {
        logger.info("Received chat message: {}", chatRequest.getMessage());

        return chatAgentService.processChat(chatRequest)
                .map(chatMessage -> {
                    logger.info("Chat response generated successfully");
                    return ResponseEntity.ok(ApiResponse.success(
                            "Message processed successfully",
                            chatMessage
                    ));
                })
                .onErrorResume(error -> {
                    logger.error("Error processing chat message: {}", error.getMessage());
                    return Mono.just(ResponseEntity.ok(ApiResponse.error(
                            "Failed to process message: " + error.getMessage()
                    )));
                });
    }

    /**
     * GET /api/chat/status - Check if chat agent is available
     *
     * Response:
     * {
     *   "success": true,
     *   "message": "Chat agent status",
     *   "data": {
     *     "available": true,
     *     "provider": "Anthropic Claude",
     *     "model": "claude-3-5-sonnet-20241022"
     *   }
     * }
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getStatus() {
        logger.info("Chat status check");

        return ResponseEntity.ok(ApiResponse.success(
                "Chat agent status",
                new java.util.HashMap<String, Object>() {{
                    put("available", true);
                    put("provider", "Anthropic Claude");
                    put("model", "claude-3-5-sonnet-20241022");
                    put("description", "AI-powered sales and support assistant for Gundam model kits");
                }}
        ));
    }
}
