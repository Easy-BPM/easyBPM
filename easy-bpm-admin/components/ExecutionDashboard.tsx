import React, { useEffect, useState } from 'react';
import { BarChart3, AlertCircle, Activity, TrendingUp, Zap, RefreshCw, ArrowRight } from 'lucide-react';
import { adminService } from '../services/adminService';
import { ExecutionMetricsDto } from '../types';

interface Props {
  onNavigateToInstances?: () => void;
}

export const ExecutionDashboard: React.FC<Props> = ({ onNavigateToInstances }) => {
  const [metrics, setMetrics] = useState<ExecutionMetricsDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const fetchMetrics = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await adminService.getExecutionMetrics();
      setMetrics(data);
      setLastUpdated(new Date());
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to fetch metrics';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  // Fetch metrics on mount
  useEffect(() => {
    fetchMetrics();

    // Set up auto-refresh every 30 seconds
    const interval = setInterval(fetchMetrics, 30000);

    return () => clearInterval(interval);
  }, []);

  if (loading && !metrics) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4" />
          <p className="text-slate-600">Loading execution metrics...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-6 text-center">
        <AlertCircle className="h-12 w-12 text-red-600 mx-auto mb-4" />
        <p className="text-red-800 font-semibold">{error}</p>
        <button
          onClick={fetchMetrics}
          className="mt-4 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
        >
          Retry
        </button>
      </div>
    );
  }

  if (!metrics) return null;

  // Metric cards configuration
  const cards = [
    {
      label: 'Total Instances',
      value: metrics.total,
      icon: <BarChart3 className="w-6 h-6" />,
      bgColor: 'bg-blue-50',
      borderColor: 'border-blue-200',
      textColor: 'text-blue-700',
      iconColor: 'text-blue-600'
    },
    {
      label: 'Running',
      value: metrics.running,
      icon: <Activity className="w-6 h-6" />,
      bgColor: 'bg-green-50',
      borderColor: 'border-green-200',
      textColor: 'text-green-700',
      iconColor: 'text-green-600'
    },
    {
      label: 'Completed',
      value: metrics.completed,
      icon: <TrendingUp className="w-6 h-6" />,
      bgColor: 'bg-emerald-50',
      borderColor: 'border-emerald-200',
      textColor: 'text-emerald-700',
      iconColor: 'text-emerald-600'
    },
    {
      label: 'Failed',
      value: metrics.failed,
      icon: <AlertCircle className="w-6 h-6" />,
      bgColor: 'bg-red-50',
      borderColor: 'border-red-200',
      textColor: 'text-red-700',
      iconColor: 'text-red-600'
    },
    {
      label: 'Incidents',
      value: metrics.incidents,
      icon: <Zap className="w-6 h-6" />,
      bgColor: 'bg-orange-50',
      borderColor: 'border-orange-200',
      textColor: 'text-orange-700',
      iconColor: 'text-orange-600'
    }
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-900">Execution Dashboard</h1>
          <p className="text-slate-600 mt-1">Real-time process execution overview</p>
        </div>
        <button
          onClick={fetchMetrics}
          disabled={loading}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {/* Last Updated */}
      {lastUpdated && (
        <p className="text-sm text-slate-500">
          Last updated: {lastUpdated.toLocaleTimeString()}
        </p>
      )}

      {/* Metric Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
        {cards.map((card, idx) => (
          <div
            key={idx}
            className={`${card.bgColor} border-2 ${card.borderColor} rounded-lg p-6 shadow-sm hover:shadow-md transition-shadow`}
          >
            <div className="flex items-start justify-between mb-4">
              <div className={card.iconColor}>{card.icon}</div>
            </div>
            <div className={`text-3xl font-bold ${card.textColor} mb-1`}>
              {card.value.toLocaleString()}
            </div>
            <div className={`text-sm ${card.textColor} font-medium`}>{card.label}</div>
          </div>
        ))}
      </div>

      {/* Quick Navigation Card */}
      <div className="bg-gradient-to-r from-purple-600 to-blue-600 rounded-lg p-8 text-white shadow-lg">
        <div className="flex items-start justify-between">
          <div>
            <h2 className="text-2xl font-bold mb-2">Instance Search</h2>
            <p className="text-purple-100 mb-4">
              View detailed instance information, filter by status, process, and more
            </p>
            <button
              onClick={onNavigateToInstances}
              className="flex items-center gap-2 px-6 py-3 bg-white text-purple-600 rounded-lg font-semibold hover:bg-purple-50 transition-colors"
            >
              Go to Instance Search
              <ArrowRight className="w-4 h-4" />
            </button>
          </div>
          <Activity className="w-16 h-16 text-white/30" />
        </div>
      </div>

      {/* Info Box */}
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 text-sm text-blue-800">
        <p className="font-semibold mb-2">💡 Dashboard Overview</p>
        <p>
          This dashboard provides a high-level view of your process execution metrics. Use the "Instance Search"
          view to filter and manage individual process instances.
        </p>
      </div>
    </div>
  );
};
