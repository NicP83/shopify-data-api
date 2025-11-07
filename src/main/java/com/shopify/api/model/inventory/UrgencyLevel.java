package com.shopify.api.model.inventory;

/**
 * Enum representing urgency level for order recommendations
 */
public enum UrgencyLevel {
    CRITICAL,  // Immediate action required
    HIGH,      // Order soon
    MEDIUM,    // Order in near future
    LOW        // Monitor, no immediate action needed
}
