import React from 'react';
import { ArrowUpDown, ArrowUp, ArrowDown, CheckCircle2, XCircle, AlertCircle } from 'lucide-react';
import { statusChipClass } from '../../shared/design-system/classes';

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
      className="flex items-center gap-2 hover:text-white transition-colors"
    >
      {label}
      {isActive ? (
        sortOrder === 'asc' ? (
          <ArrowUp size={16} className="text-[#7c8cff]" />
        ) : (
          <ArrowDown size={16} className="text-[#7c8cff]" />
        )
      ) : (
        <ArrowUpDown size={16} className="text-[#7b869b] opacity-0 group-hover:opacity-100" />
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
    <div className="overflow-x-auto rounded-md border border-[var(--ui-border)] bg-[var(--ui-base-1)]">
      <table className="control-table">
        <thead>
          <tr className="border-b border-[#2d3748] bg-[#11161f]">
            <th className="px-4 py-3 text-left text-xs font-semibold text-[#a8b2c5]">
              Execution ID
            </th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-[#a8b2c5]">
              Instance
            </th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-[#a8b2c5]">
              Method
            </th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-[#a8b2c5]">
              <SortButton
                label="Status"
                column="status"
                currentSort={sortBy}
                sortOrder={sortOrder}
                onSort={onSort}
              />
            </th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-[#a8b2c5]">
              <SortButton
                label="Executed At"
                column="executedAt"
                currentSort={sortBy}
                sortOrder={sortOrder}
                onSort={onSort}
              />
            </th>
            <th className="px-4 py-3 text-right text-xs font-semibold text-[#a8b2c5]">
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
        <tbody className="divide-y divide-[#2d3748]">
          {executions.map((execution) => {
            return (
              <tr
                key={execution.executionId}
                onClick={() => onRowClick(execution)}
                className="hover:bg-[#1c2230] cursor-pointer transition-colors"
              >
                <td className="px-4 py-3 text-sm">
                  <span className="font-mono text-[#a8b2c5]">#{execution.executionId}</span>
                </td>
                <td className="px-4 py-3 text-sm">
                  <span className="font-mono text-[#7c8cff]">#{execution.instanceId}</span>
                </td>
                <td className="px-4 py-3 text-sm text-[#e6eaf2]">
                  <div className="font-medium">{execution.methodName}</div>
                  <div className="text-xs text-[#7b869b]">{execution.className}</div>
                </td>
                <td className="px-4 py-3 text-sm">
                  <div className={statusChipClass(execution.status)}>
                    {getStatusIcon(execution.status)}
                    {execution.status}
                  </div>
                </td>
                <td className="px-4 py-3 text-sm text-[#a8b2c5]">
                  {formatDate(execution.executedAt)}
                </td>
                <td className="px-4 py-3 text-sm text-right">
                  <span className="font-mono font-semibold text-[#e6eaf2]">
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
