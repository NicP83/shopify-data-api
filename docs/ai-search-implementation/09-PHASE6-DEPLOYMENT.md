# Phase 6: Production Deployment
## Going Live on Hearn's Hobbies

**Duration:** 0.5-1 day (4-7 hours)

---

## Overview

Phase 6 deploys the AI-enhanced search system to production:

1. Environment configuration
2. Database migration on production
3. Backend deployment to Railway
4. Shopify app installation
5. Theme extension deployment
6. Production testing
7. Monitoring setup
8. Go-live checklist

---

## Pre-Deployment Checklist

### Backend Ready
- [ ] All Phase 1-5 code committed
- [ ] Tests passing locally
- [ ] Environment variables documented
- [ ] Database migrations tested
- [ ] CORS configured for hearnshobbies.com
- [ ] Error logging configured
- [ ] API keys secured

### Frontend Ready
- [ ] Admin dashboard built (`npm run build:deploy`)
- [ ] Theme extension tested on development store
- [ ] API endpoint configured for production
- [ ] Mobile responsive verified
- [ ] Cross-browser testing complete

### Shopify Ready
- [ ] Custom app created in Partner Dashboard
- [ ] OAuth credentials obtained
- [ ] Scopes configured (read_products, etc.)
- [ ] Redirect URI set
- [ ] Development store tested

---

## Step 1: Environment Configuration

### Production Environment Variables

**Railway Dashboard** → Your Project → Variables

```bash
# Database (Railway PostgreSQL)
DATABASE_URL=postgresql://user:password@host:port/database
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=<generated_password>

# Shopify App Credentials
SHOPIFY_APP_API_KEY=<from_partner_dashboard>
SHOPIFY_APP_API_SECRET=<from_partner_dashboard>
SHOPIFY_APP_REDIRECT_URI=https://your-app.railway.app/shopify/callback
SHOPIFY_SHOP_URL=hearnshobbies.myshopify.com
SHOPIFY_ACCESS_TOKEN=<will_be_set_after_oauth>

# Anthropic API
ANTHROPIC_API_KEY=sk-ant-api03-<your_key>

# Application Settings
SPRING_PROFILES_ACTIVE=production
PORT=8080
SERVER_PORT=8080

# CORS Settings
CORS_ALLOWED_ORIGINS=https://hearnshobbies.com,https://hearnshobbies.myshopify.com

# Logging
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_SHOPIFY_API=DEBUG
```

### Update application.yml for Production

**File:** `/src/main/resources/application-production.yml`

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000

  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: false

  flyway:
    enabled: true
    baseline-on-migrate: true
    validate-on-migrate: true

logging:
  level:
    root: INFO
    com.shopify.api: DEBUG
    org.springframework.web: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %logger{36} - %msg%n"

server:
  port: ${PORT:8080}
  compression:
    enabled: true
    mime-types: application/json,application/xml,text/html,text/xml,text/plain
```

---

## Step 2: Database Migration

### Backup Existing Database

```bash
# Connect to Railway PostgreSQL
railway connect postgres

# Create backup
pg_dump -h <host> -U <user> -d <database> > backup_$(date +%Y%m%d).sql
```

### Run Flyway Migrations

**Option 1: Automatic (on deployment)**
- Migrations run automatically when Spring Boot starts
- Set `spring.flyway.enabled=true`

**Option 2: Manual (safer for production)**

```bash
# Run migrations manually first
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://<host>:<port>/<database> \
  -Dflyway.user=<user> \
  -Dflyway.password=<password>

# Verify migrations
mvn flyway:info
```

### Verify Database Schema

```sql
-- Connect to production database
psql -h <host> -U <user> -d <database>

-- Check tables exist
\dt

-- Verify shopify_shops table
\d shopify_shops

-- Check migration history
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

---

## Step 3: Backend Deployment (Railway)

### Option A: Deploy via GitHub (Recommended)

1. **Push code to GitHub:**
```bash
git add .
git commit -m "Production deployment: AI-enhanced search v1.0"
git push origin main
```

