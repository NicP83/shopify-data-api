/**
 * Admin API Service
 * Provides methods for interacting with Phase 3 Admin Backend APIs
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

/**
 * Generic API call wrapper with error handling
 */
async function apiCall(endpoint, options = {}) {
  try {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
    });

    const data = await response.json();

    if (!response.ok) {
      throw new Error(data.message || `API call failed: ${response.status}`);
    }

    return data;
  } catch (error) {
    console.error(`API Error (${endpoint}):`, error);
    throw error;
  }
}

// ============================================================================
// ACTIVITY LOG APIs
// ============================================================================

export const activityApi = {
  /**
   * Get paginated activity logs
   */
  getLogs: (shop, page = 0, size = 20) => {
    return apiCall(`/api/admin/activity/logs?shop=${shop}&page=${page}&size=${size}`);
  },

  /**
   * Get logs by category
   */
  getLogsByCategory: (shop, category, page = 0, size = 20) => {
    return apiCall(
      `/api/admin/activity/logs/category?shop=${shop}&category=${category}&page=${page}&size=${size}`
    );
  },

  /**
   * Get recent activity (last N days)
   */
  getRecentActivity: (shop, days = 7) => {
    return apiCall(`/api/admin/activity/recent?shop=${shop}&days=${days}`);
  },

  /**
   * Get failed actions
   */
  getFailedActions: (shop) => {
    return apiCall(`/api/admin/activity/failed?shop=${shop}`);
  },

  /**
   * Get activity statistics
   */
  getStatistics: (shop, days = 30) => {
    return apiCall(`/api/admin/activity/statistics?shop=${shop}&days=${days}`);
  },

  /**
   * Search activity logs
   */
  search: (shop, query) => {
    return apiCall(`/api/admin/activity/search?shop=${shop}&query=${encodeURIComponent(query)}`);
  },

  /**
   * Anonymize old logs (GDPR)
   */
  anonymizeLogs: (shop, retentionYears = 2) => {
    return apiCall(
      `/api/admin/activity/anonymize?shop=${shop}&retentionYears=${retentionYears}`,
      { method: 'POST' }
    );
  },

  /**
   * Get available categories
   */
  getCategories: () => {
    return apiCall('/api/admin/activity/categories');
  },

  /**
   * Get available action types
   */
  getActionTypes: () => {
    return apiCall('/api/admin/activity/action-types');
  },
};

// ============================================================================
// CONFIGURATION MANAGEMENT APIs
// ============================================================================

export const configApi = {
  /**
   * Save configuration version
   */
  saveVersion: (shop, configType, configSnapshot, changedBy, changeReason) => {
    return apiCall('/api/admin/config/save', {
      method: 'POST',
      body: JSON.stringify({
        shop,
        configType,
        configSnapshot,
        changedBy,
        changeReason,
      }),
    });
  },

  /**
   * Activate configuration version
   */
  activateVersion: (versionId, shop, configType) => {
    return apiCall(
      `/api/admin/config/activate/${versionId}?shop=${shop}&configType=${configType}`,
      { method: 'POST' }
    );
  },

  /**
   * Rollback to previous version
   */
  rollback: (shop, configType, targetVersionId, rolledBackBy, rollbackReason) => {
    return apiCall('/api/admin/config/rollback', {
      method: 'POST',
      body: JSON.stringify({
        shop,
        configType,
        targetVersionId,
        rolledBackBy,
        rollbackReason,
      }),
    });
  },

  /**
   * Get configuration history
   */
  getHistory: (shop, configType, page = 0, size = 20) => {
    return apiCall(
      `/api/admin/config/history?shop=${shop}&configType=${configType}&page=${page}&size=${size}`
    );
  },

  /**
   * Get active configuration
   */
  getActive: (shop, configType) => {
    return apiCall(`/api/admin/config/active?shop=${shop}&configType=${configType}`);
  },

  /**
   * Get version by ID
   */
  getVersionById: (versionId) => {
    return apiCall(`/api/admin/config/versions/${versionId}`);
  },

  /**
   * Compare two versions
   */
  compareVersions: (version1, version2) => {
    return apiCall(`/api/admin/config/compare?version1=${version1}&version2=${version2}`);
  },

  /**
   * Get recent changes
   */
  getRecentChanges: (shop, days = 7) => {
    return apiCall(`/api/admin/config/recent?shop=${shop}&days=${days}`);
  },

  /**
   * Get available config types
   */
  getTypes: () => {
    return apiCall('/api/admin/config/types');
  },

  /**
   * Disable rollback for version
   */
  disableRollback: (versionId, reason) => {
    return apiCall(`/api/admin/config/versions/${versionId}/disable-rollback?reason=${encodeURIComponent(reason)}`, {
      method: 'POST',
    });
  },

  /**
   * Cleanup old versions
   */
  cleanup: (shop, configType, keepCount = 10) => {
    return apiCall(
      `/api/admin/config/cleanup?shop=${shop}&configType=${configType}&keepCount=${keepCount}`,
      { method: 'POST' }
    );
  },
};

