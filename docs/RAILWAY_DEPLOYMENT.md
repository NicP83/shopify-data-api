# Railway Deployment Guide

This guide walks you through deploying your Shopify Data API to Railway.

## Why Railway?

- **Auto-detection**: Automatically detects Java/Maven projects
- **Built-in PostgreSQL**: Easy database provisioning
- **GitHub Integration**: Deploy on every push
- **Environment Variables**: Simple configuration management
- **Free Tier**: Great for development and testing
- **No Docker required**: Railway handles the build

## Prerequisites

1. Railway account - Sign up at https://railway.com/
2. GitHub account
3. Your code pushed to a GitHub repository
4. Shopify API credentials (from SHOPIFY_CONNECTION.md)

## Step 1: Push Your Code to GitHub

### Create a GitHub Repository

```bash
# In your project directory
git init
git add .
git commit -m "Initial commit: Shopify Data API"

# Create a new repository on GitHub, then:
git remote add origin https://github.com/YOUR_USERNAME/shopify-data-api.git
git branch -M main
git push -u origin main
```

**Important**: Ensure `.gitignore` excludes:
- `.env`
- `target/`
- `.idea/`
- `*.iml`

## Step 2: Create Railway Project

1. Go to https://railway.com/
2. Click **Start a New Project**
3. Choose **Deploy from GitHub repo**
4. Authorize Railway to access your GitHub
5. Select your `shopify-data-api` repository

Railway will automatically:
- Detect it's a Java Maven project
- Start building the application
- Generate a domain name

## Step 3: Add PostgreSQL Database

1. In your Railway project dashboard
2. Click **+ New**
3. Select **Database**
4. Choose **PostgreSQL**
5. Railway provisions the database instantly

Railway automatically creates these environment variables:
- `DATABASE_URL`
- `DATABASE_HOST`
- `DATABASE_PORT`
- `DATABASE_NAME`
- `DATABASE_USER`
- `DATABASE_PASSWORD`

## Step 4: Configure Environment Variables

### Add Shopify Configuration

1. Click on your **service** (shopify-data-api)
2. Go to **Variables** tab
3. Click **+ New Variable**

Add these variables:

```
SHOPIFY_SHOP_URL=your-store.myshopify.com
SHOPIFY_ACCESS_TOKEN=shpat_xxxxxxxxxxxxxxxxxxxxx
SHOPIFY_API_VERSION=2025-01
SHOPIFY_MAX_POINTS=1000
SHOPIFY_MAX_RETRIES=3
SHOPIFY_INITIAL_BACKOFF=1000
```

### Verify Database Variables

Railway should have already set:
- `DATABASE_URL` (automatically connected to your PostgreSQL)
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`

### Optional Configuration

```
PORT=8080
SHOW_SQL=false
```

## Step 5: Deploy

### Automatic Deployment

Railway automatically deploys when:
- You first connect the repository
- You push to the main branch

### Manual Deployment

1. Go to **Deployments** tab
2. Click **Deploy**

### Monitor the Build

Watch the build logs in real-time:
1. Click on the latest deployment
2. View **Build Logs**
3. Then view **Deploy Logs**

Expected build output:
```
[INFO] Building jar: /app/target/shopify-data-api-1.0.0.jar
[INFO] BUILD SUCCESS
```

## Step 6: Get Your App URL

1. Go to **Settings** tab
2. Under **Domains**, you'll see your Railway domain:
   ```
   shopify-data-api-production.up.railway.app
   ```

3. Test your deployment:
   ```bash
   curl https://your-app.up.railway.app/api/health
   ```

## Step 7: Test the Deployment

### Health Check

```bash
curl https://your-app.up.railway.app/api/health
```

Expected response:
```json
{
  "success": true,
  "message": "Success",
  "data": {
    "status": "UP",
    "service": "Shopify Data API",
    "timestamp": 1234567890
  }
}
```

### Status Check (Tests Shopify Connection)

```bash
curl https://your-app.up.railway.app/api/status
```

Should show `"shopify_connected": true`

### Fetch Products

```bash
curl https://your-app.up.railway.app/api/products?first=5
```

## Railway Configuration Files

Railway uses these files for deployment:

### Automatically Detected

Railway automatically detects your project from:
- `pom.xml` (Maven project)
- `src/main/java/` (Java source)

### Build Command (Auto)

Railway automatically runs:
```bash
mvn clean install -DskipTests
```

### Start Command (Auto)

Railway automatically runs:
```bash
java -jar target/shopify-data-api-1.0.0.jar
```

## Custom Configuration (Optional)

### Create `railway.toml` (if needed)

```toml
[build]
builder = "nixpacks"

