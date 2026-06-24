type EasyBpmRuntimeConfig = Record<string, string | undefined>;

declare global {
  interface Window {
    __EASY_BPM_CONFIG__?: EasyBpmRuntimeConfig;
  }
}

export const getRuntimeConfigValue = (key: string): string | undefined => {
  const value = window.__EASY_BPM_CONFIG__?.[key];
  return typeof value === 'string' && value.trim().length > 0 ? value : undefined;
};

export const getModelerApiBaseUrl = (): string =>
  getRuntimeConfigValue('EASY_BPM_MODELER_API_BASE_URL') ??
  (import.meta.env.EASY_BPM_MODELER_API_BASE_URL as string | undefined) ??
  'http://localhost:8080';

