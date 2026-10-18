package com.shopify.api.config;

import com.shopify.api.filter.AdminAuthFilter;
import com.shopify.api.filter.ApiKeyAuthFilter;
import com.shopify.api.service.ApiKeyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the API-key filters.
 *  - {@link ApiKeyAuthFilter}: the external surface (/api/v1/*, /api/embed/*).
 *  - {@link AdminAuthFilter}: the rest of /api/* except its public allowlist
 *    (storefront chat, public workflows, health). Mode via ADMIN_AUTH_MODE.
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

    @Bean
    public FilterRegistrationBean<AdminAuthFilter> adminAuthFilter(
            ApiKeyService apiKeyService,
            @Value("${admin.bootstrap-key:#{null}}") String bootstrapKey,
            @Value("${admin.auth.mode:report}") String mode) {
        FilterRegistrationBean<AdminAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AdminAuthFilter(apiKeyService, bootstrapKey, AdminAuthFilter.parseMode(mode)));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(2);
        return registration;
    }
}
