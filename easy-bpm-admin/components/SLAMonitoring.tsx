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
    <article className="bg-white border border-gray-200 rounded-lg p-4 sm:p-6" role="region" aria-label="SLA Monitoring">
      <div className="flex flex-col sm:flex-row sm:justify-between sm:items-center gap-4 sm:gap-0 mb-4 sm:mb-6">
        <h3 className="text-lg font-semibold text-gray-900">SLA Monitoring</h3>
        <button
          onClick={fetchSLAMetrics}
          disabled={loading}
          aria-label="Refresh SLA metrics"
          className="p-2 hover:bg-gray-100 rounded-lg transition-colors disabled:opacity-50 min-h-[44px] min-w-[44px] focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 self-start sm:self-auto"
        >
          <RefreshCw className={`w-5 h-5 text-gray-600 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
        </button>
      </div>

      {/* SLA Percentage Overview - Responsive Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 sm:gap-4 mb-4 sm:mb-6" role="region" aria-label="SLA Status Overview">
        <article className="text-center p-3 sm:p-4 bg-green-50 rounded-lg border border-green-200">
        <article className="text-center p-3 sm:p-4 bg-green-50 rounded-lg border border-green-200">
          <p className="text-xs sm:text-sm text-gray-600 mb-1 font-medium">Met</p>
          <p className="text-2xl sm:text-3xl font-bold text-green-600" aria-label={`${slaMetrics.metricsPercentage.met.toFixed(1)} percent SLA met`}>{slaMetrics.metricsPercentage.met.toFixed(1)}%</p>
          <p className="text-xs text-gray-500 mt-2">{slaMetrics.metInstances} instances</p>
        </article>
          <p className="text-xs sm:text-sm text-gray-600 mb-1 font-medium">At Risk</p>
          <p className="text-2xl sm:text-3xl font-bold text-yellow-600" aria-label={`${slaMetrics.metricsPercentage.atRisk.toFixed(1)} percent SLA at risk`}>{slaMetrics.metricsPercentage.atRisk.toFixed(1)}%</p>
          <p className="text-xs text-gray-500 mt-2">{slaMetrics.atRiskInstances} instances</p>
        </article>
        <article className="text-center p-3 sm:p-4 bg-red-50 rounded-lg border border-red-200">
          <p className="text-xs sm:text-sm text-gray-600 mb-1 font-medium">Violated</p>
          <p className="text-2xl sm:text-3xl font-bold text-red-600" aria-label={`${slaMetrics.metricsPercentage.violated.toFixed(1)} percent SLA violated`}>{slaMetrics.metricsPercentage.violated.toFixed(1)}%</p>
          <p className="text-xs text-gray-500 mt-2">{slaMetrics.violatedInstances} instances</p>
        </article>
      </div>

      {/* Critical Instances */}
      {slaMetrics.criticalInstances.length > 0 && (
        <div role="region" aria-label="Critical Instances List">
          <h4 className="font-semibold text-gray-900 mb-3 sm:mb-4 text-sm sm:text-base">Critical Instances</h4>
          <div className="space-y-2 max-h-96 overflow-y-auto">
            {slaMetrics.criticalInstances.map((metric) => (
              <article key={metric.instanceId} className={`p-3 sm:p-4 rounded-lg border ${getStatusColor(metric.status)}`}>
                <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 sm:gap-4">
                  <div className="flex gap-2 sm:gap-3 flex-1 min-w-0">
                    {getStatusIcon(metric.status)}
                    <div className="flex-1 min-w-0">
                      <p className="font-semibold text-gray-900 text-sm break-words">
                        {metric.processName} #{metric.instanceId}
                      </p>
                      <p className="text-xs text-gray-600 mt-1 break-words">
                        Node: {metric.currentNode || 'Unknown'} | Created: {new Date(metric.createdAt).toLocaleDateString()}, {new Date(metric.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </p>
                    </div>
                  </div>
                  <div className="text-right ml-6 sm:ml-0">
                    <p className="font-semibold text-sm text-gray-900" aria-label={`${(metric.currentDurationMs / 1000).toFixed(1)} seconds`}>
                      {(metric.currentDurationMs / 1000).toFixed(1)}s
                    </p>
                    <p className="text-xs text-gray-500">Target: {(metric.targetDurationMs / 1000).toFixed(0)}s</p>
                    <div className="mt-2 w-24 sm:w-32 bg-gray-200 rounded-full h-1.5">
                      <div
                        className={`h-1.5 rounded-full transition-all ${
                          metric.status === SLAStatusEnum.VIOLATED ? 'bg-red-600' :
                          metric.status === SLAStatusEnum.AT_RISK ? 'bg-yellow-600' : 'bg-green-600'
                        }`}
                        style={{ width: `${Math.min(metric.percentageComplete, 100)}%` }}
                        role="progressbar"
                        aria-valuenow={metric.percentageComplete}
                        aria-valuemin={0}
                        aria-valuemax={100}
                        aria-label={`SLA progress: ${metric.percentageComplete}% complete`}
                      />
                    </div>
                    <p className="text-xs text-gray-500 mt-1">{metric.percentageComplete}% complete</p>
                  </div>
                </div>
              </article>
            ))}
          </div>
        </div>
      )}

      {slaMetrics.criticalInstances.length === 0 && (
        <article className="text-center py-6 sm:py-8 bg-green-50 rounded-lg border border-green-200" role="status" aria-label="All SLAs on track">
          <CheckCircle className="w-10 sm:w-12 h-10 sm:h-12 text-green-600 mx-auto mb-2" aria-hidden="true" />
          <p className="text-green-900 font-medium text-sm sm:text-base">All SLAs are on track!</p>
          <p className="text-green-700 text-xs sm:text-sm">No critical instances require attention</p>
        </article>
      )}
    </article>
  );
};
