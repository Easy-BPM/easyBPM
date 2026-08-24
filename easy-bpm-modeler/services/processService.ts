import { getModelerApiBaseUrl } from '../config/runtimeConfig';

const API_BASE_URL = getModelerApiBaseUrl();
const AUTH_STORAGE_KEY = 'easybpm_modeler_auth';

export class AuthRequiredError extends Error {
  constructor(message = 'Session expired. Please sign in again.') {
    super(message);
    this.name = 'AuthRequiredError';
  }
}

export const isAuthRequiredError = (error: unknown): error is AuthRequiredError => {
  return error instanceof AuthRequiredError;
};

type AuthSession = {
  token: string;
  username: string;
  groups: string[];
  permissions: string[];
};

type LoginResponse = {
  token: string;
  tokenType: string;
  username: string;
  groups: string[];
  permissions: string[];
};

export type ProcessDefinitionSummary = {
  id: number;
  key: string;
  processName?: string | null;
  description?: string | null;
  version: number;
  definitionXml?: string;
  definitionJson?: string;
};

export type AgentProcessDefinitionSummary = {
  id: number;
  key: string;
  processName?: string | null;
  description?: string | null;
  definitionJson: string | Record<string, unknown>;
  version: number;
  createdAt?: string;
};

type PageResponse<T> = {
  content: T[];
};

let activeSession: AuthSession | null = null;

const getSession = (): AuthSession | null => {
  if (activeSession?.token) return activeSession;

  const raw = localStorage.getItem(AUTH_STORAGE_KEY);
  if (!raw) return null;
  try {
    activeSession = JSON.parse(raw) as AuthSession;
    return activeSession;
  } catch {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    activeSession = null;
    return null;
  }
};

export const fetchWithAuth = async (url: string, init?: RequestInit) => {
  const session = getSession();
  const response = await fetch(url, {
    ...init,
    headers: {
      ...(init?.headers ?? {}),
      ...(session?.token ? { Authorization: `Bearer ${session.token}` } : {})
    }
  });

  return response;
};

export const processService = {
  getSession: (): AuthSession | null => getSession(),

  clearSession: (): void => {
    activeSession = null;
    localStorage.removeItem(AUTH_STORAGE_KEY);
  },

  login: async (username: string, password: string): Promise<AuthSession> => {
    const response = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });

    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Login failed (${response.status}): ${body || response.statusText}`);
    }

    const payload = (await response.json()) as LoginResponse;
    const session: AuthSession = {
      token: payload.token,
      username: payload.username,
      groups: payload.groups,
      permissions: payload.permissions
    };
    activeSession = session;
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
    return session;
  },

  me: async (): Promise<{ username: string; groups: string[]; permissions: string[] }> => {
    const response = await fetchWithAuth(`${API_BASE_URL}/auth/me`);
    if (response.status === 401) throw new AuthRequiredError();
    if (!response.ok) throw new Error('Session check failed');
    return response.json();
  },

  deployProcess: async (payload: string): Promise<void> => {
    const session = getSession();
    if (!session?.token) {
      throw new AuthRequiredError('No saved Modeler session was found. Please sign in again before deploying.');
    }

    const response = await fetchWithAuth(`${API_BASE_URL}/processes`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/xml'
      },
      body: payload
    });

    if (response.status === 401) {
      throw new AuthRequiredError('Session expired. Please sign in again before deploying.');
    }

    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Deploy failed (${response.status}): ${body || response.statusText}`);
    }
  },

  listLatestProcesses: async (): Promise<ProcessDefinitionSummary[]> => {
    const response = await fetchWithAuth(`${API_BASE_URL}/processes?size=50&sort=id,desc`);
    if (response.status === 401) throw new AuthRequiredError();
    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Load processes failed (${response.status}): ${body || response.statusText}`);
    }

    const payload = (await response.json()) as PageResponse<ProcessDefinitionSummary> | ProcessDefinitionSummary[];
    return Array.isArray(payload) ? payload : payload.content || [];
  },

  getProcessDefinition: async (id: number): Promise<ProcessDefinitionSummary> => {
    const response = await fetchWithAuth(`${API_BASE_URL}/processes/definitions/${id}`);
    if (response.status === 401) throw new AuthRequiredError();
    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Load process failed (${response.status}): ${body || response.statusText}`);
    }
    return response.json();
  },

  deployAgentProcess: async (payload: unknown): Promise<void> => {
    const session = getSession();
    if (!session?.token) {
      throw new AuthRequiredError('No saved Modeler session was found. Please sign in again before deploying.');
    }

    const response = await fetchWithAuth(`${API_BASE_URL}/agent-processes`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    if (response.status === 401) {
      throw new AuthRequiredError('Session expired. Please sign in again before deploying.');
    }

    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Agent process deploy failed (${response.status}): ${body || response.statusText}`);
    }
  },

  listAgentProcesses: async (): Promise<AgentProcessDefinitionSummary[]> => {
    const response = await fetchWithAuth(`${API_BASE_URL}/agent-processes`);
    if (response.status === 401) throw new AuthRequiredError();
    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Load agent processes failed (${response.status}): ${body || response.statusText}`);
    }
    return response.json();
  }
};
