# Shopify Chat Widget Performance Optimization

> **Model note (July 2026):** Claude model IDs in this document are historical. The runtime model is set by the `ANTHROPIC_MODEL` env var (currently `claude-sonnet-4-6`) — see CLAUDE.md.

## Current Performance Status (WORKING10)

### Configuration
- **Model**: claude-sonnet-4-5-20250929 (Claude Sonnet 4.5)
- **Max Tokens**: 1536
- **Temperature**: 0.7
- **System Prompt**: ~400 lines (comprehensive product search instructions)

### Measured Performance
- **Typical Response Time**: 3-5 seconds for product search queries
- **Tool Use**: 2-3 API roundtrips per conversation (think → call tool → respond)
- **Database Queries**: Config loaded from PostgreSQL on every request

## Identified Bottlenecks

### 1. Database Query Overhead (High Priority)
**Impact**: 50-200ms added latency per request
**Root Cause**: `ChatbotConfigService.getConfig()` queries PostgreSQL for configuration on every chat message
**Location**: `src/main/java/com/shopify/api/service/ChatbotConfigService.java:95`

```java
public ChatbotConfig getConfig() {
    // Queries database every time - NO CACHING
    Optional<ChatbotConfigEntity> configEntity = configRepository.findGlobalConfig();
    // ...
}
```

**Solution**: Implement Spring Cache with `@Cacheable` annotation
**Effort**: 10-15 minutes
**Risk**: Very low

### 2. Large System Prompt (Medium Priority)
**Impact**: Adds input token cost and processing time on every request
**Root Cause**: ~400 line system prompt sent to Claude API on every message
**Location**: `src/main/java/com/shopify/api/service/ChatAgentService.java:337-456`

**Current Approach**: Full prompt sent each time
**Anthropic's Solution**: Prompt Caching API
- Cache static portions of system prompt
- 90% cost reduction for cached input tokens
- Faster API response times
- Requires API version 2024-07-15 or later

**Solution Options**:
1. Implement Anthropic prompt caching (recommended)
2. Reduce prompt length by removing redundant sections
3. Both

**Effort**: 30-45 minutes for prompt caching
**Risk**: Low (well-documented Anthropic feature)

### 3. Tool Use Multi-Turn Overhead (Low Priority)
**Impact**: Each product search requires multiple API calls
**Root Cause**: Claude's tool use pattern requires:
1. Think about query
2. Call `search_products` tool
3. Wait for results
4. Formulate response

**Nature**: This is inherent to Claude's tool use architecture
**Mitigation Options**:
- Pre-fetch common products (complex, maintenance overhead)
- Accept as normal behavior (recommended - already quite fast)

**Priority**: Low - this is expected behavior and already optimized

## Optimization Plan

### Phase 1: Quick Wins (Immediate)
**Goal**: Reduce latency by 50-200ms with minimal code changes

#### 1.1 Implement Config Caching
- Add `@EnableCaching` to main application class
- Create `CacheConfig.java` with simple in-memory cache
- Add `@Cacheable("chatbotConfig")` to `getConfig()` method
- Add `@CacheEvict("chatbotConfig")` to `updateConfig()` method

**Expected Impact**: 50-200ms faster per request

#### 1.2 Add Cache Warmup
- Preload config into cache on application startup
- Ensures first request isn't slow

### Phase 2: Anthropic Prompt Caching (Recommended)
**Goal**: Reduce API cost by 90% and improve response times by 20-30%

#### 2.1 Update API Version
- Change `anthropic.api.version` from `2023-06-01` to `2024-07-15`
- Enables prompt caching features

#### 2.2 Add Cache Control Breakpoints
Modify system prompt structure to mark cacheable sections:

```java
// System content with cache control
{
  "type": "text",
  "text": "You are Camilla, customer service assistant for Hearn's Hobbies...",
  "cache_control": {"type": "ephemeral"}  // Cache this section
}
```

#### 2.3 Update Request Builder
Modify `ChatAgentService.callClaudeWithTools()` to include cache control in request body.

**Expected Impact**:
- 90% cost reduction on cached input tokens
- 20-30% faster Claude API responses
- Cache TTL: 5 minutes (Anthropic default)

#### 2.4 Monitor Cache Performance
Add logging for cache hits/misses to track effectiveness.

### Phase 3: System Prompt Optimization (Optional)
**Goal**: Reduce prompt length while maintaining quality

#### 3.1 Audit Current Prompt
- Review 400+ line prompt in `buildSystemPromptFromConfig()`
- Identify redundant or verbose sections
- Consolidate similar instructions

#### 3.2 A/B Testing
- Test shortened prompt against current version
- Measure quality impact on real queries
- Roll back if quality degrades

**Expected Impact**: 10-15% faster, clearer instructions for Claude

## Implementation Priority

### Immediate (Today)
1. ✅ Config caching - Quick win, zero risk
2. ✅ Cache warmup - Ensures consistent performance

### This Week
3. Anthropic prompt caching - Significant cost/speed improvement
4. Cache monitoring and metrics

### Future Consideration
5. System prompt optimization - Only if needed after caching improvements

## Success Metrics

### Before Optimization (Baseline)
- Response Time: 3-5 seconds
- Config Load Time: 50-200ms per request
- Input Tokens: ~2000 per request
- Cost per Request: ~$0.01

### After Phase 1 (Config Caching)
- Response Time: 2.8-4.8 seconds ✅
- Config Load Time: <5ms (cached) ✅
- Input Tokens: ~2000 per request
- Cost per Request: ~$0.01

### After Phase 2 (Prompt Caching)
- Response Time: 2.0-3.5 seconds ✅
- Config Load Time: <5ms (cached) ✅
- Input Tokens: ~200 per request (cached) ✅
- Cost per Request: ~$0.001 ✅

## Testing Plan

### Manual Testing
1. Clear cache
2. Send test query: "What paints do I need for a Tamiya Spitfire?"
3. Measure response time
4. Send same query again (should be faster with cache)
5. Verify response quality unchanged

### Automated Testing
1. Load test with multiple concurrent users
2. Monitor Railway logs for errors
3. Check cache hit rates in metrics
4. Verify cost reduction in analytics dashboard

## Rollback Plan

If performance degrades or errors occur:
1. Disable caching: Remove `@Cacheable` annotations
2. Revert API version: Back to `2023-06-01`
3. Monitor error rates return to normal
4. Investigate root cause before re-attempting

## References

- [Anthropic Prompt Caching Documentation](https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching)
- [Spring Cache Documentation](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Claude API Rate Limits](https://docs.anthropic.com/en/api/rate-limits)

---
*Last updated: 2025-11-11 - WORKING10 Milestone*
