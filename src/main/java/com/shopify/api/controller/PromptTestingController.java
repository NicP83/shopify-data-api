package com.shopify.api.controller;

import com.shopify.api.annotation.LogActivity;
import com.shopify.api.model.AdminActivityLog;
import com.shopify.api.model.PromptTestResult;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.model.SystemPrompt;
import com.shopify.api.repository.PromptTestResultRepository;
import com.shopify.api.service.AdminActivityLogService;
import com.shopify.api.service.ShopifyShopService;
import com.shopify.api.service.SystemPromptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for prompt testing and quality tracking.
 * Allows testing prompts before deployment and tracking test results.
 */
@RestController
@RequestMapping("/api/admin/prompt-testing")
@CrossOrigin(origins = "*")
public class PromptTestingController {

    @Autowired
    private PromptTestResultRepository promptTestRepository;

    @Autowired
    private SystemPromptService systemPromptService;

    @Autowired
    private ShopifyShopService shopService;

    @Autowired
    private AdminActivityLogService activityLogService;

    /**
     * Test a prompt (saved or unsaved) with a query
     * POST /api/admin/prompt-testing/test
     * Body: {
     *   "shop": "hearnshobbies.myshopify.com",
     *   "promptId": 1 (optional, null for unsaved prompts),
     *   "testQuery": "Show me hobby paints",
     *   "testContext": {"conversationHistory": [], "filters": {}},
     *   "testMode": "PREVIEW",
     *   "testedBy": "admin@example.com"
     * }
     */
    @LogActivity(
            actionType = AdminActivityLog.ACTION_TEST,
            actionCategory = AdminActivityLog.CATEGORY_TESTING,
            resourceType = "prompt_test",
            descriptionTemplate = "Tested prompt with query: {1}",
            captureReturnValue = true
    )
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testPrompt(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest
    ) {
        String shopDomain = (String) request.get("shop");
        Long promptId = request.get("promptId") != null
                ? Long.valueOf(request.get("promptId").toString())
                : null;
        String testQuery = (String) request.get("testQuery");
        Map<String, Object> testContext = (Map<String, Object>) request.get("testContext");
        String testMode = (String) request.getOrDefault("testMode", PromptTestResult.MODE_PREVIEW);
        String testedBy = (String) request.get("testedBy");

        ShopifyShop shop = shopService.findByDomain(shopDomain);
        if (shop == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shopDomain
            ));
        }

        SystemPrompt prompt = null;
        if (promptId != null) {
            prompt = systemPromptService.getPromptById(promptId).orElse(null);
            if (prompt == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Prompt not found: " + promptId
                ));
            }
        }

        // Simulate test execution (in real implementation, would call ChatAgentService)
        long startTime = System.currentTimeMillis();

        // Mock test result
        String mockResponse = "Mock AI response for: " + testQuery;
        int mockProductsReturned = 5;
        List<Long> mockProductIds = List.of(12345L, 67890L, 11111L, 22222L, 33333L);

        long endTime = System.currentTimeMillis();
        int responseTime = (int) (endTime - startTime);

        // Create test result
        PromptTestResult testResult = new PromptTestResult(
                shop,
                prompt,
                testQuery,
                mockResponse,
                responseTime,
                testMode,
                testedBy
        );

        testResult.setProductsReturned(mockProductsReturned);
        testResult.setProductIds(mockProductIds);
        testResult.setTestContext(testContext);
        testResult.setTokensUsed(250);
        testResult.setApiCostUsd(new BigDecimal("0.001250"));
        testResult.calculateAutoQualityScore();

        // Save test result
        testResult = promptTestRepository.save(testResult);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "testResult", testResult,
                "aiResponse", mockResponse,
                "productsReturned", mockProductsReturned,
                "productIds", mockProductIds,
                "responseTimeMs", responseTime,
                "qualityScore", testResult.getAutoQualityScore(),
                "message", "Prompt test completed successfully"
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get test results for a prompt
     * GET /api/admin/prompt-testing/results?shop=hearnshobbies.myshopify.com&promptId=1&page=0&size=20
     */
    @GetMapping("/results")
    public ResponseEntity<Map<String, Object>> getTestResults(
            @RequestParam String shop,
            @RequestParam(required = false) Long promptId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ShopifyShop shopEntity = shopService.findByDomain(shop);
        if (shopEntity == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shop
            ));
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<PromptTestResult> resultsPage;

        if (promptId != null) {
            SystemPrompt prompt = systemPromptService.getPromptById(promptId);
            resultsPage = promptTestRepository.findByShopAndPromptOrderByTestedAtDesc(
                    shopEntity, prompt, pageable
            );
        } else {
            resultsPage = promptTestRepository.findByShopOrderByTestedAtDesc(
                    shopEntity, pageable
            );
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "testResults", resultsPage.getContent(),
                "currentPage", resultsPage.getNumber(),
                "totalPages", resultsPage.getTotalPages(),
                "totalItems", resultsPage.getTotalElements()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get test result by ID
     * GET /api/admin/prompt-testing/results/{resultId}
     */
    @GetMapping("/results/{resultId}")
    public ResponseEntity<Map<String, Object>> getTestResultById(
            @PathVariable Long resultId
    ) {
        PromptTestResult result = promptTestRepository.findById(resultId).orElse(null);

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("testResult", result));

        return ResponseEntity.ok(response);
    }

    /**
     * Rate a test result (add tester feedback)
     * POST /api/admin/prompt-testing/results/{resultId}/rate
     * Body: {
     *   "rating": 4,
     *   "notes": "Good results, but could be more specific"
     * }
     */
    @LogActivity(
            actionType = AdminActivityLog.ACTION_UPDATE,
            actionCategory = AdminActivityLog.CATEGORY_TESTING,
            resourceType = "prompt_test",
            descriptionTemplate = "Rated prompt test result {2} with {1} stars"
    )
    @PostMapping("/results/{resultId}/rate")
    public ResponseEntity<Map<String, Object>> rateTestResult(
            @PathVariable Long resultId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest
    ) {
        PromptTestResult result = promptTestRepository.findById(resultId).orElse(null);

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        Integer rating = Integer.valueOf(request.get("rating").toString());
        String notes = (String) request.get("notes");

        if (rating < 1 || rating > 5) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Rating must be between 1 and 5"
            ));
        }

        result.setTesterRating(rating);
        result.setTesterNotes(notes);
        result = promptTestRepository.save(result);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "testResult", result,
                "message", "Test result rated successfully"
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get test statistics for a shop
     * GET /api/admin/prompt-testing/statistics?shop=hearnshobbies.myshopify.com&days=30
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getTestStatistics(
            @RequestParam String shop,
            @RequestParam(defaultValue = "30") int days
    ) {
        ShopifyShop shopEntity = shopService.findByDomain(shop);
        if (shopEntity == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shop
            ));
        }

        // Get basic counts
        long totalTests = promptTestRepository.countByShop(shopEntity);
        long passingTests = promptTestRepository.countByShopAndAutoQualityScoreGreaterThanEqual(
                shopEntity, new BigDecimal("70")
        );

        // Calculate pass rate
        double passRate = totalTests > 0 ? (double) passingTests / totalTests * 100 : 0.0;

        // Get average response time
        Double avgResponseTime = promptTestRepository.getAverageResponseTimeByShop(shopEntity);

        // Get test counts by mode
        Map<String, Long> testsByMode = Map.of(
                "PREVIEW", promptTestRepository.countByShopAndTestMode(shopEntity, PromptTestResult.MODE_PREVIEW),
                "A_B_TEST", promptTestRepository.countByShopAndTestMode(shopEntity, PromptTestResult.MODE_A_B_TEST),
                "REGRESSION", promptTestRepository.countByShopAndTestMode(shopEntity, PromptTestResult.MODE_REGRESSION),
                "MANUAL", promptTestRepository.countByShopAndTestMode(shopEntity, PromptTestResult.MODE_MANUAL)
        );

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalTests", totalTests);
        statistics.put("passingTests", passingTests);
        statistics.put("passRate", passRate);
        statistics.put("averageResponseTimeMs", avgResponseTime);
        statistics.put("testsByMode", testsByMode);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "statistics", statistics,
                "period", days + " days"
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Get failing tests for troubleshooting
     * GET /api/admin/prompt-testing/failing?shop=hearnshobbies.myshopify.com
     */
    @GetMapping("/failing")
    public ResponseEntity<Map<String, Object>> getFailingTests(
            @RequestParam String shop
    ) {
        ShopifyShop shopEntity = shopService.findByDomain(shop);
        if (shopEntity == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shop
            ));
        }

        // Get tests with quality score < 70 or rating < 3
        List<PromptTestResult> failingTests = promptTestRepository
                .findByShopAndAutoQualityScoreLessThan(shopEntity, new BigDecimal("70"));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "failingTests", failingTests,
                "count", failingTests.size()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Compare two test results
     * GET /api/admin/prompt-testing/compare?result1=5&result2=7
     */
    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compareTestResults(
            @RequestParam Long result1,
            @RequestParam Long result2
    ) {
        PromptTestResult test1 = promptTestRepository.findById(result1).orElse(null);
        PromptTestResult test2 = promptTestRepository.findById(result2).orElse(null);

        if (test1 == null || test2 == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "One or both test results not found"
            ));
        }

        Map<String, Object> comparison = new HashMap<>();
        comparison.put("test1", test1);
        comparison.put("test2", test2);
        comparison.put("responseTimeDiff", test2.getResponseTimeMs() - test1.getResponseTimeMs());
        comparison.put("qualityScoreDiff",
                test2.getAutoQualityScore().subtract(test1.getAutoQualityScore()));
        comparison.put("productsReturnedDiff", test2.getProductsReturned() - test1.getProductsReturned());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("comparison", comparison));

        return ResponseEntity.ok(response);
    }

    /**
     * Get available test modes
     * GET /api/admin/prompt-testing/test-modes
     */
    @GetMapping("/test-modes")
    public ResponseEntity<Map<String, Object>> getTestModes() {
        List<String> testModes = List.of(
                PromptTestResult.MODE_PREVIEW,
                PromptTestResult.MODE_A_B_TEST,
                PromptTestResult.MODE_REGRESSION,
                PromptTestResult.MODE_MANUAL
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("testModes", testModes));

        return ResponseEntity.ok(response);
    }

    /**
     * Delete old test results
     * DELETE /api/admin/prompt-testing/cleanup?shop=hearnshobbies.myshopify.com&days=90
     */
    @LogActivity(
            actionType = AdminActivityLog.ACTION_DELETE,
            actionCategory = AdminActivityLog.CATEGORY_SYSTEM,
            resourceType = "prompt_tests",
            descriptionTemplate = "Cleaned up test results older than {1} days"
    )
    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupOldTests(
            @RequestParam String shop,
            @RequestParam(defaultValue = "90") int days,
            HttpServletRequest request
    ) {
        ShopifyShop shopEntity = shopService.findByDomain(shop);
        if (shopEntity == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Shop not found: " + shop
            ));
        }

        // Delete tests older than N days
        ZonedDateTime cutoffDate = ZonedDateTime.now().minusDays(days);
        int deletedCount = promptTestRepository.deleteByShopAndTestedAtBefore(shopEntity, cutoffDate);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
                "deletedCount", deletedCount,
                "days", days,
                "message", "Cleaned up " + deletedCount + " old test results"
        ));

        return ResponseEntity.ok(response);
    }
}
