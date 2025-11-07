package com.shopify.api.model.inventory;

/**
 * Enum representing stock alert severity levels
 */
public enum AlertLevel {
    CRITICAL,  // Stock critically low - immediate action required
    WARNING,   // Stock low - action needed soon
    INFO       // Informational alert
}
