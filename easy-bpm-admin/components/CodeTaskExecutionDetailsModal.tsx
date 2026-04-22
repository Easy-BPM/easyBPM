import React, { useState } from 'react';
import { X, Copy, ChevronDown, ChevronUp } from 'lucide-react';

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
  execution: CodeTaskExecution;
  onClose: () => void;
}

const JSONViewer: React.FC<{ data: any; title: string }> = ({ data, title }) => {
  const [expanded, setExpanded] = useState(true);
  const jsonString = JSON.stringify(data, null, 2);

  const handleCopy = () => {
    navigator.clipboard.writeText(jsonString);
  };

  return (
    <div className="border border-slate-200 rounded-lg overflow-hidden">
      <div
        onClick={() => setExpanded(!expanded)}
        className="bg-slate-50 border-b border-slate-200 px-4 py-3 flex items-center justify-between cursor-pointer hover:bg-slate-100"
      >
        <div className="flex items-center gap-3">
          {expanded ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
          <h4 className="font-semibold text-slate-900">{title}</h4>
        </div>
        <button
          onClick={(e) => {
            e.stopPropagation();
            handleCopy();
          }}
          className="p-1 hover:bg-slate-200 rounded text-slate-600 hover:text-slate-900"
          title="Copy to clipboard"
        >
          <Copy size={16} />
        </button>
      </div>

      {expanded && (
        <div className="bg-slate-900 text-slate-100 font-mono text-xs p-4 overflow-x-auto max-h-64 overflow-y-auto">
          <pre className="whitespace-pre-wrap break-words">{jsonString}</pre>
        </div>
      )}
    </div>
  );
};

export const CodeTaskExecutionDetailsModal: React.FC<Props> = ({ execution, onClose }) => {
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

  const statusColor = getStatusColor(execution.status);
  const hasError = execution.status === 'FAILED' || execution.status === 'TIMEOUT';

  return (
    <>
      {/* Overlay */}
      <div
        className="fixed inset-0 bg-black bg-opacity-50 z-40"
        onClick={onClose}
        onKeyDown={(e) => e.key === 'Escape' && onClose()}
      />

      {/* Modal */}
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-lg shadow-2xl max-w-3xl w-full max-h-[90vh] overflow-y-auto">
          {/* Header */}
          <div className="sticky top-0 border-b border-slate-200 bg-slate-50 px-6 py-4 flex items-center justify-between">
            <div>
              <h2 className="text-xl font-bold text-slate-900">Execution Details</h2>
              <p className="text-sm text-slate-600 mt-1">
                Execution #{execution.executionId} • Instance #{execution.instanceId}
              </p>
            </div>
            <button
              onClick={onClose}
              className="p-2 hover:bg-slate-200 rounded-lg transition-colors"
              aria-label="Close modal"
            >
              <X size={20} />
            </button>
          </div>

          {/* Content */}
          <div className="p-6 space-y-6">
            {/* Summary */}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="text-xs font-semibold text-slate-600 uppercase">Status</label>
                <div className={`inline-flex items-center gap-2 px-3 py-2 mt-2 rounded-full font-medium ${statusColor.bg} ${statusColor.text}`}>
                  {execution.status}
                </div>
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-600 uppercase">Execution Time</label>
                <p className="text-lg font-semibold text-slate-900 mt-2">
                  {formatTime(execution.executionTimeMs)}
                </p>
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-600 uppercase">Executed At</label>
                <p className="text-sm text-slate-700 mt-2">
                  {formatDate(execution.executedAt)}
                </p>
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-600 uppercase">JAR ID</label>
                <p className="text-sm font-mono text-slate-700 mt-2">
                  #{execution.jarId}
                </p>
              </div>
            </div>

            {/* Method Information */}
            <div className="bg-slate-50 rounded-lg p-4 border border-slate-200">
              <h3 className="font-semibold text-slate-900 mb-3">Method Information</h3>
              <div className="space-y-2 font-mono text-sm">
                <div>
                  <span className="text-slate-600">Class: </span>
                  <span className="text-slate-900">{execution.className}</span>
                </div>
                <div>
                  <span className="text-slate-600">Method: </span>
                  <span className="text-slate-900">{execution.methodName}</span>
                </div>
              </div>
            </div>

            {/* Input Variables */}
            <JSONViewer
              data={execution.inputVariables || {}}
              title="Input Variables"
            />

            {/* Output Variables */}
            {execution.status === 'COMPLETED' && Object.keys(execution.outputVariables || {}).length > 0 && (
              <JSONViewer
                data={execution.outputVariables || {}}
                title="Output Variables"
              />
            )}

            {/* Error Message */}
            {hasError && execution.errorMessage && (
              <div className="bg-red-50 border border-red-200 rounded-lg p-4">
                <h3 className="font-semibold text-red-900 mb-2">Error Details</h3>
                <div className="bg-white border border-red-200 rounded p-3 font-mono text-xs text-red-900 max-h-48 overflow-y-auto">
                  <pre className="whitespace-pre-wrap break-words">{execution.errorMessage}</pre>
                </div>
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="border-t border-slate-200 bg-slate-50 px-6 py-3 flex justify-end gap-3">
            <button
              onClick={onClose}
              className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg font-medium transition-colors"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </>
  );
};
