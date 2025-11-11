# Hearns Hobby MCP Server - Complete Tools Reference

**Version:** 2.0
**Endpoint:** `https://YOUR-APP.up.railway.app/mcp`
**Protocol:** MCP 2024-11-05
**Total Tools:** 25

---

## Table of Contents

1. [General Database Tools](#general-database-tools) (4 tools)
2. [Business Tools](#business-tools) (6 tools)
3. [Individual Product Data Tools](#individual-product-data-tools) (4 tools)
4. [Bulk Operation Tools](#bulk-operation-tools) (4 tools)
5. [Product Discovery Tools](#product-discovery-tools) (3 tools)
6. [Management Tools](#management-tools) (2 tools)
7. [Analytics Tools](#analytics-tools) (2 tools)

---

## General Database Tools

### 1. list_tables

**Description:** List all tables in the SQL Server database

**Parameters:** None

**Returns:** Text list of all table names

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "list_tables",
    "arguments": {}
  }
}
```

**Example Response:**
```
Hearns Hobby database tables (96):
- BarcodeMaster
- CompanyDetails
- CustomerMaster
- InvoiceDetail
- InvoiceHeader
- ProductMaster
- SupplierMaster
...
```

---

### 2. describe_table

**Description:** Get schema information for a specific table

**Parameters:**
- `table_name` (string, required): Name of the table to describe

**Returns:** Schema details with column names, types, nullability

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "describe_table",
    "arguments": {
      "table_name": "ProductMaster"
    }
  }
}
```

**Example Response:**
```
Schema for table 'ProductMaster':
- StockCode: varchar(50) NOT NULL
- Description: varchar(255) NULL
- QtyOnHand: int NULL
- SellingPrice: decimal(18,2) NULL
- SupplierCode: varchar(50) NULL
- Active: bit NULL
...
```

---

### 3. query_database

**Description:** Execute a custom SQL query (SELECT only)

**Parameters:**
- `query` (string, required): SQL query to execute

**Returns:** Query results (up to 10 rows displayed)

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "query_database",
    "arguments": {
      "query": "SELECT TOP 5 StockCode, Description, QtyOnHand FROM ProductMaster WHERE Active = 1"
    }
  }
}
```

**Security:** Only SELECT queries allowed. No INSERT, UPDATE, DELETE, DROP.

---

### 4. sample_table

**Description:** Get sample data from a table

**Parameters:**
- `table_name` (string, required): Name of the table to sample
- `limit` (integer, optional): Number of rows to return (default: 5)

**Returns:** Sample rows from the table

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "tools/call",
  "params": {
    "name": "sample_table",
    "arguments": {
      "table_name": "ProductMaster",
      "limit": 3
    }
  }
}
```

---

## Business Tools

### 5. get_low_stock_products

**Description:** Get products with stock below a threshold (optimized for inventory management)

**Parameters:**
- `threshold` (integer, optional): Stock level threshold (default: 10)

**Returns:** List of low stock products with details

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "method": "tools/call",
  "params": {
    "name": "get_low_stock_products",
    "arguments": {
      "threshold": 20
    }
  }
}
```

**Example Response:**
```
Low stock products (below 20 units): 45 found

1. TAM-31114: TAMIYA ACRYLIC PAINT XF-14 JA GREY
   Stock: 8 | Reorder: 15 | Supplier: Tamiya
2. REV-32145: REVELL MODEL KIT 1/32 SPITFIRE
   Stock: 12 | Reorder: 20 | Supplier: Revell
...
```

**Token Savings:** ~450 tokens vs using query_database

---

### 6. get_customer_orders

**Description:** Get all orders for a specific customer

**Parameters:**
- `customer_id` (integer, required): Customer ID
- `limit` (integer, optional): Maximum orders to return (default: 50)

**Returns:** List of customer orders with status and totals

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "method": "tools/call",
  "params": {
    "name": "get_customer_orders",
    "arguments": {
      "customer_id": 12345,
      "limit": 10
    }
  }
}
```

**Token Savings:** ~400 tokens vs using query_database

---

### 7. get_sales_summary

**Description:** Get sales totals grouped by date range

**Parameters:**
- `start_date` (string, required): Start date (YYYY-MM-DD format)
- `end_date` (string, required): End date (YYYY-MM-DD format)
- `group_by` (string, optional): Group by "day", "week", or "month" (default: "day")

**Returns:** Sales summary with order counts and revenue

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 7,
  "method": "tools/call",
  "params": {
    "name": "get_sales_summary",
    "arguments": {
      "start_date": "2025-01-01",
      "end_date": "2025-01-31",
      "group_by": "week"
    }
  }
}
```

**Token Savings:** ~500 tokens vs using query_database

---

### 8. get_shopify_sync_errors

**Description:** Get recent Shopify synchronization errors

**Parameters:**
- `limit` (integer, optional): Maximum errors to return (default: 50)

**Returns:** List of sync errors with dates and resolution status

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 8,
  "method": "tools/call",
  "params": {
    "name": "get_shopify_sync_errors",
    "arguments": {
      "limit": 25
    }
  }
}
```

**Token Savings:** ~350 tokens vs using query_database

---

### 9. search_products

**Description:** Search products by SKU, barcode, or name

**Parameters:**
- `search_term` (string, required): Search term (partial match supported)
- `search_type` (string, optional): Search in "sku", "barcode", "name", or "all" (default: "all")

**Returns:** List of matching products with stock and price

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 9,
  "method": "tools/call",
  "params": {
    "name": "search_products",
    "arguments": {
      "search_term": "tamiya",
      "search_type": "name"
    }
  }
}
```

**Token Savings:** ~400 tokens vs using query_database

---

### 10. get_sales_by_channel

**Description:** Get sales breakdown by all channels (Store 1, Store 2, Online)

**Parameters:**
- `start_date` (string, required): Start date (YYYY-MM-DD format)
- `end_date` (string, required): End date (YYYY-MM-DD format)

**Returns:** Detailed breakdown of sales by channel with fulfillment tracking

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 10,
  "method": "tools/call",
  "params": {
    "name": "get_sales_by_channel",
    "arguments": {
      "start_date": "2025-01-01",
      "end_date": "2025-01-31"
    }
  }
}
```

