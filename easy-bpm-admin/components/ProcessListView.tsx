import React, { useEffect, useState } from 'react';
import { ProcessListItemDto, ProcessListResponseDto } from '../types';
import { adminService } from '../services/adminService';
import {
  BarChart3,
  AlertCircle,
  TrendingUp,
  Zap,
  RefreshCw,
  ChevronRight
} from 'lucide-react';

export const ProcessListView: React.FC = () => {
  const [processes, setProcesses] = useState<ProcessListItemDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(20);
  const [sortBy, setSortBy] = useState('lastExecutedAt');

  const fetchProcesses = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await adminService.getProcessList(page, pageSize, sortBy);
      setProcesses(response.content);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load processes');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProcesses();
  }, [page, sortBy]);

  const formatTime = (ms: number): string => {
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    return `${(ms / 60000).toFixed(1)}m`;
  };

  const getStatusColor = (incidents: number, total: number): string => {
    if (incidents === 0) return 'text-emerald-600';
    if (incidents / total > 0.1) return 'text-red-600';
    return 'text-yellow-600';
  };

  if (loading && processes.length === 0) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="text-center">
          <Zap className="mx-auto h-12 w-12 text-blue-500 mb-4" />
          <p className="text-gray-600">Loading processes...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-4">
        <div className="flex items-center gap-3">
          <AlertCircle className="h-5 w-5 text-red-600" />
          <div>
            <p className="font-medium text-red-900">{error}</p>
            <button
              onClick={fetchProcesses}
              className="mt-2 text-sm text-red-700 hover:text-red-900 underline"
            >
              Try again
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Process Catalog</h1>
          <p className="mt-1 text-gray-600">All deployed workflow definitions</p>
        </div>
        <button
          onClick={fetchProcesses}
          disabled={loading}
          className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      <div className="mb-4 flex gap-2">
        <button
          onClick={() => setSortBy('lastExecutedAt')}
          className={`px-4 py-2 rounded-lg ${
            sortBy === 'lastExecutedAt'
              ? 'bg-blue-600 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
        >
          Recent
        </button>
        <button
          onClick={() => setSortBy('totalInstances')}
          className={`px-4 py-2 rounded-lg ${
            sortBy === 'totalInstances'
              ? 'bg-blue-600 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
        >
          Most Used
        </button>
        <button
          onClick={() => setSortBy('successRate')}
          className={`px-4 py-2 rounded-lg ${
            sortBy === 'successRate'
              ? 'bg-blue-600 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
        >
          Success Rate
        </button>
      </div>

      <div className="space-y-4">
        {processes.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-gray-50 p-8 text-center">
            <BarChart3 className="mx-auto h-12 w-12 text-gray-400 mb-4" />
            <p className="text-gray-600">No processes deployed yet</p>
          </div>
        ) : (
          processes.map((process) => (
            <div
              key={process.processId}
              className="rounded-lg border border-gray-200 bg-white p-6 hover:border-blue-300 hover:shadow-md transition-all cursor-pointer"
            >
              <div className="flex items-start justify-between mb-4">
                <div className="flex-1">
                  <h3 className="text-lg font-semibold text-gray-900">
                    {process.processName}
                  </h3>
                  <p className="text-sm text-gray-600">{process.processId} (v{process.version})</p>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`text-2xl font-bold ${getStatusColor(process.incidentCount, process.totalInstances)}`}>
                    {process.incidentCount}
                  </span>
                  <span className="text-sm text-gray-600">issues</span>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4 sm:grid-cols-5 mb-4">
                <div>
                  <p className="text-sm text-gray-600">Total</p>
                  <p className="text-2xl font-bold text-gray-900">{process.totalInstances}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-600">Running</p>
                  <p className="text-2xl font-bold text-green-600">{process.runningInstances}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-600">Success</p>
                  <p className="text-2xl font-bold text-emerald-600">{process.successRate}%</p>
                </div>
                <div>
                  <p className="text-sm text-gray-600">Avg Time</p>
                  <p className="text-2xl font-bold text-blue-600">{formatTime(process.avgExecutionTimeMs)}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-600">Last Run</p>
                  <p className="text-sm text-gray-900 font-semibold">
                    {process.lastExecutedAt
                      ? new Date(process.lastExecutedAt).toLocaleDateString()
                      : 'Never'}
                  </p>
                </div>
              </div>

              <div className="flex items-center justify-between pt-4 border-t border-gray-200">
                <div className="flex gap-4 text-sm">
                  <span className="text-red-600 font-medium">{process.failedInstances} failed</span>
                  <span className="text-yellow-600 font-medium">{process.suspendedInstances} suspended</span>
                </div>
                <ChevronRight className="h-5 w-5 text-gray-400" />
              </div>
            </div>
          ))
        )}
      </div>

      {processes.length > 0 && (
        <div className="mt-6 flex items-center justify-between">
          <button
            onClick={() => setPage(Math.max(0, page - 1))}
            disabled={page === 0}
            className="px-4 py-2 rounded-lg bg-gray-100 text-gray-700 hover:bg-gray-200 disabled:opacity-50"
          >
            Previous
          </button>
          <span className="text-sm text-gray-600">
            Page {page + 1}
          </span>
          <button
            onClick={() => setPage(page + 1)}
            className="px-4 py-2 rounded-lg bg-gray-100 text-gray-700 hover:bg-gray-200"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
};
