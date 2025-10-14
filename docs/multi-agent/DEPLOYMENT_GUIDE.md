# Multi-Agent System - Deployment Guide

**Version:** 1.0
**Date:** 2025-10-14
**Status:** Ready for Staging Deployment

---

## Overview

This guide covers deploying the Multi-Agent Orchestration System to Railway staging environment.

---

## Prerequisites

### Required Accounts
- ✅ Railway account (railway.app)
- ✅ Anthropic API key (for Claude)
- ✅ Shopify store credentials

### Required Tools
- ✅ Railway CLI (optional, can use web interface)
- ✅ Git (for deployment)
- ✅ Maven 3.8+ (for building)
- ✅ Node.js 18+ (for frontend)

---

## Environment Variables

### Required Variables

```bash
# Claude API
ANTHROPIC_API_KEY=sk-ant-api03-xxxxx

# Shopify Integration
SHOPIFY_SHOP_URL=yourstore.myshopify.com
SHOPIFY_ACCESS_TOKEN=shpat_xxxxx
SHOPIFY_API_VERSION=2025-01
SHOPIFY_MAX_POINTS=100

# Application
PORT=8080

# Database (Auto-configured by Railway)
DATABASE_URL=postgresql://user:pass@host:port/db
```

### Optional Variables

```bash
# Spring Boot
SPRING_PROFILES_ACTIVE=production
SPRING_JPA_SHOW_SQL=false

# Logging
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_SHOPIFY_API=DEBUG
```

---

## Railway Deployment Steps

### Step 1: Create Railway Project

#### Option A: Using Railway Web Interface

1. Go to https://railway.app
2. Click "New Project"
3. Select "Deploy from GitHub repo"
4. Connect your GitHub account
5. Select the `shopify-data-api` repository
6. Click "Deploy Now"

#### Option B: Using Railway CLI

```bash
# Install Railway CLI
npm install -g @railway/cli

# Login to Railway
railway login

# Initialize project
railway init

# Link to existing project (if already created)
railway link
```

### Step 2: Provision PostgreSQL Database

1. In Railway project dashboard
2. Click "New" → "Database"
3. Select "PostgreSQL"
4. Railway will automatically set `DATABASE_URL`
5. Note: Database credentials auto-configured

### Step 3: Configure Environment Variables

#### Via Web Interface:
1. Go to your project in Railway
2. Click on your service
3. Go to "Variables" tab
4. Add each environment variable
5. Click "Add Variable" for each one

#### Via CLI:
```bash
# Set variables
railway variables set ANTHROPIC_API_KEY=sk-ant-api03-xxxxx
railway variables set SHOPIFY_SHOP_URL=yourstore.myshopify.com
railway variables set SHOPIFY_ACCESS_TOKEN=shpat_xxxxx
railway variables set SHOPIFY_API_VERSION=2025-01
railway variables set SHOPIFY_MAX_POINTS=100
railway variables set PORT=8080
```

### Step 4: Configure Build Settings

#### Backend (Maven)

Railway should auto-detect Maven project. If not, configure:

**Build Command:**
```bash
mvn clean package -DskipTests
```

**Start Command:**
```bash
java -jar target/shopify-data-api-1.0.0.jar
```

**Root Directory:** `/` (project root)

#### Frontend (React)

If deploying separately:

**Build Command:**
```bash
cd frontend && npm install && npm run build
```

**Start Command:**
```bash
npm start
```

**Root Directory:** `/frontend`

### Step 5: Deploy

#### Auto Deployment (Recommended)
- Railway automatically deploys on push to main branch
- Commit and push your code:

```bash
git add .
git commit -m "Deploy multi-agent system to staging"
git push origin main
```

#### Manual Deployment
```bash
# Via CLI
railway up

# Or trigger from web interface
# Click "Deploy" button in Railway dashboard
```

### Step 6: Monitor Deployment

1. Watch deployment logs in Railway dashboard
2. Check for:
   - ✅ Maven build success
   - ✅ Application startup
   - ✅ Database migrations executed
   - ✅ Port 8080 listening
   - ✅ Health check passing

