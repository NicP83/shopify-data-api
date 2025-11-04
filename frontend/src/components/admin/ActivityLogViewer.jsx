import { useState, useEffect } from 'react';
import { activityApi, formatDate, getCategoryColor } from '../../services/adminApi';
import './ActivityLogViewer.css';

/**
 * Activity Log Viewer - View and search admin activity logs
 */
export default function ActivityLogViewer({ shop, userEmail }) {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalLogs, setTotalLogs] = useState(0);

  // Filters
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [showFailedOnly, setShowFailedOnly] = useState(false);

  // Detail view
  const [selectedLog, setSelectedLog] = useState(null);

  // Available categories
  const [categories, setCategories] = useState([]);

  useEffect(() => {
    loadCategories();
  }, []);

  useEffect(() => {
    loadLogs();
  }, [shop, page, selectedCategory, showFailedOnly]);

  async function loadCategories() {
    try {
      const response = await activityApi.getCategories();
      setCategories(['ALL', ...response.data.categories]);
    } catch (error) {
      console.error('Error loading categories:', error);
    }
  }

  async function loadLogs() {
    setLoading(true);
    setError(null);

    try {
      let response;

      if (showFailedOnly) {
        response = await activityApi.getFailedActions(shop);
        setLogs(response.data.failedActions || []);
        setTotalPages(1);
        setTotalLogs(response.data.failedActions?.length || 0);
      } else if (selectedCategory !== 'ALL') {
        response = await activityApi.getByCategory(shop, selectedCategory, page, 20);
        setLogs(response.data.logs || []);
        setTotalPages(response.data.totalPages || 0);
        setTotalLogs(response.data.total || 0);
      } else {
        response = await activityApi.getLogs(shop, page, 20);
        setLogs(response.data.logs || []);
        setTotalPages(response.data.totalPages || 0);
        setTotalLogs(response.data.total || 0);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleSearch() {
    if (!searchQuery.trim()) {
      loadLogs();
      return;
    }

    setLoading(true);
    try {
      const response = await activityApi.searchLogs(shop, searchQuery, startDate, endDate, 0, 20);
      setLogs(response.data.logs || []);
      setTotalPages(response.data.totalPages || 0);
      setTotalLogs(response.data.total || 0);
      setPage(0);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  function handleClearFilters() {
    setSelectedCategory('ALL');
    setSearchQuery('');
    setStartDate('');
    setEndDate('');
    setShowFailedOnly(false);
    setPage(0);
  }

  function getCategoryIcon(category) {
    const icons = {
      CONFIG: '⚙️',
      PROMPT: '📝',
      TESTING: '🧪',
      ANALYTICS: '📊',
      SYSTEM: '🔧'
    };
    return icons[category] || '📄';
  }

  if (loading && logs.length === 0) {
    return (
      <div className="loading-spinner">
        <div className="spinner"></div>
      </div>
    );
  }

  return (
    <div className="activity-log-viewer">
      {/* Header */}
      <div className="viewer-header">
        <h2>📝 Activity Log</h2>
        <div className="log-stats">
          {totalLogs} {totalLogs === 1 ? 'entry' : 'entries'}
        </div>
      </div>

      {/* Filters */}
      <div className="filters-section">
        <div className="filter-row">
          <div className="filter-group">
            <label>Category:</label>
            <select
              value={selectedCategory}
              onChange={(e) => {
                setSelectedCategory(e.target.value);
                setPage(0);
              }}
            >
              {categories.map((cat) => (
                <option key={cat} value={cat}>
                  {cat}
                </option>
              ))}
            </select>
          </div>

          <div className="filter-group">
            <label>
              <input
                type="checkbox"
                checked={showFailedOnly}
                onChange={(e) => {
                  setShowFailedOnly(e.target.checked);
                  setPage(0);
                }}
              />
              Show Failed Only
            </label>
          </div>

          <button className="btn-clear" onClick={handleClearFilters}>
            Clear Filters
          </button>
        </div>

        <div className="search-row">
          <input
            type="text"
            className="search-input"
            placeholder="Search logs (user, action, entity)..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
          />
          <input
            type="date"
            className="date-input"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            placeholder="Start date"
          />
          <input
            type="date"
            className="date-input"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
            placeholder="End date"
          />
          <button className="btn-search" onClick={handleSearch}>
            🔍 Search
          </button>
        </div>
      </div>

      {error && <div className="error-message">Error: {error}</div>}

      {/* Logs Table */}
      <div className="logs-table-container">
        <table className="logs-table">
          <thead>
            <tr>
              <th>Status</th>
              <th>Category</th>
              <th>Action</th>
              <th>User</th>
              <th>Entity</th>
              <th>Timestamp</th>
              <th>Details</th>
            </tr>
          </thead>
          <tbody>
            {logs.map((log) => (
              <tr key={log.id} className={!log.success ? 'failed-row' : ''}>
                <td>
                  <span className={`status-badge ${log.success ? 'success' : 'failed'}`}>
                    {log.success ? '✓' : '✗'}
                  </span>
                </td>
                <td>
                  <span className="category-badge" style={{ background: getCategoryColor(log.actionCategory) }}>
                    {getCategoryIcon(log.actionCategory)} {log.actionCategory}
                  </span>
                </td>
                <td className="action-cell">{log.actionType}</td>
                <td className="user-cell">{log.userEmail}</td>
                <td className="entity-cell">{log.entityType || '-'}</td>
                <td className="timestamp-cell">{formatDate(log.createdAt)}</td>
                <td>
                  <button
                    className="btn-details"
                    onClick={() => setSelectedLog(log)}
                  >
                    View
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {logs.length === 0 && !loading && (
          <div className="no-results">No activity logs found</div>
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

      {/* Detail Modal */}
      {selectedLog && (
        <div className="modal-overlay" onClick={() => setSelectedLog(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Activity Log Details</h3>
              <button className="btn-close" onClick={() => setSelectedLog(null)}>
                ✕
              </button>
            </div>
            <div className="modal-body">
              <div className="detail-row">
                <label>Status:</label>
                <span className={`status-badge ${selectedLog.success ? 'success' : 'failed'}`}>
                  {selectedLog.success ? '✓ Success' : '✗ Failed'}
                </span>
              </div>
              <div className="detail-row">
                <label>Category:</label>
                <span>{getCategoryIcon(selectedLog.actionCategory)} {selectedLog.actionCategory}</span>
              </div>
              <div className="detail-row">
                <label>Action Type:</label>
                <span>{selectedLog.actionType}</span>
              </div>
              <div className="detail-row">
                <label>User:</label>
                <span>{selectedLog.userEmail}</span>
              </div>
              <div className="detail-row">
                <label>Entity Type:</label>
                <span>{selectedLog.entityType || '-'}</span>
              </div>
              <div className="detail-row">
                <label>Entity ID:</label>
                <span>{selectedLog.entityId || '-'}</span>
              </div>
              <div className="detail-row">
                <label>Timestamp:</label>
                <span>{formatDate(selectedLog.createdAt)}</span>
              </div>
              <div className="detail-row">
                <label>Summary:</label>
                <span>{selectedLog.changeSummary}</span>
              </div>
              {!selectedLog.success && selectedLog.errorMessage && (
                <div className="detail-row error">
                  <label>Error:</label>
                  <span>{selectedLog.errorMessage}</span>
                </div>
              )}
              {selectedLog.previousValue && (
                <div className="detail-row">
                  <label>Previous Value:</label>
                  <pre className="json-display">{JSON.stringify(selectedLog.previousValue, null, 2)}</pre>
                </div>
              )}
              {selectedLog.newValue && (
                <div className="detail-row">
                  <label>New Value:</label>
                  <pre className="json-display">{JSON.stringify(selectedLog.newValue, null, 2)}</pre>
                </div>
              )}
              {selectedLog.metadata && Object.keys(selectedLog.metadata).length > 0 && (
                <div className="detail-row">
                  <label>Metadata:</label>
                  <pre className="json-display">{JSON.stringify(selectedLog.metadata, null, 2)}</pre>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
