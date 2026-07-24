package com.shopify.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS (Cross-Origin Resource Sharing) configuration
 * Allows frontend React app to communicate with backend API during development
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * Comma-separated origin patterns allowed to call the external API
     * (/api/v1/**) and the embeddable widget endpoint (/api/embed/**).
     * Defaults to "*" since these are authenticated by API key, not by origin.
     */
    @Value("${external-api.allowed-origins:*}")
    private String externalAllowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // External API + embeddable widget: key-authenticated, so origin is
        // not the security boundary. Credentials off (no cookies).
        registry.addMapping("/api/v1/**")
                .allowedOriginPatterns(externalAllowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
        registry.addMapping("/api/embed/**")
                .allowedOriginPatterns(externalAllowedOrigins.split(","))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);

        // API endpoints - for hearnshobbies.com storefront and Shopify chat
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "https://hearnshobbies.com",
                        "https://www.hearnshobbies.com",
                        "https://*.myshopify.com",
                        "https://*.up.railway.app",  // Railway production
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
