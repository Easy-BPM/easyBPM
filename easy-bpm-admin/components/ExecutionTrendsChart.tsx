import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { ExecutionTrendsResponseDto } from '../types';
import { TrendingUp, AlertCircle, RefreshCw } from 'lucide-react';

export const ExecutionTrendsChart: React.FC<{ processId?: string }> = ({ processId }) => {
  const [trends, setTrends] = useState<ExecutionTrendsResponseDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchTrends = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await adminService.getExecutionTrendsAnalytics(processId, 60, 24);
      setTrends(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load trends');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTrends();
  }, [processId]);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64 bg-gray-50 rounded-lg">
        <RefreshCw className="w-6 h-6 animate-spin text-blue-600" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-50 p-4 rounded-lg border border-red-200">
        <div className="flex gap-2">
          <AlertCircle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <h3 className="font-semibold text-red-900">Error Loading Trends</h3>
            <p className="text-red-700 text-sm">{error}</p>
            <button
              onClick={fetchTrends}
              className="mt-2 px-3 py-1 bg-red-600 hover:bg-red-700 text-white text-sm rounded transition-colors"
            >
              Retry
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (!trends || trends.trends.length === 0) {
    return (
      <div className="bg-gray-50 p-8 rounded-lg border border-gray-200 text-center">
        <TrendingUp className="w-12 h-12 text-gray-300 mx-auto mb-4" />
        <p className="text-gray-600 font-medium">No trend data available</p>
        <p className="text-gray-500 text-sm">Create and complete some process instances to see trends</p>
      </div>
    );
  }

  return (
    <div className="bg-white border border-gray-200 rounded-lg p-6">
      <div className="flex justify-between items-center mb-6">
        <h3 className="text-lg font-semibold text-gray-900">Execution Time Trends</h3>
        <button
          onClick={fetchTrends}
          disabled={loading}
          className="p-2 hover:bg-gray-100 rounded-lg transition-colors disabled:opacity-50"
        >
          <RefreshCw className="w-5 h-5 text-gray-600" />
        </button>
      </div>

      {/* Stats Row */}
      <div className="grid grid-cols-4 gap-4 mb-6">
        <div className="bg-gradient-to-br from-blue-50 to-blue-100 p-4 rounded-lg">
          <p className="text-sm text-gray-600">Avg Execution</p>
          <p className="text-2xl font-bold text-blue-600">{(trends.overallAverageMs / 1000).toFixed(1)}s</p>
        </div>
        <div className="bg-gradient-to-br from-purple-50 to-purple-100 p-4 rounded-lg">
          <p className="text-sm text-gray-600">Median</p>
          <p className="text-2xl font-bold text-purple-600">{(trends.overallMedianMs / 1000).toFixed(1)}s</p>
        </div>
        <div className="bg-gradient-to-br from-orange-50 to-orange-100 p-4 rounded-lg">
          <p className="text-sm text-gray-600">P95</p>
          <p className="text-2xl font-bold text-orange-600">{(trends.p95Ms / 1000).toFixed(1)}s</p>
        </div>
        <div className="bg-gradient-to-br from-red-50 to-red-100 p-4 rounded-lg">
          <p className="text-sm text-gray-600">P99</p>
          <p className="text-2xl font-bold text-red-600">{(trends.p99Ms / 1000).toFixed(1)}s</p>
        </div>
      </div>

      {/* Trends Table */}
      <div className="overflow-x-auto">
        <table className="min-w-full">
          <thead>
            <tr className="border-b border-gray-200">
              <th className="px-4 py-2 text-left text-sm font-semibold text-gray-700">Time Bucket</th>
              <th className="px-4 py-2 text-right text-sm font-semibold text-gray-700">Avg (ms)</th>
              <th className="px-4 py-2 text-right text-sm font-semibold text-gray-700">Min / Max</th>
              <th className="px-4 py-2 text-right text-sm font-semibold text-gray-700">Count</th>
              <th className="px-4 py-2 text-right text-sm font-semibold text-gray-700">Success / Failed</th>
            </tr>
          </thead>
          <tbody>
            {trends.trends.map((trend, idx) => (
              <tr key={idx} className="border-b border-gray-100 hover:bg-gray-50">
                <td className="px-4 py-3 text-sm text-gray-700">
                  {new Date(trend.timestamp).toLocaleTimeString()}
                </td>
                <td className="px-4 py-3 text-right text-sm font-semibold text-blue-600">
                  {trend.averageExecutionTimeMs.toFixed(0)}
                </td>
                <td className="px-4 py-3 text-right text-sm text-gray-600">
                  {trend.minExecutionTimeMs.toFixed(0)} / {trend.maxExecutionTimeMs.toFixed(0)}
                </td>
                <td className="px-4 py-3 text-right text-sm text-gray-600">
                  {trend.instanceCount}
                </td>
                <td className="px-4 py-3 text-right text-sm">
                  <span className="text-green-600 font-semibold">{trend.successCount}</span>
                  <span className="text-gray-400 mx-1">/</span>
                  <span className="text-red-600 font-semibold">{trend.failureCount}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-4 pt-4 border-t border-gray-200">
        <p className="text-xs text-gray-500">Period: {trends.period} | Data refreshed at {new Date().toLocaleTimeString()}</p>
      </div>
    </div>
  );
};
