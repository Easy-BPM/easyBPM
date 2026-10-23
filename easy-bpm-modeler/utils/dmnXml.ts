import type { DecisionTable, DecisionType } from '../decisionTypes';

export const DMN_NS = 'https://www.omg.org/spec/DMN/20191111/MODEL/';
const escape = (value: string) => value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const idAttribute = (value?: string) => value ? ` id="${escape(value)}"` : '';
const descriptionXml = (value?: string) => value !== undefined ? `<description>${escape(value)}</description>` : '';
export const newDecision = (): DecisionTable => ({ key: '', name: '', hitPolicy: 'UNIQUE', inputs: [], outputs: [], rules: [] });
export const decisionToDmnXml = (table: DecisionTable): string => `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="${DMN_NS}" id="${escape(table.xmlIds?.definitions || `definitions_${table.key}`)}" name="${escape(table.name)}" namespace="https://easybpm.local/decisions">
  <decision id="${escape(table.key)}" name="${escape(table.name)}">${descriptionXml(table.description)}
    <decisionTable id="${escape(table.xmlIds?.table || `table_${table.key}`)}" hitPolicy="${table.hitPolicy}">
${table.inputs.map(input => `      <input id="${escape(input.id)}" label="${escape(input.label)}">${descriptionXml(input.description)}<inputExpression${idAttribute(table.xmlIds?.inputExpressions?.[input.id])} typeRef="${input.type === 'json' ? 'Any' : input.type}"><text>${escape(input.expression)}</text></inputExpression></input>`).join('\n')}
${table.outputs.map(output => `      <output id="${escape(output.id)}" name="${escape(output.name)}" typeRef="${output.type === 'json' ? 'Any' : output.type}">${descriptionXml(output.description)}</output>`).join('\n')}
${table.rules.map(rule => `      <rule id="${escape(rule.id)}">${descriptionXml(rule.description)}${rule.inputEntries.map((entry, i) => `<inputEntry${idAttribute(table.xmlIds?.inputEntries?.[rule.id]?.[i])}><text>${escape(entry)}</text></inputEntry>`).join('')}${rule.outputEntries.map((entry, i) => `<outputEntry${idAttribute(table.xmlIds?.outputEntries?.[rule.id]?.[i])}><text>${escape(entry)}</text></outputEntry>`).join('')}</rule>`).join('\n')}
    </decisionTable>
  </decision>
</definitions>`;

