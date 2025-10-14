# Multi-Agent System Documentation

**Project**: Shopify Data API - Multi-Agent Orchestration System
**Created**: 2025-10-14
**Status**: Phase 0 - Documentation Complete ✅

---

## Overview

This folder contains complete documentation for the Multi-Agent System - a flexible, database-driven framework for creating, managing, and orchestrating AI agents that can work together in complex workflows.

**Key Features**:
- 🤖 Dynamic agent creation (zero hardcoding)
- ⚙️ Visual workflow builder
- ✅ Human-in-the-loop approvals
- ⏰ Scheduled & triggered execution
- 🔧 Dynamic tool management
- 📚 Knowledge base integration (RAG)

---

## Documentation Index

### 📋 Core Documentation

1. **[ARCHITECTURE.md](./ARCHITECTURE.md)** - System Design & Architecture
   - Overview of the multi-agent system
   - Core principles (zero hardcoding, database-driven)
   - System components and diagrams
   - Services architecture
   - Workflow execution flow
   - Agent communication patterns
   - **Start here** to understand the system

2. **[DATABASE_SCHEMA.md](./DATABASE_SCHEMA.md)** - Complete Database Schema
   - All 10 tables with detailed specifications
   - Foreign key relationships
   - Indexes and constraints
   - Complete SQL DDL scripts ready to run
   - Migration instructions
   - Rollback procedures
   - **Use this** when implementing the database layer

3. **[IMPLEMENTATION_ROADMAP.md](./IMPLEMENTATION_ROADMAP.md)** - Step-by-Step Build Plan
   - 10 phases from database to production
   - Detailed task breakdowns
   - Time estimates (76-97 hours total)
   - Deliverables checklist
   - Dependencies and prerequisites
   - **Follow this** to implement the system sequentially

4. **[PROGRESS.md](./PROGRESS.md)** - Implementation Progress Tracker
   - Current status of each phase
   - Completed vs remaining work
   - Actual vs estimated hours
   - Session notes and decisions
   - Blockers and issues
   - **Update this** after completing tasks

### 📚 Additional Documentation (To Be Created)

5. **API_SPECIFICATION.md** - REST API Endpoints
   - All endpoint specifications
   - Request/response examples
   - Authentication requirements
   - Rate limiting details

6. **FRONTEND_COMPONENTS.md** - React UI Components
   - Complete list of components to build
   - Component responsibilities
   - Props and state management
   - UI/UX patterns

7. **WORKFLOW_EXAMPLES.md** - Sample Workflows
   - Real-world workflow configurations
   - JSON examples
   - Use case scenarios
   - Best practices

---

## Quick Start Guide

### For Developers Starting Implementation

1. **Read the docs in this order**:
   ```
   1. README.md (this file)
   2. ARCHITECTURE.md
   3. DATABASE_SCHEMA.md
   4. IMPLEMENTATION_ROADMAP.md
   ```

2. **Check current progress**:
   ```bash
   cat docs/multi-agent/PROGRESS.md
   ```

3. **Start Phase 1** (Database & Entities):
   ```bash
   # Copy SQL from DATABASE_SCHEMA.md
   # Create migration file:
   # src/main/resources/db/migration/V002__multi_agent_system.sql
   ```

4. **Follow the roadmap sequentially**:
   - Don't skip phases
   - Test after each phase
   - Update PROGRESS.md

### For Claude (AI Assistant)

When continuing this project in future sessions:

1. **Load context**:
   ```
   "Look at docs/multi-agent/PROGRESS.md to see current status"
   "Reference docs/multi-agent/ARCHITECTURE.md for system design"
   "Check docs/multi-agent/IMPLEMENTATION_ROADMAP.md for next steps"
   ```

2. **Resume work**:
   - Check PROGRESS.md for current phase
   - Reference relevant documentation
   - Update PROGRESS.md after completing tasks

3. **Avoid context loss**:
   - All architecture decisions documented
   - All database specs saved
   - All implementation steps planned
   - Progress tracked in PROGRESS.md

---

## System Summary

### What We're Building

A **database-driven multi-agent orchestration system** that:
- Lets you create AI agents via UI (no coding required)
- Chain agents together in visual workflows
- Support conditional logic and branching
- Pause for human approvals
- Run on schedules or triggered by events
- Track all executions with detailed logs

### Technology Stack

**Backend**:
- Java 17 + Spring Boot 3.2
- PostgreSQL (10 tables)
- Spring WebFlux (reactive)
- Claude/GPT/Gemini APIs

**Frontend**:
- React 18
- React Flow (visual workflow builder)
- Tailwind CSS
- Axios

