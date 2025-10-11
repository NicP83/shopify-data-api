# API Reference

Complete reference for all available endpoints in the Shopify Data API.

## Base URL

**Local Development:**
```
http://localhost:8080
```

**Production (Railway):**
```
https://your-app.up.railway.app
```

## Response Format

All endpoints return responses in this format:

```json
{
  "success": true|false,
  "message": "Description of result",
  "data": { ... },
  "error": "Error details (if success is false)"
}
```

## Authentication

Currently using Admin API access tokens configured via environment variables.

For future OAuth implementations, add header:
```
Authorization: Bearer {token}
```

---

## Health & Status Endpoints

### GET /api/health

Basic health check to verify the service is running.

**Request:**
```bash
curl http://localhost:8080/api/health
```

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "status": "UP",
    "service": "Shopify Data API",
    "timestamp": 1704067200000
  }
}
```

### GET /api/status

Detailed system status including Shopify connection test.

**Request:**
```bash
curl http://localhost:8080/api/status
```

**Response:**
```json
{
  "success": true,
  "message": "System operational",
  "data": {
    "service": "Shopify Data API",
    "timestamp": 1704067200000,
    "shopify_connected": true,
    "rate_limiter_available_points": 1000
  }
}
```

---

## Product Endpoints

### GET /api/products

Fetch list of products with pagination.

**Parameters:**
- `first` (optional, default: 50, max: 250) - Number of products to fetch

**Request:**
```bash
curl "http://localhost:8080/api/products?first=10"
```

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "products": {
      "edges": [
        {
          "node": {
            "id": "gid://shopify/Product/123456",
            "title": "Sample Product",
            "description": "Product description",
            "handle": "sample-product",
            "status": "ACTIVE",
            "vendor": "Acme Inc",
            "productType": "Apparel",
            "createdAt": "2024-01-01T00:00:00Z",
            "updatedAt": "2024-01-15T00:00:00Z",
            "tags": ["sale", "featured"],
            "variants": {
              "edges": [
                {
                  "node": {
                    "id": "gid://shopify/ProductVariant/789",
                    "title": "Small / Red",
                    "sku": "SAMPLE-S-R",
                    "price": "29.99",
                    "inventoryQuantity": 50
                  }
                }
              ]
            },
            "images": {
              "edges": [
                {
                  "node": {
                    "url": "https://cdn.shopify.com/...",
                    "altText": "Product image"
                  }
                }
              ]
            }
          }
        }
      ],
      "pageInfo": {
        "hasNextPage": true,
        "hasPreviousPage": false
      }
    }
  }
}
```

### GET /api/products/{id}

Fetch a single product by ID with full details.

**Parameters:**
- `id` (required) - Shopify product ID (with or without `gid://shopify/Product/` prefix)

**Request:**
```bash
curl "http://localhost:8080/api/products/123456"
# or
curl "http://localhost:8080/api/products/gid://shopify/Product/123456"
```

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "product": {
      "id": "gid://shopify/Product/123456",
      "title": "Sample Product",
      "description": "Full description...",
      "descriptionHtml": "<p>Full description...</p>",
      "handle": "sample-product",
      "status": "ACTIVE",
      "vendor": "Acme Inc",
      "productType": "Apparel",
      "createdAt": "2024-01-01T00:00:00Z",
      "updatedAt": "2024-01-15T00:00:00Z",
      "tags": ["sale", "featured"],
      "options": [
        {
          "name": "Size",
          "values": ["Small", "Medium", "Large"]
        }
      ],
      "variants": { ... },
      "images": { ... }
    }
  }
}
```

### GET /api/products/search

Search products by keyword.

**Parameters:**
- `q` (required) - Search query
- `first` (optional, default: 20) - Number of results

**Query Examples:**
- `title:shirt` - Search in title
- `tag:sale` - Products with "sale" tag
- `vendor:Acme` - Products by vendor
- `product_type:Apparel` - By product type

**Request:**
```bash
curl "http://localhost:8080/api/products/search?q=title:shirt&first=5"
```

---

## Order Endpoints

### GET /api/orders

Fetch list of orders with pagination.

**Parameters:**
- `first` (optional, default: 50, max: 250) - Number of orders to fetch

**Request:**
```bash
curl "http://localhost:8080/api/orders?first=20"
```

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "orders": {
      "edges": [
        {
          "node": {
            "id": "gid://shopify/Order/987654",
            "name": "#1001",
            "email": "customer@example.com",
            "createdAt": "2024-01-10T10:30:00Z",
            "updatedAt": "2024-01-10T11:00:00Z",
            "displayFinancialStatus": "PAID",
            "displayFulfillmentStatus": "FULFILLED",
            "subtotalPrice": "99.99",
            "totalPrice": "109.98",
            "totalTax": "9.99",
            "currencyCode": "USD",
            "customer": {
              "id": "gid://shopify/Customer/555",
              "email": "customer@example.com",
              "firstName": "John",
              "lastName": "Doe"
            },
            "lineItems": {
              "edges": [...]
            },
            "shippingAddress": {
              "address1": "123 Main St",
              "city": "New York",
              "province": "NY",
              "country": "United States",
              "zip": "10001"
            }
          }
        }
      ]
    }
  }
}
```

### GET /api/orders/{id}

Fetch a single order by ID with full details.

**Request:**
```bash
curl "http://localhost:8080/api/orders/987654"
```

### GET /api/orders/search

Search orders by various criteria.

**Parameters:**
- `q` (required) - Search query
- `first` (optional, default: 20) - Number of results

**Query Examples:**
- `email:customer@example.com` - By customer email
- `status:open` - Open orders
- `financial_status:paid` - Paid orders
- `fulfillment_status:unfulfilled` - Unfulfilled orders
- `created_at:>2024-01-01` - Orders after date

