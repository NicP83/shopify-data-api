# Quick Start Guide

Get your Shopify Data API up and running in 15 minutes.

## Prerequisites

- Java 17+ installed
- Maven installed
- Shopify Partner account
- Text editor or IDE

## Step 1: Get Shopify Credentials (5 min)

1. Go to https://partners.shopify.com/ and create a Partner account
2. Create a development store
3. In your store admin:
   - Settings → Apps and sales channels → Develop apps
   - Allow custom app development
   - Create app named "Shopify Data API"
   - Configure Admin API scopes: `read_products`, `read_orders`, `read_customers`, `read_inventory`
   - Install app
   - Copy the Admin API access token

## Step 2: Configure the Project (2 min)

1. Copy environment variables:
   ```bash
   cp .env.example .env
   ```

2. Edit `.env` with your credentials:
   ```bash
   SHOPIFY_SHOP_URL=your-store.myshopify.com
   SHOPIFY_ACCESS_TOKEN=shpat_xxxxxxxxxxxxxxxxxxxxx
   SHOPIFY_API_VERSION=2025-01
   ```

## Step 3: Build and Run (3 min)

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

Wait for: `Shopify Data API Started Successfully!`

## Step 4: Test the API (5 min)

### Health Check
```bash
curl http://localhost:8080/api/health
```

### Test Shopify Connection
```bash
curl http://localhost:8080/api/status
```

Should show: `"shopify_connected": true`

### Fetch Products
```bash
curl http://localhost:8080/api/products?first=5
```

### Fetch Orders
```bash
curl http://localhost:8080/api/orders?first=5
```

### Fetch Customers
```bash
curl http://localhost:8080/api/customers?first=5
```

## Available Endpoints

- `GET /api/health` - Health check
- `GET /api/status` - System status
- `GET /api/products` - List products
- `GET /api/products/{id}` - Get product by ID
- `GET /api/products/search?q=keyword` - Search products
- `GET /api/orders` - List orders
- `GET /api/orders/{id}` - Get order by ID
- `GET /api/orders/search?q=email:...` - Search orders
- `GET /api/customers` - List customers
- `GET /api/customers/{id}` - Get customer by ID
- `GET /api/customers/search?q=email:...` - Search customers
- `GET /api/inventory` - Get inventory levels
- `GET /api/inventory/product/{id}` - Get inventory for product
- `GET /api/inventory/locations` - List inventory locations

## Deploy to Railway (Optional)

1. Push to GitHub:
   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git push origin main
   ```

2. Go to https://railway.com/
3. Deploy from GitHub repo
4. Add PostgreSQL database
5. Set environment variables (copy from .env)
6. Deploy!

Your API will be live at: `https://your-app.up.railway.app`

## Next Steps

- **Read full documentation:** [README.md](./README.md)
- **Shopify setup guide:** [docs/SHOPIFY_CONNECTION.md](./docs/SHOPIFY_CONNECTION.md)
- **API reference:** [docs/API_REFERENCE.md](./docs/API_REFERENCE.md)
- **Add custom functions:** [docs/ADDING_FUNCTIONS.md](./docs/ADDING_FUNCTIONS.md)
- **Deploy to Railway:** [docs/RAILWAY_DEPLOYMENT.md](./docs/RAILWAY_DEPLOYMENT.md)

## Troubleshooting

### Connection Failed
- Check `SHOPIFY_ACCESS_TOKEN` is correct
- Verify `SHOPIFY_SHOP_URL` format (no https://)
- Ensure API scopes are enabled

### Build Failed
- Check Java version: `java -version` (need 17+)
- Clear Maven cache: `mvn clean`
- Check `JAVA_HOME` is set

### Port Already in Use
- Change port in `.env`: `PORT=8081`
- Or kill process: `lsof -ti:8080 | xargs kill -9`

See [docs/TROUBLESHOOTING.md](./docs/TROUBLESHOOTING.md) for more help.

## Support

- Check documentation in `docs/` folder
- Review Shopify API docs: https://shopify.dev/docs/api
- Railway support: https://docs.railway.app/

Happy coding!
