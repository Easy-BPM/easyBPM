import { fetchWithAuth } from './processService';
import { getModelerApiBaseUrl } from '../config/runtimeConfig';
import type { DecisionDefinition, DecisionIssue, DecisionTable, DecisionTestCase, DecisionTestResult, EvaluationResult } from '../decisionTypes';

async function request<T>(path: string, body?: unknown, rawBody?: string): Promise<T> {
  const response = await fetchWithAuth(`${getModelerApiBaseUrl()}${path}`, body === undefined && rawBody === undefined ? undefined : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: rawBody ?? JSON.stringify(body) });
  if (!response.ok) { const error = await response.json().catch(() => ({})); throw new Error(error.message || `Decision request failed (${response.status}).`); }
  return response.json();
}
export const decisionService = {
  list: () => request<DecisionDefinition[]>('/decisions'),
  versions: (key: string) => request<DecisionDefinition[]>(`/decisions/${encodeURIComponent(key)}/versions`),
  validate: (dmnXml: string) => request<{ valid: boolean; issues: DecisionIssue[]; table?: DecisionTable }>('/decisions/validate', { dmnXml }),
  test: (dmnXml: string, inputs: Record<string, unknown>) => request<EvaluationResult>('/decisions/test', { dmnXml, inputs }),
  testJson: (dmnXml: string, inputsJson: string) => request<EvaluationResult>('/decisions/test', undefined, `{"dmnXml":${JSON.stringify(dmnXml)},"inputs":${inputsJson}}`),
  deploy: (dmnXml: string) => request<DecisionDefinition>('/decisions', { dmnXml }),
  tests: (key: string) => request<DecisionTestCase[]>(`/decisions/${encodeURIComponent(key)}/tests`),
  saveTest: (key: string, test: Omit<DecisionTestCase, 'id'>) => request<DecisionTestCase>(`/decisions/${encodeURIComponent(key)}/tests`, test),
  saveTestJson: (key: string, name: string, inputsJson: string, expectedJson: string) => request<DecisionTestCase>(`/decisions/${encodeURIComponent(key)}/tests`, undefined, `{"name":${JSON.stringify(name)},"inputs":${inputsJson},"expectedOutputs":${expectedJson}}`),
  runTests: async (key: string, version?: number) => (await request<{ decisionKey: string; version: number; results: DecisionTestResult[] }>(`/decisions/${encodeURIComponent(key)}/tests/run`, { version })).results
};
