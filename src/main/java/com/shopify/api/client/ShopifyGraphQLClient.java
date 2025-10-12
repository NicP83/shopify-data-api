package com.shopify.api.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopify.api.config.ShopifyConfig;
import com.shopify.api.model.GraphQLRequest;
import com.shopify.api.model.GraphQLResponse;
import com.shopify.api.util.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Client for making GraphQL requests to Shopify Admin API
 * Handles:
 * - Request execution
 * - Rate limiting
 * - Automatic retries with exponential backoff
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
                    .retryWhen(Retry.backoff(
                            shopifyConfig.getRateLimit().getMaxRetries(),
                            Duration.ofMillis(shopifyConfig.getRateLimit().getInitialBackoffMs())
                    ).filter(throwable -> isRetryableError(throwable)))
                    .onErrorResume(throwable -> {
                        logger.error("Error executing GraphQL query", throwable);
                        return Mono.just(createErrorResponse(throwable));
                    })
                    .block();

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
                .retryWhen(Retry.backoff(
                        shopifyConfig.getRateLimit().getMaxRetries(),
                        Duration.ofMillis(shopifyConfig.getRateLimit().getInitialBackoffMs())
                ).filter(this::isRetryableError))
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
                .onErrorResume(throwable -> {
                    logger.error("Error executing GraphQL query", throwable);
                    return Mono.just(createErrorResponse(throwable));
                });
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
     * Determine if an error is retryable
     */
    private boolean isRetryableError(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null) return false;

        // Retry on rate limit errors and temporary failures
        return message.contains("429") ||
               message.contains("throttled") ||
               message.contains("timeout") ||
               message.contains("503") ||
               message.contains("502");
    }

    /**
     * Create an error response from an exception
     */
    private GraphQLResponse createErrorResponse(Throwable throwable) {
        GraphQLResponse response = new GraphQLResponse();
        GraphQLResponse.GraphQLError error = new GraphQLResponse.GraphQLError();
        error.setMessage(throwable.getMessage() != null ?
                throwable.getMessage() : "Unknown error occurred");
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
