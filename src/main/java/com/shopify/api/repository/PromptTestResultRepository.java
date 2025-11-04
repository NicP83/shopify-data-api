package com.shopify.api.repository;

import com.shopify.api.model.PromptTestResult;
import com.shopify.api.model.ShopifyShop;
import com.shopify.api.model.SystemPrompt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Repository interface for PromptTestResult entity.
 * Provides database access for prompt testing results and analytics.
 */
@Repository
public interface PromptTestResultRepository extends JpaRepository<PromptTestResult, Long> {

    /**
     * Find all test results for a shop
     */
    Page<PromptTestResult> findByShopOrderByTestedAtDesc(ShopifyShop shop, Pageable pageable);

    /**
     * Find test results for a specific prompt
     */
    List<PromptTestResult> findByPromptOrderByTestedAtDesc(SystemPrompt prompt);

    /**
     * Find test results by test mode
     */
    List<PromptTestResult> findByShopAndTestModeOrderByTestedAtDesc(
            ShopifyShop shop,
            String testMode
    );

    /**
     * Find test results by tester
     */
    List<PromptTestResult> findByTestedByOrderByTestedAtDesc(String testedBy);

    /**
     * Find recent test results (last N days)
     */
    @Query("SELECT t FROM PromptTestResult t WHERE t.shop = :shop " +
           "AND t.testedAt >= :since ORDER BY t.testedAt DESC")
    List<PromptTestResult> findRecentTests(
            @Param("shop") ShopifyShop shop,
            @Param("since") ZonedDateTime since
    );

    /**
     * Find tests with high ratings (4-5 stars)
     */
    @Query("SELECT t FROM PromptTestResult t WHERE t.shop = :shop " +
           "AND t.testerRating >= 4 ORDER BY t.testedAt DESC")
    List<PromptTestResult> findHighRatedTests(
            @Param("shop") ShopifyShop shop,
            Pageable pageable
    );

    /**
     * Find tests with low ratings (1-2 stars)
     */
    @Query("SELECT t FROM PromptTestResult t WHERE t.shop = :shop " +
           "AND t.testerRating <= 2 ORDER BY t.testedAt DESC")
    List<PromptTestResult> findLowRatedTests(
            @Param("shop") ShopifyShop shop,
            Pageable pageable
    );

    /**
     * Calculate average rating for a prompt
     */
    @Query("SELECT AVG(t.testerRating) FROM PromptTestResult t " +
           "WHERE t.prompt = :prompt AND t.testerRating IS NOT NULL")
    Double getAverageRatingForPrompt(@Param("prompt") SystemPrompt prompt);

    /**
     * Calculate average response time for a prompt
     */
    @Query("SELECT AVG(t.responseTimeMs) FROM PromptTestResult t " +
           "WHERE t.prompt = :prompt")
    Double getAverageResponseTimeForPrompt(@Param("prompt") SystemPrompt prompt);

    /**
     * Calculate total API cost for tests
     */
    @Query("SELECT SUM(t.apiCostUsd) FROM PromptTestResult t " +
           "WHERE t.shop = :shop AND t.testedAt >= :since")
    BigDecimal getTotalApiCost(
            @Param("shop") ShopifyShop shop,
            @Param("since") ZonedDateTime since
    );

    /**
     * Count tests by mode for a shop
     */
    @Query("SELECT t.testMode, COUNT(t) FROM PromptTestResult t " +
           "WHERE t.shop = :shop AND t.testedAt >= :since " +
           "GROUP BY t.testMode")
    List<Object[]> countTestsByMode(
            @Param("shop") ShopifyShop shop,
            @Param("since") ZonedDateTime since
    );

    /**
     * Get test statistics for a shop
     */
    @Query("SELECT AVG(t.responseTimeMs), AVG(t.testerRating), " +
           "AVG(t.autoQualityScore), AVG(t.productsReturned) " +
           "FROM PromptTestResult t WHERE t.shop = :shop AND t.testedAt >= :since")
    Object[] getTestStatistics(
            @Param("shop") ShopifyShop shop,
            @Param("since") ZonedDateTime since
    );

    /**
     * Find slow tests (response time > threshold)
     */
    @Query("SELECT t FROM PromptTestResult t WHERE t.shop = :shop " +
           "AND t.responseTimeMs > :thresholdMs ORDER BY t.responseTimeMs DESC")
    List<PromptTestResult> findSlowTests(
            @Param("shop") ShopifyShop shop,
            @Param("thresholdMs") Integer thresholdMs,
            Pageable pageable
    );