**Request:**
```bash
curl "http://localhost:8080/api/orders/search?q=email:customer@example.com"
```

---

## Customer Endpoints

### GET /api/customers

Fetch list of customers with pagination.

**Parameters:**
- `first` (optional, default: 50, max: 250) - Number of customers to fetch

**Request:**
```bash
curl "http://localhost:8080/api/customers?first=25"
```

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "customers": {
      "edges": [
        {
          "node": {
            "id": "gid://shopify/Customer/555",
            "email": "customer@example.com",
            "firstName": "John",
            "lastName": "Doe",
            "phone": "+1234567890",
            "createdAt": "2024-01-01T00:00:00Z",
            "updatedAt": "2024-01-15T00:00:00Z",
            "state": "ENABLED",
            "note": "VIP customer",
            "verifiedEmail": true,
            "taxExempt": false,
            "tags": ["vip", "newsletter"],
            "ordersCount": 15,
            "totalSpent": "1499.85",
            "defaultAddress": {
              "address1": "123 Main St",
              "city": "New York",
              "province": "NY",
              "country": "United States",
              "zip": "10001"
            }
          }
        }
      ]
    }
  }
}
```

### GET /api/customers/{id}

Fetch a single customer by ID including order history.

**Request:**
```bash
curl "http://localhost:8080/api/customers/555"
```

**Response includes:**
- Full customer details
- All addresses
- Recent orders (last 10)
- Lifetime value
- Tags and notes

### GET /api/customers/search

Search customers.

**Parameters:**
- `q` (required) - Search query
- `first` (optional, default: 20) - Number of results

**Query Examples:**
- `email:customer@example.com` - By email
- `phone:555-1234` - By phone
- `first_name:John` - By first name
- `tag:vip` - By tag
- `state:ENABLED` - Active customers

**Request:**
```bash
curl "http://localhost:8080/api/customers/search?q=email:customer@example.com"
```

---

## Inventory Endpoints

### GET /api/inventory

Fetch inventory levels for products.

**Parameters:**
- `first` (optional, default: 50, max: 250) - Number of products

**Request:**
```bash
curl "http://localhost:8080/api/inventory?first=10"
```

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "products": {
      "edges": [
        {
          "node": {
            "id": "gid://shopify/Product/123456",
            "title": "Sample Product",
            "status": "ACTIVE",
            "variants": {
              "edges": [
                {
                  "node": {
                    "id": "gid://shopify/ProductVariant/789",
                    "title": "Small / Red",
                    "sku": "SAMPLE-S-R",
                    "inventoryQuantity": 50,
                    "inventoryPolicy": "DENY",
                    "inventoryManagement": "SHOPIFY",
                    "inventoryItem": {
                      "id": "gid://shopify/InventoryItem/999",
                      "tracked": true,
                      "inventoryLevels": {
                        "edges": [
                          {
                            "node": {
                              "id": "gid://shopify/InventoryLevel/111",
                              "available": 50,
                              "location": {
                                "id": "gid://shopify/Location/222",
                                "name": "Main Warehouse"
                              }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
              ]
            }
          }
        }
      ]
    }
  }
}
```

### GET /api/inventory/product/{id}

Fetch inventory for a specific product across all locations.

**Request:**
```bash
curl "http://localhost:8080/api/inventory/product/123456"
```

### GET /api/inventory/locations

Fetch all inventory locations.

**Request:**
```bash
curl "http://localhost:8080/api/inventory/locations"
```

**Response:**
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "locations": {
      "edges": [
        {
          "node": {
            "id": "gid://shopify/Location/222",
            "name": "Main Warehouse",
            "isActive": true,
            "address": {
              "address1": "456 Warehouse Rd",
              "city": "Los Angeles",
              "province": "CA",
              "country": "United States",
              "zip": "90001"
            }
          }
        }
      ]
    }
  }
}
```

---

## Error Responses

### 400 Bad Request
```json
{
  "success": false,
  "message": "Invalid request",
  "error": "Parameter 'first' must be between 1 and 250"
}
```

### 404 Not Found
```json
{
  "success": false,
  "message": "Resource not found",
  "error": "Product with ID 123456 does not exist"
}
```

### 500 Internal Server Error
```json
{
  "success": false,
  "message": "Failed to fetch products",
  "error": "Shopify API error: Rate limit exceeded"
}
```

### 503 Service Unavailable
```json
{
  "success": false,
  "message": "Shopify connection failed",
  "error": "Unable to connect to Shopify API"
}
```

---

## Rate Limiting

The API automatically handles Shopify's rate limits:
- Tracks available points
- Queues requests when necessary
- Implements exponential backoff on failures

No client-side rate limiting needed!

---

## Testing with curl

### Basic GET Request
```bash
curl http://localhost:8080/api/products
```

### With Parameters
```bash
curl "http://localhost:8080/api/products?first=10"
```

### Pretty Print JSON
```bash
curl http://localhost:8080/api/products | jq
```

### Save Response to File
```bash
curl http://localhost:8080/api/products > products.json
```

---

## Testing with Postman

1. Create new collection "Shopify Data API"
2. Add environment with base URL
3. Create requests for each endpoint
4. Use variables for dynamic IDs

---

## Next Steps

- **Deploy to Railway** → [RAILWAY_DEPLOYMENT.md](./RAILWAY_DEPLOYMENT.md)
- **Add new endpoints** → [ADDING_FUNCTIONS.md](./ADDING_FUNCTIONS.md)
- **Troubleshooting** → [TROUBLESHOOTING.md](./TROUBLESHOOTING.md)
