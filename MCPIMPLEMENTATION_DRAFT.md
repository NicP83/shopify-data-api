# MCP ERP Integration - Implementation Draft

**Status:** DRAFT - Research Phase
**Date:** 2025-10-13
**MCP Server:** https://web-production-2534.up.railway.app

## Overview

This document captures the initial research and planning for integrating an MCP (Model Context Protocol) server that connects to an ERP system with the existing Shopify Data API chatbot.

## Current System Architecture

### Existing Components:
- **Backend:** Spring Boot 3.2.0 (Java 17)
- **AI Integration:** Claude API (claude-3-5-sonnet-20241022)
- **Current Tools:** 1 tool available to Claude
  - `search_products` - Searches Shopify product catalog
- **Service Layer:**
  - `ChatAgentService.java` - Claude API integration with tool use
  - `ProductService.java` - Shopify product search
  - `ChatbotConfigService.java` - Configuration management

### Current Tool Use Flow:
```
User Message → ChatAgentService.processChat()
  → callClaudeWithTools()
    → Claude decides to use tool
      → handleToolUseAndContinue()
        → executeToolCallReactive("search_products", input)
          → ProductService.searchProductsReactive()
            → Returns results to Claude
              → Claude formulates response with product data
```

## MCP Server Discovery

### Server Status:
- **URL:** https://web-production-2534.up.railway.app
- **Health Check:** `/health` endpoint available
- **Response:** `{"status":"healthy","database":"connected","tables_count":96}`
- **Database:** Connected with 96 tables (suggests comprehensive ERP system)

### Attempted Endpoints (404/Not Found):
- `/` - 404
- `/api` - 404
- `/docs` - 404
- `/swagger` - 404
- `/mcp` - Method Not Allowed
- JSON-RPC call to `/` - 404

### Working Endpoints:
- `GET /health` - ✅ Returns health status

## Unknowns Requiring Clarification

### 1. MCP Endpoint Structure
- What are the available API endpoints?
- What ERP operations are supported?
- Is there API documentation?

### 2. Authentication
- Does the MCP require API keys?
- Token-based auth?
- Other authentication mechanism?

### 3. Data Operations
**Possible ERP Features (TBD):**
- Inventory management (real-time stock levels)
- Supplier information
- Purchase orders
- Cost/pricing data
- Shipment tracking
- Product sourcing
- Warehouse locations

### 4. Integration Scope
- Which ERP features should Claude have access to?
- Should it be automatic or user-requested?
- Real-time vs cached data?

## Proposed Integration Approaches

### **Option 1: Claude Tool Integration** (Recommended)
Add ERP capabilities as new tools Claude can call during conversations.

**Advantages:**
- Natural conversation flow
- Claude can combine Shopify + ERP data intelligently
- User asks: "Do we have more Gundam RX-78 in the warehouse?" → Claude checks both Shopify and ERP
- Seamless integration with existing chatbot

**Implementation:**
1. Create `ERPService.java` with WebClient for MCP calls
2. Add new tools to `ChatAgentService.buildToolsArray()`:
   - `check_erp_inventory`
   - `get_supplier_info`
   - `check_purchase_order`
3. Add handlers in `executeToolCallReactive()`
4. Update system prompt to describe ERP capabilities

**Files to Modify:**
- `src/main/java/com/shopify/api/service/ChatAgentService.java`
- `src/main/resources/application.yml` (add ERP config)

**Files to Create:**
- `src/main/java/com/shopify/api/service/ERPService.java`
- `src/main/java/com/shopify/api/controller/ERPController.java` (optional, for testing)

### **Option 2: Direct API Passthrough**
Create REST endpoints that proxy MCP calls without AI involvement.

**Advantages:**
- Simpler implementation
- Frontend can call ERP directly
- No token usage for ERP queries

**Implementation:**
1. Create `ERPProxyController.java`
2. Add `GET /api/erp/*` endpoints
3. Forward requests to MCP server

### **Option 3: Hybrid Approach**
Combine both options - Claude tools + Direct API access.

**Advantages:**
- Maximum flexibility
- AI can use ERP when contextually relevant
- Users/frontend can also query directly

## Code Structure Examples

