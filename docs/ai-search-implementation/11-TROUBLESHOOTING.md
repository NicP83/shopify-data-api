# Troubleshooting Guide
## Common Issues and Solutions

---

## OAuth and Installation Issues

### Issue: OAuth Redirect Loop

**Symptoms:**
- After clicking "Install app", redirects back to install page
- Never completes OAuth flow
- HMAC verification fails repeatedly

**Possible Causes:**
1. Redirect URI mismatch
2. Nonce not persisted in session
3. HMAC signature calculation incorrect

**Solutions:**

**1. Verify Redirect URI:**
```bash
# Check environment variable
echo $SHOPIFY_APP_REDIRECT_URI

# Should match Shopify Partner Dashboard exactly:
# https://your-app.railway.app/shopify/callback

# Update in Shopify Partner Dashboard → Apps → Your App → App setup
```

**2. Check Session Configuration:**
```yaml
# application.yml
spring:
  session:
    store-type: jdbc  # For production
    # OR
    store-type: redis  # If using Redis

# Ensure session cookies work across redirects
server:
  servlet:
    session:
      timeout: 30m
      cookie:
        http-only: true
        secure: true  # Production only
```

**3. Debug HMAC Verification:**
```java
// Add logging to ShopifyOAuthService
logger.debug("Query string: {}", queryString);
logger.debug("Expected HMAC: {}", hmac);
logger.debug("Calculated HMAC: {}", calculatedHmac);
logger.debug("API Secret (first 5 chars): {}", apiSecret.substring(0, 5));
```

---

### Issue: "Shop not found" After Installation

**Symptoms:**
- OAuth completes successfully
- Redirects to admin dashboard
- Error: "Shop not found or inactive"

**Possible Causes:**
1. Shop not saved to database
2. Database transaction failed
3. Shopify API call failed

**Solutions:**

**1. Check Database:**
```sql
-- See if shop was saved
SELECT * FROM shopify_shops WHERE shop_domain = 'your-shop.myshopify.com';

-- If not found, check for errors in insert
SELECT * FROM flyway_schema_history WHERE success = false;
```

**2. Check Logs:**
```bash
# Railway logs
railway logs

# Look for errors like:
# "Failed to save shop"
# "Database connection timeout"
# "Shopify API error"
```

**3. Manual Shop Insert (Temporary Fix):**
```sql
INSERT INTO shopify_shops (shop_domain, shop_name, access_token, ai_enabled, installed_at)
VALUES ('your-shop.myshopify.com', 'Your Shop', 'shpat_xxxxx', true, CURRENT_TIMESTAMP);
```

---

### Issue: "Invalid HMAC Signature"

**Symptoms:**
- OAuth callback returns 401 or 400 error
- Message: "HMAC verification failed"

**Possible Causes:**
1. API secret mismatch
2. Query parameters not properly formatted
3. HMAC calculation algorithm incorrect

**Solutions:**

**1. Verify API Secret:**
```bash
# In Railway dashboard, check:
echo $SHOPIFY_APP_API_SECRET

# Compare with Shopify Partner Dashboard → Apps → Your App
# Should match exactly (no extra spaces)
```

**2. Test HMAC Calculation:**
```java
// Test with known values
String testMessage = "code=abc123&shop=test.myshopify.com&state=xyz&timestamp=1234567890";
String testSecret = "your_api_secret";
String calculatedHmac = new HmacUtils("HmacSHA256", testSecret).hmacHex(testMessage);

logger.info("Test HMAC: {}", calculatedHmac);
```

**3. Check Query Parameter Order:**
```java
// Ensure parameters are alphabetically sorted (Shopify requirement)
params.entrySet().stream()
    .filter(entry -> !entry.getKey().equals("hmac"))
    .sorted(Map.Entry.comparingByKey())  // IMPORTANT
    .forEach(entry -> { /* build query string */ });
```

---

## API and Chat Issues

### Issue: Chat API Returns 404

**Symptoms:**
- Frontend sends message
- Response: 404 Not Found
- Message: "Shop not found or inactive"

**Solutions:**

**1. Verify Shop Parameter:**
```javascript
// Frontend - check API call
const response = await fetch(
  `${API_URL}/api/shopify/chat/message?shop=${Shopify.shop}`,  // Shopify.shop should be defined
  { method: 'POST', body: JSON.stringify(data) }
)

// Check browser console
console.log('Shop domain:', Shopify.shop)  // Should be: your-shop.myshopify.com
```

**2. Check Shop in Database:**
```sql
SELECT shop_domain, is_active, uninstalled_at
FROM shopify_shops
WHERE shop_domain = 'your-shop.myshopify.com';

-- If is_active = false or uninstalled_at is not null:
UPDATE shopify_shops
SET is_active = true, uninstalled_at = null
WHERE shop_domain = 'your-shop.myshopify.com';
```

---

### Issue: Chat API Timeout (> 30 seconds)

**Symptoms:**
- API call takes > 30 seconds
- Eventually returns 503 Service Unavailable
- Claude API slow or timeout

