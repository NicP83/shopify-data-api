package com.shopify.api.service.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopify.api.client.MCPClient;
import com.shopify.api.model.inventory.ERPInventoryData;
import com.shopify.api.model.inventory.SaleRecord;
import com.shopify.api.model.inventory.SupplierInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with ERP system via MCP
 * Primary data source for inventory analysis
 *
 * ERP contains:
 * - Sales history (synced from Shopify)
 * - Warehouse inventory levels
 * - Supplier information and lead times
 * - Product costs and pricing
 */
@Service
public class ERPInventoryService {

    private static final Logger logger = LoggerFactory.getLogger(ERPInventoryService.class);

    private final MCPClient mcpClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public ERPInventoryService(MCPClient mcpClient,
                               @Value("${inventory-analysis.enabled:true}") boolean enabled) {
        this.mcpClient = mcpClient;
        this.objectMapper = new ObjectMapper();
        this.enabled = enabled;
        logger.info("ERPInventoryService initialized - Enabled: {}, MCP Client Enabled: {}",
                enabled, mcpClient.isEnabled());
    }

    /**
     * Get sales history from ERP for a specific SKU
     * ERP already has Shopify sales data synced
     *
     * @param sku Product SKU
     * @param days Number of days of history to fetch
     * @return List of sale records
     */
    public Mono<List<SaleRecord>> getSalesHistory(String sku, int days) {
        if (!enabled || !mcpClient.isEnabled()) {
            logger.warn("ERP service disabled, returning empty sales history");
            return Mono.just(new ArrayList<>());
        }

        logger.info("Fetching sales history for SKU: {} (last {} days)", sku, days);

        Map<String, Object> arguments = Map.of(
                "sku", sku,
                "days", days,
                "includeDetails", true
        );

        return mcpClient.callTool("get_sales_history", arguments)
                .map(this::parseSalesHistory)
                .doOnSuccess(records -> logger.info("Retrieved {} sale records for SKU: {}", records.size(), sku))
                .onErrorResume(error -> {
                    logger.error("Error fetching sales history for SKU {}: {}", sku, error.getMessage());
                    return Mono.just(new ArrayList<>());
                });
    }

    /**
     * Get current inventory level from ERP warehouse
     *
     * @param sku Product SKU
     * @return Current inventory quantity
     */
    public Mono<Integer> getInventoryLevel(String sku) {
        if (!enabled || !mcpClient.isEnabled()) {
            logger.warn("ERP service disabled, returning 0 inventory");
            return Mono.just(0);
        }

        logger.info("Fetching inventory level for SKU: {}", sku);

        Map<String, Object> arguments = Map.of(
                "sku", sku,
                "location", "MAIN_WAREHOUSE" // Could be configurable
        );

        return mcpClient.callTool("get_inventory_level", arguments)
                .map(this::parseInventoryLevel)
                .doOnSuccess(quantity -> logger.info("Inventory level for SKU {}: {}", sku, quantity))
                .onErrorResume(error -> {
                    logger.error("Error fetching inventory for SKU {}: {}", sku, error.getMessage());
                    return Mono.just(0);
                });
    }

    /**
     * Get supplier information from ERP
     *
     * @param sku Product SKU
     * @return Supplier information
     */
    public Mono<SupplierInfo> getSupplierInfo(String sku) {
        if (!enabled || !mcpClient.isEnabled()) {
            logger.warn("ERP service disabled, returning empty supplier info");
            return Mono.just(SupplierInfo.builder().build());
        }

        logger.info("Fetching supplier info for SKU: {}", sku);

        Map<String, Object> arguments = Map.of("sku", sku);

        return mcpClient.callTool("get_supplier_info", arguments)
                .map(this::parseSupplierInfo)
                .doOnSuccess(info -> logger.info("Retrieved supplier info for SKU {}: {}",
                        sku, info.getSupplierName()))
                .onErrorResume(error -> {
                    logger.error("Error fetching supplier info for SKU {}: {}", sku, error.getMessage());
                    return Mono.just(SupplierInfo.builder()
                            .leadTimeDays(14) // Default fallback
                            .build());
                });
    }

