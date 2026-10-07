import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { BpmTask, Page, SlaEvent, SlaMetricRow, SlaSummary, SlaTaskFilter } from '../types';

export function SlaDashboard({ onInspectInstance }: { onInspectInstance: (id: number) => void }) {
  const [status, setStatus] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [groupBy, setGroupBy] = useState('PROCESS');
  const [page, setPage] = useState(0);
  const [metricPage, setMetricPage] = useState(0);
  const [tasks, setTasks] = useState<Page<BpmTask> | null>(null);
  const [metrics, setMetrics] = useState<Page<SlaMetricRow> | null>(null);
  const [summary, setSummary] = useState<SlaSummary | null>(null);
  const [selected, setSelected] = useState<BpmTask | null>(null);
  const [events, setEvents] = useState<Page<SlaEvent> | null>(null);
  const [eventPage, setEventPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [retrying, setRetrying] = useState<number | null>(null);
  const [revision, setRevision] = useState(0);
  const iso = (value: string) => value ? new Date(value).toISOString() : '';
  const input = 'rounded border border-slate-300 p-2 text-sm bg-white';
  useEffect(() => {
    let active = true;
    const load = async () => {
      setLoading(true); setError(null);
      try {
        if (from && to && new Date(from) > new Date(to)) throw new Error('Due date from must precede due date to.');
        const filters: SlaTaskFilter[] = [];
        if (status) filters.push({ field: 'slaStatus', operator: 'EQUALS', value: status });
        if (from) filters.push({ field: 'dueDate', operator: 'GREATER_THAN_OR_EQUAL', value: iso(from) });
        if (to) filters.push({ field: 'dueDate', operator: 'LESS_THAN_OR_EQUAL', value: iso(to) });
        const results = await Promise.allSettled([adminService.searchSlaTasks(filters, page), adminService.getSlaSummary(filters), adminService.getSlaMetrics(groupBy, metricPage, iso(from), iso(to))]);
        if (!active) return;
        if (results[0].status === 'fulfilled') setTasks(results[0].value);
        if (results[1].status === 'fulfilled') setSummary(results[1].value);
        if (results[2].status === 'fulfilled') setMetrics(results[2].value);
        const failures = results.filter(r => r.status === 'rejected') as PromiseRejectedResult[];
        if (failures.length) setError(failures.map(r => r.reason.message).join(' '));
      } catch (e) { if (active) setError((e as Error).message); }
      finally { if (active) setLoading(false); }
    };
    void load();
    return () => { active = false; };
  }, [status, from, to, groupBy, page, metricPage, revision]);

  useEffect(() => {
    if (!selected) return;
    let active = true;
    adminService.getSlaEvents(selected.id, eventPage).then(data => { if (active) setEvents(data); }).catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [selected?.id, eventPage, revision]);

  const retry = async (id: number) => {
    setRetrying(id); setError(null);
    try { await adminService.retryIncident(id); setRevision(v => v + 1); }
    catch (e) { setError((e as Error).message); }
    finally { setRetrying(null); }
  };
  const pager = (value: number, total: number, change: (page: number) => void) => <div className="flex gap-3 text-sm items-center"><button disabled={value === 0} onClick={() => change(value - 1)}>Previous</button><span>Page {value + 1} of {Math.max(1, total)}</span><button disabled={value + 1 >= total} onClick={() => change(value + 1)}>Next</button></div>;
  const resetPages = () => { setPage(0); setMetricPage(0); };
  return <div className="space-y-5">
    <h2 className="text-2xl font-bold">Task SLAs</h2>
    <div className="flex flex-wrap gap-3 items-end">
      <label className="text-sm">Task SLA status<select className={`block ${input}`} value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}><option value="">All</option>{['ON_TRACK', 'AT_RISK', 'OVERDUE', 'COMPLETED'].map(s => <option key={s}>{s}</option>)}</select></label>
      <label className="text-sm">Due from<input type="datetime-local" className={`block ${input}`} value={from} onChange={e => { setFrom(e.target.value); resetPages(); }} /></label>
      <label className="text-sm">Due to (inclusive)<input type="datetime-local" className={`block ${input}`} value={to} onChange={e => { setTo(e.target.value); resetPages(); }} /></label>
      <button className={input} onClick={() => setRevision(v => v + 1)} disabled={loading}>Refresh</button>
    </div>
    <p className="text-xs text-slate-500">Date inputs use your browser timezone and are sent with a UTC offset. Group metrics use the due range; status applies to task counters and task results.</p>
    {error && <p role="alert" className="text-red-700">{error} Refresh to retry.</p>}
    {summary && <div className="rounded border p-4" aria-label="SLA counters">On track: {summary.onTrack} · At risk: {summary.atRisk} · Overdue: {summary.overdue} · Completed: {summary.completed} · No SLA: {summary.withoutSla}<p className="text-xs">As of {summary.asOf}</p></div>}
    <section className="space-y-3">
      <label>Group metrics by <select className={input} value={groupBy} onChange={e => { setGroupBy(e.target.value); setMetricPage(0); }}>{['PROCESS', 'TASK', 'USER', 'GROUP'].map(g => <option key={g}>{g}</option>)}</select></label>
      <p className="text-xs">User means current assignee, including unassigned. Groups use current candidate membership; a task may count in several groups. Within-SLA rate excludes tasks without SLA. Alert duration uses the first successful alert.</p>
      <div className="overflow-x-auto"><table className="w-full text-sm"><thead><tr>{['Dimension', 'On track', 'At risk', 'Overdue', 'Completed', 'Within SLA', 'Escalations OK / failed', 'Alert → completion'].map(h => <th className="text-left p-2" key={h}>{h}</th>)}</tr></thead><tbody>{metrics?.content.map(row => <tr key={row.key} className="border-t"><td className="p-2">{row.label || row.key}</td><td>{row.onTrack}</td><td>{row.atRisk}</td><td>{row.overdue}</td><td>{row.totalSlaCompleted}</td><td>{row.completedWithinSlaCount} / {row.totalSlaCompleted} ({row.completedWithinSlaRate === null ? '—' : `${(row.completedWithinSlaRate * 100).toFixed(1)}%`})</td><td>{row.successfulEscalations} / {row.failedEscalations}</td><td>{row.alertToCompletionAverageSeconds === null ? '—' : `${row.alertToCompletionAverageSeconds.toFixed(0)}s`} ({row.alertToCompletionSampleCount} samples)</td></tr>)}</tbody></table></div>
      {metrics?.content.length === 0 && <p>No metric rows in this range.</p>}
      {pager(metricPage, metrics?.totalPages || 0, setMetricPage)}
    </section>
    <section className="space-y-3"><h3 className="font-semibold">Tasks by earliest deadline</h3><div className="overflow-x-auto"><table className="w-full text-sm"><thead><tr>{['Task', 'Instance', 'Assignee', 'Due', 'SLA status', 'History'].map(h => <th className="text-left p-2" key={h}>{h}</th>)}</tr></thead><tbody>{tasks?.content.map(task => <tr key={task.id} className="border-t"><td className="p-2">#{task.id} {task.title || task.name}</td><td><button className="underline" onClick={() => onInspectInstance(task.processInstanceId)}>#{task.processInstanceId}</button></td><td>{task.assignee || 'Unassigned'}</td><td>{task.dueDate ? new Intl.DateTimeFormat(undefined, { dateStyle: 'short', timeStyle: 'short', timeZone: task.slaTimeZone || 'UTC' }).format(new Date(task.dueDate)) : 'No SLA'}</td><td>{task.slaStatus || 'No SLA'}</td><td><button className="underline" onClick={() => { setSelected(task); setEventPage(0); setEvents(null); }}>Inspect</button></td></tr>)}</tbody></table></div>{tasks?.content.length === 0 && <p>No tasks match these filters.</p>}{pager(page, tasks?.totalPages || 0, setPage)}</section>
    {selected && <section className="rounded border p-4 space-y-3"><h3 className="font-semibold">SLA history — Task #{selected.id}</h3><button className="underline text-sm" onClick={() => onInspectInstance(selected.processInstanceId)}>Open instance timeline and variables</button><p className="text-xs">For failed dynamic targets, correct the process variable in the instance before retrying. Completed or stale actions may return a conflict.</p><ul className="space-y-2">{events?.content.map(event => <li key={event.id} className="border-t pt-2 text-sm"><strong>{event.eventType}</strong> · {event.outcome} · Action {event.actionId || '—'}<div>Scheduled: {event.scheduledAt || '—'} · Occurred: {event.occurredAt}</div><p>{event.message}</p>{event.incidentId && <><span>Incident #{event.incidentId} </span>{event.outcome === 'FAILED' && <button className="underline" disabled={retrying !== null} onClick={() => retry(event.incidentId!)}>{retrying === event.incidentId ? 'Retrying…' : 'Retry escalation'}</button>}</>}</li>)}</ul>{events?.content.length === 0 && <p>No SLA history.</p>}{pager(eventPage, events?.totalPages || 0, setEventPage)}</section>}
  </div>;
}
