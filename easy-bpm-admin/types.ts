export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

export interface ProcessDefinition {
  id: string;
  name: string;
  key?: string;
  description?: string;
  version: number;
}

export interface ProcessInstance {
  id: number;
  processDefinitionId?: number;
  processDefinitionName?: string;
  status: string;
  currentNode?: string[];
  nodeHistory?: string[];
  createdAt: string;
  updatedAt: string;
}

export interface ProcessVariable {
  name: string;
  value: unknown;
}

export interface NodeHistoryItem {
  nodeId: string;
  timestamp?: string;
}

export interface MoveNodePayload {
  fromNode: string;
  toNode: string;
  reason?: string;
}

export interface VariableAssignmentPayload {
  variables: Record<string, unknown>;
}
