export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

export interface ProcessDefinition {
  id: number;
  name: string;
  key?: string;
  description?: string;
  version: number;
  definitionJson?: string;
}

export interface ProcessDefinitionSummary {
  id: number;
  name: string;
  key?: string;
  description?: string;
  version: number;
}

export interface WorkflowNode {
  id: string;
  name?: string;
  type: string;
  position?: { x: number; y: number };
  width?: number;
  height?: number;
  next?: string[];
  attachedTo?: string;  // For boundary events: parent node ID
  config?: Record<string, unknown>;  // For error/message/timer boundaries
}

export interface WorkflowFlow {
  from: string;
  to: string;
  condition?: string | null;
}

export interface WorkflowDefinition {
  processId?: string;
  key?: string;
  name?: string;
  description?: string;
  metadata?: Record<string, unknown>;
  nodes: WorkflowNode[];
  flows?: WorkflowFlow[];
}

export interface ProcessInstance {
  id: number;
  processDefinitionId?: number;
  processDefinitionName?: string;
  processDefinition?: ProcessDefinitionSummary;
  status: string;
  currentNode?: string[];
  nodeHistory?: string[];
  createdAt: string;
  updatedAt: string;
  parentInstanceId?: number;
  callActivityNodeId?: string;
  nestingLevel?: number;
}

export interface ProcessVariable {
  name: string;
  value: unknown;
}

export interface NodeHistoryItem {
  nodeId: string;
  timestamp?: string;
}

export interface CallActivityMapping {
  id?: number;
  parentInstanceId: number;
  childInstanceId: number;
  callActivityNodeId: string;
  inputMappings?: Record<string, string>;
  outputMappings?: Record<string, string>;
  propagateAllVariables?: boolean;
}

export interface MoveNodePayload {
  fromNode: string;
  toNode: string;
  reason?: string;
}

export interface VariableAssignmentPayload {
  variables: Record<string, unknown>;
}
