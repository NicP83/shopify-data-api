# Troubleshooting Guide

Common issues and solutions for the Shopify Data API.

## Connection Issues

### Issue: "Shopify connection failed"

**Symptoms:**
- `/api/status` shows `"shopify_connected": false`
- Errors mentioning authentication

**Common Causes:**
1. Invalid or expired access token
2. Incorrect shop URL
3. Missing API scopes

**Solutions:**

**Check 1: Verify Environment Variables**
```bash
# Local (.env file)
SHOPIFY_SHOP_URL=your-store.myshopify.com  # No https://, just domain
SHOPIFY_ACCESS_TOKEN=shpat_xxxxx...         # Must start with shpat_
```

**Check 2: Test Credentials**
```bash
curl -X POST https://your-store.myshopify.com/admin/api/2025-01/graphql.json \
  -H "X-Shopify-Access-Token: shpat_xxxxx" \
  -H "Content-Type: application/json" \
  -d '{"query": "{shop{name}}"}'
```

If this fails, regenerate your access token in Shopify.

**Check 3: Verify API Scopes**
- Go to Shopify Admin → Apps → Your App
- Ensure required scopes are enabled
- Reinstall the app after changing scopes

---

## Build & Deployment Issues

### Issue: Maven build fails

**Error:** `[ERROR] Failed to execute goal`

**Solutions:**

**Java Version Mismatch:**
```bash
# Check Java version
java -version  # Should be 17 or higher

# Set JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

**Dependency Issues:**
```bash
# Clear Maven cache
mvn clean
rm -rf ~/.m2/repository

# Rebuild
mvn clean install -U
```

**Lombok Issues:**
```bash
# IntelliJ: Enable annotation processing
# Settings → Build → Compiler → Annotation Processors → Enable
```

### Issue: Application won't start locally

**Error:** `Port 8080 already in use`

**Solution:**
```bash
# Find and kill process
lsof -ti:8080 | xargs kill -9

# Or change port in .env
PORT=8081
```

**Error:** `Database connection failed`

**Solution:**
```bash
# Option 1: Install PostgreSQL locally
brew install postgresql@15
brew services start postgresql@15
createdb shopify_data

# Option 2: Use Railway database
# Get DATABASE_URL from Railway and add to .env
```

### Issue: Railway deployment fails

**Error:** `Build failed - Cannot find JDK`

**Solution:**
Create `nixpacks.toml`:
```toml
[phases.setup]
nixPkgs = ["jdk17"]
```

**Error:** `Application crashes on startup`

**Solution:**
1. Check Railway logs for errors
2. Verify all environment variables are set:
   ```
   SHOPIFY_SHOP_URL
   SHOPIFY_ACCESS_TOKEN
   SHOPIFY_API_VERSION
   DATABASE_URL
   ```
3. Check DATABASE_URL format:
   ```
   postgresql://user:pass@host:port/dbname
   ```

---

## API Errors

### Issue: "API rate limit exceeded"

**Symptoms:**
- 429 errors in logs
- Slow response times

**Solution:**

The rate limiter should handle this automatically. If still occurring:

1. **Lower SHOPIFY_MAX_POINTS** in config:
   ```bash
   # .env
   SHOPIFY_MAX_POINTS=50  # Reduce from 100
   ```

2. **Check query complexity:**
   - Reduce number of fields requested
   - Limit nested queries
   - Use pagination

3. **Monitor rate limit headers:**
   ```java
   logger.debug("Rate limiter available: {}", rateLimiter.getAvailablePoints());
   ```

### Issue: "GraphQL query returns errors"

**Error:** `Field 'xyz' doesn't exist on type 'Product'`

**Solutions:**

1. **Verify field names:**
   - Check Shopify GraphQL docs
   - Field names are case-sensitive

2. **Test query in GraphQL Explorer:**
   - https://shopify.dev/docs/apps/tools/graphiql-admin-api
   - Paste your query
   - Fix syntax errors

3. **Check API version:**
   - Fields change between versions
   - Update SHOPIFY_API_VERSION if needed

### Issue: "Missing required access scopes"

**Error:** `Access denied for field 'customers'`

**Solution:**

1. Go to Shopify Admin → Apps → Your App
2. Click "Configure"
3. Add missing scope (e.g., `read_customers`)
4. Click "Save"
5. Reinstall the app
6. Get new access token

---

## Data Issues

### Issue: Empty response / No data returned

**Symptoms:**
- API returns success but empty data
- `edges: []` in response

**Solutions:**

1. **Check if data exists in Shopify:**
   - Log into Shopify admin
   - Verify products/orders/customers exist

2. **Add test data:**
   ```bash
   # Using Shopify CLI
   shopify populate products
   shopify populate customers
   shopify populate orders
   ```

3. **Check query filters:**
   - Remove search filters
   - Test with basic query first

### Issue: "Cannot find product/order with ID"

**Error:** 404 or null response

**Solutions:**

1. **Verify ID format:**
   ```java
   // Correct formats:
   "123456"  // API auto-converts
   "gid://shopify/Product/123456"  // Full GID

   // Wrong format:
   "#1001"  // This is order name, not ID
   ```