    /**
     * Get product cost from ERP
     *
     * @param sku Product SKU
     * @return Cost per unit
     */
    public Mono<BigDecimal> getProductCost(String sku) {
        if (!enabled || !mcpClient.isEnabled()) {
            logger.warn("ERP service disabled, returning 0 cost");
            return Mono.just(BigDecimal.ZERO);
        }

        logger.info("Fetching product cost for SKU: {}", sku);

        Map<String, Object> arguments = Map.of("sku", sku);

        return mcpClient.callTool("get_product_cost", arguments)
                .map(this::parseProductCost)
                .doOnSuccess(cost -> logger.info("Product cost for SKU {}: ${}", sku, cost))
                .onErrorResume(error -> {
                    logger.error("Error fetching cost for SKU {}: {}", sku, error.getMessage());
                    return Mono.just(BigDecimal.ZERO);
                });
    }

    /**
     * Get comprehensive ERP inventory data in one call (if MCP supports it)
     * Falls back to individual calls if not available
     *
     * @param sku Product SKU
     * @return Complete ERP inventory data
     */
    public Mono<ERPInventoryData> getERPInventoryData(String sku) {
        if (!enabled || !mcpClient.isEnabled()) {
            logger.warn("ERP service disabled, returning empty inventory data");
            return Mono.just(ERPInventoryData.builder().sku(sku).build());
        }

        logger.info("Fetching comprehensive ERP data for SKU: {}", sku);

        // Try to get all data in one MCP call (if available)
        Map<String, Object> arguments = Map.of("sku", sku);

        return mcpClient.callTool("get_comprehensive_inventory_data", arguments)
                .map(this::parseERPInventoryData)
                .onErrorResume(error -> {
                    // Fallback: Make individual calls
                    logger.info("Comprehensive call not available, falling back to individual calls");
                    return Mono.zip(
                            getInventoryLevel(sku),
                            getSupplierInfo(sku),
                            getProductCost(sku)
                    ).map(tuple -> ERPInventoryData.builder()
                            .sku(sku)
                            .quantity(tuple.getT1())
                            .available(tuple.getT1())
                            .supplierName(tuple.getT2().getSupplierName())
                            .leadTimeDays(tuple.getT2().getLeadTimeDays())
                            .costPerUnit(tuple.getT3())
                            .lastUpdated(LocalDateTime.now())
                            .build());
                });
    }

    // =====================================================
    // Parsing Methods
    // =====================================================

    /**
     * Parse sales history from MCP response
     */
    private List<SaleRecord> parseSalesHistory(JsonNode response) {
        List<SaleRecord> records = new ArrayList<>();

        try {
            if (response.has("sales")) {
                JsonNode salesArray = response.get("sales");
                if (salesArray.isArray()) {
                    for (JsonNode sale : salesArray) {
                        SaleRecord record = SaleRecord.builder()
                                .sku(sale.has("sku") ? sale.get("sku").asText() : null)
                                .date(sale.has("date") ? LocalDate.parse(sale.get("date").asText()) : LocalDate.now())
                                .quantity(sale.has("quantity") ? sale.get("quantity").asInt() : 0)
                                .amount(sale.has("amount") ? new BigDecimal(sale.get("amount").asText()) : BigDecimal.ZERO)
                                .orderId(sale.has("orderId") ? sale.get("orderId").asText() : null)
                                .channel(sale.has("channel") ? sale.get("channel").asText() : "ONLINE")
                                .build();
                        records.add(record);
                    }
                }
            }
            logger.debug("Parsed {} sale records", records.size());
        } catch (Exception e) {
            logger.error("Error parsing sales history: {}", e.getMessage(), e);
        }

        return records;
    }

