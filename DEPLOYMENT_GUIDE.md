# Railway Deployment Guide

Complete step-by-step guide to deploy the Shopify Data API to Railway.

**Estimated Time:** 20-30 minutes (first time)

## Why Railway?

- Auto-detects Spring Boot/Maven projects
- Provides free PostgreSQL database
- Automatic HTTPS and custom domains
- Simple environment variable management
- Git-based deployments
- Free tier available for testing

## Prerequisites

Before starting deployment:

- [ ] Git repository initialized (✅ Done in WORKING1)
- [ ] GitHub account created
- [ ] Railway account created (https://railway.com/)
- [ ] Shopify access token with required scopes
- [ ] Local application tested and working

## Step 1: Prepare for Deployment

### 1.1 Verify Local Setup

```bash
# Make sure app runs locally
source "$HOME/.sdkman/bin/sdkman-init.sh"
SHOPIFY_SHOP_URL=hearnshobbies.myshopify.com \
SHOPIFY_ACCESS_TOKEN=your_token_here \
SHOPIFY_API_VERSION=2025-01 \
SHOPIFY_MAX_POINTS=100 \
PORT=8080 \
mvn spring-boot:run
```

Wait for: `Shopify Data API Started Successfully!`

Test working endpoints:
```bash
curl http://localhost:8080/api/health
curl "http://localhost:8080/api/products?first=5"
curl "http://localhost:8080/api/orders?first=5"
```

### 1.2 Check Git Status

```bash
# Should show WORKING1 commit
git log --oneline --decorate

# Should show: 6380b9e (HEAD -> main, tag: WORKING1)
```

### 1.3 Verify .gitignore

The `.env` file should NOT be committed (secrets stay local):

```bash
# This should NOT show .env
git status

# This should show .env is ignored
cat .gitignore | grep .env
```

## Step 2: Push to GitHub

### 2.1 Create GitHub Repository

1. Go to https://github.com/new
2. Repository name: `shopify-data-api`
3. Description: "Shopify Data API - REST API for accessing Shopify store data"
4. Privacy: **Public** or **Private** (your choice)
5. DO NOT initialize with README, .gitignore, or license (we already have them)
6. Click "Create repository"

### 2.2 Configure Git User (if not already done)

```bash
git config user.name "Your Name"
git config user.email "your.email@example.com"

# Or set globally
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

### 2.3 Add Remote and Push

```bash
# Add GitHub remote (replace YOUR_USERNAME with your GitHub username)
git remote add origin https://github.com/YOUR_USERNAME/shopify-data-api.git

# Push to GitHub
git push -u origin main

# Push tags
git push origin --tags
```

Verify on GitHub:
1. Go to https://github.com/YOUR_USERNAME/shopify-data-api
2. You should see all files (30 files)
3. Check that `.env` is NOT visible (only `.env.example` should be there)

## Step 3: Deploy to Railway

### 3.1 Create Railway Account

1. Go to https://railway.com/
2. Click "Start a New Project" or "Login"
3. Sign up with GitHub (recommended for easy repo access)
4. Verify your email

### 3.2 Create New Project

1. Click "New Project"
2. Select "Deploy from GitHub repo"
3. If first time: Click "Configure GitHub App" to allow Railway access
4. Select your `shopify-data-api` repository
5. Railway will automatically detect it's a Maven project

### 3.3 Wait for Initial Build

Railway will:
- Detect `pom.xml`
- Install Java 17
- Run `mvn clean install`
- Build the JAR file

**This will fail on first deployment** - that's expected! We need to add environment variables.

## Step 4: Configure Environment Variables

### 4.1 Add Shopify Configuration

In Railway dashboard:
1. Click on your service
2. Go to "Variables" tab
3. Click "New Variable"
4. Add these variables one by one:

| Variable Name | Value |
|--------------|-------|
| `SHOPIFY_SHOP_URL` | `hearnshobbies.myshopify.com` |
| `SHOPIFY_ACCESS_TOKEN` | `your_token_here` |
| `SHOPIFY_API_VERSION` | `2025-01` |
| `SHOPIFY_MAX_POINTS` | `100` |

### 4.2 Add PostgreSQL Database

1. In Railway dashboard, click "New"
2. Select "Database"
3. Choose "PostgreSQL"
4. Railway automatically creates `DATABASE_URL` variable
5. Your service will automatically have access to this variable

### 4.3 Verify Variables

In Variables tab, you should see:
- ✅ SHOPIFY_SHOP_URL
- ✅ SHOPIFY_ACCESS_TOKEN
- ✅ SHOPIFY_API_VERSION
- ✅ SHOPIFY_MAX_POINTS
- ✅ DATABASE_URL (auto-created by PostgreSQL service)

**Important:** PORT variable is automatically set by Railway (don't add it manually)

## Step 5: Deploy Application

### 5.1 Trigger Deployment

Railway automatically redeploys when:
- Environment variables are added/changed
- Code is pushed to GitHub

Or manually trigger:
1. Go to "Deployments" tab
2. Click "Deploy" on the latest commit

### 5.2 Monitor Build Logs

In Railway dashboard:
1. Click on the active deployment
2. Watch "Build Logs"
3. Look for:
   ```
   [INFO] BUILD SUCCESS
   [INFO] Total time: XX:XX min
   ```

### 5.3 Monitor Application Logs

After build completes, check "Deploy Logs":
1. Look for: `Shopify Data API Started Successfully!`
2. Look for: `Service is running on port XXXX`
3. Should NOT see connection errors

**Startup time:** Usually 30-60 seconds

## Step 6: Get Your Public URL

### 6.1 Find Railway URL

1. In Railway dashboard, click "Settings" tab
2. Scroll to "Domains"
3. You'll see: `your-app-name.up.railway.app`
4. Copy this URL

### 6.2 Enable Public Access

If domain isn't generated:
1. Go to "Settings" → "Networking"
2. Click "Generate Domain"
3. Railway creates: `https://your-app-name.up.railway.app`

## Step 7: Test Production Deployment

### 7.1 Test Health Endpoint

```bash
# Replace YOUR-APP with your Railway domain
curl https://your-app-name.up.railway.app/api/health
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

### 7.2 Test Shopify Connection

```bash
curl https://your-app-name.up.railway.app/api/status
```

Expected response should include:
```json
{
  "success": true,
  "message": "System operational",
  "data": {
    "shopify_connected": true,
    ...
  }
}
```

### 7.3 Test Products Endpoint

```bash
curl "https://your-app-name.up.railway.app/api/products?first=5"
```

Should return real products from hearnshobbies store.

### 7.4 Test Orders Endpoint

```bash
curl "https://your-app-name.up.railway.app/api/orders?first=5"
```

Should return real orders.

## Step 8: Configure Custom Domain (Optional)

### 8.1 Add Custom Domain

If you have your own domain:

1. Go to Railway "Settings" → "Domains"
2. Click "Custom Domain"
3. Enter your domain: `api.yourdomain.com`
4. Railway provides DNS records to add

### 8.2 Configure DNS

In your domain provider:
1. Add CNAME record:
   ```
   api.yourdomain.com → your-app-name.up.railway.app
   ```
2. Wait for DNS propagation (5-60 minutes)

### 8.3 Test Custom Domain

```bash
curl https://api.yourdomain.com/api/health
```

Railway automatically provides SSL certificate.

## Step 9: Set Up Continuous Deployment

### 9.1 Enable Auto-Deploy

Railway automatically deploys when you push to GitHub:

```bash
# Make a change locally
echo "# Update" >> README.md

# Commit and push
git add README.md
git commit -m "Update README"
git push origin main
```

Railway will:
1. Detect new commit
2. Automatically build
3. Automatically deploy
4. Zero downtime deployment

### 9.2 Monitor Deployments

In Railway dashboard:
1. "Deployments" tab shows all deployments
2. Green checkmark = successful
3. Red X = failed (check logs)

## Step 10: Monitor and Maintain

### 10.1 View Application Logs

In Railway dashboard:
1. Click on service
2. "Logs" tab shows real-time logs
3. Filter by log level: INFO, ERROR, etc.

Useful commands in logs to look for:
```
✅ "Shopify Data API Started Successfully!"
✅ "Fetching products from Shopify"
⚠️ "Rate limiter waiting"
❌ "Error fetching products"
```

### 10.2 Check Database

View PostgreSQL database:
1. Click on PostgreSQL service
2. "Data" tab shows tables
3. "Connect" tab shows connection string

### 10.3 Monitor Performance

Railway provides:
- CPU usage
- Memory usage
- Network traffic
- Request metrics

Access via "Metrics" tab.

### 10.4 Set Up Alerts (Optional)

Railway can send alerts for:
- Deployment failures
- High resource usage
- Application crashes

Configure in "Settings" → "Notifications"

## Troubleshooting Deployment

### Issue: Build Fails

**Error:** `Cannot find JDK`

**Solution:** Create `nixpacks.toml` in project root:
```toml
[phases.setup]
nixPkgs = ["jdk17"]
```

Commit and push:
```bash
git add nixpacks.toml
git commit -m "Add nixpacks configuration"
git push origin main
```

### Issue: Application Crashes on Startup

**Check logs for:**

**Error:** `IllegalArgumentException: Not enough variable values`

**Solution:** Verify all environment variables are set in Railway dashboard

**Error:** `Connection to DATABASE_URL failed`

**Solution:** Ensure PostgreSQL service is created and linked

**Error:** `Access denied for products field`

**Solution:** Verify SHOPIFY_ACCESS_TOKEN has required scopes

### Issue: 503 Service Unavailable

**Possible causes:**
1. Application still starting (wait 60 seconds)
2. Application crashed (check logs)
3. Port binding issue (Railway auto-assigns port)

**Solution:**
```bash
# Check Railway logs
# Look for: "Service is running on port XXXX"
```

### Issue: Shopify Connection Fails

**Error in logs:** `Shopify connection failed`

**Solutions:**
1. Verify SHOPIFY_SHOP_URL (no https://, just domain)
2. Verify SHOPIFY_ACCESS_TOKEN is correct
3. Check Shopify app is installed
4. Verify API scopes

Test token manually:
```bash
curl -X POST https://hearnshobbies.myshopify.com/admin/api/2025-01/graphql.json \
  -H "X-Shopify-Access-Token: YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query": "{shop{name}}"}'
