# Hearn's Hobbies AI-Enhanced Search System

> **Model note (July 2026):** Claude model IDs in this document are historical. The runtime model is set by the `ANTHROPIC_MODEL` env var (currently `claude-sonnet-4-6`) — see CLAUDE.md.
## Implementation Documentation

---

## 📋 Document Index

This documentation is organized into the following files:

### Planning & Architecture
- **00-OVERVIEW.md** (this file) - Project overview and index
- **01-ARCHITECTURE.md** - Technical architecture and system design
- **02-DATABASE-SCHEMA.md** - Database design and migrations
- **03-API-SPECIFICATIONS.md** - API endpoints and contracts

### Implementation Guides
- **04-PHASE1-BACKEND.md** - Backend foundation (OAuth, database)
- **05-PHASE2-THEME-EXTENSION.md** - Shopify theme extension
- **06-PHASE3-ENHANCEMENTS.md** - Backend enhancements
- **07-PHASE4-ADMIN-ASSISTANT.md** - Admin product search assistant
- **08-PHASE5-TESTING.md** - Integration testing
- **09-PHASE6-DEPLOYMENT.md** - Production deployment

### Reference
- **10-CONFIGURATION.md** - Environment variables and settings
- **11-TROUBLESHOOTING.md** - Common issues and solutions
- **12-ANALYTICS.md** - Monitoring and analytics setup

---

## 📊 Executive Summary

### Project Goal
Enhance Hearn's Hobbies (hearnshobbies.com) Shopify store with an AI-powered product search assistant that works alongside existing instant search.

### Solution: Hybrid Search Bar Integration
Add an AI button (🤖) next to the existing search bar that opens a conversational chat modal powered by Claude AI, while keeping the fast native Shopify search intact.

### Key Features
- ✅ Non-disruptive: Existing search unchanged
- ✅ AI button added to search bar
- ✅ Modal chat interface with product cards
- ✅ Claude-powered recommendations
- ✅ Direct "Add to Cart" links
- ✅ Mobile responsive
- ✅ Custom Shopify app (no approval needed)
- ✅ Admin settings page
- ✅ Internal admin tool with dual-mode search

---

## 🎯 Business Context

### Current Situation
- Hearn's Hobbies operates on Shopify
- Native predictive search is fast but limited
- Customers ask complex questions that search can't handle
- No conversational shopping assistance
- Customer service gets repetitive product questions

### Problem Statement
Customers need help finding the right products but:
1. Traditional search requires exact keywords
2. Customers don't always know what to search for
3. New hobbyists need recommendations
4. Product comparison questions are hard to answer
5. Customer service is overwhelmed with "which product should I buy?" questions

### Solution Benefits
1. **For Customers:**
   - Natural language product discovery
   - Personalized recommendations
   - Instant answers to product questions
   - Fast traditional search still available

2. **For Business:**
   - Reduced customer service load
   - Higher conversion rates
   - Better customer experience
   - Competitive differentiation

3. **For Staff:**
   - Admin tool with dual-mode search
   - Quick product lookup
   - AI-assisted customer support

---

## 🏗️ Technology Stack

### Backend
- **Framework:** Spring Boot 3.x
- **Language:** Java 17+
- **Database:** PostgreSQL
- **ORM:** Hibernate/JPA
- **Build:** Maven
- **Hosting:** Railway

### Frontend
- **Framework:** React 18+
- **Build Tool:** Vite
- **Styling:** TailwindCSS
- **HTTP Client:** Axios
- **State:** React Hooks

### AI & E-commerce
- **AI Provider:** Anthropic Claude API
- **Model:** Claude Sonnet 4.5 (claude-sonnet-4-5-20250929)
- **E-commerce:** Shopify
- **Integration:** Custom Shopify App + Theme Extension

### External APIs
- Shopify Admin GraphQL API
- Shopify Storefront API (via theme extension)
- Anthropic Claude Messages API

---

## 📅 Project Timeline