    /**
     * Find tests with no products returned
     */
    @Query("SELECT t FROM PromptTestResult t WHERE t.shop = :shop " +
           "AND (t.productsReturned IS NULL OR t.productsReturned = 0) " +
           "ORDER BY t.testedAt DESC")
    List<PromptTestResult> findTestsWithNoProducts(
            @Param("shop") ShopifyShop shop,
            Pageable pageable
    );

    /**
     * Compare two prompts by test results
     */
    @Query("SELECT AVG(t.testerRating), AVG(t.responseTimeMs), AVG(t.productsReturned) " +
           "FROM PromptTestResult t WHERE t.prompt = :prompt AND t.testedAt >= :since")
    Object[] getPromptPerformanceMetrics(
            @Param("prompt") SystemPrompt prompt,
            @Param("since") ZonedDateTime since
    );

    /**
     * Find most tested queries
     */
    @Query("SELECT t.testQuery, COUNT(t) as cnt FROM PromptTestResult t " +
           "WHERE t.shop = :shop AND t.testedAt >= :since " +
           "GROUP BY t.testQuery ORDER BY cnt DESC")
    List<Object[]> findMostTestedQueries(
            @Param("shop") ShopifyShop shop,
            @Param("since") ZonedDateTime since,
            Pageable pageable
    );

    /**
     * Count tests for a prompt
     */
    Long countByPrompt(SystemPrompt prompt);

    /**
     * Count tests for a shop in date range
     */
    Long countByShopAndTestedAtBetween(
            ShopifyShop shop,
            ZonedDateTime startDate,
            ZonedDateTime endDate
    );

    /**
     * Find tests by quality score range
     */
    @Query("SELECT t FROM PromptTestResult t WHERE t.shop = :shop " +
           "AND t.autoQualityScore BETWEEN :minScore AND :maxScore " +
           "ORDER BY t.testedAt DESC")
    List<PromptTestResult> findByQualityScoreRange(
            @Param("shop") ShopifyShop shop,
            @Param("minScore") BigDecimal minScore,
            @Param("maxScore") BigDecimal maxScore
    );

    /**
     * Get performance trend over time
     */
    @Query("SELECT DATE(t.testedAt), AVG(t.responseTimeMs), AVG(t.testerRating) " +
           "FROM PromptTestResult t WHERE t.shop = :shop AND t.testedAt >= :since " +
           "GROUP BY DATE(t.testedAt) ORDER BY DATE(t.testedAt)")
    List<Object[]> getPerformanceTrend(
            @Param("shop") ShopifyShop shop,
            @Param("since") ZonedDateTime since
    );

    /**
     * Find test results for a shop and prompt (used by controller)
     */
    Page<PromptTestResult> findByShopAndPromptOrderByTestedAtDesc(
            ShopifyShop shop,
            SystemPrompt prompt,
            Pageable pageable
    );

    /**
     * Count all tests for a shop
     */
    Long countByShop(ShopifyShop shop);

    /**
     * Count tests with quality score >= threshold
     */
    Long countByShopAndAutoQualityScoreGreaterThanEqual(
            ShopifyShop shop,
            BigDecimal minScore
    );

    /**
     * Get average response time for a shop
     */
    @Query("SELECT AVG(t.responseTimeMs) FROM PromptTestResult t WHERE t.shop = :shop")
    Double getAverageResponseTimeByShop(@Param("shop") ShopifyShop shop);

    /**
     * Count tests by shop and mode
     */
    Long countByShopAndTestMode(ShopifyShop shop, String testMode);

    /**
     * Find tests with low quality scores
     */
    List<PromptTestResult> findByShopAndAutoQualityScoreLessThan(
            ShopifyShop shop,
            BigDecimal maxScore
    );

    /**
     * Delete old test results
     */
    @Modifying
    @Query("DELETE FROM PromptTestResult t WHERE t.shop = :shop " +
           "AND t.testedAt < :cutoffDate")
    int deleteByShopAndTestedAtBefore(
            @Param("shop") ShopifyShop shop,
            @Param("cutoffDate") ZonedDateTime cutoffDate
    );
}
