import React, { useEffect, useRef, useState } from 'react';
import {
  ArrowLeft,
  Bot,
  Braces,
  Code2,
  Download,
  FilePlus2,
  FileText,
  Globe2,
  Loader2,
  Trash2,
  Upload,
  UploadCloud,
  Wrench,
} from 'lucide-react';
import { ThemeMode, ThemeToggle } from './ThemeToggle';
import { AvailableCredential, isAuthRequiredError, processService } from '../services/processService';
import { getRuntimeConfigValue } from '../config/runtimeConfig';
import { Toaster, toast } from 'sonner';

interface AgentBoardModelerProps {
  currentUser: string | null;
  onBack: () => void;
  onLogout: () => void;
  theme: ThemeMode;
  onToggleTheme: () => void;
  initialDefinition?: unknown;
  availableCredentials?: AvailableCredential[];
}

interface AgentBoardState {
  processName: string;
  goal: string;
  instructions: string;
  constraints: string;
  availableTools: AgentTool[];
  providerId: string;
  modelName: string;
  endpoint: string;
  credentialRef: string;
  systemPrompt: string;
  promptTemplate: string;
}

type AgentToolKind = 'api-call' | 'code-task';
type AgentToolAuthType = 'none' | 'bearer' | 'basic' | 'apikey';

interface AgentTool {
  id: string;
  name: string;
  description: string;
  type: AgentToolKind;
  inputSchema: string;
  outputSchema: string;
  url?: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  authType?: AgentToolAuthType;
  authRef?: string;
  authIn?: 'header' | 'query';
  authKey?: string;
  headers?: string;
  bodyTemplate?: string;
  jarId?: string;
  className?: string;
  methodName?: string;
}

interface AgentProcessTemplate {
  title: string;
  description: string;
  definition: Record<string, unknown>;
}

const splitLines = (value: string) => value.split('\n').map(line => line.trim()).filter(Boolean);

const getRuntimeDefault = (key: string, fallback: string) =>
  getRuntimeConfigValue(key) ?? (import.meta.env[key] as string | undefined) ?? fallback;

const defaultSystemPrompt = 'You are an Easy BPM orchestration agent. Return concise, auditable decisions as JSON when possible.';
const defaultPromptTemplate = 'Goal: {{goal}}\nInstructions: {{instructions}}\nConstraints: {{constraints}}\nAvailable tools: {{tools}}\nInputs: {{inputs}}\n\nDecide the next orchestration outcome and explain the reason. When a tool is needed, include the intended tool call and arguments in the JSON response.';
const defaultProviderEndpoint = (providerId: string) =>
  providerId === 'ollama' ? 'http://host.docker.internal:11434' : '';
const createToolId = () => Math.random().toString(36).slice(2, 10);
const defaultApiInputSchema = '{\n  "type": "object",\n  "properties": {}\n}';
const defaultApiOutputSchema = '{\n  "type": "object",\n  "properties": {}\n}';
const defaultCodeInputSchema = '{\n  "type": "object",\n  "properties": {}\n}';
const defaultCodeOutputSchema = '{\n  "type": "object",\n  "properties": {}\n}';

