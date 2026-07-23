# CLAUDE.md — Project Guide

**What this is:** an AI-powered customer service & multi-agent platform for Hearns Hobbies (hearnshobbies.myshopify.com). Despite the repo name, it is far more than a Shopify data API: it runs a production storefront AI chatbot, a database-driven multi-agent orchestration system, workflow automation with human-in-the-loop approvals, and an inventory analysis module.

**Authoritative doc.** The many `*_STATUS.md` / phase docs in `docs/archive/` are historical snapshots — do not trust them for current state.

## Tech Stack

- **Backend:** Java 17, Spring Boot 3.2 (Maven, `pom.xml`), reactive WebClient throughout
- **Database:** PostgreSQL via JPA (`ddl-auto: none`) + Flyway migrations `src/main/resources/db/migration/` (V002–V019; `repair-on-migrate: true`)
- **AI:** Anthropic Claude API, default model `claude-sonnet-4-6` (env `ANTHROPIC_MODEL`; validated by `ModelValidationService` against `config/ModelConfig.java`)
- **Integrations:** Shopify GraphQL Admin API (`client/ShopifyGraphQLClient.java`), external ERP MCP server over JSON-RPC (`client/MCPClient.java`, env `CRS_MCP_URL`)
- **Frontend:** React 18 + Vite SPA in `frontend/` (Tailwind, react-router v6, axios, recharts, reactflow)
- **Deploy:** Railway (`nixpacks.toml`, `Procfile`); the SPA is built into Spring static resources
- **Theme extension:** `shopify-theme-extension/` — storefront chat widget (see its README)

## Build / Run

```bash
mvn spring-boot:run                 # backend on :8080 (needs .env — see .env.example)
cd frontend && npm run dev          # SPA on :3000, proxies /api → :8080
cd frontend && npm run build:deploy # vite build + copy into src/main/resources/static/
mvn -q compile                      # quick compile check
```

Package root: `com.shopify.api` under `src/main/java/`. Ignore `target/` (build artifacts, incl. duplicated resources).

## Architecture Map

### Storefront chatbot (the production-critical path)
1. `controller/ShopifyChatController.java` — `POST /api/shopify/chat/message?shop=...` (CORS for hearnshobbies + `*.myshopify.com`); validates shop via `ShopifyShopService`, applies per-shop AI config, records `ChatAnalytics` (tokens/cost).
2. `service/ChatAgentService.java` — the chatbot brain. Builds system prompt (DB `system_prompts` per shop, else from `ChatbotConfig`), runs the Claude tool loop. **Max 15 iterations**; on hitting the limit it calls Claude without tools with a summarize nudge instead of erroring. Tool calls in one turn run in parallel (`Mono.zip`).
3. `service/ChatToolRegistry.java` — auto-discovers all `ChatToolHandler` beans, builds tool definitions (each handler self-gates via `isEnabled(config)`), dispatches execution.

### Two distinct tool systems — don't confuse them
- **Chatbot tools:** implement `handler/tool/ChatToolHandler` as a `@Component` (name, description, JSON schema, `execute`, `isEnabled`). Auto-registered; no wiring needed. ~13 exist (search/browse/compare products, inventory, order lookup, promotions, delegation, …).
- **DB-agent tools:** rows in the `tools` table (types `AGENT`/`MCP`/`BUILTIN`) with a `handler_class` pointing at a Spring bean implementing `service/tool/ToolHandler`. Dispatched by `AgentExecutionService.executeToolCall`: `mcp_call` → `MCPClient`; `BUILTIN` (e.g. `web_search`) passed verbatim to Anthropic server tools; else bean resolution via `ToolRegistryService`. Registered via Flyway migration (see `V019__add_product_enrichment_tools.sql` as a template).

