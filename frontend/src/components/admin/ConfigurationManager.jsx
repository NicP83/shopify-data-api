import { useState, useEffect } from 'react';
import { configApi, formatDate } from '../../services/adminApi';
import './ConfigurationManager.css';

/**
 * Configuration Manager - Manage config versions and rollback
 */
export default function ConfigurationManager({ shop, userEmail }) {
  const [configs, setConfigs] = useState([]);
  const [selectedType, setSelectedType] = useState('');
  const [configTypes, setConfigTypes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  // Modals
  const [showSaveModal, setShowSaveModal] = useState(false);
  const [showRollbackModal, setShowRollbackModal] = useState(false);
  const [showCompareModal, setShowCompareModal] = useState(false);
  const [selectedVersion, setSelectedVersion] = useState(null);

  // Save form
  const [newConfigType, setNewConfigType] = useState('SYSTEM_PROMPT');
  const [newConfigSnapshot, setNewConfigSnapshot] = useState('');
  const [changeReason, setChangeReason] = useState('');

  // Rollback form
  const [rollbackReason, setRollbackReason] = useState('');

  // Compare
  const [compareVersion1, setCompareVersion1] = useState(null);
  const [compareVersion2, setCompareVersion2] = useState(null);

  useEffect(() => {
    loadConfigTypes();
  }, []);

  useEffect(() => {
    if (selectedType) {
      loadConfigs();
    }
  }, [shop, selectedType, page]);

  async function loadConfigTypes() {
    try {
      const response = await configApi.getTypes();
      const types = response.data.configTypes || [];
      setConfigTypes(types);
      if (types.length > 0) {
        setSelectedType(types[0]);
      }
    } catch (error) {
      console.error('Error loading config types:', error);
      setError(error.message);
    } finally {
      setLoading(false);
    }
  }

  async function loadConfigs() {
    setLoading(true);
    setError(null);

    try {
      const response = await configApi.getHistory(shop, selectedType, page, 20);
      setConfigs(response.data.versions || []);
      setTotalPages(response.data.totalPages || 0);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleSaveConfig() {
    if (!newConfigSnapshot.trim() || !changeReason.trim()) {
      alert('Please fill in all fields');
      return;
    }

    try {
      const configSnapshot = JSON.parse(newConfigSnapshot);
      await configApi.saveVersion(shop, newConfigType, configSnapshot, userEmail, changeReason);
      setShowSaveModal(false);
      setNewConfigSnapshot('');
      setChangeReason('');
      loadConfigs();
    } catch (err) {
      alert('Error saving config: ' + err.message);
    }
  }

  async function handleActivateVersion(version) {
    if (!confirm(`Activate version ${version.versionNumber}?`)) {
      return;
    }

    try {
      await configApi.activateVersion(shop, selectedType, version.id);
      loadConfigs();
    } catch (err) {
      alert('Error activating version: ' + err.message);
    }
  }

  async function handleRollback() {
    if (!selectedVersion || !rollbackReason.trim()) {
      alert('Please provide a rollback reason');
      return;
    }

    if (!confirm(`Rollback to version ${selectedVersion.versionNumber}?`)) {
      return;
    }

    try {
      await configApi.rollback(shop, selectedType, selectedVersion.id, userEmail, rollbackReason);
      setShowRollbackModal(false);
      setRollbackReason('');
      setSelectedVersion(null);
      loadConfigs();
    } catch (err) {
      alert('Error rolling back: ' + err.message);
    }
  }

  async function handleCompare() {
    if (!compareVersion1 || !compareVersion2) {
      alert('Please select two versions to compare');
      return;
    }

    try {
      const response = await configApi.compareVersions(
        shop,
        selectedType,
        compareVersion1.id,
        compareVersion2.id
      );
      console.log('Comparison:', response.data);
      // Show comparison in modal
      setShowCompareModal(true);
    } catch (err) {
      alert('Error comparing versions: ' + err.message);
    }
  }

  function getChangeTypeColor(changeType) {
    const colors = {
      MANUAL: '#4299e1',
      AUTOMATED: '#9f7aea',
      ROLLBACK: '#f6ad55',
      SCHEDULED: '#48bb78'
    };
    return colors[changeType] || '#a0aec0';
  }

  function getChangeTypeIcon(changeType) {
    const icons = {
      MANUAL: '👤',
      AUTOMATED: '🤖',
      ROLLBACK: '⏮️',
      SCHEDULED: '⏰'
    };
    return icons[changeType] || '📝';
  }

  if (loading && configs.length === 0) {
    return (
      <div className="loading-spinner">
        <div className="spinner"></div>
      </div>
    );
  }

  return (
    <div className="configuration-manager">
      {/* Header */}
      <div className="manager-header">
        <h2>⚙️ Configuration Manager</h2>
        <button className="btn-primary" onClick={() => setShowSaveModal(true)}>
          + Save New Version
        </button>
      </div>

      {/* Config Type Selector */}
      <div className="type-selector-section">
        <label>Configuration Type:</label>
        <select
          value={selectedType}
          onChange={(e) => {
            setSelectedType(e.target.value);
            setPage(0);
          }}
          className="type-selector"
        >
          {configTypes.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
      </div>

      {error && <div className="error-message">Error: {error}</div>}

      {/* Config History Table */}
      <div className="config-table-container">
        <table className="config-table">
          <thead>
            <tr>
              <th>Version</th>
              <th>Status</th>
              <th>Change Type</th>
              <th>Changed By</th>
              <th>Reason</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {configs.map((config) => (
              <tr key={config.id} className={config.isActive ? 'active-row' : ''}>
                <td>
                  <span className="version-number">v{config.versionNumber}</span>
                </td>
                <td>
                  {config.isActive ? (
                    <span className="status-badge active">✓ Active</span>
                  ) : config.canRollback ? (
                    <span className="status-badge inactive">Inactive</span>
                  ) : (
                    <span className="status-badge disabled">Disabled</span>
                  )}
                </td>
                <td>
                  <span
                    className="change-type-badge"
                    style={{ background: getChangeTypeColor(config.changeType) }}
                  >
                    {getChangeTypeIcon(config.changeType)} {config.changeType}
                  </span>
                </td>
                <td className="user-cell">{config.changedBy}</td>
                <td className="reason-cell">{config.changeReason}</td>
                <td className="timestamp-cell">{formatDate(config.createdAt)}</td>
                <td>
                  <div className="action-buttons">
                    <button
                      className="btn-small btn-view"
                      onClick={() => setSelectedVersion(config)}
                    >
                      View
                    </button>
                    {!config.isActive && config.canRollback && (
                      <>
                        <button
                          className="btn-small btn-activate"
                          onClick={() => handleActivateVersion(config)}
                        >
                          Activate
                        </button>
                        <button
                          className="btn-small btn-rollback"
                          onClick={() => {
                            setSelectedVersion(config);
                            setShowRollbackModal(true);
                          }}
                        >
                          Rollback
                        </button>
                      </>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {configs.length === 0 && !loading && (
          <div className="no-results">No configuration history found</div>
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

      {/* Save New Version Modal */}
      {showSaveModal && (
        <div className="modal-overlay" onClick={() => setShowSaveModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Save New Configuration Version</h3>
              <button className="btn-close" onClick={() => setShowSaveModal(false)}>
                ✕
              </button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>Configuration Type:</label>
                <select
                  value={newConfigType}
                  onChange={(e) => setNewConfigType(e.target.value)}
                  className="form-select"
                >
                  {configTypes.map((type) => (
                    <option key={type} value={type}>
                      {type}
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label>Configuration Snapshot (JSON):</label>
                <textarea
                  value={newConfigSnapshot}
                  onChange={(e) => setNewConfigSnapshot(e.target.value)}
                  className="form-textarea"
                  rows={10}
                  placeholder='{"key": "value"}'
                />
              </div>

              <div className="form-group">
                <label>Change Reason:</label>
                <textarea
                  value={changeReason}
                  onChange={(e) => setChangeReason(e.target.value)}
                  className="form-textarea"
                  rows={3}
                  placeholder="Explain what changed and why..."
                />
              </div>

              <div className="modal-actions">
                <button className="btn-cancel" onClick={() => setShowSaveModal(false)}>
                  Cancel
                </button>
                <button className="btn-submit" onClick={handleSaveConfig}>
                  Save Version
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Rollback Modal */}
      {showRollbackModal && selectedVersion && (
        <div className="modal-overlay" onClick={() => setShowRollbackModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Rollback to Version {selectedVersion.versionNumber}</h3>
              <button className="btn-close" onClick={() => setShowRollbackModal(false)}>
                ✕
              </button>
            </div>
            <div className="modal-body">
              <div className="warning-box">
                <strong>⚠️ Warning:</strong> Rolling back will create a new version with the
                previous configuration. The current active version will be deactivated.
              </div>

              <div className="form-group">
                <label>Rollback Reason:</label>
                <textarea
                  value={rollbackReason}
                  onChange={(e) => setRollbackReason(e.target.value)}
                  className="form-textarea"
                  rows={3}
                  placeholder="Explain why you're rolling back..."
                />
              </div>

              <div className="modal-actions">
                <button className="btn-cancel" onClick={() => setShowRollbackModal(false)}>
                  Cancel
                </button>
                <button className="btn-submit btn-danger" onClick={handleRollback}>
                  Confirm Rollback
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* View Details Modal */}
      {selectedVersion && !showRollbackModal && (
        <div className="modal-overlay" onClick={() => setSelectedVersion(null)}>
          <div className="modal-content large" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Version {selectedVersion.versionNumber} Details</h3>
              <button className="btn-close" onClick={() => setSelectedVersion(null)}>
                ✕
              </button>
            </div>
            <div className="modal-body">
              <div className="detail-grid">
                <div className="detail-item">
                  <label>Status:</label>
                  <span>
                    {selectedVersion.isActive ? (
                      <span className="status-badge active">✓ Active</span>
                    ) : (
                      <span className="status-badge inactive">Inactive</span>
                    )}
                  </span>
                </div>
                <div className="detail-item">
                  <label>Change Type:</label>
                  <span
                    className="change-type-badge"
                    style={{ background: getChangeTypeColor(selectedVersion.changeType) }}
                  >
                    {getChangeTypeIcon(selectedVersion.changeType)} {selectedVersion.changeType}
                  </span>
                </div>
                <div className="detail-item">
                  <label>Changed By:</label>
                  <span>{selectedVersion.changedBy}</span>
                </div>
                <div className="detail-item">
                  <label>Created:</label>
                  <span>{formatDate(selectedVersion.createdAt)}</span>
                </div>
              </div>

              <div className="detail-section">
                <label>Change Reason:</label>
                <p>{selectedVersion.changeReason}</p>
              </div>

              <div className="detail-section">
                <label>Configuration Snapshot:</label>
                <pre className="json-display">
                  {JSON.stringify(selectedVersion.configSnapshot, null, 2)}
                </pre>
              </div>

              {selectedVersion.performanceBefore && (
                <div className="detail-section">
                  <label>Performance Before:</label>
                  <pre className="json-display">
                    {JSON.stringify(selectedVersion.performanceBefore, null, 2)}
                  </pre>
                </div>
              )}

              {selectedVersion.performanceAfter && (
                <div className="detail-section">
                  <label>Performance After:</label>
                  <pre className="json-display">
                    {JSON.stringify(selectedVersion.performanceAfter, null, 2)}
                  </pre>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
