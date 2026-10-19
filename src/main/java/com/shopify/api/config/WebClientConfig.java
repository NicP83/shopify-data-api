package com.shopify.api.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * Configuration for WebClient used to make HTTP requests to Shopify API
 */
@Configuration
public class WebClientConfig {

    private final ShopifyConfig shopifyConfig;

    public WebClientConfig(ShopifyConfig shopifyConfig) {
        this.shopifyConfig = shopifyConfig;
    }

    /**
     * Creates a pre-configured WebClient for Shopify GraphQL API calls
     * Includes:
     * - Base URL to Shopify GraphQL endpoint
     * - Authorization header with access token
     * - Content-Type header for JSON
     * - Connect, read and response timeouts (shopify.http.*)
     */
    @Bean
    public WebClient shopifyWebClient() {
        return buildShopifyWebClient(
                shopifyConfig.getGraphQLEndpoint(),
                shopifyConfig.getAccessToken(),
                shopifyConfig.getHttp());
    }

    /**
     * Builds the Shopify WebClient on its own Reactor Netty connection pool,
     * so the timeouts here never affect the Anthropic or MCP clients.
     */
    public static WebClient buildShopifyWebClient(String baseUrl, String accessToken, ShopifyConfig.Http http) {
        ConnectionProvider pool = ConnectionProvider.builder("shopify")
                .maxIdleTime(Duration.ofMillis(http.getMaxIdleMs()))
                .build();

        HttpClient httpClient = HttpClient.create(pool)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) http.getConnectTimeoutMs())
                // Reactor Netty's responseTimeout is a read timeout: max gap between reads
                // while a response is arriving. The per-attempt and total limits are
                // enforced in ShopifyGraphQLClient.
                .responseTimeout(Duration.ofMillis(http.getReadTimeoutMs()));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Shopify-Access-Token", accessToken)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024)) // 16MB buffer for large responses
                .build();
    }
}
