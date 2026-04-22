import React, { useMemo } from 'react';
import {
  CheckCircle2,
  XCircle,
  Clock,
  Zap,
  BarChart3,
  AlertCircle
} from 'lucide-react';

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

interface FilterState {
  status?: 'COMPLETED' | 'FAILED' | 'TIMEOUT';
  instanceId?: number;
  jarId?: number;
  className?: string;
  methodName?: string;
}

interface Props {
  executions: CodeTaskExecution[];
  filters?: FilterState;
}

interface MetricCard {
  title: string;
  value: string | number;
  unit?: string;
  icon: React.ReactNode;
  color: 'green' | 'red' | 'orange' | 'blue' | 'purple';
  trend?: number;
}

export const CodeTaskExecutionMetrics: React.FC<Props> = ({ executions, filters }) => {
  const metrics = useMemo(() => {
    if (!executions || executions.length === 0) {
      return {
        totalExecutions: 0,
        successCount: 0,
        failureCount: 0,
        timeoutCount: 0,
        successRate: 0,
        failureRate: 0,
        averageTime: 0,
        minTime: 0,
        maxTime: 0,
        throughput: 0
      };
    }

    const total = executions.length;
    const successful = executions.filter(e => e.status === 'COMPLETED').length;
    const failed = executions.filter(e => e.status === 'FAILED').length;
    const timedout = executions.filter(e => e.status === 'TIMEOUT').length;

    const successRate = total > 0 ? (successful / total) * 100 : 0;
    const failureRate = total > 0 ? (failed / total) * 100 : 0;

    const totalTime = executions.reduce((sum, e) => sum + e.executionTimeMs, 0);
    const averageTime = total > 0 ? Math.round(totalTime / total) : 0;

    const times = executions.map(e => e.executionTimeMs);
    const minTime = times.length > 0 ? Math.min(...times) : 0;
    const maxTime = times.length > 0 ? Math.max(...times) : 0;

    // Calculate throughput (executions per minute)
    // Using the time span of executions if available
    let throughput = 0;
    if (executions.length > 1) {
      const dates = executions.map(e => new Date(e.executedAt).getTime());
      const minDate = Math.min(...dates);
      const maxDate = Math.max(...dates);
      const timeSpanMinutes = (maxDate - minDate) / (1000 * 60);
      if (timeSpanMinutes > 0) {
        throughput = Math.round((executions.length / timeSpanMinutes) * 100) / 100;
      }
    }

    return {
      totalExecutions: total,
      successCount: successful,
      failureCount: failed,
      timeoutCount: timedout,
      successRate: Math.round(successRate * 100) / 100,
      failureRate: Math.round(failureRate * 100) / 100,
      averageTime,
      minTime,
      maxTime,
      throughput
    };
  }, [executions]);

  const cards: MetricCard[] = [
    {
      title: 'Total Executions',
      value: metrics.totalExecutions,
      icon: <BarChart3 size={24} className="text-blue-600" />,
      color: 'blue'
    },
    {
      title: 'Success Rate',
      value: metrics.successRate,
      unit: '%',
      icon: <CheckCircle2 size={24} className="text-green-600" />,
      color: 'green'
    },
    {
      title: 'Failure Rate',
      value: metrics.failureRate,
      unit: '%',
      icon: <XCircle size={24} className="text-red-600" />,
      color: 'red'
    },
    {
      title: 'Average Execution Time',
      value: metrics.averageTime,
      unit: 'ms',
      icon: <Clock size={24} className="text-orange-600" />,
      color: 'orange'
    },
    {
      title: 'Throughput',
      value: metrics.throughput,
      unit: 'exec/min',
      icon: <Zap size={24} className="text-purple-600" />,
      color: 'purple'
    }
  ];

  const getBgColor = (color: string) => {
    switch (color) {
      case 'green':
        return 'bg-green-50';
      case 'red':
        return 'bg-red-50';
      case 'orange':
        return 'bg-orange-50';
      case 'blue':
        return 'bg-blue-50';
      case 'purple':
        return 'bg-purple-50';
      default:
        return 'bg-slate-50';
    }
  };

  const getBorderColor = (color: string) => {
    switch (color) {
      case 'green':
        return 'border-green-200';
      case 'red':
        return 'border-red-200';
      case 'orange':
        return 'border-orange-200';
      case 'blue':
        return 'border-blue-200';
      case 'purple':
        return 'border-purple-200';
      default:
        return 'border-slate-200';
    }
  };

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
      {cards.map((card, index) => (
        <div
          key={index}
          className={`${getBgColor(card.color)} border ${getBorderColor(card.color)} rounded-lg p-4 flex items-start justify-between`}
        >
          <div>
            <p className="text-xs font-semibold text-slate-600 uppercase tracking-wide">
              {card.title}
            </p>
            <div className="mt-3 flex items-baseline gap-1">
              <span className="text-3xl font-bold text-slate-900">
                {typeof card.value === 'number' && card.value % 1 !== 0
                  ? card.value.toFixed(2)
                  : card.value}
              </span>
              {card.unit && (
                <span className="text-sm font-medium text-slate-600">
                  {card.unit}
                </span>
              )}
            </div>
          </div>
          <div className="opacity-75">
            {card.icon}
          </div>
        </div>
      ))}

      {/* Details Row */}
      {executions.length > 0 && (
        <div className="col-span-full grid grid-cols-1 md:grid-cols-3 gap-4 mt-2 text-sm">
          <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
            <p className="text-slate-600 font-medium mb-2">Execution Time Range</p>
            <div className="space-y-1 font-mono text-slate-900">
              <div>Min: <span className="font-semibold">{metrics.minTime}ms</span></div>
              <div>Max: <span className="font-semibold">{metrics.maxTime}ms</span></div>
            </div>
          </div>

          <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
            <p className="text-slate-600 font-medium mb-2">Status Breakdown</p>
            <div className="space-y-1 text-slate-900">
              <div className="flex justify-between">
                <span>✓ Completed:</span>
                <span className="font-semibold text-green-600">{metrics.successCount}</span>
              </div>
              <div className="flex justify-between">
                <span>✗ Failed:</span>
                <span className="font-semibold text-red-600">{metrics.failureCount}</span>
              </div>
              <div className="flex justify-between">
                <span>⏱ Timeout:</span>
                <span className="font-semibold text-orange-600">{metrics.timeoutCount}</span>
              </div>
            </div>
          </div>

          {/* Filtered Info */}
          {filters && Object.values(filters).some(v => v !== undefined) && (
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
              <p className="text-blue-600 font-medium mb-2 flex items-center gap-2">
                <AlertCircle size={16} />
                Metrics Filtered
              </p>
              <p className="text-blue-700 text-xs">
                Showing metrics for {executions.length} executions matching your filters
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
