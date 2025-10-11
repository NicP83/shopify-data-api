# Current Project Status

**Last Updated:** 2025-10-11
**Version:** WORKING2
**Status:** Deployed to Railway - All endpoints working + Product URLs

## Quick Summary

The Shopify Data API is a Spring Boot application that provides REST API endpoints for accessing Shopify store data. Fully deployed to production on Railway with all 13 endpoints working plus product URL and cart permalink functionality.

**Store Connected:** hearnshobbies.myshopify.com
**API Version:** Shopify Admin API 2025-01
**Production URL:** https://shopify-data-api-production.up.railway.app
**GitHub Repo:** https://github.com/NicP83/shopify-data-api

## What's Working ✅

### Fully Functional Endpoints

| Endpoint | Status | Description |
|----------|--------|-------------|
| `GET /api/health` | ✅ Working | Health check |
| `GET /api/status` | ✅ Working | System status with Shopify connection test |
| `GET /api/products` | ✅ Working | List products (includes `onlineStoreUrl`) |
| `GET /api/products/{id}` | ✅ Working | Get single product (includes `onlineStoreUrl`) |
| `GET /api/products/search?q=...` | ✅ Working | Search products (includes `onlineStoreUrl`) |
| `GET /api/orders` | ✅ Working | List orders with pagination |
| `GET /api/orders/{id}` | ✅ Working | Get single order details |
| `GET /api/orders/search?q=...` | ✅ Working | Search orders |
| `GET /api/customers` | ✅ Working | List customers (fixed for API 2025-01) |
| `GET /api/customers/{id}` | ✅ Working | Get customer details (fixed for API 2025-01) |
| `GET /api/customers/search?q=...` | ✅ Working | Search customers (fixed for API 2025-01) |
| `GET /api/inventory` | ✅ Working | List inventory (fixed for API 2025-01) |
| `GET /api/inventory/locations` | ✅ Working | Get inventory locations |

### Test Commands (Known to Work)

```bash
# Health check
curl http://localhost:8080/api/health
# Response: {"success":true,"message":"Success","data":{"status":"UP",...}}

# System status
curl http://localhost:8080/api/status
# Response: {"success":true,...,"shopify_connected":true}

# Get 5 products
curl "http://localhost:8080/api/products?first=5"
# Returns: Real Gundam products from hearnshobbies store

# Get specific product
curl "http://localhost:8080/api/products/9799176044839"
# Returns: Full product details

# Get 5 orders
curl "http://localhost:8080/api/orders?first=5"
# Returns: Real orders with customer data

# Search products
curl "http://localhost:8080/api/products/search?q=title:Gundam&first=5"
# Returns: Products matching search
```

### Real Data Examples

**Products Retrieved:**
- BANDAI MG 1/100 Gundam AGE-1 Normal
- Various Gundam model kits with pricing, variants, images
- Full inventory levels per variant

**Orders Retrieved:**
- Order HHW1001 from Chad Rozario
- Complete order details including line items, shipping address
- Financial status: PAID, Fulfillment status: FULFILLED

## New Features in WORKING2 ✨

### Product URLs and Cart Permalinks

**Feature:** Enhanced product endpoints to include `onlineStoreUrl` field and cart permalink generation.

**Files Modified:**
- `ProductService.java` - Added `onlineStoreUrl` to all product queries
- `ShopifyConfig.java` - Added URL construction utility methods

**Utility Methods Added:**
```java
// Construct product page URL from handle
getProductUrl(String handle)
// Returns: https://hearnshobbies.myshopify.com/products/{handle}

// Generate cart permalink for direct add-to-cart
getAddToCartUrl(String variantId, int quantity)
// Returns: https://hearnshobbies.myshopify.com/cart/{variantId}:{quantity}
```

**How Cart Permalinks Work:**
- Cart URLs work from anywhere (email, SMS, chat, websites)
- No authentication needed
- Customer clicks → item automatically added to cart → ready to checkout
- Perfect for customer service and marketing use cases

