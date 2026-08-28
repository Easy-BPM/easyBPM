import {
  AdminGroup,
  AdminUser,
  AuthCurrentUser,
  AuthLoginResponse,
  AuthProviderConfig,
  AuthSession,
  BpmTask,
  CallActivityMapping,
  DataRetentionSettings,
  Incident,
  IncidentGroup,
  IncidentRetryStatus,
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

const API_BASE_URL = ((import.meta.env.EASY_BPM_ADMIN_API_BASE_URL as string | undefined) ?? 'http://localhost:8080').replace(/\/$/, '');
const USE_MOCK = false;
const AUTH_STORAGE_KEY = 'easybpm_admin_auth';
const OIDC_STATE_KEY = 'easybpm_admin_oidc_state';
const OIDC_VERIFIER_KEY = 'easybpm_admin_oidc_verifier';
let incidentGroupsEndpointAvailable: boolean | undefined;

type IncidentGroupFilters = {
  status?: IncidentStatus | '';
  source?: IncidentSource | '';
  processDefinitionId?: number | null;
  nodeId?: string;
  acknowledgedBy?: string;
  retryStatus?: IncidentRetryStatus | '';
  occurredSince?: string;
  minOccurrences?: number;
  page?: number;
  size?: number;
};

const groupLegacyIncidents = (incidents: Incident[], filters: IncidentGroupFilters): Page<IncidentGroup> => {
  const groups = new Map<string, Incident[]>();
  incidents.forEach((incident) => {
    const signature = [incident.source, incident.processInstanceId, incident.nodeId ?? '', incident.message].join('|');
    const entries = groups.get(signature) ?? [];
    entries.push(incident);
    groups.set(signature, entries);
  });

  const grouped = [...groups.entries()].map(([signature, entries]): IncidentGroup => {
    const newest = [...entries].sort((left, right) => new Date(right.lastOccurredAt).getTime() - new Date(left.lastOccurredAt).getTime())[0];
    const occurrenceCount = entries.reduce((total, incident) => total + (incident.occurrenceCount || 1), 0);
    const retryStatus: IncidentRetryStatus = newest.source === 'WORKER' && newest.status === 'OPEN' ? 'RETRY_ELIGIBLE' : 'NOT_ELIGIBLE';
    return {
      signature,
      representativeIncidentId: newest.id,
      processDefinitionId: null,
      processName: `Process instance #${newest.processInstanceId}`,
      source: newest.source,
      nodeId: newest.nodeId,
      status: newest.status,
      retryStatus,
      message: newest.message,
      technicalDetails: newest.technicalDetails,
      occurrenceCount,
      instanceCount: new Set(entries.map((incident) => incident.processInstanceId)).size,
      firstOccurredAt: entries.reduce((first, incident) => first < incident.createdAt ? first : incident.createdAt, newest.createdAt),
      lastOccurredAt: newest.lastOccurredAt,
      retryAttemptCount: 0,
      maxRetryAttempts: 0,
      acknowledgedBy: newest.acknowledgedBy,
      resolutionNote: newest.resolutionNote,
      sampleIncidentIds: entries.slice(0, 5).map((incident) => incident.id),
      sampleInstanceIds: [...new Set(entries.map((incident) => incident.processInstanceId))].slice(0, 5)
    };
  }).filter((group) => {
    if (filters.nodeId && group.nodeId !== filters.nodeId) return false;
    if (filters.acknowledgedBy && group.acknowledgedBy !== filters.acknowledgedBy) return false;
    if (filters.occurredSince && new Date(group.lastOccurredAt) < new Date(filters.occurredSince)) return false;
    if (filters.minOccurrences && group.occurrenceCount < filters.minOccurrences) return false;
    if (filters.retryStatus && group.retryStatus !== filters.retryStatus) return false;
    return true;
  }).sort((left, right) => new Date(right.lastOccurredAt).getTime() - new Date(left.lastOccurredAt).getTime());

  const size = filters.size ?? 25;
  const page = filters.page ?? 0;
  return {
    content: grouped.slice(page * size, (page + 1) * size),
    totalPages: Math.max(1, Math.ceil(grouped.length / size)),
    totalElements: grouped.length,
    size,
    number: page
  };
};

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

const fetchWithAuth = async (url: string, init?: RequestInit, options: { expireOnUnauthorized?: boolean } = {}): Promise<Response> => {
  const session = readSession();
  const authorization = session?.token ? { Authorization: `Bearer ${session.token}` } : {};
  const expireOnUnauthorized = options.expireOnUnauthorized ?? true;
  const response = await fetch(url, {
    ...init,
    headers: {
      ...(init?.headers ?? {}),
      ...authorization
    }
  });

  if (response.status === 401 && expireOnUnauthorized) {
    console.warn(
      `Easy BPM Admin request was rejected as unauthorized. ` +
        `url=${url} hasSavedSession=${Boolean(session?.token)} hasAuthorizationHeader=${Boolean(session?.token)}`
    );
    localStorage.removeItem(AUTH_STORAGE_KEY);
    window.dispatchEvent(new Event('easybpm-admin-auth-expired'));
  }

  return response;
};

const errorFromResponse = async (response: Response, fallback: string): Promise<Error> => {
  try {
    const contentType = response.headers.get('Content-Type') ?? '';
    if (contentType.includes('application/json')) {
      const payload = await response.json();
      if (payload?.message) return new Error(String(payload.message));
      if (payload?.error) return new Error(String(payload.error));
    } else {
      const text = await response.text();
      if (text.trim()) return new Error(text.trim());
    }
  } catch {
    // Fall back to the caller's context-specific message.
  }

  return new Error(`${fallback}: ${response.status} ${response.statusText}`.trim());
};

const base64UrlEncode = (bytes: Uint8Array): string =>
  btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');

const randomBase64Url = (byteLength: number): string => {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(bytes);
};

const sha256Base64Url = async (value: string): Promise<string> => {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return base64UrlEncode(new Uint8Array(digest));
};

const buildSessionFromMe = (token: string, me: AuthCurrentUser, idToken?: string, logoutEndpoint?: string): AuthSession => ({
  token,
  username: me.username,
  groups: me.groups,
  permissions: me.permissions,
  identityProvider: me.identityProvider,
  idToken,
  oidcLogoutEndpoint: logoutEndpoint
});

export const adminService = {
  getSession: (): AuthSession | null => readSession(),

  clearSession: (): void => {
    localStorage.removeItem(AUTH_STORAGE_KEY);
  },

  authConfig: async (): Promise<AuthProviderConfig> => {
    const response = await fetch(`${API_BASE_URL}/auth/config`);
    if (!response.ok) throw await errorFromResponse(response, 'Failed to load auth configuration');
    return response.json();
  },

  startOidcLogin: async (): Promise<void> => {
    const config = await adminService.authConfig();
    if (!config.oidc) throw new Error('OIDC login is not configured.');

    const state = randomBase64Url(24);
    const verifier = randomBase64Url(64);
    const challenge = await sha256Base64Url(verifier);
    localStorage.setItem(OIDC_STATE_KEY, state);
    localStorage.setItem(OIDC_VERIFIER_KEY, verifier);

    const params = new URLSearchParams({
      client_id: config.oidc.clientId,
      redirect_uri: window.location.origin + window.location.pathname,
      response_type: 'code',
      scope: 'openid profile email',
      state,
      code_challenge: challenge,
      code_challenge_method: 'S256'
    });

    window.location.assign(`${config.oidc.authorizationEndpoint}?${params.toString()}`);
  },

  completeOidcLoginIfPresent: async (): Promise<AuthSession | null> => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const state = params.get('state');
    if (!code) return null;

    const expectedState = localStorage.getItem(OIDC_STATE_KEY);
    const verifier = localStorage.getItem(OIDC_VERIFIER_KEY);
    localStorage.removeItem(OIDC_STATE_KEY);
    localStorage.removeItem(OIDC_VERIFIER_KEY);
    if (!state || state !== expectedState || !verifier) {
      throw new Error('OIDC login state is invalid.');
    }

    const config = await adminService.authConfig();
    if (!config.oidc) throw new Error('OIDC login is not configured.');

    const tokenResponse = await fetch(config.oidc.tokenEndpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'authorization_code',
        client_id: config.oidc.clientId,
        code,
        redirect_uri: window.location.origin + window.location.pathname,
        code_verifier: verifier
      })
    });
    if (!tokenResponse.ok) throw await errorFromResponse(tokenResponse, 'OIDC token exchange failed');
    const tokenPayload = await tokenResponse.json() as { access_token: string; id_token?: string };

    const meResponse = await fetch(`${API_BASE_URL}/auth/me`, {
      headers: { Authorization: `Bearer ${tokenPayload.access_token}` }
    });
    if (!meResponse.ok) throw await errorFromResponse(meResponse, 'Failed to load current OIDC user');
    const me = await meResponse.json() as AuthCurrentUser;
    const session = buildSessionFromMe(tokenPayload.access_token, me, tokenPayload.id_token, config.oidc.logoutEndpoint);
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
    window.history.replaceState({}, document.title, window.location.pathname);
    return session;
  },

  buildLogoutUrl: (): string | null => {
    const session = readSession();
    if (!session?.oidcLogoutEndpoint) return null;
    const params = new URLSearchParams({
      post_logout_redirect_uri: window.location.origin + window.location.pathname
    });
    if (session.idToken) params.set('id_token_hint', session.idToken);
    return `${session.oidcLogoutEndpoint}?${params.toString()}`;
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
    if (!res.ok) throw await errorFromResponse(res, 'Failed to fetch instances');
    return res.json();
  },

  findInstanceById: async (instanceId: number): Promise<ProcessInstance | null> => {
    if (USE_MOCK) {
      await delay(250);
      return MOCK_INSTANCES.find((i) => i.id === instanceId) ?? null;
    }

    const res = await fetchWithAuth(`${API_BASE_URL}/processes/instances/${instanceId}`);
    if (res.status === 404) return null;
    if (!res.ok) throw await errorFromResponse(res, `Failed to fetch instance ${instanceId}`);
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
    if (!res.ok) throw await errorFromResponse(res, 'Failed to fetch definitions');
    return res.json();
  },

  getProcessDefinitionById: async (definitionId: number): Promise<ProcessDefinition | null> => {
    if (USE_MOCK) {
      await delay(250);
      return MOCK_DEFINITIONS.find((d) => d.id === definitionId) ?? null;
    }

    const res = await fetchWithAuth(`${API_BASE_URL}/processes/definitions/${definitionId}`);
    if (res.status === 404) return null;
    if (!res.ok) throw await errorFromResponse(res, `Failed to fetch definition ${definitionId}`);
    return res.json();
  },

  getProcessVariables: async (instanceId: number): Promise<ProcessVariable[]> => {
    if (USE_MOCK) {
      await delay(250);
      return MOCK_VARIABLES[instanceId] ?? [];
    }

    const res = await fetchWithAuth(`${API_BASE_URL}/processes/instances/${instanceId}/variables`);
    if (!res.ok) throw await errorFromResponse(res, 'Failed to fetch variables');
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
    if (!res.ok) throw await errorFromResponse(res, 'Failed to fetch timeline');
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
    if (!res.ok) throw await errorFromResponse(res, 'Failed to assign variables');
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
    if (!res.ok) throw await errorFromResponse(res, 'Failed to move node');
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
    if (!res.ok) throw await errorFromResponse(res, 'Failed to stop instance');
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
    if (!res.ok) throw await errorFromResponse(res, 'Failed to delete instance');
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

    const res = await fetchWithAuth(`${API_BASE_URL}/incidents?${params.toString()}`, undefined, { expireOnUnauthorized: false });
    if (!res.ok) throw await errorFromResponse(res, 'Failed to fetch incidents');
    return res.json();
  },

  getIncidentGroups: async (filters: IncidentGroupFilters = {}): Promise<Page<IncidentGroup>> => {
    const params = new URLSearchParams({
      page: String(filters.page ?? 0),
      size: String(filters.size ?? 25)
    });
    if (filters.status) params.set('status', filters.status);
    if (filters.source) params.set('source', filters.source);
    if (filters.processDefinitionId) params.set('processDefinitionId', String(filters.processDefinitionId));
    if (filters.nodeId) params.set('nodeId', filters.nodeId);
    if (filters.acknowledgedBy) params.set('acknowledgedBy', filters.acknowledgedBy);
    if (filters.retryStatus) params.set('retryStatus', filters.retryStatus);
    if (filters.occurredSince) params.set('occurredSince', filters.occurredSince);
    if (filters.minOccurrences) params.set('minOccurrences', String(filters.minOccurrences));

    const loadLegacyGroups = async (): Promise<Page<IncidentGroup>> => {
      const incidents = await adminService.getIncidents({
        status: filters.status,
        source: filters.source,
        page: 0,
        size: 500
      });
      return groupLegacyIncidents(incidents.content, filters);
    };

    if (incidentGroupsEndpointAvailable === false) {
      return loadLegacyGroups();
    }

    const res = await fetchWithAuth(`${API_BASE_URL}/incidents/groups?${params.toString()}`, undefined, { expireOnUnauthorized: false });
    if (res.status === 400 || res.status === 401 || res.status === 404 || res.status === 405) {
      incidentGroupsEndpointAvailable = false;
      return loadLegacyGroups();
    }
    if (!res.ok) throw await errorFromResponse(res, 'Failed to fetch incident groups');
    incidentGroupsEndpointAvailable = true;
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
    const res = await fetchWithAuth(`${API_BASE_URL}/incidents/summary`, undefined, { expireOnUnauthorized: false });
    if (!res.ok) throw await errorFromResponse(res, 'Failed to fetch incident summary');
    return res.json();
  },

  getIncidentEvents: async (incidentId: number): Promise<IncidentEvent[]> => {
    const res = await fetchWithAuth(`${API_BASE_URL}/incidents/${incidentId}/events`, undefined, { expireOnUnauthorized: false });
    if (!res.ok) throw await errorFromResponse(res, 'Failed to fetch incident events');
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
