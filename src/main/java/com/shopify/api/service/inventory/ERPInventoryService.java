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
     * Search products with filters (NEW - uses MCP tool #20)
     * Returns list of SKUs matching the filters
     *
     * @param brand Filter by brand/manufacturer name (optional)
     * @param category Filter by category (Paint, Model Kit, etc) (optional)
     * @param company Filter by company/location (Hearns Hobbies or The Hobbyman) (optional)
     * @return List of matching SKUs
     */
    public Mono<List<String>> searchProductsFiltered(String brand, String category, String company) {
        if (!enabled || !mcpClient.isEnabled()) {
            logger.warn("ERP service disabled, returning empty SKU list");
            return Mono.just(new ArrayList<>());
        }

        logger.info("Searching products with filters - brand: {}, category: {}, company: {}",
                brand, category, company);

        Map<String, Object> arguments = new HashMap<>();
        if (brand != null && !brand.equals("Unknown")) {
            arguments.put("brand", brand);
        }
        if (category != null && !category.equals("Unknown")) {
            arguments.put("category", category);
        }
        if (company != null && !company.equals("Unknown")) {
            arguments.put("company", company);
        }
        arguments.put("limit", 100); // Default limit

        return mcpClient.callTool("search_products_filtered", arguments)
                .map(this::parseSkuList)
                .doOnSuccess(skus -> logger.info("Found {} SKUs matching filters", skus.size()))
                .onErrorResume(error -> {
                    logger.error("Error searching products: {}", error.getMessage());
                    return Mono.just(new ArrayList<>());
                });
    }

    /**
     * Get inventory levels for multiple SKUs (NEW - Bulk operation, MCP tool #15)
     * 99% performance improvement over individual calls
     *
     * @param skus List of SKUs to query
     * @return Map of SKU to inventory quantity
     */
    public Mono<Map<String, Integer>> getInventoryLevelsBulk(List<String> skus) {
        if (!enabled || !mcpClient.isEnabled() || skus == null || skus.isEmpty()) {
            logger.warn("ERP service disabled or empty SKU list, returning empty map");
            return Mono.just(new HashMap<>());
        }

        logger.info("Fetching inventory levels for {} SKUs (bulk operation)", skus.size());

        Map<String, Object> arguments = Map.of("skus", skus);

        return mcpClient.callTool("get_inventory_levels_bulk", arguments)
                .map(this::parseInventoryLevelsBulk)
                .doOnSuccess(map -> logger.info("Retrieved inventory for {} SKUs", map.size()))
                .onErrorResume(error -> {
                    logger.error("Error in bulk inventory fetch: {}", error.getMessage());
                    return Mono.just(new HashMap<>());
                });
    }

    /**
     * Get sales history for multiple SKUs (NEW - Bulk operation, MCP tool #16)
     *
     * @param skus List of SKUs to query
     * @param days Number of days of history
     * @return Map of SKU to sales records
     */
    public Mono<Map<String, List<SaleRecord>>> getSalesHistoryBulk(List<String> skus, int days) {
        if (!enabled || !mcpClient.isEnabled() || skus == null || skus.isEmpty()) {
            logger.warn("ERP service disabled or empty SKU list, returning empty map");
            return Mono.just(new HashMap<>());
        }

        logger.info("Fetching sales history for {} SKUs (bulk operation)", skus.size());

        Map<String, Object> arguments = Map.of("skus", skus, "days", days);

        return mcpClient.callTool("get_sales_history_bulk", arguments)
                .map(this::parseSalesHistoryBulk)
                .doOnSuccess(map -> logger.info("Retrieved sales history for {} SKUs", map.size()))
                .onErrorResume(error -> {
                    logger.error("Error in bulk sales history fetch: {}", error.getMessage());
                    return Mono.just(new HashMap<>());
                });
    }

    /**
     * Get costs for multiple SKUs (NEW - Bulk operation, MCP tool #17)
     *
     * @param skus List of SKUs to query
     * @return Map of SKU to cost
     */
    public Mono<Map<String, BigDecimal>> getCostsBulk(List<String> skus) {
        if (!enabled || !mcpClient.isEnabled() || skus == null || skus.isEmpty()) {
            logger.warn("ERP service disabled or empty SKU list, returning empty map");
            return Mono.just(new HashMap<>());
        }

        logger.info("Fetching costs for {} SKUs (bulk operation)", skus.size());

        Map<String, Object> arguments = Map.of("skus", skus);

        return mcpClient.callTool("get_costs_bulk", arguments)
                .map(this::parseCostsBulk)
                .doOnSuccess(map -> logger.info("Retrieved costs for {} SKUs", map.size()))
                .onErrorResume(error -> {
                    logger.error("Error in bulk costs fetch: {}", error.getMessage());
                    return Mono.just(new HashMap<>());
                });
    }

    /**
     * Get supplier info for multiple SKUs (NEW - Bulk operation, MCP tool #18)
     *
     * @param skus List of SKUs to query
     * @return Map of SKU to supplier info
     */
    public Mono<Map<String, SupplierInfo>> getSuppliersBulk(List<String> skus) {
        if (!enabled || !mcpClient.isEnabled() || skus == null || skus.isEmpty()) {
            logger.warn("ERP service disabled or empty SKU list, returning empty map");
            return Mono.just(new HashMap<>());
        }

        logger.info("Fetching supplier info for {} SKUs (bulk operation)", skus.size());

        Map<String, Object> arguments = Map.of("skus", skus);

        return mcpClient.callTool("get_suppliers_bulk", arguments)
                .map(this::parseSuppliersBulk)
                .doOnSuccess(map -> logger.info("Retrieved supplier info for {} SKUs", map.size()))
                .onErrorResume(error -> {
                    logger.error("Error in bulk suppliers fetch: {}", error.getMessage());
                    return Mono.just(new HashMap<>());
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
     * Parse bulk inventory levels response
     * MCP returns data in content[0].text as a JSON string
     */
    private Map<String, Integer> parseInventoryLevelsBulk(JsonNode response) {
        Map<String, Integer> result = new HashMap<>();

        try {
            // MCP response format: { "content": [{ "type": "text", "text": "{...}" }] }
            JsonNode dataNode = response;

            // Extract nested JSON if in MCP format
            if (response.has("content") && response.get("content").isArray() && response.get("content").size() > 0) {
                JsonNode firstContent = response.get("content").get(0);
                if (firstContent.has("text")) {
                    String textContent = firstContent.get("text").asText();
                    dataNode = objectMapper.readTree(textContent);
                }
            }

            // Process data: { "TAM-31114": { "quantity": 45, "reorderLevel": 20 }, ... }
            dataNode.fields().forEachRemaining(entry -> {
                String sku = entry.getKey();
                JsonNode data = entry.getValue();
                if (data.has("quantity")) {
                    result.put(sku, data.get("quantity").asInt());
                }
            });
            logger.info("Parsed {} inventory levels from bulk response", result.size());
        } catch (Exception e) {
            logger.error("Error parsing bulk inventory levels: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * Parse bulk sales history response
     * MCP returns data in content[0].text as a JSON string
     */
    private Map<String, List<SaleRecord>> parseSalesHistoryBulk(JsonNode response) {
        Map<String, List<SaleRecord>> result = new HashMap<>();

        try {
            // MCP response format: { "content": [{ "type": "text", "text": "{...}" }] }
            JsonNode dataNode = response;

            // Extract nested JSON if in MCP format
            if (response.has("content") && response.get("content").isArray() && response.get("content").size() > 0) {
                JsonNode firstContent = response.get("content").get(0);
                if (firstContent.has("text")) {
                    String textContent = firstContent.get("text").asText();
                    dataNode = objectMapper.readTree(textContent);
                }
            }

            // Process data: { "TAM-31114": [ {sale1}, {sale2} ], "TAM-31115": [...] }
            dataNode.fields().forEachRemaining(entry -> {
                String sku = entry.getKey();
                JsonNode salesArray = entry.getValue();

                List<SaleRecord> sales = new ArrayList<>();
                if (salesArray.isArray()) {
                    for (JsonNode sale : salesArray) {
                        SaleRecord record = SaleRecord.builder()
                                .sku(sku)
                                .date(sale.has("date") ? LocalDate.parse(sale.get("date").asText()) : LocalDate.now())
                                .quantity(sale.has("quantity") ? sale.get("quantity").asInt() : 0)
                                .amount(sale.has("amount") ? new BigDecimal(sale.get("amount").asText()) : BigDecimal.ZERO)
                                .orderId(sale.has("orderId") ? sale.get("orderId").asText() : null)
                                .channel(sale.has("channel") ? sale.get("channel").asText() : "ONLINE")
                                .build();
                        sales.add(record);
                    }
                }
                result.put(sku, sales);
            });
            logger.info("Parsed sales history for {} SKUs from bulk response", result.size());
        } catch (Exception e) {
            logger.error("Error parsing bulk sales history: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * Parse bulk costs response
     * MCP returns data in content[0].text as a JSON string
     */
    private Map<String, BigDecimal> parseCostsBulk(JsonNode response) {
        Map<String, BigDecimal> result = new HashMap<>();

        try {
            // MCP response format: { "content": [{ "type": "text", "text": "{...}" }] }
            JsonNode dataNode = response;

            // Extract nested JSON if in MCP format
            if (response.has("content") && response.get("content").isArray() && response.get("content").size() > 0) {
                JsonNode firstContent = response.get("content").get(0);
                if (firstContent.has("text")) {
                    String textContent = firstContent.get("text").asText();
                    dataNode = objectMapper.readTree(textContent);
                }
            }

            // Process data: { "TAM-31114": { "costPerUnit": 7.99, ... }, ... }
            dataNode.fields().forEachRemaining(entry -> {
                String sku = entry.getKey();
                JsonNode data = entry.getValue();
                if (data.has("costPerUnit")) {
                    result.put(sku, new BigDecimal(data.get("costPerUnit").asText()));
                }
            });
            logger.info("Parsed {} costs from bulk response", result.size());
        } catch (Exception e) {
            logger.error("Error parsing bulk costs: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * Parse bulk suppliers response
     * MCP returns data in content[0].text as a JSON string
     */
    private Map<String, SupplierInfo> parseSuppliersBulk(JsonNode response) {
        Map<String, SupplierInfo> result = new HashMap<>();

        try {
            // MCP response format: { "content": [{ "type": "text", "text": "{...}" }] }
            JsonNode dataNode = response;

            // Extract nested JSON if in MCP format
            if (response.has("content") && response.get("content").isArray() && response.get("content").size() > 0) {
                JsonNode firstContent = response.get("content").get(0);
                if (firstContent.has("text")) {
                    String textContent = firstContent.get("text").asText();
                    dataNode = objectMapper.readTree(textContent);
                }
            }

            // Process data: { "TAM-31114": { "supplierName": "Tamiya Inc.", ... }, ... }
            dataNode.fields().forEachRemaining(entry -> {
                String sku = entry.getKey();
                JsonNode data = entry.getValue();

                SupplierInfo info = SupplierInfo.builder()
                        .supplierName(data.has("supplierName") ? data.get("supplierName").asText() : "Unknown")
                        .supplierCode(data.has("supplierCode") ? data.get("supplierCode").asText() : null)
                        .leadTimeDays(data.has("leadTimeDays") ? data.get("leadTimeDays").asInt() : 14)
                        .build();
                result.put(sku, info);
            });
            logger.info("Parsed supplier info for {} SKUs from bulk response", result.size());
        } catch (Exception e) {
            logger.error("Error parsing bulk suppliers: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * Parse SKU list from search_products_filtered response
     * MCP returns data in content[0].text as a JSON string
     */
    private List<String> parseSkuList(JsonNode response) {
        List<String> skus = new ArrayList<>();

        try {
            // MCP response format: { "content": [{ "type": "text", "text": "{...}" }] }
            if (response.has("content") && response.get("content").isArray() && response.get("content").size() > 0) {
                JsonNode firstContent = response.get("content").get(0);
                if (firstContent.has("text")) {
                    String textContent = firstContent.get("text").asText();
                    // Parse the nested JSON string
                    JsonNode parsedContent = objectMapper.readTree(textContent);
                    if (parsedContent.has("skus") && parsedContent.get("skus").isArray()) {
                        for (JsonNode skuNode : parsedContent.get("skus")) {
                            skus.add(skuNode.asText());
                        }
                    }
                }
            }
            logger.info("Parsed {} SKUs from search response [v2-nested-json-fix]", skus.size());
        } catch (Exception e) {
            logger.error("Error parsing SKU list: {}", e.getMessage(), e);
        }

        return skus;
    }

    /**
     * Check if ERP service is available
     */
    public boolean isAvailable() {
        return enabled && mcpClient.isEnabled();
    }
}