**Expected logs:**
```
[INFO] Building Shopify Data API 1.0.0
[INFO] BUILD SUCCESS
Flyway migration starting...
Migration V001__initial_schema.sql completed
Migration V002__multi_agent_system.sql completed
Started ShopifyDataApiApplication in 12.345 seconds
==============================================
Shopify Data API Started Successfully!
==============================================
```

---

## Post-Deployment Verification

### Step 1: Check Application Health

```bash
# Get your Railway URL (e.g., https://your-app.up.railway.app)
export APP_URL="https://your-app.up.railway.app"

# Check health endpoint
curl $APP_URL/api/health

# Expected response:
# {"status":"UP"}
```

### Step 2: Test API Endpoints

```bash
# List agents
curl $APP_URL/api/agents

# List workflows
curl $APP_URL/api/workflows

# List tools
curl $APP_URL/api/tools
```

### Step 3: Test Frontend

1. Open browser to Railway app URL
2. Navigate to each page:
   - `/` - Dashboard (should load)
   - `/agents` - Agent management
   - `/workflows` - Workflow management
   - `/executions` - Execution monitoring
   - `/approvals` - Approval queue

### Step 4: Create Test Agent

```bash
# Create test agent via API
curl -X POST $APP_URL/api/agents \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Agent",
    "description": "Testing deployment",
    "model": "claude-3-5-sonnet-20241022",
    "systemPrompt": "You are a helpful assistant.",
    "temperature": 0.7,
    "maxTokens": 1000,
    "isActive": true
  }'
```

### Step 5: Create Test Workflow

1. Open `/workflows/new` in browser
2. Create simple workflow:
   - Add workflow name: "Test Workflow"
   - Add one agent step
   - Save workflow
3. Execute workflow from UI
4. Check `/executions` to verify execution

### Step 6: Test Scheduled Workflow

1. Create schedule via API or UI (when UI is built)
2. Wait for next minute (cron runs every minute)
3. Check logs for scheduled execution
4. Verify in `/executions` page

---

## Troubleshooting

### Common Issues

#### 1. Build Fails

**Problem:** Maven build fails
**Solution:**
```bash
# Check Java version
java -version  # Should be 17+

# Test build locally
mvn clean package -DskipTests

# Check Railway build logs for specific errors
```

#### 2. Database Connection Fails

**Problem:** Application can't connect to database
**Solution:**
- Verify `DATABASE_URL` is set by Railway
- Check PostgreSQL addon is provisioned
- Review connection logs in Railway

#### 3. Migrations Fail

**Problem:** Flyway migration errors
**Solution:**
```bash
# Check if migrations already applied
# In Railway database terminal:
SELECT * FROM flyway_schema_history;

# If needed, repair Flyway
# Connect to database and run:
DELETE FROM flyway_schema_history WHERE success = false;
```

#### 4. Port Binding Issues

**Problem:** Application fails to bind to port
**Solution:**
- Ensure `PORT` environment variable is set to 8080
- Railway automatically sets `PORT`, use `${PORT}` in config
- Check application.properties uses `${PORT:8080}`

#### 5. Frontend Not Loading

**Problem:** Frontend pages return 404
**Solution:**
- Ensure frontend is built (npm run build)
- Check Spring Boot serves static files
- Verify `src/main/resources/static` has built files

#### 6. Claude API Errors

**Problem:** Agent execution fails with API errors
**Solution:**
- Verify `ANTHROPIC_API_KEY` is correct
- Check Anthropic API status
- Review API rate limits
- Check logs for specific error messages

#### 7. Scheduled Workflows Not Running

**Problem:** Cron jobs not executing
**Solution:**
- Verify `@EnableScheduling` is enabled
- Check application logs for scheduler logs
- Ensure at least one schedule exists with `enabled=true`
- Wait for next minute mark (cron runs at :00 seconds)

---

## Monitoring & Logs

### View Logs

#### Via Railway Dashboard:
1. Go to your project
2. Click on service
3. Go to "Deployments" tab
4. Click on latest deployment
5. View logs in real-time

#### Via CLI:
```bash
# Tail logs
railway logs

# Follow logs
railway logs -f
```

### Key Log Patterns to Monitor

**Successful Startup:**
```
Shopify Data API Started Successfully!
```

**Database Migration:**
```
Flyway migration starting...
Migration completed successfully
```

