package com.shopify.api.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Spring Cache
 * Enables in-memory caching for frequently accessed data like chatbot configuration
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configure cache manager with named caches
     * Using simple in-memory cache (ConcurrentMapCacheManager)
     * For production with multiple instances, consider Redis or similar
     */
    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
            "chatbotConfig",      // Cache for chatbot configuration
            "productSearch",      // Cache for product search results (future use)
            "shopConfig"          // Cache for shop-specific configuration (future use)
        );

        // Allow cache creation on demand for flexibility
        cacheManager.setAllowNullValues(false); // Don't cache null values

        return cacheManager;
    }
}
