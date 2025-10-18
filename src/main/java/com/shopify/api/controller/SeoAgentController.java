package com.shopify.api.controller;

import com.shopify.api.model.ApiResponse;
import com.shopify.api.model.SeoAgentRequest;
import com.shopify.api.model.SeoAgentResponse;
import com.shopify.api.service.SeoAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST Controller for SEO Agent functionality
 *
 * Provides endpoints for AI-powered SEO tasks with configurable:
 * - Tool selection
 * - Agent orchestration
 * - LLM settings (model, temperature, tokens, etc.)
 * - Custom orchestration prompts
 *
 * See: docs/seo-agent/IMPLEMENTATION_PLAN.md for feature details
 */
@RestController
@RequestMapping("/api/seo-agent")
public class SeoAgentController {

    private static final Logger logger = LoggerFactory.getLogger(SeoAgentController.class);

    @Autowired
    private SeoAgentService seoAgentService;

    /**
     * POST /api/seo-agent/chat - Send a message to the SEO agent
     *
     * Request body:
     * {
     *   "message": "Help me optimize product titles for SEO",
     *   "conversationHistory": [
     *     {"role": "user", "content": "Hello", "timestamp": 1234567890},
     *     {"role": "assistant", "content": "Hi! How can I help?", "timestamp": 1234567891}
     *   ],
     *   "config": {
     *     "selectedTools": [1, 2, 3],
     *     "selectedAgents": [1],
     *     "llmConfig": {
     *       "model": "claude-3-5-sonnet-20241022",
     *       "temperature": 0.7,
     *       "maxTokens": 4096,
     *       "topP": 1.0
     *     },
     *     "orchestrationPrompt": "You are an SEO expert..."
     *   }
     * }
     *
     * Response:
     * {
     *   "success": true,
     *   "message": "Message processed successfully",
     *   "data": {
     *     "message": {
     *       "role": "assistant",
     *       "content": "I'd be happy to help optimize your product titles...",
     *       "timestamp": 1234567892
     *     },
     *     "toolsUsed": ["search_products", "analyze_keywords"],
     *     "agentsInvoked": ["seo_analyst"],
     *     "metadata": {
     *       "tokensUsed": 1523,
     *       "processingTimeMs": 2340,
     *       "modelUsed": "claude-3-5-sonnet-20241022"
     *     }
     *   }
     * }
     */
    @PostMapping("/chat")
    public Mono<ResponseEntity<ApiResponse<SeoAgentResponse>>> chat(@RequestBody SeoAgentRequest request) {
        logger.info("Received SEO agent chat message: {}", request.getMessage());

        return seoAgentService.processChat(request)
                .map(response -> {
                    logger.info("SEO agent response generated successfully");
                    return ResponseEntity.ok(ApiResponse.success(
                            "Message processed successfully",
                            response
                    ));
                })
                .onErrorResume(error -> {
                    logger.error("Error processing SEO agent message: {}", error.getMessage());
                    return Mono.just(ResponseEntity.ok(ApiResponse.error(
                            "Failed to process message: " + error.getMessage()
                    )));
                });
    }

    /**
     * GET /api/seo-agent/status - Check if SEO agent is available
     *
     * Response:
     * {
     *   "success": true,
     *   "message": "SEO agent status",
     *   "data": {
     *     "available": true,
     *     "provider": "Anthropic Claude",
     *     "defaultModel": "claude-3-5-sonnet-20241022",
     *     "description": "AI-powered SEO optimization assistant"
     *   }
     * }
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getStatus() {
        logger.info("SEO agent status check");

        return ResponseEntity.ok(ApiResponse.success(
                "SEO agent status",
                new java.util.HashMap<String, Object>() {{
                    put("available", true);
                    put("provider", "Anthropic Claude");
                    put("defaultModel", "claude-3-5-sonnet-20241022");
                    put("description", "AI-powered SEO optimization assistant with configurable tools and agents");
                    put("features", new String[]{
                        "Product description optimization",
                        "Meta tag generation",
                        "Keyword analysis",
                        "Content strategy",
                        "Search visibility improvements"
                    });
                }}
        ));
    }
}
