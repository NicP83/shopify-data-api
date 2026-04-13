package com.shopify.api.handler.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopify.api.model.inventory.SalesVelocity;
import com.shopify.api.model.inventory.StockAlert;
import com.shopify.api.repository.inventory.SalesVelocityRepository;
import com.shopify.api.repository.inventory.StockAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Chat tool handler for getting stock insights from the inventory analysis module.
 * Provides sales velocity, trend data, and stock alerts for social proof and urgency.
 * E.g., "This is one of our trending products — selling 5 per day!"
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetStockInsightsChatToolHandler implements ChatToolHandler {

    private final SalesVelocityRepository salesVelocityRepository;
    private final StockAlertRepository stockAlertRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getToolName() {
        return "get_stock_insights";
    }

    @Override
    public String getToolDescription() {
        return "Get sales velocity and trend insights for a product by SKU. " +
                "Returns how fast a product is selling (daily/weekly/monthly), whether sales are " +
                "increasing or decreasing, and any stock alerts. Use to add social proof " +
                "(e.g., 'This is trending — selling 5 per day!') or urgency to recommendations.";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode skuProp = objectMapper.createObjectNode();
        skuProp.put("type", "string");
        skuProp.put("description", "Product SKU to get insights for");
        properties.set("sku", skuProp);

        schema.set("properties", properties);
        ArrayNode required = objectMapper.createArrayNode();
        required.add("sku");
        schema.set("required", required);

        return schema;
    }

    @Override
    public Mono<String> execute(JsonNode input) {
        String sku = input.get("sku").asText();
        log.info("Getting stock insights for SKU: {}", sku);

        return Mono.fromCallable(() -> {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("sku", sku);

            // Get sales velocity
            Optional<SalesVelocity> velocityOpt = salesVelocityRepository.findBySku(sku);
            if (velocityOpt.isPresent()) {
                SalesVelocity velocity = velocityOpt.get();
                ObjectNode velocityNode = objectMapper.createObjectNode();
                velocityNode.put("dailyAverage", velocity.getDailyAverage().doubleValue());
                velocityNode.put("weeklyAverage", velocity.getWeeklyAverage().doubleValue());
                velocityNode.put("monthlyAverage", velocity.getMonthlyAverage().doubleValue());
                velocityNode.put("trend", velocity.getTrend().name());
                if (velocity.getTrendPercentage() != null) {
                    velocityNode.put("trendPercentage", velocity.getTrendPercentage().doubleValue());
                }
                velocityNode.put("trendDescription", velocity.getTrendDescription());
                velocityNode.put("calculationPeriodDays", velocity.getCalculationPeriodDays());
                response.set("salesVelocity", velocityNode);
                response.put("hasVelocityData", true);

                // Generate human-readable insight
                String insight = generateInsight(velocity);
                response.put("insight", insight);
            } else {
                response.put("hasVelocityData", false);
                response.put("insight", "No sales velocity data available for this product yet.");
            }

            // Get stock alert
            Optional<StockAlert> alertOpt = stockAlertRepository.findTopBySkuAndResolvedFalseOrderByCreatedAtDesc(sku);
            if (alertOpt.isPresent()) {
                StockAlert alert = alertOpt.get();
                ObjectNode alertNode = objectMapper.createObjectNode();
                alertNode.put("level", alert.getAlertLevel().name());
                alertNode.put("message", alert.getMessage());
                if (alert.getCurrentStock() != null) {
                    alertNode.put("currentStock", alert.getCurrentStock());
                }
                if (alert.getDaysUntilStockout() != null) {
                    alertNode.put("daysUntilStockout", alert.getDaysUntilStockout());
                }
                if (alert.getEstimatedStockoutDate() != null) {
                    alertNode.put("estimatedStockoutDate", alert.getEstimatedStockoutDate().toString());
                }
                response.set("stockAlert", alertNode);
                response.put("hasStockAlert", true);
            } else {
                response.put("hasStockAlert", false);
            }

            return objectMapper.writeValueAsString(response);
        }).onErrorResume(e -> {
            log.error("Error getting stock insights for {}: {}", sku, e.getMessage(), e);
            return Mono.just("{\"error\": \"Unable to retrieve stock insights\", \"sku\": \"" + sku + "\"}");
        });
    }

    private String generateInsight(SalesVelocity velocity) {
        StringBuilder insight = new StringBuilder();

        double daily = velocity.getDailyAverage().doubleValue();
        if (daily >= 3) {
            insight.append("Hot seller! Averaging ").append(String.format("%.1f", daily)).append(" sales per day. ");
        } else if (daily >= 1) {
            insight.append("Popular item — selling about ").append(String.format("%.0f", daily)).append(" per day. ");
        } else if (daily >= 0.3) {
            insight.append("Steady seller — about ").append(String.format("%.0f", velocity.getWeeklyAverage().doubleValue())).append(" per week. ");
        }

        switch (velocity.getTrend()) {
            case INCREASING:
                insight.append("Sales are trending UP");
                if (velocity.getTrendPercentage() != null) {
                    insight.append(" (").append(String.format("+%.0f%%", velocity.getTrendPercentage().doubleValue())).append(")");
                }
                insight.append(".");
                break;
            case DECREASING:
                insight.append("Sales have slowed recently.");
                break;
            case STABLE:
                insight.append("Consistent demand.");
                break;
        }

        return insight.toString();
    }

    @Override
    public boolean validateInput(JsonNode input) {
        return input.has("sku") && !input.get("sku").asText().trim().isEmpty();
    }
}
