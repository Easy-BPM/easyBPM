import React, { useState, useEffect } from 'react';
import {
  AlertCircle,
  CheckCircle2,
  Clock,
  Filter,
  RefreshCw,
  XCircle,
  ChevronLeft,
  ChevronRight,
  ChevronDown
} from 'lucide-react';
import { CodeTaskExecutionTable } from './CodeTaskExecutionTable';
import { CodeTaskExecutionDetailsModal } from './CodeTaskExecutionDetailsModal';
import { CodeTaskExecutionFilterPanel } from './CodeTaskExecutionFilterPanel';
import { CodeTaskExecutionMetrics } from './CodeTaskExecutionMetrics';
import { useCodeTaskExecutions } from '../hooks/useCodeTaskExecutions';

interface FilterState {
  status?: 'COMPLETED' | 'FAILED' | 'TIMEOUT';
  instanceId?: number;
  jarId?: number;
  className?: string;
  methodName?: string;
}

export const CodeTaskExecutionListPage: React.FC = () => {
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [sortBy, setSortBy] = useState<'status' | 'executedAt' | 'executionTime'>('executedAt');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');
  const [filters, setFilters] = useState<FilterState>({});
  const [showFilters, setShowFilters] = useState(false);
  const [selectedExecution, setSelectedExecution] = useState<any>(null);
  const [showDetailsModal, setShowDetailsModal] = useState(false);

  // API Hook with filters and pagination
  const {
    executions,
    totalElements,
    totalPages,
    loading,
    error,
    refetch
  } = useCodeTaskExecutions({
    page: currentPage,
    size: pageSize,
    status: filters.status,
    instanceId: filters.instanceId,
    jarId: filters.jarId,
    className: filters.className,
    methodName: filters.methodName,
    sortBy,
    sortOrder
  });

  // Sync URL query params with filters
  useEffect(() => {
    const params = new URLSearchParams();
    if (filters.status) params.append('status', filters.status);
    if (filters.instanceId) params.append('instanceId', filters.instanceId.toString());
    if (filters.jarId) params.append('jarId', filters.jarId.toString());
    if (filters.className) params.append('className', filters.className);
    if (filters.methodName) params.append('methodName', filters.methodName);
    params.append('page', currentPage.toString());
    params.append('sortBy', sortBy);
    params.append('sortOrder', sortOrder);

    const newUrl = `${window.location.pathname}?${params.toString()}`;
    window.history.replaceState({ filters, page: currentPage }, '', newUrl);
  }, [filters, currentPage, sortBy, sortOrder]);

  const handleFilterChange = (newFilters: FilterState) => {
    setFilters(newFilters);
    setCurrentPage(0); // Reset to first page on filter change
  };

  const handleClearFilters = () => {
    setFilters({});
    setCurrentPage(0);
  };

  const handleRowClick = (execution: any) => {
    setSelectedExecution(execution);
    setShowDetailsModal(true);
  };

  const handleSort = (column: 'status' | 'executedAt' | 'executionTime') => {
    if (sortBy === column) {
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setSortBy(column);
      setSortOrder('desc');
    }
    setCurrentPage(0);
  };

  const handlePageChange = (newPage: number) => {
    if (newPage >= 0 && newPage < totalPages) {
      setCurrentPage(newPage);
      // Scroll to top
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const hasFilters = Object.values(filters).some(v => v !== undefined);

  return (
    <div className="code-task-executions space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-900">Code Task Executions</h1>
          <p className="text-slate-600 mt-1">Monitor and debug Code Task execution history</p>
        </div>
        <div className="flex gap-3">
          <button
            onClick={() => refetch()}
            disabled={loading}
            className="flex items-center gap-2 px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg font-medium disabled:opacity-50"
          >
            <RefreshCw size={18} className={loading ? 'animate-spin' : ''} />
            Refresh
          </button>
          <button
            onClick={() => setShowFilters(!showFilters)}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-colors ${
              showFilters || hasFilters
                ? 'bg-blue-100 text-blue-700 hover:bg-blue-200'
                : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
            }`}
          >
            <Filter size={18} />
            Filters
            {hasFilters && <span className="ml-1 text-xs font-bold">({Object.keys(filters).length})</span>}
          </button>
        </div>
      </div>

      {/* Metrics Cards */}
      <CodeTaskExecutionMetrics executions={executions} filters={filters} />

      {/* Filter Panel (Expandable) */}
      {showFilters && (
        <div className="bg-white rounded-lg border border-slate-200 p-4">
          <CodeTaskExecutionFilterPanel
            filters={filters}
            onFilterChange={handleFilterChange}
            onClearFilters={handleClearFilters}
          />
        </div>
      )}

      {/* Error Display */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-start gap-3">
          <AlertCircle size={20} className="text-red-600 flex-shrink-0 mt-0.5" />
          <div>
            <h3 className="font-semibold text-red-900">Error Loading Executions</h3>
            <p className="text-red-700 text-sm mt-1">{error}</p>
          </div>
        </div>
      )}

      {/* Table */}
      <div className="bg-white rounded-lg border border-slate-200 overflow-hidden shadow-sm">
        {loading && executions.length === 0 ? (
          <div className="flex items-center justify-center h-64">
            <div className="text-center">
              <RefreshCw size={32} className="text-slate-400 animate-spin mx-auto mb-3" />
              <p className="text-slate-600">Loading executions...</p>
            </div>
          </div>
        ) : executions.length === 0 ? (
          <div className="flex items-center justify-center h-64">
            <div className="text-center">
              <AlertCircle size={32} className="text-slate-400 mx-auto mb-3" />
              <p className="text-slate-600">No Code Task executions found</p>
              {hasFilters && (
                <button
                  onClick={handleClearFilters}
                  className="text-blue-600 hover:text-blue-700 text-sm mt-2 font-medium"
                >
                  Clear filters
                </button>
              )}
            </div>
          </div>
        ) : (
          <>
            <CodeTaskExecutionTable
              executions={executions}
              sortBy={sortBy}
              sortOrder={sortOrder}
              onSort={handleSort}
              onRowClick={handleRowClick}
              loading={loading}
            />

            {/* Pagination */}
            <div className="border-t border-slate-200 px-6 py-4 flex items-center justify-between bg-slate-50">
              <div className="text-sm text-slate-600">
                Showing <span className="font-semibold">{currentPage * pageSize + 1}</span> to{' '}
                <span className="font-semibold">
                  {Math.min((currentPage + 1) * pageSize, totalElements)}
                </span>{' '}
                of <span className="font-semibold">{totalElements}</span> executions
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => handlePageChange(currentPage - 1)}
                  disabled={currentPage === 0 || loading}
                  className="p-2 hover:bg-slate-200 disabled:opacity-50 disabled:cursor-not-allowed rounded"
                >
                  <ChevronLeft size={18} />
                </button>

                <div className="flex items-center gap-1">
                  {Array.from({ length: Math.min(5, totalPages) }).map((_, i) => {
                    let pageNum = i;
                    if (totalPages > 5 && currentPage > 2) {
                      pageNum = currentPage - 2 + i;
                    }
                    if (pageNum >= totalPages) return null;

                    return (
                      <button
                        key={pageNum}
                        onClick={() => handlePageChange(pageNum)}
                        className={`w-8 h-8 rounded text-sm font-medium transition-colors ${
                          currentPage === pageNum
                            ? 'bg-blue-600 text-white'
                            : 'hover:bg-slate-200 text-slate-700'
                        }`}
                      >
                        {pageNum + 1}
                      </button>
                    );
                  })}
                </div>

                <button
                  onClick={() => handlePageChange(currentPage + 1)}
                  disabled={currentPage >= totalPages - 1 || loading}
                  className="p-2 hover:bg-slate-200 disabled:opacity-50 disabled:cursor-not-allowed rounded"
                >
                  <ChevronRight size={18} />
                </button>
              </div>
            </div>
          </>
        )}
      </div>

      {/* Details Modal */}
      {showDetailsModal && selectedExecution && (
        <CodeTaskExecutionDetailsModal
          execution={selectedExecution}
          onClose={() => setShowDetailsModal(false)}
        />
      )}
    </div>
  );
};
