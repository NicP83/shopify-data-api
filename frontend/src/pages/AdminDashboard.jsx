import { useState, useEffect } from 'react';
import { activityApi, configApi, promptTestingApi } from '../services/adminApi';
import ActivityLogViewer from '../components/admin/ActivityLogViewer';
import ConfigurationManager from '../components/admin/ConfigurationManager';
import PromptTestingInterface from '../components/admin/PromptTestingInterface';
import StatisticsDashboard from '../components/admin/StatisticsDashboard';
import './AdminDashboard.css';

/**
 * Admin Dashboard - Phase 4
 * Comprehensive admin interface for managing AI search system
 */
export default function AdminDashboard() {
  const [selectedShop, setSelectedShop] = useState('hearnshobbies.myshopify.com');
  const [activeTab, setActiveTab] = useState('overview');
  const [userEmail] = useState('admin@hearnshobbies.com');
  const [loading, setLoading] = useState(false);

  // Available shops (in production, fetch from API)
  const shops = [
    { domain: 'hearnshobbies.myshopify.com', name: "Hearn's Hobbies" },
  ];

  const tabs = [
    { id: 'overview', label: 'Overview', icon: '📊' },
    { id: 'activity', label: 'Activity Log', icon: '📝' },
    { id: 'config', label: 'Configuration', icon: '⚙️' },
    { id: 'testing', label: 'Prompt Testing', icon: '🧪' },
    { id: 'analytics', label: 'Analytics', icon: '📈' },
  ];

  return (
    <div className="admin-dashboard">
      {/* Header */}
      <header className="admin-header">
        <div className="header-content">
          <h1>🎛️ Admin Dashboard</h1>
          <div className="header-actions">
            <select
              className="shop-selector"
              value={selectedShop}
              onChange={(e) => setSelectedShop(e.target.value)}
            >
              {shops.map((shop) => (
                <option key={shop.domain} value={shop.domain}>
                  {shop.name}
                </option>
              ))}
            </select>
            <div className="user-info">
              <span className="user-email">{userEmail}</span>
            </div>
          </div>
        </div>
      </header>

      {/* Navigation Tabs */}
      <nav className="admin-nav">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            className={`nav-tab ${activeTab === tab.id ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.id)}
          >
            <span className="tab-icon">{tab.icon}</span>
            <span className="tab-label">{tab.label}</span>
          </button>
        ))}
      </nav>

      {/* Main Content */}
      <main className="admin-content">
        {activeTab === 'overview' && (
          <StatisticsDashboard shop={selectedShop} />
        )}

        {activeTab === 'activity' && (
          <ActivityLogViewer shop={selectedShop} userEmail={userEmail} />
        )}

        {activeTab === 'config' && (
          <ConfigurationManager shop={selectedShop} userEmail={userEmail} />
        )}

        {activeTab === 'testing' && (
          <PromptTestingInterface shop={selectedShop} userEmail={userEmail} />
        )}

        {activeTab === 'analytics' && (
          <div className="analytics-placeholder">
            <h2>📈 Advanced Analytics</h2>
            <p>Detailed performance metrics and trends coming soon...</p>
          </div>
        )}
      </main>
    </div>
  );
}
