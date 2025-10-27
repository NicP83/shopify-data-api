# Blog Content Product Link Agent - User Guide

## Overview

The **Blog Content Product Link Agent** is a specialized AI assistant designed to help content creators at Hearns Hobbies quickly find in-stock products and generate formatted, clickable links for use in blogs, newsletters, and marketing materials.

## Key Features

✅ **In-Stock Only** - Returns only products that have available inventory
✅ **Clickable Links** - Generates direct product page URLs
✅ **Add-to-Cart URLs** - Creates instant add-to-cart links for each variant
✅ **Markdown Formatted** - Ready to copy-paste into blog posts
✅ **Multiple Filters** - Search by name, category, vendor, or product type
✅ **Variant Support** - Lists all in-stock variants with individual cart links

## How to Use

### 1. Access the Agent

The agent can be accessed through the SEO Agent interface:

1. Navigate to the SEO Agent chat interface
2. Configure the agent by selecting "Blog Content Product Link Agent"
3. Ensure the `get_in_stock_products_with_links` tool is enabled
4. Start asking for product links!

### 2. Example Queries

#### Find Specific Products
```
"Find me Tamiya model kits that are in stock"
"Show me available Gundam models"
"What X-Acto tools do we have?"
```

#### Browse by Category
```
"List all in-stock plastic kits"
"Show me available paints from Vallejo"
"Find tools and accessories in stock"
```

#### General Searches
```
"Show me our bestselling items in stock"
"Find products for beginners"
"What's new in stock this week?"
```

### 3. Response Format

The agent returns formatted markdown that looks like this:

```markdown
### Available Products:

**1. [Tamiya 1/35 M4 Sherman Tank](https://hearnshobbies.com.au/products/tamiya-m4-sherman)**
- Price: $45.99
- SKU: TAM35190
- In Stock: 5 units
- [Add to Cart →](https://hearnshobbies.com.au/cart/add?id=12345)
- ![Product Image](https://cdn.shopify.com/...)

**2. [Vallejo Model Color Paint Set](https://hearnshobbies.com.au/products/vallejo-basic-colors)**
- Price: $29.99
- SKU: VAL70140
- In Stock: 12 units
- Variants:
  - [Basic Colors (12 bottles) - Add to Cart →](https://hearnshobbies.com.au/cart/add?id=67890)
  - [Advanced Colors (16 bottles) - Add to Cart →](https://hearnshobbies.com.au/cart/add?id=67891)
```

### 4. Copy and Use in Blogs

Simply copy the formatted output and paste it directly into your:
- WordPress blog posts
- Email newsletters (Mailchimp, etc.)
- Marketing materials
- Social media posts
- Product roundup articles

## Technical Details

### Tool: `get_in_stock_products_with_links`

**Type:** SHOPIFY
**Handler:** `GetInStockProductsWithLinksToolHandler`

**Input Parameters:**
```json
{
  "query": "search term",           // Optional: Product search query
  "limit": 20,                      // Optional: Max products to return (default: 20)
  "productType": "PLASTIC KITS"     // Optional: Filter by product type
}
```

**Output Format:**
```json
{
  "products": [
    {
      "title": "Product Name",
      "productUrl": "https://hearnshobbies.com.au/products/...",
      "vendor": "Tamiya",
      "productType": "PLASTIC KITS",
      "variants": [
        {
          "title": "Default Title",
          "sku": "TAM12345",
          "price": "45.99",
          "inventoryQuantity": 5,
          "addToCartUrl": "https://hearnshobbies.com.au/cart/add?id=12345"
        }
      ],
      "imageUrl": "https://cdn.shopify.com/..."
    }
  ],
  "totalFound": 10,
  "inStockCount": 8,
  "message": "Found 8 in-stock products out of 10 total products matching your query"
}
```

### Product Types Available

Common product types you can filter by:
- `PLASTIC KITS`
- `PAINTS`
- `TOOLS`
- `BRUSHES`
- `ACCESSORIES`
- `GLUES & ADHESIVES`
- `DECALS`
- `WEATHERING`
- `REFERENCE BOOKS`

## API Integration

### Direct API Access

You can also access the underlying tool directly via REST API:

**Endpoint:** `POST /api/seo-agent/chat`

