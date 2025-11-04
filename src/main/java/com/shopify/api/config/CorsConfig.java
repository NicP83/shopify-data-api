package com.shopify.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS (Cross-Origin Resource Sharing) configuration
 * Allows frontend React app to communicate with backend API during development
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // API endpoints - for hearnshobbies.com storefront and Shopify chat
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "https://hearnshobbies.com",
                        "https://www.hearnshobbies.com",
                        "https://*.myshopify.com",
                        "http://localhost:5173",  // Development - frontend
                        "http://localhost:8080"   // Development - backend
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        // Shopify OAuth endpoints - allow Shopify admin domains
        registry.addMapping("/shopify/**")
                .allowedOriginPatterns("https://*.myshopify.com")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
