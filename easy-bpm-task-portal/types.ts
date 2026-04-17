
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

// JSON Schema Types based on provided format
// Example: {"type": "object", "title": "...", "required": [...], "properties": {...}}
export interface JsonSchemaProperty {
  type: 'string' | 'number' | 'integer' | 'boolean' | 'object' | 'array';
  title?: string;
  description?: string;
  enum?: (string | number)[];
  format?: string; 
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
  key?: string;
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
  status: TaskStatus;
  createdAt: string; // ISO Date
  completedAt: string | null;
  formId: number | null;
  formKey?: string | null;

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

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}
