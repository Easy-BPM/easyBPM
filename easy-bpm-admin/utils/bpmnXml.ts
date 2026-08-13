import { WorkflowDefinition } from '../types';

const BPMN_NS = 'http://www.omg.org/spec/BPMN/20100524/MODEL';
const BPMNDI_NS = 'http://www.omg.org/spec/BPMN/20100524/DI';
const DC_NS = 'http://www.omg.org/spec/DD/20100524/DC';
const EASY_NS = 'https://easybpm.local/bpmn/extensions';

const hasChild = (element: Element, localName: string): boolean =>
  Array.from(element.children).some((child) => child.localName === localName);

const elementToType = (element: Element): string => {
  switch (element.localName) {
    case 'startEvent':
      return hasChild(element, 'messageEventDefinition') ? 'MessageStartEvent' : 'StartEvent';
    case 'endEvent':
      return 'EndEvent';
    case 'userTask':
      return 'HumanTask';
    case 'serviceTask':
      return 'ServiceTask';
    case 'callActivity':
      return 'CallActivity';
    case 'exclusiveGateway':
      return 'ExclusiveGateway';
    case 'parallelGateway':
      return 'ParallelGateway';
    case 'inclusiveGateway':
      return 'InclusiveGateway';
    case 'intermediateCatchEvent':
      return hasChild(element, 'messageEventDefinition') ? 'MessageIntermediateCatchEvent' : 'TimerEvent';
    case 'intermediateThrowEvent':
      return hasChild(element, 'messageEventDefinition') ? 'MessageIntermediateThrowEvent' : 'Task';
    case 'boundaryEvent':
      if (hasChild(element, 'messageEventDefinition')) return 'MessageBoundaryEvent';
      if (hasChild(element, 'timerEventDefinition')) return 'TimerBoundaryEvent';
      return 'ErrorBoundaryEvent';
    case 'participant':
      return 'Participant';
    default:
      return 'Task';
  }
};

const extensionJson = (element: Element, localName: string): any | undefined => {
  const child = Array.from(element.children).find((candidate) =>
    candidate.namespaceURI === EASY_NS && candidate.localName === localName
  ) || Array.from(element.getElementsByTagNameNS(EASY_NS, localName))[0];
  if (!child?.textContent) return undefined;
  try {
    return JSON.parse(child.textContent);
  } catch {
    return undefined;
  }
};

const uniqueElements = (elements: Element[]): Element[] => Array.from(new Set(elements));

export const parseWorkflowDefinition = (raw: string): WorkflowDefinition => {
  if (!raw.trimStart().startsWith('<')) {
    return JSON.parse(raw) as WorkflowDefinition;
  }

  const doc = new DOMParser().parseFromString(raw, 'application/xml');
  if (doc.getElementsByTagName('parsererror')[0]) {
    throw new Error('Invalid BPMN XML');
  }
  const process = doc.getElementsByTagNameNS(BPMN_NS, 'process')[0]
    || Array.from(doc.getElementsByTagName('*')).find((element) => element.localName === 'process');
  if (!process) throw new Error('BPMN XML must contain bpmn:process');

  const boundsById = new Map<string, any>();
  const shapes = uniqueElements([
    ...Array.from(doc.getElementsByTagNameNS(BPMNDI_NS, 'BPMNShape')),
    ...Array.from(doc.getElementsByTagName('*')).filter((element) => element.localName === 'BPMNShape')
  ]);
  shapes.forEach((shape) => {
    const id = shape.getAttribute('bpmnElement') || '';
    const bounds = shape.getElementsByTagNameNS(DC_NS, 'Bounds')[0]
      || Array.from(shape.getElementsByTagName('*')).find((element) => element.localName === 'Bounds');
    if (!id || !bounds) return;
    boundsById.set(id, {
      position: { x: Number(bounds.getAttribute('x')) || 100, y: Number(bounds.getAttribute('y')) || 100 },
      width: Number(bounds.getAttribute('width')) || 120,
      height: Number(bounds.getAttribute('height')) || 60
    });
  });

  const nodes = Array.from(process.children)
    .filter((element) => element.localName !== 'sequenceFlow' && element.localName !== 'extensionElements')
    .map((element) => {
      const extended = extensionJson(element, 'node') || {};
      return {
        ...extended,
        id: extended.id || element.getAttribute('id') || '',
        name: extended.name || element.getAttribute('name') || element.getAttribute('id') || '',
        type: extended.type || elementToType(element),
        attachedTo: extended.attachedTo || element.getAttribute('attachedToRef') || undefined,
        ...(boundsById.get(element.getAttribute('id') || '') || {})
      };
    });

  const sequenceFlows = uniqueElements([
    ...Array.from(process.getElementsByTagNameNS(BPMN_NS, 'sequenceFlow')),
    ...Array.from(process.getElementsByTagName('*')).filter((element) => element.localName === 'sequenceFlow')
  ]);
  const flows = sequenceFlows.map((flow) => ({
    from: flow.getAttribute('sourceRef') || '',
    to: flow.getAttribute('targetRef') || '',
    condition: flow.getElementsByTagNameNS(BPMN_NS, 'conditionExpression')[0]?.textContent || null
  }));

  return {
    processId: process.getAttribute('id') || undefined,
    name: process.getAttribute('name') || undefined,
    metadata: extensionJson(process, 'metadata'),
    nodes,
    flows
  };
};
