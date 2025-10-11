package com.shopify.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a GraphQL response from Shopify API
 */
@Data
@NoArgsConstructor
public class GraphQLResponse {

    /**
     * The data returned by the query
     */
    private Map<String, Object> data;

    /**
     * Any errors that occurred during query execution
     */
    private List<GraphQLError> errors;

    /**
     * Extensions containing additional metadata (e.g., query cost)
     */
    private Map<String, Object> extensions;

    /**
     * Check if the response has errors
     */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    /**
     * Get the query cost from extensions
     */
    public Integer getQueryCost() {
        if (extensions != null && extensions.containsKey("cost")) {
            Map<String, Object> cost = (Map<String, Object>) extensions.get("cost");
            if (cost.containsKey("actualQueryCost")) {
                return (Integer) cost.get("actualQueryCost");
            }
        }
        return null;
    }

    /**
     * Represents a GraphQL error
     */
    @Data
    @NoArgsConstructor
    public static class GraphQLError {
        private String message;
        private List<Map<String, Object>> locations;
        private Map<String, Object> extensions;
    }
}
