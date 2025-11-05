# Analytics and Monitoring
## Tracking Performance and User Behavior

---

## Overview

This document covers analytics and monitoring for the AI-enhanced search system:

1. Key Performance Indicators (KPIs)
2. Usage metrics tracking
3. Monitoring dashboards
4. Conversion tracking
5. Error monitoring
6. Performance optimization

---

## Key Performance Indicators (KPIs)

### Technical Metrics

| Metric | Target | Critical Threshold |
|--------|--------|-------------------|
| API Response Time (p95) | < 5 seconds | > 10 seconds |
| API Response Time (p50) | < 3 seconds | > 5 seconds |
| Error Rate | < 0.5% | > 2% |
| Uptime | > 99.9% | < 99% |
| Database Query Time | < 500ms | > 2 seconds |
| Claude API Success Rate | > 98% | < 95% |

### Business Metrics

| Metric | Goal | How to Measure |
|--------|------|----------------|
| AI Chat Sessions/Day | 50+ | Count unique conversation IDs |
| Messages per Session | 2-5 | Average messages per conversation |
| Product Clicks from AI | 30%+ | Track clicks on AI-recommended products |
| AI vs Traditional Search | 20/80 | Compare usage of both search types |
| Customer Satisfaction | 4+ / 5 | Post-chat survey (future) |
| Conversion Rate | Track | Compare AI vs traditional search conversions |

---

## Usage Metrics Tracking

### Database Schema for Analytics

**Migration File:** `V007__create_analytics_tables.sql`

```sql
-- Chat session tracking
CREATE TABLE chat_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    shop_domain VARCHAR(255) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP,
    message_count INTEGER DEFAULT 0,
    products_shown INTEGER DEFAULT 0,
    products_clicked INTEGER DEFAULT 0,
    user_agent TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Individual messages
CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,  -- 'user' or 'assistant'
    content TEXT NOT NULL,
    products_json JSONB,  -- Products shown in this message
    response_time_ms INTEGER,  -- Claude API response time
    tokens_used INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES chat_sessions(session_id)
);

-- Product interactions
CREATE TABLE product_interactions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    product_title VARCHAR(500),
    interaction_type VARCHAR(50) NOT NULL,  -- 'shown', 'clicked', 'added_to_cart'
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES chat_sessions(session_id)
);

-- Daily aggregated metrics
CREATE TABLE daily_metrics (
    id BIGSERIAL PRIMARY KEY,
    shop_domain VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    total_sessions INTEGER DEFAULT 0,
    total_messages INTEGER DEFAULT 0,
    total_products_shown INTEGER DEFAULT 0,
    total_products_clicked INTEGER DEFAULT 0,
    avg_messages_per_session DECIMAL(5,2),
    avg_response_time_ms INTEGER,
    error_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(shop_domain, date)
);

-- Indexes for performance
CREATE INDEX idx_chat_sessions_shop ON chat_sessions(shop_domain);
CREATE INDEX idx_chat_sessions_started ON chat_sessions(started_at);
CREATE INDEX idx_chat_messages_session ON chat_messages(session_id);
CREATE INDEX idx_product_interactions_session ON product_interactions(session_id);
CREATE INDEX idx_daily_metrics_shop_date ON daily_metrics(shop_domain, date);
```

---

## Tracking Implementation

### Backend Service

**File:** `/src/main/java/com/shopify/api/service/AnalyticsService.java`

