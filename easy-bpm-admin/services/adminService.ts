import {
  AdminGroup,
  AdminUser,
  AuthCurrentUser,
  AuthLoginResponse,
  AuthSession,
  BpmTask,
  CallActivityMapping,
  DataRetentionSettings,
  Incident,
  IncidentEvent,
  IncidentResolutionAction,
  IncidentSource,
  IncidentSummary,
  IncidentStatus,
  MoveNodePayload,
  Page,
  ProcessDefinition,
  ProcessInstanceEvent,
  ProcessInstance,
  ProcessVariable,
  TaskStatus,
  MaintenanceCleanupSummary,
  PurgeCompletedInstancesPayload,
  PurgeCompletedTasksPayload,
  UpdateDataRetentionSettingsPayload,
  VariableAssignmentPayload
} from '../types';

const API_BASE_URL = (import.meta.env.EASY_BPM_ADMIN_API_BASE_URL as string | undefined) ?? 'http://localhost:8080';
const USE_MOCK = false;
const AUTH_STORAGE_KEY = 'easybpm_admin_auth';

const MOCK_INSTANCES: ProcessInstance[] = [
  {
    id: 1001,
    processDefinitionId: 1,
    processDefinitionName: 'Order Fulfillment',
    status: 'ACTIVE',
    currentNode: ['user-review'],
    nodeHistory: ['start', 'validate-order', 'user-review'],
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 6).toISOString(),
    updatedAt: new Date(Date.now() - 1000 * 60 * 10).toISOString()
  },
  {
    id: 1002,
    processDefinitionId: 2,
    processDefinitionName: 'Expense Approval',
    status: 'ACTIVE',
    currentNode: ['manager-approval'],
    nodeHistory: ['start', 'submit-expense', 'manager-approval'],
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString(),
    updatedAt: new Date(Date.now() - 1000 * 60 * 60).toISOString()
  }
];

const MOCK_DEFINITIONS: ProcessDefinition[] = [
  {
    id: 1,
    name: 'Order Fulfillment',
    key: 'order-fulfillment',
    description: 'Handle customer orders end-to-end',
    version: 3,
    definitionJson:
      '{"processId":"order-fulfillment","nodes":[{"id":"start","name":"Start","type":"StartEvent","position":{"x":120,"y":220},"next":["review"]},{"id":"review","name":"Review Order","type":"HumanTask","position":{"x":320,"y":210},"next":["end"]},{"id":"end","name":"End","type":"EndEvent","position":{"x":560,"y":220},"next":[]}],"flows":[{"from":"start","to":"review","condition":null},{"from":"review","to":"end","condition":null}]}'
  },
  {
    id: 2,
    name: 'Expense Approval',
    key: 'expense-approval',
    description: 'Approval workflow for expenses',
    version: 2,
    definitionJson:
      '{"processId":"expense-approval","nodes":[{"id":"start","name":"Start","type":"StartEvent","position":{"x":120,"y":220},"next":["managerApproval"]},{"id":"managerApproval","name":"Manager Approval","type":"HumanTask","position":{"x":340,"y":210},"next":["end"]},{"id":"end","name":"End","type":"EndEvent","position":{"x":580,"y":220},"next":[]}],"flows":[{"from":"start","to":"managerApproval","condition":null},{"from":"managerApproval","to":"end","condition":null}]}'
  }
];

const MOCK_VARIABLES: Record<number, ProcessVariable[]> = {
  1001: [
    { name: 'orderId', value: 'ORD-9912' },
    { name: 'priority', value: 'HIGH' },
    { name: 'approved', value: false }
  ],
  1002: [
    { name: 'expenseAmount', value: 1250 },
    { name: 'costCenter', value: 'IT-OPS' },
    { name: 'approved', value: null }
  ]
};

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const readSession = (): AuthSession | null => {
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
  const session = readSession();
  return session?.token ? { Authorization: `Bearer ${session.token}` } : {};
};

