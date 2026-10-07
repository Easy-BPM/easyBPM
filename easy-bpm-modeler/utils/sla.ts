export const variableReference = /^\$\{[A-Za-z_][A-Za-z0-9_.-]*\}$/;

export function durationSeconds(value: unknown): number | null {
  if (typeof value !== 'string') return null;
  const match = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d{1,9})?)S)?)?$/.exec(value);
  if (!match || !match.slice(1).some(Boolean) || value.endsWith('T')) return null;
  const seconds = Number(match[1] || 0) * 86400 + Number(match[2] || 0) * 3600 + Number(match[3] || 0) * 60 + Number(match[4] || 0);
  return Number.isFinite(seconds) && seconds <= 9223372036 ? seconds : null;
}

export function validateSla(policy: unknown, nodeType = 'user-task'): string[] {
  if (policy === undefined) return [];
  const errors: string[] = [];
  if (nodeType !== 'user-task') errors.push('config.sla is only supported on User Tasks.');
  if (!policy || typeof policy !== 'object' || Array.isArray(policy)) return [...errors, 'config.sla must be an object.'];
  try {
    if (JSON.stringify(policy).length > 32768) errors.push('config.sla: serialized policy cannot exceed 32768 characters.');
  } catch {
    errors.push('config.sla: policy must be serializable JSON.');
  }
  const sla = policy as Record<string, unknown>;
  const duration = durationSeconds(sla.duration);
  if (!(typeof sla.duration === 'string' && variableReference.test(sla.duration)) && (duration === null || duration <= 0)) errors.push('config.sla.duration: use a positive ISO duration (PT4H) or ${variableName}.');
  if (sla.timeZone !== undefined) {
    try {
      if (typeof sla.timeZone !== 'string' || !sla.timeZone.trim()) throw new Error();
      new Intl.DateTimeFormat('en', { timeZone: sla.timeZone });
    } catch { errors.push('config.sla.timeZone: use a valid IANA timezone.'); }
  }
  const ids = new Set<string>();
  for (const field of ['alerts', 'escalations']) {
    if (sla[field] === undefined) continue;
    if (!Array.isArray(sla[field]) || sla[field].length > 100) { errors.push(`config.sla.${field}: use an array with at most 100 actions.`); continue; }
    sla[field].forEach((item: unknown, index: number) => {
      const path = `config.sla.${field}[${index}]`;
      if (!item || typeof item !== 'object' || Array.isArray(item)) { errors.push(`${path}: action must be an object.`); return; }
      const action = item as Record<string, unknown>;
      if (typeof action.id !== 'string' || !action.id.trim() || action.id.length > 100 || action.id.startsWith('__sla_') || ids.has(action.id)) errors.push(`${path}.id: use a unique nonblank ID (maximum 100 characters; __sla_ prefix is reserved).`);
      else ids.add(action.id);
      const offset = durationSeconds(action[field === 'alerts' ? 'beforeDue' : 'afterDue']);
      if (offset === null || (field === 'alerts' ? offset <= 0 || (duration !== null && offset >= duration) : offset < 0)) errors.push(`${path}: invalid ${field === 'alerts' ? 'beforeDue (positive and shorter than SLA)' : 'afterDue (zero or positive)'}.`);
      if (field === 'escalations') {
        if (!['REASSIGN', 'ADD_CANDIDATE_USER', 'ADD_CANDIDATE_GROUP'].includes(String(action.action))) errors.push(`${path}.action: unsupported escalation action.`);
        if (typeof action.target !== 'string' || !action.target.trim() || action.target.length > 255 || (action.target.includes('${') && !variableReference.test(action.target))) errors.push(`${path}.target: use a local username/group code or a single ${'${variableName}'} reference (maximum 255 characters).`);
      }
    });
  }
  return errors;
}
