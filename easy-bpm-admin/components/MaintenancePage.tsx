import React, { useEffect, useState } from 'react';
import { AlertTriangle, Database, Loader2, Search, Trash2 } from 'lucide-react';
import { adminService } from '../services/adminService';
import { MaintenanceCleanupSummary, ProcessDefinition } from '../types';

const SummaryGrid: React.FC<{ summary: MaintenanceCleanupSummary }> = ({ summary }) => {
  const rows = [
    ['Process definitions', summary.processDefinitionsDeleted],
    ['Process instances', summary.processInstancesDeleted],
    ['Tasks', summary.tasksDeleted],
    ['Process variables', summary.processVariablesDeleted],
    ['Task variables', summary.taskVariablesDeleted],
    ['Documents', summary.documentsDeleted],
    ['Messages', summary.messageSubscriptionsDeleted],
    ['Worker requests', summary.workerRequestsDeleted],
    ['Code executions', summary.codeTaskExecutionsDeleted],
    ['Incidents', summary.incidentsDeleted],
    ['Incident events', summary.incidentEventsDeleted],
    ['Timeline events', summary.timelineEventsDeleted],
    ['Call mappings', summary.callActivityMappingsDeleted]
  ] as const;

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h3 className="font-semibold text-slate-800 text-sm">{summary.dryRun ? 'Preview summary' : 'Deletion summary'}</h3>
        <span className={`text-xs px-2 py-1 rounded-full border ${summary.dryRun ? 'bg-blue-50 text-blue-700 border-blue-200' : 'bg-red-50 text-red-700 border-red-200'}`}>
          {summary.dryRun ? 'Dry run' : 'Executed'}
        </span>
      </div>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
        {rows.map(([label, value]) => (
          <div key={label} className="rounded-lg border border-slate-200 bg-slate-50 p-3">
            <p className="text-[10px] uppercase tracking-wide text-slate-500 font-semibold">{label}</p>
            <p className="text-lg font-bold text-slate-800">{value}</p>
          </div>
        ))}
      </div>
      {summary.candidateInstanceIds.length > 0 && (
        <p className="text-xs text-slate-500">
          Candidate instances: {summary.candidateInstanceIds.slice(0, 20).join(', ')}
          {summary.candidateInstanceIds.length > 20 ? ` and ${summary.candidateInstanceIds.length - 20} more` : ''}
        </p>
      )}
    </div>
  );
};

