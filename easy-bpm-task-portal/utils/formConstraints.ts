import type { JsonSchema } from '../types';

/** Match the backend's supported top-level value constraints. Required is checked separately. */
export const validateFormConstraints = (schema: JsonSchema, values: Record<string, unknown>): Record<string, string> => {
  const errors: Record<string, string> = {};
  for (const [name, rule] of Object.entries(schema.properties || {})) {
    const value = values[name];
    if (value === undefined || value === null || value === '') continue;
    const label = rule.title || name;
    let reason: string | undefined;
    if (rule.type === 'number' || rule.type === 'integer') {
      if (typeof value !== 'number' || !Number.isFinite(value)) reason = 'must be a number.';
      else if (rule.type === 'integer' && !Number.isInteger(value)) reason = 'must be an integer.';
      else if (rule.minimum !== undefined && value < rule.minimum) reason = `must be at least ${rule.minimum}.`;
      else if (rule.maximum !== undefined && value > rule.maximum) reason = `must be at most ${rule.maximum}.`;
      else if (rule.multipleOf !== undefined && (rule.multipleOf <= 0 || Math.abs(value / rule.multipleOf - Math.round(value / rule.multipleOf)) > 1e-9)) reason = `must be a multiple of ${rule.multipleOf}.`;
    } else if (rule.type === 'string' || rule.type === 'date') {
      if (typeof value !== 'string') reason = 'must be text.';
      else if (rule.minLength !== undefined && [...value].length < rule.minLength) reason = `needs at least ${rule.minLength} characters.`;
      else if (rule.maxLength !== undefined && [...value].length > rule.maxLength) reason = `allows at most ${rule.maxLength} characters.`;
      else if (rule.pattern) {
        try { if (!new RegExp(rule.pattern).test(value)) reason = 'must match the configured pattern.'; }
        catch { reason = 'has an invalid pattern rule. Contact an administrator.'; }
      }
    } else if (rule.type === 'boolean' && typeof value !== 'boolean') reason = 'must be a boolean.';
    else if (rule.type === 'object' && (typeof value !== 'object' || Array.isArray(value))) reason = 'must be an object.';
    else if (rule.type === 'array' && !Array.isArray(value)) reason = 'must be an array.';
    if (!reason && rule.enum && !rule.enum.some(option => option === value)) reason = 'must be an allowed option.';
    if (reason) errors[name] = `${label} ${reason}`;
  }
  return errors;
};
