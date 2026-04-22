export type NodeType = 'start' | 'end' | 'user-task' | 'service-task' | 'api-task' | 'call-activity' | 'gateway' | 'parallel-gateway' | 'timer-event' | 'message-start' | 'message-intermediate-catch' | 'message-intermediate-throw' | 'error-boundary' | 'message-boundary' | 'timer-boundary';

export interface Position {
  x: number;
  y: number;
}

export interface TaskVariable {
  id: string;
  name: string; // Variable name inside the Task context
  type: 'string' | 'number' | 'boolean' | 'json';
  mappingType: 'static' | 'variable'; // Is it a literal value or a link to a global process variable?
  value: string; // The literal value OR the name of the global process variable
}

export type ApiAuthType = 'none' | 'bearer' | 'basic' | 'apikey';

export interface NodeData {
  label: string;
  description?: string;
  // Human Task specific
  assignee?: string;
  candidateGroups?: string;
  formId?: string;
  inputVariables?: TaskVariable[]; // Mapping: Global -> Task
  outputVariables?: TaskVariable[]; // Mapping: Task -> Global
  // Service Task specific
  apiEndpoint?: string; 
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  headers?: string; 
  body?: string; 
  apiAuthType?: ApiAuthType;
  apiAuthRef?: string;
  apiAuthIn?: 'header' | 'query';
  apiAuthKey?: string;
  // Call Activity specific
  callActivityProcessKey?: string;      // Target subprocess process key
  inputMappings?: Record<string, string>;  // parent var -> child var
  outputMappings?: Record<string, string>; // child var -> parent var
  propagateAllVariables?: boolean;      // Copy all variables (default false)
  // Gateway specific
  condition?: string;
  // Message Event specific
  messageName?: string;
  correlationKeys?: string;
  timeoutSeconds?: number | null;
  // Error Boundary specific
  errorCode?: string;
  // Timer Boundary specific
  interrupting?: boolean;
}

export interface BpmnNode {
  uid: string; // Internal unique identifier for React/Logic
  id: string;  // User-editable BPMN Element ID
  type: NodeType;
  position: Position;
  data: NodeData;
  width: number;
  height: number;
  attachedTo?: string; // UID of the node this boundary event is attached to
}

export interface BpmnEdge {
  id: string;
  source: string; // Points to node.uid
  target: string; // Points to node.uid
  label?: string;
  condition?: string; 
}

export interface ProcessVariable {
  id: string;
  name: string;
  type: 'string' | 'number' | 'boolean' | 'json';
  defaultValue: string;
}

export interface ProcessModel {
  nodes: BpmnNode[];
  edges: BpmnEdge[];
  variables: ProcessVariable[];
}

export type ValidationSeverity = 'error' | 'warning';

export interface ValidationIssue {
  id: string;
  severity: ValidationSeverity;
  message: string;
  nodeUid?: string;
  edgeId?: string;
  nodeId?: string;
}

export interface ValidationSummary {
  isValid: boolean;
  duplicateNodeIds: string[];
  duplicateGlobalVars: string[];
  hasTaskVarDuplicates: boolean;
  errors: string[];
  warnings: string[];
  issues: ValidationIssue[];
}

export type ToolMode = 'select' | 'connect';

export type AppView = 'bpmn' | 'forms';

export interface ViewState {
  scale: number;
  offset: Position;
}

export interface FormField {
  id: string;
  name: string;
  title: string;
  type: 'string' | 'number' | 'text' | 'boolean' | 'radio' | 'select' | 'date';
  required: boolean;
  readOnly: boolean;
  options?: string[];
  defaultValue?: string;
}

export interface FormTab {
  id: string;
  name: string;
  fields: FormField[];
}

export interface FormDefinition {
  id: string;
  name: string;
  tabs: FormTab[];
}
