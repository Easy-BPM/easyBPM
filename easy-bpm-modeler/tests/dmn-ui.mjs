// Browser coverage for DMN authoring, real DOM XML compatibility and Admin operations.
// Run: NODE_PATH=<directory containing playwright> node tests/dmn-ui.mjs
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const { createServer } = await import(pathToFileURL(path.join(root, 'easy-bpm-modeler/node_modules/vite/dist/node/index.js')));
const browser = await chromium.launch({ headless: true, channel: process.env.DMN_BROWSER_CHANNEL || 'chrome' });
async function harness(app, code, run) {
  const server = await createServer({ configFile: false, root: path.join(root, app), server: { host: '127.0.0.1', port: 0 }, optimizeDeps: { include: ['react', 'react-dom/client'] } });
  server.middlewares.use('/__dmn-tests.html', async (_req, res) => { const html = await server.transformIndexHtml('/__dmn-tests.html', `<html><body><div id="root"></div><script type="module">import React from 'react'; import {createRoot} from 'react-dom/client'; ${code}</script></body></html>`); res.setHeader('Content-Type', 'text/html'); res.end(html); });
  server.middlewares.stack.unshift(server.middlewares.stack.pop());
  await server.listen(); const page = await browser.newPage(); page.setDefaultTimeout(12000);
  const errors = []; page.on('pageerror', e => errors.push(e.message));
  try { await run(page, `http://127.0.0.1:${server.httpServer.address().port}/__dmn-tests.html`); assert.deepEqual(errors, []); }
  catch (e) { console.error('Page errors:', errors, 'Rendered:', (await page.locator('body').innerText()).slice(0, 1800)); throw e; }
  finally { await page.close(); await server.close(); }
}
const definition = { id: 1, key: 'eligibility', name: 'Eligibility', version: 1, state: 'PUBLISHED', dmnXml: '', createdAt: '2026-10-20T12:00:00Z' };
const result = { decisionKey: 'eligibility', version: 1, hitPolicy: 'UNIQUE', matchedRuleIds: ['adult'], outputs: { eligible: true }, durationMs: 1, explanations: [{ ruleId: 'adult', matched: true, conditions: [{ inputId: 'age', expression: '>= 18', matched: true }] }] };
try {
  await harness('easy-bpm-modeler', `import {DecisionModeler} from '/components/DecisionModeler.tsx'; import * as dmn from '/utils/dmnXml.ts'; import * as bpmn from '/utils/bpmnXml.ts'; window.dmn=dmn;window.bpmn=bpmn; createRoot(document.getElementById('root')).render(React.createElement(DecisionModeler,{onBack:()=>{}}));`, async (page, url) => {
    const requests = []; let unavailable = false;
    await page.route('http://localhost:8080/**', async route => {
      const endpoint = new URL(route.request().url()).pathname;
      requests.push({ endpoint, body: route.request().postData() });
      if (unavailable) return route.fulfill({ status: 503, json: { message: 'Backend unavailable' } });
      if (endpoint === '/decisions' && route.request().method() === 'GET') return route.fulfill({ json: [] });
      if (endpoint === '/decisions/validate') return route.fulfill({ json: { valid: true, issues: [] } });
      if (endpoint === '/decisions/test') return route.fulfill({ json: result });
      if (endpoint === '/decisions' && route.request().method() === 'POST') { definition.dmnXml = route.request().postDataJSON().dmnXml; return route.fulfill({ json: definition }); }
      if (endpoint.endsWith('/versions')) return route.fulfill({ json: [definition] });
      if (endpoint.endsWith('/tests/run')) return route.fulfill({ json: { decisionKey: 'eligibility', version: 1, results: [{ testCaseId: 1, name: 'Adult applicant', passed: false, expectedOutputs: { eligible: false }, actualOutputs: { eligible: true }, differences: [{ path: 'eligible', expected: false, actual: true }] }] } });
      if (endpoint.endsWith('/tests') && route.request().method() === 'GET') return route.fulfill({ json: [{ id: 1, name: 'Adult applicant', inputs: { age: 21 }, expectedOutputs: { eligible: true } }] });
      if (endpoint.endsWith('/tests')) return route.fulfill({ json: { id: 1 } });
      return route.fulfill({ status: 404, json: { message: endpoint } });
    });
    await page.goto(url); await page.getByRole('heading', { name: 'Decision Tables', exact: true }).waitFor();
    await page.getByRole('button', { name: 'Add input', exact: true }).click(); await page.getByLabel('inputs 2 type').selectOption('date');
    await page.getByRole('button', { name: 'Add output', exact: true }).click(); await page.getByLabel('outputs 2 type').selectOption('json');
    await page.getByRole('button', { name: 'Add rule', exact: true }).click(); await page.getByLabel('Rule 3 input 1', { exact: true }).fill('>= 65');
    await page.getByRole('button', { name: 'Move rule 3 up', exact: true }).click();
    await page.getByRole('button', { name: 'Remove rule 2', exact: true }).click();
    await page.getByRole('button', { name: 'Remove input 2', exact: true }).click(); await page.getByRole('button', { name: 'Remove output 2', exact: true }).click();
    await page.getByLabel('Hit policy').selectOption('COLLECT'); await page.getByLabel('Hit policy').selectOption('UNIQUE');
    await page.getByRole('button', { name: 'Validate', exact: true }).click(); await page.getByText('Decision is valid.', { exact: true }).waitFor();
    await page.getByLabel('Sample inputs').fill('[]'); await page.getByRole('button', { name: 'Test decision', exact: true }).click(); assert.match(await page.getByRole('alert').innerText(), /JSON object/);
    await page.getByLabel('Sample inputs').fill('{"age":0.1000000000000000001}'); await page.getByRole('button', { name: 'Test decision', exact: true }).click(); await page.getByRole('region', { name: 'Decision test result' }).waitFor();
    assert.match(requests.findLast(r => r.endpoint === '/decisions/test').body, /0\.1000000000000000001/);
    await page.getByText('adult: Matched', { exact: true }).click(); await page.getByText('age >= 18: true', { exact: true }).waitFor();
    await page.getByRole('button', { name: 'Deploy new version', exact: true }).click(); await page.getByText(/Published eligibility v1/).waitFor();
    await page.getByLabel('Expected outputs').fill('{"rate":0.1000000000000000001}'); await page.getByRole('button', { name: 'Save test case', exact: true }).click(); await page.getByText('Test case saved.', { exact: true }).waitFor();
    assert.match(requests.findLast(r => r.endpoint.endsWith('/tests') && r.body).body, /0\.1000000000000000001/);
    await page.getByRole('button', { name: 'Run saved tests', exact: true }).click(); await page.getByText('Adult applicant: FAIL', { exact: true }).waitFor();
    unavailable = true; await page.getByRole('button', { name: 'Validate', exact: true }).click(); assert.match(await page.getByRole('alert').innerText(), /Backend unavailable/);
    const roundtrip = await page.evaluate(() => {
      const table = window.dmn.newDecision(); table.name = 'Eligibility & safety'; table.rules[0].description = 'Adults < 100';
      table.xmlIds = { definitions: 'DefinitionOriginal', table: 'TableOriginal', inputExpressions: { age: 'ExpressionOriginal' }, inputEntries: { adult: ['ConditionOriginal'] }, outputEntries: { adult: ['OutputOriginal'] } };
      const xml = window.dmn.decisionToDmnXml(table); const parsed = window.dmn.dmnXmlToDecision(xml); const again = window.dmn.dmnXmlToDecision(window.dmn.decisionToDmnXml(parsed));
      const prefixed = xml.replace(/<(\/)?(definitions|decision|decisionTable|input|inputExpression|output|rule|inputEntry|outputEntry|text|description)(?=[\s>])/g, '<$1dmn:$2').replace('xmlns=', 'xmlns:dmn=');
      const prefixParsed = window.dmn.dmnXmlToDecision(prefixed);
      const unsupported = [xml.replace('<output id=', '<outputValues/><output id='), xml.replace('hitPolicy="UNIQUE"', 'hitPolicy="UNIQUE" aggregation="SUM"'), xml.replace('<inputExpression ', '<inputExpression expressionLanguage="javascript" '), xml.replace('<text>age</text>', '<text>age</text><text>ignored</text>')];
      const rejected = unsupported.map(value => { try { window.dmn.dmnXmlToDecision(value); return false; } catch { return true; } });
      const config = { decisionKey: 'eligibility', decisionVersion: 1, inputs: [{ targetName: 'age', type: 'number', source: 'static', value: 21 }], outputs: [{ sourceName: 'eligible', target: 'variable', value: 'approved', type: 'boolean' }] };
      const bpmnXml = window.bpmn.processDefinitionToBpmnXml({ processId: 'rules', nodes: [{ id: 'decide', type: 'BusinessRuleTask', config }], flows: [] });
      return { table, parsed, again, prefixParsed, rejected, bpmnXml, config, decoded: window.bpmn.bpmnXmlToProcessDefinition(bpmnXml).nodes[0].config };
    });
    assert.deepEqual(roundtrip.parsed, roundtrip.table); assert.deepEqual(roundtrip.again, roundtrip.table); assert.deepEqual(roundtrip.prefixParsed, roundtrip.table); assert.deepEqual(roundtrip.rejected, [true, true, true, true]); assert.match(roundtrip.bpmnXml, /bpmn:businessRuleTask/); assert.deepEqual(roundtrip.decoded, roundtrip.config);
    console.log('PASS Modeler visual authoring, draft validation/test, exact JSON request numbers, deployment/saved tests and DMN/BPMN roundtrips (AC-1/4/10)');
  });
  await harness('easy-bpm-modeler', `import {BusinessRuleTaskEditor} from '/components/BusinessRuleTaskEditor.tsx'; import * as codec from '/utils/bpmnXml.ts'; function App(){const [data,setData]=React.useState({label:'Decision'});window.data=()=>data;window.reopen=()=>{const config={decisionKey:data.decisionKey,inputs:data.decisionInputs,outputs:data.decisionOutputs};const xml=codec.processDefinitionToBpmnXml({processId:'json-string',nodes:[{id:'decide',type:'BusinessRuleTask',config}],flows:[]});const restored=codec.bpmnXmlToProcessDefinition(xml).nodes[0].config;setData({label:'Decision',decisionKey:restored.decisionKey,decisionInputs:restored.inputs,decisionOutputs:restored.outputs});};return React.createElement(BusinessRuleTaskEditor,{data,onChange:patch=>setData(v=>({...v,...patch}))});}createRoot(document.getElementById('root')).render(React.createElement(App));`, async (page, url) => {
    await page.goto(url); await page.getByLabel('Task decision key').fill('eligibility'); await page.getByLabel('Exact decision version').fill('2');
    await page.getByRole('button', { name: 'Add decision input', exact: true }).click(); await page.getByLabel('Decision input 1 name').fill('age'); await page.getByLabel('Decision input 1 source').selectOption('static'); await page.getByLabel('Decision input 1 type').selectOption('number'); await page.getByLabel('Decision input 1 value').fill('21');
    await page.getByRole('button', { name: 'Add decision output', exact: true }).click(); await page.getByLabel('Decision output 1 name').fill('eligible'); await page.getByLabel('Decision output 1 variable').fill('approved'); await page.getByLabel('Decision output 1 type').selectOption('boolean');
    assert.equal(await page.getByRole('alert').count(), 0); const data = await page.evaluate(() => window.data()); assert.equal(data.decisionVersion, 2); assert.equal(data.decisionInputs[0].value, 21); assert.equal(data.decisionOutputs[0].target, 'variable');
    await page.getByLabel('Decision input 1 value').fill('0.1000000000000000001'); assert.match(await page.getByRole('alert').innerText(), /round-trip exactly/);
    await page.getByLabel('Decision input 1 type').selectOption('json'); await page.getByLabel('Decision input 1 value').fill('{"rate":0.1000000000000000001}'); assert.match(await page.getByRole('alert').innerText(), /round-trip exactly/);
    await page.getByLabel('Decision input 1 value').fill('"0.1000000000000000001"'); assert.equal(await page.getByRole('alert').count(), 0); assert.equal(await page.evaluate(() => window.data().decisionInputs[0].value), '0.1000000000000000001');
    await page.evaluate(() => window.reopen()); assert.equal(await page.getByLabel('Decision input 1 value').inputValue(), '"0.1000000000000000001"'); assert.equal(await page.getByRole('alert').count(), 0);
    await page.getByLabel('Decision input 1 value').fill('null'); assert.equal(await page.getByRole('alert').count(), 0); assert.equal(await page.evaluate(() => window.data().decisionInputs[0].value), null);
    await page.getByLabel('Decision input 1 value').fill('{"text":"0.1000000000000000001","nested":[true,null,1.25]}'); assert.equal(await page.getByRole('alert').count(), 0); await page.evaluate(() => window.reopen()); assert.deepEqual(await page.evaluate(() => window.data().decisionInputs[0].value), { text: '0.1000000000000000001', nested: [true, null, 1.25] });
    await page.getByLabel('Decision input 1 type').selectOption('number'); await page.getByLabel('Decision input 1 value').fill('21');
    await page.getByLabel('Exact decision version').fill(''); assert.equal(await page.evaluate(() => window.data().decisionVersion), undefined);
    console.log('PASS Business Rule Task key/version and typed input/output mapping UI (AC-6/7)');
  });
  await harness('easy-bpm-admin', `import {DecisionOperations} from '/components/DecisionOperations.tsx'; createRoot(document.getElementById('root')).render(React.createElement(DecisionOperations,{permissions:new URLSearchParams(location.search).has('denied')?[]:['ACCESS_BPM_ADMIN']}));`, async (page, url) => {
    const requests = []; let offline = false;
    const audit = { id: 7, decisionKey: 'eligibility', decisionVersion: 1, status: 'COMPLETED', processInstanceId: 5, nodeId: 'decide', durationMs: 2, matchedRuleIds: ['adult'], executedAt: '2026-10-20T12:00:00Z', inputs: { age: { type: 'number', redacted: true } }, outputs: { eligible: { type: 'boolean', redacted: true } }, explanations: result.explanations };
    await page.route('http://localhost:8080/**', async route => {
      const u = new URL(route.request().url()); requests.push(u);
      if (offline) return route.fulfill({ status: 403, json: { message: 'Forbidden' } });
      if (u.pathname === '/decisions') return route.fulfill({ json: [definition] });
      if (u.pathname.endsWith('/versions')) return route.fulfill({ json: [{ ...definition, id: 2, version: 2 }, definition] });
      if (u.pathname.endsWith('/diff')) return route.fulfill({ json: { decisionKey: 'eligibility', fromVersion: 1, toVersion: 2, changes: [{ path: 'name', before: 'Eligibility', after: 'Eligibility revised' }] } });
      if (u.pathname === '/decision-executions') return route.fulfill({ json: { content: u.searchParams.get('page') === '1' ? [] : [audit], totalPages: 2, totalElements: 21, number: Number(u.searchParams.get('page')), size: 20 } });
      return route.fulfill({ json: audit });
    });
    await page.goto(url); await page.getByRole('button', { name: 'Inspect #7' }).click(); await page.getByRole('region', { name: 'Decision execution details' }).waitFor(); await page.getByText('adult: Matched', { exact: true }).click(); await page.getByText('age >= 18: true', { exact: true }).waitFor();
    await page.getByLabel('Filter key').fill('eligibility'); await page.getByLabel('Filter status').selectOption('COMPLETED'); await page.getByRole('button', { name: 'Apply filters' }).click(); await page.getByRole('button', { name: 'Inspect #7' }).waitFor(); assert.ok(requests.some(u => u.searchParams.get('key') === 'eligibility' && u.searchParams.get('status') === 'COMPLETED'));
    await page.getByRole('button', { name: 'Next page' }).click(); await page.getByText('No decision executions found.', { exact: true }).waitFor();
    await page.getByLabel('Decision history key').selectOption('eligibility'); await page.getByRole('button', { name: 'Compare versions' }).click(); await page.getByText('Eligibility revised', { exact: false }).waitFor();
    offline = true; await page.getByRole('button', { name: 'Refresh', exact: true }).click(); await page.getByRole('alert').waitFor(); assert.match(await page.getByRole('alert').innerText(), /403|Forbidden/);
    await page.goto(`${url}?denied`); assert.match(await page.getByRole('alert').innerText(), /Access denied/);
    console.log('PASS Admin version diff, execution details/explanations, filters, pagination, empty/403 and UI permission gate (AC-5/9/11)');
  });
  await harness('easy-bpm-admin', `import {IncidentListPage} from '/components/IncidentListPage.tsx'; createRoot(document.getElementById('root')).render(React.createElement(IncidentListPage,{currentUser:'admin',onOpenInstance:()=>{}}));`, async (page, url) => {
    let retried = false; const queries = [];
    const group = { signature: 'decision-7', representativeIncidentId: 7, processDefinitionId: 1, processName: 'Eligibility flow', source: 'DECISION_TASK', nodeId: 'decide', status: 'OPEN', retryStatus: 'RETRY_ELIGIBLE', message: 'Decision evaluation failed', occurrenceCount: 1, instanceCount: 1, firstOccurredAt: '2026-10-20T12:00:00Z', lastOccurredAt: '2026-10-20T12:00:00Z', retryAttemptCount: 0, maxRetryAttempts: 3, sampleIncidentIds: [7], sampleInstanceIds: [5] };
    await page.route('http://localhost:8080/**', async route => { const u = new URL(route.request().url()); queries.push(u); if (u.pathname === '/incidents/7/retry') { retried = true; return route.fulfill({ json: {} }); } if (u.pathname.endsWith('/events')) return route.fulfill({ json: [] }); if (u.pathname === '/incidents/groups') return route.fulfill({ status: 404, json: { message: 'Not found' } }); return route.fulfill({ json: { content: [{ id:7, processInstanceId:5, nodeId:'decide', source:'DECISION_TASK', status:'OPEN', message:group.message, occurrenceCount:1, createdAt:group.firstOccurredAt, lastOccurredAt:group.lastOccurredAt }], totalPages: 1, totalElements: 1, size: 25, number: 0 } }); });
    await page.goto(url); await page.getByRole('button', { name: 'Retry this group', exact: true }).click(); assert.equal(retried, true);
    await page.getByRole('combobox').first().selectOption('DECISION_TASK'); await page.waitForFunction(() => document.body.textContent.includes('DECISION_TASK')); assert.ok(queries.some(u => u.searchParams.get('source') === 'DECISION_TASK'));
    console.log('PASS Admin decision incident retry and source filter (AC-8)');
  });
} finally { await browser.close(); }
