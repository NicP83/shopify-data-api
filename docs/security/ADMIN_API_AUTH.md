# Admin API authentication: audit and rollout (Oct 2026)

**Problem:** on 2026-10-18 the live chatbot model was changed with a plain, unauthenticated
`PUT /api/config/chatbot`. Every admin route on `shopify-data-api-production.up.railway.app`
accepted reads and writes from anyone on the internet.

**Fix (branch `claude/admin-api-auth`):** `filter/AdminAuthFilter` requires an `X-API-Key`
on all of `/api/*` except an explicit public allowlist. It uses the same keys as the existing
`/api/v1` surface (rows in `api_keys`, or `ADMIN_BOOTSTRAP_KEY`), and it ships in **report mode**,
so the first deploy blocks nothing.

## 1. Who calls the API today

These come from Railway HTTP logs for 2026-09-18 to 2026-10-18, the full retained window across the
last three deployments, plus the callers' source code.

| Caller | Routes | Evidence | Credential today |
|---|---|---|---|
| Storefront theme extension (`ai-search-client.js`) | `POST /api/shopify/chat/message`, `POST /api/shopify/chat/message/stream` (+ `OPTIONS`) | Browser UAs in logs; `shopify-theme-extension/assets/ai-search-client.js:89,148` | none (must stay public) |
| **HR app** (`NicP83/hearns-staff-manager`, Railway project *Hearns-HR*) | `POST /api/agents/23/execute` **only** | 1,109 calls, UA `node`, Railway egress IPs `208.77.246.x`; `src/lib/stock/enrichment-client.ts:77` | **none**. The client already sends `X-API-Key: $AGENT_API_KEY` when that env var is set (`enrichment-client.ts:62`), so it needs no code change. |
| Admin SPA (`frontend/`) | everything below | No admin SPA API traffic in the last 30 days | none (new: `hh_admin_key` in localStorage) |
| Embed widget (`embed.js`) | `POST /api/embed/chat` | none in logs | embed key (existing `ApiKeyAuthFilter`) |
| Shopify OAuth | `GET /shopify/install`, `GET /shopify/callback` | not under `/api`, so not filtered | HMAC (existing) |
| Manual curl | `PUT /api/config/chatbot` (2026-10-18) | the incident | none |

The HR app does **not** use `/api/v1/agents/23/run`. It calls the legacy admin route, so that
route needs a narrow scope (`agents:run`) and can't simply be locked to `admin`.

## 2. Audit table

**Current auth** is "none" for every row unless noted. **Proposed** is what the filter requires in
enforce mode. *Sensitive* marks routes that leak secrets, PII, or cost money (Claude or Shopify calls)
even on GET.

