# Shopify Connection Setup Guide

This guide explains how to get your Shopify API credentials and connect your application to a Shopify store.

## Overview

To access Shopify's Admin API, you need:
1. A Shopify Partner account
2. A development store
3. A custom app with Admin API access
4. An Admin API access token

## Step 1: Create a Shopify Partner Account

1. Go to https://partners.shopify.com/signup
2. Fill out the registration form
3. Verify your email
4. Complete your profile

**Why?** Partner accounts let you create development stores and apps for free.

## Step 2: Create a Development Store

1. Log in to your Partner Dashboard
2. Click **Stores** in the left sidebar
3. Click **Add store**
4. Select **Development store**
5. Fill in details:
   - Store name: `my-test-store` (choose any name)
   - Store purpose: **Build a new app for a client**
   - Login information: Set up your admin credentials
6. Click **Create development store**

Your store URL will be: `my-test-store.myshopify.com`

**Note:** Development stores are free and have all features of Shopify Plus.

## Step 3: Create a Custom App

### Enable Custom App Development

1. From your development store admin:
   - Go to **Settings** (bottom left)
   - Click **Apps and sales channels**
   - Click **Develop apps**
   - Click **Allow custom app development**
   - Click **Allow custom app development** again to confirm

### Create the App

1. Click **Create an app**
2. App name: `Shopify Data API`
3. Click **Create app**

## Step 4: Configure API Access

### Set API Scopes

1. Click **Configure Admin API scopes**

2. Select the following scopes (based on what data you need):

   **Products:**
   - `read_products`

   **Orders:**
   - `read_orders`

   **Customers:**
   - `read_customers`

   **Inventory:**
   - `read_inventory`
   - `read_locations`

   **Additional (optional):**
   - `read_fulfillments`
   - `read_shipping`
   - `read_discounts`

3. Click **Save**

### Install the App

1. Click **Install app** (top right)
2. Review the permissions
3. Click **Install app**

### Get Your Access Token

1. After installation, you'll see the **Admin API access token**
2. **IMPORTANT:** Copy this token immediately - you can only see it once!
   - Format: `shpat_xxxxxxxxxxxxxxxxxxxxxxxxxxxxx`