**Solutions:**

**1. Reduce Max Tokens:**
```java
// Lower max tokens to reduce response time
chatAgentService.setMaxTokens(2048);  // Instead of 8192
```

**2. Check Shopify API Performance:**
```java
// Add timing logs
long startTime = System.currentTimeMillis();
List<Product> products = productService.searchProducts(query);
long elapsedTime = System.currentTimeMillis() - startTime;
logger.info("Product search took: {} ms", elapsedTime);

// If > 5 seconds, Shopify API is slow
```

**3. Implement Timeout Configuration:**
```yaml
spring:
  webflux:
    timeout: 25000  # 25 seconds (before Railway 30s timeout)
```

---

### Issue: Products Not Appearing in Chat Response

**Symptoms:**
- Chat API returns successfully
- Response includes text but no products
- `products` array is empty

**Solutions:**

**1. Check Claude Tool Use:**
```java
// Verify search_products tool is defined
logger.debug("Available tools: {}", chatAgentService.getAvailableTools());

// Ensure tool definition includes:
// - name: "search_products"
// - input_schema with "query" parameter
```

**2. Check Product Search Results:**
```java
// Add logging in ProductService
List<Product> results = shopifyService.searchProducts(query);
logger.info("Found {} products for query: {}", results.size(), query);

// If 0 products:
// - Shopify might not have matching products
// - Search query too specific
// - Shopify API rate limit reached
```

**3. Test Direct Product Search:**
```bash
curl "http://localhost:8080/api/products/search" \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"query": "gundam", "limit": 10}'

# Should return products
```

---

## Theme Extension Issues

### Issue: AI Button Not Appearing

**Symptoms:**
- Visit storefront
- No AI button (🤖) next to search bar
- Console shows no errors

**Solutions:**

**1. Verify Extension Enabled:**
- Go to Shopify Admin → Online Store → Themes
- Click "Customize"
- Open "App embeds" (left sidebar, bottom)
- Check "AI Search Assistant" is toggled ON
- Click "Save"

**2. Check Extension Deployment:**
```bash
cd extensions/search-enhancer
shopify app deploy

# Verify no errors during deployment
```

**3. Check JavaScript Loading:**
```javascript
// Open browser console
// Type:
window.HearnsAI

// Should return object or undefined (not error)

// Check network tab for:
// - search-enhancer.js loaded (Status: 200)
// - ai-chat-styles.css loaded (Status: 200)
```

**4. Check Search Bar Selector:**
```javascript
// In search-enhancer.js, add debug logging
console.log('[HearnsAI] Searching for search bar...')

const searchBar = findSearchBar()
console.log('[HearnsAI] Search bar found:', searchBar)

// If null, search bar selector doesn't match your theme
// Update selectors in findSearchBar() function
```

---

### Issue: AI Modal Not Opening

**Symptoms:**
- AI button appears
- Click button → nothing happens
- No errors in console

**Solutions:**

**1. Check Event Listener:**
```javascript
// Add debug logging
function openChatModal(e) {
  console.log('[HearnsAI] Opening modal...')
  e.preventDefault()

  const modal = document.getElementById('hearns-ai-modal')
  console.log('[HearnsAI] Modal element:', modal)

  modal.classList.add('hearns-ai-modal-open')
  console.log('[HearnsAI] Modal should be visible')
}
```

**2. Check CSS Class:**
```css
/* Verify class is defined */
.hearns-ai-modal-open {
  display: flex !important;  /* Add !important if theme has conflicting styles */
}
```

**3. Check Z-Index:**
```css
/* If modal appears behind content */
.hearns-ai-modal {
  z-index: 99999 !important;
}
```

---

### Issue: CORS Error When Sending Message

**Symptoms:**
- Modal opens successfully
- Type message and click send
- Console error: "CORS policy: No 'Access-Control-Allow-Origin' header"

**Solutions:**

**1. Verify CORS Configuration:**
```java
// WebConfig.java
.allowedOrigins(
    "https://hearnshobbies.com",           // Production domain
    "https://*.myshopify.com",             // Shopify admin
    "http://localhost:5173"                // Development
)
```

**2. Check Railway Environment:**
```bash
# Verify CORS_ALLOWED_ORIGINS includes your shop
echo $CORS_ALLOWED_ORIGINS

# Should include: https://hearnshobbies.com
```

**3. Test CORS Preflight:**
```bash
curl -H "Origin: https://hearnshobbies.com" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type" \
  -X OPTIONS \
  "https://your-app.railway.app/api/shopify/chat/message"

# Should return Access-Control-Allow-Origin header
```

---

## Database Issues

### Issue: Migration Failed

**Symptoms:**
- App fails to start
- Error: "Flyway migration failed"
- Migration stuck in "pending" state

**Solutions:**

**1. Check Migration Status:**
```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC;

-- Look for success = false
```

**2. Repair Failed Migration:**
```bash
# Using Flyway CLI
mvn flyway:repair

# Or manually:
DELETE FROM flyway_schema_history WHERE success = false;

# Then re-run migrations
mvn flyway:migrate
```

