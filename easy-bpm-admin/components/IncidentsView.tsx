import React, { useEffect, useState } from 'react';
import { IncidentDto, IncidentsResponseDto } from '../types';
import { adminService } from '../services/adminService';
import {
  AlertTriangle,
  AlertCircle,
  Zap,
  RefreshCw,
  ChevronRight,
  Clock,
  AlertOctagon
} from 'lucide-react';

export const IncidentsView: React.FC = () => {
  const [incidents, setIncidents] = useState<IncidentDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(20);
  const [totalIncidents, setTotalIncidents] = useState(0);

  const fetchIncidents = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await adminService.getIncidents(page, pageSize);
      setIncidents(response.content);
      setTotalIncidents(response.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load incidents');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchIncidents();
  }, [page]);

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'FAILED':
        return <AlertOctagon className="h-5 w-5 text-red-600" />;
      case 'SUSPENDED':
        return <AlertTriangle className="h-5 w-5 text-yellow-600" />;
      case 'ERROR':
        return <AlertCircle className="h-5 w-5 text-red-500" />;
      default:
        return <AlertCircle className="h-5 w-5 text-gray-600" />;
    }
  };

  const getStatusColor = (status: string): string => {
    switch (status) {
      case 'FAILED':
        return 'bg-red-50 border-red-200';
      case 'SUSPENDED':
        return 'bg-yellow-50 border-yellow-200';
      case 'ERROR':
        return 'bg-orange-50 border-orange-200';
      default:
        return 'bg-gray-50 border-gray-200';
    }
  };

  const getStatusBadgeColor = (status: string): string => {
    switch (status) {
      case 'FAILED':
        return 'bg-red-100 text-red-800';
      case 'SUSPENDED':
        return 'bg-yellow-100 text-yellow-800';
      case 'ERROR':
        return 'bg-orange-100 text-orange-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const formatTime = (dateString: string): string => {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString();
  };

  if (loading && incidents.length === 0) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="text-center">
          <Zap className="mx-auto h-12 w-12 text-yellow-500 mb-4" />
          <p className="text-gray-600">Loading incidents...</p>
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
              onClick={fetchIncidents}
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
          <h1 className="text-3xl font-bold text-gray-900">Process Incidents</h1>
          <p className="mt-1 text-gray-600">
            {totalIncidents} {totalIncidents === 1 ? 'incident' : 'incidents'} requiring attention
          </p>
        </div>
        <button
          onClick={fetchIncidents}
          disabled={loading}
          className="inline-flex items-center gap-2 rounded-lg bg-orange-600 px-4 py-2 text-white hover:bg-orange-700 disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      <div className="space-y-4">
        {incidents.length === 0 ? (
          <div className="rounded-lg border-2 border-dashed border-green-300 bg-green-50 p-8 text-center">
            <AlertTriangle className="mx-auto h-12 w-12 text-green-600 mb-4" />
            <p className="text-lg font-semibold text-green-900">All systems operational!</p>
            <p className="text-sm text-green-700">No incidents at this time.</p>
          </div>
        ) : (
          incidents.map((incident) => (
            <div
              key={incident.instanceId}
              className={`rounded-lg border-2 p-6 hover:shadow-md transition-all cursor-pointer ${getStatusColor(incident.status)}`}
            >
              <div className="flex items-start justify-between mb-4">
                <div className="flex items-start gap-4 flex-1">
                  <div className="flex-shrink-0 mt-1">
                    {getStatusIcon(incident.status)}
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-2">
                      <h3 className="text-lg font-semibold text-gray-900">
                        {incident.processName}
                      </h3>
                      <span className={`px-2 py-1 rounded text-xs font-semibold ${getStatusBadgeColor(incident.status)}`}>
                        {incident.status}
                      </span>
                    </div>
                    <p className="text-sm text-gray-600">
                      Process: <span className="font-mono text-gray-900">{incident.processId}</span>
                    </p>
                    <p className="text-sm text-gray-600">
                      Instance: <span className="font-mono text-gray-900">#{incident.instanceId}</span>
                    </p>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4 mb-4 sm:grid-cols-4">
                <div>
                  <p className="text-xs text-gray-600 mb-1">Current Node</p>
                  <p className="font-mono text-sm font-semibold text-gray-900">
                    {incident.currentNode || 'Unknown'}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-gray-600 mb-1">Nesting Level</p>
                  <p className="text-lg font-semibold text-gray-900">
                    {incident.nestingLevel}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-gray-600 mb-1">Created</p>
                  <p className="text-sm text-gray-900">
                    {new Date(incident.createdAt).toLocaleDateString()}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-gray-600 mb-1 flex items-center gap-1">
                    <Clock className="h-3 w-3" /> Updated
                  </p>
                  <p className="text-sm font-semibold text-gray-900">
                    {formatTime(incident.updatedAt)}
                  </p>
                </div>
              </div>

              {incident.errorMessage && (
                <div className="bg-white/50 rounded p-3 mb-4 border-l-4 border-red-500">
                  <p className="text-sm text-gray-700">
                    <span className="font-semibold">Error: </span>
                    {incident.errorMessage}
                  </p>
                </div>
              )}

              {incident.parentInstanceId && (
                <div className="text-xs text-gray-600 flex items-center gap-1">
                  <span className="bg-gray-200 rounded px-2 py-1">
                    Subprocess of #{incident.parentInstanceId}
                  </span>
                </div>
              )}

              <div className="flex justify-end pt-4 border-t border-gray-300/50">
                <button className="inline-flex items-center gap-1 text-sm font-medium text-blue-600 hover:text-blue-800">
                  View Details
                  <ChevronRight className="h-4 w-4" />
                </button>
              </div>
            </div>
          ))
        )}
      </div>

      {incidents.length > 0 && (
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
            disabled={page * pageSize + incidents.length >= totalIncidents}
            className="px-4 py-2 rounded-lg bg-gray-100 text-gray-700 hover:bg-gray-200 disabled:opacity-50"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
};