3. Store it securely (we'll add it to `.env` later)

**⚠️ Security Warning:**
- Never commit the access token to Git
- Never share it publicly
- Treat it like a password

## Step 5: Note Your API Details

You'll need these values:

```
Store URL: my-test-store.myshopify.com
Access Token: shpat_xxxxxxxxxxxxxxxxxxxxxxxxxxxxx
API Version: 2025-01 (use latest stable version)
```

## Step 6: Update Your Application Configuration

### Local Development (.env file)

Edit your `.env` file:

```bash
# Shopify Configuration
SHOPIFY_SHOP_URL=my-test-store.myshopify.com
SHOPIFY_ACCESS_TOKEN=shpat_xxxxxxxxxxxxxxxxxxxxxxxxxxxxx
SHOPIFY_API_VERSION=2025-01

# Rate Limiting (based on your plan)
# Development stores get Plus limits: 1000 points/second
SHOPIFY_MAX_POINTS=1000
```

### For Railway Deployment

Add these as environment variables in Railway (see RAILWAY_DEPLOYMENT.md):
- `SHOPIFY_SHOP_URL`
- `SHOPIFY_ACCESS_TOKEN`
- `SHOPIFY_API_VERSION`
- `SHOPIFY_MAX_POINTS`

## Step 7: Test the Connection

### Start Your Application

```bash
mvn spring-boot:run
```

### Test with curl

```bash
curl http://localhost:8080/api/status
```

Expected response:
```json
{
  "success": true,
  "message": "System operational",
  "data": {
    "service": "Shopify Data API",
    "timestamp": 1234567890,
    "shopify_connected": true,
    "rate_limiter_available_points": 1000
  }
}
```

### Test Fetching Products

```bash
curl http://localhost:8080/api/products
```

If you have products in your store, you should see them. If not, add some test products first.

## Understanding Shopify API Versions

Shopify releases new API versions quarterly:
- **2025-01** (January 2025)
- **2024-10** (October 2024)
- etc.

**Best practices:**
- Always use a specific version (not "unstable")
- Update quarterly to stay current
- Test before upgrading in production
- Old versions are deprecated after 12 months

Check latest version: https://shopify.dev/docs/api/admin-graphql#api-versioning

## Rate Limits by Plan

| Shopify Plan | Points/Second |
|--------------|---------------|
| Basic | 100 |
| Shopify | 100 |
| Advanced | 200 |
| Plus | 1,000 |
| Enterprise | 2,000 |
| Development Store | 1,000 |

Update `SHOPIFY_MAX_POINTS` in your `.env` accordingly.

## API Scopes Reference

Common scopes you might need:

### Read Access
- `read_products` - Product data
- `read_orders` - Order information
- `read_customers` - Customer data
- `read_inventory` - Stock levels
- `read_locations` - Warehouse/store locations
- `read_price_rules` - Discounts and promotions
- `read_shipping` - Shipping rates and zones

### Write Access (if needed later)
- `write_products`
- `write_orders`
- `write_customers`
- `write_inventory`

**Important:** Only request scopes you actually need!

## Adding Test Data to Your Store

To test the API, you need some data in your store:

### Option 1: Manual Entry
1. Go to your store admin
2. Add products, create test orders, etc.

### Option 2: Use Sample Data
1. Shopify Partner Dashboard
2. Your development store
3. Click **Import sample products**

### Option 3: Shopify CLI
```bash
shopify populate products
shopify populate customers
shopify populate orders
```

## OAuth for Production Apps

This guide uses **Admin API access tokens** (for custom/private apps).

For **public apps** distributed to multiple stores, you need OAuth:
- Implement OAuth flow
- Handle token refresh
- Store tokens securely per shop

See Shopify's OAuth documentation: https://shopify.dev/docs/apps/auth/oauth

## Security Best Practices

1. **Never expose access tokens**
   - Don't commit to Git
   - Don't log them
   - Don't send over insecure connections

2. **Use environment variables**
   - Different tokens for dev/staging/production
   - Rotate tokens periodically

3. **Limit API scopes**
   - Only request what you need
   - Use read-only scopes when possible

4. **Monitor API usage**
   - Track rate limit consumption
   - Set up alerts for failures

5. **Use HTTPS in production**
   - Railway provides this automatically

## Troubleshooting

### Error: "Access token is invalid"

**Causes:**
- Token copied incorrectly
- Token from wrong app
- App was uninstalled

**Solution:**
- Regenerate the token in Shopify admin
- Copy carefully (no extra spaces)
- Update `.env`

### Error: "API rate limit exceeded"

**Causes:**
- Too many requests too fast
- `SHOPIFY_MAX_POINTS` set too high

**Solution:**
- Lower `SHOPIFY_MAX_POINTS` in config
- The rate limiter will handle it automatically

### Error: "Missing required access scopes"

**Causes:**
- App doesn't have permission for the requested data

**Solution:**
- Go to app settings in Shopify
- Add the required scopes
- Reinstall the app

### Connection works locally but not on Railway

**Causes:**
- Environment variables not set on Railway

**Solution:**
- Check Railway dashboard
- Add all `SHOPIFY_*` variables
- Redeploy

## Next Steps

1. **Test all endpoints** → See [API_REFERENCE.md](./API_REFERENCE.md)
2. **Deploy to Railway** → See [RAILWAY_DEPLOYMENT.md](./RAILWAY_DEPLOYMENT.md)
3. **Add custom functions** → See [ADDING_FUNCTIONS.md](./ADDING_FUNCTIONS.md)

## Useful Resources

- Shopify Partner Dashboard: https://partners.shopify.com/
- Admin API Documentation: https://shopify.dev/docs/api/admin
- GraphQL Explorer: https://shopify.dev/docs/apps/tools/graphiql-admin-api
- API Versioning: https://shopify.dev/docs/api/usage/versioning
- Rate Limits: https://shopify.dev/docs/api/usage/rate-limits
