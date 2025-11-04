import { useState, useEffect } from 'react';
import { activityApi, formatDate, getCategoryColor } from '../../services/adminApi';

/**
 * Activity Log Viewer - View and search admin activity logs
 * TODO: Complete implementation in Phase 4 Part 2
 */
export default function ActivityLogViewer({ shop, userEmail }) {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadLogs();
  }, [shop]);

  async function loadLogs() {
    try {
      const response = await activityApi.getLogs(shop, 0, 20);
      setLogs(response.data.logs || []);
    } catch (error) {
      console.error('Error loading logs:', error);
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <div>Loading activity logs...</div>;

  return (
    <div className="activity-log-viewer">
      <h2>📝 Activity Log</h2>
      <p>Viewing {logs.length} recent activities</p>
      {/* Full implementation coming in Phase 4 Part 2 */}
    </div>
  );
}