**Request Body:**
```json
{
  "message": "Find me in-stock Gundam models",
  "config": {
    "selectedTools": [10],  // ID of get_in_stock_products_with_links tool
    "selectedAgents": [5],  // ID of Blog Content Agent
    "llmConfig": {
      "model": "claude-3-5-sonnet-20241022",
      "temperature": 0.7,
      "maxTokens": 4096
    }
  }
}
```

### Using with SEO Agent System

The Blog Content Agent integrates seamlessly with the SEO Agent orchestration system:

1. **Configure the SEO Agent** to use the Blog Content Agent as a sub-agent
2. **Enable the tool** in your agent configuration
3. **Chat naturally** - the agent will automatically use the tool when appropriate

## Best Practices

### For Content Creators

1. **Be Specific** - The more specific your search, the better the results
   - ❌ "Show me models"
   - ✅ "Show me 1/35 scale WWII tank models from Tamiya"

2. **Filter by Category** - Use product types to narrow down results
   - "Find paints in stock" → Better than "Find art supplies"

3. **Check Stock Numbers** - The agent shows exact inventory counts
   - Plan your blog content around well-stocked items
   - Avoid featuring items with only 1-2 units in stock

4. **Use Multiple Variants** - If a product has variants, link to specific options
   - Each variant gets its own add-to-cart link
   - Perfect for "buy this exact configuration" scenarios

### For Developers

1. **Cache Responses** - Product data doesn't change frequently
   - Consider caching for 5-10 minutes to reduce API calls

2. **Handle Empty Results** - Not all searches will return in-stock products
   - Always check `inStockCount` in the response
   - Provide fallback content or suggestions

3. **Respect Rate Limits** - The Shopify API has rate limits
   - The tool includes built-in rate limiting
   - Avoid rapid-fire requests

4. **Error Handling** - Always handle potential errors
   - Network issues
   - Invalid product types
   - API timeouts

## Troubleshooting

### No Results Returned

**Problem:** Agent says "No products found"

**Solutions:**
- Check spelling of product names
- Try broader search terms
- Verify products are actually in stock in Shopify
- Check if product type filter is too restrictive

### Missing Add-to-Cart Links

**Problem:** Product shown but no cart link

**Solutions:**
- Verify variant has inventory > 0
- Check Shopify product configuration
- Ensure variant is not set to "Don't track inventory"

### Links Not Working

**Problem:** Clicking links leads to 404 page

**Solutions:**
- Verify store URL is configured correctly (`shopify.store.url` in application.properties)
- Check if product is published to online store
- Ensure product handle hasn't changed

## Configuration

### Environment Variables

```bash
# Required
SHOPIFY_STORE_URL=https://hearnshobbies.com.au
SHOPIFY_ACCESS_TOKEN=your_token_here
ANTHROPIC_API_KEY=your_claude_key_here

# Optional
SHOPIFY_API_VERSION=2025-01
```

### Application Properties

```properties
# Store URL for link generation
shopify.store.url=https://hearnshobbies.com.au

# API Configuration
shopify.api.version=2025-01
shopify.api.rate-limit.requests-per-second=2
```

## Example Use Cases

### 1. Weekly Newsletter

**Query:** "Find 5 popular model kits in stock for this week's newsletter"

**Use Case:** Quick product recommendations for weekly email campaigns

### 2. Blog Post - New Arrivals

**Query:** "Show me all in-stock products from the last month"

**Use Case:** Create a "New Arrivals" blog post with direct purchase links

### 3. Category Spotlight

**Query:** "Find all in-stock weathering products from AK Interactive"

**Use Case:** Write a detailed guide about weathering techniques with buy links

### 4. Beginner's Guide

**Query:** "Find starter kits and basic tools for beginners"

**Use Case:** Create a "Getting Started" guide with recommended products

### 5. Sale Promotions

**Query:** "Show me all in-stock Tamiya kits"

**Use Case:** Prepare content for a brand-specific sale event

## Support

For technical support or questions:
- Check the main project README at `/Users/np/shopify-data-api/README.md`
- Review API documentation at `/Users/np/shopify-data-api/docs/API_REFERENCE.md`
- Contact the development team

## Changelog

### Version 1.0 (2025-10-27)
- Initial release
- In-stock filtering
- Clickable product links
- Add-to-cart URL generation
- Markdown formatting
- Multi-variant support
- Product type filtering
- Image URL inclusion

---

**Note:** This agent is specifically designed for Hearns Hobbies and uses the Shopify store at hearnshobbies.com.au. The URLs and product data are real-time from the Shopify API.
