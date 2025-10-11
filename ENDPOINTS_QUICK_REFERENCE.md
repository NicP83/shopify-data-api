# API Endpoints Quick Reference

Fast reference guide for all Shopify Data API endpoints.

## Base URL

**Local:** `http://localhost:8080`
**Production:** `https://your-app-name.up.railway.app`

## Working Endpoints ✅

### Health & Status

| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| `GET` | `/api/health` | Health check | ✅ |
| `GET` | `/api/status` | System status + Shopify connection | ✅ |

### Products

| Method | Endpoint | Parameters | Status |
|--------|----------|------------|--------|
| `GET` | `/api/products` | `?first=50` | ✅ |
| `GET` | `/api/products/{id}` | - | ✅ |
| `GET` | `/api/products/search` | `?q=query&first=20` | ✅ |

### Orders

| Method | Endpoint | Parameters | Status |
|--------|----------|------------|--------|
| `GET` | `/api/orders` | `?first=50` | ✅ |
| `GET` | `/api/orders/{id}` | - | ✅ |
| `GET` | `/api/orders/search` | `?q=query&first=20` | ✅ |

## Broken Endpoints ⚠️

### Customers (GraphQL Field Errors)

| Method | Endpoint | Parameters | Status | Issue |
|--------|----------|------------|--------|-------|
| `GET` | `/api/customers` | `?first=50` | ⚠️ | Deprecated fields |
| `GET` | `/api/customers/{id}` | - | ⚠️ | Deprecated fields |
| `GET` | `/api/customers/search` | `?q=query&first=20` | ⚠️ | Deprecated fields |

**Errors:** `ordersCount`, `totalSpent`, `lifetimeDuration` don't exist in API 2025-01

### Inventory (GraphQL Field Errors)

| Method | Endpoint | Parameters | Status | Issue |
|--------|----------|------------|--------|-------|
| `GET` | `/api/inventory` | `?first=50` | ⚠️ | Deprecated fields |
| `GET` | `/api/inventory/product/{id}` | - | ⚠️ | Deprecated fields |
| `GET` | `/api/inventory/locations` | - | ✅ | Working |

**Errors:** `inventoryManagement`, `inventoryPolicy` don't exist in API 2025-01

---

## Quick Examples

### Health Check
```bash
curl http://localhost:8080/api/health
```

### Get 5 Products
```bash
curl "http://localhost:8080/api/products?first=5"
```

### Get Product by ID
```bash
curl "http://localhost:8080/api/products/9799176044839"
```

### Search Products
```bash
curl "http://localhost:8080/api/products/search?q=title:Gundam&first=5"
```

### Get 5 Orders
```bash
curl "http://localhost:8080/api/orders?first=5"
```

### Get Order by ID
```bash
curl "http://localhost:8080/api/orders/987654321"
```

### Search Orders by Email
```bash
curl "http://localhost:8080/api/orders/search?q=email:customer@example.com"
```

---

## Response Format

All endpoints return:

**Success:**
```json
{
  "success": true,
  "message": "Success",
  "data": { ... }
}
```

**Error:**
```json
{
  "success": false,
  "message": "Error description",
  "error": "Detailed error message"
}
```

---

## Parameters

### Pagination
- `first` - Number of items to return (default: 50, max: 250)
- Example: `?first=10`

### Search Queries

**Products:**
- `title:keyword` - Search in title
- `tag:tagname` - Filter by tag
- `vendor:name` - Filter by vendor
- `product_type:type` - Filter by product type

**Orders:**
- `email:customer@example.com` - By customer email
- `status:open` - By order status
- `financial_status:paid` - By payment status
- `fulfillment_status:fulfilled` - By fulfillment status
- `created_at:>2024-01-01` - By date

**Customers:**
- `email:customer@example.com` - By email
- `phone:555-1234` - By phone
- `first_name:John` - By first name
- `tag:vip` - By tag
- `state:ENABLED` - By status

---

## HTTP Status Codes

| Code | Meaning | When |
|------|---------|------|
| `200` | Success | Request completed successfully |
| `400` | Bad Request | Invalid parameters |
| `404` | Not Found | Resource doesn't exist |
| `429` | Rate Limited | Too many requests (handled automatically) |
| `500` | Server Error | Internal error or Shopify API issue |
| `503` | Unavailable | Shopify connection failed |

---

## Common Use Cases

### Customer Service
```bash
# Find customer's recent orders
curl "$API_URL/api/orders/search?q=email:customer@example.com"

# Get order details
curl "$API_URL/api/orders/{id}"

# Check product availability
curl "$API_URL/api/products/{id}"
```

### Inventory Management
```bash
# Get all inventory levels
curl "$API_URL/api/inventory?first=250"

# Check specific product stock
curl "$API_URL/api/inventory/product/{id}"

# List warehouse locations
curl "$API_URL/api/inventory/locations"
```