const agentProcessTemplates: AgentProcessTemplate[] = [
  {
    title: 'Customer Support Resolution',
    description: 'Investigate customer complaints and recommend an auditable resolution.',
    definition: {
      resourceType: 'AgentProcess',
      processKey: 'customer-support-resolution',
      processName: 'Customer Support Resolution',
      goal: 'Resolve the customer complaint while respecting refund policy and keeping the customer informed.',
      instructions: 'Investigate the complaint, inspect available context, identify the likely root cause, decide whether a refund, replacement, escalation, or clarification is required, and return an auditable recommendation.',
      constraints: [
        'Refunds above 500 require manager approval.',
        'Never promise a refund without policy evidence.',
        'Always explain the reason for the decision.',
        'Ask for human input when evidence is incomplete.'
      ],
      availableTools: [
        {
          id: 'crm_lookup',
          name: 'CRM Lookup',
          type: 'api-call',
          description: 'Look up customer account, order history, and previous tickets.',
          url: 'https://crm.example.com/customers/{{customerId}}',
          method: 'GET',
          auth: {
            type: 'bearer',
            ref: 'CRM_API_TOKEN'
          },
          inputSchema: {
            type: 'object',
            properties: {
              customerId: { type: 'string' }
            },
            required: ['customerId']
          },
          outputSchema: {
            type: 'object',
            properties: {
              tier: { type: 'string' },
              previousTickets: { type: 'array' }
            }
          }
        },
        {
          id: 'refund_policy_check',
          name: 'Refund Policy Check',
          type: 'code-task',
          description: 'Evaluate refund rules using an uploaded Java policy service.',
          jarId: 'policy-service',
          className: 'com.easy.bpm.policy.RefundPolicyService',
          methodName: 'evaluateRefund',
          inputSchema: {
            type: 'object',
            properties: {
              refundAmount: { type: 'number' },
              customerTier: { type: 'string' }
            }
          },
          outputSchema: {
            type: 'object',
            properties: {
              approved: { type: 'boolean' },
              requiresApproval: { type: 'boolean' },
              reason: { type: 'string' }
            }
          }
        }
      ],
      provider: {
        providerId: 'ollama',
        modelName: 'llama3.2',
        endpoint: 'http://host.docker.internal:11434',
        systemPrompt: 'You are an Easy BPM orchestration agent. Return concise, auditable decisions. Prefer JSON with decision, reason, requiresApproval, variables, and customerMessage.',
        promptTemplate: 'Goal: {{goal}}\nInstructions: {{instructions}}\nConstraints:\n{{constraints}}\nInputs:\n{{inputs}}\n\nReturn a JSON decision with: decision, reason, requiresApproval, variables, customerMessage.'
      }
    }
  },
  {
    title: 'Invoice Exception Review',
    description: 'Review invoice mismatches and decide approve, reject, or escalate.',
    definition: {
      resourceType: 'AgentProcess',
      processKey: 'invoice-exception-review',
      processName: 'Invoice Exception Review',
      goal: 'Analyze invoice exceptions and recommend whether the invoice should be approved, rejected, or escalated.',
      instructions: 'Compare invoice details against purchase order, receipt, vendor history, and approval policy. Return a clear decision and rationale.',
      constraints: [
        'Escalate when the mismatch is above tolerance.',
        'Do not approve invoices with missing receipt evidence.',
        'Include the variables needed by downstream BPMN gateways.'
      ],
      provider: {
        systemPrompt: defaultSystemPrompt,
        promptTemplate: 'Goal: {{goal}}\nInstructions: {{instructions}}\nConstraints:\n{{constraints}}\nInputs:\n{{inputs}}\n\nReturn JSON with: decision, reason, requiresApproval, variables.'
      }
    }
  },
  {
    title: 'Employee Onboarding Coordinator',
    description: 'Coordinate onboarding decisions across HR, IT, and facilities.',
    definition: {
      resourceType: 'AgentProcess',
      processKey: 'employee-onboarding-coordinator',
      processName: 'Employee Onboarding Coordinator',
      goal: 'Coordinate onboarding readiness and identify missing actions before the employee start date.',
      instructions: 'Review employee profile, role, start date, equipment needs, access requirements, and pending blockers. Return readiness status and next recommended action.',
      constraints: [
        'Escalate missing access for roles with security-sensitive systems.',
        'Do not mark onboarding ready when required equipment is unavailable.',
        'Explain every blocker in business language.'
      ],
      provider: {
        systemPrompt: defaultSystemPrompt,
        promptTemplate: 'Goal: {{goal}}\nInstructions: {{instructions}}\nConstraints:\n{{constraints}}\nInputs:\n{{inputs}}\n\nReturn JSON with: readinessStatus, reason, blockers, variables.'
      }
    }
  }
];

const createBlankAgentState = (): AgentBoardState => {
  const defaultProviderId = getRuntimeDefault('EASY_BPM_MODELER_DEFAULT_AI_PROVIDER', 'gemini');
  return {
  processName: '',
  goal: '',
  instructions: '',
  constraints: '',
  availableTools: [],
  providerId: defaultProviderId,
  modelName: getRuntimeDefault('EASY_BPM_MODELER_DEFAULT_AI_MODEL', 'gemini-3.5-flash'),
  endpoint: defaultProviderEndpoint(defaultProviderId),
  credentialRef: getRuntimeDefault('EASY_BPM_MODELER_DEFAULT_AI_CREDENTIAL_REF', '$GEMINI_API_KEY'),
  systemPrompt: defaultSystemPrompt,
  promptTemplate: defaultPromptTemplate
  };
};

const toMultiline = (value: unknown): string => {
  if (Array.isArray(value)) return value.map(String).join('\n');
  return typeof value === 'string' ? value : '';
};

const stringifyJsonLike = (value: unknown, fallback: string): string => {
  if (value === undefined || value === null || value === '') return fallback;
  if (typeof value === 'string') return value;
  return JSON.stringify(value, null, 2);
};

const normalizeTool = (tool: unknown, index: number): AgentTool | null => {
  if (typeof tool === 'string') {
    return {
      id: tool.trim().toLowerCase().replace(/[^a-z0-9]+/g, '_') || `tool_${index + 1}`,
      name: tool,
      description: '',
      type: 'api-call',
      method: 'GET',
      authType: 'none',
      inputSchema: defaultApiInputSchema,
      outputSchema: defaultApiOutputSchema
    };
  }
  if (!tool || typeof tool !== 'object') return null;

  const raw = tool as Record<string, any>;
  const auth = raw.auth && typeof raw.auth === 'object' ? raw.auth : {};
  const type = raw.type === 'code-task' || raw.type === 'codeTask' ? 'code-task' : 'api-call';
  const fallbackInput = type === 'code-task' ? defaultCodeInputSchema : defaultApiInputSchema;
  const fallbackOutput = type === 'code-task' ? defaultCodeOutputSchema : defaultApiOutputSchema;

  return {
    id: typeof raw.id === 'string' && raw.id.trim() ? raw.id : createToolId(),
    name: typeof raw.name === 'string' ? raw.name : `Tool ${index + 1}`,
    description: typeof raw.description === 'string' ? raw.description : '',
    type,
    inputSchema: stringifyJsonLike(raw.inputSchema, fallbackInput),
    outputSchema: stringifyJsonLike(raw.outputSchema, fallbackOutput),
    url: typeof raw.url === 'string' ? raw.url : '',
    method: ['GET', 'POST', 'PUT', 'DELETE'].includes(raw.method) ? raw.method : 'GET',
    authType: ['none', 'bearer', 'basic', 'apikey'].includes(auth.type || raw.authType) ? (auth.type || raw.authType) : 'none',
    authRef: typeof auth.ref === 'string' ? auth.ref : (typeof raw.authRef === 'string' ? raw.authRef : ''),
    authIn: ['header', 'query'].includes(auth.in || raw.authIn) ? (auth.in || raw.authIn) : 'header',
    authKey: typeof auth.key === 'string' ? auth.key : (typeof raw.authKey === 'string' ? raw.authKey : 'X-API-Key'),
    headers: stringifyJsonLike(raw.headers, ''),
    bodyTemplate: stringifyJsonLike(raw.bodyTemplate ?? raw.body, ''),
    jarId: raw.jarId !== undefined && raw.jarId !== null ? String(raw.jarId) : '',
    className: typeof raw.className === 'string' ? raw.className : '',
    methodName: typeof raw.methodName === 'string' ? raw.methodName : ''
  };
};

