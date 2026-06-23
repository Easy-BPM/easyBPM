import React, { useEffect, useMemo, useState } from 'react';
import { AlertCircle, CheckCircle2, Loader2, RefreshCw, Search, UserRoundCheck, XCircle } from 'lucide-react';
import { adminService } from '../services/adminService';
import { BpmTask, TaskStatus } from '../types';

const PAGE_SIZE = 20;

const statusClass = (status: TaskStatus) =>
  status === 'PENDING'
    ? 'bg-amber-50 text-amber-700 border-amber-200'
    : 'bg-emerald-50 text-emerald-700 border-emerald-200';

export const TaskResourcesView: React.FC = () => {
  const [tasks, setTasks] = useState<BpmTask[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [status, setStatus] = useState<TaskStatus | ''>('PENDING');
  const [assigneeFilter, setAssigneeFilter] = useState('');
  const [draftAssignees, setDraftAssignees] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(false);
  const [savingTaskId, setSavingTaskId] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const filters = useMemo(() => ({
    page,
    size: PAGE_SIZE,
    status,
    assignee: assigneeFilter
  }), [assigneeFilter, page, status]);

  const loadTasks = async () => {
    setLoading(true);
    setMessage(null);
    try {
      const result = await adminService.getTasks(filters);
      setTasks(result.content);
      setTotalPages(Math.max(result.totalPages || 1, 1));
      setDraftAssignees(Object.fromEntries(result.content.map((task) => [task.id, task.assignee ?? ''])));
    } catch (error) {
      console.error(error);
      setMessage('Failed to load task resources.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTasks();
  }, [filters]);

  const handleReassign = async (task: BpmTask, clear = false) => {
    const nextAssignee = clear ? null : draftAssignees[task.id] ?? '';
    setSavingTaskId(task.id);
    setMessage(null);
    try {
      const updated = await adminService.reassignTask(task.id, nextAssignee);
      setTasks((current) => current.map((item) => item.id === updated.id ? updated : item));
      setDraftAssignees((current) => ({ ...current, [updated.id]: updated.assignee ?? '' }));
      setMessage(clear ? `Task #${task.id} returned to the shared pool.` : `Task #${task.id} reassigned successfully.`);
    } catch (error) {
      console.error(error);
      setMessage(`Failed to update task #${task.id}.`);
    } finally {
      setSavingTaskId(null);
    }
  };

  const handleApplyFilters = () => {
    setPage(0);
    loadTasks();
  };

  return (
    <div className="space-y-6">
      <header>
        <h2 className="text-2xl font-bold text-slate-800">Task Resources</h2>
        <p className="text-slate-500 text-sm">Review user-task ownership and reassign work when an operator needs intervention.</p>
      </header>

      <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm">
        <div className="grid grid-cols-1 md:grid-cols-[160px_1fr_auto] gap-3">
          <select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as TaskStatus | '');
              setPage(0);
            }}
            className="rounded-lg border border-slate-300 px-3 py-2.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
          >
            <option value="">All statuses</option>
            <option value="PENDING">Pending</option>
            <option value="COMPLETED">Completed</option>
          </select>

          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={15} />
            <input
              value={assigneeFilter}
              onChange={(event) => setAssigneeFilter(event.target.value)}
              onKeyDown={(event) => event.key === 'Enter' && handleApplyFilters()}
              placeholder="Filter by assignee"
              className="w-full pl-9 pr-4 rounded-lg border border-slate-300 py-2.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
            />
          </div>

          <button
            onClick={loadTasks}
            disabled={loading}
            className="inline-flex items-center justify-center gap-2 rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
          >
            {loading ? <Loader2 className="animate-spin" size={16} /> : <RefreshCw size={16} />}
            Refresh
          </button>
        </div>
      </div>

      {message && (
        <div className={`rounded-lg px-4 py-3 text-sm flex items-center gap-2 border ${
          message.includes('successfully') || message.includes('shared pool')
            ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
            : 'bg-red-50 text-red-800 border-red-200'
        }`}>
          {message.includes('successfully') || message.includes('shared pool') ? <CheckCircle2 size={15} /> : <AlertCircle size={15} />}
          {message}
        </div>
      )}

      <div className="bg-white border border-slate-200 rounded-xl shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200">
            <thead className="bg-slate-50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Task</th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Resource</th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Candidates</th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Status</th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">Reassign</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {loading && tasks.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-12 text-center text-sm text-slate-500">
                    <Loader2 className="mx-auto mb-2 animate-spin text-blue-600" size={22} />
                    Loading task resources...
                  </td>
                </tr>
              ) : tasks.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-12 text-center text-sm text-slate-500">No tasks found.</td>
                </tr>
              ) : (
                tasks.map((task) => {
                  const isCompleted = task.status === 'COMPLETED';
                  const isSaving = savingTaskId === task.id;
                  return (
                    <tr key={task.id} className="hover:bg-slate-50/70">
                      <td className="px-4 py-4 align-top">
                        <div className="font-semibold text-slate-800">{task.title || task.name || `Task #${task.id}`}</div>
                        <div className="mt-1 text-xs text-slate-500">
                          #{task.id} · Instance #{task.processInstanceId} · <span className="font-mono">{task.nodeId}</span>
                        </div>
                      </td>
                      <td className="px-4 py-4 align-top">
                        <div className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-sm text-slate-700">
                          <UserRoundCheck size={14} className="text-slate-500" />
                          {task.assignee || 'Unassigned'}
                        </div>
                      </td>
                      <td className="px-4 py-4 align-top">
                        <div className="space-y-1 text-xs text-slate-500">
                          <div>Users: {task.candidateUsers?.length ? task.candidateUsers.join(', ') : '-'}</div>
                          <div>Groups: {task.candidateGroups?.length ? task.candidateGroups.join(', ') : '-'}</div>
                        </div>
                      </td>
                      <td className="px-4 py-4 align-top">
                        <span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-semibold ${statusClass(task.status)}`}>
                          {task.status}
                        </span>
                      </td>
                      <td className="px-4 py-4 align-top">
                        <div className="flex min-w-[280px] gap-2">
                          <input
                            value={draftAssignees[task.id] ?? ''}
                            onChange={(event) => setDraftAssignees((current) => ({ ...current, [task.id]: event.target.value }))}
                            disabled={isCompleted || isSaving}
                            placeholder="username"
                            className="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-100 disabled:text-slate-400"
                          />
                          <button
                            onClick={() => handleReassign(task)}
                            disabled={isCompleted || isSaving}
                            className="rounded-lg bg-blue-600 px-3 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50"
                          >
                            {isSaving ? <Loader2 className="animate-spin" size={16} /> : 'Save'}
                          </button>
                          <button
                            onClick={() => handleReassign(task, true)}
                            disabled={isCompleted || isSaving}
                            title="Clear assignee"
                            className="rounded-lg border border-slate-300 px-2.5 py-2 text-slate-600 hover:bg-slate-100 disabled:opacity-50"
                          >
                            <XCircle size={16} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-sm text-slate-600">
          <span>Page {page + 1} of {totalPages}</span>
          <div className="flex gap-2">
            <button
              onClick={() => setPage((current) => Math.max(current - 1, 0))}
              disabled={page === 0 || loading}
              className="rounded-lg border border-slate-300 px-3 py-1.5 hover:bg-slate-50 disabled:opacity-50"
            >
              Previous
            </button>
            <button
              onClick={() => setPage((current) => Math.min(current + 1, totalPages - 1))}
              disabled={page >= totalPages - 1 || loading}
              className="rounded-lg border border-slate-300 px-3 py-1.5 hover:bg-slate-50 disabled:opacity-50"
            >
              Next
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
