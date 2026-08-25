export type NodeType = 'start' | 'end' | 'user-task' | 'service-task' | 'api-task' | 'code-task' | 'ai-task' | 'agent-process-call' | 'call-activity' | 'gateway' | 'parallel-gateway' | 'timer-event' | 'message-start' | 'message-intermediate-catch' | 'message-intermediate-throw' | 'error-boundary' | 'message-boundary' | 'timer-boundary' | 'pool' | 'documentation';

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
  // AI Task specific (BETA)
  aiProviderId?: string;                 // Provider: 'openai', 'anthropic', etc.
  aiModelName?: string;                  // Model: 'gpt-4', 'claude-3-opus', etc.
  aiCredentialId?: string;               // Reference to stored credential
  aiCredentialRefName?: string;          // Environment variable reference for credential
  aiUserPrompt?: string;                 // User-facing prompt message
  aiPromptTemplate?: string;             // Prompt with {{variable}} placeholders
  aiSystemPrompt?: string;               // System role for AI
  aiEndpoint?: string;                   // Custom endpoint (for OpenAI/Azure)
  aiOutputVariable?: string;             // Variable to store AI response
  aiTuningParams?: {                     // AI tuning parameters (BETA)
    temperature?: number;                // 0.0-2.0 for OpenAI
    topP?: number;                       // 0.0-1.0
    maxTokens?: number;
    frequencyPenalty?: number;           // -2.0 to 2.0
    presencePenalty?: number;            // -2.0 to 2.0
    retryCount?: number;                 // Number of retries
    backoffMultiplier?: number;          // Exponential backoff
    initialDelayMs?: number;             // Initial retry delay
  };
  // Agent process invocation specific (feature flagged)
  agentProcessKey?: string;
  agentGoalOverride?: string;
  agentWaitForCompletion?: boolean;
  agentTimeoutDays?: number | null;
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
  waypoints?: Position[]; // Optional visual bend points for manual routing
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

export type AppView = 'bpmn' | 'xml' | 'forms';

export interface ViewState {
  scale: number;
  offset: Position;
}

export interface FormField {
  id: string;
  name: string;
  title: string;
  type: 'string' | 'number' | 'text' | 'boolean' | 'radio' | 'select' | 'date' | 'fileUpload' | 'fileDownload' | 'pdfViewer';
  required: boolean;
  readOnly: boolean;
  options?: string[];
  defaultValue?: string;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
  minimum?: number;
  maximum?: number;
  multipleOf?: number;
  includeTime?: boolean;
  minDate?: string;
  maxDate?: string;
  // File-field specific
  allowedExtensions?: string[];
  maxSizeMb?: number;
}

export interface FormTab {
  id: string;
  name: string;
  fields: FormField[];
}

export interface FormDefinition {
  id: string;
  name: string;
  formKey?: string;
  tabs: FormTab[];
}
