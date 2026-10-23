import { test } from 'node:test';
import assert from 'node:assert/strict';
import { durationSeconds, validateSla } from './sla.ts';
import { processDefinitionToBpmnXml } from './bpmnXml.ts';

const policy = { duration: 'PT4H', timeZone: 'America/Sao_Paulo', alerts: [{ id: 'warn-1', beforeDue: 'PT30M' }], escalations: [{ id: 'escalate-1', afterDue: 'PT0S', action: 'REASSIGN', target: '${supervisor}' }] };
test('fixed and dynamic duration support elapsed days and fractional seconds', () => {
  assert.equal(durationSeconds('P1D'), 86400);
  assert.equal(durationSeconds('PT0.5S'), 0.5);
  assert.deepEqual(validateSla(policy), []);
  assert.deepEqual(validateSla({ ...policy, duration: '${slaDuration}' }), []);
  assert.deepEqual(validateSla(undefined), []);
});
test('invalid durations, overflow, zones, IDs, offsets and actions block deployment', () => {
  for (const duration of ['P1M', 'P1Y', 'PT-1H', 'PT0S', 'PT', 'P1DT', 'PT999999999999999999999S', '${a} + 1', null, 4]) assert.ok(validateSla({ duration }).length);
  for (const patch of [{ timeZone: 'invalid/zone' }, { alerts: [{ id: 'warn-1', beforeDue: 'PT4H' }] }, { alerts: [{ id: 'escalate-1', beforeDue: 'PT1M' }] }, { escalations: [{ id: '', afterDue: 'PT0S', action: 'REASSIGN', target: 'user' }] }, { escalations: [{ id: 'a', afterDue: 'PT0S', action: 'UNSUPPORTED', target: 'user' }] }, { escalations: [{ id: 'a', afterDue: 'PT0S', action: 'REASSIGN', target: '${a} extra' }] }, { alerts: {} }, { alerts: [{ id: '__sla_transition', beforeDue: 'PT1M' }] }]) assert.ok(validateSla({ ...policy, ...patch }).length);
  assert.ok(validateSla(policy, 'service-task').length);
});
test('XML uses existing easy:node config, stable action IDs, and preserves unrelated config', () => {
  const definition = { processId: 'sla-fixture', nodes: [{ id: 'review', type: 'HumanTask', config: { sla: policy, formId: 'review-form', customExtension: { future: true } } }] };
  const xml = processDefinitionToBpmnXml(definition);
  const match = /<easy:node><!\[CDATA\[(.*?)\]\]><\/easy:node>/s.exec(xml)!;
  const decoded = JSON.parse(match[1]);
  assert.deepEqual(decoded.config, definition.nodes[0].config);
  assert.equal(decoded.config.sla.escalations[0].id, 'escalate-1');
  assert.match(xml, /<bpmn:userTask id="review"/);
  assert.doesNotMatch(processDefinitionToBpmnXml({ processId: 'legacy', nodes: [{ id: 'review', type: 'HumanTask', config: { formId: 'old' } }] }), /"sla"/);
});

test('BUG-1: ID, target and serialized policy boundaries match backend storage validation', () => {
  for (const field of ['alerts', 'escalations'] as const) {
    const validAction = field === 'alerts'
      ? { id: 'x'.repeat(100), beforeDue: 'PT1H' }
      : { id: 'x'.repeat(100), afterDue: 'PT0S', action: 'REASSIGN', target: 'user' };
    const valid = { duration: 'PT4H', [field]: [validAction] };
    assert.deepEqual(validateSla(valid), [], `${field} permits exactly 100 ID characters`);
    assert.ok(validateSla({ ...valid, [field]: [{ ...validAction, id: 'x'.repeat(101) }] }).some(error => error.includes(`${field}[0].id`) && error.includes('100 characters')));
  }
  const escalation = { id: 'action-1', afterDue: 'PT0S', action: 'REASSIGN', target: 'u'.repeat(255) };
  assert.deepEqual(validateSla({ duration: 'PT4H', escalations: [escalation] }), []);
  assert.ok(validateSla({ duration: 'PT4H', escalations: [{ ...escalation, target: 'u'.repeat(256) }] }).some(error => error.includes('target') && error.includes('255 characters')));
  // Unknown extension fields count too: imported policies cannot bypass the backend bound.
  const bounded = { duration: 'PT4H', futureExtension: '' };
  bounded.futureExtension = 'x'.repeat(32768 - JSON.stringify(bounded).length);
  assert.equal(JSON.stringify(bounded).length, 32768);
  assert.deepEqual(validateSla(bounded), []);
  assert.ok(validateSla({ ...bounded, futureExtension: `${bounded.futureExtension}x` }).some(error => error.includes('32768 characters')));
});
