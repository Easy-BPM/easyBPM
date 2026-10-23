import React, { useEffect, useState } from 'react';
import { Task } from '../types';
import { deadlineText, slaLabel } from '../utils/sla';

export function TaskSlaBadge({ task }: { task: Task }) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  const status = slaLabel(task.slaStatus);
  let due = task.dueDate || '';
  if (task.dueDate) {
    try { due = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short', timeZone: task.slaTimeZone || 'UTC' }).format(new Date(task.dueDate)); } catch { /* Display the API timestamp if the zone is unavailable. */ }
  }
  return <div className="text-xs mt-2 space-y-1">
    <span role="status" aria-live="polite" className={`font-semibold ${task.slaStatus === 'OVERDUE' ? 'text-red-700' : task.slaStatus === 'AT_RISK' ? 'text-amber-700' : 'text-slate-600'}`}>{task.slaStatus === 'OVERDUE' || task.slaStatus === 'AT_RISK' ? '⚠ ' : '◷ '}{status}</span>
    {task.dueDate && <><div>Due: <time dateTime={task.dueDate}>{due}</time> ({task.slaTimeZone || 'UTC'})</div><div>{task.slaStatus === 'COMPLETED' ? (task.completedWithinSla === true ? 'Completed within SLA' : task.completedWithinSla === false ? 'Completed after deadline' : 'Completed') : deadlineText(task.dueDate, now)}</div></>}
  </div>;
}
