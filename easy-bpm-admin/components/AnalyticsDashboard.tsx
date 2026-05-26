import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { AnalyticsSummaryDto } from '../types';
import { BarChart3, RefreshCw, AlertCircle, TrendingUp } from 'lucide-react';
import { ExecutionTrendsChart } from './ExecutionTrendsChart';
import { SLAMonitoring } from './SLAMonitoring';
import { ActivityFeedView } from './ActivityFeedView';

export const AnalyticsDashboard: React.FC = () => {
  const [summary, setSummary] = useState<AnalyticsSummaryDto | null>(null);
  const [period, setPeriod] = useState<string>('24h');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchSummary = async (selectedPeriod: string) => {
    try {
      setLoading(true);
      setError(null);
      const data = await adminService.getAnalyticsSummary(selectedPeriod);
      setSummary(data);
      setPeriod(selectedPeriod);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load analytics summary');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSummary('24h');
  }, []);

  const handlePeriodChange = (newPeriod: string) => {
    fetchSummary(newPeriod);
  };

  if (error && !summary) {
    return (
      <div className="bg-red-50 p-4 rounded-lg border border-red-200">
        <div className="flex gap-2">
          <AlertCircle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <h3 className="font-semibold text-red-900">Error Loading Analytics</h3>
            <p className="text-red-700 text-sm">{error}</p>
            <button
              onClick={() => fetchSummary(period)}
              className="mt-2 px-3 py-1 bg-red-600 hover:bg-red-700 text-white text-sm rounded transition-colors"
            >
              Retry
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6" role="main" aria-label="Analytics Dashboard">
      {/* Header */}
      <div className="flex flex-col lg:flex-row lg:justify-between lg:items-center gap-4 lg:gap-0">
        <div>
          <h2 className="text-2xl lg:text-3xl font-bold text-gray-900 flex items-center gap-2">
            <BarChart3 className="w-6 lg:w-8 h-6 lg:h-8 text-blue-600" aria-hidden="true" />
            Analytics Dashboard
          </h2>
          <p className="text-gray-600 text-sm mt-1">Process execution analytics and operational insights</p>
        </div>
        <div className="flex flex-wrap gap-2" role="group" aria-label="Period Selection">
          {['24h', '7d', '30d'].map((p) => (
            <button
              key={p}
              onClick={() => handlePeriodChange(p)}
              disabled={loading}
              aria-pressed={period === p}
              aria-label={`Show analytics for ${p}`}
              className={`px-4 py-2 rounded-lg font-medium transition-colors min-h-[44px] min-w-[44px] ${
                period === p
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2'
              } disabled:opacity-50`}
            >
              {p}
            </button>
          ))}
          <button
            onClick={() => fetchSummary(period)}
            disabled={loading}
            aria-label="Refresh analytics data"
            className="p-2 hover:bg-gray-100 rounded-lg transition-colors disabled:opacity-50 min-h-[44px] min-w-[44px] focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
          >
            <RefreshCw className={`w-5 h-5 text-gray-600 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
          </button>
        </div>
      </div>

      {/* Summary Cards */}
      {summary && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4" role="region" aria-label="Summary Metrics">
          {/* Total Processes */}
          <article className="bg-gradient-to-br from-blue-50 to-blue-100 rounded-lg p-3 sm:p-5 border border-blue-200">
            <p className="text-xs sm:text-sm font-medium text-gray-600 mb-1">Total Processes</p>
            <p className="text-2xl sm:text-3xl font-bold text-blue-600" aria-label={`${summary.totalProcesses} total processes`}>{summary.totalProcesses}</p>
          </article>

          {/* Total Instances */}
          <article className="bg-gradient-to-br from-purple-50 to-purple-100 rounded-lg p-3 sm:p-5 border border-purple-200">
            <p className="text-xs sm:text-sm font-medium text-gray-600 mb-1">Total Instances</p>
            <p className="text-2xl sm:text-3xl font-bold text-purple-600" aria-label={`${summary.totalInstances} total instances`}>{summary.totalInstances}</p>
          </article>

          {/* Success Rate */}
          <article className="bg-gradient-to-br from-green-50 to-green-100 rounded-lg p-3 sm:p-5 border border-green-200">
            <p className="text-xs sm:text-sm font-medium text-gray-600 mb-1">Success Rate</p>
            <p className="text-2xl sm:text-3xl font-bold text-green-600" aria-label={`${summary.successRate.toFixed(1)} percent success rate`}>{summary.successRate.toFixed(1)}%</p>
          </article>

          {/* Incidents */}
          <article className="bg-gradient-to-br from-red-50 to-red-100 rounded-lg p-3 sm:p-5 border border-red-200">
            <p className="text-xs sm:text-sm font-medium text-gray-600 mb-1">Incidents</p>
            <p className="text-2xl sm:text-3xl font-bold text-red-600" aria-label={`${summary.incidentsCount} incidents`}>{summary.incidentsCount}</p>
          </article>
        </div>
      )}

      {/* Detailed Stats Row */}
      {summary && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 sm:gap-4" role="region" aria-label="Detailed Statistics">
          {/* Completed */}
          <article className="bg-white border border-gray-200 rounded-lg p-3 sm:p-4">
            <p className="text-xs sm:text-sm text-gray-600 font-medium">Completed</p>
            <p className="text-xl sm:text-2xl font-bold text-green-600" aria-label={`${summary.completedInstances} completed instances`}>{summary.completedInstances}</p>
            <p className="text-xs text-gray-500 mt-2">Successful completions</p>
          </article>

          {/* Failed */}
          <article className="bg-white border border-gray-200 rounded-lg p-3 sm:p-4">
            <p className="text-xs sm:text-sm text-gray-600 font-medium">Failed</p>
            <p className="text-xl sm:text-2xl font-bold text-red-600" aria-label={`${summary.failedInstances} failed instances`}>{summary.failedInstances}</p>
            <p className="text-xs text-gray-500 mt-2">Failed executions</p>
          </article>

          {/* Avg Execution Time */}
          <article className="bg-white border border-gray-200 rounded-lg p-3 sm:p-4">
            <p className="text-xs sm:text-sm text-gray-600 font-medium">Avg Execution Time</p>
            <p className="text-xl sm:text-2xl font-bold text-blue-600" aria-label={`${(summary.averageExecutionTimeMs / 1000).toFixed(1)} seconds average execution time`}>{(summary.averageExecutionTimeMs / 1000).toFixed(1)}s</p>
            <p className="text-xs text-gray-500 mt-2">Average duration</p>
          </article>
        </div>
      )}

      {/* Top Failing Processes */}
      {summary && summary.topFailingProcesses.length > 0 && (
        <div className="bg-white border border-gray-200 rounded-lg p-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
            <TrendingUp className="w-5 h-5 text-orange-600" />
            Top Failing Processes
          </h3>
          <div className="space-y-3">
            {summary.topFailingProcesses.map((proc) => (
              <div key={proc.processId} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                <div>
                  <p className="font-semibold text-gray-900">{proc.processName}</p>
                  <p className="text-xs text-gray-600">{proc.totalInstances} total instances</p>
                </div>
                <div className="text-right">
                  <p className="text-lg font-bold text-red-600">{proc.failureRate.toFixed(1)}%</p>
                  <p className="text-xs text-gray-600">{proc.failedInstances} failed</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Charts and Monitoring */}
      <div className="space-y-6">
        <ExecutionTrendsChart />
        <SLAMonitoring />
        <ActivityFeedView />
      </div>
    </div>
  );
};
