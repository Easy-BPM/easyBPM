import { Task, TaskStatus, ProcessDefinition, CompleteTaskPayload, Page, Form, AuthLoginResponse, AuthSession, DocumentMetadata } from '../types';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const USE_MOCK = false;
const AUTH_STORAGE_KEY = 'easybpm_portal_auth';

const MOCK_PROCESSES: ProcessDefinition[] = [
  { id: 'proc-1', key: 'hiring-process', name: 'Employee Hiring', description: 'Standard onboarding workflow for new hires', version: 1 },
  { id: 'proc-2', key: 'expense-approval', name: 'Expense Approval', description: 'Approval chain for expenses over $500', version: 2 },
  { id: 'proc-3', key: 'document-review', name: 'Document Review', description: 'Legal department review process', version: 1 },
];

const MOCK_FORMS: Record<number, Form> = {
  100: {
    id: 100,
    formId: 'integrationResultForm',
    name: 'Integration Result Form',
    version: 1,
    createdAt: new Date().toISOString(),
    schema: {
      type: 'object',
      title: 'Resultados da Integracao',
      required: ['resultadoApi'],
      properties: {
        resultadoApi: { type: 'string', title: 'Resultado retornado da API', description: 'O valor recebido do sistema externo' },
        statusCodigo: { type: 'number', title: 'HTTP Status Code' },
        detalhes: { type: 'string', title: 'Detalhes do Processamento' }
      }
    }
  },
  101: {
    id: 101,
    formId: 'approvalForm',
    name: 'Approval Form',
    version: 1,
    createdAt: new Date().toISOString(),
    schema: {
      type: 'object',
      title: 'Aprovacao de Tarefa',
      required: ['aprovado'],
      properties: {
        aprovado: { type: 'boolean', title: 'Aprovar Solicitacao?', description: 'Marque para aprovar, desmarque para rejeitar' },
        comentarioInicial: { type: 'string', title: 'Comentarios do Aprovador' },
        prioridade: { type: 'string', title: 'Prioridade', enum: ['Baixa', 'Media', 'Alta'] }
      }
    }
  }
};

const MOCK_TASKS: Task[] = [
  {
    id: 1,
    title: 'Review Integration Data',
    name: 'Review Integration Data',
    processInstanceId: 501,
    nodeId: 'user-task-review',
    assignee: 'joao',
    status: TaskStatus.PENDING,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(),
    completedAt: null,
    formDbId: 100,
    formId: 'integrationResultForm',
    description: 'Please verify the output from the legacy system integration.',
    variables: {
      resultadoApi: 'Success: Transaction 998877',
      statusCodigo: 200
    }
  },
  {
    id: 2,
    title: 'Manager Approval',
    name: 'Manager Approval',
    processInstanceId: 502,
    nodeId: 'user-task-approve',
    assignee: 'joao',
    status: TaskStatus.PENDING,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString(),
    completedAt: null,
    formDbId: 101,
    formId: 'approvalForm',
    description: 'Approve the new expense report submitted by Alice.',
    variables: {
      comentarioInicial: 'Tudo certo'
    }
  }
];

const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

const assertOk = async (response: Response, operation: string) => {
  if (response.ok) return;
  const body = await response.text().catch(() => '');
  throw new Error(`${operation} failed (${response.status}): ${body || response.statusText}`);
};

const getSession = (): AuthSession | null => {
  const raw = localStorage.getItem(AUTH_STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthSession;
  } catch {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    return null;
  }
};

const authHeaders = (): HeadersInit => {
  const session = getSession();
  return session?.token ? { Authorization: `Bearer ${session.token}` } : {};
};

const fetchWithAuth = (url: string, init?: RequestInit) => fetch(url, {
  ...init,
  headers: {
    ...(init?.headers ?? {}),
    ...authHeaders()
  }
});

