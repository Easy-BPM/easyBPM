// Synthetic sessions and isolated API fixtures. Backend access rules are covered
// by TaskSlaControllerTest and TaskSlaApiIntegrationTest.
// Requires Playwright (or NODE_PATH) and Chrome.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { createServer } from 'vite';

const { chromium } = createRequire(import.meta.url)('playwright');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const server = await createServer({ configFile: false, root, server: { host: '127.0.0.1', port: 0 }, optimizeDeps: { include: ['react', 'react-dom/client'] } });
server.middlewares.use('/__sla-session.html', async (_req, res) => {
  res.setHeader('Content-Type', 'text/html');
  res.end(await server.transformIndexHtml('/__sla-session.html', `<div id="root"></div><script type="module">import React from 'react';import {createRoot} from 'react-dom/client';import App from '/App.tsx';createRoot(document.getElementById('root')).render(React.createElement(App));</script>`));
});
server.middlewares.stack.unshift(server.middlewares.stack.pop());

let browser;
try {
  await server.listen();
  browser = await chromium.launch({ headless: true, channel: process.env.AUTH_BROWSER_CHANNEL || 'chrome' });
  for (const permission of ['ACCESS_BPM_ADMIN', 'ACCESS_PROCESS_PORTAL']) {
    const page = await browser.newPage();
    const token = 'synthetic-issue-68-token';
    const identity = { username: 'sla-reviewer', groups: ['REVIEWERS'], permissions: [permission] };
    const errors = [];
    let expired = false;
    page.on('pageerror', () => errors.push('Unexpected page error'));
    await page.route('http://localhost:8080/**', async route => {
      const request = route.request();
      const endpoint = new URL(request.url()).pathname;
      if (endpoint === '/auth/config') return route.fulfill({ json: { provider: 'local', oidc: null } });
      if (endpoint === '/auth/login') return route.fulfill({ json: { ...identity, token } });
      if (['/auth/me', '/tasks/search', '/tasks/sla/summary'].includes(endpoint)) {
        // Boolean assertion avoids exposing credentials in failure output.
        assert.ok(request.headers().authorization === `Bearer ${token}`, 'Protected request must use the login session');
      }
      if (endpoint === '/auth/me') return route.fulfill({ json: identity });
      if (endpoint === '/tasks/search') return route.fulfill({ json: { content: [], totalPages: 0, totalElements: 0, size: 25, number: 0 } });
      if (endpoint === '/tasks/sla/summary') return route.fulfill(expired
        ? { status: 401, json: { message: 'Session expired' } }
        : { json: { onTrack: 0, atRisk: 0, overdue: 0, completed: 0, withoutSla: 0, asOf: '2026-01-01T00:00:00Z', timeZone: 'UTC' } });
      return route.fulfill({ json: [] });
    });
    const inboxCycle = async action => {
      const responses = ['/tasks/search', '/tasks/sla/summary'].map(endpoint => page.waitForResponse(response =>
        new URL(response.url()).pathname === endpoint && response.request().method() === 'POST'));
      await action();
      for (const response of await Promise.all(responses)) {
        assert.equal(response.status(), 200);
        await response.finished();
      }
      await page.getByRole('heading', { name: 'Task Inbox', exact: true }).waitFor();
      await page.getByLabel('SLA counters').waitFor();
      assert.ok(await page.evaluate(expected => JSON.parse(localStorage.getItem('easybpm_portal_auth'))?.token === expected, token), 'Summary must preserve the login session');
      assert.equal(await page.getByPlaceholder('Enter username').count(), 0);
    };
    await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/__sla-session.html`);
    await page.getByPlaceholder('Enter username').fill(identity.username);
    await page.locator('input[type=password]').fill('synthetic-password');
    await inboxCycle(() => page.getByRole('button', { name: 'Sign In', exact: true }).click());
    // Exercise the same refresh callback used when the visible inbox refreshes.
    await inboxCycle(() => page.evaluate(() => document.dispatchEvent(new Event('visibilitychange'))));
    await page.getByRole('button', { name: 'Dashboard', exact: true }).click();
    await inboxCycle(() => page.locator('aside').getByRole('button', { name: 'My Inbox', exact: true }).click());
    await inboxCycle(() => page.getByRole('button', { name: 'Completed', exact: true }).click());
    // Reload exercises auth/me and reuses the persisted bearer session.
    const me = page.waitForResponse(response => new URL(response.url()).pathname === '/auth/me');
    await inboxCycle(() => page.reload());
    assert.equal((await me).status(), 200);

    // Real 401 responses must still expire the session; no global suppression.
    expired = true;
    await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
    await page.getByPlaceholder('Enter username').waitFor();
    assert.ok(await page.evaluate(() => localStorage.getItem('easybpm_portal_auth') === null));
    assert.deepEqual(errors, []);
    console.log(`PASS #68 ${permission}: login, summary refresh, inbox navigation, reload, and genuine session expiry`);
    await page.close();
  }
} finally {
  await browser?.close();
  await server.close();
}
