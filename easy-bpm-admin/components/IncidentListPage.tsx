import React, { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, CheckCircle2, ChevronLeft, ChevronRight, ExternalLink, Loader2, Play, RefreshCw, Search, X } from 'lucide-react';
import { adminService } from '../services/adminService';
import { IncidentEvent, IncidentGroup, IncidentRetryStatus, IncidentSource, IncidentStatus } from '../types';

interface IncidentListPageProps { currentUser: string; onOpenInstance: (instanceId: number) => void; }
type OperationalView = 'needs-attention' | 'retry-eligible' | 'retry-failed' | 'acknowledged-by-me' | 'recurring';

const sourceOptions: Array<IncidentSource | ''> = ['', 'PROCESS_ENGINE', 'WORKER', 'CODE_TASK', 'AI_TASK', 'MESSAGE'];
const retryStatusLabel: Record<IncidentRetryStatus, string> = {
  NOT_ELIGIBLE: 'Manual recovery', RETRY_ELIGIBLE: 'Retry eligible', RETRY_REQUESTED: 'Retry requested', RETRYING: 'Retrying', RETRY_SUCCEEDED: 'Retry succeeded', RETRY_FAILED: 'Retry failed', RETRY_EXHAUSTED: 'Retry exhausted'
};
const views: Array<{ id: OperationalView; label: string }> = [
  { id: 'needs-attention', label: 'Needs attention' }, { id: 'retry-eligible', label: 'Retry eligible' }, { id: 'retry-failed', label: 'Retry failed' }, { id: 'acknowledged-by-me', label: 'Acknowledged by me' }, { id: 'recurring', label: 'Recurring incidents' }
];