const fetchWithAuth = async (url: string, init?: RequestInit): Promise<Response> => {
  const response = await fetch(url, {
    ...init,
    headers: {
      ...(init?.headers ?? {}),
      ...authHeaders()
    }
  });

  if (response.status === 401) {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    window.dispatchEvent(new Event('easybpm-admin-auth-expired'));
  }

  return response;
};

export const adminService = {
  getSession: (): AuthSession | null => readSession(),

  clearSession: (): void => {
    localStorage.removeItem(AUTH_STORAGE_KEY);
  },

  login: async (username: string, password: string): Promise<AuthSession> => {
    const res = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    if (!res.ok) throw new Error('Invalid username or password');
    const payload = (await res.json()) as AuthLoginResponse;
    const session: AuthSession = {
      token: payload.token,
      username: payload.username,
      groups: payload.groups,
      permissions: payload.permissions
    };
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
    return session;
  },

  me: async (): Promise<AuthCurrentUser> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/auth/me`);
    if (!res.ok) throw new Error('Session expired');
    return res.json();
  },

  getProcessInstances: async (page = 0, size = 20): Promise<Page<ProcessInstance>> => {
    if (USE_MOCK) {
      await delay(350);
      return {
        content: MOCK_INSTANCES,
        totalPages: 1,
        totalElements: MOCK_INSTANCES.length,
        number: page,
        size
      };
    }

    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const res = await fetchWithAuth(`${API_BASE_URL}/processes/instances?${params.toString()}`);
    if (!res.ok) throw new Error(`Failed to fetch instances: ${res.statusText}`);
    return res.json();
  },

  findInstanceById: async (instanceId: number): Promise<ProcessInstance | null> => {
    if (USE_MOCK) {
      await delay(250);
      return MOCK_INSTANCES.find((i) => i.id === instanceId) ?? null;
    }

    const res = await fetchWithAuth(`${API_BASE_URL}/processes/instances/${instanceId}`);
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`Failed to fetch instance ${instanceId}: ${res.statusText}`);
    return res.json();
  },

  getProcessDefinitions: async (page = 0, size = 20): Promise<Page<ProcessDefinition>> => {
    if (USE_MOCK) {
      await delay(350);
      return {
        content: MOCK_DEFINITIONS,
        totalPages: 1,
        totalElements: MOCK_DEFINITIONS.length,
        number: page,
        size
      };
    }

    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const res = await fetchWithAuth(`${API_BASE_URL}/processes?${params.toString()}`);
    if (!res.ok) throw new Error(`Failed to fetch definitions: ${res.statusText}`);
    return res.json();
  },

  getProcessDefinitionById: async (definitionId: number): Promise<ProcessDefinition | null> => {
    if (USE_MOCK) {
      await delay(250);
      return MOCK_DEFINITIONS.find((d) => d.id === definitionId) ?? null;
    }

    const res = await fetchWithAuth(`${API_BASE_URL}/processes/definitions/${definitionId}`);
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`Failed to fetch definition ${definitionId}: ${res.statusText}`);
    return res.json();
  },

  getProcessVariables: async (instanceId: number): Promise<ProcessVariable[]> => {
    if (USE_MOCK) {
      await delay(250);
      return MOCK_VARIABLES[instanceId] ?? [];
    }

    const res = await fetchWithAuth(`${API_BASE_URL}/processes/instances/${instanceId}/variables`);
    if (!res.ok) throw new Error(`Failed to fetch variables: ${res.statusText}`);
    return res.json();
  },

  getTasks: async (options?: {
    page?: number;
    size?: number;
    assignee?: string;
    status?: TaskStatus | '';
  }): Promise<Page<BpmTask>> => {
    const params = new URLSearchParams({
      page: String(options?.page ?? 0),
      size: String(options?.size ?? 20)
    });

    if (options?.assignee?.trim()) params.set('assignee', options.assignee.trim());
    if (options?.status) params.set('status', options.status);

    const hasFilters = Boolean(options?.assignee?.trim() || options?.status);
    const path = hasFilters ? '/tasks/search' : '/tasks';
    const res = await fetchWithAuth(`${API_BASE_URL}${path}?${params.toString()}`);
    if (!res.ok) throw new Error(`Failed to fetch tasks: ${res.statusText}`);
    return res.json();
  },

  reassignTask: async (taskId: number, assignee: string | null): Promise<BpmTask> => {
    const nextAssignee = assignee?.trim() ? assignee.trim() : null;
    const res = await fetchWithAuth(`${API_BASE_URL}/tasks/${taskId}/assignee`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ assignee: nextAssignee })
    });
    if (!res.ok) throw new Error(`Failed to reassign task: ${res.statusText}`);
    return res.json();
  },

  getProcessTimeline: async (instanceId: number): Promise<ProcessInstanceEvent[]> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/processes/instances/${instanceId}/timeline`);
    if (!res.ok) throw new Error(`Failed to fetch timeline: ${res.statusText}`);
    return res.json();
  },

  assignProcessVariables: async (instanceId: number, payload: VariableAssignmentPayload): Promise<void> => {
    if (USE_MOCK) {
      await delay(350);
      const existing = MOCK_VARIABLES[instanceId] ?? [];
      const merged = { ...Object.fromEntries(existing.map((v) => [v.name, v.value])), ...payload.variables };
      MOCK_VARIABLES[instanceId] = Object.entries(merged).map(([name, value]) => ({ name, value }));
      return;
    }

    const res = await fetchWithAuth(`${API_BASE_URL}/processes/instances/${instanceId}/variables`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error(`Failed to assign variables: ${res.statusText}`);
  },

  moveNode: async (instanceId: number, payload: MoveNodePayload): Promise<void> => {
    if (USE_MOCK) {
      await delay(450);
      const idx = MOCK_INSTANCES.findIndex((i) => i.id === instanceId);
      if (idx >= 0) {
        MOCK_INSTANCES[idx].currentNode = [payload.toNode];
        MOCK_INSTANCES[idx].nodeHistory = [...(MOCK_INSTANCES[idx].nodeHistory ?? []), payload.toNode];
        MOCK_INSTANCES[idx].updatedAt = new Date().toISOString();
      }
      return;
    }

    const res = await fetchWithAuth(`${API_BASE_URL}/processes/instances/${instanceId}/move-node`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error(`Failed to move node: ${res.statusText}`);
  },

  stopInstance: async (instanceId: number): Promise<void> => {
    if (USE_MOCK) {
      await delay(300);
      const idx = MOCK_INSTANCES.findIndex((i) => i.id === instanceId);
      if (idx >= 0) {
        MOCK_INSTANCES[idx].status = 'CANCELLED';
        MOCK_INSTANCES[idx].currentNode = [];
        MOCK_INSTANCES[idx].updatedAt = new Date().toISOString();
      }
      return;
    }

    const res = await fetchWithAuth(`${API_BASE_URL}/processes/instances/${instanceId}/stop`, {
      method: 'POST'
    });
    if (!res.ok) throw new Error(`Failed to stop instance: ${res.statusText}`);
  },

  deleteInstance: async (instanceId: number): Promise<void> => {
    if (USE_MOCK) {
      await delay(300);
      const idx = MOCK_INSTANCES.findIndex((i) => i.id === instanceId);
      if (idx >= 0) {
        MOCK_INSTANCES.splice(idx, 1);
      }
      delete MOCK_VARIABLES[instanceId];
      return;
    }

    const res = await fetchWithAuth(`${API_BASE_URL}/processes/instances/${instanceId}`, {
      method: 'DELETE'
    });
    if (!res.ok) throw new Error(`Failed to delete instance: ${res.statusText}`);
  },

  getChildInstances: async (parentInstanceId: number): Promise<ProcessInstance[]> => {
    if (USE_MOCK) {
      await delay(250);
      return MOCK_INSTANCES.filter((i) => i.parentInstanceId === parentInstanceId) ?? [];
    }

    const res = await fetchWithAuth(`${API_BASE_URL}/processes/instances/${parentInstanceId}/children`);
    if (!res.ok) throw new Error(`Failed to fetch child instances: ${res.statusText}`);
    return res.json();
  },

  getParentInstance: async (childInstanceId: number): Promise<ProcessInstance | null> => {
    if (USE_MOCK) {
      await delay(250);
      const child = MOCK_INSTANCES.find((i) => i.id === childInstanceId);
      if (!child || !child.parentInstanceId) return null;
      return MOCK_INSTANCES.find((i) => i.id === child.parentInstanceId) ?? null;
    }

    const res = await fetchWithAuth(`${API_BASE_URL}/processes/instances/${childInstanceId}/parent`);
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`Failed to fetch parent instance: ${res.statusText}`);
    return res.json();
  },

  getCallActivityMapping: async (parentInstanceId: number, childInstanceId: number): Promise<CallActivityMapping | null> => {
    if (USE_MOCK) {
      await delay(250);
      return null;
    }

    const res = await fetchWithAuth(`${API_BASE_URL}/processes/instances/${parentInstanceId}/children/${childInstanceId}/mapping`);
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`Failed to fetch call activity mapping: ${res.statusText}`);
    return res.json();
  },

  getIncidents: async (filters: {
    status?: IncidentStatus | '';
    source?: IncidentSource | '';
    processInstanceId?: number | null;
    page?: number;
    size?: number;
  } = {}): Promise<Page<Incident>> => {
    const params = new URLSearchParams({
      page: String(filters.page ?? 0),
      size: String(filters.size ?? 20),
      sort: 'createdAt,desc'
    });
    if (filters.status) params.set('status', filters.status);
    if (filters.source) params.set('source', filters.source);
    if (filters.processInstanceId) params.set('processInstanceId', String(filters.processInstanceId));

    const res = await fetchWithAuth(`${API_BASE_URL}/incidents?${params.toString()}`);
    if (!res.ok) throw new Error(`Failed to fetch incidents: ${res.statusText}`);
    return res.json();
  },

  acknowledgeIncident: async (incidentId: number, acknowledgedBy?: string): Promise<Incident> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/incidents/${incidentId}/acknowledge`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ acknowledgedBy })
    });
    if (!res.ok) throw new Error(`Failed to acknowledge incident: ${res.statusText}`);
    return res.json();
  },

  resolveIncident: async (
    incidentId: number,
    resolvedBy?: string,
    resolutionNote?: string,
    resolutionAction?: IncidentResolutionAction | ''
  ): Promise<Incident> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/incidents/${incidentId}/resolve`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ resolvedBy, resolutionNote, resolutionAction: resolutionAction || null })
    });
    if (!res.ok) throw new Error(`Failed to resolve incident: ${res.statusText}`);
    return res.json();
  },

  reopenIncident: async (incidentId: number): Promise<Incident> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/incidents/${incidentId}/reopen`, {
      method: 'POST'
    });
    if (!res.ok) throw new Error(`Failed to reopen incident: ${res.statusText}`);
    return res.json();
  },

  retryIncident: async (incidentId: number, requestedBy?: string): Promise<Incident> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/incidents/${incidentId}/retry`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ requestedBy })
    });
    if (!res.ok) throw new Error(`Failed to retry incident: ${res.statusText}`);
    return res.json();
  },

  getIncidentSummary: async (): Promise<IncidentSummary> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/incidents/summary`);
    if (!res.ok) throw new Error(`Failed to fetch incident summary: ${res.statusText}`);
    return res.json();
  },

  getIncidentEvents: async (incidentId: number): Promise<IncidentEvent[]> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/incidents/${incidentId}/events`);
    if (!res.ok) throw new Error(`Failed to fetch incident events: ${res.statusText}`);
    return res.json();
  },

  purgeCompletedInstances: async (payload: PurgeCompletedInstancesPayload): Promise<MaintenanceCleanupSummary> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/maintenance/purge-completed-instances`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error(`Failed to purge completed instances: ${res.statusText}`);
    return res.json();
  },

  purgeCompletedTasks: async (payload: PurgeCompletedTasksPayload): Promise<MaintenanceCleanupSummary> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/maintenance/purge-completed-tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error(`Failed to purge completed tasks: ${res.statusText}`);
    return res.json();
  },

  getRetentionSettings: async (): Promise<DataRetentionSettings> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/maintenance/retention`);
    if (!res.ok) throw new Error(`Failed to fetch retention settings: ${res.statusText}`);
    return res.json();
  },

  updateRetentionSettings: async (payload: UpdateDataRetentionSettingsPayload): Promise<DataRetentionSettings> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/maintenance/retention`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error(`Failed to update retention settings: ${res.statusText}`);
    return res.json();
  },

  previewConfiguredRetention: async (): Promise<MaintenanceCleanupSummary> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/maintenance/retention/preview`, {
      method: 'POST'
    });
    if (!res.ok) throw new Error(`Failed to preview retention: ${res.statusText}`);
    return res.json();
  },

  runConfiguredRetention: async (): Promise<MaintenanceCleanupSummary> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/maintenance/retention/run`, {
      method: 'POST'
    });
    if (res.status === 409) throw new Error('Configured retention is disabled');
    if (!res.ok) throw new Error(`Failed to run retention: ${res.statusText}`);
    return res.json();
  },

  deleteProcessDefinitionCascade: async (definitionId: number, dryRun: boolean): Promise<MaintenanceCleanupSummary> => {
    const params = new URLSearchParams({ dryRun: String(dryRun) });
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/maintenance/process-definitions/${definitionId}?${params.toString()}`, {
      method: 'DELETE'
    });
    if (res.status === 404) throw new Error('Process definition not found');
    if (!res.ok) throw new Error(`Failed to delete process definition: ${res.statusText}`);
    return res.json();
  },

  getUsers: async (): Promise<AdminUser[]> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/users`);
    if (!res.ok) throw new Error(`Failed to fetch users: ${res.statusText}`);
    return res.json();
  },

  createUser: async (payload: {
    username: string;
    password: string;
    enabled: boolean;
    groupIds: number[];
    permissionCodes: string[];
  }): Promise<AdminUser> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/users`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error(`Failed to create user: ${res.statusText}`);
    return res.json();
  },

  updateUser: async (id: number, payload: {
    enabled: boolean;
    groupIds: number[];
    permissionCodes: string[];
  }): Promise<AdminUser> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/users/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error(`Failed to update user: ${res.statusText}`);
    return res.json();
  },

  resetUserPassword: async (id: number, password: string): Promise<void> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/users/${id}/password`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ password })
    });
    if (!res.ok) throw new Error(`Failed to reset password: ${res.statusText}`);
  },

  deleteUser: async (id: number): Promise<void> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/users/${id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(`Failed to delete user: ${res.statusText}`);
  },

  getGroups: async (): Promise<AdminGroup[]> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/groups`);
    if (!res.ok) throw new Error(`Failed to fetch groups: ${res.statusText}`);
    return res.json();
  },

  createGroup: async (payload: { code: string; name: string; permissionCodes: string[] }): Promise<AdminGroup> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/groups`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error(`Failed to create group: ${res.statusText}`);
    return res.json();
  },

  updateGroup: async (id: number, payload: { name: string; permissionCodes: string[] }): Promise<AdminGroup> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/groups/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error(`Failed to update group: ${res.statusText}`);
    return res.json();
  },

  deleteGroup: async (id: number): Promise<void> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/groups/${id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(`Failed to delete group: ${res.statusText}`);
  },

  getGroupUsers: async (groupId: number): Promise<AdminUser[]> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/groups/${groupId}/users`);
    if (!res.ok) throw new Error(`Failed to fetch group users: ${res.statusText}`);
    return res.json();
  },

  updateGroupUsers: async (groupId: number, userIds: number[]): Promise<AdminUser[]> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/groups/${groupId}/users`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userIds })
    });
    if (!res.ok) throw new Error(`Failed to update group users: ${res.statusText}`);
    return res.json();
  }
};
