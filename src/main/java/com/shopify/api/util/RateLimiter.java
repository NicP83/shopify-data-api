package com.shopify.api.util;

import com.shopify.api.config.ShopifyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiter for Shopify API requests
 * Implements token bucket algorithm to stay within Shopify's rate limits
 */
@Component
public class RateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiter.class);

    private final ShopifyConfig shopifyConfig;
    private final AtomicInteger availablePoints;
    private final AtomicLong lastRefillTime;

    public RateLimiter(ShopifyConfig shopifyConfig) {
        this.shopifyConfig = shopifyConfig;
        this.availablePoints = new AtomicInteger(shopifyConfig.getRateLimit().getMaxPointsPerSecond());
        this.lastRefillTime = new AtomicLong(Instant.now().toEpochMilli());
    }

    /**
     * Wait if necessary to stay within rate limits
     * @param estimatedCost Estimated cost of the query in points
     */
    public synchronized void waitIfNecessary(int estimatedCost) {
        refillPoints();

        while (availablePoints.get() < estimatedCost) {
            long waitTime = calculateWaitTime(estimatedCost);
            if (waitTime > 0) {
                logger.debug("Rate limit reached. Waiting {} ms before next request", waitTime);
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Rate limiter interrupted", e);
                }
            }
            refillPoints();
        }

        availablePoints.addAndGet(-estimatedCost);
        logger.debug("Consumed {} points. {} points remaining", estimatedCost, availablePoints.get());
    }

    /**
     * Record actual query cost from Shopify response
     * @param actualCost The actual cost returned by Shopify
     */
    public void recordActualCost(int actualCost) {
        logger.debug("Query actual cost: {} points", actualCost);
    }

    /**
     * Refill points based on time elapsed
     */
    private void refillPoints() {
        long now = Instant.now().toEpochMilli();
        long lastRefill = lastRefillTime.get();
        long elapsedMs = now - lastRefill;

        if (elapsedMs >= 1000) {
            int maxPoints = shopifyConfig.getRateLimit().getMaxPointsPerSecond();
            int secondsElapsed = (int) (elapsedMs / 1000);
            int pointsToAdd = secondsElapsed * maxPoints;

            availablePoints.updateAndGet(current ->
                    Math.min(current + pointsToAdd, maxPoints));

            lastRefillTime.set(now);
            logger.debug("Refilled points. Available: {}", availablePoints.get());
        }
    }

    /**
     * Calculate how long to wait before next request
     */
    private long calculateWaitTime(int requiredPoints) {
        int deficit = requiredPoints - availablePoints.get();
        if (deficit <= 0) return 0;

        int maxPointsPerSecond = shopifyConfig.getRateLimit().getMaxPointsPerSecond();
        return (long) Math.ceil((double) deficit / maxPointsPerSecond * 1000);
    }

    /**
     * Get current available points
     */
    public int getAvailablePoints() {
        refillPoints();
        return availablePoints.get();
    }
}