```java
package com.shopify.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for tracking analytics and usage metrics
 */
@Service
public class AnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Create new chat session
     */
    @Async
    @Transactional
    public String createSession(String shopDomain, String userAgent, String ipAddress) {
        String sessionId = UUID.randomUUID().toString();

        String sql = """
            INSERT INTO chat_sessions (session_id, shop_domain, user_agent, ip_address)
            VALUES (?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql, sessionId, shopDomain, userAgent, ipAddress);

        logger.info("Created chat session: {}", sessionId);
        return sessionId;
    }

    /**
     * Track chat message
     */
    @Async
    @Transactional
    public void trackMessage(
            String sessionId,
            String role,
            String content,
            List<String> productIds,
            int responseTimeMs,
            int tokensUsed) {

        String sql = """
            INSERT INTO chat_messages (session_id, role, content, products_json, response_time_ms, tokens_used)
            VALUES (?, ?, ?, ?::jsonb, ?, ?)
            """;

        String productsJson = productIds != null
            ? "{\"product_ids\": " + new ObjectMapper().writeValueAsString(productIds) + "}"
            : null;

        jdbcTemplate.update(sql, sessionId, role, content, productsJson, responseTimeMs, tokensUsed);

        // Update session message count
        jdbcTemplate.update(
            "UPDATE chat_sessions SET message_count = message_count + 1 WHERE session_id = ?",
            sessionId
        );
    }

    /**
     * Track product interaction
     */
    @Async
    @Transactional
    public void trackProductInteraction(
            String sessionId,
            String productId,
            String productTitle,
            String interactionType) {

        String sql = """
            INSERT INTO product_interactions (session_id, product_id, product_title, interaction_type)
            VALUES (?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql, sessionId, productId, productTitle, interactionType);

        logger.debug("Tracked product interaction: {} - {}", interactionType, productId);
    }

    /**
     * End chat session
     */
    @Async
    @Transactional
    public void endSession(String sessionId) {
        String sql = "UPDATE chat_sessions SET ended_at = ? WHERE session_id = ?";
        jdbcTemplate.update(sql, LocalDateTime.now(), sessionId);
    }

    /**
     * Aggregate daily metrics (run via scheduled job)
     */
    @Transactional
    public void aggregateDailyMetrics(String shopDomain, LocalDate date) {
        String sql = """
            INSERT INTO daily_metrics (
                shop_domain, date, total_sessions, total_messages,
                total_products_shown, total_products_clicked,
                avg_messages_per_session, avg_response_time_ms, error_count
            )
            SELECT
                shop_domain,
                DATE(started_at) as date,
                COUNT(DISTINCT session_id) as total_sessions,
                SUM(message_count) as total_messages,
                SUM(products_shown) as total_products_shown,
                SUM(products_clicked) as total_products_clicked,
                AVG(message_count) as avg_messages_per_session,
                (SELECT AVG(response_time_ms) FROM chat_messages WHERE DATE(created_at) = ?) as avg_response_time_ms,
                0 as error_count
            FROM chat_sessions
            WHERE shop_domain = ? AND DATE(started_at) = ?
            GROUP BY shop_domain, DATE(started_at)
            ON CONFLICT (shop_domain, date)
            DO UPDATE SET
                total_sessions = EXCLUDED.total_sessions,
                total_messages = EXCLUDED.total_messages,
                avg_messages_per_session = EXCLUDED.avg_messages_per_session,
                avg_response_time_ms = EXCLUDED.avg_response_time_ms
            """;

        jdbcTemplate.update(sql, date, shopDomain, date);
        logger.info("Aggregated daily metrics for {} on {}", shopDomain, date);
    }
}
```

### Frontend Tracking

**File:** `/extensions/search-enhancer/assets/search-enhancer.js`

```javascript
// Add to widget JavaScript

let sessionId = null

/**
 * Initialize session
 */
async function initSession() {
  try {
    const response = await fetch(`${CONFIG.apiEndpoint}/api/analytics/session`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        shop: CONFIG.shopDomain,
        userAgent: navigator.userAgent
      })
    })

    const data = await response.json()
    sessionId = data.sessionId
    console.log('[HearnsAI] Session ID:', sessionId)

  } catch (error) {
    console.error('[HearnsAI] Failed to initialize session:', error)
  }
}

/**
 * Track product click
 */
function trackProductClick(product) {
  if (!sessionId) return

  fetch(`${CONFIG.apiEndpoint}/api/analytics/product-click`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      sessionId: sessionId,
      productId: product.id,
      productTitle: product.title
    })
  }).catch(error => console.error('[HearnsAI] Track click failed:', error))
}

// Call initSession when modal opens
function openChatModal() {
  if (!sessionId) {
    initSession()
  }
  // ... rest of modal logic
}

// Track product clicks
function renderProductCards(products) {
  return products.map(product => `
    <a href="${product.url}"
       onclick="trackProductClick(${JSON.stringify(product)})">
      <!-- Product card HTML -->
    </a>
  `).join('')
}
```

---

## Analytics API Endpoints

### Create Session

```http
POST /api/analytics/session
Content-Type: application/json

{
  "shop": "hearnshobbies.myshopify.com",
  "userAgent": "Mozilla/5.0..."
}

Response:
{
  "sessionId": "uuid-here",
  "timestamp": "2025-10-30T15:30:00Z"
}
```

### Track Product Click