export const MaintenancePage: React.FC = () => {
  const [definitions, setDefinitions] = useState<ProcessDefinition[]>([]);
  const [loadingDefinitions, setLoadingDefinitions] = useState(false);
  const [completedBefore, setCompletedBefore] = useState('');
  const [purgeDefinitionId, setPurgeDefinitionId] = useState('');
  const [purgeSummary, setPurgeSummary] = useState<MaintenanceCleanupSummary | null>(null);
  const [deleteDefinitionId, setDeleteDefinitionId] = useState('');
  const [deleteSummary, setDeleteSummary] = useState<MaintenanceCleanupSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    setLoadingDefinitions(true);
    adminService.getProcessDefinitions(0, 200)
      .then((page) => setDefinitions(page.content))
      .catch(() => setMessage('Failed to load process definitions.'))
      .finally(() => setLoadingDefinitions(false));
  }, []);

  const previewPurge = async (execute = false) => {
    if (!completedBefore) {
      setMessage('Choose a completed-before date first.');
      return;
    }

    setLoading(true);
    setMessage(null);
    try {
      const summary = await adminService.purgeCompletedInstances({
        completedBefore,
        processDefinitionId: purgeDefinitionId ? Number(purgeDefinitionId) : null,
        dryRun: !execute
      });
      setPurgeSummary(summary);
      setMessage(execute ? 'Completed instances purged.' : 'Purge preview generated.');
    } catch (error) {
      console.error(error);
      setMessage('Purge operation failed.');
    } finally {
      setLoading(false);
    }
  };

  const previewDefinitionDelete = async (execute = false) => {
    if (!deleteDefinitionId) {
      setMessage('Choose a process definition first.');
      return;
    }

    setLoading(true);
    setMessage(null);
    try {
      const summary = await adminService.deleteProcessDefinitionCascade(Number(deleteDefinitionId), !execute);
      setDeleteSummary(summary);
      setMessage(execute ? 'Process definition deleted.' : 'Definition deletion preview generated.');
      if (execute) {
        const page = await adminService.getProcessDefinitions(0, 200);
        setDefinitions(page.content);
      }
    } catch (error) {
      console.error(error);
      setMessage('Definition deletion failed.');
    } finally {
      setLoading(false);
    }
  };

  const selectedDeleteDefinition = definitions.find((definition) => definition.id === Number(deleteDefinitionId));

  return (
    <div className="space-y-6">
      <header>
        <h2 className="text-2xl font-bold text-slate-800 flex items-center gap-2">
          <Database size={24} className="text-slate-700" /> Purge & Archiving
        </h2>
        <p className="text-slate-500 text-sm mt-1">Clean completed runtime data and remove process definitions with their related instances.</p>
      </header>

      {message && (
        <div className="rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-700">{message}</div>
      )}

      <section className="bg-white border border-slate-200 rounded-xl p-5 space-y-4">
        <div>
          <h3 className="font-semibold text-slate-800">Purge Completed Instances</h3>
          <p className="text-sm text-slate-500 mt-1">Deletes completed instances older than the selected date, including tasks, variables, documents, incidents, worker requests, and timeline events.</p>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <input
            type="datetime-local"
            value={completedBefore}
            onChange={(event) => setCompletedBefore(event.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
          />
          <select
            value={purgeDefinitionId}
            onChange={(event) => setPurgeDefinitionId(event.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm bg-white"
            disabled={loadingDefinitions}
          >
            <option value="">All process definitions</option>
            {definitions.map((definition) => (
              <option key={definition.id} value={definition.id}>
                #{definition.id} {definition.name} v{definition.version}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => previewPurge(false)}
            disabled={loading}
            className="px-3 py-2 rounded-lg bg-slate-900 text-white text-sm font-medium flex items-center gap-2 disabled:opacity-60"
          >
            {loading ? <Loader2 className="animate-spin" size={15} /> : <Search size={15} />} Preview
          </button>
          <button
            onClick={() => previewPurge(true)}
            disabled={loading || !purgeSummary || purgeSummary.processInstancesDeleted === 0}
            className="px-3 py-2 rounded-lg bg-red-600 text-white text-sm font-medium flex items-center gap-2 disabled:opacity-50"
          >
            <Trash2 size={15} /> Execute Purge
          </button>
        </div>
        {purgeSummary && <SummaryGrid summary={purgeSummary} />}
      </section>

      <section className="bg-white border border-red-200 rounded-xl p-5 space-y-4">
        <div>
          <h3 className="font-semibold text-slate-800 flex items-center gap-2">
            <AlertTriangle size={18} className="text-red-600" /> Delete Process Definition
          </h3>
          <p className="text-sm text-slate-500 mt-1">Deletes the selected definition and all related instances, tasks, variables, documents, incidents, worker requests, and timeline events.</p>
        </div>
        <select
          value={deleteDefinitionId}
          onChange={(event) => setDeleteDefinitionId(event.target.value)}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm bg-white"
          disabled={loadingDefinitions}
        >
          <option value="">Choose a process definition</option>
          {definitions.map((definition) => (
            <option key={definition.id} value={definition.id}>
              #{definition.id} {definition.name} v{definition.version}
            </option>
          ))}
        </select>
        {selectedDeleteDefinition && (
          <p className="text-xs text-red-600">Selected: {selectedDeleteDefinition.name} v{selectedDeleteDefinition.version}</p>
        )}
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => previewDefinitionDelete(false)}
            disabled={loading || !deleteDefinitionId}
            className="px-3 py-2 rounded-lg bg-slate-900 text-white text-sm font-medium flex items-center gap-2 disabled:opacity-60"
          >
            {loading ? <Loader2 className="animate-spin" size={15} /> : <Search size={15} />} Preview Delete
          </button>
          <button
            onClick={() => previewDefinitionDelete(true)}
            disabled={loading || !deleteSummary || !deleteDefinitionId}
            className="px-3 py-2 rounded-lg bg-red-600 text-white text-sm font-medium flex items-center gap-2 disabled:opacity-50"
          >
            <Trash2 size={15} /> Delete Definition
          </button>
        </div>
        {deleteSummary && <SummaryGrid summary={deleteSummary} />}
      </section>
    </div>
  );
};
