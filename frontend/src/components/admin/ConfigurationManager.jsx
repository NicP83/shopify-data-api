import { useState, useEffect } from 'react';
import { configApi } from '../../services/adminApi';

/**
 * Configuration Manager - Manage config versions and rollback
 * TODO: Complete implementation in Phase 4 Part 2
 */
export default function ConfigurationManager({ shop, userEmail }) {
  const [configs, setConfigs] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadConfigs();
  }, [shop]);

  async function loadConfigs() {
    try {
      const types = await configApi.getTypes();
      console.log('Config types:', types);
    } catch (error) {
      console.error('Error loading configs:', error);
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <div>Loading configurations...</div>;

  return (
    <div className="configuration-manager">
      <h2>⚙️ Configuration Manager</h2>
      <p>Configuration versioning and rollback interface</p>
      {/* Full implementation coming in Phase 4 Part 2 */}
    </div>
  );
}
