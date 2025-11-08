# Inventory Management Module - Complete System Documentation

**Version:** 2.0
**Date:** November 8, 2025
**Status:** In Development

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [User Interface Structure](#user-interface-structure)
4. [Backend Components](#backend-components)
5. [Frontend Components](#frontend-components)
6. [API Reference](#api-reference)
7. [AI Agent System](#ai-agent-system)
8. [User Guides](#user-guides)
9. [Configuration](#configuration)
10. [Deployment](#deployment)

---

## Overview

The Inventory Management Module is a comprehensive system that combines **static dashboards** for quick monitoring with a **conversational AI agent** for flexible, multi-dimensional analysis and order planning.

### Key Features

- **Static Dashboards**: Quick at-a-glance metrics, alerts, and recommendations
- **AI Conversational Agent**: Natural language queries with human-in-the-loop order planning
- **Multi-Dimensional Analysis**: Filter by brand, category, supplier, velocity
- **Interactive Order Builder**: Shopping cart interface for purchase orders
- **Sales Velocity Tracking**: Trend detection and forecasting
- **MCP/ERP Integration**: Real-time data from in-store system

### Business Value

- **Reduce Stockouts**: Automated alerts for low inventory
- **Optimize Cash Flow**: Order only what you need based on actual sales velocity
- **Save Time**: AI assistant analyzes hundreds of products in seconds
- **Minimize Overstock**: Human-controlled order quantities with AI recommendations
- **Improve Margins**: Identify slow-moving products, optimize supplier orders

---

## Architecture

### System Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend (React)                          │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Static Pages: Dashboard, Alerts, Recommendations     │   │
│  │ AI Assistant: Conversational Interface               │   │
│  │ Order Builder: Interactive Cart                      │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            ↕ REST API
┌─────────────────────────────────────────────────────────────┐
│              Backend (Spring Boot / Java)                    │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Controllers: REST endpoints                          │   │
│  │ Services: Business logic, AI agent                   │   │
│  │ Repositories: Database access                        │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                    Data Sources                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │PostgreSQL│  │ERP (MCP) │  │ Shopify  │  │Claude AI │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

1. **ERP (MCP)** - Primary data source:
   - Sales history (by SKU, brand, category)
   - Current inventory levels
   - Supplier information
   - Product metadata (brand, category)

2. **PostgreSQL** - Calculated metrics storage:
   - Sales velocity calculations
   - Order recommendations
   - Stock alerts
   - Analysis history

3. **Shopify** - Secondary cross-reference:
   - Product catalog
   - Pricing information
   - Online sales data

4. **Claude AI** - Intelligence layer:
   - Natural language processing
   - Tool calling for analysis
   - Order planning recommendations
   - Context-aware conversations

---

## User Interface Structure

### Main Navigation

```
📦 Inventory Management (Dropdown Menu)
   ├── 📊 Dashboard          (/inventory/dashboard)
   ├── 💬 AI Assistant       (/inventory/ai-assistant)
   ├── 🚨 Alerts             (/inventory/alerts)
   ├── 📝 Recommendations    (/inventory/recommendations)
   ├── 📈 Velocity Tracking  (/inventory/velocity)
   └── 🛒 Order Builder      (/inventory/orders)
```

### Page Descriptions

#### 1. Dashboard (Static Overview)
**Purpose:** Quick at-a-glance system status

**Sections:**
- **Key Metrics Cards**:
  - Total Products Tracked
  - Critical Stock Count (< 3 days)
  - Pending Recommendations
  - Total Pending Order Value

- **Inventory Health Chart**:
  - Donut chart: Healthy / Fair / Warning / Critical / Out of Stock

- **Top 5 Critical Alerts**:
  - Product name, SKU
  - Days of stock remaining
  - Recommended action

- **Recent Recommendations Table**:
  - Product, Supplier, Quantity, Cost
  - Quick approve/dismiss actions

#### 2. AI Assistant (Conversational Analysis)
**Purpose:** Flexible, natural language inventory analysis

**Features:**
- Chat interface with message history
- Quick action prompts:
  - "Show low stock items"
  - "Analyze by brand"
  - "Create order plan for 30 days"
  - "Compare suppliers"
- Tool transparency (see which tools AI uses)
- Interactive result cards
- Add items to order cart from chat
- Export conversation/analysis

**Example Queries:**
```
"Show me Gundam products that are low on stock"
"What Tamiya paints should I order for 6 weeks?"
"Which suppliers should I order from this week?"
"Compare inventory health by brand"
"Show fast-moving products under $50"
```

#### 3. Alerts (Low Stock Warnings)
**Purpose:** Monitor and manage stock alerts

**Features:**
- Filter by alert level (Critical / Warning / Info)
- Filter by brand, category
- Sortable table (by urgency, days remaining, SKU)
- Bulk resolve actions
- One-click "Add to Order Cart"
- Auto-refresh every 5 minutes

**Alert Levels:**
- 🔴 **Critical**: < 3 days stock
- 🟡 **Warning**: < 7 days stock
- 🔵 **Info**: < 14 days stock

#### 4. Recommendations (Order Suggestions)
**Purpose:** Review and manage AI-generated order recommendations

**Features:**
- Filter by status (Pending / Approved / Dismissed)
- Filter by urgency (Critical / High / Medium / Low)
- Detailed view:
  - Product info (SKU, name, brand)
  - Sales velocity (daily average)
  - Current stock level
  - Days until stockout
  - Recommended quantity & cost
  - Supplier info
  - AI reasoning/notes
- Actions:
  - Approve (adds to order cart)
  - Dismiss (with reason)
  - Edit quantity
  - Request more details

#### 5. Velocity Tracking (Sales Trends)
**Purpose:** Monitor sales velocity and trends

**Features:**
- Sales velocity charts (line/bar)
- Trend indicators: ↑ Increasing, ↓ Decreasing, → Stable
- Filter by:
  - Brand
  - Category
  - Time period (7d, 30d, 90d)
- Comparison views:
  - Week-over-week
  - Month-over-month
  - Year-over-year
- Top Movers list (fast-selling products)
- Slow Movers list (consider clearance)

#### 6. Order Builder (Purchase Order Planning)
**Purpose:** Build and manage purchase orders

**Features:**
- Shopping cart interface
- Group by supplier
- Calculate totals:
  - Subtotal by supplier
  - Grand total
  - Estimated shipping
- Apply constraints:
  - Minimum Order Quantities (MOQ)
  - Case pack quantities
- Edit quantities inline
- Save draft orders
- Export formats:
  - PDF purchase order
  - CSV for import
  - Email to supplier
- Order history

---

## Backend Components

### Controllers

#### InventoryAnalysisController
**Base Path:** `/api/inventory-management`

**Endpoints:**
- `GET /dashboard` - Dashboard overview data
- `GET /health` - Module health check
- `GET /low-stock` - Low stock alerts (filterable)
- `GET /recommendations` - Order recommendations (filterable)
- `POST /analyze/{sku}` - Analyze single product
- `GET /velocity/{sku}` - Get sales velocity for SKU
- `PUT /recommendations/{id}/approve` - Approve recommendation
- `DELETE /recommendations/{id}` - Dismiss recommendation
- `PUT /alerts/{id}/resolve` - Resolve alert
- `GET /by-brand/{brand}` - Analysis filtered by brand
- `GET /by-category/{category}` - Analysis filtered by category
- `GET /by-supplier/{supplier}` - Analysis filtered by supplier
- `GET /brands` - List all brands
- `GET /categories` - List all categories

#### InventoryAnalysisAgentController
**Base Path:** `/api/inventory-management/agent`

**Endpoints:**
- `POST /chat` - Conversational interface
  - Input: `{ message, conversationHistory, context }`
  - Output: `{ response, toolCalls, recommendations }`
- `GET /tools` - List available tools
- `POST /reset` - Clear conversation history

### Services

#### InventoryAnalysisAgent
**Purpose:** AI agent orchestrator with Claude integration

**Responsibilities:**
- Process natural language queries
- Select and execute appropriate tools
- Maintain conversation context
- Generate human-readable responses
- Handle human-in-the-loop interactions

**Tools Available:**
1. `analyzeByBrand(brand, days)` - Analyze specific brand inventory
2. `analyzeByCategory(category, days)` - Analyze category inventory
3. `analyzeBySupplier(supplier, days)` - Analyze supplier inventory
4. `getLowStockFiltered(filters)` - Get filtered low stock items
5. `generateOrderPlan(sku, targetDays)` - Calculate order quantities
6. `getSupplierSummary(supplier)` - Get supplier order summary
7. `predictStockout(sku, days)` - Predict when SKU will stock out
8. `compareProducts(skuList)` - Compare multiple products

#### InventoryAnalysisService
**Purpose:** Core analysis logic (existing, enhanced)

**Key Methods:**
- `analyzeSingleProduct(sku)` - Full product analysis
- `getDashboardData()` - Dashboard metrics
- `calculateReorderPoint(velocity, leadTime)` - Reorder point formula
- `calculateSafetyStock(velocity)` - Safety stock calculation
- `generateRecommendation(analysis)` - Create order recommendation

#### SalesVelocityCalculator
**Purpose:** Calculate sales velocity and trends

**Key Methods:**
- `calculate(salesRecords, sku)` - Basic velocity calculation
- `calculateDetailed(salesRecords, sku)` - With trend detection
- `predictSales(velocity, days)` - Forecast future sales

#### OrderBuilderService
**Purpose:** Interactive order planning

**Key Methods:**
- `createOrderPlan(items)` - Build purchase order
- `groupBySupplier(items)` - Group items by supplier
- `applyConstraints(items, constraints)` - Apply MOQ, case packs
- `calculateTotals(orderPlan)` - Calculate costs
- `exportPDF(orderPlan)` - Generate PDF purchase order
- `exportCSV(orderPlan)` - Generate CSV export

#### ERPInventoryService
**Purpose:** MCP/ERP integration (enhanced)

**Key Methods (New):**
- `getProductMetadata(sku)` - Get brand, category, supplier info
- `getProductsByBrand(brand)` - Get all products for a brand
- `getProductsByCategory(category)` - Get all products in category
- `getSalesHistoryFiltered(filters, days)` - Filtered sales history

**Key Methods (Existing):**
- `getSalesHistory(sku, days)` - Historical sales data
- `getInventoryLevel(sku)` - Current stock level
- `getSupplierInfo(sku)` - Supplier details
- `getProductCost(sku)` - Product cost

### Models

#### New Models:

**OrderPlan.java**
```java
public class OrderPlan {
    private List<OrderItem> items;
    private Map<String, SupplierOrder> supplierOrders;
    private BigDecimal grandTotal;
    private LocalDateTime createdAt;
    private String createdBy;
    private String status; // DRAFT, SUBMITTED, APPROVED
}
```

**OrderItem.java**
```java
public class OrderItem {
    private String sku;
    private String productName;
    private String brand;
    private String supplier;
    private Integer quantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private Integer targetDays;
    private String notes;
}
```

**SupplierOrder.java**
```java
public class SupplierOrder {
    private String supplierName;
    private List<OrderItem> items;
    private BigDecimal subtotal;
    private Integer itemCount;
    private Boolean meetsMinimum;
}
```

---

## Frontend Components

### Page Components (6 files)

#### 1. Dashboard.jsx
**Location:** `frontend/src/pages/inventory/Dashboard.jsx`

**Props:** None

**State:**
- dashboardData: Dashboard metrics from API
- loading: Boolean
- error: Error message

**Components Used:**
- InventoryCard × 4 (metrics)
- DonutChart (inventory health)
- StockAlertCard × 5 (critical alerts)
- RecommendationCard × 5 (recent recommendations)

#### 2. AIAssistant.jsx
**Location:** `frontend/src/pages/inventory/AIAssistant.jsx`

**Props:** None

**State:**
- messages: Conversation history
- input: Current message
- loading: Boolean
- toolCalls: Recent tool executions
- context: Conversation context

**Components Used:**
- ChatInterface (reused from existing)
- QuickActionButton × 4
- ResultCard (for analysis results)
- OrderCartWidget

#### 3. Alerts.jsx
**Location:** `frontend/src/pages/inventory/Alerts.jsx`

**Props:** None

**State:**
- alerts: Array of alerts
- filters: { level, brand, category }
- sortBy: String
- selectedAlerts: Array

**Components Used:**
- FilterPanel
- StockAlertCard (for each alert)
- BulkActionBar

#### 4. Recommendations.jsx
**Location:** `frontend/src/pages/inventory/Recommendations.jsx`

**Props:** None

**State:**
- recommendations: Array
- filters: { status, urgency }
- sortBy: String

**Components Used:**
- FilterPanel
- RecommendationCard (for each)
- ApprovalModal

#### 5. VelocityTracking.jsx
**Location:** `frontend/src/pages/inventory/VelocityTracking.jsx`

**Props:** None

**State:**
- velocityData: Chart data
- filters: { brand, category, period }
- topMovers: Array
- slowMovers: Array

**Components Used:**
- FilterPanel
- VelocityChart (line/bar)
- VelocityIndicator
- ProductTable

#### 6. OrderBuilder.jsx
**Location:** `frontend/src/pages/inventory/OrderBuilder.jsx`

**Props:** None

**State:**
- cart: Array of OrderItems
- groupedBySupplier: Map
- totals: Object
- constraints: MOQ, case packs

**Components Used:**
- SupplierGroup × N
- OrderItemRow × N
- TotalsCard
- ExportButtons

### Reusable Components (7 files)

#### 1. InventoryCard.jsx
**Purpose:** Metric display card with trend

**Props:**
- title: String
- value: Number/String
- trend: { direction, percentage }
- icon: String (emoji)
- color: String (bg color)

#### 2. StockAlertCard.jsx
**Purpose:** Alert notification card

**Props:**
- alert: Object
- onResolve: Function
- onAddToCart: Function

**Displays:**
- Product name, SKU
- Alert level (color-coded)
- Days remaining
- Quick actions

#### 3. RecommendationCard.jsx
**Purpose:** Order recommendation card

**Props:**
- recommendation: Object
- onApprove: Function
- onDismiss: Function
- onEdit: Function

**Displays:**
- Product details
- Sales velocity
- Recommended quantity & cost
- Supplier info
- AI reasoning

#### 4. VelocityIndicator.jsx
**Purpose:** Visual velocity indicator

**Props:**
- velocity: Number (daily average)
- trend: 'up' | 'down' | 'stable'
- size: 'small' | 'medium' | 'large'

**Displays:**
- Gauge or bar chart
- Trend arrow
- Color-coded (red/yellow/green)

#### 5. ProductAnalyzer.jsx
**Purpose:** Single product deep-dive modal

**Props:**
- sku: String
- onClose: Function

**Displays:**
- Product info
- Sales velocity chart
- Stock level timeline
- Recommendations
- Supplier info

#### 6. OrderCartWidget.jsx
**Purpose:** Floating cart icon with badge

**Props:**
- itemCount: Number
- totalValue: Number
- onOpen: Function

**Displays:**
- Cart icon
- Item count badge
- Quick view panel

#### 7. FilterPanel.jsx
**Purpose:** Reusable filter component

**Props:**
- filters: Object
- availableFilters: Array
- onChange: Function

**Supports:**
- Brand dropdown
- Category dropdown
- Supplier dropdown
- Date range picker
- Alert level checkboxes

---

## API Reference

### Inventory Management Endpoints

#### GET /api/inventory-management/dashboard
**Description:** Get dashboard overview data

**Response:**
```json
{
  "success": true,
  "data": {
    "summary": {
      "totalProducts": 487,
      "trackedProducts": 412,
      "lowStockCount": 23,
      "criticalStockCount": 8,
      "pendingRecommendations": 15,
      "totalRecommendedOrderValue": 12450.50
    },
    "inventoryHealth": {
      "healthy": 320,
      "fair": 67,
      "warning": 15,
      "critical": 8,
      "outOfStock": 2
    },
    "topCriticalAlerts": [...],
    "recentRecommendations": [...]
  }
}
```

#### GET /api/inventory-management/by-brand/{brand}
**Description:** Get inventory analysis filtered by brand

**Parameters:**
- brand (path): Brand name (e.g., "Tamiya", "Bandai")
- days (query, optional): Analysis window (default: 30)

**Response:**
```json
{
  "success": true,
  "data": {
    "brand": "Tamiya",
    "totalProducts": 47,
    "lowStockCount": 12,
    "averageVelocity": 3.2,
    "totalValue": 8450.00,
    "products": [...]
  }
}
```

#### POST /api/inventory-management/agent/chat
**Description:** Conversational AI interface

**Request:**
```json
{
  "message": "Show me Gundam products that need ordering",
  "conversationHistory": [...],
  "context": {
    "preferredStockDays": 30,
    "budgetLimit": 10000
  }
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "response": "I found 8 Gundam products below reorder point. Here's the analysis:\n\n1. RX-78 Gundam (SKU: GUN-001)\n   - Current stock: 24 units (3 days)\n   - Velocity: 8/day\n   - Recommendation: Order 240 units (30 days) @ $15/unit = $3,600\n\n...",
    "toolCalls": [
      {
        "tool": "analyzeByBrand",
        "parameters": { "brand": "Gundam", "days": 30 },
        "result": {...}
      }
    ],
    "recommendations": [...],
    "conversationId": "conv-123"
  }
}
```

---

## AI Agent System

### System Prompt

**File:** `src/main/resources/prompts/inventory-agent-prompt.txt`

```
You are an expert inventory management assistant for a hobby store specializing in model kits, paints, and hobby supplies.

BRANDS: Tamiya, Bandai, Gundam, Vallejo, Citadel, Testors, Mr. Hobby, and more
CATEGORIES: Model Kits, Acrylic Paints, Enamel Paints, Tools, Accessories, Decals, Weathering Supplies

YOUR ROLE:
- Help users analyze stock levels and make informed purchasing decisions
- Identify products that need reordering
- Calculate order quantities based on sales velocity
- Group recommendations by supplier to minimize shipping costs
- Provide clear, actionable insights

ANALYSIS CAPABILITIES:
You can analyze inventory by:
- Brand (e.g., "Show Tamiya products")
- Category (e.g., "Analyze model kits")
- Supplier (e.g., "What should I order from Bandai?")
- Sales velocity (e.g., "Show fast-moving items")
- Price range (e.g., "Products under $50")
- Combinations (e.g., "Tamiya paints selling slowly")

ORDER PLANNING GUIDELINES:
1. ALWAYS ask for desired stock coverage before recommending quantities
   - Example: "How many days/weeks of stock would you like to order?"
   - Common responses: "30 days", "2 weeks", "1 month", "until next shipment"

2. Consider supplier constraints:
   - Minimum Order Quantities (MOQ)
   - Case pack sizes
   - Shipping costs (order more from fewer suppliers)

3. Highlight urgency:
   - Critical: < 3 days stock (ORDER IMMEDIATELY)
   - High: < 7 days stock (ORDER THIS WEEK)
   - Medium: < 14 days stock (ORDER SOON)

4. Show cost calculations clearly:
   - Unit cost × Quantity = Total
   - Group by supplier with subtotals
   - Include grand total

5. Allow quantity adjustments:
   - "Would you like to adjust any quantities?"
   - "Should I increase/decrease stock coverage?"

FORMATTING:
- Use bullet points for lists
- Show product details: Name (SKU) - stock level - velocity
- Use emojis sparingly: ⚠️ for warnings, ✅ for good stock
- Bold important numbers and recommendations
- Keep responses concise but complete

EXAMPLES:

User: "What needs ordering this week?"
You: "Found 15 products below reorder point (< 7 days stock):

⚠️ CRITICAL (< 3 days):
1. Gundam RX-78 (GUN-001) - 2 days left, sells 8/day
2. Tamiya TS-13 (TAM-013) - 1 day left, sells 12/day

HIGH (< 7 days):
3. Vallejo Black (VAL-001) - 5 days left, sells 4/day
...

How many days of stock would you like to order for?"

User: "30 days for everything"
You: "Calculating 30-day orders:

TOTAL ORDER: $12,450 (15 products, 1,847 units)

By Supplier:
📦 Bandai: $6,240 (8 products)
📦 Tamiya: $3,890 (5 products)
📦 Vallejo: $2,320 (2 products)

Top 3 by cost:
1. GUN-001: 240 units @ $15 = $3,600
2. TAM-013: 360 units @ $8 = $2,880
3. VAL-001: 120 units @ $12 = $1,440

Shall I show details for each supplier?"
```

### Tool Definitions

#### Tool: analyzeByBrand
**Purpose:** Analyze inventory for specific brand

**Parameters:**
- brand (String, required): Brand name
- days (Integer, optional): Analysis window (default: 30)

**Returns:**
```json
{
  "brand": "Tamiya",
  "totalProducts": 47,
  "lowStockCount": 12,
  "criticalCount": 3,
  "products": [...]
}
```

#### Tool: generateOrderPlan
**Purpose:** Calculate order quantities for a product

**Parameters:**
- sku (String, required): Product SKU
- targetDays (Integer, required): Desired stock coverage in days

**Returns:**
```json
{
  "sku": "GUN-001",
  "productName": "RX-78 Gundam",
  "currentStock": 24,
  "dailyVelocity": 8,
  "targetDays": 30,
  "recommendedQuantity": 240,
  "unitCost": 15.00,
  "totalCost": 3600.00,
  "supplier": "Bandai"
}
```

---

## User Guides

### Quick Start Guide

#### For Daily Monitoring:
1. Open **Inventory Management → Dashboard**
2. Check Critical Stock count (should be 0)
3. Review Top Alerts (if any)
4. Check Pending Recommendations

#### For Weekly Ordering:
1. Open **AI Assistant**
2. Ask: "What needs ordering this week?"
3. Specify stock coverage: "30 days for fast movers, 14 days for slow"
4. Review AI recommendations
5. Adjust quantities as needed
6. Open **Order Builder**
7. Review cart grouped by supplier
8. Export purchase orders

#### For Brand Analysis:
1. Open **AI Assistant**
2. Ask: "Analyze Tamiya inventory"
3. Review brand health metrics
4. Ask follow-ups: "Show slow-moving Tamiya products"
5. Make clearance or ordering decisions

### Example Workflows

#### Workflow 1: Daily Stock Check
```
Time: 5 minutes

1. Open Dashboard
2. See: 3 critical alerts
3. Click "View Alerts"
4. Filter to Critical
5. Click "Add All to Cart"
6. Open Order Builder
7. Review & export
```

#### Workflow 2: Weekly Order Planning (AI-Assisted)
```
Time: 15-20 minutes

1. Open AI Assistant
2. You: "What should I order this week? I want 4 weeks stock for products under $30, 2 weeks for products over $30"

3. AI: [Analyzes 487 products]
   "Found 23 products to order:

    Under $30 (4 weeks stock): 15 products, $4,200
    Over $30 (2 weeks stock): 8 products, $6,800

    Total: $11,000 for 23 products

    By supplier:
    - Bandai: $5,400
    - Tamiya: $3,200
    - Vallejo: $2,400

    Want to see details?"

4. You: "Show Bandai details"

5. AI: [Shows 8 Bandai products with quantities and costs]

6. You: "Looks good, add all to cart"

7. Open Order Builder
8. Review Bandai order (grouped)
9. Export PDF purchase order
10. Email to supplier
```

#### Workflow 3: Brand Performance Analysis
```
Time: 10 minutes

1. Open AI Assistant
2. You: "Compare inventory health across all brands"

3. AI: [Uses analyzeByBrand for each brand]
   "Brand Comparison (30-day analysis):

    🟢 HEALTHY:
    - Vallejo: 100% stock health, 0 low stock
    - Citadel: 95% stock health, 2 low stock

    🟡 NEEDS ATTENTION:
    - Tamiya: 75% stock health, 12 low stock
    - Bandai: 68% stock health, 15 low stock

    🔴 CRITICAL:
    - Gundam: 45% stock health, 28 low stock

    Recommendation: Prioritize Gundam restock ($8,400 needed)"

4. You: "Show Gundam products by urgency"

5. AI: [Lists Gundam products, critical first]

6. You: "Create order for all critical Gundam items, 30 days stock"

7. AI: [Calculates, shows order plan]

8. You: "Add to cart"

9. Switch to Order Builder
10. Export & order
```

---

## Configuration

### Application Properties

**File:** `src/main/resources/application.yml`

```yaml
inventory-management:
  # Enable/disable the entire module
  enabled: ${INVENTORY_MANAGEMENT_ENABLED:true}

  # Stock alert thresholds (in days)
  thresholds:
    critical-stock-days: ${CRITICAL_STOCK_DAYS:3}
    warning-stock-days: ${WARNING_STOCK_DAYS:7}
    info-stock-days: ${INFO_STOCK_DAYS:14}

  # Safety stock calculation
  safety:
    safety-stock-days: ${SAFETY_STOCK_DAYS:7}
    safety-multiplier: ${SAFETY_MULTIPLIER:1.5}

  # Sales velocity configuration
  velocity:
    default-window-days: ${VELOCITY_WINDOW_DAYS:30}
    minimum-sales-threshold: ${MIN_SALES_THRESHOLD:5}
    trend-threshold-percentage: ${TREND_THRESHOLD:15.0}

  # AI-powered analysis
  ai:
    enabled: ${AI_ANALYSIS_ENABLED:true}
    model: ${INVENTORY_AI_MODEL:claude-3-7-sonnet-20250219}
    temperature: ${AI_TEMPERATURE:0.3}
    max-tokens: ${AI_MAX_TOKENS:2000}
    system-prompt-file: ${AI_SYSTEM_PROMPT:classpath:prompts/inventory-agent-prompt.txt}

  # Scheduled jobs (cron expressions)
  schedule:
    velocity-update-cron: ${VELOCITY_CRON:0 0 * * * *}        # Hourly
    low-stock-check-cron: ${LOW_STOCK_CRON:0 0 */6 * * *}    # Every 6 hours
    recommendations-cron: ${RECOMMENDATIONS_CRON:0 0 8 * * *} # Daily at 8 AM

  # Performance tuning
  performance:
    parallel-analysis-limit: ${PARALLEL_LIMIT:10}
    mcp-timeout-ms: ${MCP_TIMEOUT:10000}
    cache-ttl-seconds: ${CACHE_TTL:3600}

  # Order planning defaults
  order:
    default-target-days: ${DEFAULT_TARGET_DAYS:30}
    min-order-value: ${MIN_ORDER_VALUE:100}
    shipping-threshold: ${FREE_SHIPPING_THRESHOLD:500}
```

### Environment Variables (Railway)

Required:
```bash
# Core API
ANTHROPIC_API_KEY=sk-ant-xxx

# ERP Integration
CRS_MCP_URL=https://web-production-2534.up.railway.app/mcp
CRS_MCP_ENABLED=true

# Database
DATABASE_URL=postgresql://...
```

Optional (with defaults):
```bash
# Inventory Module
INVENTORY_MANAGEMENT_ENABLED=true
CRITICAL_STOCK_DAYS=3
WARNING_STOCK_DAYS=7
AI_ANALYSIS_ENABLED=true
DEFAULT_TARGET_DAYS=30
```

---

## Deployment

### Build Process

#### Backend (Maven)
```bash
cd /Users/np/shopify-data-api
mvn clean package -DskipTests
```

#### Frontend (npm)
```bash
cd /Users/np/shopify-data-api/frontend
npm install
npm run build
npm run build:deploy  # Copies to src/main/resources/static/
```

### Railway Deployment

1. **Commit Changes:**
```bash
git add .
git commit -m "Add Inventory Management Module with AI assistant"
git push origin main
```

2. **Railway Auto-Deploy:**
- Railway detects push
- Builds Spring Boot app
- Runs database migrations
- Deploys to: `https://shopify-data-api-production.up.railway.app`

3. **Verify Deployment:**
```bash
# Health check
curl https://shopify-data-api-production.up.railway.app/api/inventory-management/health

# Dashboard
curl https://shopify-data-api-production.up.railway.app/api/inventory-management/dashboard

# Frontend
open https://shopify-data-api-production.up.railway.app/inventory/dashboard
```

### Database Migrations

**Auto-run on startup via Flyway:**
- `V014__create_inventory_analysis_tables.sql` (already exists)
- `V015__add_brand_category_to_velocity.sql` (new - adds brand/category columns)

---

## Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests

```bash
# Test ERP integration
curl -X POST http://localhost:8080/api/inventory-management/analyze/GUN-001

# Test AI agent
curl -X POST http://localhost:8080/api/inventory-management/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Show low stock Gundam products"}'
```

### Frontend Testing

```bash
cd frontend
npm run dev  # Development server at localhost:5173
```

---

## Troubleshooting

### Common Issues

**Issue:** "No products found"
**Solution:**
1. Check MCP connection: `GET /api/inventory-management/health`
2. Verify `CRS_MCP_ENABLED=true`
3. Test MCP endpoint manually

**Issue:** "AI agent not responding"
**Solution:**
1. Check `ANTHROPIC_API_KEY` is set
2. Verify `AI_ANALYSIS_ENABLED=true`
3. Check logs for API errors

**Issue:** "Sales velocity showing 0"
**Solution:**
1. Ensure MCP returns sales history
2. Check `VELOCITY_WINDOW_DAYS` setting
3. Verify product has sales in window

---

## Performance Optimization

### Caching Strategy

- Dashboard metrics: 5 minutes
- Sales velocity: 1 hour
- Brand/category lists: 24 hours
- Product metadata: 1 hour

### Database Indexes

Already created in migration:
- `idx_velocity_sku` - Fast SKU lookup
- `idx_alerts_unresolved` - Filter unresolved alerts
- `idx_recommendations_pending` - Filter pending recommendations

### Scheduled Jobs

Run during off-peak hours:
- Velocity updates: Hourly (spread across hour)
- Low stock checks: Every 6 hours
- Recommendations: Once daily at 8 AM

---

## Future Enhancements

### Phase 3 (Future)
- Predictive analytics (machine learning)
- Automatic purchase order submission
- Supplier performance tracking
- Seasonal trend detection
- Mobile app
- Email/SMS alerts
- Integration with accounting systems
- Multi-location inventory
- Demand forecasting

---

## Support

### Documentation
- This file: `docs/INVENTORY_MANAGEMENT_COMPLETE.md`
- API Reference: See API section above
- Original module docs: `docs/INVENTORY_ANALYSIS_MODULE.md`

### Contact
- Internal support: Check logs at `/inventory/logs`
- Railway logs: Railway dashboard

---

**End of Documentation**

Last Updated: November 8, 2025