```

### Issue: Environment Variables Not Loading

**Check:**
1. Railway dashboard → Variables tab
2. All required variables present
3. No typos in variable names
4. Values don't have extra spaces

**Re-deploy after fixing:**
1. Click "Deployments"
2. Click "Redeploy" on latest deployment

## Cost Considerations

### Railway Free Tier
- $5 free credit per month
- Automatically includes:
  - 512 MB RAM
  - Shared CPU
  - PostgreSQL database

### Estimated Monthly Cost

For this application:
- **Development/Testing:** $0-5/month (free tier)
- **Light Production:** $5-10/month
- **Medium Production:** $10-20/month

### Optimize Costs
1. Use free tier for development
2. Upgrade to paid plan only when needed
3. Monitor usage in Railway dashboard
4. Set spending limits in settings

## Post-Deployment Checklist

- [ ] Application deployed successfully
- [ ] Health endpoint returns 200 OK
- [ ] Status endpoint shows `shopify_connected: true`
- [ ] Products endpoint returns real data
- [ ] Orders endpoint returns real data
- [ ] Public URL documented and saved
- [ ] Environment variables backed up (not in git!)
- [ ] Railway project bookmarked
- [ ] Monitoring/alerts configured (optional)
- [ ] Custom domain configured (optional)

## Railway Dashboard Overview

### Key Sections

**Service Overview:**
- Current deployment status
- Resource usage (CPU, memory)
- Public URL

**Deployments:**
- Deployment history
- Build logs
- Deploy logs

**Variables:**
- Environment variables
- Add/edit/delete variables

**Settings:**
- Domain configuration
- Region selection
- Service deletion

**Metrics:**
- Request volume
- Response times
- Error rates

## Next Steps After Deployment

1. **Document Production URL**
   - Save URL: `https://your-app-name.up.railway.app`
   - Share with team
   - Update any client applications

2. **Test All Working Endpoints**
   - Products ✅
   - Orders ✅
   - Health ✅
   - Status ✅

3. **Create Usage Examples**
   - See USAGE_EXAMPLES.md (next doc)

4. **Fix Remaining Endpoints**
   - Customers endpoint
   - Inventory endpoint

5. **Deploy Final Version**
   - After fixes are complete
   - Test all endpoints
   - Update documentation

## Support Resources

- **Railway Docs:** https://docs.railway.app/
- **Railway Discord:** https://discord.gg/railway
- **Railway Status:** https://status.railway.app/
- **This Project Docs:** See `docs/` folder

---

**Your API is now live!** 🚀

Next: See `USAGE_EXAMPLES.md` for how to use your deployed API.
