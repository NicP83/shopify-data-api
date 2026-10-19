package com.shopify.api.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopify.api.config.ShopifyConfig;
import com.shopify.api.model.GraphQLRequest;
import com.shopify.api.model.GraphQLResponse;
import com.shopify.api.util.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Client for making GraphQL requests to Shopify Admin API
 * Handles:
 * - Request execution
 * - Rate limiting
 * - Automatic retries with exponential backoff
 * - Per-attempt and total timeouts (shopify.http.*)
 * - Error handling
 */
@Component
public class ShopifyGraphQLClient {

    private static final Logger logger = LoggerFactory.getLogger(ShopifyGraphQLClient.class);
    private static final int DEFAULT_QUERY_COST = 10; // Conservative estimate

    private final WebClient webClient;
    private final ShopifyConfig shopifyConfig;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public ShopifyGraphQLClient(WebClient shopifyWebClient,
                                ShopifyConfig shopifyConfig,
                                RateLimiter rateLimiter,
                                ObjectMapper objectMapper) {
        this.webClient = shopifyWebClient;
        this.shopifyConfig = shopifyConfig;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    /**
     * Execute a GraphQL query
     * @param query The GraphQL query string
     * @return GraphQLResponse containing data or errors
     */
    public GraphQLResponse executeQuery(String query) {
        return executeQuery(new GraphQLRequest(query));
    }

    /**
     * Execute a GraphQL query with variables
     * @param request The GraphQL request with query and variables
     * @return GraphQLResponse containing data or errors
     */
    public GraphQLResponse executeQuery(GraphQLRequest request) {
        logger.info("Executing Shopify GraphQL query");
        logger.debug("Query: {}", request.getQuery());

        // Wait if necessary to respect rate limits
        rateLimiter.waitIfNecessary(DEFAULT_QUERY_COST);

        try {
            GraphQLResponse response = webClient.post()
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(GraphQLResponse.class)
                    .timeout(attemptTimeout())
                    .retryWhen(Retry.backoff(
                            shopifyConfig.getRateLimit().getMaxRetries(),
                            Duration.ofMillis(shopifyConfig.getRateLimit().getInitialBackoffMs())
                    ).filter(this::isRetryableError))
                    .timeout(totalTimeout())
                    .onErrorResume(throwable -> Mono.just(handleCallError(throwable)))
                    // Hard backstop: never hold the calling thread past the total timeout
                    .block(totalTimeout().plusSeconds(5));

            if (response != null) {
                // Record actual query cost if available
                Integer actualCost = response.getQueryCost();
                if (actualCost != null) {
                    rateLimiter.recordActualCost(actualCost);
                }

                if (response.hasErrors()) {
                    logger.error("GraphQL query returned errors: {}", response.getErrors());
                } else {
                    logger.info("GraphQL query executed successfully");
                }
            }

            return response;

        } catch (Exception e) {
            logger.error("Unexpected error executing GraphQL query", e);
            return createErrorResponse(e);
        }
    }

    /**
     * Execute a GraphQL query reactively (non-blocking)
     * @param request The GraphQL request with query and variables
     * @return Mono<GraphQLResponse> containing data or errors
     */
    public Mono<GraphQLResponse> executeQueryReactive(GraphQLRequest request) {
        logger.info("Executing Shopify GraphQL query (reactive)");
        logger.debug("Query: {}", request.getQuery());

        // Wait if necessary to respect rate limits
        rateLimiter.waitIfNecessary(DEFAULT_QUERY_COST);

        return webClient.post()
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GraphQLResponse.class)
                .timeout(attemptTimeout())
                .retryWhen(Retry.backoff(
                        shopifyConfig.getRateLimit().getMaxRetries(),
                        Duration.ofMillis(shopifyConfig.getRateLimit().getInitialBackoffMs())
                ).filter(this::isRetryableError))
                .timeout(totalTimeout())
                .doOnNext(response -> {
                    // Record actual query cost if available
                    Integer actualCost = response.getQueryCost();
                    if (actualCost != null) {
                        rateLimiter.recordActualCost(actualCost);
                    }

                    if (response.hasErrors()) {
                        logger.error("GraphQL query returned errors: {}", response.getErrors());
                    } else {
                        logger.info("GraphQL query executed successfully");
                    }
                })
                .onErrorResume(throwable -> Mono.just(handleCallError(throwable)));
    }