---

## Individual Product Data Tools

**NEW** - These tools fulfill the Java Inventory Analysis Service requirements

### 11. get_inventory_level

**Description:** Get current inventory level for a single SKU

**Parameters:**
- `sku` (string, required): Product SKU/StockCode

**Returns:** JSON object with inventory details

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 11,
  "method": "tools/call",
  "params": {
    "name": "get_inventory_level",
    "arguments": {
      "sku": "TAM-31114"
    }
  }
}
```

**Example Response:**
```json
{
  "StockCode": "TAM-31114",
  "QtyOnHand": 45,
  "ReOrderLevel": 20,
  "Active": 1
}
```

**Use Case:** Java service calls this to get current stock for a single SKU

---

### 12. get_sales_history

**Description:** Get sales history for a SKU over the last N days

**Parameters:**
- `sku` (string, required): Product SKU/StockCode
- `days` (integer, optional): Number of days of history (default: 30)

**Returns:** JSON array of sale records

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 12,
  "method": "tools/call",
  "params": {
    "name": "get_sales_history",
    "arguments": {
      "sku": "TAM-31114",
      "days": 60
    }
  }
}
```

**Example Response:**
```json
{
  "sku": "TAM-31114",
  "days": 60,
  "sales": [
    {
      "StockCode": "TAM-31114",
      "Date": "2025-01-08",
      "Quantity": 2,
      "Amount": 15.98,
      "OrderId": "INV-12345",
      "Channel": "ONLINE"
    },
    {
      "StockCode": "TAM-31114",
      "Date": "2025-01-05",
      "Quantity": 1,
      "Amount": 7.99,
      "OrderId": "INV-12340",
      "Channel": "STORE"
    }
  ]
}
```

