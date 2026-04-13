# 🎯 Snippet Deployment Guide for Header Integration

## Developer Insight Applied

**From Developer:** "we converted the dual search bar into a snippet instead of a section to be able to successfully insert it into the header"

This is the **correct approach** for header integration! Here's the complete guide.

---

## Why Snippets for Header?

### **Blocks/Sections vs Snippets**

| Feature | Blocks/Sections | Snippets |
|---------|----------------|----------|
| **Has {% schema %}** | ✅ Yes | ❌ No |
| **Has settings** | ✅ block.settings | ❌ No (uses parameters) |
| **Can be placed via Customizer** | ✅ Yes | ❌ No |
| **Can be rendered anywhere** | ❌ No | ✅ Yes with {% render %} |
| **Can be in header code** | ❌ Limited | ✅ Yes |
| **Customizable by users** | ✅ Yes | ❌ No (hardcoded) |

### **Why Your Developer Converted It:**

**Problem:**
```liquid
<!-- Blocks can't be directly rendered in header code -->
<header>
  {% block 'dual-search-bar' %} ❌ This doesn't work!
</header>
```

**Solution:**
```liquid
<!-- Snippets CAN be rendered anywhere -->
<header>
  {% render 'dual-search-bar-snippet' %} ✅ This works!
</header>
```

---

## Files for Snippet Deployment

### **1. JavaScript (Header-Safe v2.2)**
- **File:** `dual-search-enhanced-v2.2-header-safe.js`
- **Location:** Shopify → Assets → `dual-search.js`
- **Why:** Handles timing when code loads in header

### **2. Search Bar Snippet**
- **File:** `snippets/dual-search-bar-snippet.liquid`
- **Location:** Shopify → Snippets → `dual-search-bar-snippet.liquid`
- **Why:** Snippet version without {% schema %}, can be rendered in header

### **3. Floating Button (Optional)**
- **File:** `blocks/floating-ai-button.liquid`
- **Location:** Shopify → Blocks → `floating-ai-button.liquid`
- **Why:** Can stay as block (added via Customizer)

---

## Deployment Steps

### **STEP 1: Create the Snippet**

1. **Shopify Admin** → **Online Store** → **Themes** → **Edit code**

2. **Snippets folder** → **Add a new snippet**
   - Name: `dual-search-bar-snippet`
   - Click **Create snippet**

3. **Copy content from:**
   `/Users/np/shopify-data-api/shopify-theme-extension/snippets/dual-search-bar-snippet.liquid`

4. **Paste into new snippet**

5. **Click Save** ✅

---

### **STEP 2: Render Snippet in Header**

1. **Go to Sections folder**

2. **Find your header file** (usually `header.liquid` or `header.section.liquid`)

3. **Choose where to insert:**
   ```liquid
   <header class="site-header">
     <div class="header-container">

       <!-- Your existing header content -->
       <div class="logo">...</div>

       <!-- INSERT SEARCH BAR HERE -->
       {% render 'dual-search-bar-snippet' %}

       <!-- Your existing header content -->
       <div class="cart-icon">...</div>

     </div>
   </header>
   ```

4. **Click Save** ✅

---

### **STEP 3: Update JavaScript to v2.2**

1. **Go to Assets folder**

2. **Find or create** `dual-search.js`

3. **Replace entire content** with:
   `/Users/np/shopify-data-api/shopify-theme-extension/dual-search-enhanced-v2.2-header-safe.js`

4. **Click Save** ✅

---

### **STEP 4: Clear ALL Caches**

1. **Browser cache:**
   - Mac: Cmd + Shift + R
   - Windows: Ctrl + Shift + F5

2. **Test in Incognito mode**

3. **Wait 2-3 minutes** for Shopify CDN to update

---

## Testing the Snippet Deployment

### **Test 1: Verify Snippet Rendered**

1. Open your site
2. Right-click anywhere → **View Page Source**
3. Search for (Cmd+F): `ai-dual-search-form`
4. Should see the search bar HTML in the header section ✅

---

### **Test 2: Console Check**

1. Open browser console (F12 → Console)
2. Look for initialization messages:

**✅ Good signs:**
```
ℹ️ AI Search script loaded (header-safe v2.2), waiting for page load...
✅ Dual search bar snippet initialized
✅ AI Search initialized successfully (header-safe mode)
✅ Search submit handler attached to #ai-dual-search-form ONLY (header-safe)
```

