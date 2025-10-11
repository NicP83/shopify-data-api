# Usage Examples

Practical examples for using the Shopify Data API in real applications.

## Base URLs

**Local Development:**
```
http://localhost:8080
```

**Production (Railway):**
```
https://your-app-name.up.railway.app
```

Replace `your-app-name` with your actual Railway domain.

For these examples, we'll use `$API_URL` to represent either URL.

## Quick Start

### Set Your API URL

```bash
# For local development
export API_URL="http://localhost:8080"

# For production
export API_URL="https://your-app-name.up.railway.app"
```

### Test Connection

```bash
curl $API_URL/api/health
```

Expected response:
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

## Use Case 1: Customer Service Dashboard

### Scenario
Customer service team needs to quickly look up order details when customers call.

### Example Workflow

**1. Search for customer's order by email:**

```bash
curl "$API_URL/api/orders/search?q=email:chad@example.com"
```

Response includes:
- Order number (e.g., #HHW1001)
- Order date
- Total amount
- Payment status
- Fulfillment status

**2. Get full order details:**

```bash
# Using order ID from search results
curl "$API_URL/api/orders/9876543210"
```

Response includes:
- Complete line items
- Shipping address
- Tracking information
- Customer details

**3. Check product availability:**

```bash
curl "$API_URL/api/products/9799176044839"
```

Response includes:
- Current inventory levels
- Product variants
- Pricing

### JavaScript Integration

```javascript
// customer-service-dashboard.js

const API_URL = 'https://your-app-name.up.railway.app';

async function lookupOrderByEmail(email) {
  const response = await fetch(
    `${API_URL}/api/orders/search?q=email:${encodeURIComponent(email)}`
  );
  const data = await response.json();

  if (data.success) {
    return data.data.orders.edges.map(edge => edge.node);
  }
  throw new Error(data.error || 'Failed to fetch orders');
}

async function getOrderDetails(orderId) {
  const response = await fetch(`${API_URL}/api/orders/${orderId}`);
  const data = await response.json();

  if (data.success) {
    return data.data.order;
  }
  throw new Error(data.error || 'Failed to fetch order');
}

// Usage
async function handleCustomerCall(customerEmail) {
  try {
    // Find all orders for customer
    const orders = await lookupOrderByEmail(customerEmail);

    console.log(`Found ${orders.length} orders for ${customerEmail}`);

    // Get details of most recent order
    if (orders.length > 0) {
      const latestOrder = orders[0];
      const details = await getOrderDetails(latestOrder.id);

      console.log('Order Number:', details.name);
      console.log('Status:', details.displayFinancialStatus);
      console.log('Total:', details.totalPrice, details.currencyCode);
    }
  } catch (error) {
    console.error('Error:', error.message);
  }
}

// Example call
handleCustomerCall('chad@example.com');
```

## Use Case 2: Inventory Management

### Scenario
Warehouse team needs to check stock levels before shipping orders.

### Example Workflow

**1. Get inventory for all products:**

```bash
curl "$API_URL/api/inventory?first=50"
```

**2. Check specific product inventory:**

```bash
curl "$API_URL/api/inventory/product/9799176044839"
```

Response includes:
- Inventory by location
- Available quantity
- SKU information

**3. Get all warehouse locations:**

```bash
curl "$API_URL/api/inventory/locations"
```

### Python Integration

```python
# inventory_checker.py

import requests

API_URL = 'https://your-app-name.up.railway.app'

class InventoryChecker:
    def __init__(self, api_url=API_URL):
        self.api_url = api_url

    def check_product_stock(self, product_id):
        """Check inventory levels for a product"""
        response = requests.get(
            f"{self.api_url}/api/inventory/product/{product_id}"
        )
        data = response.json()

        if data['success']:
            product = data['data']['product']
            return {
                'id': product['id'],
                'title': product['title'],
                'variants': self._extract_inventory(product['variants'])
            }
        raise Exception(data.get('error', 'Failed to fetch inventory'))

    def _extract_inventory(self, variants):
        """Extract inventory info from variants"""
        result = []
        for edge in variants.get('edges', []):
            variant = edge['node']
            inventory_item = variant.get('inventoryItem', {})

            for level_edge in inventory_item.get('inventoryLevels', {}).get('edges', []):
                level = level_edge['node']
                result.append({
                    'sku': variant.get('sku'),
                    'title': variant.get('title'),
                    'available': level.get('available'),
                    'location': level.get('location', {}).get('name')
                })
        return result

    def get_low_stock_products(self, threshold=10):
        """Find products with low stock"""
        response = requests.get(f"{self.api_url}/api/inventory?first=250")
        data = response.json()

        if not data['success']:
            raise Exception(data.get('error', 'Failed to fetch inventory'))

        low_stock = []
        products = data['data']['products']['edges']

        for edge in products:
            product = edge['node']
            for variant_edge in product['variants']['edges']:
                variant = variant_edge['node']
                qty = variant.get('inventoryQuantity', 0)

                if qty < threshold:
                    low_stock.append({
                        'product': product['title'],
                        'sku': variant['sku'],
                        'quantity': qty
                    })

        return low_stock

# Usage
checker = InventoryChecker()

# Check specific product
stock = checker.check_product_stock('9799176044839')
print(f"Product: {stock['title']}")
for variant in stock['variants']:
    print(f"  SKU {variant['sku']}: {variant['available']} available at {variant['location']}")

# Find low stock items
low_stock = checker.get_low_stock_products(threshold=5)
print(f"\nFound {len(low_stock)} items with low stock:")
for item in low_stock:
    print(f"  {item['product']} (SKU: {item['sku']}): {item['quantity']} left")
```

## Use Case 3: Sales Analytics

### Scenario
Management wants daily sales reports with order details.

### Example Workflow

**1. Get recent orders:**

```bash
# Get last 50 orders
curl "$API_URL/api/orders?first=50"
```

**2. Filter orders by date:**

```bash
# Orders created after specific date
curl "$API_URL/api/orders/search?q=created_at:>2024-01-01"
```

**3. Get paid orders only:**

```bash
curl "$API_URL/api/orders/search?q=financial_status:paid&first=100"
```

### Python Report Generator

```python
# sales_report.py

import requests
from datetime import datetime, timedelta
from collections import defaultdict

API_URL = 'https://your-app-name.up.railway.app'

class SalesReporter:
    def __init__(self, api_url=API_URL):
        self.api_url = api_url

    def get_daily_sales(self, date=None):
        """Get sales for a specific date (default: today)"""
        if date is None:
            date = datetime.now().strftime('%Y-%m-%d')

        response = requests.get(
            f"{self.api_url}/api/orders/search",
            params={'q': f'created_at:>{date}', 'first': 250}
        )
        data = response.json()

        if not data['success']:
            raise Exception(data.get('error', 'Failed to fetch orders'))

        orders = data['data']['orders']['edges']

        # Calculate totals
        total_revenue = 0
        total_orders = len(orders)
        payment_statuses = defaultdict(int)

        for edge in orders:
            order = edge['node']
            total_revenue += float(order.get('totalPrice', 0))
            status = order.get('displayFinancialStatus', 'UNKNOWN')
            payment_statuses[status] += 1

        return {
            'date': date,
            'total_orders': total_orders,
            'total_revenue': total_revenue,
            'payment_breakdown': dict(payment_statuses),
            'average_order_value': total_revenue / total_orders if total_orders > 0 else 0
        }

    def get_top_products(self, days=7):
        """Get top selling products from recent orders"""
        # Get orders from last N days
        start_date = (datetime.now() - timedelta(days=days)).strftime('%Y-%m-%d')

        response = requests.get(
            f"{self.api_url}/api/orders/search",
            params={'q': f'created_at:>{start_date}', 'first': 250}
        )
        data = response.json()

        if not data['success']:
            raise Exception(data.get('error', 'Failed to fetch orders'))

        # Count product occurrences
        product_sales = defaultdict(lambda: {'count': 0, 'revenue': 0})

        for order_edge in data['data']['orders']['edges']:
            order = order_edge['node']
            for item_edge in order.get('lineItems', {}).get('edges', []):
                item = item_edge['node']
                product_title = item.get('title', 'Unknown')
                quantity = item.get('quantity', 0)
                price = float(item.get('originalTotalPrice', {}).get('amount', 0))

                product_sales[product_title]['count'] += quantity
                product_sales[product_title]['revenue'] += price

        # Sort by revenue
        sorted_products = sorted(
            product_sales.items(),
            key=lambda x: x[1]['revenue'],
            reverse=True
        )

        return sorted_products[:10]  # Top 10

# Usage
reporter = SalesReporter()

# Daily report
report = reporter.get_daily_sales()
print(f"Sales Report for {report['date']}")
print(f"Total Orders: {report['total_orders']}")
print(f"Total Revenue: ${report['total_revenue']:.2f}")
print(f"Average Order Value: ${report['average_order_value']:.2f}")
print(f"Payment Status Breakdown:")
for status, count in report['payment_breakdown'].items():
    print(f"  {status}: {count}")

# Top products
print("\nTop 10 Products (Last 7 Days):")
top_products = reporter.get_top_products(days=7)
for i, (product, stats) in enumerate(top_products, 1):
    print(f"{i}. {product}")
    print(f"   Quantity Sold: {stats['count']}")
    print(f"   Revenue: ${stats['revenue']:.2f}")
```

## Use Case 4: Product Catalog Sync

### Scenario
E-commerce platform needs to sync product data from Shopify.

### Example Workflow

**1. Get all products:**

```bash
curl "$API_URL/api/products?first=250"
```

**2. Get specific product with variants:**

```bash
curl "$API_URL/api/products/9799176044839"
```

**3. Search for products by category:**

```bash
curl "$API_URL/api/products/search?q=product_type:Gundam"
```

### Node.js Sync Script

```javascript
// product_sync.js

const axios = require('axios');
const API_URL = 'https://your-app-name.up.railway.app';

class ProductSync {
  constructor(apiUrl = API_URL) {
    this.apiUrl = apiUrl;
    this.client = axios.create({
      baseURL: apiUrl,
      timeout: 10000
    });
  }

  async getAllProducts() {
    /**
     * Fetch all products with pagination
     * Note: Shopify limits to 250 per request
     */
    let allProducts = [];
    let hasMore = true;
    let first = 250;

    while (hasMore) {
      const response = await this.client.get('/api/products', {
        params: { first }
      });

      if (!response.data.success) {
        throw new Error(response.data.error || 'Failed to fetch products');
      }

      const products = response.data.data.products.edges.map(edge => edge.node);
      allProducts = allProducts.concat(products);

      // Check if there are more pages
      hasMore = response.data.data.products.pageInfo.hasNextPage;

      // In real implementation, use cursor-based pagination
      // For now, we'll stop after first batch
      hasMore = false;
    }

    return allProducts;
  }

  async syncProductsToDatabase(database) {
    /**
     * Sync Shopify products to local database
     */
    console.log('Starting product sync...');

    const products = await this.getAllProducts();
    console.log(`Fetched ${products.length} products from Shopify`);

    for (const product of products) {
      try {
        // Transform Shopify data to your database format
        const dbProduct = {
          shopify_id: product.id,
          title: product.title,
          description: product.description,
          handle: product.handle,
          vendor: product.vendor,
          product_type: product.productType,
          status: product.status,
          created_at: product.createdAt,
          updated_at: product.updatedAt,
          tags: product.tags,
          variants: this.transformVariants(product.variants),
          images: this.transformImages(product.images)
        };

        // Upsert to database
        await database.upsertProduct(dbProduct);
        console.log(`✓ Synced: ${product.title}`);
      } catch (error) {
        console.error(`✗ Failed to sync: ${product.title}`, error.message);
      }
    }

    console.log('Product sync completed!');
  }

  transformVariants(variants) {
    return variants.edges.map(edge => ({
      shopify_id: edge.node.id,
      title: edge.node.title,
      sku: edge.node.sku,
      price: edge.node.price,
      inventory_quantity: edge.node.inventoryQuantity
    }));
  }

  transformImages(images) {
    return images.edges.map(edge => ({
      url: edge.node.url,
      alt_text: edge.node.altText
    }));
  }

  async syncSingleProduct(productId) {
    /**
     * Sync a single product (useful for webhooks)
     */
    const response = await this.client.get(`/api/products/${productId}`);

    if (!response.data.success) {
      throw new Error(response.data.error || 'Failed to fetch product');
    }

    return response.data.data.product;
  }
}

// Usage
const sync = new ProductSync();

// Mock database interface
const mockDatabase = {
  async upsertProduct(product) {
    // In real implementation: INSERT ON CONFLICT UPDATE
    console.log(`Upserting product: ${product.title}`);
  }
};

// Run sync
sync.syncProductsToDatabase(mockDatabase)
  .then(() => console.log('Sync complete'))
  .catch(error => console.error('Sync failed:', error));
```

## Use Case 5: Customer Data Export

### Scenario
Marketing team needs customer list for email campaigns.

### Example Workflow

**1. Get all customers:**

```bash
curl "$API_URL/api/customers?first=250"
```

**2. Search for VIP customers:**

```bash
curl "$API_URL/api/customers/search?q=tag:vip"
```

**3. Get customer with order history:**

```bash
curl "$API_URL/api/customers/12345678"
```

### CSV Export Script

```python
# export_customers.py

import requests
import csv
from datetime import datetime

API_URL = 'https://your-app-name.up.railway.app'

def export_customers_to_csv(filename='customers_export.csv'):
    """Export all customers to CSV file"""

    # Fetch customers
    response = requests.get(f"{API_URL}/api/customers", params={'first': 250})
    data = response.json()

    if not data['success']:
        print(f"Error: {data.get('error')}")
        return

    customers = data['data']['customers']['edges']

    # Write to CSV
    with open(filename, 'w', newline='', encoding='utf-8') as csvfile:
        fieldnames = [
            'Email', 'First Name', 'Last Name', 'Phone',
            'Orders Count', 'Total Spent', 'Tags',
            'Created At', 'Status'
        ]
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
        writer.writeheader()

        for edge in customers:
            customer = edge['node']
            writer.writerow({
                'Email': customer.get('email', ''),
                'First Name': customer.get('firstName', ''),
                'Last Name': customer.get('lastName', ''),
                'Phone': customer.get('phone', ''),
                'Orders Count': customer.get('ordersCount', 0),
                'Total Spent': customer.get('totalSpent', '0'),
                'Tags': ', '.join(customer.get('tags', [])),
                'Created At': customer.get('createdAt', ''),
                'Status': customer.get('state', '')
            })

    print(f"Exported {len(customers)} customers to {filename}")

# Usage
export_customers_to_csv('hearnshobbies_customers.csv')
```

## Testing with Postman

### Import as Postman Collection

1. Create new collection: "Shopify Data API"
2. Add environment variable: `base_url` = `https://your-app-name.up.railway.app`

### Example Requests

**Health Check:**
```
GET {{base_url}}/api/health
```

**Get Products:**
```
GET {{base_url}}/api/products?first=10
```

**Search Orders:**
```
GET {{base_url}}/api/orders/search?q=email:{{customer_email}}
```

Save responses as examples for documentation.

## Rate Limiting Considerations

The API implements automatic rate limiting:
- Max 100 points per request (Basic Shopify plan)
- Refills at 50 points/second
- Automatically queues requests when limit reached

**Best Practices:**
1. Batch requests when possible
2. Cache responses for frequently accessed data
3. Implement exponential backoff on errors
4. Monitor rate limit headers (future feature)

## Error Handling

All endpoints return this format:

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
  "message": "Failed to fetch products",
  "error": "Shopify API error details"
}
```

**Example Error Handling (JavaScript):**
```javascript
async function fetchProducts() {
  try {
    const response = await fetch(`${API_URL}/api/products?first=10`);
    const data = await response.json();

    if (!data.success) {
      console.error('API Error:', data.error);
      // Handle error appropriately
      return null;
    }

    return data.data.products;
  } catch (error) {
    console.error('Network Error:', error);
    return null;
  }
}
```

## Next Steps

1. **Deploy to Railway** (see DEPLOYMENT_GUIDE.md)
2. **Replace `$API_URL`** in examples with your actual URL
3. **Test all examples** with real data
4. **Build your application** using these patterns

## Additional Resources

- **Complete API Reference:** `docs/API_REFERENCE.md`
- **Add Custom Endpoints:** `docs/ADDING_FUNCTIONS.md`
- **Troubleshooting:** `docs/TROUBLESHOOTING.md`

---

These examples provide a foundation for building customer service tools, inventory management systems, analytics dashboards, and more using your Shopify Data API.