### Multi-agent system (`service/agent/`, `controller/agent/`, schema V002)
- `AgentExecutionService.java` — executes DB-defined agents (max 10 iterations). Loads agents with **fetch-join queries** (`AgentRepository.findByIdWithTools`, `AgentToolRepository.findByAgentIdWithTool`) — lazy collections break in the reactive pipeline; keep using fetch-joins.
- Chatbot → agent delegation: `handler/tool/DelegateToAgentChatToolHandler` → `service/tool/AgentDelegationToolHandler`, which **injects a product-first instruction** (show real products + cart links, don't just ask questions) into every delegated task. Enabled only when `ChatbotConfig.linkedAgentIds` is set.
- `WorkflowOrchestratorService.java` — ordered workflow steps with shared context, approval steps (`ApprovalService`, resumed via `ApprovalController`), timeouts; cron scheduling via `SchedulerService` + `WorkflowSchedule`.
- REST: `/api/agents`, `/api/workflows` (+ `/public/{id}/execute`), `/api/tools`, `/api/schedules`, `/api/approvals`. Spec: `docs/multi-agent/API_SPECIFICATION.md`; architecture: `docs/multi-agent/ARCHITECTURE.md`, `DATABASE_SCHEMA.md`.

### Other modules
- **Shopify data REST:** `ProductController`, `OrderController`, `CustomerController`, `InventoryController`, `FulfillmentController`, `AnalyticsController` → services → `ShopifyGraphQLClient` (rate limiting in `util/RateLimiter.java`).
- **Inventory analysis:** `service/inventory/*` (velocity, alerts, order recommendations), `scheduler/InventoryAnalysisScheduler.java`, AI assistant at `POST /api/inventory-management/agent/chat`.
- **SEO agent:** `SeoAgentController` (`/api/seo-agent/chat`).
- **OAuth / shops:** `ShopifyInstallController` (`/shopify/install|callback`), `ShopifyOAuthService` (HMAC verify), per-shop rows in `shopify_shops`.
- **Admin:** versioned config with rollback (`ConfigManagementController`), prompt testing, chat logs/analytics, AOP activity logging (`annotation/LogActivity` + `aspect/ActivityLoggingAspect`).

### Frontend (`frontend/src/`)
- Routes in `App.jsx` (~25 pages): chat surfaces (`ChatAgent`, `SeoAgent`, `InventoryAssistant`, `WorkflowChatExecutor`), agent/workflow editors, `ApprovalQueue`, analytics, inventory suite, `Settings`.
- API layers: `services/api.js` (axios, baseURL `/api`) and `services/adminApi.js` (fetch, `VITE_API_URL`).

## Conventions

- New DB change = next Flyway `V0xx__description.sql`; never edit applied migrations (`repair-on-migrate` covers checksum drift, not content).
- Chatbot behavior is config-driven: per-shop `chatbot_configs` + `system_prompts` tables override `application.yml` `chatbot.*` defaults. Prompt file fallback: `src/main/resources/prompts/system-prompt.txt`.
- All secrets/env via `.env` (see `.env.example`); nothing hardcoded in `application.yml`.
- MVC async timeout is 120s to accommodate long agent orchestration.

## Known loose ends (flagged in July 2026 review — future cleanup)

- `frontend/src/pages/ProductSearchPage.jsx` and `frontend/src/pages/WorkflowEditorVisual.jsx` (reactflow visual builder) exist but are not wired into any route.
- `frontend/src/components/ChatInterface.jsx` hardcodes `http://localhost:8080` and bypasses the api.js service layer.
- Two inconsistent frontend API clients (`api.js` axios vs `adminApi.js` fetch).
- OpenAI/Gemini providers in `AgentExecutionService` are stubs (unsupported).
- Market Discount Tracking (Vision Module 4, `/market-intel` route) was never built — placeholder page only.

## Documentation Index

- `docs/multi-agent/` — multi-agent architecture, DB schema, API spec, workflow examples (most accurate doc set)
- `docs/mcp/` — ERP MCP server tool reference (25 tools)
- `docs/ai-search-implementation/` — storefront AI search spec
- `docs/seo-agent/` — SEO agent plan
- `docs/archive/` — historical status/checkpoint docs (superseded)
- `shopify-theme-extension/README.md` — chat widget deployment
- `PROJECT_VISION.md`, `DEVELOPMENT_ROADMAP.md` — original vision/roadmap (historical banners at top)
