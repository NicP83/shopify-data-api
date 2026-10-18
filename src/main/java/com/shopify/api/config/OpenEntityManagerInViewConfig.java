package com.shopify.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Open-EntityManager-in-View for every route EXCEPT the storefront chat.
 *
 * Replaces Spring Boot's default (spring.jpa.open-in-view, now false in application.yml), which
 * applied OSIV to all routes. Under OSIV, Spring's HibernateJpaVendorAdapter sets
 * DELAYED_ACQUISITION_AND_HOLD, so the first DB read on the request thread (the chat
 * controller's shop lookup) pins a pooled connection until the whole async request ends —
 * up to the 180s MVC async timeout for an SSE stream — and on an async request that ends
 * without a final dispatch (e.g. a client disconnect) it may never be released. That is the
 * leading suspect for the Oct 2026 Hikari pool exhaustion.
 *
 * The chat controller only reads scalar fields of ShopifyShop (no lazy associations), so it
 * doesn't need OSIV. Admin routes keep it unchanged, because some controllers serialize
 * entities with lazy associations.
 */
@Configuration
public class OpenEntityManagerInViewConfig implements WebMvcConfigurer {

    /** Storefront chat routes that must not pin a DB connection for the life of the request. */
    static final String[] OSIV_EXCLUDED_PATHS = {"/api/shopify/chat/**"};

    @Bean
    public OpenEntityManagerInViewInterceptor openEntityManagerInViewInterceptor() {
        return new OpenEntityManagerInViewInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addWebRequestInterceptor(openEntityManagerInViewInterceptor())
                .excludePathPatterns(OSIV_EXCLUDED_PATHS);
    }
}
