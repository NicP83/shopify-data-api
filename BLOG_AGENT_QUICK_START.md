# Blog Content Agent - Quick Start Guide

## TL;DR

Get in-stock product links with add-to-cart URLs for your blogs in seconds!

## Quick Example

### Ask the Agent:
```
"Find me in-stock Gundam models for a blog post"
```

### Get Formatted Output:
```markdown
### Available Gundam Models:

**1. [Bandai RG 1/144 RX-78-2 Gundam](https://hearnshobbies.com.au/products/rg-rx-78-2)**
- Price: $35.99
- SKU: BAN5055362
- In Stock: 8 units
- [Add to Cart →](https://hearnshobbies.com.au/cart/add?id=42345678901234)

**2. [Bandai MG 1/100 Gundam Barbatos](https://hearnshobbies.com.au/products/mg-barbatos)**
- Price: $54.99
- SKU: BAN5057405
- In Stock: 3 units
- [Add to Cart →](https://hearnshobbies.com.au/cart/add?id=42345678901235)
```

### Copy & Paste
Just copy the markdown and paste it into your blog! The links work immediately.

---

## How to Access

### Option 1: Via SEO Agent Interface (Recommended)

1. Open the SEO Agent chat at: `http://your-api-url/seo-agent`
2. Select "Blog Content Product Link Agent" from agent dropdown
3. Enable the `get_in_stock_products_with_links` tool
4. Start chatting!

### Option 2: Direct API Call

```bash
curl -X POST http://your-api-url/api/seo-agent/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Find me in-stock Gundam models",
    "config": {
      "selectedAgents": [5],
      "selectedTools": [10],
      "llmConfig": {
        "model": "claude-3-5-sonnet-20241022",
        "temperature": 0.7
      }
    }
  }'
```

---

## Common Queries

### Search by Product Name
```
"Find Tamiya kits in stock"
"Show me X-Acto tools"
"List available Vallejo paints"
```

### Filter by Category
```
"Show plastic kits in stock"
"Find all available paint sets"
"List weathering products"
```

### Browse by Brand
```
"What Tamiya products are in stock?"
"Show me Bandai Gundam kits"
"Find AK Interactive weathering supplies"
```

### Specific Requests
```
"I need 5 beginner-friendly kits for a blog post"
"Show me paints suitable for military models"
"Find tools under $30"
```

---

## What You Get

Every response includes:

✅ **Product Name** - Clear, descriptive title
✅ **Product Link** - Direct URL to product page
✅ **Price** - Current price in AUD
✅ **SKU** - Stock keeping unit for reference
✅ **Stock Level** - Exact inventory count
✅ **Add to Cart Link** - One-click purchase URL
✅ **Image URL** - Product photo (when available)
✅ **Variants** - Multiple options if available

---

## Pro Tips

### 🎯 Be Specific
- ✅ "Find 1/35 scale WWII tank models"
- ❌ "Show me tanks"

### 🏷️ Use Product Types
- "Find products in PLASTIC KITS category"
- "Show PAINTS from Vallejo"

### 📊 Check Stock Levels
- Products with 1-2 units might sell out quickly
- Feature items with healthy stock (5+ units)

### 🔗 Test Your Links
- Always verify links work before publishing
- Check mobile responsiveness

---

## Example Prompts by Use Case

### Weekly Newsletter
```
"Give me 5 popular items in stock for this week's newsletter"
```

### Blog Post - Product Review
```
"Find all in-stock products related to aircraft weathering"
```

### Beginner's Guide
```
"Show me starter kits and essential tools for beginners"
```

### Brand Spotlight
```
"List all in-stock Tamiya products for a brand feature"
```

### Sale Preparation
```
"Find all products from Vallejo and AK Interactive in stock"
```

---

## Troubleshooting

### ❓ No Results?
- Try broader search terms
- Check spelling
- Remove product type filter
- Verify items are actually in stock

### ❓ Links Don't Work?
- Ensure product is published online
- Check store URL configuration
- Verify product handle is correct

### ❓ Need Help?
- Read full guide: `BLOG_CONTENT_AGENT_GUIDE.md`
- Check API docs: `docs/API_REFERENCE.md`
- Contact dev team

---

## Next Steps

1. **Try It Out** - Start with a simple query
2. **Customize Format** - Adapt the markdown to your blog style
3. **Save Templates** - Create reusable query templates
4. **Automate** - Consider scheduled blog posts with API integration

---

## Example Blog Post Template

```markdown
# New Arrivals at Hearns Hobbies - October 2025

Check out these amazing kits that just arrived and are ready to ship!

[PASTE AGENT OUTPUT HERE]

All products are in stock and available for immediate dispatch.
Click "Add to Cart" to secure yours today!

Need help choosing? Contact our team at info@hearnshobbies.com.au
```

---

**Quick Reference:**
- Agent Name: `Blog Content Product Link Agent`
- Tool Name: `get_in_stock_products_with_links`
- API Endpoint: `/api/seo-agent/chat`
- Documentation: `BLOG_CONTENT_AGENT_GUIDE.md`

**Start using it now and save hours on content creation!** 🚀
