import React, { useEffect, useRef, useState } from 'react';
import type { DecisionDefinition, DecisionIssue, DecisionTable, DecisionTestCase, DecisionTestResult, DecisionType, EvaluationResult } from '../decisionTypes';
import { decisionService } from '../services/decisionService';
import { decisionToDmnXml, dmnXmlToDecision, newDecision } from '../utils/dmnXml';
import { openTextFile, saveTextFile } from '../services/desktopBridge';
import { isDesktopModeler } from '../config/runtimeConfig';
import { processService } from '../services/processService';

const types: DecisionType[] = ['string', 'number', 'boolean', 'date', 'json'];
const field = 'rounded border border-[var(--modeler-border)] bg-[var(--modeler-input-bg)] px-2 py-1.5 text-sm text-[var(--modeler-text)]';
const button = 'rounded border border-[var(--modeler-border)] px-3 py-2 text-sm hover:bg-blue-500/15 disabled:opacity-40';
const objectInput = (text: string): Record<string, unknown> => { const value = JSON.parse(text); if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error('Sample inputs must be a JSON object.'); return value; };

export const DecisionModeler: React.FC<{ onBack: () => void; onConnect?: () => void }> = ({ onBack, onConnect }) => {
  const [table, setTable] = useState<DecisionTable>(newDecision);
  const [definitions, setDefinitions] = useState<DecisionDefinition[]>([]);
  const [versions, setVersions] = useState<DecisionDefinition[]>([]);
  const [published, setPublished] = useState<DecisionDefinition | null>(null);
  const [issues, setIssues] = useState<DecisionIssue[]>([]);
  const [result, setResult] = useState<EvaluationResult | null>(null);
  const [inputs, setInputs] = useState('{"age": 21}');
  const [expected, setExpected] = useState('{"eligible": true}');
  const [testName, setTestName] = useState('Adult applicant');
  const [tests, setTests] = useState<DecisionTestCase[]>([]);
  const [testResults, setTestResults] = useState<DecisionTestResult[]>([]);
  const [error, setError] = useState('');
  const [status, setStatus] = useState('');
  const [busy, setBusy] = useState(false);
  const [dirty, setDirty] = useState(false);
  const file = useRef<HTMLInputElement>(null);
  const connected = !isDesktopModeler() || !!processService.getSession()?.token;
  const update = (value: DecisionTable) => { setTable(value); setDirty(true); setIssues([]); setResult(null); setStatus(''); };
  const run = async (action: () => Promise<void>) => { setError(''); setStatus(''); setBusy(true); try { await action(); } catch (e) { setError(e instanceof Error ? e.message : 'Decision operation failed.'); } finally { setBusy(false); } };
  const refresh = async () => { setDefinitions(await decisionService.list()); };
  useEffect(() => { if (connected) void run(refresh); }, [connected]);
  const load = async (definition: DecisionDefinition) => {
    if (dirty && !window.confirm('Discard unsaved decision changes?')) return;
    const next = dmnXmlToDecision(definition.dmnXml);
    setTable(next); setPublished(definition); setDirty(false); setResult(null); setIssues([]); setTestResults([]);
    const [history, saved] = await Promise.all([decisionService.versions(definition.key), decisionService.tests(definition.key)]);
    setVersions(history); setTests(saved); setStatus(`Opened ${definition.key} v${definition.version}. Editing creates a new version.`);
  };
  const importXml = (xml: string) => { const next = dmnXmlToDecision(xml); if (dirty && !window.confirm('Replace unsaved decision changes?')) return; setTable(next); setDirty(true); setPublished(null); setVersions([]); setTests([]); setResult(null); setIssues([]); setStatus('DMN imported. Validate before deployment.'); };
  const addInput = () => { const id = `input_${Date.now()}`; update({ ...table, inputs: [...table.inputs, { id, label: 'Input', expression: 'input', type: 'string' }], rules: table.rules.map(r => ({ ...r, inputEntries: [...r.inputEntries, '-'] })) }); };
  const addOutput = () => { const id = `output_${Date.now()}`; update({ ...table, outputs: [...table.outputs, { id, name: id, type: 'string' }], rules: table.rules.map(r => ({ ...r, outputEntries: [...r.outputEntries, '""'] })) }); };
  const editColumn = (kind: 'inputs' | 'outputs', index: number, key: string, value: string) => update({ ...table, [kind]: table[kind].map((c, i) => i === index ? { ...c, [key]: value } : c) });
  const removeColumn = (kind: 'inputs' | 'outputs', index: number) => update({ ...table, [kind]: table[kind].filter((_, i) => i !== index), rules: table.rules.map(rule => ({ ...rule, [kind === 'inputs' ? 'inputEntries' : 'outputEntries']: rule[kind === 'inputs' ? 'inputEntries' : 'outputEntries'].filter((_, i) => i !== index) })) });
  const editCell = (row: number, kind: 'inputEntries' | 'outputEntries', col: number, value: string) => update({ ...table, rules: table.rules.map((rule, i) => i === row ? { ...rule, [kind]: rule[kind].map((entry, j) => j === col ? value : entry) } : rule) });
  const back = () => { if (!dirty || window.confirm('Leave with unsaved decision changes? Export DMN to keep a local copy.')) onBack(); };
  return <div className="h-screen overflow-auto bg-[var(--modeler-bg)] text-[var(--modeler-text)]">
    <header className="sticky top-0 z-10 flex flex-wrap items-center gap-3 border-b border-[var(--modeler-border)] bg-[var(--modeler-surface)] p-4">
      <button className={button} onClick={back}>Back to workspace</button><h1 className="text-lg font-semibold">Decision Tables</h1>{dirty && <span>Unsaved changes</span>}
      <button className={button} disabled={busy} onClick={() => { if (!dirty || window.confirm('Discard unsaved decision changes?')) { setTable(newDecision()); setPublished(null); setDirty(false); setTests([]); setVersions([]); setResult(null); setIssues([]); } }}>New decision</button>
      <button className={button} disabled={busy} onClick={() => isDesktopModeler() ? void run(async () => { const selected = await openTextFile([{ name: 'DMN XML', extensions: ['dmn', 'xml'] }]); if (selected) importXml(selected.content); }) : file.current?.click()}>Import DMN</button>
      <input ref={file} className="hidden" aria-label="Import DMN file" type="file" accept=".dmn,.xml" onChange={event => { const selected = event.target.files?.[0]; if (selected) void run(async () => importXml(await selected.text())); event.target.value = ''; }} />
      <button className={button} onClick={() => void run(async () => { if (await saveTextFile(decisionToDmnXml(table), `${table.key || 'decision'}.dmn`, [{ name: 'DMN XML', extensions: ['dmn', 'xml'] }])) setStatus('DMN exported.'); })}>Export DMN</button>
      <button className={button} disabled={busy || !connected} onClick={() => void run(async () => { const validation = await decisionService.validate(decisionToDmnXml(table)); setIssues(validation.issues); setStatus(validation.valid ? 'Decision is valid.' : 'Correct validation errors before deployment.'); })}>Validate</button>
      <button className={`${button} bg-blue-600 text-white`} disabled={busy || !connected} onClick={() => void run(async () => { const xml = decisionToDmnXml(table); const validation = await decisionService.validate(xml); setIssues(validation.issues); if (!validation.valid) throw new Error('Correct validation errors before deployment.'); const deployed = await decisionService.deploy(xml); setPublished(deployed); setDirty(false); await refresh(); setVersions(await decisionService.versions(deployed.key)); setTests(await decisionService.tests(deployed.key)); setStatus(`Published ${deployed.key} v${deployed.version}. Existing versions are unchanged.`); })}>Deploy new version</button>
    </header>
    <main className="space-y-5 p-5">
      {!connected && <p role="status">Edit and import/export locally. <button className={button} onClick={onConnect}>Connect to backend</button> to validate, test, or deploy.</p>}
      {busy && <p role="status">Working…</p>}{error && <p role="alert" className="rounded border border-red-500 bg-red-500/10 p-3">{error}</p>}{status && <p role="status" className="rounded border border-blue-500/40 p-3">{status}</p>}
      {connected && <section className="flex flex-wrap gap-3 items-end" aria-label="Published decisions">
        <label>Published decision<select aria-label="Published decision" className={`${field} block`} value={published?.key || ''} disabled={busy} onChange={e => { const d = definitions.find(d => d.key === e.target.value); if (d) void run(() => load(d)); }}><option value="">Select decision</option>{definitions.map(d => <option key={d.id} value={d.key}>{d.name} ({d.key}) · v{d.version}</option>)}</select></label>
        {versions.length > 0 && <label>Version<select aria-label="Decision version" className={`${field} block`} value={published?.version || ''} disabled={busy} onChange={e => { const d = versions.find(d => d.version === Number(e.target.value)); if (d) void run(() => load(d)); }}>{versions.map(d => <option key={d.id} value={d.version}>v{d.version} · {d.state}</option>)}</select></label>}
        <button className={button} disabled={busy} onClick={() => void run(refresh)}>Refresh decisions</button>
      </section>}
      <section className="flex flex-wrap gap-4" aria-label="Decision properties">
        <label>Decision key<input aria-label="Decision key" className={`${field} block`} value={table.key} onChange={e => { update({ ...table, key: e.target.value }); setPublished(null); setTests([]); setVersions([]); }} /></label>
        <label>Name<input aria-label="Decision name" className={`${field} block`} value={table.name} onChange={e => update({ ...table, name: e.target.value })} /></label>
        <label>Hit policy<select aria-label="Hit policy" className={`${field} block`} value={table.hitPolicy} onChange={e => update({ ...table, hitPolicy: e.target.value as DecisionTable['hitPolicy'] })}>{['UNIQUE', 'FIRST', 'COLLECT'].map(p => <option key={p}>{p}</option>)}</select></label>
      </section>
      <p className="text-sm text-[var(--modeler-text-muted)]">UNIQUE allows one match. FIRST returns the first matching row. COLLECT returns all matching rows in order, without aggregation. No match returns null (UNIQUE/FIRST) or [] (COLLECT).</p>
      {issues.length > 0 && <ul aria-label="Validation issues" className="space-y-1">{issues.map((issue, i) => <li key={i} className={issue.severity.toUpperCase() === 'ERROR' ? 'text-red-500' : 'text-amber-500'}>{issue.severity}: {issue.message} {issue.ruleId && `(rule ${issue.ruleId})`}</li>)}</ul>}
      <section className="space-y-3" aria-label="Decision table editor"><div className="flex gap-2"><h2 className="text-base font-semibold mr-auto">Decision table</h2><button className={button} onClick={addInput}>Add input</button><button className={button} onClick={addOutput}>Add output</button><button className={button} onClick={() => update({ ...table, rules: [...table.rules, { id: `rule_${Date.now()}`, inputEntries: table.inputs.map(() => '-'), outputEntries: table.outputs.map(() => '') }] })}>Add rule</button></div>
        <div className="overflow-x-auto rounded border border-[var(--modeler-border)]"><table className="w-full text-sm"><thead><tr><th className="p-3 min-w-40">Rule</th>{(['inputs', 'outputs'] as const).flatMap(kind => table[kind].map((column, i) => <th key={`${kind}-${i}`} className="min-w-48 space-y-2 p-3 border-l border-[var(--modeler-border)] text-left">
          <span>{kind === 'inputs' ? 'Input' : 'Output'} {i + 1}</span><button className="float-right text-red-500" aria-label={`Remove ${kind === 'inputs' ? 'input' : 'output'} ${i + 1}`} onClick={() => removeColumn(kind, i)}>×</button>
          <input aria-label={`${kind} ${i + 1} ID`} className={`${field} block w-full`} value={column.id} onChange={e => editColumn(kind, i, 'id', e.target.value)} />
          <input aria-label={`${kind} ${i + 1} name`} className={`${field} block w-full`} value={'label' in column ? column.label : column.name} onChange={e => editColumn(kind, i, kind === 'inputs' ? 'label' : 'name', e.target.value)} />
          {'expression' in column && <input aria-label={`Input ${i + 1} expression`} className={`${field} block w-full`} value={column.expression} onChange={e => editColumn(kind, i, 'expression', e.target.value)} />}
          <select aria-label={`${kind} ${i + 1} type`} className={`${field} block w-full`} value={column.type} onChange={e => editColumn(kind, i, 'type', e.target.value)}>{types.map(t => <option key={t}>{t}</option>)}</select>
        </th>))}<th>Actions</th></tr></thead><tbody>{table.rules.map((rule, row) => <tr key={row} className="border-t border-[var(--modeler-border)]"><td className="p-2 space-y-1"><input aria-label={`Rule ${row + 1} ID`} className={`${field} w-full`} value={rule.id} onChange={e => update({ ...table, rules: table.rules.map((r, i) => i === row ? { ...r, id: e.target.value } : r) })} /><input aria-label={`Rule ${row + 1} description`} placeholder="Description" className={`${field} w-full`} value={rule.description || ''} onChange={e => update({ ...table, rules: table.rules.map((r, i) => i === row ? { ...r, description: e.target.value } : r) })} /></td>{(['inputEntries', 'outputEntries'] as const).flatMap(kind => rule[kind].map((entry, col) => <td key={`${kind}-${col}`} className="p-2 border-l border-[var(--modeler-border)]"><input aria-label={`Rule ${row + 1} ${kind === 'inputEntries' ? 'input' : 'output'} ${col + 1}`} className={`${field} w-full font-mono`} value={entry} onChange={e => editCell(row, kind, col, e.target.value)} /></td>))}<td className="p-2 whitespace-nowrap"><button aria-label={`Move rule ${row + 1} up`} disabled={row === 0} className={button} onClick={() => { const rules = [...table.rules]; [rules[row - 1], rules[row]] = [rules[row], rules[row - 1]]; update({ ...table, rules }); }}>↑</button><button aria-label={`Remove rule ${row + 1}`} className={button} onClick={() => update({ ...table, rules: table.rules.filter((_, i) => i !== row) })}>Remove</button></td></tr>)}</tbody></table></div>
        <p className="text-xs text-[var(--modeler-text-muted)]">Use - for any value; literals, comparisons, intervals and comma-separated alternatives for conditions. Quote strings. JSON outputs use JSON literals. Server validation reports unsupported expressions and overlaps.</p>
      </section>
      <section className="space-y-3 rounded border border-[var(--modeler-border)] p-4" aria-label="Decision playground"><h2 className="font-semibold">Playground and saved tests</h2><div className="grid gap-4 md:grid-cols-2"><label>Sample inputs (JSON object)<textarea aria-label="Sample inputs" className={`${field} block w-full min-h-28 font-mono`} value={inputs} onChange={e => setInputs(e.target.value)} /></label><label>Expected outputs (JSON)<textarea aria-label="Expected outputs" className={`${field} block w-full min-h-28 font-mono`} value={expected} onChange={e => setExpected(e.target.value)} /></label></div><div className="flex flex-wrap gap-2"><button className={button} disabled={busy || !connected} onClick={() => void run(async () => { objectInput(inputs); setResult(await decisionService.testJson(decisionToDmnXml(table), inputs)); })}>Test decision</button><input aria-label="Test case name" className={field} value={testName} onChange={e => setTestName(e.target.value)} /><button className={button} disabled={busy || !connected || !published || dirty} onClick={() => void run(async () => { if (!testName.trim()) throw new Error('Test case name is required.'); objectInput(inputs); JSON.parse(expected); await decisionService.saveTestJson(table.key, testName, inputs, expected); setTests(await decisionService.tests(table.key)); setStatus('Test case saved.'); })}>Save test case</button><button className={button} disabled={busy || !published || !connected || dirty} onClick={() => void run(async () => setTestResults(await decisionService.runTests(table.key, published!.version)))}>Run saved tests</button></div>
        {dirty && <p className="text-sm">Publish the draft before saving or running versioned test cases.</p>}
        {result && <div role="region" aria-label="Decision test result"><p className="font-semibold">Matched rules: {result.matchedRuleIds.join(', ') || 'None'} · {result.durationMs} ms</p><pre className="overflow-auto p-3">{JSON.stringify(result.outputs, null, 2)}</pre>{result.explanations.map(explanation => <details key={explanation.ruleId}><summary>{explanation.ruleId}: {explanation.matched ? 'Matched' : 'Did not match'}</summary><ul>{explanation.conditions.map((condition, i) => <li key={i}>{condition.inputId} {condition.expression}: {condition.matched ? 'true' : 'false'}</li>)}</ul></details>)}</div>}
        {tests.length === 0 && <p className="text-sm">No saved test cases.</p>}{tests.map(test => <button key={test.id} className={`${button} mr-2`} onClick={() => { setInputs(JSON.stringify(test.inputs, null, 2)); setExpected(JSON.stringify(test.expectedOutputs, null, 2)); setTestName(test.name); }}>{test.name}</button>)}
        {testResults.map(test => <div key={test.testCaseId} className="border-t border-[var(--modeler-border)] p-2"><strong>{test.name}: {test.passed ? 'PASS' : 'FAIL'}</strong>{!test.passed && <pre className="overflow-auto">{JSON.stringify({ expected: test.expectedOutputs, actual: test.actualOutputs, error: test.error, differences: test.differences }, null, 2)}</pre>}</div>)}
      </section>
    </main>
  </div>;
};