export const IncidentListPage: React.FC<IncidentListPageProps> = ({ currentUser, onOpenInstance }) => {
  const [activeView, setActiveView] = useState<OperationalView>('needs-attention');
  const [source, setSource] = useState<IncidentSource | ''>('');
  const [processDefinitionId, setProcessDefinitionId] = useState('');
  const [nodeId, setNodeId] = useState('');
  const [occurredSince, setOccurredSince] = useState('');
  const [search, setSearch] = useState('');
  const [groups, setGroups] = useState<IncidentGroup[]>([]);
  const [selectedGroup, setSelectedGroup] = useState<IncidentGroup | null>(null);
  const [selectedSignatures, setSelectedSignatures] = useState<string[]>([]);
  const [events, setEvents] = useState<IncidentEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const viewFilters = useMemo(() => {
    switch (activeView) {
      case 'retry-eligible': return { status: 'OPEN' as IncidentStatus, retryStatus: 'RETRY_ELIGIBLE' as IncidentRetryStatus };
      case 'retry-failed': return { retryStatus: 'RETRY_FAILED' as IncidentRetryStatus };
      case 'acknowledged-by-me': return { status: 'ACKNOWLEDGED' as IncidentStatus, acknowledgedBy: currentUser };
      case 'recurring': return { minOccurrences: 2 };
      default: return { status: 'OPEN' as IncidentStatus };
    }
  }, [activeView, currentUser]);

  const loadGroups = async () => {
    setLoading(true);
    setActionMessage(null);
    try {
      const response = await adminService.getIncidentGroups({
        ...viewFilters,
        source,
        processDefinitionId: Number(processDefinitionId) || null,
        nodeId,
        occurredSince: occurredSince ? `${occurredSince}T00:00:00` : undefined,
        page,
        size: 25
      });
      setGroups(response.content);
      setTotalPages(response.totalPages);
      setTotalElements(response.totalElements);
      setSelectedSignatures([]);
      setSelectedGroup((current) => response.content.find((group) => group.signature === current?.signature) ?? response.content[0] ?? null);
    } catch (error) {
      console.error(error);
      setActionMessage('Failed to load incident groups.');
    } finally { setLoading(false); }
  };

  useEffect(() => { loadGroups(); }, [viewFilters, source, processDefinitionId, nodeId, occurredSince, page]);
  useEffect(() => {
    if (!selectedGroup) { setEvents([]); return; }
    setDetailsLoading(true);
    adminService.getIncidentEvents(selectedGroup.representativeIncidentId).then(setEvents).catch(() => setEvents([])).finally(() => setDetailsLoading(false));
  }, [selectedGroup?.representativeIncidentId]);

  const visibleGroups = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return groups;
    return groups.filter((group) => [group.message, group.processName, group.nodeId, group.source].filter(Boolean).some((value) => String(value).toLowerCase().includes(query)));
  }, [groups, search]);

  const toggleGroup = (signature: string) => setSelectedSignatures((current) => current.includes(signature) ? current.filter((entry) => entry !== signature) : [...current, signature]);
  const selectAllVisible = () => {
    const allSelected = visibleGroups.length > 0 && visibleGroups.every((group) => selectedSignatures.includes(group.signature));
    setSelectedSignatures(allSelected ? [] : visibleGroups.map((group) => group.signature));
  };
  const retrySelectedGroup = async () => {
    if (!selectedGroup || selectedGroup.retryStatus !== 'RETRY_ELIGIBLE') return;
    setActionLoading(true); setActionMessage(null);
    try { await adminService.retryIncident(selectedGroup.representativeIncidentId, currentUser); setActionMessage(`Retry requested for ${selectedGroup.message}.`); await loadGroups(); }
    catch (error) { console.error(error); setActionMessage('Failed to request retry for this incident group.'); }
    finally { setActionLoading(false); }
  };
  const acknowledgeSelectedGroup = async () => {
    if (!selectedGroup) return;
    setActionLoading(true);
    try { await adminService.acknowledgeIncident(selectedGroup.representativeIncidentId, currentUser); setActionMessage('Incident group acknowledged.'); await loadGroups(); }
    catch (error) { console.error(error); setActionMessage('Failed to acknowledge incident group.'); }
    finally { setActionLoading(false); }
  };

  return <div className="space-y-5">
    <header className="flex flex-wrap items-start justify-between gap-4"><div><h2 className="flex items-center gap-2 text-2xl font-bold text-slate-800"><AlertTriangle size={23} className="text-red-600" /> Incident Manager</h2><p className="mt-1 text-sm text-slate-500">Group repeated failures and recover affected work safely.</p></div><button onClick={loadGroups} disabled={loading} className="flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700">{loading ? <Loader2 size={15} className="animate-spin" /> : <RefreshCw size={15} />} Refresh</button></header>
    <div className="flex flex-wrap gap-1 border-b border-slate-200" role="tablist" aria-label="Incident views">{views.map((view) => <button key={view.id} type="button" role="tab" aria-selected={activeView === view.id} onClick={() => { setActiveView(view.id); setPage(0); }} className={`border-b-2 px-3 py-2.5 text-sm font-medium ${activeView === view.id ? 'border-blue-600 text-blue-700' : 'border-transparent text-slate-500 hover:text-slate-800'}`}>{view.label}</button>)}</div>
    <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_420px] 2xl:grid-cols-[minmax(0,1fr)_460px]">
      <section className="min-w-0 space-y-3">
        <div className="grid grid-cols-1 gap-2 rounded-xl border border-slate-200 bg-white p-3 md:grid-cols-2 xl:grid-cols-[180px_170px_170px_170px_minmax(220px,1fr)]">
          <select value={source} onChange={(event) => { setSource(event.target.value as IncidentSource | ''); setPage(0); }} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"><option value="">All sources</option>{sourceOptions.filter(Boolean).map((option) => <option key={option} value={option}>{option}</option>)}</select>
          <input type="number" value={processDefinitionId} onChange={(event) => { setProcessDefinitionId(event.target.value); setPage(0); }} className="rounded-lg border border-slate-300 px-3 py-2 text-sm" placeholder="Process definition" />
          <input value={nodeId} onChange={(event) => { setNodeId(event.target.value); setPage(0); }} className="rounded-lg border border-slate-300 px-3 py-2 text-sm" placeholder="Node ID" />
          <input type="date" value={occurredSince} onChange={(event) => { setOccurredSince(event.target.value); setPage(0); }} className="rounded-lg border border-slate-300 px-3 py-2 text-sm" aria-label="Occurred since" />
          <div className="relative"><Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" /><input value={search} onChange={(event) => setSearch(event.target.value)} className="w-full rounded-lg border border-slate-300 py-2 pl-9 pr-3 text-sm" placeholder="Search groups" /></div>
        </div>
        {selectedSignatures.length > 0 && <div className="flex flex-wrap items-center gap-3 rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-800"><strong>{selectedSignatures.length} selected</strong><span className="text-blue-600">Batch retry will be enabled after queue concurrency controls are available.</span><button onClick={() => setSelectedSignatures([])} className="ml-auto text-blue-700" aria-label="Clear selection"><X size={16} /></button></div>}
        {actionMessage && <div className="rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-700">{actionMessage}</div>}
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm"><div className="overflow-x-auto"><table className="w-full min-w-[1080px] text-left text-sm"><thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr><th className="w-10 px-3 py-3"><input type="checkbox" checked={visibleGroups.length > 0 && visibleGroups.every((group) => selectedSignatures.includes(group.signature))} onChange={selectAllVisible} aria-label="Select visible incident groups" /></th><th className="px-3 py-3">Incident group</th><th className="px-3 py-3">Source</th><th className="px-3 py-3">Process / instances</th><th className="px-3 py-3">Node</th><th className="px-3 py-3 text-right">Occurrences</th><th className="px-3 py-3">Last occurrence</th><th className="px-3 py-3">Recovery</th></tr></thead><tbody className="divide-y divide-slate-200">
          {loading ? <tr><td colSpan={8} className="px-3 py-14 text-center text-slate-500"><Loader2 size={20} className="mx-auto mb-2 animate-spin" />Loading incident groups...</td></tr> : visibleGroups.length === 0 ? <tr><td colSpan={8} className="px-3 py-14 text-center text-slate-500">No incident groups match this view.</td></tr> : visibleGroups.map((group) => <tr key={group.signature} onClick={() => setSelectedGroup(group)} className={`incident-group-row cursor-pointer ${selectedGroup?.signature === group.signature ? 'is-selected' : ''}`}><td className="px-3 py-3"><input type="checkbox" checked={selectedSignatures.includes(group.signature)} onClick={(event) => event.stopPropagation()} onChange={() => toggleGroup(group.signature)} aria-label={`Select ${group.message}`} /></td><td className="max-w-[420px] px-3 py-3"><p className="line-clamp-2 font-medium text-slate-800">{group.message}</p><p className="mt-1 text-xs text-slate-500">{group.status === 'ACKNOWLEDGED' && group.acknowledgedBy ? `Acknowledged by ${group.acknowledgedBy}` : group.status}</p></td><td className="px-3 py-3 font-mono text-xs text-slate-600">{group.source}</td><td className="px-3 py-3"><p className="text-slate-700">{group.processName || `Definition #${group.processDefinitionId ?? 'unknown'}`}</p><p className="mt-1 text-xs text-slate-500">{group.instanceCount} affected instances</p></td><td className="px-3 py-3 font-mono text-xs text-slate-600">{group.nodeId || '—'}</td><td className="px-3 py-3 text-right font-semibold tabular-nums text-slate-800">{group.occurrenceCount}</td><td className="px-3 py-3 whitespace-nowrap text-xs text-slate-600">{new Date(group.lastOccurredAt).toLocaleString()}</td><td className="px-3 py-3"><RetryStatus status={group.retryStatus} /></td></tr>)}</tbody></table></div>
          <div className="flex items-center justify-between border-t border-slate-200 bg-slate-50 px-4 py-3 text-xs text-slate-600"><span>{totalElements} groups</span><div className="flex items-center gap-2"><button onClick={() => setPage((current) => Math.max(0, current - 1))} disabled={page === 0} className="rounded p-1 disabled:opacity-40"><ChevronLeft size={17} /></button><span>Page {page + 1} of {Math.max(totalPages, 1)}</span><button onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))} disabled={page >= totalPages - 1} className="rounded p-1 disabled:opacity-40"><ChevronRight size={17} /></button></div></div>
        </div>
      </section>
      <aside className="min-h-[440px] rounded-xl border border-slate-200 bg-white p-5 shadow-sm">{!selectedGroup ? <div className="flex h-full items-center justify-center text-center text-sm text-slate-500">Select an incident group to inspect its recovery details.</div> : <IncidentDrawer group={selectedGroup} events={events} loading={detailsLoading} actionLoading={actionLoading} onRetry={retrySelectedGroup} onAcknowledge={acknowledgeSelectedGroup} onOpenInstance={onOpenInstance} />}</aside>
    </div>
  </div>;
};

