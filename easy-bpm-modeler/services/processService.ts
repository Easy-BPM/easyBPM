import { getModelerApiBaseUrl } from '../config/runtimeConfig';

const API_BASE_URL = getModelerApiBaseUrl();
const AUTH_STORAGE_KEY = 'easybpm_modeler_auth';
const OIDC_STATE_KEY = 'easybpm_modeler_oidc_state';
const OIDC_VERIFIER_KEY = 'easybpm_modeler_oidc_verifier';
const OIDC_AUTO_LOGIN_SUPPRESS_KEY = 'easybpm_modeler_oidc_auto_login_suppressed';

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
  identityProvider?: string | null;
  idToken?: string;
  oidcLogoutEndpoint?: string;
};

type LoginResponse = {
  token: string;
  tokenType: string;
  username: string;
  groups: string[];
  permissions: string[];
};

type AuthCurrentUser = {
  id?: number;
  username: string;
  email?: string | null;
  displayName?: string | null;
  identityProvider?: string | null;
  externalIdentityId?: string | null;
  groups: string[];
  permissions: string[];
};

type AuthProviderConfig = {
  provider: 'local' | 'oidc' | 'keycloak' | string;
  oidc?: {
    issuerUri: string;
    clientId: string;
    authorizationEndpoint: string;
    tokenEndpoint: string;
    logoutEndpoint: string;
  } | null;
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
    // Use the operation-specific fallback below.
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

export const processService = {
  getSession: (): AuthSession | null => getSession(),

  clearSession: (): void => {
    activeSession = null;
    localStorage.removeItem(AUTH_STORAGE_KEY);
  },

  authConfig: async (): Promise<AuthProviderConfig> => {
    const response = await fetch(`${API_BASE_URL}/auth/config`);
    if (!response.ok) throw await errorFromResponse(response, 'Failed to load auth configuration');
    return response.json();
  },

  markOidcAutoLoginSuppressed: (): void => {
    sessionStorage.setItem(OIDC_AUTO_LOGIN_SUPPRESS_KEY, 'true');
  },

  shouldAutoStartOidcLogin: async (): Promise<boolean> => {
    const suppressed = sessionStorage.getItem(OIDC_AUTO_LOGIN_SUPPRESS_KEY) === 'true';
    if (suppressed) return false;

    const config = await processService.authConfig();
    return Boolean(config.oidc);
  },

  startOidcLogin: async (): Promise<void> => {
    const config = await processService.authConfig();
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

    const config = await processService.authConfig();
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
    activeSession = session;
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
    window.history.replaceState({}, document.title, window.location.pathname);
    return session;
  },

  buildLogoutUrl: (): string | null => {
    const session = getSession();
    if (!session?.oidcLogoutEndpoint) return null;
    const params = new URLSearchParams({
      post_logout_redirect_uri: window.location.origin + window.location.pathname
    });
    if (session.idToken) params.set('id_token_hint', session.idToken);
    return `${session.oidcLogoutEndpoint}?${params.toString()}`;
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

  me: async (): Promise<AuthCurrentUser> => {
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
