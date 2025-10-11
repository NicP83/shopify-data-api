package com.shopify.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a GraphQL request to Shopify API
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphQLRequest {

    /**
     * The GraphQL query string
     */
    private String query;

    /**
     * Optional variables for the query
     */
    private Map<String, Object> variables;

    /**
     * Constructor for queries without variables
     */
    public GraphQLRequest(String query) {
        this.query = query;
    }
}