**Total Duration:** 5-7 days (35-45 developer hours)

### Phase Breakdown

| Phase | Duration | Description |
|-------|----------|-------------|
| **Phase 1** | 1.5-2 days | Backend foundation (OAuth, database, APIs) |
| **Phase 2** | 1-1.5 days | Shopify theme extension (search bar + modal) |
| **Phase 3** | 0.5-1 day | Backend enhancements (prompts, config) |
| **Phase 4** | 1.5-2 days | Admin product search assistant |
| **Phase 5** | 0.5-1 day | Integration testing |
| **Phase 6** | 0.5-1 day | Production deployment |

---

## 🎨 User Interface Preview

### Customer Experience

```
┌─────────────────────────────────────────────┐
│  hearnshobbies.com Header                   │
├─────────────────────────────────────────────┤
│                                             │
│  [Search products...         ] [🔍] [🤖]   │
│                                             │
└─────────────────────────────────────────────┘
     │                              │
     │ Click 🔍                    │ Click 🤖
     ▼                              ▼
┌────────────────┐      ┌─────────────────────┐
│ Instant Search │      │  AI Chat Modal      │
│ Dropdown       │      │  ┌───────────────┐  │
│ (< 100ms)      │      │  │ 🤖 Hello!     │  │
│                │      │  │               │  │
│ Product 1      │      │  │ [Product Card]│  │
│ Product 2      │      │  │ [Product Card]│  │
│ Product 3      │      │  └───────────────┘  │
└────────────────┘      │  [Type message...]  │
                        └─────────────────────┘
```

### Admin Tool

```
┌─────────────────────────────────────────────┐
│  Product Search Assistant                   │
├─────────────────────────────────────────────┤
│                                             │
│  ┌──────────────┐  ┌──────────────┐        │
│  │ 🔍 Quick     │  │ 🤖 AI        │        │
│  │   Search     │  │   Assistant  │        │
│  │  (Active)    │  │              │        │
│  └──────────────┘  └──────────────┘        │
│                                             │
│  [Search: gundam...                    ]    │
│                                             │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐      │
│  │ Product │ │ Product │ │ Product │      │
│  │  Card   │ │  Card   │ │  Card   │      │
│  └─────────┘ └─────────┘ └─────────┘      │
│                                             │
└─────────────────────────────────────────────┘
```

---

## ✅ Success Criteria

### Customer Experience
- [ ] Search bar clearly shows both options (🔍 Quick / 🤖 AI)
- [ ] AI modal opens in < 300ms
- [ ] AI response within 5 seconds
- [ ] Product recommendations include images, prices, cart links
- [ ] Mobile responsive (< 768px screen width)
- [ ] No disruption to existing search functionality

### Technical
- [ ] 99.9% uptime
- [ ] API response time < 5 seconds (p95)
- [ ] Zero conflicts with Shopify theme
- [ ] CORS properly configured
- [ ] OAuth flow secure and tested
- [ ] Database migrations run successfully

### Business
- [ ] Track AI usage vs traditional search
- [ ] Monitor conversion rate from AI recommendations
- [ ] Measure customer satisfaction
- [ ] Analytics dashboard for insights

---

## 🔐 Security Considerations

### Authentication & Authorization
1. **Shopify OAuth 2.0**
   - HMAC signature verification
   - Nonce/state parameter (CSRF protection)
   - Access tokens encrypted at rest

2. **CORS Configuration**
   - Whitelist hearnshobbies.com only
   - Credentials enabled
   - POST/GET methods only

3. **Rate Limiting**
   - IP-based: 100 requests/minute
   - Shop-based: 500 requests/hour
   - Claude API: respect Anthropic limits

4. **Input Validation**
   - Sanitize shop domain
   - Validate message length
   - Escape HTML in user input
   - Validate JSON schemas

5. **Data Protection**
   - HTTPS only (TLS 1.3)
   - Environment variables for secrets
   - No API keys in frontend code
   - Encrypted database credentials

---

## 📦 Deliverables