**Use Case:** Java service uses this to calculate sales velocity

---

### 13. get_product_cost

**Description:** Get product cost and pricing information

**Parameters:**
- `sku` (string, required): Product SKU/StockCode

**Returns:** JSON object with cost and price details

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 13,
  "method": "tools/call",
  "params": {
    "name": "get_product_cost",
    "arguments": {
      "sku": "TAM-31114"
    }
  }
}
```

**Example Response:**
```json
{
  "sku": "TAM-31114",
  "costPerUnit": 7.99,
  "sellingPrice": 7.99,
  "quantity": 45,
  "supplierCode": "TAMIYA"
}
```

**Note:** Currently uses SellingPrice as costPerUnit. Update if CostPrice field is added to database.

**Use Case:** Java service uses this for order cost calculations

---

### 14. get_supplier_info

**Description:** Get supplier information for a SKU

**Parameters:**
- `sku` (string, required): Product SKU/StockCode

**Returns:** JSON object with supplier details

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 14,
  "method": "tools/call",
  "params": {
    "name": "get_supplier_info",
    "arguments": {
      "sku": "TAM-31114"
    }
  }
}
```

**Example Response:**
```json
{
  "sku": "TAM-31114",
  "supplierCode": "TAMIYA",
  "supplierName": "Tamiya Inc.",
  "reorderLevel": 20,
  "leadTimeDays": 14
}
```

**Note:** leadTimeDays currently defaults to 14. Update if LeadTime field is added to database.

**Use Case:** Java service uses this for supplier-based ordering and lead time calculations

---

## Bulk Operation Tools

**NEW** - Performance critical tools for batch operations

### 15. get_inventory_levels_bulk

**Description:** Get inventory levels for multiple SKUs at once (batch operation)

**Parameters:**
- `skus` (array of strings, required): Array of product SKUs

**Returns:** JSON map of SKU → inventory details

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 15,
  "method": "tools/call",
  "params": {
    "name": "get_inventory_levels_bulk",
    "arguments": {
      "skus": ["TAM-31114", "TAM-31115", "TAM-31116"]
    }
  }
}
```

**Example Response:**
```json
{
  "TAM-31114": {
    "quantity": 45,
    "reorderLevel": 20,
    "active": 1
  },
  "TAM-31115": {
    "quantity": 32,
    "reorderLevel": 15,
    "active": 1
  },
  "TAM-31116": {
    "quantity": 8,
    "reorderLevel": 20,
    "active": 1
  }
}
```

**Performance:** 100 SKUs = 1 call instead of 100 individual calls (99% reduction)

**Use Case:** Replace individual `get_inventory_level()` calls in loops

---

### 16. get_sales_history_bulk

**Description:** Get sales history for multiple SKUs at once

**Parameters:**
- `skus` (array of strings, required): Array of product SKUs
- `days` (integer, optional): Number of days of history (default: 30)

**Returns:** JSON map of SKU → sales array

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 16,
  "method": "tools/call",
  "params": {
    "name": "get_sales_history_bulk",
    "arguments": {
      "skus": ["TAM-31114", "TAM-31115"],
      "days": 30
    }
  }
}
```

**Example Response:**
```json
{
  "TAM-31114": [
    {
      "date": "2025-01-08",
      "quantity": 2,
      "amount": 15.98,
      "orderId": "INV-12345",
      "channel": "ONLINE"
    }
  ],
  "TAM-31115": [
    {
      "date": "2025-01-07",
      "quantity": 1,
      "amount": 8.50,
      "orderId": "INV-12344",
      "channel": "STORE"
    }
  ]
}
```

**Performance:** 100 SKUs = 1 call instead of 100 individual calls (99% reduction)

**Use Case:** Replace individual `get_sales_history()` calls in `generateBulkOrderPlan()`

---

### 17. get_costs_bulk

**Description:** Get costs for multiple SKUs at once

**Parameters:**
- `skus` (array of strings, required): Array of product SKUs