2. **Railway auto-deploys from GitHub**
   - Railway detects changes
   - Builds Maven project
   - Runs database migrations
   - Starts Spring Boot app

3. **Monitor deployment logs:**
   - Railway Dashboard → Deployments
   - Watch for "Started ShopifyDataApiApplication"

### Option B: Deploy via Railway CLI

```bash
# Install Railway CLI
npm install -g @railway/cli

# Login
railway login

# Link project
railway link

# Deploy
railway up
```

### Verify Deployment

**Check health endpoint:**
```bash
curl https://your-app.railway.app/health

# Expected:
{
  "status": "UP",
  "timestamp": "2025-10-30T15:30:00Z",
  "services": {
    "database": "UP",
    "shopify": "UP",
    "claude": "UP"
  }
}
```

**Check API version:**
```bash
curl https://your-app.railway.app/api/version

# Expected:
{
  "version": "1.0.0",
  "buildDate": "2025-10-30",
  "commit": "abc123",
  "environment": "production"
}
```

---

## Step 4: Install Shopify App

### Create Custom App in Shopify Admin

1. **Go to Shopify Admin:**
   ```
   https://hearnshobbies.myshopify.com/admin/settings/apps/development
   ```

2. **Create custom app:**
   - Name: "AI Search Assistant"
   - App URL: `https://your-app.railway.app`
   - Allowed redirection URL(s): `https://your-app.railway.app/shopify/callback`

3. **Configure API scopes:**
   - `read_products`
   - `write_script_tags` (for theme extension)

4. **Get API credentials:**
   - Copy API key
   - Copy API secret key
   - Save to Railway environment variables

### Install App via OAuth

1. **Open install URL:**
   ```
   https://your-app.railway.app/shopify/install?shop=hearnshobbies.myshopify.com
   ```

2. **Follow OAuth flow:**
   - Redirects to Shopify
   - Click "Install app"
   - Redirects back to your app
   - Shop saved to database

3. **Verify installation:**
```sql
SELECT * FROM shopify_shops WHERE shop_domain = 'hearnshobbies.myshopify.com';
```

---

## Step 5: Deploy Theme Extension

### Build Theme Extension

```bash
cd extensions/search-enhancer

# Update API endpoint in shopify.extension.toml
# Change: default = "https://your-app.railway.app"

# Deploy extension
shopify app deploy
```

### Enable Extension in Theme

1. **Go to Shopify Admin:**
   ```
   https://hearnshobbies.myshopify.com/admin/themes
   ```

2. **Click "Customize" on active theme**

3. **Open "App embeds"** (left sidebar, bottom)

4. **Find "AI Search Assistant"**

5. **Toggle ON**

6. **Save**

### Verify Extension Live

1. **Visit hearnshobbies.com**
2. **Look for AI button (🤖) next to search bar**
3. **Click button → modal should open**
4. **Send test message**
5. **Verify product cards appear**

---

## Step 6: Production Testing

### Smoke Tests

**Test 1: AI Chat Works**
```bash
curl -X POST "https://your-app.railway.app/api/shopify/chat/message?shop=hearnshobbies.myshopify.com" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Show me popular Gundam kits",
    "conversationHistory": []
  }'
```

**Test 2: Storefront Integration**
- Visit https://hearnshobbies.com
- Click AI button
- Send message
- Verify products appear

**Test 3: Admin Product Search**
- Login to admin dashboard
- Navigate to `/product-search`
- Test both modes

### Performance Testing

**Response time benchmarks:**
- Chat API: < 5 seconds
- Product search: < 1 second
- Modal open: < 300ms

**Load testing (optional):**
```bash
ab -n 100 -c 10 -p test-message.json -T application/json \
  "https://your-app.railway.app/api/shopify/chat/message?shop=hearnshobbies.myshopify.com"
```

### User Acceptance Testing

**Internal team testing:**
- [ ] Customer service team tests AI assistant
- [ ] Management approves product recommendations
- [ ] Marketing reviews messaging
- [ ] IT verifies technical implementation

---

## Step 7: Monitoring Setup

### Railway Monitoring

**Enable metrics in Railway Dashboard:**
- CPU usage
- Memory usage
- Response time
- Error rate