**❌ Bad signs:**
```
❌ Uncaught ReferenceError: ...
❌ Cannot read property 'addEventListener' of null
⚠️ block is not defined (means you're using block version, not snippet)
```

---

### **Test 3: Critical Functionality**

Test on **multiple page types**:

#### **Homepage:**
- [ ] Search bar visible in header
- [ ] Can toggle between Search / Ask Camilla
- [ ] No console errors

#### **Gift Card Page:**
- [ ] Variant dropdown works ✅ **CRITICAL**
- [ ] Can select amounts
- [ ] Price updates
- [ ] Add to cart works
- [ ] Search bar still in header

#### **Product Page:**
- [ ] "View more" button visible ✅ **CRITICAL**
- [ ] Variant selectors work
- [ ] Dropdowns work ✅ **CRITICAL**
- [ ] Search bar in header

#### **Collection Page:**
- [ ] Products load
- [ ] Filters work
- [ ] Search bar in header

---

## Differences: Block vs Snippet Version

### **Block Version (Original)**

```liquid
<!-- Has schema -->
{% schema %}
{
  "name": "Dual Search Bar",
  "settings": [
    {
      "type": "color",
      "id": "primary_color",
      "default": "#212b36"
    }
  ]
}
{% endschema %}

<!-- Uses block.settings -->
<style>
  .mode-toggle-btn.active {
    background: {{ block.settings.primary_color }};
  }
</style>
```

**Pros:**
- ✅ User-customizable via Theme Editor
- ✅ Can change colors without code

**Cons:**
- ❌ Can't be rendered in header
- ❌ Must be placed via Customizer

---

### **Snippet Version (New)**

```liquid
<!-- NO schema -->
<!-- Hardcoded values -->
<style>
  .mode-toggle-btn.active {
    background: #212b36; /* Hardcoded */
  }
</style>
```

**Pros:**
- ✅ Can be rendered anywhere with {% render %}
- ✅ Works in header
- ✅ More control over placement

**Cons:**
- ❌ Not user-customizable
- ❌ Must edit code to change colors

---

## How to Customize Snippet Colors

If you want to change the primary color:

1. **Open:** Snippets → `dual-search-bar-snippet.liquid`

2. **Find** (around line 72):
   ```css
   .mode-toggle-btn.active {
     background: #212b36; /* Change this color */
     color: white;
   }
   ```

3. **Replace** `#212b36` with your brand color:
   ```css
   .mode-toggle-btn.active {
     background: #FF6B35; /* Your color */
     color: white;
   }
   ```

4. **Also update** focus states (around line 78):
   ```css
   .mode-toggle-btn:focus {
     outline: 2px solid #FF6B35; /* Match your color */
   }
   ```

5. **And input focus** (around line 112):
   ```css
   .search-input:focus {
     border-color: #FF6B35; /* Match your color */
   }
   ```

6. **Save** ✅

---

## Snippet Rendering Options

### **Option A: Direct in Header (Recommended)**

```liquid
<header>
  {% render 'dual-search-bar-snippet' %}
</header>
```

**Best for:** Always visible search bar

---

### **Option B: Conditional Rendering**

```liquid
<header>
  {% if template.name == 'index' or template.name == 'collection' %}
    {% render 'dual-search-bar-snippet' %}
  {% endif %}
</header>
```

**Best for:** Only show on specific pages

---

### **Option C: With Section Wrapper**

```liquid
<section class="header-search">
  {% render 'dual-search-bar-snippet' %}
</section>
```

**Best for:** Extra styling control

---

## Troubleshooting Snippet Deployment

### **Issue: "block is not defined" error**

**Cause:** You're using the block version, not snippet version

**Fix:**
1. Make sure you created the SNIPPET (not block)
2. File should be in **Snippets** folder
3. File should NOT have `{% schema %}`
4. File should NOT reference `{{ block.settings }}`

---

### **Issue: Search bar not showing in header**

**Check:**
1. Did you add `{% render 'dual-search-bar-snippet' %}` to header?
2. Is the snippet file named exactly `dual-search-bar-snippet.liquid`?
3. View page source - do you see `ai-dual-search-form`?

**Debug:**
```liquid
<!-- Add this to test rendering -->
{% render 'dual-search-bar-snippet' %}
<p>If you see this but not search bar, check snippet file</p>
```

