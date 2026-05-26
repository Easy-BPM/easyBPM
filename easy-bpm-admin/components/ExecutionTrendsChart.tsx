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
    <div className="bg-gray-50 p-6 sm:p-8 rounded-lg border border-gray-200 text-center">
        <TrendingUp className="w-10 sm:w-12 h-10 sm:h-12 text-gray-300 mx-auto mb-4" aria-hidden="true" />
        <p className="text-gray-600 font-medium text-sm sm:text-base">No trend data available</p>
        <p className="text-gray-500 text-xs sm:text-sm">Create and complete some process instances to see trends</p>
      </div>
    );
  }

  return (
    <article className="bg-white border border-gray-200 rounded-lg p-4 sm:p-6" role="region" aria-label="Execution Time Trends">
      <div className="flex flex-col sm:flex-row sm:justify-between sm:items-center gap-4 sm:gap-0 mb-4 sm:mb-6">
        <h3 className="text-lg font-semibold text-gray-900">Execution Time Trends</h3>
        <button
          onClick={fetchTrends}
          disabled={loading}
          aria-label="Refresh execution trends data"
          className="p-2 hover:bg-gray-100 rounded-lg transition-colors disabled:opacity-50 min-h-[44px] min-w-[44px] focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 self-start sm:self-auto"
        >
          <RefreshCw className={`w-5 h-5 text-gray-600 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
        </button>
      </div>

      {/* Stats Row - Responsive Grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 sm:gap-4 mb-4 sm:mb-6" role="region" aria-label="Execution Statistics">
        <article className="bg-gradient-to-br from-blue-50 to-blue-100 p-3 sm:p-4 rounded-lg">
          <p className="text-xs sm:text-sm text-gray-600 font-medium truncate">Avg Execution</p>
          <p className="text-lg sm:text-2xl font-bold text-blue-600" aria-label={`${(trends.overallAverageMs / 1000).toFixed(1)} seconds average`}>{(trends.overallAverageMs / 1000).toFixed(1)}s</p>
        </article>
        <article className="bg-gradient-to-br from-purple-50 to-purple-100 p-3 sm:p-4 rounded-lg">
          <p className="text-xs sm:text-sm text-gray-600 font-medium truncate">Median</p>
          <p className="text-lg sm:text-2xl font-bold text-purple-600" aria-label={`${(trends.overallMedianMs / 1000).toFixed(1)} seconds median`}>{(trends.overallMedianMs / 1000).toFixed(1)}s</p>
        </article>
        <article className="bg-gradient-to-br from-orange-50 to-orange-100 p-3 sm:p-4 rounded-lg">
          <p className="text-xs sm:text-sm text-gray-600 font-medium truncate">P95</p>
          <p className="text-lg sm:text-2xl font-bold text-orange-600" aria-label={`${(trends.p95Ms / 1000).toFixed(1)} seconds 95th percentile`}>{(trends.p95Ms / 1000).toFixed(1)}s</p>
        </article>
        <article className="bg-gradient-to-br from-red-50 to-red-100 p-3 sm:p-4 rounded-lg">
          <p className="text-xs sm:text-sm text-gray-600 font-medium truncate">P99</p>
          <p className="text-lg sm:text-2xl font-bold text-red-600" aria-label={`${(trends.p99Ms / 1000).toFixed(1)} seconds 99th percentile`}>{(trends.p99Ms / 1000).toFixed(1)}s</p>
        </article>
      </div>

      {/* Trends Table - Mobile responsive */}
      <div className="overflow-x-auto" role="region" aria-label="Execution trends table">
        <table className="min-w-full text-xs sm:text-sm">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50">
              <th className="px-2 sm:px-4 py-2 text-left font-semibold text-gray-700">Time Bucket</th>
              <th className="px-2 sm:px-4 py-2 text-right font-semibold text-gray-700">Avg (ms)</th>
              <th className="px-2 sm:px-4 py-2 text-right font-semibold text-gray-700">Min / Max</th>
              <th className="px-2 sm:px-4 py-2 text-right font-semibold text-gray-700">Count</th>
              <th className="px-2 sm:px-4 py-2 text-right font-semibold text-gray-700">Success / Failed</th>
            </tr>
          </thead>
          <tbody>
            {trends.trends.map((trend, idx) => (
              <tr key={idx} className="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                <td className="px-2 sm:px-4 py-2 sm:py-3 text-gray-700 font-medium">
                  {new Date(trend.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </td>
                <td className="px-2 sm:px-4 py-2 sm:py-3 text-right font-semibold text-blue-600">
                  {trend.averageExecutionTimeMs.toFixed(0)}
                </td>
                <td className="px-2 sm:px-4 py-2 sm:py-3 text-right text-gray-600 text-xs sm:text-sm">
                  {trend.minExecutionTimeMs.toFixed(0)} / {trend.maxExecutionTimeMs.toFixed(0)}
                </td>
                <td className="px-2 sm:px-4 py-2 sm:py-3 text-right text-gray-600">
                  {trend.instanceCount}
                </td>
                <td className="px-2 sm:px-4 py-2 sm:py-3 text-right text-xs sm:text-sm">
                  <span className="text-green-600 font-semibold" aria-label={`${trend.successCount} successful`}>{trend.successCount}</span>
                  <span className="text-gray-400 mx-0.5">/</span>
                  <span className="text-red-600 font-semibold" aria-label={`${trend.failureCount} failed`}>{trend.failureCount}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-3 sm:mt-4 pt-3 sm:pt-4 border-t border-gray-200">
        <p className="text-xs text-gray-500">Period: {trends.period} | Data refreshed at {new Date().toLocaleTimeString()}</p>
      </div>
    </article>
  );
};