### ERPService.java (Skeleton)
```java
@Service
public class ERPService {
    private static final Logger logger = LoggerFactory.getLogger(ERPService.class);

    private final WebClient webClient;

    @Autowired
    public ERPService(WebClient.Builder webClientBuilder,
                     @Value("${erp.mcp-url}") String mcpUrl,
                     @Value("${erp.api-key:}") String apiKey) {
        this.webClient = webClientBuilder
                .baseUrl(mcpUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    /**
     * Check inventory levels in ERP for a specific SKU
     */
    public Mono<Map<String, Object>> checkInventory(String sku) {
        logger.info("Checking ERP inventory for SKU: {}", sku);
        return webClient.get()
                .uri("/api/inventory/{sku}", sku)  // TBD: actual endpoint
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .onErrorResume(e -> {
                    logger.error("Error checking inventory: {}", e.getMessage());
                    return Mono.just(Map.of("error", e.getMessage()));
                });
    }

    /**
     * Get supplier information
     */
    public Mono<Map<String, Object>> getSupplierInfo(String supplierId) {
        // TBD: Implementation
        return Mono.empty();
    }

    /**
     * Check purchase order status
     */
    public Mono<Map<String, Object>> checkPurchaseOrder(String poNumber) {
        // TBD: Implementation
        return Mono.empty();
    }
}
```

### ChatAgentService.java Changes

**Add to buildToolsArray():**
```java
// Add inventory check tool
ObjectNode inventoryTool = objectMapper.createObjectNode();
inventoryTool.put("name", "check_erp_inventory");
inventoryTool.put("description", "Check real-time inventory levels in the ERP system for a specific product SKU. " +
        "Returns warehouse stock, incoming shipments, and reorder status.");

ObjectNode inventorySchema = objectMapper.createObjectNode();
inventorySchema.put("type", "object");
ObjectNode inventoryProps = objectMapper.createObjectNode();
ObjectNode skuProp = objectMapper.createObjectNode();
skuProp.put("type", "string");
skuProp.put("description", "Product SKU to check inventory for");
inventoryProps.set("sku", skuProp);
inventorySchema.set("properties", inventoryProps);
ArrayNode inventoryRequired = objectMapper.createArrayNode();
inventoryRequired.add("sku");
inventorySchema.set("required", inventoryRequired);

inventoryTool.set("input_schema", inventorySchema);
tools.add(inventoryTool);
```

**Add to executeToolCallReactive():**
```java
if ("check_erp_inventory".equals(toolName)) {
    String sku = input.get("sku").asText();
    logger.info("Executing ERP inventory check for SKU: {}", sku);

    return erpService.checkInventory(sku)
            .map(results -> {
                try {
                    String jsonResult = objectMapper.writeValueAsString(results);
                    logger.debug("ERP inventory check completed");
                    return jsonResult;
                } catch (Exception e) {
                    logger.error("Error formatting ERP results: {}", e.getMessage());
                    return "Error: " + e.getMessage();
                }
            })
            .onErrorResume(e -> {
                logger.error("Error executing ERP inventory check: {}", e.getMessage());
                return Mono.just("Error checking inventory: " + e.getMessage());
            });
}
```

**Update System Prompt:**
```java
// In buildSystemPrompt() method
if (erpIntegrationEnabled) {
    prompt.append("=== ERP SYSTEM ACCESS ===\n");
    prompt.append("You have access to our ERP (Enterprise Resource Planning) system with these tools:\n");
    prompt.append("- check_erp_inventory: Check real-time warehouse inventory for any SKU\n");
    prompt.append("- get_supplier_info: Get supplier details and lead times\n");
    prompt.append("- check_purchase_order: Check status of purchase orders\n\n");

    prompt.append("Use ERP tools when:\n");
    prompt.append("- User asks about stock availability beyond Shopify\n");
    prompt.append("- User asks about restocking or supplier information\n");
    prompt.append("- User wants to know when out-of-stock items will arrive\n\n");
}
```

### Configuration (application.yml)
```yaml
# ERP/MCP Configuration
erp:
  enabled: ${ERP_ENABLED:false}
  mcp-url: ${ERP_MCP_URL:https://web-production-2534.up.railway.app}
  api-key: ${ERP_API_KEY:}
  timeout-ms: ${ERP_TIMEOUT:10000}
  max-retries: ${ERP_MAX_RETRIES:3}
```

