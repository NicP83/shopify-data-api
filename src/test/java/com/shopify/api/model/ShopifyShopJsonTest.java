package com.shopify.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopifyShopJsonTest {

    @Test
    void accessTokenIsNeverSerialized() throws Exception {
        ShopifyShop shop = new ShopifyShop();
        shop.setShopDomain("example.myshopify.com");
        shop.setAccessToken("shpat_secret_value");

        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(shop);

        assertTrue(json.contains("example.myshopify.com"));
        assertFalse(json.contains("shpat_secret_value"));
        assertFalse(json.contains("accessToken"));
    }
}
