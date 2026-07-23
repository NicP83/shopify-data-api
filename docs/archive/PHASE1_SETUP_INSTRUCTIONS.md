# Phase 1: React Frontend - Setup Instructions

**Date:** 2025-10-11
**Status:** React project created, awaiting npm install

## What's Been Created

### React Application Structure ✅
All React frontend files have been created in the `frontend/` directory:

```
frontend/
├── src/
│   ├── components/
│   │   ├── Navigation.jsx          ✅ Main navigation bar
│   │   ├── SearchBar.jsx           ✅ Search input component
│   │   ├── ProductCard.jsx         ✅ Product card display
│   │   ├── ProductDetail.jsx       ✅ Product detail modal
│   │   └── VariantSelector.jsx     ✅ Variant selection UI
│   ├── pages/
│   │   ├── Dashboard.jsx           ✅ Home/dashboard page
│   │   └── ProductSearch.jsx       ✅ Product search page
│   ├── services/
│   │   └── api.js                  ✅ API service layer
│   ├── utils/
│   │   └── urlHelpers.js           ✅ URL construction utilities
│   ├── App.jsx                     ✅ Main app component
│   ├── main.jsx                    ✅ Entry point
│   └── index.css                   ✅ Global styles with Tailwind
├── index.html                      ✅ HTML template
├── vite.config.js                  ✅ Vite configuration
├── tailwind.config.js              ✅ Tailwind CSS config
├── postcss.config.js               ✅ PostCSS config
├── package.json                    ✅ Dependencies defined
├── .gitignore                      ✅ Git ignore rules
└── README.md                       ✅ Frontend documentation
```

### Spring Boot Configuration ✅
- **CorsConfig.java** created at `src/main/java/com/shopify/api/config/CorsConfig.java`
  - Enables CORS for frontend development
  - Allows localhost:3000 and localhost:8080

### Build Script ✅
- **build-frontend.sh** created at project root
  - Automated build and deployment script
  - Builds React app and copies to Spring Boot static resources

## Next Steps (Manual Action Required)

### Step 1: Fix npm Cache Permissions

There's an npm cache permission issue that needs to be resolved. Run this command:

```bash
sudo chown -R 501:20 "/Users/np/.npm"
```

Then clean the cache:

```bash
cd /Users/np/shopify-data-api/frontend
npm cache clean --force
```

### Step 2: Install Dependencies

```bash
cd /Users/np/shopify-data-api/frontend
npm install
```

This will install:
- React 18
- React Router 6
- Vite (build tool)
- Tailwind CSS 3
- Axios
- All required dependencies

### Step 3: Build the Frontend

Use the automated build script:

```bash
cd /Users/np/shopify-data-api
./build-frontend.sh
```

This script will:
1. Build the React app for production
2. Create `src/main/resources/static/` directory
3. Copy all build files to Spring Boot static resources

### Step 4: Build Spring Boot

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
mvn clean package -DskipTests
```

### Step 5: Test Locally

```bash
SHOPIFY_SHOP_URL=hearnshobbies.myshopify.com \
SHOPIFY_ACCESS_TOKEN=your_token_here \
SHOPIFY_API_VERSION=2025-01 \
SHOPIFY_MAX_POINTS=100 \
PORT=8080 \
mvn spring-boot:run
```

Then open your browser to:
- **Frontend:** http://localhost:8080/
- **API:** http://localhost:8080/api/health

### Step 6: Test the Application

1. **Dashboard**: Should load with system status and navigation
2. **Product Search**:
   - Enter "Gundam" in search box
   - Click Search button
   - View product cards with images
   - Click a product card to open details
   - Test "View Product" and "Add to Cart" buttons
   - Copy cart link and test in browser

### Step 7: Deploy to Railway

Once local testing is successful:

```bash
# Ensure build files are included
git add -A
git status  # Verify static files are included

