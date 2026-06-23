import React, { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, CheckCircle2, ExternalLink, Loader2, Play, RotateCcw, Search, ShieldCheck } from 'lucide-react';
import { adminService } from '../services/adminService';
import { Incident, IncidentEvent, IncidentResolutionAction, IncidentSource, IncidentStatus } from '../types';

interface IncidentListPageProps {
  currentUser: string;
  onOpenInstance: (instanceId: number) => void;
}

const statusOptions: Array<IncidentStatus | ''> = ['', 'OPEN', 'ACKNOWLEDGED', 'RESOLVED'];
const sourceOptions: Array<IncidentSource | ''> = ['', 'PROCESS_ENGINE', 'WORKER', 'CODE_TASK', 'AI_TASK', 'MESSAGE'];
const resolutionActions: Array<IncidentResolutionAction | ''> = [
  '',
  'RESOLVED_MANUALLY',
  'VARIABLE_FIXED',
  'RETRIED_SUCCESSFULLY',
  'IGNORED_KNOWN_ISSUE',
  'INSTANCE_CANCELLED'
];

export const IncidentListPage: React.FC<IncidentListPageProps> = ({ currentUser, onOpenInstance }) => {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [status, setStatus] = useState<IncidentStatus | ''>('OPEN');
  const [source, setSource] = useState<IncidentSource | ''>('');
  const [instanceId, setInstanceId] = useState('');
  const [loading, setLoading] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [resolutionNotes, setResolutionNotes] = useState<Record<number, string>>({});
  const [resolutionActionByIncident, setResolutionActionByIncident] = useState<Record<number, IncidentResolutionAction | ''>>({});
  const [eventsByIncident, setEventsByIncident] = useState<Record<number, IncidentEvent[]>>({});
  const [expandedIncidentId, setExpandedIncidentId] = useState<number | null>(null);

  const parsedInstanceId = useMemo(() => {
    const parsed = Number(instanceId);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }, [instanceId]);

  const loadIncidents = async () => {
    setLoading(true);
    setActionMessage(null);
    try {
      const page = await adminService.getIncidents({
        status,
        source,
        processInstanceId: parsedInstanceId,
        page: 0,
        size: 50
      });
      setIncidents(page.content);
    } catch (error) {
      console.error(error);
      setActionMessage('Failed to load incidents.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadIncidents();
  }, [status, source]);

  const acknowledgeIncident = async (incidentId: number) => {
    try {
      await adminService.acknowledgeIncident(incidentId, currentUser);
      setActionMessage(`Incident #${incidentId} acknowledged.`);
      await loadIncidents();
    } catch (error) {
      console.error(error);
      setActionMessage('Failed to acknowledge incident.');
    }
  };

  const resolveIncident = async (incidentId: number) => {
    try {
      await adminService.resolveIncident(
        incidentId,
        currentUser,
        resolutionNotes[incidentId] || undefined,
        resolutionActionByIncident[incidentId] || undefined
      );
      setActionMessage(`Incident #${incidentId} resolved.`);
      await loadIncidents();
    } catch (error) {
      console.error(error);
      setActionMessage('Failed to resolve incident.');
    }
  };

  const reopenIncident = async (incidentId: number) => {
    try {
      await adminService.reopenIncident(incidentId);
      setActionMessage(`Incident #${incidentId} reopened.`);
      await loadIncidents();
    } catch (error) {
      console.error(error);
      setActionMessage('Failed to reopen incident.');
    }
  };

  const retryIncident = async (incidentId: number) => {
    try {
      await adminService.retryIncident(incidentId, currentUser);
      setActionMessage(`Retry requested for incident #${incidentId}.`);
      await loadIncidents();
    } catch (error) {
      console.error(error);
      setActionMessage('Failed to retry incident.');
    }
  };

  const toggleTimeline = async (incidentId: number) => {
    if (expandedIncidentId === incidentId) {
      setExpandedIncidentId(null);
      return;
    }

    setExpandedIncidentId(incidentId);
    if (eventsByIncident[incidentId]) return;

    try {
      const events = await adminService.getIncidentEvents(incidentId);
      setEventsByIncident((current) => ({ ...current, [incidentId]: events }));
    } catch (error) {
      console.error(error);
      setActionMessage('Failed to load incident timeline.');
    }
  };

  const statusClass = (incidentStatus: IncidentStatus) => {
    switch (incidentStatus) {
      case 'OPEN':
        return 'bg-red-50 text-red-700 border-red-200';
      case 'ACKNOWLEDGED':
        return 'bg-amber-50 text-amber-700 border-amber-200';
      case 'RESOLVED':
        return 'bg-emerald-50 text-emerald-700 border-emerald-200';
    }
  };

  return (
    <div className="space-y-6">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-slate-800 flex items-center gap-2">
            <AlertTriangle size={24} className="text-red-600" /> Incident Manager
          </h2>
          <p className="text-slate-500 text-sm mt-1">Review failed execution records and track operational recovery.</p>
        </div>
        <button
          onClick={loadIncidents}
          disabled={loading}
          className="px-3 py-2 rounded-lg bg-slate-900 text-white text-sm font-medium flex items-center gap-2 disabled:opacity-60"
        >
          {loading ? <Loader2 className="animate-spin" size={15} /> : <RotateCcw size={15} />}
          Refresh
        </button>
      </header>

      <div className="bg-white border border-slate-200 rounded-xl p-4">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <select
            value={status}
            onChange={(event) => setStatus(event.target.value as IncidentStatus | '')}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm bg-white"
          >
            {statusOptions.map((option) => (
              <option key={option || 'ALL'} value={option}>{option || 'All statuses'}</option>
            ))}
          </select>
          <select
            value={source}
            onChange={(event) => setSource(event.target.value as IncidentSource | '')}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm bg-white"
          >
            {sourceOptions.map((option) => (
              <option key={option || 'ALL'} value={option}>{option || 'All sources'}</option>
            ))}
          </select>
          <div className="relative md:col-span-2">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={15} />
            <input
              type="number"
              value={instanceId}
              onChange={(event) => setInstanceId(event.target.value)}
              onKeyDown={(event) => event.key === 'Enter' && loadIncidents()}
              className="w-full rounded-lg border border-slate-300 pl-9 pr-3 py-2 text-sm"
              placeholder="Filter by process instance id"
            />
          </div>
        </div>
      </div>

      {actionMessage && (
        <div className="rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-700">{actionMessage}</div>
      )}

      {loading ? (
        <div className="flex items-center justify-center py-16 text-slate-400 gap-3">
          <Loader2 className="animate-spin text-red-500" size={24} />
          <span className="text-sm">Loading incidents...</span>
        </div>
      ) : incidents.length === 0 ? (
        <div className="bg-white border border-slate-200 rounded-xl p-10 text-center">
          <ShieldCheck size={34} className="mx-auto text-emerald-500 mb-3" />
          <p className="text-sm font-medium text-slate-700">No incidents found</p>
          <p className="text-xs text-slate-500 mt-1">The selected filters have no matching operational issues.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {incidents.map((incident) => (
            <div key={incident.id} className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2 mb-2">
                    <span className={`px-2.5 py-1 rounded-full text-xs font-semibold border ${statusClass(incident.status)}`}>
                      {incident.status}
                    </span>
                    <span className="px-2.5 py-1 rounded-full text-xs font-semibold border bg-slate-50 text-slate-600 border-slate-200">
                      {incident.source}
                    </span>
                    <span className="px-2.5 py-1 rounded-full text-xs font-semibold border bg-purple-50 text-purple-700 border-purple-200">
                      {incident.severity}
                    </span>
                  </div>
                  <h3 className="text-sm font-semibold text-slate-800">{incident.message}</h3>
                  <div className="flex flex-wrap gap-3 mt-2 text-xs text-slate-500">
                    <span>Incident #{incident.id}</span>
                    <span>Instance #{incident.processInstanceId}</span>
                    {incident.nodeId && <span>Node {incident.nodeId}</span>}
                    <span>Created {new Date(incident.createdAt).toLocaleString()}</span>
                    {incident.occurrenceCount > 1 && (
                      <span>{incident.occurrenceCount} occurrences, last {new Date(incident.lastOccurredAt).toLocaleString()}</span>
                    )}
                    {incident.externalReferenceId && <span>{incident.externalReferenceId}</span>}
                  </div>
                  {incident.technicalDetails && (
                    <p className="mt-3 text-xs text-slate-500 bg-slate-50 border border-slate-200 rounded-lg px-3 py-2">
                      {incident.technicalDetails}
                    </p>
                  )}
                  {incident.resolutionAction && (
                    <p className="mt-2 text-xs text-emerald-700">Resolution action: {incident.resolutionAction}</p>
                  )}
                </div>
                <button
                  onClick={() => onOpenInstance(incident.processInstanceId)}
                  className="text-slate-400 hover:text-blue-600 transition-colors"
                  title={`Process instance ${incident.processInstanceId}`}
                >
                  <ExternalLink size={16} />
                </button>
              </div>

              <div className="mt-4 flex flex-col gap-3 border-t border-slate-100 pt-4">
                {incident.status !== 'RESOLVED' && (
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
                    <select
                      value={resolutionActionByIncident[incident.id] ?? ''}
                      onChange={(event) => setResolutionActionByIncident((current) => ({
                        ...current,
                        [incident.id]: event.target.value as IncidentResolutionAction | ''
                      }))}
                      className="rounded-lg border border-slate-300 px-3 py-2 text-sm bg-white"
                    >
                      {resolutionActions.map((action) => (
                        <option key={action || 'NONE'} value={action}>{action || 'Resolution action'}</option>
                      ))}
                    </select>
                    <textarea
                      value={resolutionNotes[incident.id] ?? ''}
                      onChange={(event) => setResolutionNotes((current) => ({ ...current, [incident.id]: event.target.value }))}
                      className="md:col-span-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm min-h-20 resize-y"
                      placeholder="Resolution note"
                    />
                  </div>
                )}
                <div className="flex flex-wrap gap-2">
                  {incident.status === 'OPEN' && (
                    <button
                      onClick={() => acknowledgeIncident(incident.id)}
                      className="px-3 py-2 rounded-lg border border-amber-200 bg-amber-50 text-amber-700 text-sm font-medium"
                    >
                      Acknowledge
                    </button>
                  )}
                  {incident.status !== 'RESOLVED' && incident.source === 'WORKER' && (
                    <button
                      onClick={() => retryIncident(incident.id)}
                      className="px-3 py-2 rounded-lg border border-blue-200 bg-blue-50 text-blue-700 text-sm font-medium flex items-center gap-2"
                    >
                      <Play size={15} /> Retry
                    </button>
                  )}
                  {incident.status !== 'RESOLVED' ? (
                    <button
                      onClick={() => resolveIncident(incident.id)}
                      className="px-3 py-2 rounded-lg border border-emerald-200 bg-emerald-50 text-emerald-700 text-sm font-medium flex items-center gap-2"
                    >
                      <CheckCircle2 size={15} /> Resolve
                    </button>
                  ) : (
                    <button
                      onClick={() => reopenIncident(incident.id)}
                      className="px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-slate-700 text-sm font-medium flex items-center gap-2"
                    >
                      <RotateCcw size={15} /> Reopen
                    </button>
                  )}
                  <button
                    onClick={() => toggleTimeline(incident.id)}
                    className="px-3 py-2 rounded-lg border border-slate-200 bg-white text-slate-700 text-sm font-medium"
                  >
                    {expandedIncidentId === incident.id ? 'Hide Timeline' : 'Timeline'}
                  </button>
                </div>
                {expandedIncidentId === incident.id && (
                  <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 space-y-2">
                    {(eventsByIncident[incident.id] ?? []).length === 0 ? (
                      <p className="text-xs text-slate-500">No timeline events recorded.</p>
                    ) : (
                      eventsByIncident[incident.id].map((event) => (
                        <div key={event.id} className="flex items-start justify-between gap-3 text-xs">
                          <div>
                            <p className="font-semibold text-slate-700">{event.eventType}</p>
                            <p className="text-slate-500">{event.message}</p>
                            {event.actor && <p className="text-slate-400">Actor: {event.actor}</p>}
                          </div>
                          <span className="text-slate-400 whitespace-nowrap">{new Date(event.createdAt).toLocaleString()}</span>
                        </div>
                      ))
                    )}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