**Returns:** JSON map of SKU → cost details

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 17,
  "method": "tools/call",
  "params": {
    "name": "get_costs_bulk",
    "arguments": {
      "skus": ["TAM-31114", "TAM-31115", "TAM-31116"]
    }
  }
}
```

**Example Response:**
```json
{
  "TAM-31114": {
    "costPerUnit": 7.99,
    "sellingPrice": 7.99,
    "supplierCode": "TAMIYA"
  },
  "TAM-31115": {
    "costPerUnit": 8.50,
    "sellingPrice": 8.50,
    "supplierCode": "TAMIYA"
  }
}
```

**Performance:** 100 SKUs = 1 call instead of 100 individual calls (99% reduction)

**Use Case:** Replace individual `get_product_cost()` calls in order calculations

---

### 18. get_suppliers_bulk

**Description:** Get supplier information for multiple SKUs at once

**Parameters:**
- `skus` (array of strings, required): Array of product SKUs

**Returns:** JSON map of SKU → supplier details

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 18,
  "method": "tools/call",
  "params": {
    "name": "get_suppliers_bulk",
    "arguments": {
      "skus": ["TAM-31114", "REV-32145", "ITA-12456"]
    }
  }
}
```

**Example Response:**
```json
{
  "TAM-31114": {
    "supplierCode": "TAMIYA",
    "supplierName": "Tamiya Inc.",
    "reorderLevel": 20,
    "leadTimeDays": 14
  },
  "REV-32145": {
    "supplierCode": "REVELL",
    "supplierName": "Revell GmbH",
    "reorderLevel": 15,
    "leadTimeDays": 14
  }
}
```

**Performance:** 100 SKUs = 1 call instead of 100 individual calls (99% reduction)

**Use Case:** Replace individual `get_supplier_info()` calls when grouping orders by supplier

---

## Product Discovery Tools

**NEW** - Replace hacky SKU pattern matching with proper database queries

### 19. get_product_metadata

**Description:** Get comprehensive metadata for a SKU

**Parameters:**
- `sku` (string, required): Product SKU/StockCode

**Returns:** JSON object with complete product metadata

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 19,
  "method": "tools/call",
  "params": {
    "name": "get_product_metadata",
    "arguments": {
      "sku": "TAM-31114"
    }
  }
}
```

**Example Response:**
```json
{
  "sku": "TAM-31114",
  "title": "TAMIYA ACRYLIC PAINT XF-14 JA GREY",
  "brand": "Tamiya Inc.",
  "category": "Unknown",
  "supplier": "Tamiya Inc.",
  "supplierCode": "TAMIYA",
  "upc": "4950344995783",
  "description": "TAMIYA ACRYLIC PAINT XF-14 JA GREY",
  "quantity": 45,
  "price": 7.99,
  "active": 1
}
```

**Note:**
- `brand` uses SupplierName (no explicit Brand field in database)
- `category` is "Unknown" (no ProductType field in database - would need to be added)

**Use Case:** Determine if a SKU is paint, model kit, tool, etc. without pattern matching

---

### 20. search_products_filtered

**Description:** Search products with filters and return list of SKUs

**Parameters:**
- `brand` (string, optional): Filter by brand/supplier name
- `category` (string, optional): Filter by category (Paint, Model Kit, Tools, etc.)
- `supplier` (string, optional): Filter by supplier name
- `product_type` (string, optional): Filter by product type
- `limit` (integer, optional): Maximum SKUs to return (default: 100)

**Returns:** JSON object with filters applied and array of matching SKUs

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 20,
  "method": "tools/call",
  "params": {
    "name": "search_products_filtered",
    "arguments": {
      "brand": "Tamiya",
      "category": "Paint",
      "limit": 50
    }
  }
}
```

**Example Response:**
```json
{
  "filters": {
    "brand": "Tamiya",
    "category": "Paint"
  },
  "skus": [
    "TAM-31114",
    "TAM-31115",
    "TAM-31116",
    "TAM-31117"
  ],
  "count": 4
}
```

