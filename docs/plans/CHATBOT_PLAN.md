# Chatbot improvement plan (combined, 2026-10-19)

**Status:** agreed with Nic 2026-10-19. Who is doing what right now: [WORK_BOARD.md](WORK_BOARD.md). This page puts the four detailed plans into one order:

| # | Plan |
|---|---|
| 1 | [Reliability](chatbot-1-reliability.md) |
| 2 | [Speed](chatbot-2-speed.md) |
| 3 | [Feedback](chatbot-3-feedback.md) |
| 4 | [Smarter answers](chatbot-4-smarter-answers.md) |

Every step below is one Railway deploy, or one theme or config change. Each one is checked live before the next one starts, and each can be undone on its own.

## What planning found (things wrong today)

1. **Product search is switched off in the live config** (search off, 0 results, cart links and prices off). It was already off before the 2026-10-18 Sonnet 5.5 switch (the pre-switch backup has the same values); most likely an earlier partial save hit the save bug fixed in step 2. Restored in step 0.
2. **The storefront bot can reach any agent by name, including agent 23 (Product Enrichment).** The bot is only told about linked agents, but nothing actually blocks the others, so a customer could talk it into using one. This needs a two-line fix.
3. **About 10–20 seconds of a slow reply is wasted on "expert" agents that have no tools.**
   - Several, such as SPECIALS FINDER and the Stock Check workflow's agent, can't look anything up, so they answer from general knowledge.
   - PAINT EXPERT's web search fails to load.
   - The bot delegates on almost every product question: 28 of its 65 tool calls last week were delegations.
4. **Streamed chats are never logged.** Most customer chats are streamed, so the Logs and Chat Analytics pages miss most of them. That also blocks the feedback buttons and any way to measure speed.
5. **When a stream fails early, the widget silently re-asks from scratch.** A shopper who has already waited 30 seconds then waits another 30.
6. **Order lookup from chat may always fail,** because of a threading bug. This is still to be confirmed in the logs.
7. **Workflow approval steps don't pause anything.** Steps after an "approval" run before anyone approves.
8. Two chats at the same moment can overwrite each other's settings. This is known and is fixed in step 9.

## Order of work

| Step | What | Kind | Risk to chat | Plan |
|---|---|---|---|---|
| 0 | Restore the wiped config | Nic runs one command | none | — |
| 1 | Admin API key check, in report mode | deploy | very low | `docs/security/ADMIN_API_AUTH.md` |
| 2 | Config saves can't wipe settings again | deploy | very low | Speed §R |
| 3 | Block the bot from reaching unlinked agents (protects agent 23) | deploy | very low | Smarter answers, risk R3 |
| 4 | Log streamed chats, and add timing logs | deploy | low | Speed §0, Feedback step 1 |
| 5 | **Unlink or re-scope the useless experts**: the biggest speed win | Settings change, no deploy, instant undo | low | Speed §1 |
| 6 | Shopify call timeouts | deploy | low | Reliability R1 |
| 7 | Spring Boot 3.2.0 → 3.2.12 | deploy | low–medium | Reliability R2 |
| 8 | Lower the tool-step limit, and stream the wrap-up answer | deploy | low–medium | Speed §2 |
| 9 | Per-chat settings (fixes the two-chats race) | deploy | medium | Reliability R3 |
| 10 | Widget: honest long-wait message, no silent re-run | theme change | low | Speed §3 |
| 11 | Feedback: backend route plus a DB column (migration on its own) | 2 deploys | low | Feedback |
| 12 | Feedback: 👍/👎 buttons in the widget, off by default | theme change | low | Feedback |
| 13 | Faster model for the remaining experts (Haiku) | per-agent setting | low | Speed §4 |
| 14 | Fix approval steps so they really pause | deploy | medium (admin and scheduled workflows) | Smarter answers C1 |
| 15 | Bot told about linked workflows, plus shop context in its tools | 2 deploys | low | Smarter answers A, B |
| 16 | Long workflows from chat, run in the background | deploys | medium | Smarter answers C2, C3 |

Steps 2–5 should bring the slow replies of 25–47 seconds down to about 10–20 seconds. Steps 3, 8, 9 and 15 touch the chatbot's code, which you asked to keep untouched in July; each is small and has its own rollback in its plan. Allow at least a day between deploys.

## Questions for Nic (answers unblock the steps in brackets)

1. **Experts:** may the storefront bot answer stock, price and specials questions itself, with the tool-less expert agents unlinked or re-scoped? [5]
2. **Results per search:** what was `maxSearchResults` before? The restore command uses 10. Was "Show SKUs" on? [0]
3. **Spring Boot target 3.2.12:** OK? It's the safe step; 3.3 or later is a bigger project. [7]
4. **Docker for local testing:** OK to use a throwaway local database? It never connects to prod. [6–9]
5. **Feedback:** thumbs only, or thumbs plus an optional comment? Comments may contain customer personal details. [11–12]
6. **Theme edits:** who edits the theme's JavaScript, and how? Is it pasted in through Online Store → Edit code? [10, 12]
7. **Workflows from chat:** which workflows should customers be able to run, and is their output safe for customers to see? Who watches the Approval Queue? [14–16]
8. **Shop prompt:** one read-only database query tells us whether a custom shop prompt is active. May I run it, or will you? [9, 15]