export const dmnXmlToDecision = (xml: string): DecisionTable => {
  if (new TextEncoder().encode(xml).length > 1_000_000) throw new Error('DMN XML exceeds 1 MB.');
  if (/<!DOCTYPE|<!ENTITY/i.test(xml)) throw new Error('DTD and entity declarations are not supported.');
  const doc = new DOMParser().parseFromString(xml, 'application/xml');
  if (doc.getElementsByTagName('parsererror').length) throw new Error('Invalid DMN XML.');
  const root = doc.documentElement;
  if (root.localName !== 'definitions' || root.namespaceURI !== DMN_NS) throw new Error('Expected DMN 1.3 definitions.');
  const allowed: Record<string, string[]> = { definitions: ['decision'], decision: ['decisionTable', 'description'], decisionTable: ['input', 'output', 'rule'], input: ['inputExpression', 'description'], inputExpression: ['text'], output: ['description'], rule: ['description', 'inputEntry', 'outputEntry'], inputEntry: ['text'], outputEntry: ['text'], text: [], description: [] };
  const attributes: Record<string, string[]> = { definitions: ['id', 'name', 'namespace'], decision: ['id', 'name'], decisionTable: ['id', 'hitPolicy'], input: ['id', 'label'], inputExpression: ['id', 'typeRef'], output: ['id', 'name', 'label', 'typeRef'], rule: ['id'], inputEntry: ['id'], outputEntry: ['id'], text: [], description: [] };
  const check = (node: Element) => {
    if (node.namespaceURI !== DMN_NS || !allowed[node.localName]) throw new Error(`Unsupported DMN element: ${node.localName}`);
    for (const attr of Array.from(node.attributes)) {
      if (attr.namespaceURI === 'http://www.w3.org/2000/xmlns/') continue;
      if (!attributes[node.localName].includes(attr.name)) throw new Error(`Unsupported DMN attribute: ${attr.name}`);
    }
    for (const child of Array.from(node.children)) { if (!allowed[node.localName].includes(child.localName)) throw new Error(`Unsupported DMN element: ${child.localName}`); check(child); }
    if (['inputExpression', 'inputEntry', 'outputEntry'].includes(node.localName) && Array.from(node.children).filter(child => child.localName === 'text').length !== 1) throw new Error('Each expression requires exactly one text element.');
    if (Array.from(node.children).filter(child => child.localName === 'description').length > 1) throw new Error('At most one description is supported.');
    if (node.localName === 'input' && Array.from(node.children).filter(child => child.localName === 'inputExpression').length !== 1) throw new Error('Each input requires exactly one expression.');
  };
  check(root);
  const children = (node: Element, name: string) => Array.from(node.children).filter(child => child.localName === name && child.namespaceURI === DMN_NS);
  const decisions = children(root, 'decision');
  if (decisions.length !== 1 || children(root, 'inputData').length || children(root, 'businessKnowledgeModel').length) throw new Error('Only one standalone decision table is supported.');
  const decision = decisions[0], tables = children(decision, 'decisionTable');
  if (tables.length !== 1 || children(decision, 'informationRequirement').length) throw new Error('Only standalone decision tables are supported.');
  const table = tables[0], policy = table.getAttribute('hitPolicy') || 'UNIQUE';
  if (!['UNIQUE', 'FIRST', 'COLLECT'].includes(policy) || table.hasAttribute('aggregation')) throw new Error('Unsupported hit policy or aggregation.');
  const type = (ref: string | null): DecisionType => {
    const value = ref === 'Any' ? 'json' : ref;
    if (!['string', 'number', 'boolean', 'date', 'json'].includes(value || '')) throw new Error(`Unsupported type: ${ref}`);
    return value as DecisionType;
  };
  const description = (node: Element) => { const value = children(node, 'description')[0]; return value ? { description: value.textContent || '' } : {}; };
  const text = (node: Element) => children(node, 'text')[0]?.textContent || '';
  const key = decision.getAttribute('id') || '';
  const xmlIds: NonNullable<DecisionTable['xmlIds']> = {};
  if (root.getAttribute('id') && root.getAttribute('id') !== `definitions_${key}`) xmlIds.definitions = root.getAttribute('id')!;
  if (table.getAttribute('id') && table.getAttribute('id') !== `table_${key}`) xmlIds.table = table.getAttribute('id')!;
  for (const input of children(table, 'input')) { const exprId = children(input, 'inputExpression')[0].getAttribute('id'); if (exprId) (xmlIds.inputExpressions ||= {})[input.getAttribute('id') || ''] = exprId; }
  for (const rule of children(table, 'rule')) for (const kind of ['inputEntries', 'outputEntries'] as const) { const ids = children(rule, kind === 'inputEntries' ? 'inputEntry' : 'outputEntry').map(entry => entry.getAttribute('id') || ''); if (ids.some(Boolean)) (xmlIds[kind] ||= {})[rule.getAttribute('id') || ''] = ids; }
  return { ...(Object.keys(xmlIds).length ? { xmlIds } : {}), ...description(decision), key, name: decision.getAttribute('name') || key, hitPolicy: policy as DecisionTable['hitPolicy'],
    inputs: children(table, 'input').map(input => { const expr = children(input, 'inputExpression')[0]; if (!expr) throw new Error('Input expression is required.'); return { ...description(input), id: input.getAttribute('id') || '', label: input.getAttribute('label') || '', expression: text(expr), type: type(expr.getAttribute('typeRef')) }; }),
    outputs: children(table, 'output').map(output => ({ ...description(output), id: output.getAttribute('id') || '', name: output.getAttribute('name') || '', type: type(output.getAttribute('typeRef')) })),
    rules: children(table, 'rule').map(rule => ({ id: rule.getAttribute('id') || '', ...description(rule), inputEntries: children(rule, 'inputEntry').map(text), outputEntries: children(rule, 'outputEntry').map(text) })) };
};
