export type DecisionType = 'string' | 'number' | 'boolean' | 'date' | 'json';
export interface DecisionTable {
  xmlIds?: { definitions?: string; table?: string; inputExpressions?: Record<string, string>; inputEntries?: Record<string, string[]>; outputEntries?: Record<string, string[]> };
  key: string; name: string; hitPolicy: 'UNIQUE' | 'FIRST' | 'COLLECT';
  inputs: { id: string; label: string; expression: string; type: DecisionType }[];
  outputs: { id: string; name: string; type: DecisionType }[];
  rules: { id: string; description?: string; inputEntries: string[]; outputEntries: string[] }[];
}
export interface DecisionDefinition { id: number; key: string; name: string; version: number; state: string; dmnXml: string; createdAt: string }
export interface DecisionIssue { severity: string; code: string; message: string; ruleId?: string; columnId?: string }
export interface EvaluationResult {
  decisionKey: string; version?: number; hitPolicy: string; matchedRuleIds: string[]; outputs: unknown; durationMs: number;
  explanations: { ruleId: string; matched: boolean; conditions: { inputId: string; expression: string; matched: boolean }[] }[];
}
export interface DecisionTestCase { id: number; name: string; inputs: Record<string, unknown>; expectedOutputs: unknown }
export interface DecisionTestResult { testCaseId: number; name: string; passed: boolean; actualOutputs: unknown; expectedOutputs: unknown; error?: string; differences?: unknown }
