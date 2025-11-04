# Phase 3: Admin Backend APIs - Complete Documentation

**Status:** ✅ DEPLOYED
**Date:** 2025-11-04
**Version:** 1.0.0

---

## Overview

Phase 3 provides comprehensive REST APIs for managing admin activity logs, configuration versioning/rollback, and prompt testing. These APIs are ready for the Phase 4 admin UI dashboard.

---

## 🎯 API Endpoints Summary

| Controller | Endpoints | Purpose |
|------------|-----------|---------|
| **AdminActivityController** | 10 endpoints | View, search, and analyze admin activity logs |
| **ConfigManagementController** | 11 endpoints | Manage configuration versions and rollback |
| **PromptTestingController** | 9 endpoints | Test prompts and track quality metrics |
| **Total** | **30 endpoints** | **Complete admin management system** |

---

## 1. Admin Activity Log APIs

**Base URL:** `/api/admin/activity`

### 1.1 Get Activity Logs (Paginated)
```http
GET /api/admin/activity/logs?shop=hearnshobbies.myshopify.com&page=0&size=20
```

**Response:**
```json
{
  "success": true,
  "data": {
    "logs": [...],
    "currentPage": 0,
    "totalPages": 5,
    "totalItems": 100,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

### 1.2 Get Logs by Category
```http
GET /api/admin/activity/logs/category?shop=hearnshobbies.myshopify.com&category=CONFIG&page=0&size=20
```

**Categories:** `CONFIG`, `PROMPT`, `TESTING`, `ANALYTICS`, `SYSTEM`

### 1.3 Get Recent Activity
```http
GET /api/admin/activity/recent?shop=hearnshobbies.myshopify.com&days=7
```

### 1.4 Get Failed Actions
```http
GET /api/admin/activity/failed?shop=hearnshobbies.myshopify.com
```

Returns all failed admin actions for troubleshooting.

### 1.5 Get Activity Statistics
```http
GET /api/admin/activity/statistics?shop=hearnshobbies.myshopify.com&days=30
```

**Response:**
```json
{
  "success": true,
  "data": {
    "statistics": {
      "actionsByCategory": {"CONFIG": 45, "PROMPT": 23, "TESTING": 12},
      "failedActions": 3,
      "avgDurationByCategory": {"CONFIG": 125.5, "PROMPT": 89.3},
      "mostActiveUsers": {"admin@example.com": 67, "user@example.com": 23}
    },
    "period": "30 days"
  }
}
```

### 1.6 Search Activity Logs
```http
GET /api/admin/activity/search?shop=hearnshobbies.myshopify.com&query=AI+model
```

### 1.7 Anonymize Old Logs (GDPR)
```http
POST /api/admin/activity/anonymize?shop=hearnshobbies.myshopify.com&retentionYears=2
```

### 1.8 Get Action Categories
```http
GET /api/admin/activity/categories
```

### 1.9 Get Action Types
```http
GET /api/admin/activity/action-types
```

---

## 2. Configuration Management APIs

**Base URL:** `/api/admin/config`

### 2.1 Save Configuration Version
```http
POST /api/admin/config/save
Content-Type: application/json

{
  "shop": "hearnshobbies.myshopify.com",
  "configType": "AI_MODEL",
  "configSnapshot": {
    "model": "claude-3-7-sonnet-20250219",
    "temperature": 0.7,
    "maxTokens": 4096
  },
  "changedBy": "admin@example.com",
  "changeReason": "Updated to Claude 3.7 Sonnet for better performance"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "version": {
      "id": 15,
      "versionNumber": 5,
      "configType": "AI_MODEL",
      "isActive": true,
      "createdAt": "2025-11-04T16:30:00Z",
      "changedBy": "admin@example.com"
    },
    "message": "Configuration version saved successfully"
  }
}
```

### 2.2 Activate Configuration Version
```http
POST /api/admin/config/activate/15?shop=hearnshobbies.myshopify.com&configType=AI_MODEL
```

### 2.3 Rollback to Previous Version
```http
POST /api/admin/config/rollback
Content-Type: application/json

