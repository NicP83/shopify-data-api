package com.shopify.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenEntityManagerInViewConfigTest {

    /** Exposes the protected interceptor list so the path mapping can be checked. */
    static class ExposedRegistry extends InterceptorRegistry {
        List<Object> interceptors() {
            return getInterceptors();
        }
    }

    private MappedInterceptor registeredOsiv() {
        ExposedRegistry registry = new ExposedRegistry();
        new OpenEntityManagerInViewConfig().addInterceptors(registry);
        List<Object> interceptors = registry.interceptors();
        assertThat(interceptors).hasSize(1);
        MappedInterceptor mapped = (MappedInterceptor) interceptors.get(0);
        assertThat(mapped.getInterceptor()).isInstanceOf(WebRequestHandlerInterceptorAdapter.class);
        return mapped;
    }

    private static boolean applies(MappedInterceptor interceptor, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        ServletRequestPathUtils.parseAndCache(request);
        return interceptor.matches(request);
    }

    @Test
    void storefrontChatRoutesAreExcludedFromOsiv() {
        MappedInterceptor osiv = registeredOsiv();
        assertThat(applies(osiv, "/api/shopify/chat/message")).isFalse();
        assertThat(applies(osiv, "/api/shopify/chat/message/stream")).isFalse();
    }

    @Test
    void adminRoutesKeepOsiv() {
        MappedInterceptor osiv = registeredOsiv();
        assertThat(applies(osiv, "/api/agents/23")).isTrue();
        assertThat(applies(osiv, "/api/workflows")).isTrue();
        assertThat(applies(osiv, "/api/shopify/products")).isTrue();
    }

    @Test
    void interceptorBeanIsTheOsivInterceptor() {
        assertThat(new OpenEntityManagerInViewConfig().openEntityManagerInViewInterceptor())
                .isInstanceOf(OpenEntityManagerInViewInterceptor.class);
    }
}
