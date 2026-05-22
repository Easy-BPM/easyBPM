const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080';
const AUTH_STORAGE_KEY = 'easybpm_modeler_auth';

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

export const fetchWithAuth = (url: string, init?: RequestInit) => {
  const session = getSession();
  return fetch(url, {
    ...init,
    headers: {
      ...(init?.headers ?? {}),
      ...(session?.token ? { Authorization: `Bearer ${session.token}` } : {})
    }
  });
};

export const processService = {
  getSession: (): AuthSession | null => getSession(),

  clearSession: (): void => {
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
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
    return session;
  },

  me: async (): Promise<{ username: string; groups: string[]; permissions: string[] }> => {
    const response = await fetchWithAuth(`${API_BASE_URL}/auth/me`);
    if (!response.ok) throw new Error('Session expired');
    return response.json();
  },

  deployProcess: async (payload: unknown): Promise<void> => {
    const response = await fetchWithAuth(`${API_BASE_URL}/processes`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Deploy failed (${response.status}): ${body || response.statusText}`);
    }
  }
};
