import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { ActivityFeedResponseDto, ActivityType } from '../types';
import { AlertCircle, CheckCircle, Play, Square, AlertTriangle, RefreshCw, ChevronRight } from 'lucide-react';

export const ActivityFeedView: React.FC = () => {
  const [feed, setFeed] = useState<ActivityFeedResponseDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const fetchActivityFeed = async (pageNum: number = 0) => {
    try {
      setLoading(true);
      setError(null);
      const data = await adminService.getActivityFeed(pageNum, 50);
      setFeed(data);
      setPage(pageNum);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load activity feed');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchActivityFeed(0);
  }, []);

  const getActivityIcon = (type: ActivityType) => {
    switch (type) {
      case ActivityType.INSTANCE_CREATED:
        return <Play className="w-4 h-4 text-blue-600" />;
      case ActivityType.INSTANCE_COMPLETED:
        return <CheckCircle className="w-4 h-4 text-green-600" />;
      case ActivityType.INSTANCE_FAILED:
      case ActivityType.INCIDENT_CREATED:
        return <AlertCircle className="w-4 h-4 text-red-600" />;
      case ActivityType.INSTANCE_SUSPENDED:
        return <AlertTriangle className="w-4 h-4 text-yellow-600" />;
      case ActivityType.TASK_CREATED:
        return <Square className="w-4 h-4 text-purple-600" />;
      case ActivityType.TASK_COMPLETED:
        return <CheckCircle className="w-4 h-4 text-green-600" />;
      default:
        return <Square className="w-4 h-4 text-gray-600" />;
    }
  };

  const getActivityBgColor = (severity: string) => {
    switch (severity) {
      case 'ERROR':
        return 'bg-red-50 border-l-4 border-red-600';
      case 'WARNING':
        return 'bg-yellow-50 border-l-4 border-yellow-600';
      default:
        return 'bg-gray-50 border-l-4 border-blue-600';
    }
  };

  const formatTime = (timestamp: string) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffSecs = Math.floor(diffMs / 1000);
    const diffMins = Math.floor(diffSecs / 60);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffSecs < 60) return `${diffSecs}s ago`;
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString();
  };

  if (loading && !feed) {
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
            <h3 className="font-semibold text-red-900">Error Loading Activity Feed</h3>
            <p className="text-red-700 text-sm">{error}</p>
            <button
              onClick={() => fetchActivityFeed(page)}
              className="mt-2 px-3 py-1 bg-red-600 hover:bg-red-700 text-white text-sm rounded transition-colors"
            >
              Retry
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (!feed || feed.items.length === 0) {
    return (
      <div className="bg-gray-50 p-8 rounded-lg border border-gray-200 text-center">
        <p className="text-gray-600 font-medium">No activity recorded yet</p>
        <p className="text-gray-500 text-sm">Activity will appear here as you execute processes</p>
      </div>
    );
  }

  return (
    <div className="bg-white border border-gray-200 rounded-lg p-6">
      <div className="flex justify-between items-center mb-6">
        <h3 className="text-lg font-semibold text-gray-900">Activity Feed</h3>
        <button
          onClick={() => fetchActivityFeed(0)}
          disabled={loading}
          className="p-2 hover:bg-gray-100 rounded-lg transition-colors disabled:opacity-50"
        >
          <RefreshCw className="w-5 h-5 text-gray-600" />
        </button>
      </div>

      <div className="space-y-3">
        {feed.items.map((item) => (
          <div key={item.id} className={`p-4 rounded-lg ${getActivityBgColor(item.severity)}`}>
            <div className="flex items-start justify-between">
              <div className="flex gap-3 flex-1">
                {getActivityIcon(item.type)}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="font-semibold text-gray-900 text-sm">{item.processName}</p>
                    <span className="text-xs bg-gray-200 text-gray-700 px-2 py-0.5 rounded">
                      #{item.instanceId}
                    </span>
                  </div>
                  <p className="text-gray-700 text-sm mt-1">{item.description}</p>
                  {item.nodeName && (
                    <p className="text-xs text-gray-600 mt-1">
                      Node: <span className="font-mono">{item.nodeName}</span>
                    </p>
                  )}
                  {item.metadata && Object.keys(item.metadata).length > 0 && (
                    <div className="text-xs text-gray-600 mt-2 space-y-1">
                      {Object.entries(item.metadata).map(([key, value]) => (
                        <div key={key}>
                          <span className="font-semibold">{key}:</span> {String(value)}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
              <div className="text-right ml-4 flex-shrink-0">
                <p className="text-xs text-gray-600 font-medium">{formatTime(item.timestamp)}</p>
                <p className="text-xs text-gray-500 mt-1">{new Date(item.timestamp).toLocaleTimeString()}</p>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Pagination */}
      {feed.totalCount > 50 && (
        <div className="mt-6 flex justify-between items-center">
          <p className="text-sm text-gray-600">
            Showing {page * 50 + 1}-{Math.min((page + 1) * 50, feed.totalCount)} of {feed.totalCount} activities
          </p>
          <div className="flex gap-2">
            <button
              onClick={() => fetchActivityFeed(page - 1)}
              disabled={page === 0 || loading}
              className="px-3 py-1 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed text-sm font-medium"
            >
              Previous
            </button>
            <button
              onClick={() => fetchActivityFeed(page + 1)}
              disabled={!feed.hasMore || loading}
              className="px-3 py-1 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed text-sm font-medium flex items-center gap-1"
            >
              Next
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      <div className="mt-4 pt-4 border-t border-gray-200">
        <p className="text-xs text-gray-500">Data refreshed at {feed.generatedAt}</p>
      </div>
    </div>
  );
};
