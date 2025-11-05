#!/bin/bash

# Test Shopify Chat API Connection
# Tests the connection between Shopify theme and Railway backend

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
API_URL="https://shopify-data-api-production.up.railway.app"
SHOP_DOMAIN="hearnshobbies.myshopify.com"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Shopify Chat API Connection Test${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Test 1: Backend Health Check
echo -e "${YELLOW}Test 1: Backend Health Check${NC}"
echo "Testing: ${API_URL}/api/health"
echo ""

HEALTH_RESPONSE=$(curl -s "${API_URL}/api/health")
HEALTH_STATUS=$(echo $HEALTH_RESPONSE | grep -o '"status":"UP"' || echo "")

if [ ! -z "$HEALTH_STATUS" ]; then
    echo -e "${GREEN}✓ Backend is UP and running${NC}"
    echo "Response: ${HEALTH_RESPONSE}"
else
    echo -e "${RED}✗ Backend is DOWN or not responding${NC}"
    echo "Response: ${HEALTH_RESPONSE}"
    exit 1
fi

echo ""
echo "---"
echo ""

# Test 2: Chat Endpoint Test
echo -e "${YELLOW}Test 2: Chat Endpoint (Shop Registration Check)${NC}"
echo "Testing: ${API_URL}/api/shopify/chat/message?shop=${SHOP_DOMAIN}"
echo ""

CHAT_REQUEST='{
  "message": "test connection",
  "conversationHistory": [],
  "maxResults": 3
}'

CHAT_RESPONSE=$(curl -s -X POST "${API_URL}/api/shopify/chat/message?shop=${SHOP_DOMAIN}" \
  -H "Content-Type: application/json" \
  -d "$CHAT_REQUEST")

# Check for shop not found error
SHOP_NOT_FOUND=$(echo $CHAT_RESPONSE | grep -o '"Shop not found or inactive"' || echo "")

if [ ! -z "$SHOP_NOT_FOUND" ]; then
    echo -e "${RED}✗ Shop not registered in database${NC}"
    echo "Response: ${CHAT_RESPONSE}"
    echo ""
    echo -e "${YELLOW}Action Required:${NC}"
    echo "1. Set SHOPIFY_ACCESS_TOKEN environment variable on Railway"
    echo "2. Redeploy Railway to run migration V009"
    echo "3. Run this test again"
    echo ""
    echo "See: docs/SHOPIFY_THEME_CONNECTION_GUIDE.md (Step 2)"
    exit 1
fi

# Check for successful response
RESPONSE_FIELD=$(echo $CHAT_RESPONSE | grep -o '"response"' || echo "")

if [ ! -z "$RESPONSE_FIELD" ]; then
    echo -e "${GREEN}✓ Shop is registered and AI is responding${NC}"
    echo "Response preview: $(echo $CHAT_RESPONSE | cut -c1-200)..."
else
    echo -e "${RED}✗ Unexpected response from chat endpoint${NC}"
    echo "Response: ${CHAT_RESPONSE}"
    exit 1
fi

echo ""
echo "---"
echo ""

# Test 3: CORS Check
echo -e "${YELLOW}Test 3: CORS Configuration Check${NC}"
echo "Checking if storefront domain is allowed..."
echo ""

CORS_RESPONSE=$(curl -s -X OPTIONS "${API_URL}/api/shopify/chat/message" \
  -H "Origin: https://hearnshobbies.com" \
  -H "Access-Control-Request-Method: POST" \
  -I | grep -i "access-control")

if [ ! -z "$CORS_RESPONSE" ]; then
    echo -e "${GREEN}✓ CORS is configured${NC}"
    echo "$CORS_RESPONSE"
else
    echo -e "${YELLOW}⚠ Could not verify CORS (may still work)${NC}"
fi

echo ""
echo "---"
echo ""

# Summary
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}Connection Test Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${GREEN}✓ Backend is healthy and responding${NC}"
echo -e "${GREEN}✓ Shop is registered in database${NC}"
echo -e "${GREEN}✓ Chat endpoint is functional${NC}"
echo ""
echo -e "${BLUE}Next Steps:${NC}"
echo "1. Update Shopify theme settings with API URL:"
echo "   ${API_URL}/api/shopify/chat/message"
echo ""
echo "2. Test from Shopify storefront:"
echo "   - Open: https://hearnshobbies.com"
echo "   - Press: Cmd/Ctrl + K"
echo "   - Type: Show me Gundam model kits"
echo ""
echo "3. Check browser console (F12) for API calls"
echo ""
echo "See full guide: docs/SHOPIFY_THEME_CONNECTION_GUIDE.md"
echo ""