---

### **Issue: Still seeing gift card / dropdown issues**

**Check:**
1. Are you using v2.2 header-safe JavaScript?
2. Console shows "header-safe v2.2" message?
3. Clear browser cache aggressively

**Verify v2.2 is loaded:**
- Open: Assets → `dual-search.js`
- Search for: "header-safe v2.2"
- Should see this in the file ✅

---

### **Issue: JavaScript not working**

**Check console for:**
```
✅ Dual search bar snippet initialized  ← Snippet loaded
✅ AI Search initialized successfully (header-safe mode)  ← JS loaded
```

**If missing:**
1. Check if `dual-search.js` is included in theme
2. Verify file isn't cached
3. Check for JavaScript syntax errors

---

## Where to Place Snippet in Header

### **Typical Header Structure:**

```liquid
<header class="site-header">
  <div class="header-row">

    <!-- Logo (left) -->
    <div class="header-logo">
      <a href="/">Logo</a>
    </div>

    <!-- SEARCH BAR (center) ← INSERT HERE -->
    <div class="header-search">
      {% render 'dual-search-bar-snippet' %}
    </div>

    <!-- Icons (right) -->
    <div class="header-icons">
      <a href="/cart">Cart</a>
    </div>

  </div>
</header>
```

---

### **Mobile-First Header:**

```liquid
<header>
  <!-- Desktop header -->
  <div class="header-desktop">
    <div class="logo">...</div>
    {% render 'dual-search-bar-snippet' %}
    <div class="icons">...</div>
  </div>

  <!-- Mobile header -->
  <div class="header-mobile">
    <button class="menu-toggle">☰</button>
    <div class="logo">...</div>
    <div class="icons">...</div>
    <!-- Search bar in mobile menu or separate row -->
  </div>
</header>
```

---

## Complete File Checklist

Before going live, verify:

- [ ] **Snippet created:** Snippets → `dual-search-bar-snippet.liquid`
- [ ] **Snippet has NO {% schema %}**
- [ ] **Snippet uses hardcoded colors** (not block.settings)
- [ ] **Header renders snippet:** `{% render 'dual-search-bar-snippet' %}`
- [ ] **JavaScript updated:** Assets → `dual-search.js` = v2.2 header-safe
- [ ] **Cache cleared:** Browser + Incognito mode
- [ ] **Console shows:** "header-safe v2.2" and "snippet initialized"
- [ ] **Gift cards work:** Variant selection functional ✅
- [ ] **Dropdowns work:** Hover menus show ✅
- [ ] **Product pages work:** "View more" visible ✅

---

## Success Criteria

✅ **Snippet deployment successful when:**

| Test | Result |
|------|--------|
| Search bar in header on all pages | ✅ |
| Toggle works (Search / Ask Camilla) | ✅ |
| Gift card variants work | ✅ |
| Dropdown hover menus work | ✅ |
| Product "View more" visible | ✅ |
| Console shows "header-safe v2.2" | ✅ |
| Console shows "snippet initialized" | ✅ |
| No JavaScript errors | ✅ |
| AI search submits correctly | ✅ |

If **ALL ✅**, deployment is successful!

If **ANY ❌**, see troubleshooting section or use rollback.

---

## Rollback

If snippet deployment causes issues:

### **Quick Rollback:**

1. **Edit header file**
2. **Remove or comment out:**
   ```liquid
   {# {% render 'dual-search-bar-snippet' %} #}
   ```
3. **Save** ✅
4. Site returns to normal instantly

### **Full Rollback:**

See `EMERGENCY-ROLLBACK.md` for complete instructions.

---

## Advanced: Passing Parameters to Snippet

Snippets can accept parameters:

```liquid
<!-- In header -->
{% render 'dual-search-bar-snippet',
  primary_color: '#FF6B35',
  show_on_mobile: true
%}
```

Then in snippet:
```liquid
<style>
  .mode-toggle-btn.active {
    background: {{ primary_color | default: '#212b36' }};
  }
</style>

{% if show_on_mobile %}
  <!-- Mobile version -->
{% endif %}
```

This allows customization without {% schema %}!

---

**Document Version:** 2.2 Snippet Edition
**Date:** 2025-12-03
**Purpose:** Guide for snippet-based header deployment
**Status:** Ready for snippet deployment
**Tested:** Compatible with v2.2 header-safe JavaScript
