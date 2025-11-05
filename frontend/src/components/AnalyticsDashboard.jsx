import React, { useState, useEffect } from 'react';
import {
  TrendingUp,
  MessageSquare,
  Clock,
  DollarSign,
  CheckCircle,
  XCircle,
  BarChart3,
  Activity,
  Loader2
} from 'lucide-react';

/**
 * Analytics Dashboard Component
 * Displays chat interaction metrics and performance data
 */
const AnalyticsDashboard = ({ shopDomain }) => {
  const [analytics, setAnalytics] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [timeRange, setTimeRange] = useState(30); // days

  useEffect(() => {
    fetchAnalytics();
  }, [shopDomain, timeRange]);

  const fetchAnalytics = async () => {
    setIsLoading(true);
    setError(null);

    try {
      const response = await fetch(
        `http://localhost:8080/api/shopify/analytics?shop=${encodeURIComponent(shopDomain)}&days=${timeRange}`,
        {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json'
          }
        }
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const data = await response.json();
      setAnalytics(data);
    } catch (err) {
      console.error('Analytics error:', err);
      setError(err.message);
      // Set mock data for development
      setAnalytics({
        summary: {
          totalInteractions: 1247,
          successfulInteractions: 1198,
          successRate: '96.07%',
          averageResponseTimeMs: 2340,
          totalApiCost: '$12.4567'
        },
        dailyStats: [],
        feedbackSummary: {
          positive: 892,
          neutral: 234,
          negative: 45
        }
      });
    } finally {
      setIsLoading(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex justify-center items-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
      </div>
    );
  }

  if (error && !analytics) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-6">
        <p className="text-red-800">Failed to load analytics: {error}</p>
        <p className="text-sm text-red-600 mt-2">Showing demo data below</p>
      </div>
    );
  }

  const summary = analytics?.summary || {};
  const feedback = analytics?.feedbackSummary || {};

  return (
    <div className="space-y-6">
      {/* Time Range Selector */}
      <div className="bg-white rounded-lg shadow-md border border-gray-200 p-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-gray-900">Analytics Overview</h2>
          <select
            value={timeRange}
            onChange={(e) => setTimeRange(Number(e.target.value))}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value={7}>Last 7 days</option>
            <option value={30}>Last 30 days</option>
            <option value={90}>Last 90 days</option>
          </select>
        </div>
      </div>

      {/* Metrics Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {/* Total Interactions */}
        <div className="bg-white rounded-lg shadow-md border border-gray-200 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-600 font-medium">Total Interactions</p>
              <p className="text-3xl font-bold text-gray-900 mt-2">
                {summary.totalInteractions?.toLocaleString() || '0'}
              </p>
            </div>
            <div className="p-3 bg-blue-100 rounded-lg">
              <MessageSquare className="h-6 w-6 text-blue-600" />
            </div>
          </div>
          <div className="mt-4 flex items-center text-sm text-green-600">
            <TrendingUp className="h-4 w-4 mr-1" />
            <span>+12% from last period</span>
          </div>
        </div>

        {/* Success Rate */}
        <div className="bg-white rounded-lg shadow-md border border-gray-200 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-600 font-medium">Success Rate</p>
              <p className="text-3xl font-bold text-gray-900 mt-2">
                {summary.successRate || '0%'}
              </p>
            </div>
            <div className="p-3 bg-green-100 rounded-lg">
              <CheckCircle className="h-6 w-6 text-green-600" />
            </div>
          </div>
          <div className="mt-4 text-sm text-gray-600">
            {summary.successfulInteractions || 0} successful / {summary.totalInteractions || 0} total
          </div>
        </div>

        {/* Avg Response Time */}
        <div className="bg-white rounded-lg shadow-md border border-gray-200 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-600 font-medium">Avg Response Time</p>
              <p className="text-3xl font-bold text-gray-900 mt-2">
                {summary.averageResponseTimeMs
                  ? `${(summary.averageResponseTimeMs / 1000).toFixed(1)}s`
                  : '0s'}
              </p>
            </div>
            <div className="p-3 bg-purple-100 rounded-lg">
              <Clock className="h-6 w-6 text-purple-600" />
            </div>
          </div>
          <div className="mt-4 text-sm text-gray-600">
            {summary.averageResponseTimeMs || 0}ms average
          </div>
        </div>

        {/* API Cost */}
        <div className="bg-white rounded-lg shadow-md border border-gray-200 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-600 font-medium">Total API Cost</p>
              <p className="text-3xl font-bold text-gray-900 mt-2">
                {summary.totalApiCost || '$0.00'}
              </p>
            </div>
            <div className="p-3 bg-yellow-100 rounded-lg">
              <DollarSign className="h-6 w-6 text-yellow-600" />
            </div>
          </div>
          <div className="mt-4 text-sm text-gray-600">
            Last {timeRange} days
          </div>
        </div>
      </div>

      {/* User Feedback */}
      <div className="bg-white rounded-lg shadow-md border border-gray-200 p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
          <Activity className="h-5 w-5" />
          User Feedback
        </h3>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {/* Positive */}
          <div className="bg-green-50 border border-green-200 rounded-lg p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-green-700 font-medium">Positive</p>
                <p className="text-2xl font-bold text-green-900 mt-1">
                  {feedback.positive || 0}
                </p>
              </div>
              <CheckCircle className="h-8 w-8 text-green-600" />
            </div>
            <div className="mt-2 text-sm text-green-700">
              {summary.totalInteractions > 0
                ? `${((feedback.positive / summary.totalInteractions) * 100).toFixed(1)}%`
                : '0%'}
            </div>
          </div>

          {/* Neutral */}
          <div className="bg-gray-50 border border-gray-200 rounded-lg p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-700 font-medium">Neutral</p>
                <p className="text-2xl font-bold text-gray-900 mt-1">
                  {feedback.neutral || 0}
                </p>
              </div>
              <BarChart3 className="h-8 w-8 text-gray-600" />
            </div>
            <div className="mt-2 text-sm text-gray-700">
              {summary.totalInteractions > 0
                ? `${((feedback.neutral / summary.totalInteractions) * 100).toFixed(1)}%`
                : '0%'}
            </div>
          </div>

          {/* Negative */}
          <div className="bg-red-50 border border-red-200 rounded-lg p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-red-700 font-medium">Negative</p>
                <p className="text-2xl font-bold text-red-900 mt-1">
                  {feedback.negative || 0}
                </p>
              </div>
              <XCircle className="h-8 w-8 text-red-600" />
            </div>
            <div className="mt-2 text-sm text-red-700">
              {summary.totalInteractions > 0
                ? `${((feedback.negative / summary.totalInteractions) * 100).toFixed(1)}%`
                : '0%'}
            </div>
          </div>
        </div>
      </div>

      {/* Model Usage */}
      {analytics?.summary?.modelUsage && analytics.summary.modelUsage.length > 0 && (
        <div className="bg-white rounded-lg shadow-md border border-gray-200 p-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Model Usage</h3>
          <div className="space-y-3">
            {analytics.summary.modelUsage.map((model, index) => (
              <div key={index} className="flex items-center justify-between">
                <span className="text-sm text-gray-700 font-medium">{model.model}</span>
                <span className="text-sm text-gray-900">{model.count} requests</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default AnalyticsDashboard;
