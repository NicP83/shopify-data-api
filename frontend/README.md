# Customer Service Hub - React Frontend

React-based user interface for the Shopify Customer Service Hub.

## Features

- **Product Search**: Search and view product details with cart link generation
- **Dashboard**: System status and quick navigation
- **Navigation**: Easy access to all hub modules
- **Responsive Design**: Mobile-friendly interface built with Tailwind CSS

## Development

### Prerequisites
- Node.js 18+ and npm
- Backend API running on port 8080

### Local Development

1. Install dependencies:
```bash
npm install
```

2. Start development server:
```bash
npm run dev
```

The app will run at `http://localhost:3000` with hot reload enabled.

### Building for Production

1. Build the React app:
```bash
npm run build
```

2. Copy to Spring Boot (automated):
```bash
npm run build:deploy
```

Or use the root-level build script:
```bash
../build-frontend.sh
```

## Project Structure

```
frontend/
├── src/
│   ├── components/      # Reusable React components
│   │   ├── Navigation.jsx
│   │   ├── SearchBar.jsx
│   │   ├── ProductCard.jsx
│   │   ├── ProductDetail.jsx
│   │   └── VariantSelector.jsx
│   ├── pages/          # Page components
│   │   ├── Dashboard.jsx
│   │   └── ProductSearch.jsx
│   ├── services/       # API service layer
│   │   └── api.js
│   ├── utils/          # Utility functions
│   │   └── urlHelpers.js
│   ├── App.jsx         # Main app component
│   ├── main.jsx        # Entry point
│   └── index.css       # Global styles
├── public/             # Static assets
├── index.html          # HTML template
├── vite.config.js      # Vite configuration
├── tailwind.config.js  # Tailwind CSS config
└── package.json        # Dependencies
```

## Available Scripts

- `npm run dev` - Start development server
- `npm run build` - Build for production
- `npm run preview` - Preview production build
- `npm run build:deploy` - Build and copy to Spring Boot

## Tech Stack

- **React 18** - UI library
- **React Router 6** - Client-side routing
- **Vite** - Build tool and dev server
- **Tailwind CSS 3** - Utility-first CSS framework
- **Axios** - HTTP client

## API Integration

The frontend communicates with the Spring Boot backend via the `/api` endpoints:

- `/api/health` - Health check
- `/api/status` - System status
- `/api/products` - List products
- `/api/products/search` - Search products
- `/api/products/{id}` - Get product by ID

During development, Vite proxies API requests to `http://localhost:8080`.

## Deployment

The React app is built and deployed as static files served by Spring Boot:

1. Run `build-frontend.sh` to build React app
2. Files are copied to `src/main/resources/static/`
3. Spring Boot serves the React app at the root URL
4. API endpoints remain accessible at `/api/*`

## Coming Soon

- 💬 AI Chat Agent interface
- 📊 Analytics Dashboard
- 💰 Market Intelligence module
- 🔔 Real-time notifications
- 🌙 Dark mode support