// ============================================================================
// PROMPT TESTING APIs
// ============================================================================

export const promptTestingApi = {
  /**
   * Test a prompt
   */
  testPrompt: (shop, promptId, testQuery, testContext, testMode, testedBy) => {
    return apiCall('/api/admin/prompt-testing/test', {
      method: 'POST',
      body: JSON.stringify({
        shop,
        promptId,
        testQuery,
        testContext,
        testMode,
        testedBy,
      }),
    });
  },

  /**
   * Get test results
   */
  getResults: (shop, promptId = null, page = 0, size = 20) => {
    const url = promptId
      ? `/api/admin/prompt-testing/results?shop=${shop}&promptId=${promptId}&page=${page}&size=${size}`
      : `/api/admin/prompt-testing/results?shop=${shop}&page=${page}&size=${size}`;
    return apiCall(url);
  },

  /**
   * Get test result by ID
   */
  getResultById: (resultId) => {
    return apiCall(`/api/admin/prompt-testing/results/${resultId}`);
  },

  /**
   * Rate a test result
   */
  rateResult: (resultId, rating, notes) => {
    return apiCall(`/api/admin/prompt-testing/results/${resultId}/rate`, {
      method: 'POST',
      body: JSON.stringify({ rating, notes }),
    });
  },

  /**
   * Get test statistics
   */
  getStatistics: (shop, days = 30) => {
    return apiCall(`/api/admin/prompt-testing/statistics?shop=${shop}&days=${days}`);
  },

  /**
   * Get failing tests
   */
  getFailingTests: (shop) => {
    return apiCall(`/api/admin/prompt-testing/failing?shop=${shop}`);
  },

  /**
   * Compare two test results
   */
  compareResults: (result1, result2) => {
    return apiCall(`/api/admin/prompt-testing/compare?result1=${result1}&result2=${result2}`);
  },

  /**
   * Get test modes
   */
  getTestModes: () => {
    return apiCall('/api/admin/prompt-testing/test-modes');
  },

  /**
   * Cleanup old tests
   */
  cleanup: (shop, days = 90) => {
    return apiCall(`/api/admin/prompt-testing/cleanup?shop=${shop}&days=${days}`, {
      method: 'DELETE',
    });
  },
};

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

/**
 * Format date for display
 */
export function formatDate(dateString) {
  if (!dateString) return 'N/A';
  const date = new Date(dateString);
  return date.toLocaleString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/**
 * Format duration in milliseconds
 */
export function formatDuration(ms) {
  if (!ms) return 'N/A';
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`;
  return `${(ms / 60000).toFixed(2)}m`;
}

/**
 * Get status badge color
 */
export function getStatusColor(success) {
  return success ? 'green' : 'red';
}

/**
 * Get category badge color
 */
export function getCategoryColor(category) {
  const colors = {
    CONFIG: 'blue',
    PROMPT: 'purple',
    TESTING: 'yellow',
    ANALYTICS: 'cyan',
    SYSTEM: 'gray',
  };
  return colors[category] || 'gray';
}

export default {
  activityApi,
  configApi,
  promptTestingApi,
  formatDate,
  formatDuration,
  getStatusColor,
  getCategoryColor,
};
