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

export type ProcessInstanceEventType =
  | 'PROCESS_STARTED'
  | 'NODE_ENTERED'
  | 'TASK_CREATED'
  | 'TASK_CLAIMED'
  | 'TASK_COMPLETED'
  | 'WORKER_REQUESTED'
  | 'WORKER_COMPLETED'
  | 'WORKER_FAILED'
  | 'MESSAGE_WAITING'
  | 'MESSAGE_RECEIVED'
  | 'MESSAGE_THROWN'
  | 'TIMER_WAITING'
  | 'TIMER_TRIGGERED'
  | 'GATEWAY_EVALUATED'
  | 'MANUAL_MOVE'
  | 'INCIDENT_CREATED'
  | 'INCIDENT_RETRY_REQUESTED'
  | 'INCIDENT_RESOLVED'
  | 'PROCESS_COMPLETED'
  | 'PROCESS_FAILED'
  | 'PROCESS_CANCELLED';

export interface ProcessInstanceEvent {
  id: number;
  processInstanceId: number;
  nodeId?: string | null;
  eventType: ProcessInstanceEventType;
  message: string;
  actor?: string | null;
  details?: string | null;
  createdAt: string;
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

export type IncidentStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
export type IncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type IncidentSource = 'PROCESS_ENGINE' | 'WORKER' | 'CODE_TASK' | 'AI_TASK' | 'MESSAGE';
export type IncidentResolutionAction = 'RESOLVED_MANUALLY' | 'VARIABLE_FIXED' | 'RETRIED_SUCCESSFULLY' | 'IGNORED_KNOWN_ISSUE' | 'INSTANCE_CANCELLED';
export type IncidentEventType = 'CREATED' | 'OCCURRED_AGAIN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'REOPENED' | 'RETRY_REQUESTED';

export interface Incident {
  id: number;
  processInstanceId: number;
  nodeId?: string | null;
  status: IncidentStatus;
  severity: IncidentSeverity;
  source: IncidentSource;
  message: string;
  technicalDetails?: string | null;
  externalReferenceId?: string | null;
  occurrenceCount: number;
  lastOccurredAt: string;
  createdAt: string;
  updatedAt: string;
  acknowledgedAt?: string | null;
  acknowledgedBy?: string | null;
  resolvedAt?: string | null;
  resolvedBy?: string | null;
  resolutionNote?: string | null;
  resolutionAction?: IncidentResolutionAction | null;
}

export interface IncidentEvent {
  id: number;
  incidentId: number;
  eventType: IncidentEventType;
  message: string;
  actor?: string | null;
  createdAt: string;
}

export interface IncidentSummary {
  openIncidents: number;
  criticalIncidents: number;
  acknowledgedIncidents: number;
  incidentsCreatedToday: number;
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

export interface PurgeCompletedInstancesPayload {
  completedBefore: string;
  processDefinitionId?: number | null;
  processKey?: string | null;
  dryRun: boolean;
}

export interface MaintenanceCleanupSummary {
  dryRun: boolean;
  processDefinitionsDeleted: number;
  processInstancesDeleted: number;
  tasksDeleted: number;
  processVariablesDeleted: number;
  taskVariablesDeleted: number;
  documentsDeleted: number;
  messageSubscriptionsDeleted: number;
  workerRequestsDeleted: number;
  codeTaskExecutionsDeleted: number;
  incidentsDeleted: number;
  incidentEventsDeleted: number;
  timelineEventsDeleted: number;
  callActivityMappingsDeleted: number;
  candidateInstanceIds: number[];
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
