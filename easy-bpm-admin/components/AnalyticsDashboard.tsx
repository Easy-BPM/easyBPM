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
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            <BarChart3 className="w-8 h-8 text-blue-600" />
            Analytics Dashboard
          </h2>
          <p className="text-gray-600 text-sm mt-1">Process execution analytics and operational insights</p>
        </div>
        <div className="flex gap-2">
          {['24h', '7d', '30d'].map((p) => (
            <button
              key={p}
              onClick={() => handlePeriodChange(p)}
              disabled={loading}
              className={`px-4 py-2 rounded-lg font-medium transition-colors ${
                period === p
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              } disabled:opacity-50`}
            >
              {p}
            </button>
          ))}
          <button
            onClick={() => fetchSummary(period)}
            disabled={loading}
            className="p-2 hover:bg-gray-100 rounded-lg transition-colors disabled:opacity-50"
          >
            <RefreshCw className={`w-5 h-5 text-gray-600 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* Summary Cards */}
      {summary && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Total Processes */}
          <div className="bg-gradient-to-br from-blue-50 to-blue-100 rounded-lg p-5 border border-blue-200">
            <p className="text-sm font-medium text-gray-600 mb-1">Total Processes</p>
            <p className="text-3xl font-bold text-blue-600">{summary.totalProcesses}</p>
          </div>

          {/* Total Instances */}
          <div className="bg-gradient-to-br from-purple-50 to-purple-100 rounded-lg p-5 border border-purple-200">
            <p className="text-sm font-medium text-gray-600 mb-1">Total Instances</p>
            <p className="text-3xl font-bold text-purple-600">{summary.totalInstances}</p>
          </div>

          {/* Success Rate */}
          <div className="bg-gradient-to-br from-green-50 to-green-100 rounded-lg p-5 border border-green-200">
            <p className="text-sm font-medium text-gray-600 mb-1">Success Rate</p>
            <p className="text-3xl font-bold text-green-600">{summary.successRate.toFixed(1)}%</p>
          </div>

          {/* Incidents */}
          <div className="bg-gradient-to-br from-red-50 to-red-100 rounded-lg p-5 border border-red-200">
            <p className="text-sm font-medium text-gray-600 mb-1">Incidents</p>
            <p className="text-3xl font-bold text-red-600">{summary.incidentsCount}</p>
          </div>
        </div>
      )}

      {/* Detailed Stats Row */}
      {summary && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {/* Completed */}
          <div className="bg-white border border-gray-200 rounded-lg p-4">
            <p className="text-sm text-gray-600 font-medium">Completed</p>
            <p className="text-2xl font-bold text-green-600">{summary.completedInstances}</p>
            <p className="text-xs text-gray-500 mt-2">Successful completions</p>
          </div>

          {/* Failed */}
          <div className="bg-white border border-gray-200 rounded-lg p-4">
            <p className="text-sm text-gray-600 font-medium">Failed</p>
            <p className="text-2xl font-bold text-red-600">{summary.failedInstances}</p>
            <p className="text-xs text-gray-500 mt-2">Failed executions</p>
          </div>

          {/* Avg Execution Time */}
          <div className="bg-white border border-gray-200 rounded-lg p-4">
            <p className="text-sm text-gray-600 font-medium">Avg Execution Time</p>
            <p className="text-2xl font-bold text-blue-600">{(summary.averageExecutionTimeMs / 1000).toFixed(1)}s</p>
            <p className="text-xs text-gray-500 mt-2">Average duration</p>
          </div>
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