# Commit changes
git commit -m "$(cat <<'EOF'
Add React frontend for Phase 1

- Product search interface with real-time query
- Dashboard with system status display
- Navigation for all hub modules
- Product detail modal with variant selection
- Cart permalink generation
- Responsive mobile design
- Integrated with Spring Boot backend

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"

# Create WORKING3 checkpoint
git tag WORKING3

# Push to GitHub
git push origin main --tags
```

Railway will automatically deploy the changes.

### Step 8: Test Production

Visit: https://shopify-data-api-production.up.railway.app/

Test all features:
- [ ] Dashboard loads correctly
- [ ] Navigation works
- [ ] Product search returns results
- [ ] Product cards display images and details
- [ ] Product detail modal opens
- [ ] Cart links work
- [ ] System status shows "Shopify Connected: ✅"

## Features Implemented

### Dashboard Page
- System status display (Shopify connection, API version, environment)
- Quick access cards for all 4 modules
- Feature status indicators (Active vs Coming Soon)
- Quick product search shortcut

### Product Search Page
- Real-time search bar
- Search tips and instructions
- Product card grid with:
  - Product images
  - Title, SKU, price
  - Status badge (ACTIVE/DRAFT/ARCHIVED)
  - Vendor and variant count
- Loading states with spinner
- Error handling with user-friendly messages
- "How to use" instructions

### Product Detail Modal
- Full product information
- Image display
- Product description, vendor, type, tags
- Variant selector with:
  - Multiple variant support
  - Price and SKU per variant
  - Stock levels
  - Radio-button style selection
- Action buttons:
  - View Product (opens onlineStoreUrl)
  - Copy Product Link
  - Add to Cart (generates permalink)
  - Copy Cart Link
- Responsive mobile layout

### Navigation
- Horizontal navigation bar
- Active route highlighting
- Module icons for visual clarity
- Links to all 4 future modules

### Technical Features
- CORS configuration for development
- API proxy in Vite (dev mode)
- Static file serving from Spring Boot (production)
- Tailwind CSS for styling
- Axios for API calls
- React Router for navigation
- Loading states and error handling
- Responsive design (mobile-first)

## Troubleshooting

### Issue: npm Install Fails with Permission Error

**Solution:**
```bash
sudo chown -R 501:20 "/Users/np/.npm"
npm cache clean --force
```

### Issue: Port 8080 Already in Use

**Solution:**
```bash
lsof -ti:8080 | xargs kill -9
```

### Issue: React Build Fails

**Solution:**
```bash
cd frontend
rm -rf node_modules package-lock.json
npm install
npm run build
```

### Issue: Static Files Not Served

**Check:**
1. Build script ran successfully: `ls -la src/main/resources/static/`
2. Static files exist: index.html, assets/, etc.
3. Spring Boot packaging included static files: `unzip -l target/*.jar | grep static`

### Issue: API Calls Fail from Frontend

**Check:**
1. CORS configuration is present: `CorsConfig.java`
2. Backend is running on port 8080
3. Check browser console for CORS errors
4. Verify API endpoint paths start with `/api`

## Expected Outcome

After completing all steps, you should have:

✅ **Local Development:**
- React app running with hot reload at localhost:3000 (dev mode)
- Spring Boot serving React app at localhost:8080 (prod mode)
- All API endpoints accessible
- Product search fully functional

✅ **Production Deployment:**
- Single deployment URL serving both frontend and backend
- React app served from Spring Boot static resources
- All features working on Railway
- WORKING3 checkpoint created

## Next Phase Preview

**Phase 2: AI Chat Agent**
- Conversational interface for sales and support
- Claude API integration
- Product recommendations in chat
- Cart link generation in conversations
- Context-aware responses

See `DEVELOPMENT_ROADMAP.md` for full Phase 2 details.

---

**Status:** React project created and configured. Ready for npm install and testing.
**Last Updated:** 2025-10-11
