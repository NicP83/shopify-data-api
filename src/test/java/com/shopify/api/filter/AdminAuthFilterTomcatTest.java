package com.shopify.api.filter;

import com.shopify.api.service.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the filter in real embedded Tomcat (no DB) to check the /api/* registration
 * and the container's path normalisation, which the mock-request tests can't see.
 */
@SpringBootTest(
    classes = AdminAuthFilterTomcatTest.TestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAuthFilterTomcatTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class, FlywayAutoConfiguration.class})
    static class TestApp {
        @Bean
        FilterRegistrationBean<AdminAuthFilter> adminAuthFilter() {
            ApiKeyService keys = mock(ApiKeyService.class);
            when(keys.validate(anyString())).thenReturn(Optional.empty());
            // Same registration as ApiKeyFilterConfig, in enforce mode
            FilterRegistrationBean<AdminAuthFilter> reg = new FilterRegistrationBean<>();
            reg.setFilter(new AdminAuthFilter(keys, "boot-key", AdminAuthFilter.Mode.ENFORCE));
            reg.addUrlPatterns("/api/*");
            reg.setOrder(2);
            return reg;
        }

        @Bean
        Echo echo() {
            return new Echo();
        }
    }

    @RestController
    static class Echo {
        @RequestMapping("/**")
        String any() {
            return "reached";
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    FilterRegistrationBean<AdminAuthFilter> registration;

    private int status(String method, String path, String key) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, HttpRequest.BodyPublishers.noBody());
        if (key != null) b.header("X-API-Key", key);
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** Raw request line, so ".." reaches Tomcat un-normalised by the HTTP client. */
    private String rawStatusLine(String method, String path) throws IOException {
        try (Socket s = new Socket("localhost", port)) {
            s.getOutputStream().write((method + " " + path + " HTTP/1.1\r\nHost: localhost\r\n"
                + "Content-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String resp = new String(s.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            return resp.substring(0, resp.indexOf("\r\n"));
        }
    }

    @Test
    void publicRoutesReachTheController() throws Exception {
        assertThat(status("POST", "/api/shopify/chat/message?shop=x.myshopify.com", null)).isEqualTo(200);
        assertThat(status("POST", "/api/shopify/chat/message/stream", null)).isEqualTo(200);
        assertThat(status("POST", "/api/workflows/public/3/execute", null)).isEqualTo(200);
        assertThat(status("GET", "/api/health", null)).isEqualTo(200);
        assertThat(status("GET", "/shopify/callback?code=x", null)).isEqualTo(200);
        assertThat(status("GET", "/assets/index.js", null)).isEqualTo(200);
        assertThat(status("GET", "/workflows", null)).isEqualTo(200);
    }

    @Test
    void adminRoutesNeedAKey() throws Exception {
        assertThat(status("PUT", "/api/config/chatbot", null)).isEqualTo(401);
        assertThat(status("POST", "/api/agents/23/execute", null)).isEqualTo(401);
        assertThat(status("PUT", "/api/config/chatbot", "boot-key")).isEqualTo(200);
    }

    @Test
    void dotDotFromPublicPrefixIsNotAWayIn() throws Exception {
        String line = rawStatusLine("PUT", "/api/shopify/chat/../../config/chatbot");
        assertThat(line).doesNotContain(" 200");
        String encoded = rawStatusLine("PUT", "/api/shopify/chat/%2e%2e/%2e%2e/config/chatbot");
        assertThat(encoded).doesNotContain(" 200");
    }

    @Test
    void registeredOnApiOnly() {
        assertThat(registration.getUrlPatterns()).containsExactly("/api/*");
    }
}
