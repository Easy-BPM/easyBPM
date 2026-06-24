import { getRuntimeConfigValue } from './runtimeConfig';

const truthyValues = new Set(['1', 'true', 'yes', 'on', 'enabled']);

const isEnabled = (value: unknown): boolean => {
  if (typeof value !== 'string') return false;
  return truthyValues.has(value.trim().toLowerCase());
};

export const featureFlags = {
  agenticOrchestration: isEnabled(
    getRuntimeConfigValue('EASY_BPM_MODELER_AGENTIC_ORCHESTRATION') ??
      import.meta.env.EASY_BPM_MODELER_AGENTIC_ORCHESTRATION
  )
};