{
  "shop": "hearnshobbies.myshopify.com",
  "configType": "AI_MODEL",
  "targetVersionId": 12,
  "rolledBackBy": "admin@example.com",
  "rollbackReason": "Performance degradation with version 5"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "rolledBackVersion": {
      "id": 16,
      "versionNumber": 6,
      "changeType": "ROLLBACK",
      "isActive": true
    },
    "message": "Successfully rolled back to version 3"
  }
}
```

### 2.4 Get Configuration History
```http
GET /api/admin/config/history?shop=hearnshobbies.myshopify.com&configType=AI_MODEL&page=0&size=20
```

### 2.5 Get Active Configuration
```http
GET /api/admin/config/active?shop=hearnshobbies.myshopify.com&configType=AI_MODEL
```

### 2.6 Get Configuration Version by ID
```http
GET /api/admin/config/versions/15
```

### 2.7 Compare Two Versions
```http
GET /api/admin/config/compare?version1=12&version2=15
```

**Response:**
```json
{
  "success": true,
  "data": {
    "comparison": {
      "version1": {"versionNumber": 3, "config": {...}},
      "version2": {"versionNumber": 5, "config": {...}},
      "differences": {
        "model": {"old": "claude-3-sonnet", "new": "claude-3-7-sonnet"},
        "temperature": {"old": 0.5, "new": 0.7}
      },
      "performanceComparison": {
        "version1Performance": {"responseTime": 850, "successRate": 0.92},
        "version2Performance": {"responseTime": 720, "successRate": 0.95},
        "improvement": {"responseTime": -15.29, "successRate": 3.26}
      }
    }
  }
}
```

### 2.8 Get Recent Changes
```http
GET /api/admin/config/recent?shop=hearnshobbies.myshopify.com&days=7
```

### 2.9 Get Configuration Types
```http
GET /api/admin/config/types
```

**Config Types:** `AI_MODEL`, `BEHAVIOR`, `PROMPT`, `ANALYTICS`, `FULL_CONFIG`

### 2.10 Disable Rollback for Version
```http
POST /api/admin/config/versions/15/disable-rollback?reason=Critical+security+fix
```

### 2.11 Cleanup Old Versions
```http
POST /api/admin/config/cleanup?shop=hearnshobbies.myshopify.com&configType=AI_MODEL&keepCount=10
```

---

## 3. Prompt Testing APIs

**Base URL:** `/api/admin/prompt-testing`

### 3.1 Test a Prompt
```http
POST /api/admin/prompt-testing/test
Content-Type: application/json

{
  "shop": "hearnshobbies.myshopify.com",
  "promptId": 1,
  "testQuery": "Show me hobby paints under $10",
  "testContext": {
    "conversationHistory": [],
    "filters": {"priceMax": 10}
  },
  "testMode": "PREVIEW",
  "testedBy": "admin@example.com"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "testResult": {
      "id": 42,
      "testQuery": "Show me hobby paints under $10",
      "aiResponse": "I found 12 hobby paints under $10...",
      "productsReturned": 12,
      "responseTimeMs": 850,
      "autoQualityScore": 85.5,
      "tokensUsed": 250,
      "apiCostUsd": 0.001250,
      "testedAt": "2025-11-04T16:45:00Z"
    },
    "message": "Prompt test completed successfully"
  }
}
```

### 3.2 Get Test Results
```http
GET /api/admin/prompt-testing/results?shop=hearnshobbies.myshopify.com&promptId=1&page=0&size=20
```

### 3.3 Get Test Result by ID
```http
GET /api/admin/prompt-testing/results/42
```

### 3.4 Rate a Test Result
```http
POST /api/admin/prompt-testing/results/42/rate
Content-Type: application/json

{
  "rating": 4,
  "notes": "Good results, but could be more specific about paint types"
}
```

### 3.5 Get Test Statistics
```http
GET /api/admin/prompt-testing/statistics?shop=hearnshobbies.myshopify.com&days=30
```

**Response:**
```json
{
  "success": true,
  "data": {
    "statistics": {
      "totalTests": 156,
      "passingTests": 142,
      "passRate": 91.03,
      "averageResponseTimeMs": 785.5,
      "testsByMode": {
        "PREVIEW": 89,
        "A_B_TEST": 45,
        "REGRESSION": 15,
        "MANUAL": 7
      }
    },
    "period": "30 days"
  }
}
```

### 3.6 Get Failing Tests
```http
GET /api/admin/prompt-testing/failing?shop=hearnshobbies.myshopify.com
```

Returns tests with quality score < 70 or rating < 3.

### 3.7 Compare Two Test Results
```http
GET /api/admin/prompt-testing/compare?result1=40&result2=42
```

### 3.8 Get Test Modes
```http
GET /api/admin/prompt-testing/test-modes
```

**Test Modes:** `PREVIEW`, `A_B_TEST`, `REGRESSION`, `MANUAL`

### 3.9 Cleanup Old Tests
```http
DELETE /api/admin/prompt-testing/cleanup?shop=hearnshobbies.myshopify.com&days=90
```

---

## 🔒 Security & Error Handling

### Authentication
All endpoints support the existing authentication mechanisms. User email is extracted from:
1. Request parameters (`userEmail`)
2. HTTP headers (`X-User-Email`)
3. Security context (if configured)

### Error Responses
```json
{
  "success": false,
  "message": "Shop not found: invalid.myshopify.com"
}
```

### CORS
All controllers support CORS with `@CrossOrigin(origins = "*")` for development. Update for production.

---

## 🎨 Usage Examples

### Example 1: Save and Rollback Configuration

```bash
# 1. Save new AI model configuration
curl -X POST 'http://localhost:8080/api/admin/config/save' \
  -H 'Content-Type: application/json' \
  -d '{
    "shop": "hearnshobbies.myshopify.com",
    "configType": "AI_MODEL",
    "configSnapshot": {"model": "claude-3-7-sonnet-20250219", "temperature": 0.7},
    "changedBy": "admin@example.com",
    "changeReason": "Testing new model"
  }'

