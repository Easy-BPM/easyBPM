
// Generic types for the BPM engine

export enum TaskStatus {
  PENDING = 'PENDING',
  COMPLETED = 'COMPLETED',
  CANCELED = 'CANCELED'
}

export interface ProcessDefinition {
  id: string;
  key: string;
  processName?: string;
  description: string;
  version: number;
}

// Document metadata returned by POST /api/documents
export interface DocumentMetadata {
  id: string; // UUID
  fileName: string;
  contentType: string;
  fileSize: number;
  taskId: number | null;
  processInstanceId: number | null;
  formFieldKey: string | null;
  uploadedBy: string | null;
  createdAt: string;
}

// JSON Schema Types based on provided format
// Example: {"type": "object", "title": "...", "required": [...], "properties": {...}}
export interface JsonSchemaProperty {
  type: 'string' | 'number' | 'integer' | 'boolean' | 'date' | 'object' | 'array';
  title?: string;
  description?: string;
  enum?: (string | number)[];
  /**
   * Extended format values (beyond JSON Schema standard):
   *   - 'textarea'    → multi-line text area
   *   - 'date'        → date picker
   *   - 'fileUpload'  → file upload component (value = document UUID)
   *   - 'fileDownload'→ file download button (value = document UUID)
   *   - 'pdfViewer'   → inline PDF viewer (value = document UUID)
   */
  format?: string;
  // File-field specific constraints (present when format is fileUpload/fileDownload/pdfViewer)
  allowedExtensions?: string[];
  maxSizeMb?: number;
  readOnly?: boolean;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
  minimum?: number;
  maximum?: number;
  multipleOf?: number;
  includeTime?: boolean;
  minDate?: string;
  maxDate?: string;
  formatMinimum?: string;
  formatMaximum?: string;
}

export interface JsonSchema {
  type: 'object';
  title?: string;
  description?: string;
  required?: string[];
  properties: Record<string, JsonSchemaProperty>;
}

// Entity: com.easy.bpm.model.form.Form
export interface Form {
  id: number;
  formId: string;
  name: string;
  schema: JsonSchema; // Mapped to jsonb column
  version: number;
  createdAt: string;
}

// Entity: com.easy.bpm.model.task.Task
export interface Task {
  id: number;
  title: string;
  processInstanceId: number;
  nodeId: string;
  assignee: string | null;
  candidateUsers?: string[];
  candidateGroups?: string[];
  status: TaskStatus;
  createdAt: string; // ISO Date
  completedAt: string | null;
  formDbId: number | null;
  formId?: string | null;

  // UI Helper fields (might be enriched by the API or separate call)
  name?: string;
  description?: string;
  variables?: Record<string, any>;
}

// Payload for completing a task
// POST /tasks/id/complete
export interface CompleteTaskPayload {
  assignee: string;
  variables: Record<string, any>;
}

export interface AuthSession {
  token: string;
  username: string;
  groups: string[];
  permissions: string[];
}

export interface AuthLoginResponse {
  token: string;
  tokenType: string;
  username: string;
  groups: string[];
  permissions: string[];
}

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}