**Deployment**:
- Railway (staging & production)
- Separate databases per environment

### Zero Hardcoding Principle

**Everything** is defined in the database:
- Agent types (no Java classes for specific agents)
- Tool registry (dynamically loaded)
- Workflows (JSON configurations)
- Conditions (evaluated at runtime)

Example: To create a new agent type, you just add a database record. No code changes needed.

---

## File Structure

```
/docs/multi-agent/
├── README.md                      ← You are here
├── ARCHITECTURE.md                ← System design (16KB)
├── DATABASE_SCHEMA.md             ← Database schema (40KB)
├── IMPLEMENTATION_ROADMAP.md      ← 10-phase plan (15KB)
├── PROGRESS.md                    ← Progress tracker (current status)
├── API_SPECIFICATION.md           ← (To be created)
├── FRONTEND_COMPONENTS.md         ← (To be created)
└── WORKFLOW_EXAMPLES.md           ← (To be created)
```

---

## Key Concepts

### Agents
- Database records defining AI personalities
- Configurable: model, prompt, temperature, tools
- Reusable across workflows
- Example: "Product Expert", "Customer Support", "Order Processor"

### Tools
- Capabilities agents can use
- Registered in database
- Dynamically assigned to agents
- Example: product_search, order_lookup, send_email

### Workflows
- Sequences of agent executions
- Conditional branching
- Human approvals
- Triggered manually, on schedule, or by events

### Context
- Shared data structure passed between agents
- Each agent adds its output
- Next agents access previous results
- Example: `${step1.sentiment}` references first agent's output

---

## Database Overview

### 10 Core Tables

1. **`agents`** - Agent definitions
2. **`tools`** - Tool registry
3. **`agent_tools`** - Agent-tool assignments (many-to-many)
4. **`workflows`** - Workflow definitions
5. **`workflow_steps`** - Steps within workflows
6. **`workflow_executions`** - Execution tracking
7. **`agent_executions`** - Individual agent runs
8. **`approval_requests`** - Human approval tracking
9. **`knowledge_bases`** - RAG content per agent
10. **`workflow_schedules`** - Cron schedules

**Size Estimate**: ~400MB for 10,000 executions

---

## Implementation Status

**Current Phase**: Phase 0 - Documentation (70% complete)

**Next Steps**:
1. Complete remaining documentation
2. Create SQL migration file
3. Generate JPA entities
4. Test database schema

**Estimated Total Time**: 76-97 hours (2-3 weeks)

See [PROGRESS.md](./PROGRESS.md) for detailed status.

---

## How to Reference This Documentation

### In Your Messages to Claude

```
"Look at docs/multi-agent/ARCHITECTURE.md before implementing agents"
"Check docs/multi-agent/DATABASE_SCHEMA.md for table definitions"
"Reference docs/multi-agent/IMPLEMENTATION_ROADMAP.md for next phase"
"Update docs/multi-agent/PROGRESS.md with completed work"
```

Claude will automatically read these files and use them as context.

### In Code Comments

```java
/**
 * Multi-Agent Orchestrator Service
 *
 * See: docs/multi-agent/ARCHITECTURE.md for system design
 * See: docs/multi-agent/IMPLEMENTATION_ROADMAP.md for implementation plan
 */
```

---

## Decision Log

Key architectural decisions:

| Decision | Rationale |
|----------|-----------|
| PostgreSQL JSONB for config | Flexibility without schema changes |
| Single-phase implementation | User preference for complete system |
| Railway for deployment | Existing infrastructure, easy staging |
| React Flow for visual builder | Industry standard, well-maintained |
| Zero hardcoding | Maximum flexibility, no code changes for new agents |

---

## Support & Questions

If you're unsure about:
- **Architecture**: Read ARCHITECTURE.md
- **Database**: Read DATABASE_SCHEMA.md
- **Next steps**: Read IMPLEMENTATION_ROADMAP.md
- **Current status**: Read PROGRESS.md

For new sessions with Claude:
```
"I'm working on the multi-agent system.
Look at docs/multi-agent/PROGRESS.md to see where we left off."
```

---

## Success Criteria

The system is complete when:
- ✅ All 10 phases in IMPLEMENTATION_ROADMAP.md are finished
- ✅ Users can create agents via UI without coding
- ✅ Visual workflow builder is functional
- ✅ Approvals system works end-to-end
- ✅ Scheduled workflows execute correctly
- ✅ System deployed to Railway staging
- ✅ Full documentation complete
- ✅ Test workflows demonstrate all features

---

**Last Updated**: 2025-10-14
**Version**: 1.0
**Status**: Documentation Phase - In Progress
