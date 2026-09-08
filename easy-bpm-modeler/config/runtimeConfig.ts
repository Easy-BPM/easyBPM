type EasyBpmRuntimeConfig = Record<string, string | undefined>;

declare global {
  interface Window {
    __EASY_BPM_CONFIG__?: EasyBpmRuntimeConfig;
  }
}

export const getRuntimeConfigValue = (key: string): string | undefined => {
  const desktopWindow = window as Window & { easyBpmDesktop?: { config?: EasyBpmRuntimeConfig } };
  const value = window.__EASY_BPM_CONFIG__?.[key] ?? desktopWindow.easyBpmDesktop?.config?.[key];
  return typeof value === 'string' && value.trim().length > 0 ? value : undefined;
};

export const getModelerApiBaseUrl = (): string =>
  localStorage.getItem('easybpm_modeler_backend_url') ||
  (getRuntimeConfigValue('EASY_BPM_MODELER_API_BASE_URL') ??
    (import.meta.env.EASY_BPM_MODELER_API_BASE_URL as string | undefined) ??
    'http://localhost:8080');

export const setModelerApiBaseUrl = (url: string): void => {
  const normalized = url.trim().replace(/\/+$/, '');
  if (normalized) {
    localStorage.setItem('easybpm_modeler_backend_url', normalized);
  } else {
    localStorage.removeItem('easybpm_modeler_backend_url');
  }
};

export const getModelerRuntimeMode = (): 'online' | 'desktop' => {
  if (typeof window !== 'undefined' && window.easyBpmDesktop) return 'desktop';
  const value =
    getRuntimeConfigValue('EASY_BPM_MODELER_RUNTIME_MODE') ??
    (import.meta.env.EASY_BPM_MODELER_RUNTIME_MODE as string | undefined);
  return value?.trim().toLowerCase() === 'desktop' ? 'desktop' : 'online';
};

export const isDesktopModeler = (): boolean => getModelerRuntimeMode() === 'desktop';