    /**
     * Parse inventory level from MCP response
     */
    private Integer parseInventoryLevel(JsonNode response) {
        try {
            if (response.has("quantity")) {
                return response.get("quantity").asInt();
            }
            if (response.has("available")) {
                return response.get("available").asInt();
            }
            logger.warn("No quantity field in inventory response: {}", response);
            return 0;
        } catch (Exception e) {
            logger.error("Error parsing inventory level: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Parse supplier info from MCP response
     */
    private SupplierInfo parseSupplierInfo(JsonNode response) {
        try {
            return SupplierInfo.builder()
                    .supplierName(response.has("supplierName") ? response.get("supplierName").asText() : "Unknown")
                    .supplierCode(response.has("supplierCode") ? response.get("supplierCode").asText() : null)
                    .leadTimeDays(response.has("leadTimeDays") ? response.get("leadTimeDays").asInt() : 14)
                    .averageLeadTimeDays(response.has("averageLeadTime") ? response.get("averageLeadTime").asDouble() : null)
                    .minimumOrderQuantity(response.has("minimumOrderQuantity") ? response.get("minimumOrderQuantity").asInt() : null)
                    .orderMultiple(response.has("orderMultiple") ? response.get("orderMultiple").asInt() : null)
                    .standardCost(response.has("standardCost") ? new BigDecimal(response.get("standardCost").asText()) : null)
                    .currency(response.has("currency") ? response.get("currency").asText() : "USD")
                    .lastOrderDate(response.has("lastOrderDate") ? LocalDate.parse(response.get("lastOrderDate").asText()) : null)
                    .lastOrderQuantity(response.has("lastOrderQuantity") ? response.get("lastOrderQuantity").asInt() : null)
                    .reliabilityScore(response.has("reliabilityScore") ? response.get("reliabilityScore").asDouble() : null)
                    .build();
        } catch (Exception e) {
            logger.error("Error parsing supplier info: {}", e.getMessage());
            return SupplierInfo.builder()
                    .supplierName("Unknown")
                    .leadTimeDays(14)
                    .build();
        }
    }

    /**
     * Parse product cost from MCP response
     */
    private BigDecimal parseProductCost(JsonNode response) {
        try {
            if (response.has("costPerUnit")) {
                return new BigDecimal(response.get("costPerUnit").asText());
            }
            if (response.has("cost")) {
                return new BigDecimal(response.get("cost").asText());
            }
            logger.warn("No cost field in response: {}", response);
            return BigDecimal.ZERO;
        } catch (Exception e) {
            logger.error("Error parsing product cost: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Parse comprehensive ERP inventory data
     */
    private ERPInventoryData parseERPInventoryData(JsonNode response) {
        try {
            return ERPInventoryData.builder()
                    .sku(response.has("sku") ? response.get("sku").asText() : null)
                    .quantity(response.has("quantity") ? response.get("quantity").asInt() : 0)
                    .location(response.has("location") ? response.get("location").asText() : null)
                    .warehouseLocation(response.has("warehouseLocation") ? response.get("warehouseLocation").asText() : null)
                    .supplierName(response.has("supplierName") ? response.get("supplierName").asText() : null)
                    .supplierCode(response.has("supplierCode") ? response.get("supplierCode").asText() : null)
                    .leadTimeDays(response.has("leadTimeDays") ? response.get("leadTimeDays").asInt() : 14)
                    .costPerUnit(response.has("costPerUnit") ? new BigDecimal(response.get("costPerUnit").asText()) : BigDecimal.ZERO)
                    .currency(response.has("currency") ? response.get("currency").asText() : "USD")
                    .reorderQuantity(response.has("reorderQuantity") ? response.get("reorderQuantity").asInt() : null)
                    .minimumStockLevel(response.has("minimumStockLevel") ? response.get("minimumStockLevel").asInt() : null)
                    .reserved(response.has("reserved") ? response.get("reserved").asInt() : 0)
                    .available(response.has("available") ? response.get("available").asInt() : response.get("quantity").asInt())
                    .lastUpdated(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            logger.error("Error parsing ERP inventory data: {}", e.getMessage());
            return ERPInventoryData.builder().build();
        }
    }

    /**
     * Check if ERP service is available
     */
    public boolean isAvailable() {
        return enabled && mcpClient.isEnabled();
    }
}
