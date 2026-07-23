# Hearns Hobbies — AI Customer Service & Agent Platform

An AI-powered customer service and sales operations platform for [Hearns Hobbies](https://hearnshobbies.myshopify.com), built on Spring Boot and the Anthropic Claude API. It began life as a Shopify data API (hence the repo name) and now runs:

- **Storefront AI chatbot** — a Claude-powered assistant embedded in the Shopify theme (`shopify-theme-extension/`), with a dynamic tool registry (product search/browse/compare, inventory checks, order lookup, promotions, agent delegation), per-shop configuration, conversation memory, and cost/usage analytics.
- **Multi-agent system** — database-defined agents with their own tools (Shopify catalog, ERP via MCP, native web search), executed through a Claude tool loop; the chatbot can delegate specialist tasks to them.
- **Workflow automation** — JSON-defined workflows with ordered steps, human-in-the-loop approval steps, cron scheduling, and a React admin UI (editor, gallery, executions, approval queue).
- **Inventory analysis** — sales velocity tracking, stock alerts, reorder recommendations, and an inventory AI assistant, fed by Shopify plus an external ERP MCP server.
- **Analytics & admin** — sales/channel analytics, chat analytics (tokens, cost, conversions), versioned config with rollback, prompt testing, activity logging.
- **Shopify data REST API** — the original clean REST layer over the Shopify GraphQL Admin API (products, orders, customers, inventory, fulfillment) with rate limiting and retry.

## Tech Stack

- **Backend:** Java 17, Spring Boot 3.2, Maven, reactive WebClient
- **Database:** PostgreSQL (JPA + Flyway migrations V002–V019)
- **AI:** Anthropic Claude API (default model `claude-sonnet-4-6`, configurable via `ANTHROPIC_MODEL`)
- **Frontend:** React 18 + Vite SPA (`frontend/`), served from Spring static resources in production
- **Deployment:** Railway
- **Integrations:** Shopify GraphQL Admin API, Shopify OAuth app install, ERP MCP server (JSON-RPC)

## Quick Start

```bash
# Backend (needs .env — copy .env.example and fill in Shopify + Anthropic + DB creds)
mvn spring-boot:run                  # http://localhost:8080

# Frontend dev server
cd frontend && npm install && npm run dev   # http://localhost:3000, proxies /api → :8080

# Production frontend build (into Spring static resources)
cd frontend && npm run build:deploy
```

Key env vars: `DATABASE_URL/USERNAME/PASSWORD`, `SHOPIFY_SHOP_URL`, `SHOPIFY_ACCESS_TOKEN`, `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL`, `CRS_MCP_URL`. See `.env.example` and `src/main/resources/application.yml` for the full list.

## Project Structure

```
src/main/java/com/shopify/api/
├── controller/          # REST endpoints (~22 controllers)
│   ├── ShopifyChatController.java   # storefront chatbot endpoint
│   ├── agent/                       # agents, workflows, tools, schedules, approvals
│   └── ...                          # products, orders, analytics, admin, OAuth, SEO, inventory
├── service/
│   ├── ChatAgentService.java        # storefront chatbot brain (Claude tool loop)
│   ├── ChatToolRegistry.java        # auto-discovering chatbot tool registry
│   ├── agent/                       # multi-agent engine + workflow orchestrator
│   ├── inventory/                   # inventory analysis module
│   └── tool/                        # DB-agent tool handlers (incl. agent delegation)
├── handler/tool/        # chatbot tools (ChatToolHandler beans, auto-registered)
├── client/              # ShopifyGraphQLClient, MCPClient
├── model/ repository/   # JPA entities + Spring Data repos (agent, inventory, chat domains)
└── config/ util/ ...    # Shopify/CORS/cache config, rate limiter, AOP logging
src/main/resources/db/migration/     # Flyway V002–V019
frontend/                # React SPA (admin hub: chat, agents, workflows, inventory, analytics)
shopify-theme-extension/ # storefront chat widget + deployment guide
docs/                    # architecture & module docs (see index below)
```

## Documentation

- **`CLAUDE.md`** — authoritative architecture guide (start here)
- `docs/multi-agent/` — multi-agent system: architecture, DB schema, API spec, workflow examples
- `docs/mcp/` — ERP MCP server tool reference
- `docs/API_REFERENCE.md`, `ENDPOINTS_QUICK_REFERENCE.md`, `USAGE_EXAMPLES.md` — REST API usage
- `docs/RAILWAY_DEPLOYMENT.md`, `DEPLOYMENT_GUIDE.md`, `QUICKSTART.md` — setup & deploy
- `docs/INVENTORY_ANALYSIS_MODULE.md`, `docs/seo-agent/`, `docs/ai-search-implementation/` — module docs
- `shopify-theme-extension/README.md` — chat widget deployment
- `docs/archive/` — historical status/checkpoint docs (superseded; kept for reference)

## License

Private — Hearns Hobbies internal.
