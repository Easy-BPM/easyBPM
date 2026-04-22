import React from 'react';
import { ArrowUpDown, ArrowUp, ArrowDown, CheckCircle2, XCircle, AlertCircle } from 'lucide-react';

interface CodeTaskExecution {
  executionId: number;
  instanceId: number;
  jarId: number;
  className: string;
  methodName: string;
  status: 'COMPLETED' | 'FAILED' | 'TIMEOUT';
  inputVariables: Record<string, any>;
  outputVariables: Record<string, any>;
  errorMessage?: string;
  executedAt: string;
  executionTimeMs: number;
}

interface Props {
  executions: CodeTaskExecution[];
  sortBy: 'status' | 'executedAt' | 'executionTime';
  sortOrder: 'asc' | 'desc';
  onSort: (column: 'status' | 'executedAt' | 'executionTime') => void;
  onRowClick: (execution: CodeTaskExecution) => void;
  loading?: boolean;
}

const getStatusColor = (status: string) => {
  switch (status) {
    case 'COMPLETED':
      return { bg: 'bg-green-100', text: 'text-green-800', border: 'border-green-300' };
    case 'FAILED':
      return { bg: 'bg-red-100', text: 'text-red-800', border: 'border-red-300' };
    case 'TIMEOUT':
      return { bg: 'bg-orange-100', text: 'text-orange-800', border: 'border-orange-300' };
    default:
      return { bg: 'bg-slate-100', text: 'text-slate-800', border: 'border-slate-300' };
  }
};

const getStatusIcon = (status: string) => {
  switch (status) {
    case 'COMPLETED':
      return <CheckCircle2 size={16} className="text-green-600" />;
    case 'FAILED':
      return <XCircle size={16} className="text-red-600" />;
    case 'TIMEOUT':
      return <AlertCircle size={16} className="text-orange-600" />;
    default:
      return null;
  }
};

const SortButton: React.FC<{
  label: string;
  column: 'status' | 'executedAt' | 'executionTime';
  currentSort: 'status' | 'executedAt' | 'executionTime';
  sortOrder: 'asc' | 'desc';
  onSort: (column: 'status' | 'executedAt' | 'executionTime') => void;
}> = ({ label, column, currentSort, sortOrder, onSort }) => {
  const isActive = currentSort === column;

  return (
    <button
      onClick={() => onSort(column)}
      className="flex items-center gap-2 hover:text-slate-900 transition-colors"
    >
      {label}
      {isActive ? (
        sortOrder === 'asc' ? (
          <ArrowUp size={16} className="text-blue-600" />
        ) : (
          <ArrowDown size={16} className="text-blue-600" />
        )
      ) : (
        <ArrowUpDown size={16} className="text-slate-400 opacity-0 group-hover:opacity-100" />
      )}
    </button>
  );
};

export const CodeTaskExecutionTable: React.FC<Props> = ({
  executions,
  sortBy,
  sortOrder,
  onSort,
  onRowClick,
  loading
}) => {
  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleString('en-US', {
      month: '2-digit',
      day: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  };

  const formatTime = (ms: number) => {
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(2)}s`;
  };

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50">
            <th className="px-6 py-3 text-left text-xs font-semibold text-slate-700">
              Execution ID
            </th>
            <th className="px-6 py-3 text-left text-xs font-semibold text-slate-700">
              Instance
            </th>
            <th className="px-6 py-3 text-left text-xs font-semibold text-slate-700">
              Method
            </th>
            <th className="px-6 py-3 text-left text-xs font-semibold text-slate-700">
              <SortButton
                label="Status"
                column="status"
                currentSort={sortBy}
                sortOrder={sortOrder}
                onSort={onSort}
              />
            </th>
            <th className="px-6 py-3 text-left text-xs font-semibold text-slate-700">
              <SortButton
                label="Executed At"
                column="executedAt"
                currentSort={sortBy}
                sortOrder={sortOrder}
                onSort={onSort}
              />
            </th>
            <th className="px-6 py-3 text-right text-xs font-semibold text-slate-700">
              <SortButton
                label="Time"
                column="executionTime"
                currentSort={sortBy}
                sortOrder={sortOrder}
                onSort={onSort}
              />
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200">
          {executions.map((execution) => {
            const statusColor = getStatusColor(execution.status);
            return (
              <tr
                key={execution.executionId}
                onClick={() => onRowClick(execution)}
                className="hover:bg-slate-50 cursor-pointer transition-colors"
              >
                <td className="px-6 py-4 text-sm">
                  <span className="font-mono text-slate-600">#{execution.executionId}</span>
                </td>
                <td className="px-6 py-4 text-sm">
                  <span className="font-mono text-blue-600">#{execution.instanceId}</span>
                </td>
                <td className="px-6 py-4 text-sm text-slate-700">
                  <div className="font-medium">{execution.methodName}</div>
                  <div className="text-xs text-slate-500">{execution.className}</div>
                </td>
                <td className="px-6 py-4 text-sm">
                  <div className={`inline-flex items-center gap-2 px-3 py-1 rounded-full font-medium ${statusColor.bg} ${statusColor.text}`}>
                    {getStatusIcon(execution.status)}
                    {execution.status}
                  </div>
                </td>
                <td className="px-6 py-4 text-sm text-slate-600">
                  {formatDate(execution.executedAt)}
                </td>
                <td className="px-6 py-4 text-sm text-right">
                  <span className="font-mono font-semibold text-slate-700">
                    {formatTime(execution.executionTimeMs)}
                  </span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};
