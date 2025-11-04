package com.shopify.api.controller;

import com.shopify.api.model.ApiResponse;
import com.shopify.api.model.ChatAnalytics;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.repository.ChatAnalyticsRepository;
import com.shopify.api.repository.ShopifyShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple controller for viewing chat interaction logs
 */
@RestController
@RequestMapping("/api/logs")
@CrossOrigin(origins = "*")
public class ChatLogsController {

    private static final Logger logger = LoggerFactory.getLogger(ChatLogsController.class);

    @Autowired
    private ChatAnalyticsRepository chatAnalyticsRepository;

    @Autowired
    private ShopifyShopRepository shopifyShopRepository;

    /**
     * GET /api/logs/chat?shop=hearnshobbies.myshopify.com&page=0&size=50
     * Get paginated chat logs for a shop
     */
    @GetMapping("/chat")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getChatLogs(
            @RequestParam(defaultValue = "hearnshobbies.myshopify.com") String shop,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        logger.info("GET /api/logs/chat - shop: {}, page: {}, size: {}", shop, page, size);

        try {
            ShopifyShop shopEntity = shopifyShopRepository.findByShopDomain(shop)
                    .orElse(null);

            if (shopEntity == null) {
                return ResponseEntity.ok(ApiResponse.error("Shop not found: " + shop));
            }

            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<ChatAnalytics> logsPage = chatAnalyticsRepository.findByShop(shopEntity, pageRequest);

            Map<String, Object> data = new HashMap<>();
            data.put("logs", logsPage.getContent());
            data.put("currentPage", logsPage.getNumber());
            data.put("totalPages", logsPage.getTotalPages());
            data.put("totalItems", logsPage.getTotalElements());
            data.put("pageSize", logsPage.getSize());

            return ResponseEntity.ok(ApiResponse.success("Chat logs retrieved", data));

        } catch (Exception e) {
            logger.error("Error fetching chat logs", e);
            return ResponseEntity.ok(ApiResponse.error("Failed to fetch logs: " + e.getMessage()));
        }
    }

    /**
     * GET /api/logs/chat/{id}
     * Get detailed view of a single chat interaction
     */
    @GetMapping("/chat/{id}")
    public ResponseEntity<ApiResponse<ChatAnalytics>> getChatLogDetail(@PathVariable Long id) {
        logger.info("GET /api/logs/chat/{}", id);

        try {
            ChatAnalytics log = chatAnalyticsRepository.findById(id)
                    .orElse(null);

            if (log == null) {
                return ResponseEntity.ok(ApiResponse.error("Log not found"));
            }

            return ResponseEntity.ok(ApiResponse.success("Chat log detail", log));

        } catch (Exception e) {
            logger.error("Error fetching chat log detail", e);
            return ResponseEntity.ok(ApiResponse.error("Failed to fetch log: " + e.getMessage()));
        }
    }
}