```http
POST /api/analytics/product-click
Content-Type: application/json

{
  "sessionId": "uuid-here",
  "productId": "gid://shopify/Product/123",
  "productTitle": "RG RX-78-2 Gundam"
}

Response:
{
  "success": true
}
```

### Get Daily Metrics

```http
GET /api/analytics/metrics?shop=hearnshobbies.myshopify.com&date=2025-10-30

Response:
{
  "date": "2025-10-30",
  "totalSessions": 42,
  "totalMessages": 156,
  "avgMessagesPerSession": 3.7,
  "totalProductsShown": 84,
  "totalProductsClicked": 27,
  "clickThroughRate": 0.32,
  "avgResponseTime": 3240
}
```

---

## Monitoring Dashboards

### Railway Metrics

**Built-in metrics (Railway Dashboard):**
- CPU usage (%)
- Memory usage (MB)
- Network I/O
- HTTP requests/sec
- Response time (p50, p95, p99)

**Set up alerts:**
1. Railway Dashboard → Project → Observability
2. Click "Add Alert"
3. Configure:
   - CPU > 80% for 5 minutes
   - Memory > 90%
   - Response time p95 > 10 seconds

### Custom Metrics Dashboard

**Option 1: Grafana + PostgreSQL**

```yaml
# docker-compose.yml (for local development)
version: '3.8'
services:
  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./grafana/datasources:/etc/grafana/provisioning/datasources
```

**Grafana Dashboard JSON:**
```json
{
  "dashboard": {
    "title": "AI Search Analytics",
    "panels": [
      {
        "title": "Daily Chat Sessions",
        "type": "graph",
        "targets": [
          {
            "rawSql": "SELECT date, total_sessions FROM daily_metrics WHERE shop_domain = 'hearnshobbies.myshopify.com' ORDER BY date"
          }
        ]
      },
      {
        "title": "Average Response Time",
        "type": "graph",
        "targets": [
          {
            "rawSql": "SELECT date, avg_response_time_ms FROM daily_metrics WHERE shop_domain = 'hearnshobbies.myshopify.com' ORDER BY date"
          }
        ]
      }
    ]
  }
}
```

**Option 2: Simple Admin Dashboard (React)**

```jsx
// /frontend/src/pages/Analytics.jsx
import { useState, useEffect } from 'react'
import api from '../services/api'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend } from 'recharts'

function Analytics() {
  const [metrics, setMetrics] = useState([])

  useEffect(() => {
    loadMetrics()
  }, [])

  const loadMetrics = async () => {
    const response = await api.get('/api/analytics/metrics', {
      params: { shop: 'hearnshobbies.myshopify.com', days: 30 }
    })
    setMetrics(response.data)
  }

  return (
    <div className="space-y-6">
      <h1 className="text-3xl font-bold">AI Search Analytics</h1>

      {/* Key Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <MetricCard
          title="Chat Sessions Today"
          value={metrics.todaySessions}
          change="+12%"
        />
        <MetricCard
          title="Avg Response Time"
          value={`${metrics.avgResponseTime}ms`}
          change="-8%"
        />
        <MetricCard
          title="Product Click Rate"
          value={`${(metrics.clickRate * 100).toFixed(1)}%`}
          change="+5%"
        />
        <MetricCard
          title="Active Users"
          value={metrics.activeUsers}
          change="+20%"
        />
      </div>

      {/* Charts */}
      <div className="card">
        <h2 className="text-xl font-semibold mb-4">Daily Sessions</h2>
        <LineChart width={800} height={300} data={metrics.dailyData}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="date" />
          <YAxis />
          <Tooltip />
          <Legend />
          <Line type="monotone" dataKey="sessions" stroke="#8884d8" />
        </LineChart>
      </div>
    </div>
  )
}

function MetricCard({ title, value, change }) {
  const isPositive = change.startsWith('+')

  return (
    <div className="card">
      <h3 className="text-sm text-gray-600 mb-1">{title}</h3>
      <div className="flex items-baseline gap-2">
        <span className="text-3xl font-bold">{value}</span>
        <span className={`text-sm ${isPositive ? 'text-green-600' : 'text-red-600'}`}>
          {change}
        </span>
      </div>
    </div>
  )
}

export default Analytics
```

---

## Scheduled Jobs

### Daily Metrics Aggregation

**File:** `/src/main/java/com/shopify/api/scheduler/AnalyticsScheduler.java`