const normalizeTools = (value: unknown): AgentTool[] => {
  if (!Array.isArray(value)) return [];
  return value.map(normalizeTool).filter((tool): tool is AgentTool => Boolean(tool));
};

const parseJsonField = (value: string): unknown => {
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  try {
    return JSON.parse(trimmed);
  } catch {
    return trimmed;
  }
};

const serializeTool = (tool: AgentTool) => {
  const common = {
    id: tool.id,
    name: tool.name.trim() || tool.id,
    type: tool.type,
    description: tool.description.trim(),
    inputSchema: parseJsonField(tool.inputSchema) ?? {},
    outputSchema: parseJsonField(tool.outputSchema) ?? {}
  };

  if (tool.type === 'code-task') {
    return {
      ...common,
      jarId: tool.jarId?.trim() || undefined,
      className: tool.className?.trim() || undefined,
      methodName: tool.methodName?.trim() || undefined
    };
  }

  const authType = tool.authType || 'none';
  return {
    ...common,
    url: tool.url?.trim() || '',
    method: tool.method || 'GET',
    auth: authType === 'none'
      ? { type: 'none' }
      : {
          type: authType,
          ref: tool.authRef?.trim() || undefined,
          in: authType === 'apikey' ? (tool.authIn || 'header') : undefined,
          key: authType === 'apikey' ? (tool.authKey?.trim() || 'X-API-Key') : undefined
        },
    headers: parseJsonField(tool.headers || ''),
    bodyTemplate: parseJsonField(tool.bodyTemplate || '')
  };
};

const normalizeImportedAgent = (data: unknown): AgentBoardState => {
  if (!data || typeof data !== 'object') {
    throw new Error('The selected file is not a valid Agent Process JSON object.');
  }

  const imported = data as Record<string, any>;
  if (imported.resourceType && imported.resourceType !== 'AgentProcess') {
    throw new Error(`Unsupported resourceType: ${imported.resourceType}`);
  }

  const blank = createBlankAgentState();
  const provider = imported.provider && typeof imported.provider === 'object' ? imported.provider : {};

  return {
    processName: typeof imported.processName === 'string' ? imported.processName : '',
    goal: typeof imported.goal === 'string' ? imported.goal : '',
    instructions: typeof imported.instructions === 'string' ? imported.instructions : '',
    constraints: toMultiline(imported.constraints),
    availableTools: normalizeTools(imported.availableTools),
    providerId: typeof provider.providerId === 'string' ? provider.providerId : blank.providerId,
    modelName: typeof provider.modelName === 'string' ? provider.modelName : blank.modelName,
    endpoint: typeof provider.endpoint === 'string'
      ? provider.endpoint
      : defaultProviderEndpoint(typeof provider.providerId === 'string' ? provider.providerId : blank.providerId),
    credentialRef: typeof provider.credentialRef === 'string' ? provider.credentialRef : blank.credentialRef,
    systemPrompt: typeof provider.systemPrompt === 'string' ? provider.systemPrompt : blank.systemPrompt,
    promptTemplate: typeof provider.promptTemplate === 'string' ? provider.promptTemplate : blank.promptTemplate
  };
};