const RetryStatus: React.FC<{ status: IncidentRetryStatus }> = ({ status }) => {
  const tone = status === 'RETRY_ELIGIBLE' ? 'bg-blue-50 text-blue-700 border-blue-200' : status === 'RETRY_EXHAUSTED' || status === 'RETRY_FAILED' ? 'bg-red-50 text-red-700 border-red-200' : status === 'RETRY_REQUESTED' || status === 'RETRYING' ? 'bg-amber-50 text-amber-700 border-amber-200' : 'bg-slate-100 text-slate-700 border-slate-200';
  return <span className={`inline-flex whitespace-nowrap rounded-full border px-2 py-1 text-xs font-medium ${tone}`}>{retryStatusLabel[status]}</span>;
};

const IncidentDrawer: React.FC<{ group: IncidentGroup; events: IncidentEvent[]; loading: boolean; actionLoading: boolean; onRetry: () => void; onAcknowledge: () => void; onOpenInstance: (instanceId: number) => void }> = ({ group, events, loading, actionLoading, onRetry, onAcknowledge, onOpenInstance }) => <div className="space-y-5">
  <div><RetryStatus status={group.retryStatus} /><h3 className="mt-3 text-base font-semibold text-slate-800">{group.message}</h3><p className="mt-1 text-xs text-slate-500">{group.processName || `Definition #${group.processDefinitionId ?? 'unknown'}`} · {group.nodeId || 'No node'}</p></div>
  <div className="border-t border-slate-200 pt-4"><p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Recovery</p><dl className="mt-3 grid grid-cols-2 gap-3 text-sm"><div><dt className="text-xs text-slate-500">Attempts</dt><dd className="mt-1 font-medium text-slate-800">{group.retryAttemptCount} of {group.maxRetryAttempts}</dd></div><div><dt className="text-xs text-slate-500">Affected</dt><dd className="mt-1 font-medium text-slate-800">{group.instanceCount} instances</dd></div>{group.nextRetryAt && <div className="col-span-2"><dt className="text-xs text-slate-500">Next retry</dt><dd className="mt-1 text-slate-700">{new Date(group.nextRetryAt).toLocaleString()}</dd></div>}</dl>{group.lastRetryError && <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700">{group.lastRetryError}</p>}</div>
  {group.technicalDetails && <div className="border-t border-slate-200 pt-4"><p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Technical details</p><p className="mt-2 break-words text-sm text-slate-700">{group.technicalDetails}</p></div>}
  {group.resolutionNote && <div className="border-t border-slate-200 pt-4"><p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Latest note</p><p className="mt-2 break-words text-sm text-slate-700">{group.resolutionNote}</p></div>}
  <div className="border-t border-slate-200 pt-4"><p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Affected instances</p><div className="mt-2 flex flex-wrap gap-2">{group.sampleInstanceIds.length === 0 ? <p className="text-sm text-slate-500">No instance samples available.</p> : group.sampleInstanceIds.slice(0, 5).map((instanceId) => <button key={instanceId} onClick={() => onOpenInstance(instanceId)} className="rounded-md border border-slate-300 px-2 py-1 text-xs font-medium text-blue-700">Instance #{instanceId}</button>)}</div></div>
  <div className="border-t border-slate-200 pt-4"><p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Timeline</p>{loading ? <Loader2 size={16} className="mt-3 animate-spin text-slate-400" /> : <div className="mt-3 space-y-3">{events.length === 0 ? <p className="text-sm text-slate-500">No timeline events recorded.</p> : events.slice(0, 5).map((event) => <div key={event.id} className="border-l-2 border-blue-200 pl-3"><p className="text-xs font-medium text-slate-700">{event.eventType}</p><p className="mt-1 text-xs text-slate-500">{event.message}</p><p className="mt-1 text-[11px] text-slate-400">{new Date(event.createdAt).toLocaleString()}</p></div>)}</div>}</div>
  <div className="grid grid-cols-2 gap-2 border-t border-slate-200 pt-4">{group.retryStatus === 'RETRY_ELIGIBLE' && <button onClick={onRetry} disabled={actionLoading} className="col-span-2 flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-60">{actionLoading ? <Loader2 size={15} className="animate-spin" /> : <Play size={15} />} Retry this group</button>}<button onClick={onAcknowledge} disabled={actionLoading || group.status === 'RESOLVED'} className="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 disabled:opacity-50"><CheckCircle2 size={15} className="mr-1 inline" />Acknowledge</button><button onClick={() => group.sampleInstanceIds[0] && onOpenInstance(group.sampleInstanceIds[0])} className="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700"><ExternalLink size={15} className="mr-1 inline" />Open instance</button></div>
</div>;
