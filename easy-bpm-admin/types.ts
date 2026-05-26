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

// ====== Execution Metrics Types ======

export interface ExecutionMetricsDto {
  total: number;
  running: number;
  completed: number;
  failed: number;
  suspended: number;
  incidents: number;
  timestamp: number;
}

export interface ProcessMetricsDto {
  processId: string;
  processName: string;
  total: number;
  running: number;
  completed: number;
  failed: number;
  suspended: number;
  avgExecutionTimeMs: number;
  lastExecutedAt: string | null;
  successRate: number;
}

export interface ExecutionTimeStatsDto {
  processId: string | null;
  avgExecutionTimeMs: number;
  minExecutionTimeMs: number;
  maxExecutionTimeMs: number;
  p50LatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  totalExecutions: number;
}

export interface TrendDataPoint {
  timestamp: number;
  avgExecutionTimeMs: number;
  completedCount: number;
  failedCount: number;
}

export enum SLAStatus {
  COMPLIANT = 'COMPLIANT',
  AT_RISK = 'AT_RISK',
  VIOLATED = 'VIOLATED'
}

export interface SLAStatusDto {
  instanceId: number;
  processId: string;
  status: SLAStatus;
  thresholdMinutes: number;
  elapsedTimeMinutes: number;
  percentageUsed: number;
}

// ====== Phase 9.2: Process & Incident Management Types ======

export interface ProcessListItemDto {
  processId: string;
  processName: string;
  version: number;
  totalInstances: number;
  runningInstances: number;
  completedInstances: number;
  failedInstances: number;
  suspendedInstances: number;
  incidentCount: number;
  avgExecutionTimeMs: number;
  successRate: number;
  lastExecutedAt: string | null;
  createdAt: string | null;
}

export interface ProcessListResponseDto {
  content: ProcessListItemDto[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

export interface IncidentDto {
  instanceId: number;
  processId: string;
  processName: string;
  status: string;
  errorMessage: string | null;
  errorType: string | null;
  currentNode: string | null;
  createdAt: string;
  updatedAt: string;
  nestingLevel: number;
  parentInstanceId: number | null;
}

export interface IncidentsResponseDto {
  content: IncidentDto[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  hasUnacknowledged: boolean;
}

export interface ProcessFilterDto {
  processId?: string | null;
  status?: string | null;
  fromDate?: string | null;
  toDate?: string | null;
  nestingLevel?: number | null;
  minSuccessRate?: number | null;
  maxExecutionTimeMs?: number | null;
  page: number;
  pageSize: number;
  sortBy: string;
  sortDirection: string;
}

export interface IncidentFilterDto {
  status?: string | null;
  processId?: string | null;
  fromDate?: string | null;
  toDate?: string | null;
  acknowledged?: boolean | null;
  page: number;
  pageSize: number;
  sortBy: string;
  sortDirection: string;
}

export interface BulkActionDto {
  action: string;
  instanceIds: number[];
}

export interface BulkActionResultDto {
  action: string;
  totalProcessed: number;
  successful: number;
  failed: number;
  errors: string[];
}

// ====== Phase 9.3: Analytics Extension Types ======

export enum ActivityType {
  INSTANCE_CREATED = 'INSTANCE_CREATED',
  INSTANCE_COMPLETED = 'INSTANCE_COMPLETED',
  INSTANCE_FAILED = 'INSTANCE_FAILED',
  INSTANCE_SUSPENDED = 'INSTANCE_SUSPENDED',
  NODE_EXECUTED = 'NODE_EXECUTED',
  TASK_CREATED = 'TASK_CREATED',
  TASK_COMPLETED = 'TASK_COMPLETED',
  VARIABLE_UPDATED = 'VARIABLE_UPDATED',
  CALL_ACTIVITY_STARTED = 'CALL_ACTIVITY_STARTED',
  ERROR_CAUGHT = 'ERROR_CAUGHT',
  INCIDENT_CREATED = 'INCIDENT_CREATED'
}

export enum SLAStatusEnum {
  MET = 'MET',
  AT_RISK = 'AT_RISK',
  VIOLATED = 'VIOLATED',
  NOT_APPLICABLE = 'NOT_APPLICABLE'
}

export interface ExecutionTrendDto {
  timestamp: number;
  averageExecutionTimeMs: number;
  minExecutionTimeMs: number;
  maxExecutionTimeMs: number;
  instanceCount: number;
  successCount: number;
  failureCount: number;
}

export interface ExecutionTrendsResponseDto {
  processId: string | null;
  period: string;
  trends: ExecutionTrendDto[];
  overallAverageMs: number;
  overallMedianMs: number;
  p95Ms: number;
  p99Ms: number;
}

export interface SLAMetricDto {
  instanceId: number;
  processId: string;
  processName: string;
  currentNode: string | null;
  createdAt: string;
  targetDurationMs: number;
  currentDurationMs: number;
  status: SLAStatusEnum;
  percentageComplete: number;
}

export interface SLAPercentageDto {
  met: number;
  atRisk: number;
  violated: number;
}

export interface SLAMetricsResponseDto {
  totalInstances: number;
  metInstances: number;
  atRiskInstances: number;
  violatedInstances: number;
  metricsPercentage: SLAPercentageDto;
  criticalInstances: SLAMetricDto[];
  timestamp: number;
}

export interface ActivityFeedItemDto {
  id: number;
  timestamp: string;
  type: ActivityType;
  processId: string;
  processName: string;
  instanceId: number;
  nodeId: string | null;
  nodeName: string | null;
  description: string;
  severity: string; // INFO, WARNING, ERROR
  metadata: Record<string, unknown> | null;
}

export interface ActivityFeedResponseDto {
  items: ActivityFeedItemDto[];
  totalCount: number;
  hasMore: boolean;
  generatedAt: string;
}

export interface ProcessFailureRateDto {
  processId: string;
  processName: string;
  totalInstances: number;
  failedInstances: number;
  failureRate: number;
}

export interface AnalyticsSummaryDto {
  period: string;
  totalProcesses: number;
  totalInstances: number;
  completedInstances: number;
  failedInstances: number;
  suspendedInstances: number;
  runningInstances: number;
  averageExecutionTimeMs: number;
  successRate: number;
  slaMetStatus: SLAPercentageDto;
  incidentsCount: number;
  recentActivities: ActivityFeedItemDto[];
  topFailingProcesses: ProcessFailureRateDto[];
}

