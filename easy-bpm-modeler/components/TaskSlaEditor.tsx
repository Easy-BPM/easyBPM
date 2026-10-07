import React from 'react';
import type { TaskSla } from '../types';
import { validateSla } from '../utils/sla';

export function TaskSlaEditor({ value, onChange }: { value?: TaskSla; onChange: (value: TaskSla | undefined) => void }) {
  const input = 'w-full rounded border border-slate-300 bg-transparent p-2 text-xs';
  const update = (patch: Partial<TaskSla>) => onChange({ ...value!, ...patch });
  if (value !== undefined && (!value || typeof value !== 'object' || typeof value.duration !== 'string' ||
    (value.timeZone !== undefined && typeof value.timeZone !== 'string') ||
    (value.alerts !== undefined && (!Array.isArray(value.alerts) || value.alerts.some(a => !a || typeof a.id !== 'string' || typeof a.beforeDue !== 'string'))) ||
    (value.escalations !== undefined && (!Array.isArray(value.escalations) || value.escalations.some(a => !a || typeof a.id !== 'string' || typeof a.afterDue !== 'string' || typeof a.action !== 'string' || typeof a.target !== 'string'))))) {
    return <section aria-label="Task SLA"><p role="alert" className="text-xs text-red-600">Imported SLA has an invalid structure. Remove it to configure a valid policy.</p><button type="button" onClick={() => onChange(undefined)}>Remove invalid SLA</button></section>;
  }
  return <section className="space-y-3 mb-6" aria-label="Task SLA">
    <label className="flex items-center gap-2 text-xs font-semibold"><input type="checkbox" checked={value !== undefined} onChange={e => onChange(e.target.checked ? { duration: 'PT4H', alerts: [], escalations: [] } : undefined)} /> Task SLA</label>
    {value !== undefined && <>
      <label className="block text-xs">Duration<input className={input} value={value?.duration || ''} placeholder="PT4H or ${slaDuration}" onChange={e => update({ duration: e.target.value })} /></label>
      <label className="block text-xs">Timezone (empty uses server default)<input className={input} value={value?.timeZone || ''} placeholder="America/Sao_Paulo" onChange={e => update({ timeZone: e.target.value || undefined })} /></label>
      <p className="text-xs text-slate-500">Continuous elapsed time: P1D is 24 hours. Alerts must precede the deadline. Actions execute in the order shown.</p>
      <div className="space-y-2"><p className="text-xs font-semibold">Advance alerts</p>
        {(value?.alerts || []).map((alert, i) => <div key={i} className="space-y-1 rounded border p-2">
          <label className="block text-xs">Alert ID<input className={input} value={alert.id} onChange={e => update({ alerts: value.alerts!.map((a, index) => index === i ? { ...a, id: e.target.value } : a) })} /></label>
          <label className="block text-xs">Before deadline<input className={input} value={alert.beforeDue} onChange={e => update({ alerts: value.alerts!.map((a, index) => index === i ? { ...a, beforeDue: e.target.value } : a) })} /></label>
          <button type="button" className="text-xs underline" onClick={() => update({ alerts: value.alerts!.filter((_, index) => index !== i) })}>Remove alert</button>
        </div>)}
        <button type="button" className="text-xs underline" onClick={() => update({ alerts: [...(value?.alerts || []), { id: `alert-${crypto.randomUUID()}`, beforeDue: 'PT30M' }] })}>Add alert</button>
      </div>
      <div className="space-y-2"><p className="text-xs font-semibold">Escalations</p>
        {(value?.escalations || []).map((action, i) => {
          const change = (patch: Partial<typeof action>) => update({ escalations: value.escalations!.map((a, index) => index === i ? { ...a, ...patch } : a) });
          return <div key={i} className="space-y-1 rounded border p-2">
            <label className="block text-xs">Action ID<input className={input} value={action.id} onChange={e => change({ id: e.target.value })} /></label>
            <label className="block text-xs">After deadline<input className={input} value={action.afterDue} onChange={e => change({ afterDue: e.target.value })} /></label>
            <label className="block text-xs">Action<select className={input} value={action.action} onChange={e => change({ action: e.target.value as typeof action.action })}><option value="REASSIGN">Reassign to user</option><option value="ADD_CANDIDATE_USER">Add candidate user</option><option value="ADD_CANDIDATE_GROUP">Add candidate group</option></select></label>
            <label className="block text-xs">Target<input className={input} value={action.target} placeholder="username/group or ${supervisor}" onChange={e => change({ target: e.target.value })} /></label>
            <button type="button" className="text-xs underline" onClick={() => update({ escalations: value.escalations!.filter((_, index) => index !== i) })}>Remove escalation</button>
          </div>;
        })}
        <button type="button" className="text-xs underline" onClick={() => update({ escalations: [...(value?.escalations || []), { id: `escalation-${crypto.randomUUID()}`, afterDue: 'PT0S', action: 'REASSIGN', target: '' }] })}>Add escalation</button>
      </div>
      {validateSla(value).length > 0 && <ul role="alert" className="text-xs text-red-600">{validateSla(value).map(error => <li key={error}>{error}</li>)}</ul>}
    </>}
  </section>;
}
