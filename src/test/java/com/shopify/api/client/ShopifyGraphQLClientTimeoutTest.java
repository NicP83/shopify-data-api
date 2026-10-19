package com.shopify.api.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopify.api.config.ShopifyConfig;
import com.shopify.api.config.WebClientConfig;
import com.shopify.api.model.GraphQLRequest;
import com.shopify.api.model.GraphQLResponse;
import com.shopify.api.util.RateLimiter;
import com.sun.net.httpserver.HttpServer;
import io.netty.channel.ConnectTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Shopify timeouts against a local JDK HttpServer (no DB, no Shopify).
 */
class ShopifyGraphQLClientTimeoutTest {

    private static final String OK_BODY = "{\"data\":{\"shop\":{\"name\":\"Test\"}}}";

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    /** Maps the 1-based request number to a behaviour: HTTP status, or negative = sleep that many ms. */
    private volatile IntFunction<Integer> behaviour = n -> 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            int action = behaviour.apply(requests.incrementAndGet());
            try {
                if (action < 0) {
                    Thread.sleep(-action);
                    action = 200;
                }
                byte[] body = (action == 200 ? OK_BODY : "{\"errors\":\"busy\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(action, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } catch (InterruptedException | IOException ignored) {
                // client gave up; nothing to do
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private ShopifyGraphQLClient client(long readMs, long attemptMs, long totalMs, int maxRetries, long backoffMs) {
        ShopifyConfig config = new ShopifyConfig();
        config.getHttp().setConnectTimeoutMs(1000);
        config.getHttp().setReadTimeoutMs(readMs);
        config.getHttp().setResponseTimeoutMs(attemptMs);
        config.getHttp().setTotalTimeoutMs(totalMs);
        config.getRateLimit().setMaxRetries(maxRetries);
        config.getRateLimit().setInitialBackoffMs(backoffMs);
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/graphql.json";
        return new ShopifyGraphQLClient(
                WebClientConfig.buildShopifyWebClient(url, "test-token", config.getHttp()),
                config, new RateLimiter(config), new ObjectMapper());
    }

    private static String firstError(GraphQLResponse response) {
        assertThat(response).isNotNull();
        assertThat(response.hasErrors()).isTrue();
        return response.getErrors().get(0).getMessage();
    }

    @Test
    void slowResponseHitsPerAttemptTimeoutAndIsNotRetried() {
        behaviour = n -> -5000;
        ShopifyGraphQLClient client = client(10_000, 500, 3000, 3, 10);

        long start = System.currentTimeMillis();
        GraphQLResponse response = client.executeQuery(new GraphQLRequest("{ shop { name } }"));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(firstError(response)).isEqualTo("Shopify call timed out after 500ms (per-attempt timeout)");
        assertThat(elapsed).isLessThan(2500);
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void slowResponseTimesOutReactivelyToo() {
        behaviour = n -> -5000;
        ShopifyGraphQLClient client = client(10_000, 500, 3000, 3, 10);

        GraphQLResponse response = client.executeQueryReactive(new GraphQLRequest("{ shop { name } }")).block();

        assertThat(firstError(response)).isEqualTo("Shopify call timed out after 500ms (per-attempt timeout)");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void stalledReadHitsReadTimeout() {
        behaviour = n -> -5000;
        ShopifyGraphQLClient client = client(400, 10_000, 20_000, 3, 10);

        GraphQLResponse response = client.executeQuery(new GraphQLRequest("{ shop { name } }"));

        assertThat(firstError(response)).isEqualTo("Shopify call timed out after 400ms (read timeout)");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void retriesStopAtTotalTimeout() {
        behaviour = n -> 503;
        ShopifyGraphQLClient client = client(10_000, 10_000, 800, 20, 300);

        long start = System.currentTimeMillis();
        GraphQLResponse response = client.executeQuery(new GraphQLRequest("{ shop { name } }"));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(firstError(response)).isEqualTo("Shopify call timed out after 800ms (total timeout)");
        assertThat(elapsed).isLessThan(2500);
    }

    @Test
    void gatewayErrorsAreStillRetriedThenSucceed() {
        behaviour = n -> n <= 2 ? 503 : 200;
        ShopifyGraphQLClient client = client(5000, 5000, 10_000, 3, 10);

        GraphQLResponse response = client.executeQuery(new GraphQLRequest("{ shop { name } }"));

        assertThat(response.hasErrors()).isFalse();
        assertThat(response.getData()).isNotNull();
        assertThat(requests.get()).isEqualTo(3);
    }

    @Test
    void fastResponseIsUnaffected() {
        ShopifyGraphQLClient client = client(5000, 5000, 10_000, 3, 10);

        GraphQLResponse response = client.executeQueryReactive("{ shop { name } }").block();

        assertThat(response.hasErrors()).isFalse();
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void connectTimeoutIsDescribedAndNotRetried() {
        ShopifyConfig.Http http = new ShopifyConfig.Http();
        Exception wrapped = new RuntimeException("connect failed", new ConnectTimeoutException("connection timed out"));

        assertThat(ShopifyGraphQLClient.isTimeout(wrapped)).isTrue();
        assertThat(ShopifyGraphQLClient.describeTimeout(wrapped, http))
                .isEqualTo("Shopify call timed out after 5000ms (connect timeout)");
        assertThat(client(5000, 5000, 10_000, 3, 10).isRetryableError(wrapped)).isFalse();
    }
}
