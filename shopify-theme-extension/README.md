# AI Product Search Assistant - Shopify Theme Extension

AI-powered product search assistant for Shopify storefronts, specifically designed for Hearn's Hobbies.

## Features

- **Conversational AI Search**: Natural language product search powered by Claude AI
- **Smart Product Recommendations**: AI understands context and suggests relevant products
- **Beautiful UI**: Modern, responsive chat modal with smooth animations
- **Message History**: Maintains conversation context for better results
- **Keyboard Shortcuts**: Quick access with `Cmd/Ctrl + K`
- **Mobile Optimized**: Fully responsive design
- **Easy Integration**: Simple theme blocks that work with any Shopify theme

## Installation

### Method 1: Using Shopify CLI (Recommended)

1. **Install Shopify CLI** (if not already installed):
   ```bash
   npm install -g @shopify/cli @shopify/theme
   ```

2. **Navigate to the extension directory**:
   ```bash
   cd /path/to/shopify-theme-extension
   ```

3. **Deploy to your Shopify store**:
   ```bash
   shopify theme push
   ```

4. **Follow the prompts** to connect to your Shopify store

### Method 2: Manual Installation

1. **Copy extension files** to your Shopify theme:
   - Copy `blocks/search-bar.liquid` → `sections/` in your theme
   - Copy `blocks/search-modal.liquid` → `sections/` in your theme
   - Copy `assets/ai-search-client.js` → `assets/` in your theme
   - Copy `snippets/ai-search-assets.liquid` → `snippets/` in your theme

2. **Add to theme.liquid**:
   Add this line before the closing `</body>` tag:
   ```liquid
   {% render 'ai-search-assets' %}
   ```

3. **Add blocks to your theme** via the Shopify theme editor:
   - Go to Online Store → Themes → Customize
   - Add the "AI Search Bar" block to your header
   - Add the "AI Search Modal" block to your theme (anywhere)

## Configuration

### Backend API Setup

1. **Update API URL** in the theme customizer:
   - Navigate to theme settings
   - Find "AI Search Modal" settings
   - Update `api_url` to your Railway backend URL:
     ```
     https://your-app.railway.app/api/shopify/chat/message
     ```

2. **Configure Shop Domain**:
   - The shop domain is automatically detected from `{{ shop.permanent_domain }}`
   - For hearnshobbies.com, this will be: `hearnshobbies.myshopify.com`

### Customization Options

#### Search Bar Settings

- **Primary Color**: Brand color for buttons and accents
- **Text Color**: Text color for content
- **Position**:
  - `header` - Displays in header section
  - `fixed-bottom` - Floating button at bottom right
  - `inline` - Inline with other content
- **Auto-focus**: Automatically focus search on page load

#### Search Modal Settings

- **Primary Color**: Brand color for UI elements
- **Text Color**: Text color for messages
- **API URL**: Backend API endpoint
- **Maximum Results**: Max products to display (3-20)

### Theme Integration

The extension provides two main blocks:

1. **AI Search Bar** (`search-bar.liquid`)
   - Triggers the chat modal
   - Can be placed in header, as floating button, or inline
   - Fully customizable appearance

2. **AI Search Modal** (`search-modal.liquid`)
   - Full-screen chat interface
   - Message history
   - Product cards with images and prices
   - Typing indicators and error handling

## Usage

### For Customers

1. **Click the search bar** or press `Cmd/Ctrl + K`
2. **Ask natural language questions** like:
   - "Show me Gundam model kits under $50"
   - "I need hobby paints for plastic models"
   - "What tools do I need for scale modeling?"
3. **Browse AI-recommended products** directly in the chat
4. **Click product cards** to view product pages

### For Developers

#### JavaScript API

Access the modal programmatically:

```javascript
// Open modal
window.aiSearchModal.open();

// Close modal
window.aiSearchModal.close();

// Send message programmatically
window.aiSearchModal.client.sendMessage("Show me model kits")
  .then(response => console.log(response));

// Clear conversation history
window.aiSearchModal.client.clearHistory();
```

#### Custom Styling

All CSS classes are prefixed with `ai-` for easy customization:

```css
/* Customize search bar */
.ai-search-trigger {
  /* Your custom styles */
}

/* Customize modal */
.ai-modal-content {
  /* Your custom styles */
}

/* Customize messages */
.ai-message-assistant .ai-message-content {
  /* Your custom styles */
}
```

#### Event Hooks

Listen for modal events:

```javascript
document.addEventListener('DOMContentLoaded', function() {
  const modal = document.getElementById('ai-search-modal');

  // Modal opened
  modal.addEventListener('open', function() {
    console.log('Modal opened');
  });

  // Modal closed
  modal.addEventListener('close', function() {
    console.log('Modal closed');
  });
});
```

## Architecture

### Flow Diagram

```
Customer → Search Bar → Modal → API Client → Backend (Railway)
                                              ↓
                                         Claude AI (Anthropic)
                                              ↓
                                    Product Search (Shopify API)
                                              ↓
Customer ← Product Cards ← Modal ← API Response
```

### API Communication

The extension communicates with your backend using:

- **Endpoint**: `POST /api/shopify/chat/message`
- **Query Param**: `shop=hearnshobbies.myshopify.com`
- **Body**:
  ```json
  {
    "message": "Show me Gundam kits",
    "conversationHistory": [...],
    "maxResults": 10
  }
  ```
- **Response**:
  ```json
  {
    "response": "Here are some Gundam kits...",
    "products": [
      {
        "id": "123",
        "title": "RG 1/144 RX-78-2 Gundam",
        "handle": "rg-rx-78-2-gundam",
        "price": 29.99,
        "image": "https://..."
      }
    ],
    "role": "assistant",
    "timestamp": "2025-10-30T..."
  }
  ```

## Troubleshooting

### Search bar not appearing

1. Ensure `ai-search-assets.liquid` is rendered in `theme.liquid`
2. Check that both blocks are added via theme customizer
3. Clear browser cache and reload

### Modal not opening

1. Check browser console for JavaScript errors
2. Ensure `ai-search-client.js` is loaded
3. Verify modal element exists: `document.getElementById('ai-search-modal')`

### API errors

1. Verify backend is running on Railway
2. Check API URL is correct in theme settings
3. Ensure CORS is configured for `hearnshobbies.com`
4. Check browser network tab for request/response details

### Products not displaying

1. Verify backend returns `products` array in response
2. Check product object structure matches expected format
3. Ensure product handles/IDs are valid

## Browser Support

- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+
- Mobile browsers (iOS Safari 14+, Chrome Mobile)

## Performance

- **Initial Load**: ~15KB (JS + CSS inline)
- **Lazy Loading**: Product images load on-demand
- **Optimized**: Modal only renders when opened
- **Responsive**: Smooth animations with CSS transitions

## Security

- **CORS**: Backend validates origin
- **Shop Verification**: Backend verifies shop is installed
- **No Credentials**: Extension doesn't store sensitive data
- **XSS Protection**: All user input is sanitized

## Support

For issues or questions:
- Check troubleshooting section above
- Review browser console for errors
- Contact development team

## Version

**Current Version**: 1.0.0
**Last Updated**: October 2025
**Shopify API Version**: 2024-01

## License

Proprietary - Hearn's Hobbies © 2025