**Example Usage:**
```bash
# Search for product
curl "https://shopify-data-api-production.up.railway.app/api/products/search?q=gundam&first=1"

# Response includes:
# - handle: "hg-petit-bear-guy-winning-yellow"
# - variants[0].id: "gid://shopify/ProductVariant/6585417925"

# Construct URLs:
# Product: https://hearnshobbies.myshopify.com/products/hg-petit-bear-guy-winning-yellow
# Add to Cart: https://hearnshobbies.myshopify.com/cart/6585417925:1
```

## Technical Stack

### Core Technologies
- **Language:** Java 17.0.9 (OpenJDK)
- **Framework:** Spring Boot 3.2.0
- **Build Tool:** Maven 3.9.11
- **API Protocol:** GraphQL (Shopify Admin API)
- **HTTP Client:** Spring WebFlux WebClient

### Key Dependencies
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>
```

### Development Tools
- **Java/Maven Installation:** SDKMAN (no sudo required)
- **Version Control:** Git
- **API Testing:** curl, Postman (optional)

## Configuration

### Environment Variables

**Required for local development:**
```bash
SHOPIFY_SHOP_URL=hearnshobbies.myshopify.com
SHOPIFY_ACCESS_TOKEN=your_token_here
SHOPIFY_API_VERSION=2025-01
SHOPIFY_MAX_POINTS=100
PORT=8080
```

**Required for Railway deployment:**
```bash
SHOPIFY_SHOP_URL=hearnshobbies.myshopify.com
SHOPIFY_ACCESS_TOKEN=your_token_here
SHOPIFY_API_VERSION=2025-01
SHOPIFY_MAX_POINTS=100
# DATABASE_URL will be auto-provided by Railway PostgreSQL service
```

### Shopify API Scopes

The access token has these scopes enabled:
- `read_products`
- `read_orders`
- `read_customers`
- `read_inventory`

**Important:** After changing API scopes in Shopify admin, the app must be reinstalled to generate a new access token with updated permissions.

## Architecture Overview

### Request Flow
```
Client HTTP Request
    ↓
Controller (REST endpoint)
    ↓
Service (Business logic)
    ↓
ShopifyGraphQLClient (GraphQL communication)
    ↓
RateLimiter (Token bucket algorithm)
    ↓
Shopify Admin API (GraphQL)
    ↓
Response back through layers
```

### Key Components

**1. Controllers** (`src/main/java/com/shopify/api/controller/`)
- `HealthController.java` - Health/status endpoints
- `ProductController.java` - Product endpoints (✅ working)
- `OrderController.java` - Order endpoints (✅ working)
- `CustomerController.java` - Customer endpoints (⚠️ broken)
- `InventoryController.java` - Inventory endpoints (⚠️ broken)

**2. Services** (`src/main/java/com/shopify/api/service/`)
- `ShopifyGraphQLClient.java` - Core GraphQL client with retry logic
- `ProductService.java` - Product business logic (✅ working)
- `OrderService.java` - Order business logic (✅ working)
- `CustomerService.java` - Customer business logic (⚠️ needs fix)
- `InventoryService.java` - Inventory business logic (⚠️ needs fix)

**3. Utilities** (`src/main/java/com/shopify/api/util/`)
- `RateLimiter.java` - Token bucket rate limiting

**4. Configuration** (`src/main/java/com/shopify/api/config/`)
- `ShopifyConfig.java` - Shopify connection configuration
- `WebClientConfig.java` - HTTP client configuration

**5. Models** (`src/main/java/com/shopify/api/model/`)
- `ApiResponse.java` - Standard API response wrapper
- `GraphQLRequest.java` - GraphQL request structure
- `GraphQLResponse.java` - GraphQL response structure

## Rate Limiting

### Token Bucket Implementation
- **Max Points:** 100 (configurable based on Shopify plan)
- **Refill Rate:** 50 points per second
- **Behavior:** Automatically queues requests when limit reached
- **Retry Logic:** Exponential backoff on failures (3 retries, starting at 1 second)

### Shopify Plan Limits
- **Basic:** 100 points
- **Advanced:** 200 points
- **Plus:** 1000 points
- **Enterprise:** 2000 points

Current configuration: Basic plan (100 points)

## How to Run Locally

### Prerequisites
- Java 17+ installed (via SDKMAN)
- Maven installed (via SDKMAN)
- Shopify store with custom app configured
- Valid access token with required scopes

### Start Application

```bash
# Load SDKMAN
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Run with environment variables
SHOPIFY_SHOP_URL=hearnshobbies.myshopify.com \
SHOPIFY_ACCESS_TOKEN=your_token_here \
SHOPIFY_API_VERSION=2025-01 \
SHOPIFY_MAX_POINTS=100 \
PORT=8080 \
mvn spring-boot:run
```

### Wait for Startup

Look for this message:
```
Shopify Data API Started Successfully!
Service is running on port 8080
```

### Test Endpoints

```bash
# Health check (should return immediately)
curl http://localhost:8080/api/health

