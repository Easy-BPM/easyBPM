import React, { useEffect, useState } from 'react';
import { AlertTriangle, CalendarClock, Database, Loader2, Save, Search, Trash2 } from 'lucide-react';
import { adminService } from '../services/adminService';
import { DataRetentionSettings, MaintenanceCleanupSummary, ProcessDefinition } from '../types';

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
      {summary.candidateTaskIds.length > 0 && (
        <p className="text-xs text-slate-500">
          Candidate tasks: {summary.candidateTaskIds.slice(0, 20).join(', ')}
          {summary.candidateTaskIds.length > 20 ? ` and ${summary.candidateTaskIds.length - 20} more` : ''}
        </p>
      )}
    </div>
  );
};

export const MaintenancePage: React.FC = () => {
  const [definitions, setDefinitions] = useState<ProcessDefinition[]>([]);
  const [loadingDefinitions, setLoadingDefinitions] = useState(false);
  const [completedBefore, setCompletedBefore] = useState('');
  const [purgeBatchSize, setPurgeBatchSize] = useState('500');
  const [purgeDefinitionId, setPurgeDefinitionId] = useState('');
  const [purgeSummary, setPurgeSummary] = useState<MaintenanceCleanupSummary | null>(null);
  const [retentionSettings, setRetentionSettings] = useState<DataRetentionSettings | null>(null);
  const [retentionForm, setRetentionForm] = useState<DataRetentionSettings>({
    enabled: false,
    completedProcessRetentionDays: 90,
    completedTaskRetentionDays: 90,
    batchSize: 500,
    cron: '0 0 3 * * *'
  });
  const [retentionSummary, setRetentionSummary] = useState<MaintenanceCleanupSummary | null>(null);
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

    adminService.getRetentionSettings()
      .then((settings) => {
        setRetentionSettings(settings);
        setRetentionForm(settings);
        setPurgeBatchSize(String(settings.batchSize));
      })
      .catch(() => setMessage('Failed to load retention settings.'));
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
        batchSize: purgeBatchSize ? Number(purgeBatchSize) : null,
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

  const runConfiguredRetention = async (execute = false) => {
    setLoading(true);
    setMessage(null);
    try {
      const summary = execute
        ? await adminService.runConfiguredRetention()
        : await adminService.previewConfiguredRetention();
      setRetentionSummary(summary);
      setMessage(execute ? 'Configured retention executed.' : 'Configured retention preview generated.');
    } catch (error) {
      console.error(error);
      setMessage(error instanceof Error ? error.message : 'Configured retention failed.');
    } finally {
      setLoading(false);
    }
  };

  const saveRetentionSettings = async () => {
    setLoading(true);
    setMessage(null);
    try {
      const settings = await adminService.updateRetentionSettings({
        enabled: retentionForm.enabled,
        completedProcessRetentionDays: Number(retentionForm.completedProcessRetentionDays),
        completedTaskRetentionDays: Number(retentionForm.completedTaskRetentionDays),
        batchSize: Number(retentionForm.batchSize),
        cron: retentionForm.cron.trim()
      });
      setRetentionSettings(settings);
      setRetentionForm(settings);
      setPurgeBatchSize(String(settings.batchSize));
      setRetentionSummary(null);
      setMessage('Retention settings saved.');
    } catch (error) {
      console.error(error);
      setMessage('Failed to save retention settings.');
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
        <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-3">
          <div>
            <h3 className="font-semibold text-slate-800 flex items-center gap-2">
              <CalendarClock size={18} className="text-slate-700" /> Data Retention Policy
            </h3>
            <p className="text-sm text-slate-500 mt-1">Scheduled cleanup for completed process instances and completed tasks.</p>
          </div>
          {retentionSettings && (
            <span className={`text-xs px-2 py-1 rounded-full border w-fit ${retentionSettings.enabled ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-slate-50 text-slate-600 border-slate-200'}`}>
              {retentionSettings.enabled ? 'Enabled' : 'Disabled'}
            </span>
          )}
        </div>
        {retentionSettings && (
          <div className="grid grid-cols-1 md:grid-cols-4 gap-2">
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
              <p className="text-[10px] uppercase tracking-wide text-slate-500 font-semibold">Completed process TTL</p>
              <p className="text-lg font-bold text-slate-800">{retentionSettings.completedProcessRetentionDays} days</p>
            </div>
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
              <p className="text-[10px] uppercase tracking-wide text-slate-500 font-semibold">Completed task TTL</p>
              <p className="text-lg font-bold text-slate-800">{retentionSettings.completedTaskRetentionDays} days</p>
            </div>
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
              <p className="text-[10px] uppercase tracking-wide text-slate-500 font-semibold">Batch size</p>
              <p className="text-lg font-bold text-slate-800">{retentionSettings.batchSize}</p>
            </div>
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
              <p className="text-[10px] uppercase tracking-wide text-slate-500 font-semibold">Cron</p>
              <p className="text-sm font-semibold text-slate-800 break-words">{retentionSettings.cron}</p>
            </div>
          </div>
        )}
        <div className="grid grid-cols-1 md:grid-cols-5 gap-3 items-end">
          <label className="flex items-center gap-2 rounded-lg border border-slate-300 px-3 py-2 text-sm min-h-[38px]">
            <input
              type="checkbox"
              checked={retentionForm.enabled}
              onChange={(event) => setRetentionForm((current) => ({ ...current, enabled: event.target.checked }))}
              className="h-4 w-4"
            />
            <span className="font-medium text-slate-700">Enabled</span>
          </label>
          <label className="space-y-1">
            <span className="text-xs font-semibold text-slate-600">Completed process TTL days</span>
            <input
              type="number"
              min="1"
              max="3650"
              value={retentionForm.completedProcessRetentionDays}
              onChange={(event) => setRetentionForm((current) => ({ ...current, completedProcessRetentionDays: Number(event.target.value) }))}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
          </label>
          <label className="space-y-1">
            <span className="text-xs font-semibold text-slate-600">Completed task TTL days</span>
            <input
              type="number"
              min="1"
              max="3650"
              value={retentionForm.completedTaskRetentionDays}
              onChange={(event) => setRetentionForm((current) => ({ ...current, completedTaskRetentionDays: Number(event.target.value) }))}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
          </label>
          <label className="space-y-1">
            <span className="text-xs font-semibold text-slate-600">Batch size</span>
            <input
              type="number"
              min="1"
              max="10000"
              value={retentionForm.batchSize}
              onChange={(event) => setRetentionForm((current) => ({ ...current, batchSize: Number(event.target.value) }))}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
          </label>
          <label className="space-y-1">
            <span className="text-xs font-semibold text-slate-600">Cron</span>
            <input
              type="text"
              value={retentionForm.cron}
              onChange={(event) => setRetentionForm((current) => ({ ...current, cron: event.target.value }))}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
          </label>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            onClick={saveRetentionSettings}
            disabled={loading}
            className="px-3 py-2 rounded-lg bg-white border border-slate-300 text-slate-700 text-sm font-medium flex items-center gap-2 disabled:opacity-60"
          >
            {loading ? <Loader2 className="animate-spin" size={15} /> : <Save size={15} />} Save Settings
          </button>
          <button
            onClick={() => runConfiguredRetention(false)}
            disabled={loading}
            className="px-3 py-2 rounded-lg bg-slate-900 text-white text-sm font-medium flex items-center gap-2 disabled:opacity-60"
          >
            {loading ? <Loader2 className="animate-spin" size={15} /> : <Search size={15} />} Preview Policy
          </button>
          <button
            onClick={() => runConfiguredRetention(true)}
            disabled={loading || !retentionSettings?.enabled || !retentionSummary || (retentionSummary.processInstancesDeleted === 0 && retentionSummary.tasksDeleted === 0)}
            className="px-3 py-2 rounded-lg bg-red-600 text-white text-sm font-medium flex items-center gap-2 disabled:opacity-50"
          >
            <Trash2 size={15} /> Run Policy
          </button>
        </div>
        {retentionSummary && <SummaryGrid summary={retentionSummary} />}
      </section>

      <section className="bg-white border border-slate-200 rounded-xl p-5 space-y-4">
        <div>
          <h3 className="font-semibold text-slate-800">Purge Completed Instances</h3>
          <p className="text-sm text-slate-500 mt-1">Deletes completed instances older than the selected date, including tasks, variables, documents, incidents, worker requests, and timeline events.</p>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
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
          <input
            type="number"
            min="1"
            max="10000"
            value={purgeBatchSize}
            onChange={(event) => setPurgeBatchSize(event.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
            aria-label="Batch size"
          />
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