### Sales Analytics
```bash
# Get recent orders
curl "$API_URL/api/orders?first=100"

# Get paid orders only
curl "$API_URL/api/orders/search?q=financial_status:paid&first=250"

# Get orders from specific date
curl "$API_URL/api/orders/search?q=created_at:>2024-01-01"
```

### Product Catalog
```bash
# Get all products
curl "$API_URL/api/products?first=250"

# Search by category
curl "$API_URL/api/products/search?q=product_type:Electronics"

# Search by vendor
curl "$API_URL/api/products/search?q=vendor:BANDAI"
```

---

## Rate Limiting

- Automatically handled by the API
- Max 100 points per request (configurable)
- Refills at 50 points/second
- Requests are queued when limit reached

**You don't need to implement rate limiting on the client side!**

---

## Testing Tips

### Set API URL Variable
```bash
# Local
export API_URL="http://localhost:8080"

# Production
export API_URL="https://your-app-name.up.railway.app"
```

### Use with jq for Pretty Output
```bash
curl "$API_URL/api/products?first=5" | jq
```

### Save Response to File
```bash
curl "$API_URL/api/products" > products.json
```

### Check Status Code
```bash
curl -w "\nHTTP Status: %{http_code}\n" "$API_URL/api/health"
```

### Verbose Output (Debug)
```bash
curl -v "$API_URL/api/products?first=5"
```

---

## JavaScript Fetch

```javascript
const API_URL = 'https://your-app-name.up.railway.app';

async function getProducts(limit = 10) {
  const response = await fetch(`${API_URL}/api/products?first=${limit}`);
  const data = await response.json();

  if (data.success) {
    return data.data.products.edges.map(edge => edge.node);
  }
  throw new Error(data.error || 'Failed to fetch products');
}
```

---

## Python Requests

```python
import requests

API_URL = 'https://your-app-name.up.railway.app'

def get_orders(limit=10):
    response = requests.get(
        f"{API_URL}/api/orders",
        params={'first': limit}
    )
    data = response.json()

    if data['success']:
        return [edge['node'] for edge in data['data']['orders']['edges']]
    raise Exception(data.get('error', 'Failed to fetch orders'))
```

---

## curl with Authentication (Future)

When authentication is added:

```bash
curl -H "Authorization: Bearer YOUR_TOKEN" \
     "$API_URL/api/products"
```

Currently no authentication required (configured via environment variables).

---

## Real Data Examples

### Products Response
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "products": {
      "edges": [
        {
          "node": {
            "id": "gid://shopify/Product/9799176044839",
            "title": "BANDAI MG 1/100 Gundam AGE-1 Normal",
            "handle": "bandai-mg-1-100-gundam-age-1-normal",
            "status": "ACTIVE",
            "vendor": "BANDAI",
            "productType": "Model Kits",
            "variants": { ... },
            "images": { ... }
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

### Orders Response
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "orders": {
      "edges": [
        {
          "node": {
            "id": "gid://shopify/Order/6486663266599",
            "name": "#HHW1001",
            "email": "chad@example.com",
            "displayFinancialStatus": "PAID",
            "displayFulfillmentStatus": "FULFILLED",
            "totalPrice": "109.98",
            "currencyCode": "CAD",
            "customer": {
              "firstName": "Chad",
              "lastName": "Rozario"
            }
          }
        }
      ]
    }
  }
}
```

---

## Troubleshooting

### Connection Refused
- Ensure application is running
- Check port is 8080 (local) or Railway-assigned (production)

### 404 Not Found
- Verify endpoint path
- Check resource ID is correct

### Empty Data
- Ensure data exists in Shopify
- Check search query syntax
- Verify API scopes

### Rate Limit Errors
- Reduce request frequency
- Lower `SHOPIFY_MAX_POINTS` in config
- Check query complexity

---

## Additional Resources

- **Complete API Docs:** `docs/API_REFERENCE.md`
- **Usage Examples:** `USAGE_EXAMPLES.md`
- **Deployment Guide:** `DEPLOYMENT_GUIDE.md`
- **Troubleshooting:** `docs/TROUBLESHOOTING.md`

---

## Quick Copy-Paste Commands

```bash
# Set API URL
export API_URL="http://localhost:8080"

# Test everything
curl $API_URL/api/health && echo "✅ Health OK"
curl "$API_URL/api/products?first=1" > /dev/null && echo "✅ Products OK"
curl "$API_URL/api/orders?first=1" > /dev/null && echo "✅ Orders OK"

# Get counts
echo "Products: $(curl -s '$API_URL/api/products?first=250' | jq '.data.products.edges | length')"
echo "Orders: $(curl -s '$API_URL/api/orders?first=250' | jq '.data.orders.edges | length')"
```

---

**For detailed information on each endpoint, see:** `docs/API_REFERENCE.md`
