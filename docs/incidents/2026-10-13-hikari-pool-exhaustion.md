# 2026-10-13: HikariCP pool exhaustion (storefront chat down)

**Symptom:** every DB call failed with `HikariPool-1 - Connection is not available, request timed out after 30000ms`. The storefront chatbot stopped answering, and `SchedulerService.processScheduledWorkflows` logged the error every minute. A redeploy on 2026-10-13 05:15Z fixed it. The previous app deploy was 2026-07-31.

## Timeline (from `railway logs`)

- **2026-07-31:** last app deploy (`ea70189c`).
- **2026-08-30 14:58Z:** the **Postgres service was redeployed** while the app kept running.
- **2026-09-13 18:00Z:** earliest app log Railway still retains. **The pool was already exhausted then.** The scheduler error appears every minute from the first retained line. The outage therefore began about a month before it was noticed, and its exact start has aged out of the logs.
- The scheduler's hourly inventory job often logs at `:00:30` instead of `:00:00`. Spring's single scheduler thread was stuck in the 30s pool wait every minute.
- The error carries no `Caused by` from pgjdbc. Hikari was not failing to *create* connections: all 10 were checked out and never returned. That points to an app-side leak, not a Postgres or capacity problem.
- Postgres's "silent after 2026-10-07" logs are a red herring. The only lines are scanner noise (`invalid length of startup packet`) and checkpoints. Default Postgres logging records nothing for normal client traffic.

## Diagnosis

The cause is a pool of 10 connections with **nothing bounding how long one can be held**. The leading mechanism is open-in-view (OSIV) on async chat requests.

1. **OSIV was on by default.** `spring.jpa.open-in-view` was unset, so Boot defaulted it to true. Spring's `HibernateJpaVendorAdapter` (spring-orm 6.1.1) sets `hibernate.connection.handling_mode=DELAYED_ACQUISITION_AND_HOLD` (verified in the jar's bytecode). Once a request-thread EntityManager touches the DB, it **holds that physical connection until the EntityManager closes**, not just until the transaction ends.
2. **Every chat request touches the DB on the request thread, then goes async.** `ShopifyChatController.sendMessage` and `streamMessage` call `shopifyShopService.isShopActive()` and `getShop()` synchronously (`ShopifyChatController.java:78,91,185,189`). They then return a `Mono` or an SSE `Flux` that runs for up to 180s (`spring.mvc.async.request-timeout`), and longer during expert delegations. Each in-flight chat pins one of the 10 connections for its whole duration.
3. **Async requests that end abnormally can skip the close.** In spring-orm 6.1.1, `AsyncRequestInterceptor.afterCompletion` closes the EntityManager **only if the request timed out or errored** (verified in bytecode). On a normal finish, the close is left to the final async re-dispatch through `OpenEntityManagerInViewInterceptor`. If an async request ends any other way, the EntityManager and its connection are never closed. The usual case is a client disconnect. Spring 6.1.x reworked disconnected-client handling for async requests several times in later patch releases (see spring-framework issues #32042, #32342, #33421), and this app runs 6.1.1 (Boot 3.2.0). The storefront regularly produces client disconnects on the chat endpoints: Railway HTTP logs show HTTP 499s on most days. Each one that leaks takes a connection permanently. Ten leaks over weeks of low chat traffic exhaust the pool, and only a restart frees them. That matches the observed slow onset with no deploy.
4. **Nothing surfaced or bounded it.** There was no `leak-detection-threshold`. pgjdbc's `socketTimeout` defaulted to 0, so a query on a socket dropped by the network waits forever. The Aug 30 Postgres redeploy is a candidate trigger for this path. There was also no TCP keepalive.

**Ruled out:**
- `@Transactional` on Mono-returning methods (`AgentExecutionService:92,101`, `WorkflowOrchestratorService:56`). With a non-reactive `JpaTransactionManager`, the transaction commits when the method returns the Mono, so it does not span the async work. `WorkflowOrchestratorService` does its DB work inside `Mono.fromCallable`, outside any surrounding transaction.
- Scheduler jobs. `SchedulerService` and `InventoryAnalysisScheduler` hold connections only for short repository calls. The `@Transactional` on `SchedulerService.executeScheduledWorkflow` is a self-invocation, so it has no effect, and `executeWorkflow` is fire-and-forget. The scheduler was a victim, not the cause.
- Postgres capacity: one app instance with 10 connections, against Postgres's default limit of 100.

**Not proven:** the leaked connections' owners can't be identified after the fact, because the redeploy killed the JVM. The fix below makes the next occurrence self-identifying.

## Fix (branch `claude/hikari-pool-exhaustion-fix`)

1. `OpenEntityManagerInViewConfig`: OSIV stays on for every route **except `/api/shopify/chat/**`**, and `spring.jpa.open-in-view: false` replaces Boot's all-routes default. The chat controller reads only scalar fields of `ShopifyShop`, which has no associations, so it does not need OSIV. Each repository call now borrows and returns a connection in milliseconds. Admin routes keep OSIV because some of them serialize entities with lazy associations.
2. Hikari and pgjdbc hardening in `application.yml`:
   - `leak-detection-threshold: 200000`. Hikari logs a WARN with the borrower's stack trace for any connection held longer than 200s. That is above the 180s async timeout, so a warning means a real leak.
   - `keepalive-time: 120000`.
   - `tcpKeepAlive=true`.
   - `socketTimeout=300`, in seconds.
   - Explicit `maximum-pool-size` of 10, overridable through environment variables.

## Deploy risks

- **OSIV exclusion on chat:** any lazy-association access during a chat request outside a transaction would now throw `LazyInitializationException`. Code review found none, because the chat path's lazy-loading work runs on Reactor threads, where OSIV never applied. Check: send a chat message, and a streaming one, after deploy.
- **`socketTimeout=300`:** a single query running longer than 5 minutes would fail. None exists in this app. Override with `DB_SOCKET_TIMEOUT_S` if needed.
- **Leak detection** only logs; it never closes connections. Watch for `Connection leak detection triggered` in the logs after deploy.

## Verify after deploy

Run this read-only query against prod Postgres:

```sql
select state, count(*), max(now() - state_change) as oldest
from pg_stat_activity
where backend_type = 'client backend' and datname = current_database()
group by state;
```

Healthy: up to 10 `idle` connections and no `idle in transaction` older than seconds. Run it now, before deploying, too. If idle connections are being held more than 3 minutes since the Oct 13 redeploy, the leak is already rebuilding.

## Follow-ups (not in this change)

- Upgrade Spring Boot 3.2.0 → latest 3.2.x/3.3.x for the async disconnect fixes in Spring Framework 6.1.x.
- Turn OSIV off globally once admin controllers return DTOs or fetch-joined entities.
- `ChatAgentService.setShopContext`, `setAnthropicModel` and the other setters are called on a singleton per request, so concurrent chats can race on that state.
- `spring.task.scheduling.pool.size` is 1 by default, so all `@Scheduled` jobs share one thread.
- Shopify `WebClient` (`WebClientConfig`) has no response timeout, and `ShopifyGraphQLClient.executeQuery` blocks without one.
