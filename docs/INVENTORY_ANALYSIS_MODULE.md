# Inventory Analysis & Order Recommendation Module

**Version:** 1.0.0
**Created:** November 6, 2025
**Status:** In Development

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Data Flow](#data-flow)
4. [Components](#components)
5. [API Endpoints](#api-endpoints)
6. [Database Schema](#database-schema)
7. [Configuration](#configuration)
8. [MCP Integration](#mcp-integration)
9. [AI-Powered Analysis](#ai-powered-analysis)
10. [Usage Examples](#usage-examples)
11. [Deployment](#deployment)

---

## Overview

The Inventory Analysis & Order Recommendation Module is an intelligent system that analyzes inventory levels and sales patterns to generate automated ordering recommendations. It leverages:

- **MCP/ERP** as the primary data source (contains all Shopify sales data already synced)
- **Shopify GraphQL API** for cross-referencing product information
- **Claude AI** for intelligent analysis and recommendations
- **Automated scheduling** for continuous monitoring

### Key Features

✅ **Sales Velocity Calculation** - Analyzes historical sales from ERP
✅ **Low Stock Detection** - Identifies products at risk of stockout
✅ **Intelligent Reorder Points** - Calculates optimal reorder thresholds
✅ **AI-Powered Recommendations** - Claude analyzes patterns and generates insights
✅ **Supplier Lead Time Integration** - Accounts for supplier delivery times
✅ **Cost Optimization** - Considers product costs from ERP
✅ **Automated Monitoring** - Scheduled jobs run analysis automatically
✅ **Dashboard API** - Complete REST API for frontend integration

---

## Architecture

### System Design

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend Dashboard                        │
│   - Inventory Health Overview                                   │
│   - Low Stock Alerts (Critical/Warning)                         │
│   - Order Recommendations with AI Insights                      │
│   - Sales Velocity Charts & Trends                              │
└─────────────────────────────────────────────────────────────────┘
                              ↓ HTTP REST
┌─────────────────────────────────────────────────────────────────┐
│              InventoryAnalysisController                         │
│  GET  /api/inventory-analysis/dashboard                         │
│  GET  /api/inventory-analysis/low-stock                         │
│  GET  /api/inventory-analysis/recommendations                   │
│  POST /api/inventory-analysis/analyze/{sku}                     │
│  GET  /api/inventory-analysis/velocity/{sku}                    │
│  PUT  /api/inventory-analysis/recommendations/{id}/approve      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│              InventoryAnalysisService (Core Logic)               │
│  - Orchestrates analysis workflow                               │
│  - Combines data from multiple sources                          │
│  - Generates recommendations                                    │
│  - Manages alerts and notifications                             │
└─────────────────────────────────────────────────────────────────┘
        ↓                        ↓                        ↓
┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
│ERPInventoryService│   │SalesVelocity     │   │AnthropicService  │
│- Sales history   │   │Calculator        │   │- AI analysis     │
│- Stock levels    │   │- Velocity calc   │   │- Insights        │
│- Supplier info   │   │- Trend detection │   │- Recommendations │
│- Costs           │   │- Predictions     │   │                  │
└──────────────────┘   └──────────────────┘   └──────────────────┘
        ↓                                              ↓
┌──────────────────┐                          ┌──────────────────┐
│    MCPClient     │                          │   Claude API     │
│  (JSON-RPC 2.0)  │                          │  (Messages API)  │
└──────────────────┘                          └──────────────────┘
        ↓
┌──────────────────────────────────────────────────────────────┐
│              CRS ERP System (via MCP Server)                 │
│  - Complete sales history (synced from Shopify)              │
│  - Warehouse inventory levels                                │
│  - Supplier information & lead times                         │
│  - Product costs & pricing                                   │
│  - Reorder history                                           │
└──────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

1. **ERP as Primary Source**: ERP already syncs Shopify sales data, eliminating redundant API calls
2. **Shopify for Cross-Reference**: Use Shopify only to validate product info and get current online stock
3. **AI-Enhanced Analysis**: Claude provides intelligent insights beyond simple calculations
4. **Non-Invasive**: New module doesn't modify existing services or controllers
5. **Database Persistence**: All analysis results stored for historical tracking

---

## Data Flow

### 1. Scheduled Analysis Flow (Automated)

```
[Scheduled Job Triggers]
    ↓
[InventoryAnalysisService.analyzeAllProducts()]
    ↓
[For each product with inventory tracking:]
    ↓
[ERPInventoryService.getSalesHistory(sku, 30 days)]
    ↓
[SalesVelocityCalculator.calculate(salesData)]
    ↓
[ERPInventoryService.getInventoryLevel(sku)]
    ↓
[Calculate: daysUntilStockout = currentStock / dailyVelocity]
    ↓
[If daysUntilStockout < threshold:]
    ↓
[ERPInventoryService.getSupplierInfo(sku)]
    ↓
[Calculate reorder point and quantity]
    ↓
[AnthropicService.generateRecommendation(analysisData)]
    ↓
[Save OrderRecommendation to database]
    ↓
[Create StockAlert if critical]
    ↓
[Notify dashboard/users]
```

### 2. On-Demand Analysis Flow (User-Initiated)

```
[User requests: POST /api/inventory-analysis/analyze/GUNDAM-RX78]
    ↓
[InventoryAnalysisService.analyzeSingleProduct(sku)]
    ↓
[Fetch all data in parallel:]
    - ERP sales history (last 30, 60, 90 days)
    - ERP current inventory
    - ERP supplier info & lead time
    - ERP product cost
    - Shopify product info (cross-reference)
    ↓
[SalesVelocityCalculator.calculateDetailed()]
    - Daily average
    - Weekly average
    - Monthly average
    - Trend detection (increasing/stable/decreasing)
    - Seasonality analysis
    ↓
[Calculate metrics:]
    - Reorder point = (dailyVelocity × leadTime) + safetyStock
    - Order quantity = optimal economic order quantity
    - Days until stockout
    - Confidence score
    ↓
[Claude AI Analysis:]
    - Review all metrics
    - Identify patterns and anomalies
    - Generate reasoning and recommendations
    - Assess urgency level
    - Provide actionable insights
    ↓
[Return comprehensive InventoryAnalysisResult]
```

---

## Components

### 1. Data Models

#### `SalesVelocity.java`
```java
@Entity
@Table(name = "sales_velocity")
public class SalesVelocity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sku;
    private String productId; // Shopify product ID for reference

    // Velocity metrics
    private BigDecimal dailyAverage;
    private BigDecimal weeklyAverage;
    private BigDecimal monthlyAverage;

    // Trend analysis
    @Enumerated(EnumType.STRING)
    private TrendDirection trend; // INCREASING, DECREASING, STABLE

    private BigDecimal trendPercentage; // e.g., +15% or -8%

    // Calculation metadata
    private LocalDateTime lastCalculated;
    private Integer calculationPeriodDays; // e.g., 30, 60, 90
    private Integer dataSampleSize; // number of sales transactions analyzed
}
```

#### `OrderRecommendation.java`
```java
@Entity
@Table(name = "order_recommendations")
public class OrderRecommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sku;
    private String productTitle;

    // Stock information
    private Integer currentStock;
    private Integer erpStock; // From ERP system
    private Integer shopifyStock; // From Shopify (for cross-reference)

    // Recommendation details
    private Integer recommendedQuantity;
    private Integer reorderPoint;
    private Integer safetyStockLevel;

    // Supplier information
    private Integer leadTimeDays;
    private String supplierName;
    private BigDecimal costPerUnit;
    private BigDecimal totalCost; // recommendedQuantity × costPerUnit

    // Urgency and confidence
    @Enumerated(EnumType.STRING)
    private UrgencyLevel urgency; // CRITICAL, HIGH, MEDIUM, LOW

    private BigDecimal confidenceScore; // 0.0 to 1.0

    // AI-generated insights
    @Column(columnDefinition = "TEXT")
    private String aiReasoning; // Claude's analysis

    @Column(columnDefinition = "TEXT")
    private String recommendations; // Specific action items

    // Predictions
    private LocalDate estimatedStockoutDate;
    private Integer daysUntilStockout;

    // Status tracking
    @Enumerated(EnumType.STRING)
    private RecommendationStatus status; // PENDING, APPROVED, ORDERED, DISMISSED

    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private String approvedBy;
}
```

#### `StockAlert.java`
```java
@Entity
@Table(name = "stock_alerts")
public class StockAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sku;
    private String productTitle;

    // Stock levels
    private Integer currentStock;
    private Integer reorderPoint;

    // Alert details
    @Enumerated(EnumType.STRING)
    private AlertLevel alertLevel; // CRITICAL, WARNING, INFO

    private Integer daysUntilStockout;
    private LocalDate estimatedStockoutDate;

    @Column(columnDefinition = "TEXT")
    private String message; // Human-readable alert message

    // Tracking
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private Boolean resolved;

    // Link to recommendation
    private Long recommendationId;
}
```

#### `ERPInventoryData.java`
```java
@Data
@Builder
public class ERPInventoryData {
    private String sku;
    private Integer quantity;
    private String location;
    private String supplierName;
    private Integer leadTimeDays;
    private BigDecimal costPerUnit;
    private LocalDateTime lastUpdated;

    // Additional ERP fields
    private Integer reorderQuantity;
    private Integer minimumStockLevel;
    private String warehouseLocation;
}
```

#### `InventoryAnalysisResult.java`
```java
@Data
@Builder
public class InventoryAnalysisResult {
    private String sku;
    private String productTitle;

    // Current state
    private Integer shopifyStock;
    private Integer erpStock;
    private Integer totalAvailableStock;

    // Sales velocity
    private SalesVelocity velocity;

    // Supplier info
    private String supplierName;
    private Integer leadTimeDays;
    private BigDecimal costPerUnit;

    // Calculated metrics
    private Integer reorderPoint;
    private Integer recommendedOrderQuantity;
    private Integer daysUntilStockout;
    private LocalDate estimatedStockoutDate;

    // AI insights
    private String aiAnalysis;
    private String recommendations;
    private UrgencyLevel urgencyLevel;
    private BigDecimal confidenceScore;

    // Analysis metadata
    private LocalDateTime analyzedAt;
    private String analysisVersion;
}
```

### 2. Services

#### `ERPInventoryService.java`
Primary interface to MCP/ERP system for inventory data.

**Key Methods:**
```java
// Get sales history from ERP (already synced from Shopify)
Mono<List<SaleRecord>> getSalesHistory(String sku, int days);

// Get current inventory level from ERP warehouse
Mono<Integer> getInventoryLevel(String sku);

// Get supplier information
Mono<SupplierInfo> getSupplierInfo(String sku);

// Get lead time for product reordering
Mono<Integer> getLeadTimeDays(String sku);

// Get product cost from ERP
Mono<BigDecimal> getProductCost(String sku);

// Get comprehensive ERP data in one call
Mono<ERPInventoryData> getERPInventoryData(String sku);
```

**MCP Tool Calls:**
```java
// Example MCP tool call structure
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_sales_history",
    "arguments": {
      "sku": "GUNDAM-RX78",
      "days": 30
    }
  }
}
```

#### `SalesVelocityCalculator.java`
Calculates sales velocity and detects trends.

**Key Methods:**
```java
// Calculate velocity from sales records
SalesVelocity calculate(List<SaleRecord> salesRecords);

// Calculate detailed velocity with multiple time windows
SalesVelocity calculateDetailed(List<SaleRecord> salesRecords, int[] windowDays);

// Detect trend direction and strength
TrendAnalysis detectTrend(List<SaleRecord> salesRecords);

// Predict future sales based on velocity and trend
BigDecimal predictSales(SalesVelocity velocity, int days);
```

**Calculation Logic:**
```
Daily Velocity = Total Units Sold / Days in Period
Weekly Velocity = Daily Velocity × 7
Monthly Velocity = Daily Velocity × 30

Trend Detection:
- Compare last 7 days avg vs previous 7 days avg
- If difference > 15%: INCREASING or DECREASING
- If difference < 15%: STABLE

Weighted Average (recent sales weighted more):
- Last 7 days: weight 3x
- Days 8-14: weight 2x
- Days 15-30: weight 1x
```

#### `InventoryAnalysisService.java`
Core orchestration service for inventory analysis.

**Key Methods:**
```java
// Analyze a single product (comprehensive)
Mono<InventoryAnalysisResult> analyzeSingleProduct(String sku);

// Analyze all products (scheduled job)
Flux<InventoryAnalysisResult> analyzeAllProducts();

// Find products with low stock
Flux<StockAlert> findLowStockProducts(AlertLevel minimumLevel);

// Generate order recommendations
Flux<OrderRecommendation> generateRecommendations();

// Get dashboard summary data
Mono<InventoryDashboard> getDashboardData();

// Calculate optimal reorder point
Integer calculateReorderPoint(SalesVelocity velocity, int leadTimeDays, int safetyDays);

// Calculate optimal order quantity
Integer calculateOrderQuantity(SalesVelocity velocity, int currentStock, int reorderPoint);

// Generate AI-powered insights
Mono<String> generateAIInsights(InventoryAnalysisResult analysis);
```

### 3. Repositories

```java
public interface SalesVelocityRepository extends JpaRepository<SalesVelocity, Long> {
    Optional<SalesVelocity> findBySku(String sku);
    List<SalesVelocity> findByLastCalculatedAfter(LocalDateTime cutoff);
}

public interface OrderRecommendationRepository extends JpaRepository<OrderRecommendation, Long> {
    List<OrderRecommendation> findByStatus(RecommendationStatus status);
    List<OrderRecommendation> findByUrgency(UrgencyLevel urgency);
    Optional<OrderRecommendation> findTopBySkuOrderByCreatedAtDesc(String sku);
}

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {
    List<StockAlert> findByResolvedFalse();
    List<StockAlert> findByAlertLevel(AlertLevel level);
    List<StockAlert> findByCreatedAtAfter(LocalDateTime cutoff);
}
```

---

## API Endpoints

### Base Path: `/api/inventory-analysis`

#### 1. Dashboard Overview
```
GET /api/inventory-analysis/dashboard
```

**Response:**
```json
{
  "summary": {
    "totalProducts": 1250,
    "trackedProducts": 980,
    "lowStockCount": 23,
    "criticalStockCount": 5,
    "pendingRecommendations": 18
  },
  "inventoryHealth": {
    "healthy": 1180,
    "warning": 47,
    "critical": 23
  },
  "topCriticalAlerts": [
    {
      "sku": "GUNDAM-RX78",
      "productTitle": "RG RX-78-2 Gundam",
      "currentStock": 2,
      "daysUntilStockout": 3,
      "alertLevel": "CRITICAL"
    }
  ],
  "recentRecommendations": [...],
  "salesMetrics": {
    "totalSalesLast30Days": 1547,
    "averageDailySales": 51.5,
    "topSellingProducts": [...]
  }
}
```

#### 2. Low Stock Alerts
```
GET /api/inventory-analysis/low-stock?level=WARNING
```

**Query Parameters:**
- `level` (optional): `CRITICAL`, `WARNING`, `INFO` (default: `WARNING`)
- `limit` (optional): Max results (default: 50)

**Response:**
```json
[
  {
    "id": 123,
    "sku": "GUNDAM-RX78",
    "productTitle": "RG RX-78-2 Gundam",
    "currentStock": 5,
    "reorderPoint": 12,
    "daysUntilStockout": 6,
    "estimatedStockoutDate": "2025-11-12",
    "alertLevel": "WARNING",
    "message": "Stock below reorder point. Recommend ordering soon.",
    "createdAt": "2025-11-06T10:00:00Z"
  }
]
```

#### 3. Order Recommendations
```
GET /api/inventory-analysis/recommendations?status=PENDING
```

**Query Parameters:**
- `status` (optional): `PENDING`, `APPROVED`, `ORDERED`, `DISMISSED`
- `urgency` (optional): `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`

**Response:**
```json
[
  {
    "id": 1,
    "sku": "GUNDAM-RX78",
    "productTitle": "RG RX-78-2 Gundam",
    "currentStock": 5,
    "recommendedQuantity": 24,
    "reorderPoint": 12,
    "leadTimeDays": 14,
    "supplierName": "Bandai Imports",
    "costPerUnit": 28.50,
    "totalCost": 684.00,
    "velocity": {
      "dailyAverage": 0.85,
      "weeklyAverage": 6.0,
      "trend": "INCREASING",
      "trendPercentage": 15.2
    },
    "urgency": "HIGH",
    "confidenceScore": 0.92,
    "aiReasoning": "Sales velocity has increased 15% over the last 2 weeks, indicating growing demand. Current stock of 5 units will last approximately 6 days. With a 14-day supplier lead time, ordering now is critical to avoid stockout.",
    "recommendations": "1. Order 24 units immediately\n2. Consider increasing standing order quantity\n3. Monitor competitor pricing\n4. Verify supplier stock availability",
    "estimatedStockoutDate": "2025-11-12",
    "daysUntilStockout": 6,
    "status": "PENDING",
    "createdAt": "2025-11-06T08:00:00Z"
  }
]
```

#### 4. Analyze Single Product
```
POST /api/inventory-analysis/analyze/{sku}
```

**Path Parameters:**
- `sku`: Product SKU to analyze

**Query Parameters:**
- `velocityDays` (optional): Days to analyze (default: 30)
- `generateRecommendation` (optional): Create recommendation if needed (default: true)

**Response:**
```json
{
  "sku": "GUNDAM-RX78",
  "productTitle": "RG RX-78-2 Gundam",
  "shopifyStock": 5,
  "erpStock": 5,
  "totalAvailableStock": 5,
  "velocity": {
    "dailyAverage": 0.85,
    "weeklyAverage": 6.0,
    "monthlyAverage": 25.5,
    "trend": "INCREASING",
    "trendPercentage": 15.2
  },
  "supplierName": "Bandai Imports",
  "leadTimeDays": 14,
  "costPerUnit": 28.50,
  "reorderPoint": 12,
  "recommendedOrderQuantity": 24,
  "daysUntilStockout": 6,
  "estimatedStockoutDate": "2025-11-12",
  "urgencyLevel": "HIGH",
  "confidenceScore": 0.92,
  "aiAnalysis": "This product shows strong performance with accelerating sales...",
  "recommendations": "Immediate action required: order 24 units...",
  "analyzedAt": "2025-11-06T14:30:00Z"
}
```

#### 5. Get Sales Velocity
```
GET /api/inventory-analysis/velocity/{sku}?days=30
```

**Response:**
```json
{
  "sku": "GUNDAM-RX78",
  "dailyAverage": 0.85,
  "weeklyAverage": 6.0,
  "monthlyAverage": 25.5,
  "trend": "INCREASING",
  "trendPercentage": 15.2,
  "lastCalculated": "2025-11-06T14:00:00Z",
  "calculationPeriodDays": 30,
  "dataSampleSize": 47
}
```

#### 6. Approve Recommendation
```
PUT /api/inventory-analysis/recommendations/{id}/approve
```

**Request Body:**
```json
{
  "approvedBy": "john.doe@hearnshobbies.com",
  "notes": "Approved for immediate ordering"
}
```

**Response:**
```json
{
  "success": true,
  "recommendation": {...},
  "message": "Recommendation approved and ready for ordering"
}
```

#### 7. Dismiss Recommendation
```
DELETE /api/inventory-analysis/recommendations/{id}
```

**Request Body:**
```json
{
  "reason": "Product being discontinued",
  "dismissedBy": "john.doe@hearnshobbies.com"
}
```

---

## Database Schema

### Migration: `V004__create_inventory_analysis_tables.sql`

```sql
-- Sales Velocity Tracking
CREATE TABLE sales_velocity (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(255) NOT NULL UNIQUE,
    product_id VARCHAR(255),

    -- Velocity metrics
    daily_average DECIMAL(10,2) NOT NULL,
    weekly_average DECIMAL(10,2) NOT NULL,
    monthly_average DECIMAL(10,2) NOT NULL,

    -- Trend analysis
    trend VARCHAR(50) NOT NULL CHECK (trend IN ('INCREASING', 'DECREASING', 'STABLE')),
    trend_percentage DECIMAL(5,2),

    -- Metadata
    last_calculated TIMESTAMP NOT NULL DEFAULT NOW(),
    calculation_period_days INT NOT NULL,
    data_sample_size INT,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sales_velocity_sku ON sales_velocity(sku);
CREATE INDEX idx_sales_velocity_last_calculated ON sales_velocity(last_calculated);

-- Order Recommendations
CREATE TABLE order_recommendations (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(255) NOT NULL,
    product_title VARCHAR(500),

    -- Stock information
    current_stock INT NOT NULL,
    erp_stock INT,
    shopify_stock INT,

    -- Recommendation details
    recommended_quantity INT NOT NULL,
    reorder_point INT NOT NULL,
    safety_stock_level INT,

    -- Supplier information
    lead_time_days INT NOT NULL,
    supplier_name VARCHAR(255),
    cost_per_unit DECIMAL(10,2),
    total_cost DECIMAL(10,2),

    -- Urgency and confidence
    urgency VARCHAR(50) NOT NULL CHECK (urgency IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    confidence_score DECIMAL(3,2),

    -- AI insights
    ai_reasoning TEXT,
    recommendations TEXT,

    -- Predictions
    estimated_stockout_date DATE,
    days_until_stockout INT,

    -- Status tracking
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'ORDERED', 'DISMISSED')),

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    approved_at TIMESTAMP,
    approved_by VARCHAR(255),

    -- Link to velocity calculation
    velocity_id BIGINT REFERENCES sales_velocity(id)
);

CREATE INDEX idx_order_recommendations_sku ON order_recommendations(sku);
CREATE INDEX idx_order_recommendations_status ON order_recommendations(status);
CREATE INDEX idx_order_recommendations_urgency ON order_recommendations(urgency);
CREATE INDEX idx_order_recommendations_created_at ON order_recommendations(created_at);

-- Stock Alerts
CREATE TABLE stock_alerts (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(255) NOT NULL,
    product_title VARCHAR(500),

    -- Stock levels
    current_stock INT NOT NULL,
    reorder_point INT NOT NULL,

    -- Alert details
    alert_level VARCHAR(50) NOT NULL CHECK (alert_level IN ('CRITICAL', 'WARNING', 'INFO')),
    days_until_stockout INT,
    estimated_stockout_date DATE,
    message TEXT,

    -- Tracking
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,

    -- Link to recommendation
    recommendation_id BIGINT REFERENCES order_recommendations(id)
);

CREATE INDEX idx_stock_alerts_sku ON stock_alerts(sku);
CREATE INDEX idx_stock_alerts_resolved ON stock_alerts(resolved);
CREATE INDEX idx_stock_alerts_alert_level ON stock_alerts(alert_level);
CREATE INDEX idx_stock_alerts_created_at ON stock_alerts(created_at);

-- Analysis History (optional - for tracking analysis runs)
CREATE TABLE analysis_history (
    id BIGSERIAL PRIMARY KEY,
    analysis_type VARCHAR(50) NOT NULL, -- 'FULL_SCAN', 'SINGLE_PRODUCT', 'SCHEDULED'
    products_analyzed INT,
    recommendations_generated INT,
    alerts_created INT,
    execution_time_ms BIGINT,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analysis_history_created_at ON analysis_history(created_at);
CREATE INDEX idx_analysis_history_type ON analysis_history(analysis_type);
```

---

## Configuration

### `application.yml` additions

```yaml
# Inventory Analysis Module Configuration
inventory-analysis:
  # Enable/disable the module
  enabled: ${INVENTORY_ANALYSIS_ENABLED:true}

  # Stock thresholds
  thresholds:
    # Alert when stock will last fewer than N days
    critical-stock-days: ${CRITICAL_STOCK_DAYS:3}
    warning-stock-days: ${WARNING_STOCK_DAYS:7}
    info-stock-days: ${INFO_STOCK_DAYS:14}

  # Safety stock calculation
  safety:
    # Additional days of stock to maintain as buffer
    safety-stock-days: ${SAFETY_STOCK_DAYS:7}
    # Multiplier for safety stock (higher = more conservative)
    safety-multiplier: ${SAFETY_MULTIPLIER:1.5}

  # Sales velocity configuration
  velocity:
    # Default window for velocity calculation (days)
    default-window-days: ${VELOCITY_WINDOW_DAYS:30}
    # Minimum sales for reliable calculation
    minimum-sales-threshold: ${MIN_SALES_THRESHOLD:5}
    # Trend detection threshold (percentage change)
    trend-threshold-percentage: ${TREND_THRESHOLD:15.0}

  # AI analysis
  ai:
    # Enable AI-powered insights
    enabled: ${AI_ANALYSIS_ENABLED:true}
    # Temperature for AI recommendations (0.0-1.0)
    temperature: ${AI_TEMPERATURE:0.3}
    # Max tokens for AI response
    max-tokens: ${AI_MAX_TOKENS:1000}

  # Scheduled jobs configuration
  schedule:
    # Update sales velocity hourly
    velocity-update-cron: ${VELOCITY_CRON:0 0 * * * *}
    # Check for low stock every 6 hours
    low-stock-check-cron: ${LOW_STOCK_CRON:0 0 */6 * * *}
    # Generate recommendations daily at 8 AM
    recommendations-cron: ${RECOMMENDATIONS_CRON:0 0 8 * * *}

  # Performance tuning
  performance:
    # Max products to analyze in parallel
    parallel-analysis-limit: ${PARALLEL_LIMIT:10}
    # Timeout for MCP calls (milliseconds)
    mcp-timeout-ms: ${MCP_TIMEOUT:10000}
    # Cache TTL for analysis results (seconds)
    cache-ttl-seconds: ${CACHE_TTL:3600}
```

### Environment Variables

```bash
# Enable inventory analysis module
INVENTORY_ANALYSIS_ENABLED=true

# Stock alert thresholds (days)
CRITICAL_STOCK_DAYS=3
WARNING_STOCK_DAYS=7
INFO_STOCK_DAYS=14

# Safety stock settings
SAFETY_STOCK_DAYS=7
SAFETY_MULTIPLIER=1.5

# Sales velocity settings
VELOCITY_WINDOW_DAYS=30
MIN_SALES_THRESHOLD=5
TREND_THRESHOLD=15.0

# AI settings
AI_ANALYSIS_ENABLED=true
AI_TEMPERATURE=0.3
AI_MAX_TOKENS=1000

# Scheduled jobs (cron format)
VELOCITY_CRON="0 0 * * * *"
LOW_STOCK_CRON="0 0 */6 * * *"
RECOMMENDATIONS_CRON="0 0 8 * * *"

# Performance
PARALLEL_LIMIT=10
MCP_TIMEOUT=10000
CACHE_TTL=3600
```

---

## MCP Integration

### Required MCP Tools

The module expects the following MCP tools to be available:

#### 1. `get_sales_history`
Get historical sales data for a SKU.

**Arguments:**
```json
{
  "sku": "GUNDAM-RX78",
  "days": 30,
  "includeDetails": true
}
```

**Expected Response:**
```json
{
  "sku": "GUNDAM-RX78",
  "sales": [
    {
      "date": "2025-11-05",
      "quantity": 2,
      "orderId": "12345",
      "amount": 57.00
    },
    {
      "date": "2025-11-03",
      "quantity": 1,
      "orderId": "12340",
      "amount": 28.50
    }
  ],
  "summary": {
    "totalQuantity": 25,
    "totalAmount": 712.50,
    "periodDays": 30
  }
}
```

#### 2. `get_inventory_level`
Get current inventory level from ERP warehouse.

**Arguments:**
```json
{
  "sku": "GUNDAM-RX78",
  "location": "MAIN_WAREHOUSE"
}
```

**Expected Response:**
```json
{
  "sku": "GUNDAM-RX78",
  "quantity": 5,
  "location": "MAIN_WAREHOUSE",
  "lastUpdated": "2025-11-06T10:00:00Z",
  "reserved": 0,
  "available": 5
}
```

#### 3. `get_supplier_info`
Get supplier information and lead times.

**Arguments:**
```json
{
  "sku": "GUNDAM-RX78"
}
```

**Expected Response:**
```json
{
  "sku": "GUNDAM-RX78",
  "supplierName": "Bandai Imports",
  "supplierCode": "BND-001",
  "leadTimeDays": 14,
  "minimumOrderQuantity": 12,
  "orderMultiple": 6,
  "lastOrderDate": "2025-10-15",
  "averageLeadTime": 15.5
}
```

#### 4. `get_product_cost`
Get product cost information.

**Arguments:**
```json
{
  "sku": "GUNDAM-RX78"
}
```

**Expected Response:**
```json
{
  "sku": "GUNDAM-RX78",
  "costPerUnit": 28.50,
  "currency": "USD",
  "lastUpdated": "2025-10-01",
  "volumeDiscounts": [
    {
      "quantity": 24,
      "costPerUnit": 27.00
    },
    {
      "quantity": 48,
      "costPerUnit": 25.50
    }
  ]
}
```

### Error Handling

If MCP tools are unavailable or return errors:

1. **Graceful Degradation**: Use Shopify data only
2. **Logging**: Log MCP errors for debugging
3. **User Notification**: Indicate reduced functionality
4. **Fallback Values**: Use reasonable defaults (e.g., 14 days lead time)

---

## AI-Powered Analysis

### Claude AI Integration

The module uses Claude AI to provide intelligent insights beyond simple calculations.

#### Analysis Prompt Template

```
You are an inventory management expert for Hearn's Hobbies, a specialty hobby store.

Analyze this product's inventory situation and provide recommendations:

PRODUCT INFORMATION:
- SKU: {sku}
- Title: {productTitle}
- Category: {category}

CURRENT STOCK LEVELS:
- Shopify Online Store: {shopifyStock} units
- ERP Warehouse: {erpStock} units
- Total Available: {totalStock} units

SALES VELOCITY (Last {velocityDays} days):
- Daily Average: {dailyVelocity} units/day
- Weekly Average: {weeklyVelocity} units/week
- Monthly Average: {monthlyVelocity} units/month
- Trend: {trend} ({trendPercentage}% change)
- Sample Size: {sampleSize} transactions

SUPPLIER INFORMATION:
- Supplier: {supplierName}
- Lead Time: {leadTimeDays} days
- Cost Per Unit: ${costPerUnit}
- Minimum Order Quantity: {moq} units

CALCULATED METRICS:
- Reorder Point: {reorderPoint} units
- Days Until Stockout: {daysUntilStockout} days
- Estimated Stockout Date: {stockoutDate}

CONTEXT:
- Safety Stock Target: {safetyStockDays} days of inventory
- Current Stock Below Reorder Point: {belowReorderPoint}

TASK:
Provide a comprehensive analysis including:

1. SITUATION ASSESSMENT
   - Is this an urgent situation? Why or why not?
   - What are the key risk factors?

2. RECOMMENDATION
   - How many units should be ordered?
   - Why this specific quantity?
   - When should the order be placed?

3. REASONING
   - What patterns do you observe in the sales data?
   - How does the trend affect the recommendation?
   - Are there any seasonality considerations?

4. RISK ANALYSIS
   - What happens if we don't order now?
   - What happens if we order too much?
   - What's the confidence level in this recommendation?

5. COST OPTIMIZATION
   - Is this the most cost-effective order quantity?
   - Should we consider volume discounts?
   - What's the total investment required?

6. ACTION ITEMS
   - Specific, actionable steps to take
   - Priority level (Critical/High/Medium/Low)
   - Timeline for action

Be specific, data-driven, and practical in your recommendations.
```

#### AI Response Processing

Claude's response is parsed for:
- **Urgency Level**: Critical, High, Medium, Low
- **Recommended Quantity**: Extracted number
- **Confidence Score**: 0.0 to 1.0
- **Reasoning**: Full explanation
- **Action Items**: Bullet-pointed list

---

## Usage Examples

### Example 1: Manual Product Analysis

```bash
# Analyze a specific product
curl -X POST http://localhost:8080/api/inventory-analysis/analyze/GUNDAM-RX78 \
  -H "Content-Type: application/json"

# Response includes:
# - Current stock levels
# - Sales velocity
# - Reorder recommendation
# - AI-generated insights
```

### Example 2: Check Low Stock Alerts

```bash
# Get all critical alerts
curl http://localhost:8080/api/inventory-analysis/low-stock?level=CRITICAL

# Returns products that need immediate attention
```

### Example 3: Review Pending Recommendations

```bash
# Get pending order recommendations
curl http://localhost:8080/api/inventory-analysis/recommendations?status=PENDING

# Review AI-generated recommendations and approve
```

### Example 4: Approve Recommendation

```bash
# Approve a recommendation for ordering
curl -X PUT http://localhost:8080/api/inventory-analysis/recommendations/123/approve \
  -H "Content-Type: application/json" \
  -d '{
    "approvedBy": "inventory@hearnshobbies.com",
    "notes": "Approved for immediate purchase order"
  }'
```

### Example 5: Dashboard Overview

```bash
# Get complete dashboard data
curl http://localhost:8080/api/inventory-analysis/dashboard

# Returns:
# - Summary statistics
# - Critical alerts
# - Recent recommendations
# - Inventory health metrics
```

---

## Deployment

### Railway Deployment

1. **Environment Variables**: Add all required environment variables to Railway
2. **Database Migration**: Flyway will automatically run `V004__create_inventory_analysis_tables.sql`
3. **MCP Server**: Ensure CRS MCP server is accessible
4. **Scheduled Jobs**: Verify cron expressions are correct for your timezone

### Health Check

```bash
# Verify module is loaded
curl http://localhost:8080/api/health

# Should include inventory-analysis in response
```

### Monitoring

Monitor these metrics:
- Analysis execution time
- MCP call success rate
- AI API usage and costs
- Recommendation accuracy over time
- Alert response times

---

## Future Enhancements

### Planned Features

- 📊 **Advanced Analytics Dashboard** (Phase 2)
- 🔔 **Email/SMS Alerts** for critical stock situations
- 📈 **Predictive Analytics** with ML models
- 🔄 **Automatic Purchase Order Generation**
- 📦 **Multi-Location Inventory** support
- 🎯 **Category-Level Analysis** (e.g., all Gundam kits)
- 💰 **Profit Optimization** recommendations
- 📱 **Mobile App Integration**
- 🤖 **Chatbot Integration** ("What should I order today?")

---

## Troubleshooting

### Common Issues

**Issue: No recommendations generated**
- Check MCP server connectivity
- Verify sales data is available in ERP
- Check minimum sales threshold configuration

**Issue: Inaccurate velocity calculations**
- Ensure sufficient sales history (30+ days recommended)
- Verify ERP data sync is working
- Check for data quality issues

**Issue: AI insights not generating**
- Verify Anthropic API key is configured
- Check API quota/rate limits
- Review prompt template configuration

---

## Support & Contributing

For questions or issues with the Inventory Analysis Module:

1. Check this documentation
2. Review logs in Railway dashboard
3. Contact development team

---

**Document Version:** 1.0.0
**Last Updated:** November 6, 2025
**Maintained By:** Development Team
