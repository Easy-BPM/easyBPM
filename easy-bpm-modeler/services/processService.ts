const API_BASE_URL = (import.meta.env.EASY_BPM_MODELER_API_BASE_URL as string | undefined) ?? 'http://localhost:8080';
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

  if (response.status === 401) {
    console.warn('Easy BPM Modeler request was rejected as unauthorized.', {
      url,
      hasSavedSession: Boolean(session?.token)
    });
  }

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

  deployProcess: async (payload: unknown): Promise<void> => {
    const session = getSession();
    if (!session?.token) {
      throw new AuthRequiredError('No saved Modeler session was found. Please sign in again before deploying.');
    }

    const response = await fetchWithAuth(`${API_BASE_URL}/processes`, {
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
      throw new Error(`Deploy failed (${response.status}): ${body || response.statusText}`);
    }
  }
};