**Use Case:** Replace `matchesBrand()` and `matchesCategory()` helper functions in Java code

---

### 21. get_products_by_supplier

**Description:** Get all SKUs for a specific supplier

**Parameters:**
- `supplier_name` (string, required): Supplier name (partial match supported)
- `limit` (integer, optional): Maximum SKUs to return (default: 100)

**Returns:** JSON object with supplier name and array of SKUs

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 21,
  "method": "tools/call",
  "params": {
    "name": "get_products_by_supplier",
    "arguments": {
      "supplier_name": "Tamiya",
      "limit": 100
    }
  }
}
```

**Example Response:**
```json
{
  "supplier": "Tamiya",
  "skus": [
    "TAM-31114",
    "TAM-31115",
    "TAM-31116",
    "TAM-35296",
    "TAM-87001"
  ],
  "count": 5
}
```

**Use Case:** When user says "order all Tamiya products", get the complete SKU list directly

---

## Management Tools

**NEW** - Browse and discover suppliers and categories

### 22. list_suppliers

**Description:** Get list of all suppliers with product counts

**Parameters:** None

**Returns:** Text list of suppliers with product counts

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 22,
  "method": "tools/call",
  "params": {
    "name": "list_suppliers",
    "arguments": {}
  }
}
```

**Example Response:**
```
Suppliers (45 total):

1. Tamiya Inc. (TAMIYA)
   Products: 1250 | Active: 1180
2. Revell GmbH (REVELL)
   Products: 890 | Active: 856
3. Italeri SpA (ITALERI)
   Products: 456 | Active: 445
...
```

**Use Case:** Browse available suppliers, see which have the most products

---

### 23. list_categories

**Description:** Get list of product categories with counts

**Parameters:** None

**Returns:** Text list of categories with product counts

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 23,
  "method": "tools/call",
  "params": {
    "name": "list_categories",
    "arguments": {}
  }
}
```

**Example Response:**
```
Categories (4 total):

1. Paint: 2340 products
2. Model Kit: 1876 products
3. Tools: 456 products
4. Accessories: 789 products
```

**Note:** Categories are extracted from Description field patterns. This is a workaround - ideally database would have a ProductType field.

**Use Case:** Browse available categories, understand product distribution

---

## Analytics Tools

**NEW** - Sales forecasting and reorder recommendations

### 24. get_sales_forecast

**Description:** Get sales forecast based on historical data

**Parameters:**
- `sku` (string, required): Product SKU/StockCode
- `days` (integer, optional): Days to forecast (default: 30)

**Returns:** JSON object with forecast and confidence level

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 24,
  "method": "tools/call",
  "params": {
    "name": "get_sales_forecast",
    "arguments": {
      "sku": "TAM-31114",
      "days": 30
    }
  }
}
```

**Example Response:**
```json
{
  "sku": "TAM-31114",
  "predictedDailySales": 1.52,
  "confidence": "high",
  "basedOnDays": 87,
  "totalSales": 132
}
```

**Confidence Levels:**
- `high`: Based on 60+ days of sales data
- `medium`: Based on 30-59 days of sales data
- `low`: Based on <30 days of sales data

**Use Case:** Predict future demand for inventory planning

---

### 25. get_reorder_recommendation

**Description:** Get reorder recommendation based on current stock and sales velocity

**Parameters:**
- `sku` (string, required): Product SKU/StockCode

**Returns:** JSON object with recommendation and urgency