2. **Get correct ID:**
   - List all resources first
   - Copy the `id` field from response

---

## Performance Issues

### Issue: Slow API responses

**Symptoms:**
- Requests take > 5 seconds
- Timeouts

**Solutions:**

1. **Reduce query size:**
   ```java
   // Bad: Requesting too much
   products(first: 250) { ... 50 fields ... }

   // Good: Only what you need
   products(first: 50) { ... 10 fields ... }
   ```

2. **Use pagination:**
   ```bash
   # Instead of:
   /api/products?first=250

   # Use:
   /api/products?first=50  # Make multiple requests
   ```

3. **Check network:**
   - Test Railway deployment location
   - Use CDN if serving to global users

### Issue: High query costs

**Symptoms:**
- Rate limit errors
- `actualQueryCost` is high (>500)

**Solutions:**

1. **Simplify queries:**
   - Remove deep nesting
   - Request fewer relationships
   - Limit variant/image counts

2. **Example optimization:**
   ```java
   // Before: Cost ~200
   products {
     variants(first: 100) {
       inventoryItem {
         inventoryLevels(first: 10) { ... }
       }
     }
   }

   // After: Cost ~50
   products {
     variants(first: 10) { ... }
   }
   ```

---

## Common Java/Spring Boot Errors

### Issue: NullPointerException

**Error:** `java.lang.NullPointerException at ...`

**Solutions:**

1. **Check response parsing:**
   ```java
   // Add null checks
   if (response.getData() != null && response.getData().get("products") != null) {
       // Process data
   }
   ```

2. **Log raw response:**
   ```java
   logger.debug("Raw response: {}", response);
   ```

### Issue: JSON parsing error

**Error:** `Cannot deserialize value of type...`

**Solutions:**

1. **Check response structure:**
   - Log the actual JSON received
   - Match it to your model

2. **Use Map for flexibility:**
   ```java
   // Instead of strict model
   Map<String, Object> data = response.getData();
   ```

### Issue: Bean creation error

**Error:** `Error creating bean with name...`

**Solutions:**

1. **Check component scanning:**
   ```java
   @SpringBootApplication
   public class ShopifyDataApiApplication { }
   ```

2. **Verify dependencies injected:**
   ```java
   @Service
   public class ProductService {
       private final ShopifyGraphQLClient client;

       // Constructor injection
       public ProductService(ShopifyGraphQLClient client) {
           this.client = client;
       }
   }
   ```

---

## Environment-Specific Issues

### Local works, Railway doesn't

**Common causes:**

1. **Environment variables not set on Railway**
   - Check Railway dashboard
   - Add all variables from .env

2. **Database connection string different**
   - Railway uses different DATABASE_URL
   - Don't copy local .env to Railway

3. **Port configuration**
   - Railway assigns PORT automatically
   - Don't hardcode port in code

### Railway works, local doesn't

**Common causes:**

1. **Missing local PostgreSQL**
   - Install PostgreSQL locally
   - Or point to Railway database (not recommended)

2. **Environment variables**
   - Ensure .env file exists
   - Check file is not in .gitignore

---

## Debugging Tips

### Enable Debug Logging

**application.yml:**
```yaml
logging:
  level:
    com.shopify.api: DEBUG
    org.springframework.web: DEBUG
```

### View Raw Requests/Responses

Add to `ShopifyGraphQLClient.java`:
```java
logger.debug("Request: {}", request);
logger.debug("Response: {}", response);
```

### Test GraphQL Queries Directly

Use curl:
```bash
curl -X POST https://your-store.myshopify.com/admin/api/2025-01/graphql.json \
  -H "X-Shopify-Access-Token: $SHOPIFY_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query": "{shop{name}}"}'
```

### Monitor Railway Logs

```bash
# Real-time logs
railway logs --tail

# Or in dashboard
Railway → Your Service → Logs
```

---

## Getting Help

### 1. Check Documentation
- [PROJECT_SETUP.md](./PROJECT_SETUP.md)
- [SHOPIFY_CONNECTION.md](./SHOPIFY_CONNECTION.md)
- [API_REFERENCE.md](./API_REFERENCE.md)

### 2. Shopify Resources
- Shopify API Docs: https://shopify.dev/docs/api/admin
- GraphQL Explorer: https://shopify.dev/docs/apps/tools/graphiql-admin-api
- Shopify Community: https://community.shopify.com/

### 3. Spring Boot Resources
- Spring Boot Docs: https://spring.io/projects/spring-boot
- Spring WebClient: https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html

### 4. Railway Resources
- Railway Docs: https://docs.railway.app/
- Railway Discord: https://discord.gg/railway

---

## Error Code Reference

| Error Code | Meaning | Solution |
|------------|---------|----------|
| 400 | Bad Request | Check request parameters |
| 401 | Unauthorized | Verify access token |
| 403 | Forbidden | Check API scopes |
| 404 | Not Found | Verify resource ID |
| 429 | Rate Limited | Reduce request rate |
| 500 | Server Error | Check application logs |
| 503 | Service Unavailable | Shopify API down or connection issue |