### Phase 1 Deliverables
- ✅ Database schema and migration
- ✅ Shopify OAuth flow working
- ✅ Shop data saved to database
- ✅ Chat API accepts shop parameter
- ✅ Deployed backend on Railway

### Phase 2 Deliverables
- ✅ AI button appears in search bar
- ✅ Modal chat interface functional
- ✅ Product cards render with images
- ✅ Mobile responsive design
- ✅ Theme extension deployed

### Phase 3 Deliverables
- ✅ AI returns structured product JSON
- ✅ Shop settings configurable
- ✅ System prompt optimized
- ✅ Analytics tracking implemented

### Phase 4 Deliverables
- ✅ Admin dual-mode search page
- ✅ Product cards in AI chat
- ✅ Mode switching functional
- ✅ Integrated with existing components

### Phase 5 Deliverables
- ✅ All integration tests passing
- ✅ Mobile/browser testing complete
- ✅ Performance benchmarks met
- ✅ Security audit passed

### Phase 6 Deliverables
- ✅ Live on hearnshobbies.com
- ✅ Monitoring dashboard active
- ✅ Documentation complete
- ✅ Training materials provided

---

## 🚀 Quick Start

### Prerequisites
- Java 17+ installed
- Node.js 18+ installed
- PostgreSQL database
- Shopify Partner account
- Anthropic API key
- Railway account (or hosting platform)

### Environment Variables Required
```bash
# Shopify
SHOPIFY_SHOP_URL=hearnshobbies.myshopify.com
SHOPIFY_ACCESS_TOKEN=shpat_...
SHOPIFY_APP_API_KEY=your_api_key
SHOPIFY_APP_API_SECRET=your_api_secret

# Anthropic
ANTHROPIC_API_KEY=sk-ant-...

# Database
DATABASE_URL=postgresql://...
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=...

# App
PORT=8080
```

### Installation Steps (High Level)
1. Clone repository
2. Set environment variables
3. Run database migrations
4. Install dependencies (Maven + npm)
5. Build frontend: `npm run build:deploy`
6. Start backend: `mvn spring-boot:run`
7. Create Shopify app in Partner Dashboard
8. Deploy theme extension
9. Install app on store
10. Enable extension in theme

**Detailed steps in deployment guide (09-PHASE6-DEPLOYMENT.md)**

---

## 📞 Support & Resources

### Documentation Files
- See document index at top of this file
- Each phase has detailed implementation guide
- Code examples provided for every file

### External Resources
- [Shopify App Development](https://shopify.dev/docs/apps)
- [Anthropic Claude API](https://docs.anthropic.com/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [React Documentation](https://react.dev/)

### Getting Help
- Check troubleshooting guide (11-TROUBLESHOOTING.md)
- Review error logs in Railway dashboard
- Test with Shopify development store first
- Use Postman to test API endpoints

---

## 📝 Notes

### Important Considerations
1. **Custom App = No Approval:** Since this is a custom Shopify app (not public), no app review process is needed
2. **OAuth Required:** Even for single shop, OAuth is best practice for security
3. **Theme Compatibility:** Tested with Dawn theme, should work with most modern themes
4. **Mobile First:** Design is mobile-responsive by default
5. **Rate Limits:** Be aware of both Shopify and Claude API rate limits

### Future Enhancements
- Multi-language support
- Voice input for AI chat
- Image search (upload photo, find product)
- Product comparison tool
- Personalized recommendations based on history
- Integration with customer accounts
- Analytics dashboard for merchants
- A/B testing different AI prompts

---

## 🏁 Ready to Start?

**Next Step:** Read `01-ARCHITECTURE.md` to understand the system design.

**Then:** Follow phase-by-phase implementation guides (04-PHASE1 through 09-PHASE6).

**Questions?** Check `11-TROUBLESHOOTING.md` or create an issue.

---

*Documentation Version: 1.0*
*Last Updated: 2025-10-30*
*Project: Hearn's Hobbies AI Search Enhancement*
