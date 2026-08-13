const BPMN_NS = 'http://www.omg.org/spec/BPMN/20100524/MODEL';
const BPMNDI_NS = 'http://www.omg.org/spec/BPMN/20100524/DI';
const DC_NS = 'http://www.omg.org/spec/DD/20100524/DC';
const DI_NS = 'http://www.omg.org/spec/DD/20100524/DI';
const EASY_NS = 'https://easybpm.local/bpmn/extensions';

const escapeXml = (value: unknown): string => String(value ?? '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;');

const typeToTag = (type: string): string => ({
  StartEvent: 'startEvent',
  EndEvent: 'endEvent',
  HumanTask: 'userTask',
  UserTask: 'userTask',
  ServiceTask: 'serviceTask',
  APITask: 'serviceTask',
  AiTask: 'serviceTask',
  CodeTask: 'serviceTask',
  AgentProcessCall: 'callActivity',
  CallActivity: 'callActivity',
  ExclusiveGateway: 'exclusiveGateway',
  ParallelGateway: 'parallelGateway',
  InclusiveGateway: 'inclusiveGateway',
  TimerEvent: 'intermediateCatchEvent',
  MessageStartEvent: 'startEvent',
  MessageIntermediateCatchEvent: 'intermediateCatchEvent',
  MessageIntermediateThrowEvent: 'intermediateThrowEvent',
  ErrorBoundaryEvent: 'boundaryEvent',
  MessageBoundaryEvent: 'boundaryEvent',
  TimerBoundaryEvent: 'boundaryEvent',
  Participant: 'participant',
  Pool: 'participant'
}[type] || 'task');

const tagToType = (tag: string): string => ({
  startEvent: 'StartEvent',
  endEvent: 'EndEvent',
  userTask: 'HumanTask',
  serviceTask: 'ServiceTask',
  callActivity: 'CallActivity',
  exclusiveGateway: 'ExclusiveGateway',
  parallelGateway: 'ParallelGateway',
  inclusiveGateway: 'InclusiveGateway',
  intermediateCatchEvent: 'TimerEvent',
  intermediateThrowEvent: 'MessageIntermediateThrowEvent',
  boundaryEvent: 'ErrorBoundaryEvent',
  participant: 'Participant'
}[tag] || 'Task');

const extension = (tag: string, value: unknown, indent = '      ') =>
  `${indent}<easy:${tag}><![CDATA[${JSON.stringify(value)}]]></easy:${tag}>\n`;

const getExtensionJson = (element: Element, localName: string): any | undefined => {
  const child = element.getElementsByTagNameNS(EASY_NS, localName)[0];
  if (!child?.textContent) return undefined;
  try {
    return JSON.parse(child.textContent);
  } catch {
    return undefined;
  }
};