# Products (should return real products from hearnshobbies)
curl "http://localhost:8080/api/products?first=5"

# Orders (should return real orders)
curl "http://localhost:8080/api/orders?first=5"
```

## Next Steps

### Immediate Tasks (Approved Plan)
1. ✅ **Task 0:** Save WORKING1 checkpoint - COMPLETED
2. 🔄 **Task 1:** Document everything - IN PROGRESS
3. ⏳ **Task 2:** Deploy to Railway
4. ⏳ **Task 3:** Show usage examples
5. ⏳ **Task 4:** Fix Customers & Inventory endpoints
6. ⏳ **Task 5:** Deploy final version

### Deployment Strategy
- Deploy current working version first (Products & Orders only)
- Test in production environment
- Fix remaining endpoints
- Deploy complete version

### Post-Deployment
- Monitor Railway logs for errors
- Test all working endpoints on production URL
- Document production API usage
- Create usage examples for customer service applications

## Troubleshooting

### Common Issues

**Issue:** Port 8080 already in use
**Solution:** `lsof -ti:8080 | xargs kill -9`

**Issue:** Connection to Shopify fails
**Solution:** Verify access token has required scopes, reinstall app if needed

**Issue:** GraphQL field errors
**Solution:** Check Shopify API documentation for correct field names in API 2025-01

**Issue:** Rate limit exceeded
**Solution:** Reduce SHOPIFY_MAX_POINTS or reduce query complexity

## Documentation

### Available Documentation Files
- `README.md` - Project overview and quick start
- `QUICKSTART.md` - 15-minute setup guide
- `WORKING1_STATUS.md` - WORKING1 checkpoint details
- `CURRENT_STATUS.md` - This file
- `docs/PROJECT_SETUP.md` - Detailed setup instructions
- `docs/SHOPIFY_CONNECTION.md` - Shopify app configuration
- `docs/RAILWAY_DEPLOYMENT.md` - Railway deployment guide
- `docs/API_REFERENCE.md` - Complete API endpoint reference
- `docs/ADDING_FUNCTIONS.md` - How to add new endpoints
- `docs/TROUBLESHOOTING.md` - Common issues and solutions

### Quick Links
- Shopify API Docs: https://shopify.dev/docs/api/admin
- GraphQL Explorer: https://shopify.dev/docs/apps/tools/graphiql-admin-api
- Railway Docs: https://docs.railway.app/
- Spring Boot Docs: https://spring.io/projects/spring-boot

## Project Health

### Metrics
- **Total Endpoints:** 13
- **Working Endpoints:** 13 (100%) ✅
- **Broken Endpoints:** 0 (0%) ✅
- **Critical Issues:** 0 ✅
- **Code Quality:** Excellent (proper error handling, logging, rate limiting, URL utilities)
- **Documentation:** Comprehensive (vision, roadmap, checkpoints, API docs)

### Readiness
- **Local Development:** ✅ Ready
- **Railway Deployment:** ✅ Deployed
- **Production Use:** ✅ Fully Functional
- **All Features:** ✅ Complete

### Next Phase
- **Current Checkpoint:** WORKING2
- **Next Milestone:** WORKING3 (React Frontend)
- **See:** `DEVELOPMENT_ROADMAP.md` for detailed plan

---

**Status:** All 13 endpoints fully functional and deployed to production. Product URL and cart permalink features added. Backend API complete. Ready to build React frontend (Phase 1).
