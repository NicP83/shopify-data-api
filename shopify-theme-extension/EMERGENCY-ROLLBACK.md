# 🚨 EMERGENCY ROLLBACK INSTRUCTIONS

## If Ask Camilla Features Are Breaking Your Site

Follow these steps to **immediately** remove all Ask Camilla features and restore your site to working condition:

---

## ⚡ Quick Rollback (5 Minutes)

### **Option A: Remove via Theme Customizer (Easiest)**

1. Shopify Admin → **Online Store** → **Themes** → **Customize**
2. Look for these blocks on ANY page (especially header):
   - **"Dual Search Bar"** → Click → **Remove section/block**
   - **"Floating AI Button"** → Click → **Remove section/block**
   - **"AI Search Modal"** → Click → **Remove section/block**
3. Click **Save** (top right)
4. Your site is now back to normal! ✅

---

### **Option B: Disable via Code (If Customizer doesn't work)**

1. Shopify Admin → **Online Store** → **Themes** → **...(Actions)** → **Edit code**

2. **Find where blocks are loaded:**
   - Click **Layout** → **theme.liquid**
   - Search for (Cmd+F): `ai-search-assets`
   - Comment out this line:
     ```liquid
     {# {% render 'ai-search-assets' %} #}
     ```
   - Click **Save**

3. **Or delete the files entirely:**
   - Go to **Snippets** → Delete: `ai-search-assets.liquid`
   - Go to **Blocks** → Delete:
     - `dual-search-bar.liquid`
     - `floating-ai-button.liquid`
     - `search-modal.liquid`
   - Go to **Assets** → Delete:
     - `dual-search.js` (or `dual-search-enhanced.js`)
     - `dual-search.css` (or `dual-search-enhanced.css`)
     - `ai-search-client.js`

4. Click **Save** after each deletion

---

## 🔍 Verify Site is Working

After rollback, check:
- [ ] Gift card variant selection works
- [ ] Dropdown menus appear on hover
- [ ] Product description "View more" button visible
- [ ] Navigation menus work
- [ ] Cart functions normally
- [ ] Checkout works

---

## 🛡️ What Went Wrong?

Our Ask Camilla code had conflicts with your theme:

### **Issue #1: JavaScript Conflicts**
- Our event listeners interfered with:
  - Gift card variant selectors
  - Dropdown menu hover triggers
  - Product description expand/collapse

### **Issue #2: CSS Conflicts**
- Our z-index and styles covered:
  - Navigation dropdowns
  - Product page elements
  - Theme UI components

### **Issue #3: ID Conflicts**
- Generic IDs like `#search-input` clashed with theme elements

---

## 📝 If You Want to Try Again Later

**Before re-installing, we need to:**

1. **Get theme information:**
   - What Shopify theme are you using? (Dawn, Debut, custom?)
   - Theme version?

2. **Identify conflicts:**
   - Which elements use `#search-input`, `#dual-search-form`, etc.?
   - What JavaScript libraries does your theme use?

3. **Create isolated version:**
   - Use unique prefixes for ALL IDs, classes, and variables
   - Scope ALL JavaScript to our components only
   - Use CSS isolation techniques
   - Lower z-index to 50 or below

---

## 🔄 Current State After Rollback

| Feature | Status |
|---------|--------|
| Gift card variants | ✅ Working |
| Dropdown menus | ✅ Working |
| Product descriptions | ✅ Working |
| Ask Camilla search | ❌ Removed |
| AI product assistant | ❌ Removed |

---

## 📞 Need Help?

If rollback doesn't fix the issues:

1. **Clear all caches:**
   - Browser cache: Cmd+Shift+R (Mac) or Ctrl+Shift+F5 (Windows)
   - Try incognito mode
   - Clear Shopify cache (contact Shopify support)

2. **Check for leftover code:**
   - Search theme for: `ai-search`, `camilla`, `dual-search`
   - Remove any references found

3. **Restore from backup:**
   - Shopify Admin → **Online Store** → **Themes**
   - Find previous theme version
   - Click **Actions** → **Publish** (if you have a backup)

---

## ⏱️ Rollback Timeline

- **Via Customizer:** 2-5 minutes
- **Via Code Editor:** 5-10 minutes
- **Cache Clear:** 1-2 minutes
- **Verification:** 5 minutes

**Total time:** 10-20 minutes to fully restore site

---

## ✅ Rollback Complete Checklist

After rollback, confirm:

- [ ] No JavaScript errors in console (F12 → Console)
- [ ] All navigation menus work
- [ ] All product features work (variants, add to cart, descriptions)
- [ ] Gift cards work completely
- [ ] Cart and checkout work
- [ ] No leftover "Ask Camilla" elements visible on site

If ALL checkboxes are ✅, your rollback was successful!

---

**Document Version:** 1.0
**Date:** 2025-12-03
**Purpose:** Emergency recovery from Ask Camilla integration issues
**Status:** Ready to use if needed