**3. Baseline Existing Database:**
```yaml
# application.yml
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 0
```

---

### Issue: Database Connection Timeout

**Symptoms:**
- App starts but can't connect to database
- Error: "Connection timeout"
- Railway database unreachable

**Solutions:**

**1. Verify DATABASE_URL:**
```bash
# Railway dashboard → Variables
echo $DATABASE_URL

# Format: postgresql://user:password@host:port/database
# Ensure no extra spaces or newlines
```

**2. Check Database Status:**
- Railway Dashboard → PostgreSQL service
- Status should be "Active"
- If stopped, restart

**3. Increase Connection Timeout:**
```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 60000  # 60 seconds (from 30s)
```

---

## Performance Issues

### Issue: Slow API Response Times

**Symptoms:**
- Chat API takes > 10 seconds
- Admin search slow
- High CPU/memory usage

**Solutions:**

**1. Check Shopify API Rate Limits:**
```java
// Add rate limit logging
@RestController
public class ProductController {

    @GetMapping("/search")
    public ResponseEntity<?> search(HttpServletRequest request) {
        String rateLimitHeader = response.getHeader("X-Shopify-Shop-Api-Call-Limit");
        logger.info("Shopify rate limit: {}", rateLimitHeader);  // e.g., "32/40"

        // If approaching limit (e.g., 38/40), slow down
    }
}
```

**2. Implement Caching:**
```java
@Cacheable(value = "products", key = "#query")
public List<Product> searchProducts(String query) {
    // Cache product search results for 5 minutes
}
```

**3. Optimize Claude Requests:**
```java
// Reduce max tokens
chatAgentService.setMaxTokens(2048);

// Increase temperature for faster sampling
chatAgentService.setTemperature(0.9);
```

---

## Railway Deployment Issues

### Issue: Build Fails on Railway

**Symptoms:**
- Push to GitHub
- Railway build fails
- Error in build logs

**Solutions:**

**1. Check Build Logs:**
```bash
# Railway CLI
railway logs --deployment

# Look for Maven errors like:
# - Compilation errors
# - Missing dependencies
# - Test failures
```

**2. Skip Tests in Build:**
```bash
# Update build command in Railway
mvn clean package -DskipTests
```

**3. Verify Java Version:**
```json
// package.json or nixpacks.toml
{
  "engines": {
    "java": "17"
  }
}
```

---

### Issue: App Crashes on Startup

**Symptoms:**
- Deployment succeeds
- App starts then immediately crashes
- Railway shows "Crashed"

**Solutions:**

**1. Check Startup Logs:**
```bash
railway logs

# Common errors:
# - Missing environment variable
# - Database connection failed
# - Port binding error
```

**2. Verify Environment Variables:**
```bash
# Railway dashboard → Variables
# Ensure all required variables set:
# - DATABASE_URL
# - SHOPIFY_APP_API_KEY
# - SHOPIFY_APP_API_SECRET
# - ANTHROPIC_API_KEY
```

**3. Check Health Endpoint:**
```bash
curl https://your-app.railway.app/health

# If 503 or timeout, app isn't starting
```

---

## Common Error Messages

### "Shop not found or inactive"
- Shop not in database → Run OAuth install again
- `is_active = false` → Update database: `SET is_active = true`

### "AI assistant is disabled for this shop"
- `ai_enabled = false` in database
- Update: `UPDATE shopify_shops SET ai_enabled = true WHERE shop_domain = '...'`

### "HMAC verification failed"
- API secret mismatch → Verify SHOPIFY_APP_API_SECRET
- Query parameters incorrect → Check HMAC calculation logic

### "Rate limit exceeded"
- Too many requests → Wait for rate limit window reset
- Implement rate limiting on frontend

### "Claude API timeout"
- Request too large → Reduce max_tokens
- Claude API slow → Retry with exponential backoff

---

## Getting Help

### Check Logs

**Backend (Railway):**
```bash
railway logs --tail
```

**Frontend (Browser):**
- Open DevTools (F12)
- Console tab for JavaScript errors
- Network tab for API requests

**Database:**
```sql
-- Check recent errors
SELECT * FROM error_logs ORDER BY created_at DESC LIMIT 10;
```

### Debugging Steps

1. **Isolate the problem:**
   - Does it happen in development or production?
   - Is it consistent or intermittent?
   - What changed recently?

2. **Check the docs:**
   - Review relevant phase guide (04-09)
   - Check configuration reference (10)

3. **Test components individually:**
   - Test OAuth flow separately
   - Test chat API with Postman
   - Test theme extension in isolation

4. **Enable debug logging:**
```yaml
logging:
  level:
    com.shopify.api: DEBUG
```

---

## Related Documentation

- **10-CONFIGURATION.md** - Configuration reference
- **09-PHASE6-DEPLOYMENT.md** - Deployment guide
- **01-ARCHITECTURE.md** - System architecture

---

*Last Updated: 2025-10-30*
