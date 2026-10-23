import React, { useEffect, useRef, useState } from 'react';
import type { DecisionDefinition, DecisionIssue, DecisionTable, DecisionTestCase, DecisionTestResult, DecisionType, EvaluationResult } from '../decisionTypes';
import { decisionService } from '../services/decisionService';
import { decisionToDmnXml, dmnXmlToDecision, newDecision } from '../utils/dmnXml';
import { openTextFile, saveTextFile } from '../services/desktopBridge';
import { isDesktopModeler } from '../config/runtimeConfig';
import { processService } from '../services/processService';
import { decisionExamples, type DecisionExample } from '../utils/decisionExamples';
import { ArrowLeft, Table2, Plus, Download, CheckCircle2, Rocket, FlaskConical } from 'lucide-react';
import { ThemeToggle, type ThemeMode } from './ThemeToggle';
import './DecisionModeler.css';

const types: DecisionType[] = ['string', 'number', 'boolean', 'date', 'json'];
const field = 'decision-field';
const button = 'decision-button';
const objectInput = (text: string): Record<string, unknown> => { const value = JSON.parse(text); if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error('Sample inputs must be a JSON object.'); return value; };

export const DecisionModeler: React.FC<{ onBack: () => void; onConnect?: () => void; theme?: ThemeMode; onToggleTheme?: () => void; initialDefinition?: DecisionDefinition }> = ({ onBack, onConnect, theme = 'light', onToggleTheme, initialDefinition }) => {
  const [table, setTable] = useState<DecisionTable>(newDecision);
  const [versions, setVersions] = useState<DecisionDefinition[]>([]);
  const [published, setPublished] = useState<DecisionDefinition | null>(null);
  const [issues, setIssues] = useState<DecisionIssue[]>([]);
  const [result, setResult] = useState<EvaluationResult | null>(null);
  const [inputs, setInputs] = useState('{}');
  const [expected, setExpected] = useState('null');
  const [testName, setTestName] = useState('');
  const [showExamples, setShowExamples] = useState(false);
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
  const load = async (definition: DecisionDefinition) => {
    if (dirty && !window.confirm('Discard unsaved decision changes?')) return;
    const next = dmnXmlToDecision(definition.dmnXml);
    setTable(next); setPublished(definition); setDirty(false); setResult(null); setIssues([]); setTestResults([]);
    const [history, saved] = await Promise.all([decisionService.versions(definition.key), decisionService.tests(definition.key)]);
    setVersions(history); setTests(saved); setStatus(`Opened ${definition.key} v${definition.version}. Editing creates a new version.`);
  };
  useEffect(() => { if (initialDefinition && connected) void run(() => load(initialDefinition)); }, [initialDefinition, connected]);
  const reset = () => {
    setTable(newDecision()); setPublished(null); setDirty(false); setTests([]); setVersions([]); setResult(null); setIssues([]);
    setInputs('{}'); setExpected('null'); setTestName(''); setTestResults([]); setError(''); setStatus(''); setShowExamples(false);
  };
  const loadExample = (example: DecisionExample) => {
    if (dirty && !window.confirm('Replace unsaved decision changes with this example?')) return;
    reset(); setTable(structuredClone(example.table)); setInputs(example.inputs); setExpected(example.expected); setTestName(example.testName); setDirty(true);
    setStatus(`${example.title} example loaded. Edit and test it before publishing.`);
  };
  const importXml = (xml: string) => { const next = dmnXmlToDecision(xml); if (dirty && !window.confirm('Replace unsaved decision changes?')) return; setTable(next); setDirty(true); setPublished(null); setVersions([]); setTests([]); setResult(null); setIssues([]); setStatus('DMN imported. Validate before deployment.'); };
  const addInput = () => { const id = `input_${Date.now()}`; update({ ...table, inputs: [...table.inputs, { id, label: 'Input', expression: 'input', type: 'string' }], rules: table.rules.map(r => ({ ...r, inputEntries: [...r.inputEntries, '-'] })) }); };
  const addOutput = () => { const id = `output_${Date.now()}`; update({ ...table, outputs: [...table.outputs, { id, name: id, type: 'string' }], rules: table.rules.map(r => ({ ...r, outputEntries: [...r.outputEntries, '""'] })) }); };
  const editColumn = (kind: 'inputs' | 'outputs', index: number, key: string, value: string) => update({ ...table, [kind]: table[kind].map((c, i) => i === index ? { ...c, [key]: value } : c) });
  const removeColumn = (kind: 'inputs' | 'outputs', index: number) => update({ ...table, [kind]: table[kind].filter((_, i) => i !== index), rules: table.rules.map(rule => ({ ...rule, [kind === 'inputs' ? 'inputEntries' : 'outputEntries']: rule[kind === 'inputs' ? 'inputEntries' : 'outputEntries'].filter((_, i) => i !== index) })) });
  const editCell = (row: number, kind: 'inputEntries' | 'outputEntries', col: number, value: string) => update({ ...table, rules: table.rules.map((rule, i) => i === row ? { ...rule, [kind]: rule[kind].map((entry, j) => j === col ? value : entry) } : rule) });
  const back = () => { if (!dirty || window.confirm('Leave with unsaved decision changes? Export DMN to keep a local copy.')) onBack(); };
  return <div className="process-modeler decision-workspace" data-theme={theme}>
    <header className="decision-topbar">
      <div className="decision-identity"><button className={`${button} decision-back`} aria-label="Back to workspace" title="Back to workspace" onClick={back}><ArrowLeft size={18} /></button><span className="decision-mark"><Table2 size={21} /></span><div><span className="decision-eyebrow">BUSINESS RULES</span><h1>Decision Tables</h1></div></div>
      <span className={`decision-state ${dirty ? 'is-dirty' : ''}`}>{dirty ? 'Unsaved changes' : published ? `Published · v${published.version}` : 'Draft decision'}</span>
      <div className="decision-top-actions">
      <button className={button} disabled={busy} onClick={() => { if (!dirty || window.confirm('Discard unsaved decision changes?')) reset(); }}>New decision</button>
      <button className={button} aria-expanded={showExamples} onClick={() => setShowExamples(value => !value)}>Examples</button>
      <button className={button} disabled={busy} onClick={() => isDesktopModeler() ? void run(async () => { const selected = await openTextFile([{ name: 'DMN XML', extensions: ['dmn', 'xml'] }]); if (selected) importXml(selected.content); }) : file.current?.click()}>Import DMN</button>
      <input ref={file} className="hidden" aria-label="Import DMN file" type="file" accept=".dmn,.xml" onChange={event => { const selected = event.target.files?.[0]; if (selected) void run(async () => importXml(await selected.text())); event.target.value = ''; }} />
      <button className={button} onClick={() => void run(async () => { if (await saveTextFile(decisionToDmnXml(table), `${table.key || 'decision'}.dmn`, [{ name: 'DMN XML', extensions: ['dmn', 'xml'] }])) setStatus('DMN exported.'); })}><Download size={15} />Export DMN</button>
      <button className={button} disabled={busy || !connected} onClick={() => void run(async () => { const validation = await decisionService.validate(decisionToDmnXml(table)); setIssues(validation.issues); setStatus(validation.valid ? 'Decision is valid.' : 'Correct validation errors before deployment.'); })}>Validate</button>
      <button className={`${button} decision-primary`} disabled={busy || !connected} onClick={() => void run(async () => { const xml = decisionToDmnXml(table); const validation = await decisionService.validate(xml); setIssues(validation.issues); if (!validation.valid) throw new Error('Correct validation errors before deployment.'); const deployed = await decisionService.deploy(xml); setPublished(deployed); setDirty(false); setVersions(await decisionService.versions(deployed.key)); setTests(await decisionService.tests(deployed.key)); setStatus(`Published ${deployed.key} v${deployed.version}. Existing versions are unchanged.`); })}><Rocket size={15} />Deploy new version</button>
      {onToggleTheme && <ThemeToggle theme={theme} onToggle={onToggleTheme} />}</div>
    </header>
    <main className="decision-main">
      {!connected && <p role="status">Edit and import/export locally. <button className={button} onClick={onConnect}>Connect to backend</button> to validate, test, or deploy.</p>}
      {busy && <p role="status">Working…</p>}{error && <p role="alert" className="rounded border border-red-500 bg-red-500/10 p-3">{error}</p>}{status && <p role="status" className="rounded border border-blue-500/40 p-3">{status}</p>}
      {showExamples && <section className="decision-examples" aria-label="Decision examples"><div className="decision-panel-heading"><div><h2>Learn with an example</h2><p>Examples are loaded only when you choose one. Your new decisions always start empty.</p></div><button className={button} onClick={() => setShowExamples(false)}>Close examples</button></div><div className="decision-example-cards">{decisionExamples.map(example => <article key={example.table.key}><span className="decision-eyebrow">{example.table.hitPolicy}</span><h3>{example.title}</h3><p>{example.description}</p><button className={button} aria-label={`Load ${example.title} example`} onClick={() => loadExample(example)}>Load example</button></article>)}</div></section>}
      {published && versions.length > 1 && <label className="decision-version-picker">Version of {published.key}<select aria-label="Decision version" className={field} value={published.version} disabled={busy} onChange={e => { const definition = versions.find(value => value.version === Number(e.target.value)); if (definition) void run(() => load(definition)); }}>{versions.map(value => <option key={value.id} value={value.version}>v{value.version}</option>)}</select></label>}
      <section className="decision-properties" aria-label="Decision properties">
        <div className="decision-document-title"><span className="decision-eyebrow">DECISION CONFIGURATION</span><h2>Define the decision</h2><p>Set the identity and how matching rules produce results.</p></div>
        <label>Decision key<input aria-label="Decision key" className={`${field} block`} value={table.key} onChange={e => { update({ ...table, key: e.target.value }); setPublished(null); setTests([]); setVersions([]); }} /></label>
        <label>Name<input aria-label="Decision name" className={`${field} block`} value={table.name} onChange={e => update({ ...table, name: e.target.value })} /></label>
        <label>Hit policy<select aria-label="Hit policy" className={`${field} block`} value={table.hitPolicy} onChange={e => update({ ...table, hitPolicy: e.target.value as DecisionTable['hitPolicy'] })}>{['UNIQUE', 'FIRST', 'COLLECT'].map(p => <option key={p}>{p}</option>)}</select></label>
      </section>
      <p className="decision-policy-note"><CheckCircle2 size={16} />{table.hitPolicy === 'UNIQUE' ? 'Unique match: exactly one rule can match. No match returns null.' : table.hitPolicy === 'FIRST' ? 'First match: returns the first matching rule, from top to bottom. No match returns null.' : 'Collect: returns every matching rule in table order. No match returns an empty list.'}</p>
      {issues.length > 0 && <ul aria-label="Validation issues" className="space-y-1">{issues.map((issue, i) => <li key={i} className={issue.severity.toUpperCase() === 'ERROR' ? 'text-red-500' : 'text-amber-500'}>{issue.severity}: {issue.message} {issue.ruleId && `(rule ${issue.ruleId})`}</li>)}</ul>}
      <div className="decision-editor-grid">
      <section className="decision-table-panel" aria-label="Decision table editor"><div className="decision-panel-heading"><div><h2><Table2 size={18} />Decision table <span className="decision-count">{table.rules.length} rules</span></h2><p>When inputs match, return the corresponding outputs.</p></div><div className="decision-panel-actions"><button className={button} onClick={addInput}>Add input</button><button className={button} onClick={addOutput}>Add output</button><button className={`${button} decision-subtle-primary`} onClick={() => update({ ...table, rules: [...table.rules, { id: `rule_${Date.now()}`, inputEntries: table.inputs.map(() => '-'), outputEntries: table.outputs.map(() => '') }] })}><Plus size={14} />Add rule</button></div></div>
        {table.inputs.length === 0 && table.outputs.length === 0 && table.rules.length === 0 && <div className="decision-empty-table"><span className="decision-empty-icon"><Table2 size={28} /></span><h3>Your decision starts here</h3><p>Add input and output columns, then define your first rule. Or explore a ready-to-edit example.</p><button className={button} onClick={() => setShowExamples(true)}>Browse examples</button></div>}
        <div className="overflow-x-auto rounded border border-[var(--modeler-border)]" hidden={table.inputs.length === 0 && table.outputs.length === 0 && table.rules.length === 0}><table className="w-full text-sm"><thead><tr><th className="p-3 min-w-40">Rule</th>{(['inputs', 'outputs'] as const).flatMap(kind => table[kind].map((column, i) => <th key={`${kind}-${i}`} className="min-w-48 space-y-2 p-3 border-l border-[var(--modeler-border)] text-left">
          <span className={`decision-column-kind ${kind}`}>{kind === 'inputs' ? 'IF · INPUT' : 'THEN · OUTPUT'} {i + 1}</span><button className="decision-remove-column" aria-label={`Remove ${kind === 'inputs' ? 'input' : 'output'} ${i + 1}`} onClick={() => removeColumn(kind, i)}>×</button>
          <input aria-label={`${kind} ${i + 1} ID`} className={`${field} block w-full`} value={column.id} onChange={e => editColumn(kind, i, 'id', e.target.value)} />
          <input aria-label={`${kind} ${i + 1} name`} className={`${field} block w-full`} value={'label' in column ? column.label : column.name} onChange={e => editColumn(kind, i, kind === 'inputs' ? 'label' : 'name', e.target.value)} />
          {'expression' in column && <input aria-label={`Input ${i + 1} expression`} className={`${field} block w-full`} value={column.expression} onChange={e => editColumn(kind, i, 'expression', e.target.value)} />}
          <select aria-label={`${kind} ${i + 1} type`} className={`${field} block w-full`} value={column.type} onChange={e => editColumn(kind, i, 'type', e.target.value)}>{types.map(t => <option key={t}>{t}</option>)}</select>
        </th>))}<th>Actions</th></tr></thead><tbody>{table.rules.map((rule, row) => <tr key={row} className="border-t border-[var(--modeler-border)]"><td className="p-2 space-y-1"><input aria-label={`Rule ${row + 1} ID`} className={`${field} w-full`} value={rule.id} onChange={e => update({ ...table, rules: table.rules.map((r, i) => i === row ? { ...r, id: e.target.value } : r) })} /><input aria-label={`Rule ${row + 1} description`} placeholder="Description" className={`${field} w-full`} value={rule.description || ''} onChange={e => update({ ...table, rules: table.rules.map((r, i) => i === row ? { ...r, description: e.target.value } : r) })} /></td>{(['inputEntries', 'outputEntries'] as const).flatMap(kind => rule[kind].map((entry, col) => <td key={`${kind}-${col}`} className="p-2 border-l border-[var(--modeler-border)]"><input aria-label={`Rule ${row + 1} ${kind === 'inputEntries' ? 'input' : 'output'} ${col + 1}`} className={`${field} w-full font-mono`} value={entry} onChange={e => editCell(row, kind, col, e.target.value)} /></td>))}<td className="p-2 whitespace-nowrap"><button aria-label={`Move rule ${row + 1} up`} disabled={row === 0} className={button} onClick={() => { const rules = [...table.rules]; [rules[row - 1], rules[row]] = [rules[row], rules[row - 1]]; update({ ...table, rules }); }}>↑</button><button aria-label={`Remove rule ${row + 1}`} className={button} onClick={() => update({ ...table, rules: table.rules.filter((_, i) => i !== row) })}>Remove</button></td></tr>)}</tbody></table></div>
        <p className="text-xs text-[var(--modeler-text-muted)]">Use - for any value; literals, comparisons, intervals and comma-separated alternatives for conditions. Quote strings. JSON outputs use JSON literals. Server validation reports unsupported expressions and overlaps.</p>
      </section>
      <section className="decision-playground" aria-label="Decision playground"><div className="decision-panel-heading"><div><h2><FlaskConical size={18} />Playground and saved tests</h2><p>Try sample data before publishing.</p></div></div><div className="decision-test-inputs"><label>Sample inputs (JSON object)<textarea aria-label="Sample inputs" className={`${field} block w-full min-h-28 font-mono`} value={inputs} onChange={e => setInputs(e.target.value)} /></label><label>Expected outputs (JSON)<textarea aria-label="Expected outputs" className={`${field} block w-full min-h-28 font-mono`} value={expected} onChange={e => setExpected(e.target.value)} /></label></div><div className="decision-test-actions"><button className={`${button} decision-primary`} disabled={busy || !connected} onClick={() => void run(async () => { objectInput(inputs); setResult(await decisionService.testJson(decisionToDmnXml(table), inputs)); })}><FlaskConical size={15} />Test decision</button><label>Test case name<input aria-label="Test case name" className={field} value={testName} onChange={e => setTestName(e.target.value)} /></label><button className={button} disabled={busy || !connected || !published || dirty} onClick={() => void run(async () => { if (!testName.trim()) throw new Error('Test case name is required.'); objectInput(inputs); JSON.parse(expected); await decisionService.saveTestJson(table.key, testName, inputs, expected); setTests(await decisionService.tests(table.key)); setStatus('Test case saved.'); })}>Save test case</button><button className={button} disabled={busy || !published || !connected || dirty} onClick={() => void run(async () => setTestResults(await decisionService.runTests(table.key, published!.version)))}>Run saved tests</button></div>
        {dirty && <p className="text-sm">Publish the draft before saving or running versioned test cases.</p>}
        {result && <div role="region" aria-label="Decision test result"><p className="font-semibold">Matched rules: {result.matchedRuleIds.join(', ') || 'None'} · {result.durationMs} ms</p><pre className="overflow-auto p-3">{JSON.stringify(result.outputs, null, 2)}</pre>{result.explanations.map(explanation => <details key={explanation.ruleId}><summary>{explanation.ruleId}: {explanation.matched ? 'Matched' : 'Did not match'}</summary><ul>{explanation.conditions.map((condition, i) => <li key={i}>{condition.inputId} {condition.expression}: {condition.matched ? 'true' : 'false'}</li>)}</ul></details>)}</div>}
        {tests.length === 0 && <p className="text-sm">No saved test cases.</p>}{tests.map(test => <button key={test.id} className={`${button} mr-2`} onClick={() => { setInputs(JSON.stringify(test.inputs, null, 2)); setExpected(JSON.stringify(test.expectedOutputs, null, 2)); setTestName(test.name); }}>{test.name}</button>)}
        {testResults.map(test => <div key={test.testCaseId} className="border-t border-[var(--modeler-border)] p-2"><strong>{test.name}: {test.passed ? 'PASS' : 'FAIL'}</strong>{!test.passed && <pre className="overflow-auto">{JSON.stringify({ expected: test.expectedOutputs, actual: test.actualOutputs, error: test.error, differences: test.differences }, null, 2)}</pre>}</div>)}
      </section>
      </div>
    </main>
  </div>;
};
