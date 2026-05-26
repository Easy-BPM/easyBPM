import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { SLAMetricsResponseDto, SLAStatusEnum } from '../types';
import { AlertCircle, CheckCircle, AlertTriangle, Clock, RefreshCw } from 'lucide-react';

export const SLAMonitoring: React.FC = () => {
  const [slaMetrics, setSLAMetrics] = useState<SLAMetricsResponseDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchSLAMetrics = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await adminService.getSLAMetrics();
      setSLAMetrics(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load SLA metrics');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSLAMetrics();
    const interval = setInterval(fetchSLAMetrics, 30000); // Refresh every 30s
    return () => clearInterval(interval);
  }, []);

  const getStatusIcon = (status: SLAStatusEnum) => {
    switch (status) {
      case SLAStatusEnum.MET:
        return <CheckCircle className="w-5 h-5 text-green-600" />;
      case SLAStatusEnum.AT_RISK:
        return <AlertTriangle className="w-5 h-5 text-yellow-600" />;
      case SLAStatusEnum.VIOLATED:
        return <AlertCircle className="w-5 h-5 text-red-600" />;
      default:
        return <Clock className="w-5 h-5 text-gray-600" />;
    }
  };

  const getStatusColor = (status: SLAStatusEnum) => {
    switch (status) {
      case SLAStatusEnum.MET:
        return 'bg-green-50 border-green-200';
      case SLAStatusEnum.AT_RISK:
        return 'bg-yellow-50 border-yellow-200';
      case SLAStatusEnum.VIOLATED:
        return 'bg-red-50 border-red-200';
      default:
        return 'bg-gray-50 border-gray-200';
    }
  };

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
            <h3 className="font-semibold text-red-900">Error Loading SLA Metrics</h3>
            <p className="text-red-700 text-sm">{error}</p>
            <button
              onClick={fetchSLAMetrics}
              className="mt-2 px-3 py-1 bg-red-600 hover:bg-red-700 text-white text-sm rounded transition-colors"
            >
              Retry
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (!slaMetrics) {
    return <div className="text-center text-gray-500">No SLA data available</div>;
  }

  return (
    <div className="bg-white border border-gray-200 rounded-lg p-6">
      <div className="flex justify-between items-center mb-6">
        <h3 className="text-lg font-semibold text-gray-900">SLA Monitoring</h3>
        <button
          onClick={fetchSLAMetrics}
          disabled={loading}
          className="p-2 hover:bg-gray-100 rounded-lg transition-colors disabled:opacity-50"
        >
          <RefreshCw className="w-5 h-5 text-gray-600" />
        </button>
      </div>

      {/* SLA Percentage Overview */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        <div className="text-center p-4 bg-green-50 rounded-lg border border-green-200">
          <p className="text-sm text-gray-600 mb-1">Met</p>
          <p className="text-3xl font-bold text-green-600">{slaMetrics.metricsPercentage.met.toFixed(1)}%</p>
          <p className="text-xs text-gray-500 mt-2">{slaMetrics.metInstances} instances</p>
        </div>
        <div className="text-center p-4 bg-yellow-50 rounded-lg border border-yellow-200">
          <p className="text-sm text-gray-600 mb-1">At Risk</p>
          <p className="text-3xl font-bold text-yellow-600">{slaMetrics.metricsPercentage.atRisk.toFixed(1)}%</p>
          <p className="text-xs text-gray-500 mt-2">{slaMetrics.atRiskInstances} instances</p>
        </div>
        <div className="text-center p-4 bg-red-50 rounded-lg border border-red-200">
          <p className="text-sm text-gray-600 mb-1">Violated</p>
          <p className="text-3xl font-bold text-red-600">{slaMetrics.metricsPercentage.violated.toFixed(1)}%</p>
          <p className="text-xs text-gray-500 mt-2">{slaMetrics.violatedInstances} instances</p>
        </div>
      </div>

      {/* Critical Instances */}
      {slaMetrics.criticalInstances.length > 0 && (
        <div>
          <h4 className="font-semibold text-gray-900 mb-4">Critical Instances</h4>
          <div className="space-y-2 max-h-64 overflow-y-auto">
            {slaMetrics.criticalInstances.map((metric) => (
              <div key={metric.instanceId} className={`p-3 rounded-lg border ${getStatusColor(metric.status)}`}>
                <div className="flex items-start justify-between">
                  <div className="flex gap-3 flex-1">
                    {getStatusIcon(metric.status)}
                    <div className="flex-1">
                      <p className="font-semibold text-gray-900 text-sm">
                        {metric.processName} #{metric.instanceId}
                      </p>
                      <p className="text-xs text-gray-600 mt-1">
                        Node: {metric.currentNode || 'Unknown'} | Created: {new Date(metric.createdAt).toLocaleString()}
                      </p>
                    </div>
                  </div>
                  <div className="text-right ml-4">
                    <p className="font-semibold text-sm text-gray-900">
                      {(metric.currentDurationMs / 1000).toFixed(1)}s
                    </p>
                    <p className="text-xs text-gray-500">Target: {(metric.targetDurationMs / 1000).toFixed(0)}s</p>
                    <div className="mt-2 w-32 bg-gray-200 rounded-full h-1.5">
                      <div
                        className={`h-1.5 rounded-full ${
                          metric.status === SLAStatusEnum.VIOLATED ? 'bg-red-600' :
                          metric.status === SLAStatusEnum.AT_RISK ? 'bg-yellow-600' : 'bg-green-600'
                        }`}
                        style={{ width: `${Math.min(metric.percentageComplete, 100)}%` }}
                      />
                    </div>
                    <p className="text-xs text-gray-500 mt-1">{metric.percentageComplete}% complete</p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {slaMetrics.criticalInstances.length === 0 && (
        <div className="text-center py-8 bg-green-50 rounded-lg border border-green-200">
          <CheckCircle className="w-12 h-12 text-green-600 mx-auto mb-2" />
          <p className="text-green-900 font-medium">All SLAs are on track!</p>
          <p className="text-green-700 text-sm">No critical instances require attention</p>
        </div>
      )}
    </div>
  );
};
