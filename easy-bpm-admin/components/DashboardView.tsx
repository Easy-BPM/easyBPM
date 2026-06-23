import React, { useEffect, useMemo, useState } from 'react';
import {
  Activity,
  AlertCircle,
  BarChart3,
  CheckCircle2,
  Clock,
  Download,
  Filter,
  Layers,
  Loader2,
  RefreshCw,
  Search,
  TimerReset,
  TrendingUp,
  Workflow,
  XCircle
} from 'lucide-react';
import { adminService } from '../services/adminService';
import { ProcessDefinition, ProcessInstance } from '../types';

interface DashboardViewProps {
  onNavigate: (view: string) => void;
}

type DateRange = 'today' | '7d' | '30d' | 'all';

const ACTIVE_STATUSES = new Set(['ACTIVE']);
const WAITING_STATUSES = new Set(['WAITING', 'SUSPENDED']);
const CLOSED_STATUSES = new Set(['COMPLETED', 'FAILED', 'CANCELLED']);

export const DashboardView: React.FC<DashboardViewProps> = ({ onNavigate }) => {
  const [instances, setInstances] = useState<ProcessInstance[]>([]);
  const [definitions, setDefinitions] = useState<ProcessDefinition[]>([]);
  const [totalInstances, setTotalInstances] = useState(0);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [processFilter, setProcessFilter] = useState('all');
  const [statusFilter, setStatusFilter] = useState('all');
  const [dateRange, setDateRange] = useState<DateRange>('7d');

  const loadDashboard = async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    setError(null);

    try {
      const [instancePage, definitionPage] = await Promise.all([
        adminService.getProcessInstances(0, 200),
        adminService.getProcessDefinitions(0, 100)
      ]);
      setInstances(instancePage.content);
      setTotalInstances(instancePage.totalElements);
      setDefinitions(definitionPage.content);
    } catch (loadError) {
      console.error(loadError);
      setError('Failed to load dashboard data.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    void loadDashboard();
  }, []);

  const filteredInstances = useMemo(() => {
    const now = new Date();
    const rangeStart = getRangeStart(dateRange, now);

    return instances.filter((instance) => {
      const definitionId = getDefinitionId(instance);
      const matchesProcess = processFilter === 'all' || String(definitionId) === processFilter;
      const matchesStatus = statusFilter === 'all' || instance.status === statusFilter;
      const createdAt = parseDate(instance.createdAt);
      const matchesDate = !rangeStart || (createdAt ? createdAt >= rangeStart : false);
      return matchesProcess && matchesStatus && matchesDate;
    });
  }, [dateRange, instances, processFilter, statusFilter]);

  const analytics = useMemo(() => buildAnalytics(filteredInstances), [filteredInstances]);
  const statusOptions = useMemo(() => Array.from(new Set(instances.map((instance) => instance.status))).sort(), [instances]);

  const exportReport = () => {
    const rows = [
      ['Instance ID', 'Process', 'Status', 'Current Node', 'Created At', 'Updated At'],
      ...filteredInstances.map((instance) => [
        String(instance.id),
        getProcessName(instance),
        instance.status,
        (instance.currentNode ?? []).join(', '),
        instance.createdAt,
        instance.updatedAt
      ])
    ];

    const csv = rows.map((row) => row.map(csvCell).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `easybpm-dashboard-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-slate-400 gap-3">
        <Loader2 className="animate-spin text-blue-500" size={28} />
        <p className="text-sm">Loading operations dashboard...</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-slate-800">BPM Administration Dashboard</h2>
          <p className="text-sm text-slate-500 mt-1">
            Operational cockpit based on the latest {instances.length} of {totalInstances} process instances.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => void loadDashboard(true)}
            disabled={refreshing}
            className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-60"
          >
            {refreshing ? <Loader2 size={15} className="animate-spin" /> : <RefreshCw size={15} />}
            Refresh
          </button>
          <button
            onClick={exportReport}
            className="inline-flex items-center gap-2 rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800"
          >
            <Download size={15} />
            Export Report
          </button>
        </div>
      </header>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {error}
        </div>
      )}

      <section className="grid grid-cols-1 gap-3 rounded-xl border border-slate-200 bg-white p-4 md:grid-cols-4">
        <label className="space-y-1.5">
          <span className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-widest text-slate-400">
            <Filter size={12} /> Process
          </span>
          <select
            value={processFilter}
            onChange={(event) => setProcessFilter(event.target.value)}
            className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
          >
            <option value="all">All processes</option>
            {definitions.map((definition) => (
              <option key={definition.id} value={definition.id}>
                {definition.name} v{definition.version}
              </option>
            ))}
          </select>
        </label>
        <label className="space-y-1.5">
          <span className="text-[11px] font-bold uppercase tracking-widest text-slate-400">Status</span>
          <select
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
            className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
          >
            <option value="all">All statuses</option>
            {statusOptions.map((status) => (
              <option key={status} value={status}>{formatStatus(status)}</option>
            ))}
          </select>
        </label>
        <label className="space-y-1.5">
          <span className="text-[11px] font-bold uppercase tracking-widest text-slate-400">Date range</span>
          <select
            value={dateRange}
            onChange={(event) => setDateRange(event.target.value as DateRange)}
            className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
          >
            <option value="today">Today</option>
            <option value="7d">Last 7 days</option>
            <option value="30d">Last 30 days</option>
            <option value="all">All loaded data</option>
          </select>
        </label>
        <div className="flex items-end">
          <button
            onClick={() => {
              setProcessFilter('all');
              setStatusFilter('all');
              setDateRange('7d');
            }}
            className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100"
          >
            Reset filters
          </button>
        </div>
      </section>

      <section className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-6">
        <KpiCard icon={<Activity size={18} />} title="Active" value={analytics.active} tone="blue" />
        <KpiCard icon={<CheckCircle2 size={18} />} title="Completed" value={analytics.completed} tone="emerald" />
        <KpiCard icon={<XCircle size={18} />} title="Failed" value={analytics.failed} tone="red" />
        <KpiCard icon={<Clock size={18} />} title="Waiting" value={analytics.waiting} tone="amber" />
        <KpiCard icon={<TrendingUp size={18} />} title="Success Rate" value={`${analytics.successRate}%`} tone="emerald" />
        <KpiCard icon={<TimerReset size={18} />} title="Avg Completion" value={analytics.averageCompletionTime} tone="slate" />
      </section>

      <section className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <div className="rounded-xl border border-slate-200 bg-white p-5 xl:col-span-2">
          <div className="mb-5 flex items-center justify-between gap-3">
            <div>
              <h3 className="text-sm font-semibold text-slate-800">Process Volume</h3>
              <p className="text-xs text-slate-500">Started instances by day</p>
            </div>
            <BarChart3 size={18} className="text-blue-500" />
          </div>
          <VolumeChart points={analytics.volumeTrend} />
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <div className="mb-4 flex items-center justify-between">
            <div>
              <h3 className="text-sm font-semibold text-slate-800">Completion vs Failure</h3>
              <p className="text-xs text-slate-500">Closed instance outcome</p>
            </div>
            <span className="rounded-full bg-slate-100 px-2 py-1 text-[11px] font-bold text-slate-500">
              {analytics.closedCount} closed
            </span>
          </div>
          <OutcomeBars completed={analytics.completed} failed={analytics.failed} cancelled={analytics.cancelled} />
        </div>
      </section>

      <section className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <AttentionPanel
          failed={analytics.recentFailed}
          longRunning={analytics.longRunning}
          backlog={analytics.waiting + analytics.active}
          onNavigate={onNavigate}
        />
        <TopProcessPanel processes={analytics.topProcesses} />
        <QuickActions onNavigate={onNavigate} onExport={exportReport} />
      </section>
    </div>
  );
};

const KpiCard: React.FC<{ icon: React.ReactNode; title: string; value: string | number; tone: 'blue' | 'emerald' | 'red' | 'amber' | 'slate' }> = ({ icon, title, value, tone }) => {
  const toneStyles = {
    blue: 'bg-blue-50 text-blue-600 border-blue-100',
    emerald: 'bg-emerald-50 text-emerald-600 border-emerald-100',
    red: 'bg-red-50 text-red-600 border-red-100',
    amber: 'bg-amber-50 text-amber-600 border-amber-100',
    slate: 'bg-slate-50 text-slate-600 border-slate-100'
  }[tone];

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className={`mb-3 inline-flex rounded-lg border p-2 ${toneStyles}`}>{icon}</div>
      <p className="text-[11px] font-bold uppercase tracking-widest text-slate-400">{title}</p>
      <p className="mt-1 text-2xl font-bold text-slate-900">{value}</p>
    </div>
  );
};

const VolumeChart: React.FC<{ points: Array<{ label: string; value: number }> }> = ({ points }) => {
  const max = Math.max(1, ...points.map((point) => point.value));

  return (
    <div className="flex h-56 items-end gap-2 border-b border-l border-slate-200 px-2 pb-2">
      {points.map((point) => {
        const height = Math.max(6, (point.value / max) * 185);
        return (
          <div key={point.label} className="flex min-w-0 flex-1 flex-col items-center gap-2">
            <div className="flex h-48 w-full items-end">
              <div
                className="w-full rounded-t-md bg-blue-500/80 transition-all"
                style={{ height }}
                title={`${point.label}: ${point.value}`}
              />
            </div>
            <span className="w-full truncate text-center text-[10px] font-medium text-slate-400">{point.label}</span>
          </div>
        );
      })}
    </div>
  );
};

const OutcomeBars: React.FC<{ completed: number; failed: number; cancelled: number }> = ({ completed, failed, cancelled }) => {
  const total = Math.max(1, completed + failed + cancelled);
  const rows = [
    { label: 'Completed', value: completed, color: 'bg-emerald-500' },
    { label: 'Failed', value: failed, color: 'bg-red-500' },
    { label: 'Cancelled', value: cancelled, color: 'bg-slate-400' }
  ];

  return (
    <div className="space-y-4">
      {rows.map((row) => (
        <div key={row.label} className="space-y-1.5">
          <div className="flex justify-between text-xs">
            <span className="font-medium text-slate-600">{row.label}</span>
            <span className="font-bold text-slate-800">{row.value}</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-slate-100">
            <div className={`h-full rounded-full ${row.color}`} style={{ width: `${(row.value / total) * 100}%` }} />
          </div>
        </div>
      ))}
    </div>
  );
};

const AttentionPanel: React.FC<{
  failed: ProcessInstance[];
  longRunning: ProcessInstance[];
  backlog: number;
  onNavigate: (view: string) => void;
}> = ({ failed, longRunning, backlog, onNavigate }) => (
  <div className="rounded-xl border border-slate-200 bg-white p-5">
    <div className="mb-4 flex items-center justify-between">
      <div>
        <h3 className="text-sm font-semibold text-slate-800">Attention Required</h3>
        <p className="text-xs text-slate-500">Failure and waiting signals</p>
      </div>
      <AlertCircle size={18} className="text-amber-500" />
    </div>
    <div className="space-y-3">
      <AttentionRow label="Recently failed" value={failed.length} tone="red" />
      <AttentionRow label="Queue backlog" value={backlog} tone="amber" />
      <AttentionRow label="Longest running" value={longRunning.length} tone="blue" />
    </div>
    <div className="mt-5 space-y-2">
      {[...failed, ...longRunning].slice(0, 4).map((instance) => (
        <button
          key={`${instance.status}-${instance.id}`}
          onClick={() => onNavigate('instances')}
          className="flex w-full items-center justify-between rounded-lg border border-slate-100 bg-slate-50 px-3 py-2 text-left hover:border-slate-200 hover:bg-white"
        >
          <span className="min-w-0">
            <span className="block truncate text-xs font-semibold text-slate-800">#{instance.id} {getProcessName(instance)}</span>
            <span className="block text-[11px] text-slate-500">{formatAge(instance.createdAt)} running</span>
          </span>
          <StatusPill status={instance.status} />
        </button>
      ))}
      {failed.length === 0 && longRunning.length === 0 && (
        <div className="rounded-lg bg-emerald-50 px-3 py-3 text-xs font-medium text-emerald-700">No failed or long-running instances in the current filter.</div>
      )}
    </div>
  </div>
);

const AttentionRow: React.FC<{ label: string; value: number; tone: 'red' | 'amber' | 'blue' }> = ({ label, value, tone }) => {
  const toneStyles = {
    red: 'bg-red-50 text-red-700',
    amber: 'bg-amber-50 text-amber-700',
    blue: 'bg-blue-50 text-blue-700'
  }[tone];
  return (
    <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2">
      <span className="text-xs font-medium text-slate-600">{label}</span>
      <span className={`rounded-md px-2 py-1 text-xs font-bold ${toneStyles}`}>{value}</span>
    </div>
  );
};

const TopProcessPanel: React.FC<{ processes: Array<{ name: string; total: number; failed: number }> }> = ({ processes }) => (
  <div className="rounded-xl border border-slate-200 bg-white p-5">
    <div className="mb-4 flex items-center justify-between">
      <div>
        <h3 className="text-sm font-semibold text-slate-800">Top Processes by Volume</h3>
        <p className="text-xs text-slate-500">Most active process definitions</p>
      </div>
      <Workflow size={18} className="text-emerald-500" />
    </div>
    <div className="space-y-3">
      {processes.length === 0 ? (
        <div className="rounded-lg bg-slate-50 px-3 py-3 text-xs text-slate-500">No process activity in the current filter.</div>
      ) : (
        processes.map((process) => (
          <div key={process.name} className="space-y-1.5">
            <div className="flex items-center justify-between gap-3">
              <span className="truncate text-xs font-semibold text-slate-700">{process.name}</span>
              <span className="text-xs font-bold text-slate-900">{process.total}</span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-slate-100">
              <div className="h-full rounded-full bg-emerald-500" style={{ width: `${Math.max(8, process.total * 8)}%`, maxWidth: '100%' }} />
            </div>
            {process.failed > 0 && <p className="text-[11px] font-medium text-red-600">{process.failed} failed</p>}
          </div>
        ))
      )}
    </div>
  </div>
);

const QuickActions: React.FC<{ onNavigate: (view: string) => void; onExport: () => void }> = ({ onNavigate, onExport }) => {
  const actions = [
    { label: 'Search Instances', icon: Search, action: () => onNavigate('instances') },
    { label: 'View Workflows', icon: Layers, action: () => onNavigate('workflows') },
    { label: 'Code Task Audit', icon: Activity, action: () => onNavigate('code-tasks') },
    { label: 'Export Report', icon: Download, action: onExport }
  ];

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5">
      <div className="mb-4">
        <h3 className="text-sm font-semibold text-slate-800">Quick Actions</h3>
        <p className="text-xs text-slate-500">Operational shortcuts</p>
      </div>
      <div className="grid grid-cols-1 gap-2">
        {actions.map(({ label, icon: Icon, action }) => (
          <button
            key={label}
            onClick={action}
            className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-2.5 text-sm font-medium text-slate-700 hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700"
          >
            <span className="flex items-center gap-2"><Icon size={15} /> {label}</span>
            <span className="text-slate-300">→</span>
          </button>
        ))}
      </div>
    </div>
  );
};

const StatusPill: React.FC<{ status: string }> = ({ status }) => {
  const styles = status === 'FAILED'
    ? 'bg-red-50 text-red-700 border-red-100'
    : WAITING_STATUSES.has(status)
      ? 'bg-amber-50 text-amber-700 border-amber-100'
      : status === 'COMPLETED'
        ? 'bg-emerald-50 text-emerald-700 border-emerald-100'
        : 'bg-blue-50 text-blue-700 border-blue-100';

  return <span className={`rounded-full border px-2 py-1 text-[10px] font-bold ${styles}`}>{formatStatus(status)}</span>;
};

function buildAnalytics(instances: ProcessInstance[]) {
  const active = instances.filter((instance) => ACTIVE_STATUSES.has(instance.status)).length;
  const waiting = instances.filter((instance) => WAITING_STATUSES.has(instance.status)).length;
  const completed = instances.filter((instance) => instance.status === 'COMPLETED').length;
  const failed = instances.filter((instance) => instance.status === 'FAILED').length;
  const cancelled = instances.filter((instance) => instance.status === 'CANCELLED').length;
  const closedCount = instances.filter((instance) => CLOSED_STATUSES.has(instance.status)).length;
  const successRate = closedCount === 0 ? 0 : Math.round((completed / closedCount) * 100);

  const completionDurations = instances
    .filter((instance) => instance.status === 'COMPLETED')
    .map((instance) => durationMs(instance.createdAt, instance.updatedAt))
    .filter((duration): duration is number => duration !== null);

  const averageCompletionMs = completionDurations.length === 0
    ? null
    : completionDurations.reduce((sum, duration) => sum + duration, 0) / completionDurations.length;

  const longRunning = instances
    .filter((instance) => ACTIVE_STATUSES.has(instance.status) || WAITING_STATUSES.has(instance.status))
    .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
    .slice(0, 5);

  const recentFailed = instances
    .filter((instance) => instance.status === 'FAILED')
    .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
    .slice(0, 5);

  const processMap = new Map<string, { name: string; total: number; failed: number }>();
  instances.forEach((instance) => {
    const name = getProcessName(instance);
    const current = processMap.get(name) ?? { name, total: 0, failed: 0 };
    current.total += 1;
    if (instance.status === 'FAILED') current.failed += 1;
    processMap.set(name, current);
  });

  return {
    active,
    waiting,
    completed,
    failed,
    cancelled,
    closedCount,
    successRate,
    averageCompletionTime: averageCompletionMs === null ? '-' : formatDuration(averageCompletionMs),
    longRunning,
    recentFailed,
    topProcesses: Array.from(processMap.values()).sort((a, b) => b.total - a.total).slice(0, 5),
    volumeTrend: buildVolumeTrend(instances)
  };
}

function buildVolumeTrend(instances: ProcessInstance[]) {
  const days = Array.from({ length: 7 }, (_, index) => {
    const date = new Date();
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() - (6 - index));
    return {
      key: date.toISOString().slice(0, 10),
      label: date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
      value: 0
    };
  });

  const byKey = new Map(days.map((day) => [day.key, day]));
  instances.forEach((instance) => {
    const createdAt = parseDate(instance.createdAt);
    if (!createdAt) return;
    const key = createdAt.toISOString().slice(0, 10);
    const day = byKey.get(key);
    if (day) day.value += 1;
  });

  return days;
}

function getRangeStart(range: DateRange, now: Date): Date | null {
  if (range === 'all') return null;
  const start = new Date(now);
  start.setHours(0, 0, 0, 0);
  if (range === 'today') return start;
  start.setDate(start.getDate() - (range === '7d' ? 6 : 29));
  return start;
}

function getDefinitionId(instance: ProcessInstance) {
  return instance.processDefinitionId ?? instance.processDefinition?.id;
}

function getProcessName(instance: ProcessInstance) {
  return instance.processDefinitionName ?? instance.processDefinition?.name ?? `Definition #${getDefinitionId(instance) ?? 'unknown'}`;
}

function parseDate(value?: string) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function durationMs(start?: string, end?: string) {
  const startDate = parseDate(start);
  const endDate = parseDate(end);
  if (!startDate || !endDate) return null;
  return Math.max(0, endDate.getTime() - startDate.getTime());
}

function formatDuration(ms: number) {
  const minutes = Math.round(ms / 60000);
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.round(minutes / 60);
  if (hours < 48) return `${hours}h`;
  return `${Math.round(hours / 24)}d`;
}

function formatAge(start?: string) {
  const date = parseDate(start);
  if (!date) return '-';
  return formatDuration(Date.now() - date.getTime());
}

function formatStatus(status: string) {
  return status.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function csvCell(value: string) {
  return `"${value.replace(/"/g, '""')}"`;
}