```java
package com.shopify.api.scheduler;

import com.shopify.api.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Scheduled jobs for analytics aggregation
 */
@Component
public class AnalyticsScheduler {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsScheduler.class);

    private final AnalyticsService analyticsService;

    @Autowired
    public AnalyticsScheduler(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Aggregate daily metrics at 1 AM every day
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void aggregateDailyMetrics() {
        logger.info("Running daily metrics aggregation...");

        LocalDate yesterday = LocalDate.now().minusDays(1);

        try {
            // Get all active shops
            List<String> shops = getActiveShops();

            for (String shop : shops) {
                analyticsService.aggregateDailyMetrics(shop, yesterday);
            }

            logger.info("Daily metrics aggregation completed for {} shops", shops.size());

        } catch (Exception e) {
            logger.error("Error aggregating daily metrics: {}", e.getMessage(), e);
        }
    }

    /**
     * Clean up old sessions (> 90 days)
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldSessions() {
        logger.info("Cleaning up old chat sessions...");

        // Delete sessions older than 90 days
        analyticsService.deleteOldSessions(90);
    }
}
```

### Enable Scheduling

```java
// Application.java
@SpringBootApplication
@EnableScheduling  // Add this annotation
public class ShopifyDataApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopifyDataApiApplication.class, args);
    }
}
```

---

## Performance Monitoring

### Response Time Tracking

```java
// Add interceptor to track response times
@Component
public class PerformanceInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("startTime", System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        long startTime = (Long) request.getAttribute("startTime");
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Log slow requests (> 5 seconds)
        if (duration > 5000) {
            logger.warn("Slow request: {} {} took {}ms", request.getMethod(), request.getRequestURI(), duration);
        }

        // Track in metrics
        metricsService.recordResponseTime(request.getRequestURI(), duration);
    }
}
```

---

## Error Monitoring

### Error Logging Table

```sql
CREATE TABLE error_logs (
    id BIGSERIAL PRIMARY KEY,
    shop_domain VARCHAR(255),
    error_type VARCHAR(100) NOT NULL,
    error_message TEXT,
    stack_trace TEXT,
    request_path VARCHAR(500),
    request_method VARCHAR(10),
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_error_logs_shop ON error_logs(shop_domain);
CREATE INDEX idx_error_logs_type ON error_logs(error_type);
CREATE INDEX idx_error_logs_created ON error_logs(created_at);
```

### Error Handler

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    private ErrorLoggingService errorLoggingService;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(
            Exception e,
            HttpServletRequest request) {

        // Log error to database
        errorLoggingService.logError(
            extractShopDomain(request),
            e.getClass().getSimpleName(),
            e.getMessage(),
            getStackTrace(e),
            request.getRequestURI(),
            request.getMethod()
        );

        // Return error response
        Map<String, Object> error = new HashMap<>();
        error.put("error", "Internal server error");
        error.put("message", e.getMessage());
        error.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

---

## Reporting

### Weekly Summary Email (Future Enhancement)

```java
@Scheduled(cron = "0 0 9 * * MON")  // Every Monday at 9 AM
public void sendWeeklySummary() {
    LocalDate endDate = LocalDate.now().minusDays(1);
    LocalDate startDate = endDate.minusDays(7);

    WeeklySummary summary = analyticsService.generateWeeklySummary(
        "hearnshobbies.myshopify.com",
        startDate,
        endDate
    );

    emailService.sendWeeklySummary(summary);
}
```

**Email Template:**
```
Subject: AI Search Weekly Summary - October 23-29, 2025

Hi Team,

Here's your AI Search performance summary for last week:

📊 Usage Stats:
- Total Sessions: 342 (+15% from previous week)
- Total Messages: 1,268 (+12%)
- Avg Messages/Session: 3.7

🎯 Engagement:
- Products Shown: 684
- Products Clicked: 219 (32% click-through rate)
- Top Searched Terms: "gundam", "paint", "rc car"

⚡ Performance:
- Avg Response Time: 3.2 seconds
- Error Rate: 0.3%
- Uptime: 99.98%

🔝 Popular Products:
1. RG RX-78-2 Gundam (42 views)
2. Tamiya Paint Set (38 views)
3. RC Drift Car (31 views)

Keep up the great work!
```

---

## Related Documentation

- **01-ARCHITECTURE.md** - System architecture
- **09-PHASE6-DEPLOYMENT.md** - Deployment and monitoring setup
- **10-CONFIGURATION.md** - Configuration reference

---

*Last Updated: 2025-10-30*
*Documentation Complete*