## Implementation Steps (When Resumed)

### Phase 1: Discovery (15 minutes)
1. Obtain MCP API documentation from user
2. Test available endpoints with curl/Postman
3. Document request/response formats
4. Identify authentication requirements

### Phase 2: Service Layer (30 minutes)
1. Create `ERPService.java` with WebClient setup
2. Implement core methods (inventory, suppliers, etc.)
3. Add error handling and retry logic
4. Add comprehensive logging

### Phase 3: Claude Integration (30 minutes)
1. Add new tools to `buildToolsArray()`
2. Add handlers in `executeToolCallReactive()`
3. Update system prompt with ERP capabilities
4. Test tool use with mock data

### Phase 4: Configuration (15 minutes)
1. Add ERP config to `application.yml`
2. Add environment variables to Railway
3. Create feature flag for ERP integration
4. Update documentation

### Phase 5: Testing (30 minutes)
1. Test ERP service methods directly
2. Test Claude tool use with real ERP calls
3. Test error scenarios (timeout, invalid SKU, etc.)
4. Verify logging and monitoring

### Phase 6: Frontend (Optional, 30 minutes)
1. Add ERP data display to Product Search
2. Create ERP status dashboard
3. Add ERP config to Settings page

## Dependencies Required

### Maven (pom.xml)
No new dependencies required - already have:
- `spring-boot-starter-webflux` (for WebClient)
- `jackson-databind` (for JSON processing)
- `lombok` (for reducing boilerplate)

## Configuration Variables Needed

### Environment Variables for Railway:
```bash
ERP_ENABLED=true
ERP_MCP_URL=https://web-production-2534.up.railway.app
ERP_API_KEY=<to_be_provided>
ERP_TIMEOUT=10000
ERP_MAX_RETRIES=3
```

## Testing Checklist

- [ ] ERP health check working
- [ ] Authentication configured correctly
- [ ] Each ERP endpoint tested individually
- [ ] Claude can successfully call ERP tools
- [ ] Error handling works (timeout, 404, 500)
- [ ] Retry logic functioning
- [ ] Logging capturing all operations
- [ ] Performance acceptable (<2s response time)
- [ ] Frontend displays ERP data correctly

## Questions for Next Session

1. **What is the MCP server's authentication method?**
   - API key in header?
   - Token-based?
   - Other?

2. **What are the exact API endpoints?**
   - Inventory: `/api/inventory/{sku}`?
   - Suppliers: `/api/suppliers/{id}`?
   - Purchase orders: `/api/po/{number}`?

3. **What data should Claude have access to?**
   - Just inventory?
   - Suppliers and pricing?
   - Purchase orders?
   - All ERP features?

4. **Should Claude automatically use ERP or ask first?**
   - Example: User asks "Do you have X?" → Auto-check ERP?
   - Or require explicit: "Check the warehouse for X"

5. **Do you have sample API responses?**
   - Would help with error handling and data parsing

## Related Files

### Current Implementation:
- `src/main/java/com/shopify/api/service/ChatAgentService.java` (lines 249-291: tool execution)
- `src/main/java/com/shopify/api/service/ProductService.java` (search implementation reference)
- `src/main/resources/application.yml` (configuration)

### Files to Create:
- `src/main/java/com/shopify/api/service/ERPService.java`
- `src/main/java/com/shopify/api/controller/ERPController.java` (optional)
- `src/main/java/com/shopify/api/model/ERPInventoryResponse.java` (if needed)

## Notes

- MCP server has 96 database tables - suggests comprehensive ERP system
- Current architecture already supports multiple tools (search_products working)
- Adding new tools follows same pattern - straightforward extension
- WebClient reactive approach already proven with ProductService
- Error handling and retry logic patterns already established

## Next Steps

**SUSPENDED** - Resume when:
1. MCP API documentation available
2. Authentication details provided
3. Priority ERP features identified
4. User ready to proceed with implementation

---

**Document Status:** DRAFT
**Last Updated:** 2025-10-13
**Created By:** Claude Code + Nicola Poltronieri
