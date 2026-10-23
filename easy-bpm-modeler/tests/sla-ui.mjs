// Run with Node and Playwright installed (or NODE_PATH pointing to a provided runtime).
// Uses installed Chrome by default; set SLA_BROWSER_CHANNEL for another Playwright channel.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const { createServer } = await import(pathToFileURL(path.join(root, 'easy-bpm-modeler/node_modules/vite/dist/node/index.js')));
const browser = await chromium.launch({ headless: true, channel: process.env.SLA_BROWSER_CHANNEL || 'chrome' });
const pageOf = content => ({ content, totalPages: 1, totalElements: content.length, size: 20, number: 0 });
const counters = { onTrack: 1, atRisk: 0, overdue: 0, completed: 0, withoutSla: 0, asOf: '2026-10-07T12:00:00Z', timeZone: 'UTC' };
const task = { id: 54, title: 'Review SLA', name: 'Review SLA', processInstanceId: 100, nodeId: 'review', assignee: 'alice', candidateUsers: [], candidateGroups: [], status: 'PENDING', createdAt: '2026-10-07T11:00:00Z', completedAt: null, formDbId: 3, formId: 'review', variables: { comment: 'initial' }, dueDate: '2026-10-07T12:00:20Z', slaStatus: 'ON_TRACK', slaTimeZone: 'UTC', completedWithinSla: null };

