package com.shopify.api.model.inventory;

/**
 * Enum representing the status of an order recommendation
 */
public enum RecommendationStatus {
    PENDING,   // Recommendation created, awaiting review
    APPROVED,  // Approved for ordering
    ORDERED,   // Purchase order created
    DISMISSED  // Recommendation dismissed/rejected
}