# 2. Monitor performance...

# 3. Rollback if issues found
curl -X POST 'http://localhost:8080/api/admin/config/rollback' \
  -H 'Content-Type: application/json' \
  -d '{
    "shop": "hearnshobbies.myshopify.com",
    "configType": "AI_MODEL",
    "targetVersionId": 12,
    "rolledBackBy": "admin@example.com",
    "rollbackReason": "Higher error rate"
  }'
```

### Example 2: Test Prompt Quality

```bash
# 1. Test a prompt
curl -X POST 'http://localhost:8080/api/admin/prompt-testing/test' \
  -H 'Content-Type: application/json' \
  -d '{
    "shop": "hearnshobbies.myshopify.com",
    "promptId": 1,
    "testQuery": "Show me Gundam model kits",
    "testMode": "PREVIEW",
    "testedBy": "admin@example.com"
  }'

# 2. Rate the result
curl -X POST 'http://localhost:8080/api/admin/prompt-testing/results/42/rate' \
  -H 'Content-Type: application/json' \
  -d '{
    "rating": 5,
    "notes": "Excellent results!"
  }'

# 3. Get statistics
curl 'http://localhost:8080/api/admin/prompt-testing/statistics?shop=hearnshobbies.myshopify.com&days=7'
```

### Example 3: Monitor Admin Activity

```bash
# 1. Get recent activity
curl 'http://localhost:8080/api/admin/activity/recent?shop=hearnshobbies.myshopify.com&days=7'

# 2. Search for specific actions
curl 'http://localhost:8080/api/admin/activity/search?shop=hearnshobbies.myshopify.com&query=AI+model'

# 3. Get statistics
curl 'http://localhost:8080/api/admin/activity/statistics?shop=hearnshobbies.myshopify.com&days=30'

# 4. Check for failures
curl 'http://localhost:8080/api/admin/activity/failed?shop=hearnshobbies.myshopify.com'
```

---

## 📊 Integration with Phase 2 Logging

All admin APIs automatically log their activities using the `@LogActivity` annotation:

```java
@LogActivity(
    actionType = AdminActivityLog.ACTION_UPDATE,
    actionCategory = AdminActivityLog.CATEGORY_CONFIG,
    resourceType = "config_version",
    descriptionTemplate = "Rolled back configuration to version {2}",
    captureReturnValue = true
)
@PostMapping("/rollback")
public ResponseEntity<Map<String, Object>> rollbackToVersion(...)
```

This ensures complete audit trails for all admin operations.

---

## 🚀 Next Steps: Phase 4

Phase 4 will build a React admin dashboard that consumes these APIs:

1. **Activity Log Viewer** → Uses Admin Activity APIs
2. **Config Version Manager** → Uses Config Management APIs
3. **Prompt Testing Interface** → Uses Prompt Testing APIs
4. **Performance Dashboard** → Aggregates data from all APIs
5. **Rollback Console** → One-click configuration rollback
6. **Analytics Charts** → Visual representation of statistics

---

## ✅ Phase 3 Complete!

**Delivered:**
- ✅ 30 REST API endpoints
- ✅ Complete CRUD operations for all admin features
- ✅ Pagination support
- ✅ Search and filtering
- ✅ Statistics and analytics
- ✅ Automatic activity logging
- ✅ CORS support
- ✅ Error handling
- ✅ Comprehensive documentation

**Ready for Phase 4 frontend development!**
