# Shopify Data Extraction API

A Java Spring Boot REST API for extracting and accessing data from Shopify stores using the Shopify GraphQL Admin API.

## Overview

This API provides a clean interface to access Shopify data for customer service, analytics, and other business applications. It handles authentication, rate limiting, and provides simple REST endpoints to query Shopify resources.

## Features

- **GraphQL to REST Translation**: Simple REST endpoints backed by Shopify GraphQL queries
- **Automatic Rate Limiting**: Built-in rate limiter respects Shopify API limits
- **Retry Logic**: Automatic exponential backoff for failed requests
- **Comprehensive Data Access**: Products, Orders, Customers, and Inventory
- **Easy Deployment**: Designed for Railway with auto-detection
- **Production Ready**: Proper error handling, logging, and monitoring

## Tech Stack

- **Java 17**
- **Spring Boot 3.2**
- **Maven**
- **PostgreSQL** (for token storage and caching)
- **WebClient** (for GraphQL requests)
- **Railway** (deployment platform)

## Project Structure

```
shopify-data-api/
├── src/main/java/com/shopify/api/
│   ├── ShopifyDataApiApplication.java  # Main application
│   ├── config/                         # Configuration classes
│   │   ├── ShopifyConfig.java         # Shopify settings
│   │   └── WebClientConfig.java       # HTTP client setup
│   ├── client/                        # Shopify API client
│   │   └── ShopifyGraphQLClient.java  # GraphQL query executor
│   ├── controller/                    # REST endpoints
│   │   ├── HealthController.java      # Health checks
│   │   ├── ProductController.java     # Product endpoints
│   │   ├── OrderController.java       # Order endpoints
│   │   ├── CustomerController.java    # Customer endpoints
│   │   └── InventoryController.java   # Inventory endpoints
│   ├── service/                       # Business logic
│   │   ├── ProductService.java
│   │   ├── OrderService.java
│   │   ├── CustomerService.java
│   │   └── InventoryService.java
│   ├── model/                         # Data models
│   │   ├── GraphQLRequest.java
│   │   ├── GraphQLResponse.java
│   │   └── ApiResponse.java
│   └── util/                          # Utilities
│       └── RateLimiter.java           # Rate limiting logic
├── src/main/resources/
│   └── application.yml                # Application configuration
├── docs/                              # Documentation
│   ├── PROJECT_SETUP.md
│   ├── SHOPIFY_CONNECTION.md
│   ├── RAILWAY_DEPLOYMENT.md
│   ├── API_REFERENCE.md
│   ├── ADDING_FUNCTIONS.md
│   └── TROUBLESHOOTING.md
├── pom.xml                            # Maven dependencies
└── README.md                          # This file
```

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Shopify Partner account
- Shopify store with Admin API access
- Railway account (for deployment)

### Local Development

1. **Clone the repository**
   ```bash
   cd shopify-data-api
   ```

2. **Configure environment variables**
   ```bash
   cp .env.example .env
   # Edit .env with your Shopify credentials
   ```

3. **Build the project**
   ```bash
   mvn clean install
   ```

4. **Run locally**
   ```bash
   mvn spring-boot:run
   ```

5. **Test the connection**
   ```bash
   curl http://localhost:8080/api/status
   ```

## API Endpoints

### Health & Status
- `GET /api/health` - Basic health check
- `GET /api/status` - Detailed status with Shopify connection test

### Products
- `GET /api/products?first=50` - List products
- `GET /api/products/{id}` - Get product by ID
- `GET /api/products/search?q=keyword` - Search products

### Orders
- `GET /api/orders?first=50` - List orders
- `GET /api/orders/{id}` - Get order by ID
- `GET /api/orders/search?q=email:customer@example.com` - Search orders

### Customers
- `GET /api/customers?first=50` - List customers
- `GET /api/customers/{id}` - Get customer by ID (includes order history)
- `GET /api/customers/search?q=email:customer@example.com` - Search customers

### Inventory
- `GET /api/inventory?first=50` - Get inventory for products
- `GET /api/inventory/product/{id}` - Get inventory for specific product
- `GET /api/inventory/locations` - List all inventory locations

## Configuration

Key environment variables (see `.env.example`):

```bash
SHOPIFY_SHOP_URL=your-store.myshopify.com
SHOPIFY_ACCESS_TOKEN=your_admin_api_access_token
SHOPIFY_API_VERSION=2025-01
SHOPIFY_MAX_POINTS=100  # Based on your Shopify plan
DATABASE_URL=jdbc:postgresql://...
```

## Documentation

Detailed documentation is available in the `docs/` directory:

- **[PROJECT_SETUP.md](docs/PROJECT_SETUP.md)** - Initial project setup guide
- **[SHOPIFY_CONNECTION.md](docs/SHOPIFY_CONNECTION.md)** - Getting Shopify API credentials
- **[RAILWAY_DEPLOYMENT.md](docs/RAILWAY_DEPLOYMENT.md)** - Deploying to Railway
- **[API_REFERENCE.md](docs/API_REFERENCE.md)** - Complete API documentation
- **[ADDING_FUNCTIONS.md](docs/ADDING_FUNCTIONS.md)** - How to add new endpoints
- **[TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)** - Common issues and solutions

## Deployment

This project is optimized for Railway deployment:

1. Push to GitHub
2. Connect to Railway
3. Add PostgreSQL database
4. Set environment variables
5. Deploy automatically

See [RAILWAY_DEPLOYMENT.md](docs/RAILWAY_DEPLOYMENT.md) for detailed instructions.

## Rate Limiting

The API automatically handles Shopify's rate limits:

- Tracks available points per second
- Implements token bucket algorithm
- Automatic retry with exponential backoff
- Configurable based on your Shopify plan

## Adding New Functions

To add a new endpoint (e.g., for webhooks or metafields):

1. Create GraphQL query in the service
2. Add parsing logic
3. Create REST controller endpoint
4. Update documentation

See [ADDING_FUNCTIONS.md](docs/ADDING_FUNCTIONS.md) for step-by-step guide.

## License

MIT License - See LICENSE file for details

## Support

For issues or questions:
1. Check [TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)
2. Review Shopify API documentation
3. Open an issue in the repository
