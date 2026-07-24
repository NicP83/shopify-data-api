package com.shopify.api.config;

import com.shopify.api.filter.ApiKeyAuthFilter;
import com.shopify.api.service.ApiKeyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link ApiKeyAuthFilter} on the authenticated surface only.
 * Existing admin routes and the storefront chat endpoint are intentionally
 * not covered here (see Phase 5 for full admin lockdown).
 */
@Configuration
public class ApiKeyFilterConfig {

    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(
            ApiKeyService apiKeyService,
            @Value("${admin.bootstrap-key:#{null}}") String bootstrapKey) {
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ApiKeyAuthFilter(apiKeyService, bootstrapKey));
        registration.addUrlPatterns("/api/v1/*", "/api/embed/*");
        registration.setOrder(1);
        return registration;
    }
}