| Route (controller) | Methods | Current | Proposed | Notes |
|---|---|---|---|---|
| `/api/shopify/chat/message`, `/message/stream` (ShopifyChatController) | POST | none (shop-domain check) | **public** | storefront; untouched |
| `/api/workflows/public/{id}/execute` (WorkflowController) | POST | none (`is_public` check) | **public** | unchanged |
| `/api/workflows/public/{id}` (WorkflowController, **new**) | GET | — | **public** | form metadata for public workflows only; 404 otherwise |
| `/api/health` (HealthController) | GET | none | **public** | static payload |
| `/api/v1/**`, `/api/embed/**` | all | X-API-Key (`ApiKeyAuthFilter`) | unchanged (skipped by new filter) | |
| `/shopify/install`, `/shopify/callback`, SPA routes, `/assets/**`, `/embed.js` | GET | none | **public** (not under `/api`) | |
| `/api/agents/{id}/execute` (AgentController) | POST | none | `agents:run` or `admin` | **HR app**; runs Claude (cost) |
| `/api/workflows/{id}/execute` (WorkflowController) | POST | none | `workflows:run` or `admin` | runs Claude (cost) |
| `/api/config/ai`, `/chatbot`, `/chatbot/profiles[/{slug}]`, `/chatbot/reset`, `/validate-model` (ConfigController) | PUT/POST/DELETE | none | `admin` | **replaces live storefront bot config** (the incident) |
| `/api/config/ai`, `/prompt`, `/models`, `/chatbot`, `/chatbot/profiles`, `/chatbot/preview-prompt`, `/agents` | GET | none | `admin` | system prompts, linked agents |
| `/api/shopify/config` + `/ai`, `/prompts`, `/prompts/{id}`, `/prompts/customize`, `/analytics` (ShopConfigController) | GET/PUT/POST | none | `admin` | per-shop AI config and prompts |
| `/api/admin/config/*` (ConfigManagementController, `@CrossOrigin("*")`) | POST save/activate/rollback/cleanup/disable-rollback; GET history/active/versions/compare/recent/types | none | `admin` | versioned config activate/rollback |
| `/api/admin/prompt-testing/*` (PromptTestingController, `@CrossOrigin("*")`) | POST test/rate, DELETE cleanup, GET results/statistics/... | none | `admin` | `test` calls Claude (cost) |
| `/api/admin/activity/*` (AdminActivityController, `@CrossOrigin("*")`) | GET logs/search/...; POST anonymize | none | `admin` | activity log contents |
| `/api/admin/shops/register` (ShopRegistrationController) | POST | none | `admin` | **registers a shop + access token** |
| `/api/agents/**` except execute (AgentController) | POST/PUT/DELETE create/update/delete/activate/deactivate/tool attach; GET list/by-name/tools/executions | none | `admin` | can rewrite agent 23's prompt |
| `/api/workflows/**` except public + execute (WorkflowController) | CRUD workflows and steps, reorder, activate | none | `admin` | |
| `/api/executions`, `/api/executions/{id}`, `/api/workflows/{id}/executions` (ExecutionController) | GET | none | `admin` | execution inputs/outputs |
| `/api/tools/**` (ToolController) | CRUD, activate, `validate-handler` | none | `admin` | tool `handler_class` → bean |
| `/api/schedules/**` (ScheduleController) | POST/DELETE/PUT activate, cron, trigger-data; GET | none | `admin` | can schedule workflows on cron |
| `/api/approvals/**` (ApprovalController) | POST approve/reject/create; GET pending/count/execution | none | `admin` | resumes paused workflows |
| `/api/logs/chat`, `/api/logs/chat/{id}` (ChatLogsController) | GET | none | `admin` | *Sensitive*: customer chat transcripts |
| `/api/analytics/chat/*` (ChatAnalyticsController) | GET summary/daily/session/recent/...; PUT `{id}/feedback` | none | `admin` | *Sensitive*: sessions. The storefront does not call feedback. |
| `/api/customers/**`, `/api/orders/**`, `/api/fulfillment/**` | GET | none | `admin` | *Sensitive*: customer PII via Shopify |
| `/api/products/**`, `/api/inventory/**`, `/api/analytics/**` | GET | none | `admin` | Shopify quota; sales figures |
| `/api/inventory-management/**` (InventoryAnalysisController) | POST analyze/analyze-batch/analyze-all/seed-test-data, PUT approve/resolve, DELETE; GET | none | `admin` | `seed-test-data` writes rows |
| `/api/inventory-management/agent/*` | POST chat/reset, GET tools | none | `admin` | Claude (cost) |
| `/api/seo-agent/chat`, `/status` | POST/GET | none | `admin` | Claude (cost) |
| `/api/chat/message`, `/api/chat/status` (ChatController) | POST/GET | none | `admin` | generic chat; Settings "test" button |
| `/api/status` (HealthController) | GET | none | `admin` | calls Shopify on every hit |

## 3. What the change does

- **`filter/AdminAuthFilter`** is registered on `/api/*` (order 2, after `ApiKeyAuthFilter`) in `config/ApiKeyFilterConfig`.
  - Public allowlist: `/api/shopify/chat/`, `/api/workflows/public/`, `/api/v1/`, `/api/embed/` prefixes, plus exact `/api/health`. `OPTIONS` always passes.
  - A path counts as public only if **both** the container-normalised path and the raw URI (with `;params` stripped) are public. That blocks `/api/shopify/chat/../../config/chatbot` (tested against real Tomcat).
  - Scopes: `POST /api/agents/{id}/execute` needs `agents:run`, `POST /api/workflows/{id}/execute` needs `workflows:run`, and everything else needs `admin`. An `admin` key passes everything.
  - Bindings: a non-`admin` key with `bound_agent_id` / `bound_workflow_id` can execute only that target. Otherwise it gets a 403 (logged in report mode). `/api/v1` run routes apply the same check in `ExternalApiController`.
  - Valid DB keys get `last_used_at` updated, visible on the API Keys page. The admin routes have no rate limiting, same as before.
- **`ADMIN_AUTH_MODE`** (`admin.auth.mode`) has three settings:
  - `report` (default): never blocks. Logs `admin-auth REPORT would reject <method> <path> -> 401|403 (...) ua=... ip=...`.
  - `enforce`: returns 401 for a missing or invalid key and 403 for a missing scope.
  - `off`: does nothing.
  - An unrecognised value fails closed to `enforce` and logs an ERROR.
- **New `GET /api/workflows/public/{id}`:** the public form and chat executor pages used to load `GET /api/workflows/{id}`, which is now admin-only.
- **SPA:**
  - `services/adminAuth.js` adds `X-API-Key` from `localStorage.hh_admin_key` to every axios (`api.js`) and `fetch` call to our own `/api/**`. That covers `adminApi.js` and the pages that call `fetch` directly. It uses the same storage slot the API Keys page already uses.
  - On a 401, `AdminKeyPrompt` shows a banner asking for the key. It is hidden on public `/workflow/*` pages.
  - The static bundle is rebuilt into `src/main/resources/static`. **That build also ships the Phase 4 Assistants page** (commit 4493b96), which had never been built into static. Its backend is already live.
