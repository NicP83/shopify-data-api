import { useState, useEffect } from 'react';
import { promptTestingApi, formatDate } from '../../services/adminApi';
import './PromptTestingInterface.css';

/**
 * Prompt Testing Interface - Test prompts and track quality
 */
export default function PromptTestingInterface({ shop, userEmail }) {
  const [testResults, setTestResults] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  // Test form
  const [showTestModal, setShowTestModal] = useState(false);
  const [testPrompt, setTestPrompt] = useState('');
  const [testQuery, setTestQuery] = useState('');
  const [testMode, setTestMode] = useState('PREVIEW');
  const [testModes, setTestModes] = useState([]);
  const [testing, setTesting] = useState(false);

  // Filters
  const [selectedMode, setSelectedMode] = useState('ALL');
  const [showFailingOnly, setShowFailingOnly] = useState(false);

  // Detail view
  const [selectedTest, setSelectedTest] = useState(null);

  // Statistics
  const [statistics, setStatistics] = useState(null);

  useEffect(() => {
    loadTestModes();
    loadStatistics();
  }, [shop]);

  useEffect(() => {
    loadTestResults();
  }, [shop, page, selectedMode, showFailingOnly]);

  async function loadTestModes() {
    try {
      const response = await promptTestingApi.getTestModes();
      setTestModes(response.data.testModes || []);
    } catch (error) {
      console.error('Error loading test modes:', error);
    }
  }

  async function loadStatistics() {
    try {
      const response = await promptTestingApi.getStatistics(shop, 30);
      setStatistics(response.data.statistics);
    } catch (error) {
      console.error('Error loading statistics:', error);
    }
  }

  async function loadTestResults() {
    setLoading(true);
    setError(null);

    try {
      let response;

      if (showFailingOnly) {
        response = await promptTestingApi.getFailingTests(shop, 5.0);
        setTestResults(response.data.failingTests || []);
        setTotalPages(1);
      } else {
        const mode = selectedMode === 'ALL' ? null : selectedMode;
        response = await promptTestingApi.getResults(shop, mode, page, 20);
        setTestResults(response.data.testResults || []);
        setTotalPages(response.data.totalPages || 0);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleRunTest() {
    if (!testPrompt.trim() || !testQuery.trim()) {
      alert('Please provide both prompt and test query');
      return;
    }

    setTesting(true);
    try {
      const response = await promptTestingApi.testPrompt(
        shop,
        testPrompt,
        testQuery,
        testMode,
        userEmail
      );

      alert('Test completed successfully!');
      setShowTestModal(false);
      setTestPrompt('');
      setTestQuery('');
      loadTestResults();
      loadStatistics();
    } catch (err) {
      alert('Error running test: ' + err.message);
    } finally {
      setTesting(false);
    }
  }

  async function handleRateTest(testId, humanRating) {
    try {
      await promptTestingApi.rateTest(testId, humanRating, userEmail);
      loadTestResults();
      if (selectedTest && selectedTest.id === testId) {
        const response = await promptTestingApi.getTestResult(testId);
        setSelectedTest(response.data.testResult);
      }
    } catch (err) {
      alert('Error rating test: ' + err.message);
    }
  }

  function getQualityColor(score) {
    if (score >= 8) return '#48bb78';
    if (score >= 6) return '#f6ad55';
    return '#f56565';
  }

  function getQualityLabel(score) {
    if (score >= 8) return 'Excellent';
    if (score >= 6) return 'Good';
    if (score >= 4) return 'Fair';
    return 'Poor';
  }

  function getTestModeIcon(mode) {
    const icons = {
      PREVIEW: '👁️',
      A_B_TEST: '🔄',
      REGRESSION: '🔍',
      MANUAL: '👤'
    };
    return icons[mode] || '🧪';
  }

  function getTestModeColor(mode) {
    const colors = {
      PREVIEW: '#4299e1',
      A_B_TEST: '#9f7aea',
      REGRESSION: '#f6ad55',
      MANUAL: '#48bb78'
    };
    return colors[mode] || '#a0aec0';
  }

  if (loading && testResults.length === 0) {
    return (
      <div className="loading-spinner">
        <div className="spinner"></div>
      </div>
    );
  }

  return (
    <div className="prompt-testing-interface">
      {/* Header */}
      <div className="testing-header">
        <h2>🧪 Prompt Testing</h2>
        <button className="btn-primary" onClick={() => setShowTestModal(true)}>
          + Run New Test
        </button>
      </div>

      {/* Statistics Cards */}
      {statistics && (
        <div className="stats-grid">
          <div className="stat-card">
            <div className="stat-icon">📊</div>
            <div className="stat-content">
              <div className="stat-value">{statistics.totalTests || 0}</div>
              <div className="stat-label">Total Tests</div>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-icon">✅</div>
            <div className="stat-content">
              <div className="stat-value">
                {statistics.passRate ? `${statistics.passRate.toFixed(1)}%` : 'N/A'}
              </div>
              <div className="stat-label">Pass Rate</div>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-icon">⭐</div>
            <div className="stat-content">
              <div className="stat-value">
                {statistics.avgQualityScore ? statistics.avgQualityScore.toFixed(1) : 'N/A'}
              </div>
              <div className="stat-label">Avg Quality</div>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-icon">⚡</div>
            <div className="stat-content">
              <div className="stat-value">
                {statistics.avgResponseTime ? `${statistics.avgResponseTime.toFixed(0)}ms` : 'N/A'}
              </div>
              <div className="stat-label">Avg Response</div>
            </div>
          </div>
        </div>
      )}

      {/* Filters */}
      <div className="filters-section">
        <div className="filter-row">
          <div className="filter-group">
            <label>Test Mode:</label>
            <select
              value={selectedMode}
              onChange={(e) => {
                setSelectedMode(e.target.value);
                setPage(0);
              }}
            >
              <option value="ALL">All Modes</option>
              {testModes.map((mode) => (
                <option key={mode} value={mode}>
                  {mode}
                </option>
              ))}
            </select>
          </div>

          <div className="filter-group">
            <label>
              <input
                type="checkbox"
                checked={showFailingOnly}
                onChange={(e) => {
                  setShowFailingOnly(e.target.checked);
                  setPage(0);
                }}
              />
              Show Failing Only
            </label>
          </div>
        </div>
      </div>

      {error && <div className="error-message">Error: {error}</div>}

      {/* Test Results Table */}
      <div className="results-table-container">
        <table className="results-table">
          <thead>
            <tr>
              <th>Quality</th>
              <th>Mode</th>
              <th>Query</th>
              <th>Response Time</th>
              <th>Tested By</th>
              <th>Tested At</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {testResults.map((test) => (
              <tr key={test.id} className={test.autoQualityScore < 6 ? 'failing-row' : ''}>
                <td>
                  <div className="quality-indicator">
                    <div
                      className="quality-score"
                      style={{ background: getQualityColor(test.autoQualityScore) }}
                    >
                      {test.autoQualityScore?.toFixed(1) || 'N/A'}
                    </div>
                    <div className="quality-label">
                      {getQualityLabel(test.autoQualityScore)}
                    </div>
                  </div>
                </td>
                <td>
                  <span
                    className="mode-badge"
                    style={{ background: getTestModeColor(test.testMode) }}
                  >
                    {getTestModeIcon(test.testMode)} {test.testMode}
                  </span>
                </td>
                <td className="query-cell">{test.testQuery}</td>
                <td className="time-cell">{test.responseTimeMs}ms</td>
                <td className="user-cell">{test.testedBy}</td>
                <td className="timestamp-cell">{formatDate(test.createdAt)}</td>
                <td>
                  <button
                    className="btn-small btn-view"
                    onClick={() => setSelectedTest(test)}
                  >
                    View
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {testResults.length === 0 && !loading && (
          <div className="no-results">No test results found</div>
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="pagination">
          <button
            className="btn-page"
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
          >
            ← Previous
          </button>
          <span className="page-info">
            Page {page + 1} of {totalPages}
          </span>
          <button
            className="btn-page"
            disabled={page >= totalPages - 1}
            onClick={() => setPage(page + 1)}
          >
            Next →
          </button>
        </div>
      )}

      {/* Test Modal */}
      {showTestModal && (
        <div className="modal-overlay" onClick={() => setShowTestModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Run New Prompt Test</h3>
              <button className="btn-close" onClick={() => setShowTestModal(false)}>
                ✕
              </button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>Test Mode:</label>
                <select
                  value={testMode}
                  onChange={(e) => setTestMode(e.target.value)}
                  className="form-select"
                >
                  {testModes.map((mode) => (
                    <option key={mode} value={mode}>
                      {getTestModeIcon(mode)} {mode}
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label>System Prompt:</label>
                <textarea
                  value={testPrompt}
                  onChange={(e) => setTestPrompt(e.target.value)}
                  className="form-textarea"
                  rows={6}
                  placeholder="Enter the system prompt to test..."
                />
              </div>

              <div className="form-group">
                <label>Test Query:</label>
                <textarea
                  value={testQuery}
                  onChange={(e) => setTestQuery(e.target.value)}
                  className="form-textarea"
                  rows={3}
                  placeholder="Enter the test query (e.g., 'Show me running shoes')"
                />
              </div>

              <div className="info-box">
                <strong>ℹ️ Note:</strong> This will execute the prompt against your search system and
                evaluate the quality of results automatically.
              </div>

              <div className="modal-actions">
                <button className="btn-cancel" onClick={() => setShowTestModal(false)}>
                  Cancel
                </button>
                <button
                  className="btn-submit"
                  onClick={handleRunTest}
                  disabled={testing}
                >
                  {testing ? 'Testing...' : 'Run Test'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Detail Modal */}
      {selectedTest && (
        <div className="modal-overlay" onClick={() => setSelectedTest(null)}>
          <div className="modal-content large" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Test Result Details</h3>
              <button className="btn-close" onClick={() => setSelectedTest(null)}>
                ✕
              </button>
            </div>
            <div className="modal-body">
              {/* Quality Score Section */}
              <div className="detail-section quality-section">
                <h4>Quality Assessment</h4>
                <div className="quality-display">
                  <div
                    className="quality-circle"
                    style={{ background: getQualityColor(selectedTest.autoQualityScore) }}
                  >
                    <div className="quality-number">{selectedTest.autoQualityScore?.toFixed(1)}</div>
                    <div className="quality-text">{getQualityLabel(selectedTest.autoQualityScore)}</div>
                  </div>
                  <div className="quality-details">
                    <div className="quality-metric">
                      <span className="metric-label">Auto Score:</span>
                      <span className="metric-value">{selectedTest.autoQualityScore?.toFixed(2)}</span>
                    </div>
                    {selectedTest.humanRating && (
                      <div className="quality-metric">
                        <span className="metric-label">Human Rating:</span>
                        <span className="metric-value">{selectedTest.humanRating}/10</span>
                      </div>
                    )}
                    <div className="quality-metric">
                      <span className="metric-label">Response Time:</span>
                      <span className="metric-value">{selectedTest.responseTimeMs}ms</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Rating Section */}
              {!selectedTest.humanRating && (
                <div className="detail-section rating-section">
                  <h4>Rate This Test</h4>
                  <div className="rating-buttons">
                    {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((rating) => (
                      <button
                        key={rating}
                        className="btn-rating"
                        onClick={() => handleRateTest(selectedTest.id, rating)}
                      >
                        {rating}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* Test Info */}
              <div className="detail-grid">
                <div className="detail-item">
                  <label>Test Mode:</label>
                  <span
                    className="mode-badge"
                    style={{ background: getTestModeColor(selectedTest.testMode) }}
                  >
                    {getTestModeIcon(selectedTest.testMode)} {selectedTest.testMode}
                  </span>
                </div>
                <div className="detail-item">
                  <label>Tested By:</label>
                  <span>{selectedTest.testedBy}</span>
                </div>
                <div className="detail-item">
                  <label>Tested At:</label>
                  <span>{formatDate(selectedTest.createdAt)}</span>
                </div>
              </div>

              {/* Prompt */}
              <div className="detail-section">
                <h4>System Prompt</h4>
                <pre className="text-display">{selectedTest.promptText}</pre>
              </div>

              {/* Query */}
              <div className="detail-section">
                <h4>Test Query</h4>
                <pre className="text-display">{selectedTest.testQuery}</pre>
              </div>

              {/* Response */}
              <div className="detail-section">
                <h4>AI Response</h4>
                <pre className="text-display">{selectedTest.aiResponse}</pre>
              </div>

              {/* Context */}
              {selectedTest.testContext && Object.keys(selectedTest.testContext).length > 0 && (
                <div className="detail-section">
                  <h4>Test Context</h4>
                  <pre className="json-display">
                    {JSON.stringify(selectedTest.testContext, null, 2)}
                  </pre>
                </div>
              )}

              {/* Product IDs */}
              {selectedTest.productIds && selectedTest.productIds.length > 0 && (
                <div className="detail-section">
                  <h4>Product IDs ({selectedTest.productIds.length})</h4>
                  <div className="product-ids">
                    {selectedTest.productIds.map((id, idx) => (
                      <span key={idx} className="product-id-badge">
                        {id}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