**Example Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 25,
  "method": "tools/call",
  "params": {
    "name": "get_reorder_recommendation",
    "arguments": {
      "sku": "TAM-31114"
    }
  }
}
```

**Example Response:**
```json
{
  "sku": "TAM-31114",
  "currentStock": 8,
  "reorderLevel": 20,
  "dailyVelocity": 1.52,
  "daysUntilStockout": 5.3,
  "recommendedQuantity": 46,
  "urgency": "CRITICAL"
}
```

**Urgency Levels:**
- `CRITICAL`: Current stock ≤ reorder level
- `HIGH`: Will stock out in < 14 days
- `MEDIUM`: Will stock out in < 30 days
- `LOW`: Stock is sufficient

**Recommendation Logic:**
- CRITICAL: Order max(reorderLevel × 2, 30 days supply)
- HIGH: Order 30 days supply
- MEDIUM: Order 21 days supply
- LOW: No order needed

**Use Case:** Automated reorder recommendations based on velocity and current stock

---

## Integration Guide

### HTTP Endpoint
```
POST https://YOUR-APP.up.railway.app/mcp
Content-Type: application/json
```

### Request Format
All requests follow JSON-RPC 2.0 format:

```json
{
  "jsonrpc": "2.0",
  "id": <unique_request_id>,
  "method": "tools/call",
  "params": {
    "name": "<tool_name>",
    "arguments": {
      // tool-specific parameters
    }
  }
}
```

### Response Format
Successful responses:

```json
{
  "jsonrpc": "2.0",
  "id": <request_id>,
  "result": {
    "content": [{
      "type": "text",
      "text": "<tool_output>"
    }]
  }
}
```

Error responses:

```json
{
  "jsonrpc": "2.0",
  "id": <request_id>,
  "error": {
    "code": -32603,
    "message": "<error_message>"
  }
}
```

---

## Performance Comparison

### Individual vs Bulk Operations

**Scenario:** Generate bulk order plan for 100 Tamiya paint SKUs

**Using Individual Tools (OLD):**
```
100 SKUs × 4 calls per SKU = 400 total MCP calls
- get_inventory_level() × 100
- get_sales_history() × 100
- get_product_cost() × 100
- get_supplier_info() × 100

Estimated time: 60-120 seconds (with network latency)
```

**Using Bulk Tools (NEW):**
```
4 total bulk MCP calls:
- get_inventory_levels_bulk([100 SKUs])
- get_sales_history_bulk([100 SKUs], 30)
- get_costs_bulk([100 SKUs])
- get_suppliers_bulk([100 SKUs])

Estimated time: 2-5 seconds
Performance improvement: 99% reduction in calls, 95%+ faster
```

---

## Security & Limitations

### Security Features
- ✅ Read-only operations (SELECT queries only)
- ✅ Parameterized queries (SQL injection protection)
- ✅ HTTPS encryption via Railway
- ✅ Input validation via MCP protocol
- ✅ No UPDATE, INSERT, DELETE, DROP allowed

### Current Limitations
1. **CostPrice field**: Using SellingPrice as proxy (update when CostPrice added to DB)
2. **Brand field**: Using SupplierName as proxy (update when Brand added to DB)
3. **ProductType field**: Extracting from Description patterns (update when ProductType added to DB)
4. **LeadTime field**: Default 14 days (update when LeadTime added to SupplierMaster)

### Database Requirements
- SQL Server database
- Tables: ProductMaster, SupplierMaster, InvoiceHeader, InvoiceDetail, BarcodeMaster, ShopifyOrder
- Network access from Railway to SQL Server (IP: 58.179.146.192:1433)

---

## Support & Documentation

**Full Documentation:** `NEW_TOOLS_IMPLEMENTATION_SUMMARY.md`
**Source Code:** `http_server.py`
**Deployment Guide:** `DEPLOYMENT_GUIDE.md`
**Tool Development:** `TOOL_DEVELOPMENT_GUIDE.md`

**Railway Deployment:** Auto-deploys on git push to main branch
**Health Check:** `GET /health`
**MCP Protocol:** `POST /mcp`
**SSE Streaming:** `GET /sse`

---

## Version History

**v2.0 (2025-11-08)**
- Added 15 new tools (individual, bulk, discovery, management, analytics)
- 99% performance improvement for bulk operations
- Total tools: 25

**v1.0 (Initial Release)**
- 10 original tools (general database + business tools)
- Total tools: 10

---

**End of Reference Document**

For questions or integration support, refer to the implementation summary or source code documentation.
