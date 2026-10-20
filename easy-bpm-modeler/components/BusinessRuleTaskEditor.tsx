import React from 'react';
import type { NodeData } from '../types';
import type { DecisionType } from '../decisionTypes';

const field = 'w-full rounded border border-[var(--modeler-border)] bg-[var(--modeler-input-bg)] px-2 py-1.5 text-xs text-[var(--modeler-text)]';
const types: DecisionType[] = ['string', 'number', 'boolean', 'date', 'json'];
const decimalIdentity = (value: string): string => {
  const match = value.match(/^(-?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/);
  if (!match) return value;
  let digits = (match[2] + (match[3] || '')).replace(/^0+/, '');
  if (!digits) return '0';
  let power = BigInt(match[4] || '0') - BigInt((match[3] || '').length);
  while (digits.endsWith('0')) { digits = digits.slice(0, -1); power++; }
  return `${match[1]}${digits}e${power}`;
};
const staticValue = (value: string, type: DecisionType): { value: unknown; error?: string } => {
  if (type === 'string' || type === 'date') return { value };
  try {
    const parsed = JSON.parse(value);
    if (type === 'number' && typeof parsed === 'number' && decimalIdentity(value.trim()) !== decimalIdentity(String(parsed))) return { value: undefined, error: 'Numbers must round-trip exactly; use a process variable for higher precision.' };
    if (type === 'json') {
      // Compare number tokens outside JSON strings before accepting JS's parsed value.
      const tokens = value.match(/"(?:[^"\\]|\\.)*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g) || [];
      if (tokens.some(token => !token.startsWith('"') && (!Number.isFinite(Number(token)) || decimalIdentity(token) !== decimalIdentity(String(Number(token)))))) return { value: undefined, error: 'JSON numbers must round-trip exactly; use a process variable for higher precision.' };
    }
    return { value: parsed };
  } catch { return { value: undefined, error: 'Enter a valid typed JSON literal.' }; }
};
export const decisionMappingErrors = (data: NodeData): string[] => {
  const errors: string[] = [];
  for (const draft of Object.values(data.decisionInputDrafts || {})) if (draft.error) errors.push(draft.error);
  if (!/^[A-Za-z_][A-Za-z0-9_.-]{0,127}$/.test(data.decisionKey || '')) errors.push('Decision key is required (maximum 128 characters; letters, numbers, underscore, dot, hyphen).');
  if (data.decisionVersion !== undefined && (!Number.isSafeInteger(data.decisionVersion) || data.decisionVersion < 1)) errors.push('Exact version must be a positive integer.');
  const inputs = data.decisionInputs || [], outputs = data.decisionOutputs || [];
  if (inputs.length > 32 || outputs.length > 32) errors.push('At most 32 input and output mappings are supported.');
  if (new Set(inputs.map(i => i.targetName)).size !== inputs.length || new Set(outputs.map(o => o.value)).size !== outputs.length) errors.push('Duplicate input names or output targets are not allowed.');
  for (const input of inputs) {
    if (!input.targetName.trim() || (input.source === 'variable' && (typeof input.value !== 'string' || !input.value.trim()))) errors.push('Each input needs a decision input name and source.');
    if (input.source === 'static' && ((input.type === 'number' && (typeof input.value !== 'number' || !Number.isFinite(input.value))) || (input.type === 'boolean' && typeof input.value !== 'boolean') || (input.type === 'date' && (typeof input.value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(input.value))))) errors.push(`Static input ${input.targetName || '(unnamed)'} must match its type. Numbers must round-trip exactly; use a process variable for higher precision.`);
    if (input.source === 'static' && input.type === 'json' && input.value === undefined) errors.push('Static JSON input requires a valid literal.');
  }
  for (const output of outputs) if (!output.sourceName.trim() || !output.value.trim()) errors.push('Each output needs an output name and target process variable.');
  return errors;
};
export const BusinessRuleTaskEditor: React.FC<{ data: NodeData; onChange: (data: Partial<NodeData>) => void }> = ({ data, onChange }) => {
  const inputs = data.decisionInputs || [], outputs = data.decisionOutputs || [];
  const errors = decisionMappingErrors(data);
  const displayedValue = (input: typeof inputs[number]) => input.source === 'static' && input.type === 'json' ? JSON.stringify(input.value) ?? '' : typeof input.value === 'string' ? input.value : JSON.stringify(input.value) ?? '';
  const editValue = (i: number, text: string, type = inputs[i].type, source = inputs[i].source) => {
    const parsed = source === 'static' ? staticValue(text, type) : { value: text };
    onChange({ decisionInputs: inputs.map((v, j) => i === j ? { ...v, source, type, value: parsed.value } : v), decisionInputDrafts: { ...data.decisionInputDrafts, [i]: { text, error: parsed.error } } });
  };
  return <section className="space-y-4 p-4 border-t border-[var(--modeler-border)]" aria-label="Business rule task configuration">
    <h3 className="font-semibold text-sm">Business Rule Task</h3>
    <label className="block text-xs">Decision key<input aria-label="Task decision key" className={field} value={data.decisionKey || ''} onChange={e => onChange({ decisionKey: e.target.value })} /></label>
    <label className="block text-xs">Exact version (optional)<input aria-label="Exact decision version" className={field} type="number" min="1" step="1" value={data.decisionVersion ?? ''} onChange={e => onChange({ decisionVersion: e.target.value === '' ? undefined : Number(e.target.value) })} /></label>
    <p className="text-xs text-[var(--modeler-text-muted)]">Leave version empty to select latest on first task execution. That version stays pinned across retries and re-entry.</p>
    <div className="flex justify-between"><h4 className="text-xs font-semibold">Decision inputs</h4><button type="button" className="text-xs text-blue-500" onClick={() => onChange({ decisionInputs: [...inputs, { targetName: '', source: 'variable', value: '', type: 'string' }] })}>Add decision input</button></div>
    {inputs.map((input, i) => <div key={i} className="space-y-2 rounded border border-[var(--modeler-border)] p-2">
      <label className="block text-xs">Decision input<input aria-label={`Decision input ${i + 1} name`} className={field} value={input.targetName} onChange={e => onChange({ decisionInputs: inputs.map((v, j) => i === j ? { ...v, targetName: e.target.value } : v) })} /></label>
      <select aria-label={`Decision input ${i + 1} source`} className={field} value={input.source} onChange={e => editValue(i, '', input.type, e.target.value as 'variable' | 'static')}><option value="variable">Process variable / path</option><option value="static">Static value</option></select>
      <select aria-label={`Decision input ${i + 1} type`} className={field} value={input.type} onChange={e => editValue(i, data.decisionInputDrafts?.[i]?.text ?? displayedValue(input), e.target.value as DecisionType)}>{types.map(t => <option key={t}>{t}</option>)}</select>
      <input aria-label={`Decision input ${i + 1} value`} placeholder={input.source === 'variable' ? 'customer.age' : 'Typed literal'} className={field} value={data.decisionInputDrafts?.[i]?.text ?? displayedValue(input)} onChange={e => editValue(i, e.target.value)} />
      <button className="text-xs text-red-500" onClick={() => onChange({ decisionInputs: inputs.filter((_, j) => i !== j), decisionInputDrafts: Object.fromEntries(Object.entries(data.decisionInputDrafts || {}).filter(([index]) => Number(index) !== i).map(([index, draft]) => [Number(index) > i ? Number(index) - 1 : Number(index), draft])) })}>Remove input mapping {i + 1}</button>
    </div>)}
    <div className="flex justify-between"><h4 className="text-xs font-semibold">Decision outputs</h4><button type="button" className="text-xs text-blue-500" onClick={() => onChange({ decisionOutputs: [...outputs, { sourceName: '', target: 'variable', value: '', type: 'string' }] })}>Add decision output</button></div>
    {outputs.map((output, i) => <div key={i} className="space-y-2 rounded border border-[var(--modeler-border)] p-2">
      <input aria-label={`Decision output ${i + 1} name`} placeholder="Decision output name (or $result)" className={field} value={output.sourceName} onChange={e => onChange({ decisionOutputs: outputs.map((v, j) => i === j ? { ...v, sourceName: e.target.value } : v) })} />
      <input aria-label={`Decision output ${i + 1} variable`} placeholder="Target process variable" className={field} value={output.value} onChange={e => onChange({ decisionOutputs: outputs.map((v, j) => i === j ? { ...v, value: e.target.value } : v) })} />
      <select aria-label={`Decision output ${i + 1} type`} className={field} value={output.type} onChange={e => onChange({ decisionOutputs: outputs.map((v, j) => i === j ? { ...v, type: e.target.value as DecisionType } : v) })}>{types.map(t => <option key={t}>{t}</option>)}</select>
      <button className="text-xs text-red-500" onClick={() => onChange({ decisionOutputs: outputs.filter((_, j) => i !== j) })}>Remove output mapping {i + 1}</button>
    </div>)}
    <p className="text-xs text-[var(--modeler-text-muted)]">COLLECT maps named outputs to ordered arrays; choose each element's type. Use $result with JSON type for the whole result.</p>
    {errors.length > 0 && <ul role="alert" className="text-xs text-red-500">{errors.map((error, i) => <li key={i}>{error}</li>)}</ul>}
  </section>;
};