- **Unchanged:** the storefront chat path (`ShopifyChatController`, `ChatAgentService`), `ApiKeyAuthFilter`, and the theme extension. There is no DB migration.

Tests:
- `AdminAuthFilterTest` (22 cases): public routes, scopes, agent/workflow bindings, modes, `..` smuggling.
- `ExternalApiControllerBindingTest` (4 cases): bindings on `/api/v1` run routes.
- `AdminAuthFilterTomcatTest`: real embedded Tomcat with the `/api/*` registration. Checks that storefront chat, OAuth and static paths pass, admin routes return 401 without a key, and raw `..` / `%2e%2e` requests can't reach the admin route.
- `WorkflowControllerPublicTest` (3 cases).
- SPA checked in a browser against a stub backend. After a 401 the banner appears, and once a key is saved, axios and fetch calls carry it. The public workflow page loads with no key.

## 4. Rollout plan (the user deploys; each stage is revertible)

### Stage 0: credentials (no deploy)
1. **Check `ADMIN_BOOTSTRAP_KEY` in Railway** (Shopifu-data-ai → production). The variable listing shows the name as `ADMIN_BOOTSTRAP_KEY ` **with a trailing space**. If that's real, Spring never binds it, and the bootstrap key is silently disabled today. Recreate it with the exact name and a strong value (`openssl rand -base64 32`). Railway restarts the app on a variable change, so do it at a quiet time.
2. Use the bootstrap key to mint two keys from the API Keys page (`/api-keys`) or `POST /api/v1/admin/keys`. That surface is already live and gated, so this works before the deploy.
   - `hearns-hr-enrichment`, scopes `agents:run`, **bound agent 23**. Shown once; copy it.
   - `admin-spa`, scopes `admin`, for browsers. Keep the bootstrap key for break-glass use only.

### Stage 1: deploy this branch in report mode
3. Merge to `main` and deploy, with `ADMIN_AUTH_MODE` **unset** (report). Nothing is blocked.
4. Smoke test: one storefront chat message (normal and stream), and one public workflow page if any are in use.
5. `railway logs | grep admin-auth` should show `REPORT would reject` lines for unauthenticated admin calls, including the HR app's `POST /api/agents/23/execute ua='node'`.

### Stage 2: hand out the credentials
6. **HR app:** in Railway project *Hearns-HR*, set `AGENT_API_KEY=<hearns-hr-enrichment key>` on the staff-manager service. The code already sends it, so this is a config-only redeploy of the HR app. Run one product enrichment. Its line must drop out of the report log, and the key's "last used" should update.
7. **Admin SPA:** open the admin site. Paste the `admin-spa` key into the yellow banner (or the API Keys page). This is once per browser/device.
8. Leave report mode on for a few days. Railway only retains about 30 days of HTTP logs, so infrequent callers may not have shown up in the audit. Any `would reject` line from an unknown UA or IP is a caller to identify before Stage 3.

### Stage 3: enforce
9. Set `ADMIN_AUTH_MODE=enforce` (variable change, restart).
10. Verify:
    - `curl -X PUT .../api/config/chatbot` with no key → 401.
    - The same call with the admin key → 200.
    - A storefront chat message → 200.
    - One HR app enrichment works.
    - The admin SPA works.

**Rollback at any stage:** set `ADMIN_AUTH_MODE=report` (or `off`). That's a variable change, not a code revert. Reverting the branch is only needed if the deploy itself misbehaves.

## 5. Risks and follow-ups

- Each Railway variable change restarts the single replica, so storefront chat is briefly unavailable. Schedule Stages 0, 3 and rollbacks at low traffic.
- The admin key sits in browser localStorage, so an XSS in the SPA could read it. Use the revocable `admin-spa` key there, not the bootstrap key.
- Run keys are limited by their binding. A non-`admin` key with `api_keys.bound_agent_id` (or `bound_workflow_id`) set can run only that agent (or workflow), on both `/api/{agents|workflows}/{id}/execute` and `/api/v1/{agents|workflows}/{id}/run`. Anything else gets a 403. Binding to an agent also blocks every workflow, and the reverse. Unbound run keys can still run any agent or workflow, so mint the HR key bound to agent 23. Keys can't be edited, so an existing unbound HR key has to be replaced. `GET /api/v1/agents` still lists every agent for a bound key, but it can only run its own.
- 401s from this filter carry no CORS headers. That only affects cross-origin browser callers, and none are known.
- Every authenticated admin request does one indexed key lookup and one `last_used_at` update. That's negligible next to Hikari's 10 connections at admin traffic levels.
- Later: move the HR app to `/api/v1/agents/23/run`, then lock `/api/agents/*/execute` to `admin`. Also remove `@CrossOrigin("*")` on the three admin controllers. With keys enforced it no longer matters for security, but it is misleading.
