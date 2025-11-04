import { useState, useEffect } from 'react';
import { promptTestingApi } from '../../services/adminApi';

/**
 * Prompt Testing Interface - Test prompts and track quality
 * TODO: Complete implementation in Phase 4 Part 2
 */
export default function PromptTestingInterface({ shop, userEmail }) {
  const [testResults, setTestResults] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadResults();
  }, [shop]);

  async function loadResults() {
    try {
      const response = await promptTestingApi.getResults(shop, null, 0, 10);
      setTestResults(response.data.testResults || []);
    } catch (error) {
      console.error('Error loading test results:', error);
    } finally {
      setLoading(false);
    }
  }

  if (loading) return <div>Loading prompt tests...</div>;

  return (
    <div className="prompt-testing-interface">
      <h2>🧪 Prompt Testing</h2>
      <p>Test prompts and track quality metrics</p>
      {/* Full implementation coming in Phase 4 Part 2 */}
    </div>
  );
}