async function harness(app, code, run) {
  const server = await createServer({ configFile: false, root: path.join(root, app), server: { host: '127.0.0.1', port: 0 }, optimizeDeps: { include: ['react', 'react-dom/client'] } });
  server.middlewares.use('/__sla-tests.html', async (_req, res) => {
    const html = await server.transformIndexHtml('/__sla-tests.html', `<html><body><div id="root"></div><script type="module">import React from 'react'; import {createRoot} from 'react-dom/client'; ${code}</script></body></html>`);
    res.setHeader('Content-Type', 'text/html'); res.end(html);
  });
  server.middlewares.stack.unshift(server.middlewares.stack.pop());
  await server.listen();
  const page = await browser.newPage(); page.setDefaultTimeout(10000);
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  try { await run(page, `http://127.0.0.1:${server.httpServer.address().port}/__sla-tests.html`); assert.deepEqual(errors, []); }
  catch (error) { console.error('Browser errors:', errors, 'Rendered:', (await page.locator('body').innerText()).slice(0, 1500)); throw error; }
  finally { await page.close(); await server.close(); }
}
try {
  await harness('easy-bpm-modeler', `import {TaskSlaEditor} from '/components/TaskSlaEditor.tsx'; import * as codec from '/utils/bpmnXml.ts'; window.codec=codec; function App(){const [sla,setSla]=React.useState();window.sla=()=>sla;window.setSla=setSla;return React.createElement(TaskSlaEditor,{value:sla,onChange:setSla});} createRoot(document.getElementById('root')).render(React.createElement(App));`, async (page, url) => {
    await page.goto(url); await page.getByRole('checkbox', { name: 'Task SLA' }).check();
    await page.getByLabel('Duration', { exact: true }).fill('PT4H');
    await page.getByRole('button', { name: 'Add alert', exact: true }).click();
    await page.getByRole('button', { name: 'Add escalation', exact: true }).click();
    await page.getByLabel('Target', { exact: true }).fill('${supervisor}');
    await page.getByLabel('Duration', { exact: true }).fill('P1M');
    await page.getByRole('alert').waitFor(); assert.match(await page.getByRole('alert').innerText(), /positive ISO duration/);
    await page.getByLabel('Duration', { exact: true }).fill('PT4H');
    assert.equal(await page.getByRole('alert').count(), 0);
    await page.getByLabel('Alert ID', { exact: true }).fill('x'.repeat(101));
    assert.match(await page.getByRole('alert').innerText(), /maximum 100 characters/);
    await page.getByLabel('Alert ID', { exact: true }).fill('x'.repeat(100));
    await page.getByLabel('Target', { exact: true }).fill('u'.repeat(256));
    assert.match(await page.getByRole('alert').innerText(), /maximum 255 characters/);
    await page.getByLabel('Target', { exact: true }).fill('${supervisor}');
    await page.evaluate(() => window.setSla({ ...window.sla(), futureExtension: 'x'.repeat(32768) }));
    await page.getByRole('alert').waitFor();
    assert.match(await page.getByRole('alert').innerText(), /32768 characters/);
    await page.evaluate(() => { const { futureExtension, ...policy } = window.sla(); window.setSla(policy); });
    await page.getByRole('alert').waitFor({ state: 'detached' });
    const roundtrip = await page.evaluate(() => {
      const policy = window.sla();
      let xml = window.codec.processDefinitionToBpmnXml({ processId: 'sla', metadata: { custom: 'keep' }, nodes: [{ id: 'review', type: 'HumanTask', config: { sla: policy, custom: { preserve: true }, assignee: 'alice', note: ']]>' } }, {id:'end',type:'EndEvent'}], flows:[{id:'Flow_original',from:'review',to:'end',condition:'${approved}'}] });
      xml = xml.replace('</easy:node>', '</easy:node><vendor:node xmlns:vendor="urn:vendor" note="keep"/>');
      xml = xml.replace('</bpmn:extensionElements>', '<vendor:data xmlns:vendor="urn:vendor" flag="keep">custom</vendor:data></bpmn:extensionElements>');
      const imported = window.codec.bpmnXmlToProcessDefinition(xml);
      imported.nodes[0].name = 'Edited review';
      const exported = window.codec.processDefinitionToBpmnXml(imported);
      const final = window.codec.bpmnXmlToProcessDefinition(exported);
      return { policy, final, exported };
    });
    assert.deepEqual(roundtrip.final.nodes[0].config.sla, roundtrip.policy);
    assert.deepEqual(roundtrip.final.nodes[0].config.custom, { preserve: true });
    assert.equal(roundtrip.final.nodes[0].config.note, ']]>');
    assert.match(roundtrip.exported, /vendor:node/); assert.equal(roundtrip.final.flows[0].id, 'Flow_original'); assert.equal(roundtrip.final.flows[0].condition, '${approved}'); assert.equal(roundtrip.final.nodes[1].config?.sla, undefined);
    assert.match(roundtrip.exported, /vendor:data/); assert.equal(roundtrip.final.metadata.custom, 'keep');
    console.log('PASS Modeler editor, validation and real DOM XML import/edit/export (AC-12/13)');
  });
  await harness('easy-bpm-task-portal', `import {TaskDetailView,InboxView} from '/App.tsx'; const query=new URLSearchParams(location.search);createRoot(document.getElementById('root')).render(query.has('inbox')?React.createElement(InboxView,{currentUser:'alice',onSelectTask:id=>window.selected=id}):React.createElement(TaskDetailView,{taskId:54,currentUser:'alice',onBack:()=>{}}));`, async (page, url) => {
    let current = { ...task }; let offline = false; const searches = [];
    await page.route('http://localhost:8080/**', async route => {
      const request = route.request(), endpoint = new URL(request.url()).pathname;
      if (offline) return route.abort('failed');
      if (endpoint === '/tasks/54') return route.fulfill({ json: current });
      if (endpoint === '/forms/3') return route.fulfill({ json: { id: 3, formId: 'review', schema: { type: 'object', properties: { comment: { type: 'string', title: 'Comment' } } } } });
      if (endpoint === '/tasks/search') { searches.push({ sort: new URL(request.url()).searchParams.get('sort'), body: request.postDataJSON() }); return route.fulfill({ json: pageOf([current]) }); }
      if (endpoint === '/tasks/sla/summary') return route.fulfill({ json: counters });
      return route.fulfill({ status: 404, json: { message: endpoint } });
    });
    await page.clock.install({ time: new Date('2026-10-07T12:00:00Z') });
    await page.goto(url); const comment = page.getByPlaceholder('Enter Comment...'); await comment.fill('unsaved edits');
    current = { ...current, slaStatus: 'AT_RISK', variables: { comment: 'server draft' } };
    await page.clock.fastForward(15000); await page.getByRole('status').filter({ hasText: 'At risk' }).waitFor();
    assert.equal(await comment.inputValue(), 'unsaved edits'); assert.match(await page.locator('body').innerText(), /Task #54/);
    current = { ...current, slaStatus: 'OVERDUE' }; await page.clock.fastForward(15000); await page.getByRole('status').filter({ hasText: 'Overdue' }).waitFor();
    assert.equal(await comment.inputValue(), 'unsaved edits');
    offline = true; await page.clock.fastForward(15000); await page.getByRole('alert').waitFor(); assert.match(await page.getByRole('alert').innerText(), /inputs are preserved/); assert.equal(await comment.inputValue(), 'unsaved edits');
    offline = false; await page.goto(`${url}?inbox=1`); await page.getByRole('heading', { name: 'Task Inbox' }).waitFor();
    await page.getByLabel('Sort', { exact: true }).selectOption('dueDate,asc');
    await page.getByRole('button', { name: 'Filters', exact: true }).click(); await page.getByRole('button', { name: 'Add filter' }).click();
    await page.locator('select').filter({ has: page.locator('option[value="slaStatus"]') }).selectOption('slaStatus');
    await page.locator('input[placeholder]').last().fill('OVERDUE');
    await page.getByRole('button', { name: 'Apply Filters', exact: false }).click();
    await page.waitForFunction(() => document.body.innerText.includes('SLA Status = OVERDUE'));
    const before = searches.length; await page.clock.fastForward(15000); await page.waitForFunction(() => document.body.innerText.includes('Overdue'));
    assert.ok(searches.length > before); assert.equal(searches.at(-1).sort, 'dueDate,asc'); assert.ok(searches.at(-1).body.filters.some(f => f.field === 'slaStatus' && f.value === 'OVERDUE'));
    assert.match(await page.locator('body').innerText(), /SLA Status = OVERDUE/);
    console.log('PASS Portal fake-clock authoritative refresh, unsaved form, filters, countdown, offline error (AC-8/15)');
  });
  await harness('easy-bpm-admin', `import {SlaDashboard} from '/components/SlaDashboard.tsx';createRoot(document.getElementById('root')).render(React.createElement(SlaDashboard,{onInspectInstance:id=>window.inspected=id}));`, async (page, url) => {
    const calls = []; let failedRetry = true; let retried = false;
    await page.route('http://localhost:8080/**', async route => {
      const request = route.request(), endpoint = new URL(request.url()).pathname; calls.push({ endpoint, query: new URL(request.url()).search, body: request.postData() });
      if (endpoint === '/tasks/search') return route.fulfill({ json: pageOf([task]) });
      if (endpoint === '/tasks/sla/summary') return route.fulfill({ json: counters });
      if (endpoint === '/admin/sla/metrics') return route.fulfill({ json: pageOf([{ key: 'review', label: 'Review process', ...counters, totalSlaCompleted: 2, completedWithinSlaCount: 1, completedWithinSlaRate: .5, successfulEscalations: 1, failedEscalations: 1, alertToCompletionSampleCount: 1, alertToCompletionAverageSeconds: 60 }]) });
      if (endpoint === '/tasks/54/sla/events') return route.fulfill({ json: pageOf([{ id: 1, taskId: 54, actionId: 'escalate-1', eventType: retried ? 'ESCALATION_SUCCEEDED' : 'ESCALATION_FAILED', scheduledAt: task.dueDate, occurredAt: task.dueDate, outcome: retried ? 'SUCCEEDED' : 'FAILED', message: 'Target validation', incidentId: 9 }]) });
      if (endpoint === '/incidents/9/retry') { if (failedRetry) return route.fulfill({ status: 409, json: { message: 'Task is stale' } }); retried = true; return route.fulfill({ json: { id: 9, status: 'RESOLVED' } }); }
      return route.fulfill({ status: 404 });
    });
    await page.goto(url); await page.getByText('Review process', { exact: true }).waitFor();
    assert.match(await page.locator('body').innerText(), /50.0%/);
    await page.getByLabel('Task SLA status').selectOption('OVERDUE'); await page.getByLabel('Group metrics by').selectOption('GROUP');
    await page.getByLabel('Due from', { exact: true }).fill('2026-10-07T10:00');
    await page.getByLabel('Due to (inclusive)').fill('2026-10-07T14:00');
    await page.getByRole('button', { name: 'Inspect', exact: true }).click(); await page.getByText('ESCALATION_FAILED', { exact: true }).waitFor();
    await page.getByRole('button', { name: 'Retry escalation' }).click(); await page.getByRole('alert').waitFor(); assert.match(await page.getByRole('alert').innerText(), /409.*Task is stale/);
    failedRetry = false; await page.getByRole('button', { name: 'Retry escalation' }).click(); await page.getByText('ESCALATION_SUCCEEDED', { exact: true }).waitFor();
    await page.getByRole('button', { name: 'Open instance timeline and variables' }).click(); assert.equal(await page.evaluate(() => window.inspected), 100);
    assert.ok(calls.some(c => c.endpoint === '/admin/sla/metrics' && c.query.includes('groupBy=GROUP') && c.query.includes('dueDateFrom=')));
    assert.ok(calls.some(c => c.endpoint === '/tasks/search' && JSON.parse(c.body).filters.some(f => f.field === 'slaStatus' && f.value === 'OVERDUE')));
    console.log('PASS Admin grouped metrics, ranges/status, task history, retry conflict/success and instance navigation (AC-9/14/16)');
  });
} finally { await browser.close(); }