**Set up alerts:**
- Alert if CPU > 80% for 5 minutes
- Alert if memory > 90%
- Alert if error rate > 1%

### Application Logging

**Update logging configuration:**

```yaml
logging:
  level:
    root: INFO
    com.shopify.api: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: /var/log/shopify-api.log
    max-size: 10MB
    max-history: 30
```

### Health Checks

**Configure Railway health checks:**
- Health check URL: `/health`
- Interval: 30 seconds
- Timeout: 10 seconds
- Unhealthy threshold: 3 consecutive failures

### Error Tracking (Optional)

**Integrate Sentry:**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.sentry</groupId>
    <artifactId>sentry-spring-boot-starter</artifactId>
    <version>6.34.0</version>
</dependency>
```

```yaml
# application.yml
sentry:
  dsn: ${SENTRY_DSN}
  environment: production
  traces-sample-rate: 1.0
```

---

## Step 8: Go-Live Checklist

### Pre-Launch
- [ ] All environment variables set
- [ ] Database migrations completed
- [ ] Backend deployed and healthy
- [ ] Shopify app installed
- [ ] Theme extension enabled
- [ ] SSL certificates valid
- [ ] CORS configured correctly
- [ ] Error logging working
- [ ] Monitoring dashboards set up

### Launch
- [ ] Smoke tests passed
- [ ] Performance benchmarks met
- [ ] User acceptance testing completed
- [ ] Documentation updated
- [ ] Team trained on admin tools
- [ ] Support team ready for questions

### Post-Launch
- [ ] Monitor error rates (first 24 hours)
- [ ] Review response times
- [ ] Collect user feedback
- [ ] Check analytics (chat usage)
- [ ] Verify no production errors
- [ ] Review Shopify API usage

---

## Rollback Plan

### If Critical Issues Arise

**1. Disable Theme Extension (Fastest)**
- Go to Shopify Admin → Themes → Customize
- Open "App embeds"
- Toggle OFF "AI Search Assistant"
- Storefront AI button disappears immediately

**2. Rollback Backend Deployment**
```bash
# Railway CLI
railway rollback

# Or via Railway Dashboard → Deployments → Previous deployment → Redeploy
```

**3. Revert Database (Last Resort)**
```bash
# Restore from backup
psql -h <host> -U <user> -d <database> < backup_20251030.sql
```

---

## Post-Deployment Tasks

### Week 1: Monitoring

**Daily checks:**
- Review error logs
- Check response times
- Monitor Claude API usage
- Review Shopify API rate limits
- Collect user feedback

**Metrics to track:**
- Chat sessions per day
- Average messages per session
- Product clicks from AI recommendations
- Conversion rate (AI vs normal search)
- Average response time

### Week 2-4: Optimization

**Based on monitoring data:**
- Tune AI temperature if needed
- Adjust system prompt based on feedback
- Optimize slow queries
- Add caching if beneficial
- Scale resources if needed

---

## Success Metrics

### Technical Metrics
- 99.9% uptime
- < 5 second AI response time (p95)
- < 1% error rate
- Zero security incidents

### Business Metrics
- X chat sessions per day
- Y% of visitors use AI assistant
- Z% conversion rate from AI recommendations
- Positive customer feedback

---

## Support & Maintenance

### Daily Tasks
- Review error logs
- Check health dashboard
- Monitor API usage

### Weekly Tasks
- Review analytics
- Check for Shopify updates
- Review Claude API bills
- Team feedback session

### Monthly Tasks
- Performance optimization review
- Feature enhancement planning
- Security audit
- Documentation updates

---

## Congratulations! 🎉

You've successfully deployed the AI-enhanced search system for Hearn's Hobbies!

**What's next:**
- Monitor performance and gather feedback
- Iterate based on user behavior
- Plan Phase 2 features (voice search, image search, etc.)
- Scale as needed

---

*Last Updated: 2025-10-30*
*Documentation Complete*
*See: 10-CONFIGURATION.md, 11-TROUBLESHOOTING.md, 12-ANALYTICS.md*