export const AgentBoardModeler: React.FC<AgentBoardModelerProps> = ({
  currentUser,
  onBack,
  onLogout,
  theme,
  onToggleTheme,
  initialDefinition,
  availableCredentials = []
}) => {
  const [agentState, setAgentState] = useState<AgentBoardState>(() => initialDefinition ? normalizeImportedAgent(initialDefinition) : createBlankAgentState());
  const [isDeploying, setIsDeploying] = useState(false);
  const [isTemplateBrowserOpen, setIsTemplateBrowserOpen] = useState(false);
  const [credentials, setCredentials] = useState<AvailableCredential[]>(availableCredentials);
  const importInputRef = useRef<HTMLInputElement>(null);
  const {
    processName,
    goal,
    instructions,
    constraints,
    availableTools,
    providerId,
    modelName,
    endpoint,
    credentialRef,
    systemPrompt,
    promptTemplate,
  } = agentState;

  useEffect(() => {
    setCredentials(availableCredentials);
  }, [availableCredentials]);

  useEffect(() => {
    if (availableCredentials.length > 0) return;
    processService.listAvailableCredentials()
      .then(setCredentials)
      .catch(() => setCredentials([]));
  }, [availableCredentials.length]);

  const secretsForProvider = credentials.filter(secret => secret.providerId === providerId || secret.providerId === 'custom-api');
  const apiSecrets = credentials.filter(secret => secret.providerId === 'custom-api' || secret.credentialType === 'API_KEY' || secret.credentialType === 'BEARER');

  const updateAgentState = (updates: Partial<AgentBoardState>) => {
    setAgentState(current => ({ ...current, ...updates }));
  };

  const handleProviderChange = (nextProviderId: string) => {
    updateAgentState({
      providerId: nextProviderId,
      endpoint: defaultProviderEndpoint(nextProviderId)
    });
  };

  const addTool = (type: AgentToolKind) => {
    const nextTool: AgentTool = {
      id: createToolId(),
      name: type === 'api-call' ? 'New API tool' : 'New Code Task tool',
      description: '',
      type,
      method: 'GET',
      authType: 'none',
      authIn: 'header',
      authKey: 'X-API-Key',
      inputSchema: type === 'api-call' ? defaultApiInputSchema : defaultCodeInputSchema,
      outputSchema: type === 'api-call' ? defaultApiOutputSchema : defaultCodeOutputSchema
    };
    updateAgentState({ availableTools: [...availableTools, nextTool] });
  };

  const updateTool = (toolId: string, updates: Partial<AgentTool>) => {
    updateAgentState({
      availableTools: availableTools.map(tool => tool.id === toolId ? { ...tool, ...updates } : tool)
    });
  };

  const deleteTool = (toolId: string) => {
    updateAgentState({ availableTools: availableTools.filter(tool => tool.id !== toolId) });
  };

  const loadTemplate = (template: AgentProcessTemplate) => {
    setAgentState(normalizeImportedAgent(template.definition));
    setIsTemplateBrowserOpen(false);
    toast.success(`${template.title} template loaded.`);
  };

  const buildDefinition = () => ({
      resourceType: 'AgentProcess',
      processKey: processName.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-') || 'agent-process',
      processName,
      goal,
      instructions,
      constraints: splitLines(constraints),
      availableTools: availableTools.map(serializeTool),
      provider: {
        providerId,
        modelName,
        endpoint: endpoint.trim() || undefined,
        credentialRef,
        systemPrompt,
        promptTemplate,
        tuningParams: {
          temperature: 0.2,
          topP: 1.0,
          maxTokens: 1200,
          retryCount: 0
        }
      },
      audit: {
        decisionTraceRequired: true,
        createdFrom: 'easy-bpm-modeler-agent-definition',
          exportedAt: new Date().toISOString()
        }
    });

  const exportDefinition = () => {
    const definition = buildDefinition();
    const dataStr = `data:text/json;charset=utf-8,${encodeURIComponent(JSON.stringify(definition, null, 2))}`;
    const link = document.createElement('a');
    link.href = dataStr;
    link.download = `${definition.processKey}.agent-process.json`;
    link.click();
  };

  const resetAgent = () => {
    setAgentState(createBlankAgentState());
    toast.success('New Agent Process started.');
  };

  const importDefinition = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;

    try {
      const text = await file.text();
      const json = JSON.parse(text);
      setAgentState(normalizeImportedAgent(json));
      toast.success('Agent Process imported.');
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Import failed.');
    }
  };

  const deployDefinition = async () => {
    if (!goal.trim()) {
      toast.error('Agent Process goal is required.');
      return;
    }
    const invalidTool = availableTools.find(tool => {
      if (!tool.name.trim()) return true;
      if (tool.type === 'api-call') return !tool.url?.trim();
      return !tool.className?.trim() || !tool.methodName?.trim();
    });
    if (invalidTool) {
      toast.error('Every agent tool needs a name and its required runtime fields.');
      return;
    }

    setIsDeploying(true);
    try {
      await processService.deployAgentProcess(buildDefinition());
      toast.success('Agent Process deployed successfully.');
    } catch (error) {
      if (isAuthRequiredError(error)) {
        toast.error(error.message);
      } else {
        toast.error(error instanceof Error ? error.message : 'Unexpected Agent Process deploy error');
      }
    } finally {
      setIsDeploying(false);
    }
  };

  return (
    <div className="process-modeler flex h-screen flex-col bg-slate-100 text-slate-900" data-theme={theme}>
      <Toaster position="top-right" richColors />
      <div className="modeler-navbar border-b px-5 py-3 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3 min-w-0">
          <button
            type="button"
            onClick={onBack}
            className="modeler-ghost-button flex h-9 w-9 items-center justify-center rounded-md"
            title="Back"
            aria-label="Back"
          >
            <ArrowLeft className="h-4 w-4" />
          </button>
          <div className="flex h-9 w-9 items-center justify-center rounded-md bg-blue-600 text-white">
            <Bot className="h-5 w-5" />
          </div>
          <div className="min-w-0">
            <h1 className="modeler-heading truncate text-sm font-semibold">{processName || 'Agent Process'}</h1>
            <p className="modeler-muted text-xs">Agentic orchestration draft</p>
          </div>
        </div>
        <div className="flex shrink-0 flex-wrap items-center justify-end gap-2">
          <ThemeToggle theme={theme} onToggle={onToggleTheme} />
          <button
            type="button"
            onClick={resetAgent}
            className="inline-flex items-center gap-2 rounded-md border border-slate-300 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-50"
          >
            <FilePlus2 className="h-4 w-4" />
            New
          </button>
          <button
            type="button"
            onClick={() => setIsTemplateBrowserOpen(true)}
            className="inline-flex items-center gap-2 rounded-md border border-slate-300 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-50"
          >
            <FileText className="h-4 w-4" />
            Templates
          </button>
          <button
            type="button"
            onClick={() => importInputRef.current?.click()}
            className="inline-flex items-center gap-2 rounded-md border border-slate-300 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-50"
          >
            <Upload className="h-4 w-4" />
            Import
          </button>
          <input
            ref={importInputRef}
            type="file"
            accept=".json,application/json"
            onChange={importDefinition}
            className="hidden"
          />
          <button
            type="button"
            onClick={exportDefinition}
            className="inline-flex items-center gap-2 rounded-md border border-slate-300 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-50"
          >
            <Download className="h-4 w-4" />
            Export
          </button>
          <button
            type="button"
            onClick={deployDefinition}
            disabled={isDeploying}
            className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-3 py-2 text-xs font-semibold text-white shadow-sm transition-colors hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-70"
          >
            {isDeploying ? <Loader2 className="h-4 w-4 animate-spin" /> : <UploadCloud className="h-4 w-4" />}
            Deploy Agent
          </button>
          <button
            type="button"
            onClick={onLogout}
            className="modeler-ghost-button rounded-md px-3 py-2 text-xs font-medium"
          >
            {currentUser || 'Sign out'}
          </button>
        </div>
      </div>

      <div className="flex min-h-0 flex-1">
        <aside className="w-72 shrink-0 overflow-y-auto border-r border-slate-200 bg-[#121920] p-4 text-slate-200">
          <div className="space-y-5">
            <div>
              <p className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Agent Process</p>
              <p className="mt-2 text-xs leading-relaxed text-slate-400">Goal-oriented AI decision block that uses process inputs, instructions, constraints and provider configuration.</p>
            </div>
            <div className="space-y-2">
              <p className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Start</p>
              <button
                type="button"
                onClick={() => setIsTemplateBrowserOpen(true)}
                className="flex w-full items-center justify-center gap-2 rounded-md border border-white/[0.08] bg-white/[0.06] px-3 py-2 text-xs font-semibold text-slate-100 transition-colors hover:bg-white/[0.1]"
              >
                <FileText className="h-4 w-4" />
                Browse Templates
              </button>
            </div>
            <div className="space-y-2">
              <p className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Controls</p>
              <button
                type="button"
                onClick={deployDefinition}
                disabled={isDeploying}
                className="flex w-full items-center justify-center gap-2 rounded-md bg-blue-600 px-3 py-2 text-xs font-semibold text-white shadow-sm transition-colors hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-70"
              >
                {isDeploying ? <Loader2 className="h-4 w-4 animate-spin" /> : <UploadCloud className="h-4 w-4" />}
                Deploy Agent
              </button>
            </div>
          </div>
        </aside>

        <main className="min-w-0 flex-1 overflow-y-auto bg-slate-100">
          <div className="mx-auto grid w-full max-w-7xl gap-5 px-6 py-6 xl:grid-cols-[minmax(0,1fr)_380px]">
            <section className="rounded-lg border border-slate-200 bg-white shadow-sm xl:order-1">
              <div className="border-b border-slate-200 px-5 py-4">
                <h2 className="text-sm font-semibold text-slate-900">Decision definition</h2>
                <p className="mt-1 text-xs text-slate-500">Describe what this AI block should decide inside the BPMN process.</p>
              </div>
              <div className="space-y-4 p-5">
                <label className="block space-y-1.5">
                  <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Name</span>
                  <input
                    value={processName}
                    onChange={event => updateAgentState({ processName: event.target.value })}
                    className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                    placeholder="Customer Support Resolution"
                  />
                </label>

                <label className="block space-y-1.5">
                  <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Goal</span>
                  <textarea
                    value={goal}
                    onChange={event => updateAgentState({ goal: event.target.value })}
                    className="h-24 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-800 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                    placeholder="State the decision this AI block must produce."
                  />
                </label>

                <div className="grid gap-4 lg:grid-cols-2">
                  <label className="block space-y-1.5">
                    <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Instructions</span>
                    <textarea
                      value={instructions}
                      onChange={event => updateAgentState({ instructions: event.target.value })}
                      className="h-44 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-800 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                      placeholder="Tell the AI how to evaluate the process input."
                    />
                  </label>
                  <label className="block space-y-1.5">
                    <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Constraints</span>
                    <textarea
                      value={constraints}
                      onChange={event => updateAgentState({ constraints: event.target.value })}
                      className="h-44 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-800 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                      placeholder="One constraint per line."
                    />
                  </label>
                </div>
              </div>
            </section>

            <section className="rounded-lg border border-slate-200 bg-white shadow-sm xl:order-3 xl:col-span-2">
              <div className="flex flex-col gap-3 border-b border-slate-200 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h2 className="text-sm font-semibold text-slate-900">Agent tools</h2>
                  <p className="mt-1 text-xs text-slate-500">Configure the systems this agent may call while deciding the next action.</p>
                </div>
                <div className="flex shrink-0 gap-2">
                  <button
                    type="button"
                    onClick={() => addTool('api-call')}
                    className="inline-flex items-center gap-2 rounded-md border border-slate-300 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-50"
                  >
                    <Globe2 className="h-4 w-4" />
                    API Call
                  </button>
                  <button
                    type="button"
                    onClick={() => addTool('code-task')}
                    className="inline-flex items-center gap-2 rounded-md border border-slate-300 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-50"
                  >
                    <Code2 className="h-4 w-4" />
                    Code Task
                  </button>
                </div>
              </div>
              <div className="space-y-4 p-5">
                {availableTools.length === 0 ? (
                  <div className="flex min-h-28 items-center justify-center rounded-md border border-dashed border-slate-300 bg-slate-50 px-4 text-center">
                    <div>
                      <Wrench className="mx-auto h-5 w-5 text-slate-400" />
                      <p className="mt-2 text-sm font-semibold text-slate-700">No tools configured</p>
                      <p className="mt-1 text-xs text-slate-500">Add API calls or Java code tasks that the agent can consider during orchestration.</p>
                    </div>
                  </div>
                ) : (
                  availableTools.map((tool, index) => (
                    <div key={tool.id} className="rounded-md border border-slate-200 bg-slate-50">
                      <div className="flex flex-col gap-3 border-b border-slate-200 bg-white px-4 py-3 lg:flex-row lg:items-center">
                        <div className="flex min-w-0 flex-1 items-center gap-3">
                          <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-md ${tool.type === 'api-call' ? 'bg-emerald-50 text-emerald-600' : 'bg-indigo-50 text-indigo-600'}`}>
                            {tool.type === 'api-call' ? <Globe2 className="h-4 w-4" /> : <Code2 className="h-4 w-4" />}
                          </span>
                          <div className="grid min-w-0 flex-1 gap-2 md:grid-cols-[0.8fr_1.2fr]">
                            <input value={tool.name} onChange={event => updateTool(tool.id, { name: event.target.value })} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" placeholder={`Tool ${index + 1}`} />
                            <input value={tool.description} onChange={event => updateTool(tool.id, { description: event.target.value })} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-800 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" placeholder="What this tool is allowed to do" />
                          </div>
                        </div>
                        <div className="flex shrink-0 items-center gap-2">
                          <select
                            value={tool.type}
                            onChange={event => updateTool(tool.id, {
                              type: event.target.value as AgentToolKind,
                              inputSchema: event.target.value === 'api-call' ? defaultApiInputSchema : defaultCodeInputSchema,
                              outputSchema: event.target.value === 'api-call' ? defaultApiOutputSchema : defaultCodeOutputSchema
                            })}
                            className="rounded-md border border-slate-300 bg-white px-3 py-2 text-xs font-semibold text-slate-700 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                          >
                            <option value="api-call">API Call</option>
                            <option value="code-task">Code Task</option>
                          </select>
                          <button type="button" onClick={() => deleteTool(tool.id)} className="flex h-9 w-9 items-center justify-center rounded-md border border-red-100 bg-white text-red-500 transition-colors hover:bg-red-50" title="Delete tool" aria-label="Delete tool">
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </div>

                      <div className="space-y-4 p-4">
                        {tool.type === 'api-call' ? (
                          <>
                            <div className="grid gap-3 lg:grid-cols-[120px_minmax(0,1fr)]">
                              <label className="block space-y-1.5">
                                <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Method</span>
                                <select value={tool.method || 'GET'} onChange={event => updateTool(tool.id, { method: event.target.value as AgentTool['method'] })} className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100">
                                  <option>GET</option><option>POST</option><option>PUT</option><option>DELETE</option>
                                </select>
                              </label>
                              <label className="block space-y-1.5">
                                <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">URL</span>
                                <input value={tool.url || ''} onChange={event => updateTool(tool.id, { url: event.target.value })} className="w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" placeholder="https://api.example.com/resource/{{id}}" />
                              </label>
                            </div>
                            <div className="grid gap-3 md:grid-cols-3">
                              <label className="block space-y-1.5">
                                <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Auth Type</span>
                                <select value={tool.authType || 'none'} onChange={event => updateTool(tool.id, { authType: event.target.value as AgentToolAuthType })} className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100">
                                  <option value="none">None</option><option value="bearer">Bearer</option><option value="basic">Basic</option><option value="apikey">API Key</option>
                                </select>
                              </label>
                              {(tool.authType || 'none') !== 'none' && (
                                <label className="block space-y-1.5">
                                  <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Auth Ref</span>
                                  {apiSecrets.length > 0 && (
                                    <select value={tool.authRef || ''} onChange={event => updateTool(tool.id, { authRef: event.target.value })} className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100">
                                      <option value="">Select a workspace secret</option>
                                      {apiSecrets.map(secret => (
                                        <option key={secret.id} value={secret.reference}>{secret.name} - {secret.maskedToken}</option>
                                      ))}
                                    </select>
                                  )}
                                  <input value={tool.authRef || ''} onChange={event => updateTool(tool.id, { authRef: event.target.value })} className="w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" placeholder="CRM_API_TOKEN" />
                                </label>
                              )}
                              {(tool.authType || 'none') === 'apikey' && (
                                <label className="block space-y-1.5">
                                  <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">API Key</span>
                                  <div className="grid grid-cols-[100px_minmax(0,1fr)] gap-2">
                                    <select value={tool.authIn || 'header'} onChange={event => updateTool(tool.id, { authIn: event.target.value as 'header' | 'query' })} className="rounded-md border border-slate-300 bg-white px-2 py-2 text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100">
                                      <option value="header">Header</option><option value="query">Query</option>
                                    </select>
                                    <input value={tool.authKey || 'X-API-Key'} onChange={event => updateTool(tool.id, { authKey: event.target.value })} className="min-w-0 rounded-md border border-slate-300 px-3 py-2 font-mono text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" placeholder="X-API-Key" />
                                  </div>
                                </label>
                              )}
                            </div>
                            <div className="grid gap-3 lg:grid-cols-2">
                              <label className="block space-y-1.5">
                                <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Headers JSON</span>
                                <textarea value={tool.headers || ''} onChange={event => updateTool(tool.id, { headers: event.target.value })} className="h-24 w-full resize-none rounded-md border border-slate-300 px-3 py-2 font-mono text-xs text-slate-800 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" placeholder='{ "Accept": "application/json" }' />
                              </label>
                              <label className="block space-y-1.5">
                                <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Body Template</span>
                                <textarea value={tool.bodyTemplate || ''} onChange={event => updateTool(tool.id, { bodyTemplate: event.target.value })} className="h-24 w-full resize-none rounded-md border border-slate-300 px-3 py-2 font-mono text-xs text-slate-800 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" placeholder='{ "caseId": "{{caseId}}" }' />
                              </label>
                            </div>
                          </>
                        ) : (
                          <div className="grid gap-3 md:grid-cols-3">
                            <label className="block space-y-1.5">
                              <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Jar ID</span>
                              <input value={tool.jarId || ''} onChange={event => updateTool(tool.id, { jarId: event.target.value })} className="w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" placeholder="42 or policy-service" />
                            </label>
                            <label className="block space-y-1.5">
                              <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Java Class</span>
                              <input value={tool.className || ''} onChange={event => updateTool(tool.id, { className: event.target.value })} className="w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" placeholder="com.example.PolicyService" />
                            </label>
                            <label className="block space-y-1.5">
                              <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Method</span>
                              <input value={tool.methodName || ''} onChange={event => updateTool(tool.id, { methodName: event.target.value })} className="w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" placeholder="evaluate" />
                            </label>
                          </div>
                        )}
                        <div className="grid gap-3 lg:grid-cols-2">
                          <label className="block space-y-1.5">
                            <span className="inline-flex items-center gap-1 text-[10px] font-bold uppercase tracking-widest text-slate-500"><Braces className="h-3 w-3" />Input Schema</span>
                            <textarea value={tool.inputSchema} onChange={event => updateTool(tool.id, { inputSchema: event.target.value })} className="h-32 w-full resize-none rounded-md border border-slate-300 px-3 py-2 font-mono text-xs text-slate-800 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
                          </label>
                          <label className="block space-y-1.5">
                            <span className="inline-flex items-center gap-1 text-[10px] font-bold uppercase tracking-widest text-slate-500"><Braces className="h-3 w-3" />Output Schema</span>
                            <textarea value={tool.outputSchema} onChange={event => updateTool(tool.id, { outputSchema: event.target.value })} className="h-32 w-full resize-none rounded-md border border-slate-300 px-3 py-2 font-mono text-xs text-slate-800 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
                          </label>
                        </div>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </section>

            <aside className="rounded-lg border border-slate-200 bg-white shadow-sm xl:order-2">
              <div className="border-b border-slate-200 px-5 py-4">
                <h2 className="text-sm font-semibold text-slate-900">Provider</h2>
                <p className="mt-1 text-xs text-slate-500">Runtime AI connection used by the backend.</p>
              </div>
              <div className="space-y-4 p-5">
                <label className="block space-y-1.5">
                  <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Provider</span>
                  <select value={providerId} onChange={event => handleProviderChange(event.target.value)} className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100">
                    <option value="openai">OpenAI</option>
                    <option value="anthropic">Anthropic</option>
                    <option value="gemini">Gemini</option>
                    <option value="azure-openai">Azure OpenAI</option>
                    <option value="ollama">Ollama</option>
                    <option value="custom-rest">Custom REST</option>
                  </select>
                </label>
                <label className="block space-y-1.5">
                  <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Model</span>
                  <input value={modelName} onChange={event => updateAgentState({ modelName: event.target.value })} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
                </label>
                <label className="block space-y-1.5">
                  <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Endpoint</span>
                  <input value={endpoint} onChange={event => updateAgentState({ endpoint: event.target.value })} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" placeholder={providerId === 'ollama' ? 'http://host.docker.internal:11434' : 'Optional provider endpoint'} />
                </label>
                <label className="block space-y-1.5">
                  <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Credential Ref</span>
                  {secretsForProvider.length > 0 && (
                    <select value={credentialRef} onChange={event => updateAgentState({ credentialRef: event.target.value })} className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100">
                      <option value="">Select a workspace secret</option>
                      {secretsForProvider.map(secret => (
                        <option key={secret.id} value={secret.reference}>{secret.name} - {secret.maskedToken}</option>
                      ))}
                    </select>
                  )}
                  <input value={credentialRef} onChange={event => updateAgentState({ credentialRef: event.target.value })} className="w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" placeholder="$GEMINI_API_KEY" />
                </label>
              </div>
            </aside>

            <section className="rounded-lg border border-slate-200 bg-white shadow-sm xl:order-4 xl:col-span-2">
              <div className="border-b border-slate-200 px-5 py-4">
                <h2 className="text-sm font-semibold text-slate-900">Prompt</h2>
                <p className="mt-1 text-xs text-slate-500">The backend renders this template with goal, instructions, constraints and process inputs.</p>
              </div>
              <div className="grid gap-4 p-5 lg:grid-cols-[0.9fr_1.4fr]">
                <label className="block space-y-1.5">
                  <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">System Prompt</span>
                  <textarea value={systemPrompt} onChange={event => updateAgentState({ systemPrompt: event.target.value })} className="h-40 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-800 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
                </label>
                <label className="block space-y-1.5">
                  <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Prompt Template</span>
                  <textarea value={promptTemplate} onChange={event => updateAgentState({ promptTemplate: event.target.value })} className="h-40 w-full resize-none rounded-md border border-slate-300 px-3 py-2 font-mono text-sm text-slate-800 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100" />
                </label>
              </div>
            </section>
          </div>

          <section className="border-t border-slate-200 bg-white px-6 py-5">
            <div className="flex flex-col gap-3 rounded-md border border-blue-100 bg-blue-50 p-4 text-sm text-blue-900 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-start gap-3">
                <FileText className="mt-0.5 h-4 w-4 shrink-0" />
                <p>This feature-flagged modeler resource deploys an Agent Process definition. Provider and tool credentials stay outside the modeler: use credential references resolved by the backend.</p>
              </div>
              <button
                type="button"
                onClick={deployDefinition}
                disabled={isDeploying}
                className="inline-flex shrink-0 items-center justify-center gap-2 rounded-md bg-blue-600 px-3 py-2 text-xs font-semibold text-white shadow-sm transition-colors hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-70"
              >
                {isDeploying ? <Loader2 className="h-4 w-4 animate-spin" /> : <UploadCloud className="h-4 w-4" />}
                Deploy Agent
              </button>
            </div>
          </section>
        </main>
      </div>
      {isTemplateBrowserOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4 py-6 backdrop-blur-sm">
          <div className="max-h-full w-full max-w-3xl overflow-hidden rounded-lg border border-[var(--modeler-border)] bg-[var(--modeler-surface)] text-[var(--modeler-text)] shadow-2xl">
            <div className="flex items-start justify-between gap-4 border-b border-[var(--modeler-border)] px-5 py-4">
              <div>
                <h2 className="text-base font-semibold text-[var(--modeler-text)]">Agent Process templates</h2>
                <p className="mt-1 text-sm text-[var(--modeler-text-muted)]">Load a focused agent definition and adapt it to your workflow.</p>
              </div>
              <button
                type="button"
                onClick={() => setIsTemplateBrowserOpen(false)}
                className="rounded-md border border-[var(--modeler-border)] bg-[var(--modeler-surface-muted)] px-3 py-2 text-sm font-semibold text-[var(--modeler-text-soft)] transition-colors hover:bg-[var(--modeler-border)] hover:text-[var(--modeler-text)]"
              >
                Close
              </button>
            </div>
            <div className="grid max-h-[70vh] gap-3 overflow-y-auto p-5 sm:grid-cols-2">
              {agentProcessTemplates.map(template => (
                <button
                  key={template.title}
                  type="button"
                  onClick={() => loadTemplate(template)}
                  className="group flex min-h-40 flex-col items-start justify-between rounded-lg border border-[var(--modeler-border)] bg-[var(--modeler-surface-muted)] p-4 text-left transition-colors hover:border-blue-400 hover:bg-blue-500/10"
                >
                  <span className="flex items-start gap-3">
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-blue-500/15 text-blue-400">
                      <Bot className="h-5 w-5" />
                    </span>
                    <span>
                      <span className="block text-sm font-semibold text-[var(--modeler-text)]">{template.title}</span>
                      <span className="mt-1 block text-xs leading-5 text-[var(--modeler-text-muted)]">{template.description}</span>
                    </span>
                  </span>
                  <span className="mt-5 inline-flex items-center gap-2 text-xs font-semibold text-blue-500 transition-colors group-hover:text-blue-400">
                    Load template
                  </span>
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