export const bpmService = {
  getSession: (): AuthSession | null => getSession(),

  clearSession: (): void => {
    localStorage.removeItem(AUTH_STORAGE_KEY);
  },

  login: async (username: string, password: string): Promise<AuthSession> => {
    if (USE_MOCK) {
      await delay(200);
      return { username, token: `mock-jwt-token-${Date.now()}`, groups: ['ADMIN'], permissions: ['ACCESS_PROCESS_PORTAL'] };
    }

    const url = `${API_BASE_URL}/auth/login`;
    const body = JSON.stringify({ username, password });
    console.log('Logging in to:', url, 'with body:', body);
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body
    });
    await assertOk(res, 'Login');
    const payload = await res.json() as AuthLoginResponse;
    const session: AuthSession = {
      token: payload.token,
      username: payload.username,
      groups: payload.groups,
      permissions: payload.permissions
    };
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
    return session;
  },

  me: async (): Promise<{ username: string; groups: string[]; permissions: string[] }> => {
    const response = await fetchWithAuth(`${API_BASE_URL}/auth/me`);
    await assertOk(response, 'Get current user');
    return response.json();
  },

  startProcess: async (processKey: string): Promise<any> => {
    if (USE_MOCK) {
      await delay(300);
      return { id: Math.floor(Math.random() * 1000), key: processKey };
    }

    const res = await fetchWithAuth(`${API_BASE_URL}/processes/${processKey}/start`, { method: 'POST' });
    await assertOk(res, 'Start process');
    return res.json();
  },

  getProcesses: async (page = 0, size = 10): Promise<Page<ProcessDefinition>> => {
    if (USE_MOCK) {
      await delay(200);
      return {
        content: MOCK_PROCESSES,
        totalPages: 1,
        totalElements: MOCK_PROCESSES.length,
        size,
        number: page
      };
    }

    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const response = await fetchWithAuth(`${API_BASE_URL}/processes?${params.toString()}`);
    await assertOk(response, 'Get processes');
    return response.json();
  },

  getTasks: async (assignee?: string): Promise<Task[]> => {
    if (USE_MOCK) {
      await delay(200);
      const tasks = [...MOCK_TASKS];
      return assignee ? tasks.filter(t => t.assignee === assignee) : tasks;
    }

    const params = new URLSearchParams({ page: '0', size: '100' });
    if (assignee) params.append('assignee', assignee);

    const response = await fetchWithAuth(`${API_BASE_URL}/tasks/search?${params.toString()}`);
    await assertOk(response, 'Get tasks');
    const taskPage: Page<Task> = await response.json();
    return taskPage.content;
  },

  getTaskById: async (id: number): Promise<Task> => {
    if (USE_MOCK) {
      await delay(100);
      const task = MOCK_TASKS.find(t => t.id === id);
      if (!task) throw new Error('Task not found');
      return task;
    }

    const response = await fetchWithAuth(`${API_BASE_URL}/tasks/${id}`);
    await assertOk(response, `Get task ${id}`);
    return response.json();
  },

  getFormById: async (id: number): Promise<Form> => {
    if (USE_MOCK) {
      await delay(100);
      const form = MOCK_FORMS[id];
      if (!form) throw new Error('Form not found');
      return form;
    }

    const response = await fetchWithAuth(`${API_BASE_URL}/forms/${id}`);
    await assertOk(response, `Get form ${id}`);
    return response.json();
  },

  completeTask: async (id: number, payload: CompleteTaskPayload): Promise<void> => {
    if (USE_MOCK) {
      await delay(300);
      const tIndex = MOCK_TASKS.findIndex(t => t.id === id);
      if (tIndex > -1) {
        MOCK_TASKS[tIndex].status = TaskStatus.COMPLETED;
        MOCK_TASKS[tIndex].completedAt = new Date().toISOString();
        MOCK_TASKS[tIndex].variables = payload.variables;
      }
      return;
    }

    const response = await fetchWithAuth(`${API_BASE_URL}/tasks/${id}/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    await assertOk(response, `Complete task ${id}`);
  },

  claimTask: async (id: number): Promise<void> => {
    const response = await fetchWithAuth(`${API_BASE_URL}/tasks/${id}/claim`, {
      method: 'POST'
    });
    await assertOk(response, `Claim task ${id}`);
  },

  // -------------------------------------------------------------------------
  // Document handling
  // -------------------------------------------------------------------------

  /** Upload a file, optionally associating it with a task and form field. Returns metadata including the UUID. */
  uploadDocument: async (
    file: File,
    taskId?: number,
    processInstanceId?: number,
    formFieldKey?: string
  ): Promise<DocumentMetadata> => {
    const formData = new FormData();
    formData.append('file', file);
    if (taskId !== undefined) formData.append('taskId', String(taskId));
    if (processInstanceId !== undefined) formData.append('processInstanceId', String(processInstanceId));
    if (formFieldKey) formData.append('formFieldKey', formFieldKey);

    const response = await fetchWithAuth(`${API_BASE_URL}/api/documents`, {
      method: 'POST',
      body: formData
    });
    await assertOk(response, 'Upload document');
    return response.json() as Promise<DocumentMetadata>;
  },

  /** Retrieve document metadata (no binary content). */
  getDocumentMetadata: async (id: string): Promise<DocumentMetadata> => {
    const response = await fetchWithAuth(`${API_BASE_URL}/api/documents/${id}`);
    await assertOk(response, `Get document metadata ${id}`);
    return response.json() as Promise<DocumentMetadata>;
  },

  /** Returns the URL to download a document (attachment). Suitable for <a href> usage. */
  getDocumentDownloadUrl: (id: string): string =>
    `${API_BASE_URL}/api/documents/${id}/download`,

  /** Returns the URL to preview a document inline (inline for PDFs). Suitable for <iframe src> usage. */
  getDocumentPreviewUrl: (id: string): string =>
    `${API_BASE_URL}/api/documents/${id}/preview`,

  /** Delete a document by UUID. */
  deleteDocument: async (id: string): Promise<void> => {
    const response = await fetchWithAuth(`${API_BASE_URL}/api/documents/${id}`, {
      method: 'DELETE'
    });
    await assertOk(response, `Delete document ${id}`);
  },

  /** List documents associated with a task. */
  listDocumentsByTask: async (taskId: number): Promise<DocumentMetadata[]> => {
    const response = await fetchWithAuth(`${API_BASE_URL}/api/documents?taskId=${taskId}`);
    await assertOk(response, `List documents for task ${taskId}`);
    return response.json() as Promise<DocumentMetadata[]>;
  },
};