[deploy]
startCommand = "java -Xmx512m -jar target/shopify-data-api-1.0.0.jar"
restartPolicyType = "on_failure"
restartPolicyMaxRetries = 3
```

This allows you to:
- Customize memory (`-Xmx512m`)
- Set restart policies
- Override build commands

## Monitoring Your Application

### View Logs

1. Click on your service
2. Go to **Logs** tab
3. See real-time application logs

### Metrics

1. Go to **Metrics** tab
2. View:
   - CPU usage
   - Memory usage
   - Network traffic
   - Request counts

### Set Up Alerts (Optional)

1. **Settings** → **Webhooks**
2. Add webhook URL for deployment notifications

## Database Management

### Access PostgreSQL

**Option 1: Railway Dashboard**
1. Click on PostgreSQL service
2. Go to **Data** tab
3. Browse tables directly

**Option 2: Connection String**
1. Get `DATABASE_URL` from variables
2. Use any PostgreSQL client:
   ```bash
   psql postgresql://user:pass@host:port/dbname
   ```

### Backup Database

Railway Pro provides automatic backups. For free tier:
1. Use `pg_dump`:
   ```bash
   pg_dump $DATABASE_URL > backup.sql
   ```

## Continuous Deployment

### Auto-Deploy on Push

Already configured! Every push to `main` triggers deployment.

### Disable Auto-Deploy

1. **Settings** → **Service**
2. Toggle **Enable Auto-Deploy** off

### Deploy from Branches

1. **Settings** → **Service**
2. Change **Source** branch
3. Can deploy from `dev`, `staging`, etc.

## Environment Management

### Multiple Environments

Create separate Railway projects:
- `shopify-api-dev` (development)
- `shopify-api-staging` (testing)
- `shopify-api-production` (live)

Each with its own:
- Database
- Environment variables
- Shopify store connection

## Scaling

### Vertical Scaling

Railway automatically handles scaling based on usage.

For more resources:
1. **Settings** → **Resources**
2. Upgrade to Railway Pro for more CPU/Memory

### Horizontal Scaling

Not needed for most use cases. Rate limiting is API-side (Shopify).

## Custom Domain (Optional)

### Add Your Domain

1. **Settings** → **Domains**
2. Click **+ Custom Domain**
3. Enter your domain: `api.yourdomain.com`
4. Add CNAME record to your DNS:
   ```
   CNAME api.yourdomain.com → your-app.up.railway.app
   ```
5. Railway provides free SSL automatically

## Costs

### Free Tier
- $5 free credit per month
- Includes:
  - 512MB RAM
  - Shared CPU
  - PostgreSQL database

Enough for development and low-traffic production.

### Pro Tier ($20/month)
- More resources
- Better uptime SLA
- Automatic backups
- Priority support

## Rollback Deployments

### Revert to Previous Version

1. **Deployments** tab
2. Find working deployment
3. Click **⋮** menu
4. Select **Rollback to this deployment**

## Troubleshooting

### Build Fails: "Cannot find JDK"

Railway uses Java 11 by default. Force Java 17:

Create `nixpacks.toml`:
```toml
[phases.setup]
nixPkgs = ["jdk17"]
```

### Application Crashes on Start

**Check Logs:**
1. View Deploy Logs
2. Look for errors

**Common issues:**
- Missing environment variables
- Database connection failed
- Port not exposed (should be 8080)

**Solution:**
- Verify all `SHOPIFY_*` variables are set
- Check `DATABASE_URL` is correct
- Ensure `PORT=8080` or let Railway auto-assign

### "502 Bad Gateway"

**Causes:**
- App isn't listening on correct port
- App crashed during startup

**Solution:**
- Check logs for errors
- Verify `PORT` variable matches application.yml

### Database Connection Timeout

**Solution:**
- Ensure DATABASE_URL is set
- Check PostgreSQL service is running
- Verify connection string format

### Shopify Connection Fails

**Solution:**
- Verify `SHOPIFY_SHOP_URL` (no https://, just domain)
- Check `SHOPIFY_ACCESS_TOKEN` is correct
- Test API scopes in Shopify admin

## Best Practices

1. **Use Environment Variables** - Never hardcode credentials
2. **Monitor Logs** - Check regularly for errors
3. **Set Up Alerts** - Get notified of deployment failures
4. **Test Before Deploying** - Always test locally first
5. **Use Branches** - Deploy from staging branch before main
6. **Keep Dependencies Updated** - Update API version quarterly
7. **Backup Database** - Regular backups for production

## Security

Railway provides:
- ✅ HTTPS by default
- ✅ Environment variable encryption
- ✅ Private networking between services
- ✅ Automatic security updates

Additional security:
- Rotate Shopify tokens regularly
- Use separate tokens per environment
- Enable Shopify API webhook verification

## Next Steps

1. **Test all endpoints** → See [API_REFERENCE.md](./API_REFERENCE.md)
2. **Add custom functions** → See [ADDING_FUNCTIONS.md](./ADDING_FUNCTIONS.md)
3. **Monitor performance** → Set up logging/alerts
4. **Add Docker** (Phase 2) → Create Dockerfile for consistency

## Useful Commands

```bash
# View Railway logs locally
railway logs

# Connect to database
railway connect postgres

# Open Railway dashboard
railway open

# Link local project to Railway
railway link
```

## Resources

- Railway Documentation: https://docs.railway.app/
- Railway Discord: https://discord.gg/railway
- Spring Boot on Railway: https://docs.railway.app/guides/spring-boot
