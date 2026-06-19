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
  errorMessage?: string | null;
  errorNodeId?: string | null;
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

export type TaskStatus = 'PENDING' | 'COMPLETED';

export interface BpmTask {
  id: number;
  title?: string | null;
  name: string;
  description?: string | null;
  processInstanceId: number;
  nodeId: string;
  assignee?: string | null;
  candidateUsers: string[];
  candidateGroups: string[];
  status: TaskStatus;
  createdAt: string;
  completedAt?: string | null;
  formDbId?: number | null;
  formId?: string | null;
  variables: Record<string, unknown>;
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

export interface AuthLoginResponse {
  token: string;
  tokenType: string;
  username: string;
  groups: string[];
  permissions: string[];
}

export interface AuthCurrentUser {
  username: string;
  groups: string[];
  permissions: string[];
}

export interface AuthSession {
  token: string;
  username: string;
  groups: string[];
  permissions: string[];
}

export interface AdminUser {
  id: number;
  username: string;
  enabled: boolean;
  groups: string[];
  permissions: string[];
}

export interface AdminGroup {
  id: number;
  code: string;
  name: string;
  permissions: string[];
}
