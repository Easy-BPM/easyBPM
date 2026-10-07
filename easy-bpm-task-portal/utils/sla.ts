export function deadlineText(dueDate: string | null | undefined, now: number): string {
  if (!dueDate) return 'No SLA';
  const delta = new Date(dueDate).getTime() - now;
  if (!Number.isFinite(delta)) return 'Deadline unavailable';
  const minutes = Math.ceil(Math.abs(delta) / 60000);
  const text = minutes >= 60 ? `${Math.floor(minutes / 60)}h ${minutes % 60}m` : `${minutes}m`;
  return delta > 0 ? `${text} remaining` : `${text} overdue`;
}

export function slaLabel(status: string | null | undefined): string {
  return ({ ON_TRACK: 'On track', AT_RISK: 'At risk', OVERDUE: 'Overdue', COMPLETED: 'Completed' } as Record<string, string>)[status || ''] || 'No SLA';
}

// Refresh metadata only; form values and variables stay in their own state.
export function mergeSlaMetadata<T extends { id: number }>(current: T, incoming: T): T {
  return { ...current, ...Object.fromEntries(Object.entries(incoming).filter(([key]) => !['variables', 'description'].includes(key))) };
}

export function validOffsetDate(value: string): boolean {
  return /(?:Z|[+-]\d{2}:\d{2})$/.test(value) && Number.isFinite(Date.parse(value));
}
