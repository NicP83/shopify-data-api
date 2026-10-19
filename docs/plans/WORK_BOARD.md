# Work board: chatbot and platform (from 2026-10-19)

Single source of truth for parallel work. Plan and step numbers: [CHATBOT_PLAN.md](CHATBOT_PLAN.md).

## Already live (on `main`, deployed)
- Sonnet 5.5 support. The live chatbot model is set in `chatbot_configs.model_name` (`/api/config/chatbot`), now `claude-sonnet-5-5`. Rollback: set it to `claude-sonnet-4-6`.
- Hikari pool-leak fix (open-in-view off for chat routes, leak detection). Incident: `docs/incidents/2026-10-13-hikari-pool-exhaustion.md`.

## Decisions (Nic, 2026-10-19)
- Product search back on with 10 results, plus cart links and prices (step 0).
- The storefront chat code may now be changed, **one small deploy at a time**, each tested live with real chats and easy to undo. This replaces the July no-touch rule.
- Experts: unlink SPECIALS FINDER (id 19). Keep the six advisor experts for specialist advice only. The bot answers stock, price, availability and specials itself (step 5, done together with step 0).
- Ship the Assistants (personas) page with the admin-login deploy.
- Defaults unless Nic says otherwise: Spring Boot 3.2.12; throwaway local Docker Postgres for testing (never prod).

## Lanes (who owns what)
| Lane | Plan steps | Branch | Owns these files (nobody else edits them) |
|---|---|---|---|
| 1 Admin login | 1 (+ API-key agent binding) | `claude/api-key-agent-binding` (contains `claude/admin-api-auth`) | `filter/AdminAuthFilter`, `config/ApiKeyFilterConfig`, `model/ApiKey`, `controller/v1/ExternalApiController`, `controller/agent/WorkflowController`, **all of `frontend/` and the `src/main/resources/static/` build** |
| 2 Chat fixes | 2, 3, 4 | one branch per step | `service/ChatbotConfigService`, `model/ChatbotConfig*`, `service/tool/AgentDelegationToolHandler`, `handler/tool/DelegateTo*`, `controller/ShopifyChatController`, `service/ChatAgentService`, chat analytics/logging |
| 3 Reliability | 6, 7 (+ web_search load failure) | one branch per step | `pom.xml`, `client/ShopifyGraphQLClient`, WebClient config, `service/tool/WebSearchToolHandler` |
| Nic | 0, 5 | — | live config changes (auto mode blocks Claude from changing production config) |

Later, not started: steps 8 to 16 (they need the logging from step 4 first), the feedback buttons, and the theme widget. The August branches `collection-sort-job`, `collection-out-of-stock-sort` and `conversion-tracking-audit` are parked and untouched.

## Rules for every session
1. Work in your own worktree and branch. Never push to `main`; a push to `main` deploys to Railway.
2. Edit only the files your lane owns. If you need a file another lane owns, stop and ask the coordinating session.
3. `application.yml`: add keys only under your own commented block, never reorder or reformat it, so merges stay trivial.
4. Only Lane 1 rebuilds the frontend or touches `static/`.
5. Done means: compiles, `mvn test` passes, the change has a written rollback, and the branch is rebased on the latest `main`.
6. Merges happen one at a time in this order: Lane 1, then 2 (step 2), 2 (step 3), 2 (step 4), then 3 (step 6), 3 (step 7). After each deploy, test a blocking chat and a streamed chat live before the next merge.
7. Anything in production (config PUTs, DB writes, Railway restarts) is Nic's to run.
