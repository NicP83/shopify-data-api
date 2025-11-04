import { useState, useEffect } from 'react';
import { activityApi, promptTestingApi, formatDate } from '../../services/adminApi';
import './StatisticsDashboard.css';

/**
 * Statistics Dashboard - Overview of all system metrics
 */
export default function StatisticsDashboard({ shop }) {
  const [activityStats, setActivityStats] = useState(null);
  const [testingStats, setTestingStats] = useState(null);
  const [recentActivity, setRecentActivity] = useState([]);
  const [failedActions, setFailedActions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [period, setPeriod] = useState(30);

  useEffect(() => {
    loadStatistics();
  }, [shop, period]);

  async function loadStatistics() {
    setLoading(true);
    setError(null);

    try {
      const [activityRes, testingRes, recentRes, failedRes] = await Promise.all([
        activityApi.getStatistics(shop, period),
        promptTestingApi.getStatistics(shop, period),
        activityApi.getRecentActivity(shop, 7),
        activityApi.getFailedActions(shop),
      ]);

      setActivityStats(activityRes.data.statistics);
      setTestingStats(testingRes.data.statistics);
      setRecentActivity(recentRes.data.logs);
      setFailedActions(failedRes.data.failedActions);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  if (loading) {
    return (
      <div className="loading-spinner">
        <div className="spinner"></div>
      </div>
    );
  }

  if (error) {
    return <div className="error-message">Error loading statistics: {error}</div>;
  }

  return (
    <div className="statistics-dashboard">
      <div className="dashboard-header">
        <h2>📊 System Overview</h2>
        <select
          className="period-selector"
          value={period}
          onChange={(e) => setPeriod(Number(e.target.value))}
        >
          <option value={7}>Last 7 days</option>
          <option value={30}>Last 30 days</option>
          <option value={90}>Last 90 days</option>
        </select>
      </div>

      {/* Key Metrics */}
      <div className="metrics-grid">
        <MetricCard
          title="Total Actions"
          value={
            activityStats?.actionsByCategory
              ? Object.values(activityStats.actionsByCategory).reduce((a, b) => a + b, 0)
              : 0
          }
          icon="📝"
          color="blue"
        />
        <MetricCard
          title="Failed Actions"
          value={activityStats?.failedActions || 0}
          icon="❌"
          color="red"
          alert={activityStats?.failedActions > 0}
        />
        <MetricCard
          title="Prompt Tests"
          value={testingStats?.totalTests || 0}
          icon="🧪"
          color="purple"
        />
        <MetricCard
          title="Test Pass Rate"
          value={testingStats?.passRate ? `${testingStats.passRate.toFixed(1)}%` : 'N/A'}
          icon="✅"
          color="green"
        />
      </div>

      {/* Charts Row */}
      <div className="charts-row">
        {/* Actions by Category */}
        <div className="chart-card">
          <h3>Actions by Category</h3>
          {activityStats?.actionsByCategory && (
            <div className="bar-chart">
              {Object.entries(activityStats.actionsByCategory).map(([category, count]) => (
                <div key={category} className="bar-item">
                  <div className="bar-label">{category}</div>
                  <div className="bar-container">
                    <div
                      className={`bar bar-${category.toLowerCase()}`}
                      style={{
                        width: `${
                          (count /
                            Math.max(...Object.values(activityStats.actionsByCategory))) *
                          100
                        }%`,
                      }}
                    >
                      <span className="bar-value">{count}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Test Modes Distribution */}
        <div className="chart-card">
          <h3>Test Modes Distribution</h3>
          {testingStats?.testsByMode && (
            <div className="pie-chart-legend">
              {Object.entries(testingStats.testsByMode).map(([mode, count]) => (
                <div key={mode} className="legend-item">
                  <span className={`legend-dot dot-${mode.toLowerCase()}`}></span>
                  <span className="legend-label">{mode}</span>
                  <span className="legend-value">{count}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Recent Activity & Failures */}
      <div className="activity-row">
        {/* Recent Activity */}
        <div className="activity-card">
          <h3>🕐 Recent Activity</h3>
          <div className="activity-list">
            {recentActivity.slice(0, 5).map((log) => (
              <div key={log.id} className="activity-item">
                <div className="activity-icon">
                  {log.actionCategory === 'CONFIG' && '⚙️'}
                  {log.actionCategory === 'PROMPT' && '📝'}
                  {log.actionCategory === 'TESTING' && '🧪'}
                  {log.actionCategory === 'ANALYTICS' && '📊'}
                  {log.actionCategory === 'SYSTEM' && '🔧'}
                </div>
                <div className="activity-details">
                  <div className="activity-summary">{log.changeSummary}</div>
                  <div className="activity-meta">
                    {log.userEmail} • {formatDate(log.createdAt)}
                  </div>
                </div>
                <div className={`activity-status status-${log.success}`}>
                  {log.success ? '✓' : '✗'}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Failed Actions */}
        {failedActions.length > 0 && (
          <div className="activity-card failures">
            <h3>⚠️ Recent Failures</h3>
            <div className="activity-list">
              {failedActions.slice(0, 5).map((log) => (
                <div key={log.id} className="activity-item failure">
                  <div className="activity-icon">❌</div>
                  <div className="activity-details">
                    <div className="activity-summary">{log.changeSummary}</div>
                    <div className="activity-error">{log.errorMessage}</div>
                    <div className="activity-meta">
                      {log.userEmail} • {formatDate(log.createdAt)}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Performance Metrics */}
      {activityStats?.avgDurationByCategory && (
        <div className="performance-card">
          <h3>⚡ Average Response Time by Category</h3>
          <div className="performance-grid">
            {Object.entries(activityStats.avgDurationByCategory).map(([category, avgMs]) => (
              <div key={category} className="performance-item">
                <div className="performance-category">{category}</div>
                <div className="performance-time">{avgMs.toFixed(0)}ms</div>
                <div className={`performance-status ${avgMs < 1000 ? 'fast' : avgMs < 3000 ? 'medium' : 'slow'}`}>
                  {avgMs < 1000 ? '🟢 Fast' : avgMs < 3000 ? '🟡 Medium' : '🔴 Slow'}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * Metric Card Component
 */
function MetricCard({ title, value, icon, color, alert }) {
  return (
    <div className={`metric-card metric-${color} ${alert ? 'alert' : ''}`}>
      <div className="metric-icon">{icon}</div>
      <div className="metric-content">
        <div className="metric-value">{value}</div>
        <div className="metric-title">{title}</div>
      </div>
    </div>
  );
}