    /**
     * Execute a GraphQL query reactively (non-blocking)
     * @param query The GraphQL query string
     * @return Mono<GraphQLResponse> containing data or errors
     */
    public Mono<GraphQLResponse> executeQueryReactive(String query) {
        return executeQueryReactive(new GraphQLRequest(query));
    }

    /**
     * Determine if an error is retryable.
     * Rate limits (429) and gateway failures (502/503) are retried. Timeouts are not:
     * one bounded attempt is better for a waiting customer than several slow ones.
     */
    boolean isRetryableError(Throwable throwable) {
        if (isTimeout(throwable)) return false;

        if (throwable instanceof WebClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return status == 429 || status == 502 || status == 503;
        }

        String message = throwable.getMessage();
        if (message == null) return false;

        // Retry on rate limit errors and temporary failures
        return message.contains("429") ||
               message.contains("throttled") ||
               message.contains("503") ||
               message.contains("502");
    }

    /**
     * True if the error (or any cause) is a connect, read, per-attempt or total timeout
     */
    static boolean isTimeout(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException
                    || t instanceof ReadTimeoutException
                    || t instanceof ConnectTimeoutException
                    || t instanceof java.net.SocketTimeoutException) {
                return true;
            }
            if (t.getCause() == t) break;
        }
        return false;
    }

    /**
     * Log a failed call and convert it to the error response callers already handle
     */
    private GraphQLResponse handleCallError(Throwable throwable) {
        if (isTimeout(throwable)) {
            String message = describeTimeout(throwable, shopifyConfig.getHttp());
            logger.error(message);
            return createErrorResponse(message);
        }
        logger.error("Error executing GraphQL query", throwable);
        return createErrorResponse(throwable);
    }

    /**
     * e.g. "Shopify call timed out after 15000ms (per-attempt timeout)"
     */
    static String describeTimeout(Throwable throwable, ShopifyConfig.Http http) {
        for (Throwable t = throwable; t != null && t.getCause() != t; t = t.getCause()) {
            if (t instanceof ConnectTimeoutException) {
                return timedOut(http.getConnectTimeoutMs(), "connect");
            }
            if (t instanceof ReadTimeoutException || t instanceof java.net.SocketTimeoutException) {
                return timedOut(http.getReadTimeoutMs(), "read");
            }
        }
        // Reactor's TimeoutException message reads "... within {n}ms in 'timeout' ..."
        String message = throwable.getMessage();
        if (message != null && message.contains("within " + http.getTotalTimeoutMs() + "ms")) {
            return timedOut(http.getTotalTimeoutMs(), "total");
        }
        return timedOut(http.getResponseTimeoutMs(), "per-attempt");
    }

    private static String timedOut(long ms, String kind) {
        return "Shopify call timed out after " + ms + "ms (" + kind + " timeout)";
    }

    private Duration attemptTimeout() {
        return Duration.ofMillis(shopifyConfig.getHttp().getResponseTimeoutMs());
    }

    private Duration totalTimeout() {
        return Duration.ofMillis(shopifyConfig.getHttp().getTotalTimeoutMs());
    }

    /**
     * Create an error response from an exception
     */
    private GraphQLResponse createErrorResponse(Throwable throwable) {
        return createErrorResponse(throwable.getMessage());
    }

    private GraphQLResponse createErrorResponse(String message) {
        GraphQLResponse response = new GraphQLResponse();
        GraphQLResponse.GraphQLError error = new GraphQLResponse.GraphQLError();
        error.setMessage(message != null ? message : "Unknown error occurred");
        response.setErrors(java.util.List.of(error));
        return response;
    }

    /**
     * Test the connection to Shopify
     * @return true if connection is successful
     */
    public boolean testConnection() {
        logger.info("Testing Shopify API connection...");

        String testQuery = """
            {
              shop {
                name
                email
                currencyCode
              }
            }
            """;

        GraphQLResponse response = executeQuery(testQuery);

        if (response != null && !response.hasErrors() && response.getData() != null) {
            logger.info("Shopify connection successful!");
            logger.debug("Shop data: {}", response.getData());
            return true;
        } else {
            logger.error("Shopify connection failed");
            return false;
        }
    }
}
