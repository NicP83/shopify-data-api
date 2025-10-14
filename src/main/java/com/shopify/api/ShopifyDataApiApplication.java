package com.shopify.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot Application for Shopify Data API
 *
 * This application provides REST endpoints to extract data from Shopify stores
 * using the Shopify GraphQL Admin API.
 *
 * Features:
 * - Product data extraction
 * - Order management
 * - Customer information
 * - Inventory tracking
 * - Automatic rate limiting
 * - OAuth token management
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class ShopifyDataApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopifyDataApiApplication.class, args);
        System.out.println("==============================================");
        System.out.println("Shopify Data API Started Successfully!");
        System.out.println("==============================================");
    }
}