export const processDefinitionToBpmnXml = (definition: any): string => {
  const processId = definition.processId || definition.id;
  const processName = definition.processName || definition.name || processId;
  const nodes = Array.isArray(definition.nodes) ? definition.nodes : [];
  const flows = Array.isArray(definition.flows) ? definition.flows : [];

  let xml = `<?xml version="1.0" encoding="UTF-8"?>\n`;
  xml += `<bpmn:definitions xmlns:bpmn="${BPMN_NS}" xmlns:bpmndi="${BPMNDI_NS}" xmlns:dc="${DC_NS}" xmlns:di="${DI_NS}" xmlns:easy="${EASY_NS}" id="Definitions_${escapeXml(processId)}" targetNamespace="https://easybpm.local/process/${escapeXml(processId)}">\n`;
  xml += `  <bpmn:process id="${escapeXml(processId)}" name="${escapeXml(processName)}" isExecutable="true">\n`;

  if ((definition.variables?.length || 0) > 0 || definition.metadata) {
    xml += `    <bpmn:extensionElements>\n`;
    if (definition.variables?.length) xml += extension('variables', definition.variables, '      ');
    if (definition.metadata) xml += extension('metadata', definition.metadata, '      ');
    xml += `    </bpmn:extensionElements>\n`;
  }

  for (const node of nodes) {
    const tag = typeToTag(node.type);
    const attached = tag === 'boundaryEvent' && node.attachedTo ? ` attachedToRef="${escapeXml(node.attachedTo)}"` : '';
    xml += `    <bpmn:${tag} id="${escapeXml(node.id)}" name="${escapeXml(node.name || node.id)}"${attached}>\n`;
    xml += `      <bpmn:extensionElements>\n`;
    xml += extension('node', node, '        ');
    xml += `      </bpmn:extensionElements>\n`;
    if (node.type === 'TimerEvent' || node.type === 'TimerBoundaryEvent') xml += `      <bpmn:timerEventDefinition/>\n`;
    if (['MessageStartEvent', 'MessageIntermediateCatchEvent', 'MessageIntermediateThrowEvent', 'MessageBoundaryEvent'].includes(node.type)) xml += `      <bpmn:messageEventDefinition/>\n`;
    if (node.type === 'ErrorBoundaryEvent') xml += `      <bpmn:errorEventDefinition/>\n`;
    xml += `    </bpmn:${tag}>\n`;
  }

  flows.forEach((flow: any, index: number) => {
    const id = flow.id || `Flow_${index + 1}_${flow.from || flow.source}_${flow.to || flow.target}`;
    const condition = flow.condition ? String(flow.condition) : '';
    const waypoints = Array.isArray(flow.waypoints) && flow.waypoints.length > 0 ? flow.waypoints : undefined;
    if (!condition && !waypoints) {
      xml += `    <bpmn:sequenceFlow id="${escapeXml(id)}" sourceRef="${escapeXml(flow.from || flow.source)}" targetRef="${escapeXml(flow.to || flow.target)}"/>\n`;
      return;
    }
    xml += `    <bpmn:sequenceFlow id="${escapeXml(id)}" sourceRef="${escapeXml(flow.from || flow.source)}" targetRef="${escapeXml(flow.to || flow.target)}">\n`;
    if (condition) {
      xml += `      <bpmn:conditionExpression xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="bpmn:tFormalExpression">${escapeXml(condition)}</bpmn:conditionExpression>\n`;
    }
    if (waypoints) {
      xml += `      <bpmn:extensionElements>\n${extension('waypoints', waypoints, '        ')}      </bpmn:extensionElements>\n`;
    }
    xml += `    </bpmn:sequenceFlow>\n`;
  });

  xml += `  </bpmn:process>\n`;
  xml += `  <bpmndi:BPMNDiagram id="BPMNDiagram_1">\n    <bpmndi:BPMNPlane id="BPMNPlane_1">\n`;
  for (const node of nodes) {
    const x = node.position?.x ?? 100;
    const y = node.position?.y ?? 100;
    xml += `      <bpmndi:BPMNShape id="${escapeXml(node.id)}_di" bpmnElement="${escapeXml(node.id)}">\n`;
    xml += `        <dc:Bounds x="${x}" y="${y}" width="${node.width ?? 120}" height="${node.height ?? 60}"/>\n`;
    xml += `      </bpmndi:BPMNShape>\n`;
  }
  flows.forEach((flow: any, index: number) => {
    const id = flow.id || `Flow_${index + 1}_${flow.from || flow.source}_${flow.to || flow.target}`;
    xml += `      <bpmndi:BPMNEdge id="${escapeXml(id)}_di" bpmnElement="${escapeXml(id)}">\n`;
    (flow.waypoints || []).forEach((point: any) => {
      xml += `        <di:waypoint x="${Number(point.x) || 0}" y="${Number(point.y) || 0}"/>\n`;
    });
    xml += `      </bpmndi:BPMNEdge>\n`;
  });
  xml += `    </bpmndi:BPMNPlane>\n  </bpmndi:BPMNDiagram>\n</bpmn:definitions>\n`;
  return xml;
};

export const bpmnXmlToProcessDefinition = (xml: string): any => {
  const doc = new DOMParser().parseFromString(xml, 'application/xml');
  const parserError = doc.getElementsByTagName('parsererror')[0];
  if (parserError) throw new Error('Invalid BPMN XML file.');
  const process = doc.getElementsByTagNameNS(BPMN_NS, 'process')[0];
  if (!process) throw new Error('BPMN XML must contain bpmn:process.');

  const boundsById = new Map<string, any>();
  Array.from(doc.getElementsByTagNameNS(BPMNDI_NS, 'BPMNShape')).forEach((shape) => {
    const id = shape.getAttribute('bpmnElement') || '';
    const bounds = shape.getElementsByTagNameNS(DC_NS, 'Bounds')[0];
    if (!id || !bounds) return;
    boundsById.set(id, {
      position: { x: Number(bounds.getAttribute('x')) || 100, y: Number(bounds.getAttribute('y')) || 100 },
      width: Number(bounds.getAttribute('width')) || 120,
      height: Number(bounds.getAttribute('height')) || 60
    });
  });

  const nodes = Array.from(process.children)
    .filter((element) => element.namespaceURI === BPMN_NS && element.localName !== 'sequenceFlow' && element.localName !== 'extensionElements')
    .map((element) => ({
      ...(getExtensionJson(element, 'node') || {}),
      id: getExtensionJson(element, 'node')?.id || element.getAttribute('id'),
      name: getExtensionJson(element, 'node')?.name || element.getAttribute('name') || element.getAttribute('id'),
      type: getExtensionJson(element, 'node')?.type || tagToType(element.localName),
      attachedTo: getExtensionJson(element, 'node')?.attachedTo || element.getAttribute('attachedToRef') || undefined,
      ...(boundsById.get(element.getAttribute('id') || '') || {})
    }));

  const flows = Array.from(process.getElementsByTagNameNS(BPMN_NS, 'sequenceFlow')).map((flow) => ({
    id: flow.getAttribute('id') || undefined,
    from: flow.getAttribute('sourceRef') || '',
    to: flow.getAttribute('targetRef') || '',
    condition: flow.getElementsByTagNameNS(BPMN_NS, 'conditionExpression')[0]?.textContent || null,
    waypoints: getExtensionJson(flow, 'waypoints')
  }));

  return {
    processId: process.getAttribute('id') || `process_${Date.now()}`,
    processName: process.getAttribute('name') || '',
    metadata: getExtensionJson(process, 'metadata'),
    variables: getExtensionJson(process, 'variables') || [],
    nodes,
    flows
  };
};

export const isBpmnXml = (value: string): boolean => value.trimStart().startsWith('<');
