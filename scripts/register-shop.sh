#!/bin/bash

# Script to register hearnshobbies.myshopify.com in the database
# This enables the AI chat endpoint to work

echo "=== Registering Hearn's Hobbies Shop ==="
echo ""

# Database connection from Railway environment variables
# These should be set in your Railway deployment

API_URL="https://shopify-data-api-production.up.railway.app"
SHOP_DOMAIN="hearnshobbies.myshopify.com"

echo "Shop: $SHOP_DOMAIN"
echo "API: $API_URL"
echo ""

# First, check if shop exists via config endpoint
echo "1. Checking if shop is already registered..."
RESPONSE=$(curl -s "$API_URL/api/shopify/config?shop=$SHOP_DOMAIN")
echo "Response: $RESPONSE"
echo ""

# If shop doesn't exist, we need to register it via direct database access
# Since this is a custom app (not OAuth), we'll use the migration script approach

echo "2. Shop needs to be registered in database"
echo ""
echo "To register the shop, you have two options:"
echo ""
echo "Option A: Via Railway PostgreSQL Console"
echo "==========================================="
echo "1. Go to Railway dashboard: https://railway.app/"
echo "2. Select your project: shopify-data-api"
echo "3. Click on PostgreSQL service"
echo "4. Go to 'Query' tab"
echo "5. Run this SQL:"
echo ""
echo "INSERT INTO shopify_shops ("
echo "  shop_domain,"
echo "  access_token,"
echo "  scope,"
echo "  is_active,"
echo "  ai_enabled,"
echo "  analytics_enabled,"
echo "  installed_at,"
echo "  shop_name"
echo ") VALUES ("
echo "  'hearnshobbies.myshopify.com',"
echo "  'YOUR_SHOPIFY_ACCESS_TOKEN_HERE',  -- Replace with actual token from AI-Connector app"
echo "  'read_products,write_script_tags',"
echo "  true,"
echo "  true,"
echo "  true,"
echo "  NOW(),"
echo "  'Hearns Hobbies'"
echo ") ON CONFLICT (shop_domain) DO UPDATE SET"
echo "  is_active = true,"
echo "  uninstalled_at = NULL;"
echo ""
echo ""
echo "Option B: Create API Endpoint (Recommended)"
echo "============================================"
echo "Add a registration endpoint to your backend that doesn't require OAuth"
echo "This will be automatically called when the theme is first loaded"
echo ""

# Test the chat endpoint after registration
echo "3. After registration, test with:"
echo "curl -X POST '$API_URL/api/shopify/chat/message?shop=$SHOP_DOMAIN' \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"message\":\"Show me Gundam kits\",\"conversationHistory\":[],\"maxResults\":5}'"
echo ""