**Agent Execution:**
```
Executing agent: [Agent Name]
Agent execution completed successfully
```

**Workflow Execution:**
```
Starting workflow execution: [Workflow Name]
Workflow completed successfully
```

**Scheduled Execution:**
```
Processing scheduled workflows at [timestamp]
Found N scheduled workflows to execute
```

---

## Rollback Procedure

### If Deployment Fails:

#### Via Railway Dashboard:
1. Go to "Deployments" tab
2. Find previous successful deployment
3. Click "Redeploy"

#### Via Git:
```bash
# Revert to previous commit
git revert HEAD
git push origin main

# Or reset to specific commit
git reset --hard <commit-hash>
git push origin main --force
```

---

## Security Considerations

### Current Security Status

⚠️ **WARNING:** Current deployment has NO authentication!

**Missing Security Features:**
- ❌ No API authentication
- ❌ No user login
- ❌ No RBAC
- ❌ No rate limiting

**For Staging:** This is acceptable for internal testing

**For Production:** Must add:
1. JWT authentication
2. User login system
3. Role-based access control
4. API rate limiting
5. HTTPS enforcement (Railway provides this automatically)

### Recommended Security Additions (Before Production)

See `docs/multi-agent/SECURITY_ENHANCEMENTS.md` for detailed guide.

---

## Performance Tuning

### Database Optimization

```sql
-- Add indexes for common queries (already in migrations)
CREATE INDEX IF NOT EXISTS idx_workflows_active ON workflows(is_active);
CREATE INDEX IF NOT EXISTS idx_agents_active ON agents(is_active);
CREATE INDEX IF NOT EXISTS idx_executions_status ON workflow_executions(status);
CREATE INDEX IF NOT EXISTS idx_schedules_next_run ON workflow_schedules(next_run_at, enabled);
```

### JVM Tuning (Optional)

Add to Railway environment variables:
```bash
JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC
```

### Database Connection Pool

Already configured in application.properties:
```properties
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
```

---

## Backup & Recovery

### Database Backups

Railway automatically backs up PostgreSQL databases.

**Manual Backup:**
```bash
# Via Railway CLI
railway db backup

# Download backup
railway db download
```

**Restore:**
```bash
# Via Railway CLI
railway db restore <backup-id>
```

---

## Scaling Considerations

### Vertical Scaling
- Railway allows instance size upgrades
- Increase memory for handling more concurrent workflows

### Horizontal Scaling
- Current implementation supports single instance
- For multiple instances, add:
  - Redis for distributed caching
  - Message queue for distributed scheduling
  - Load balancer (Railway provides this)

---

## Support & Documentation

### Documentation Files
- `ARCHITECTURE.md` - System design
- `DATABASE_SCHEMA.md` - Database structure
- `IMPLEMENTATION_ROADMAP.md` - Development progress
- `PROGRESS.md` - Implementation status
- `MVP_ASSESSMENT.md` - MVP readiness
- `DEPLOYMENT_GUIDE.md` - This file

### Getting Help
- Check Railway logs first
- Review error messages
- Consult documentation
- Railway community: community.railway.app

---

## Deployment Checklist

### Pre-Deployment
- [x] All code committed to Git
- [x] Tests passing locally
- [x] Environment variables documented
- [x] Database migrations ready
- [ ] Railway project created
- [ ] PostgreSQL provisioned
- [ ] Environment variables configured

### During Deployment
- [ ] Build succeeds
- [ ] Database migrations run
- [ ] Application starts
- [ ] Health check passes
- [ ] Frontend accessible

### Post-Deployment
- [ ] API endpoints working
- [ ] Frontend pages loading
- [ ] Test agent created
- [ ] Test workflow executed
- [ ] Scheduled workflow tested
- [ ] Logs monitored for errors

### Production Checklist (Before Going Live)
- [ ] Authentication implemented
- [ ] RBAC configured
- [ ] Rate limiting added
- [ ] Security audit completed
- [ ] Load testing performed
- [ ] Backup strategy confirmed
- [ ] Monitoring alerts configured
- [ ] User documentation complete

---

**Document Version:** 1.0
**Last Updated:** 2025-10-14
**Next Review:** After staging deployment
