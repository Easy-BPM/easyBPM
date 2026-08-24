import React, { useMemo, useRef, useState } from 'react';
import {
  ArrowLeft,
  ArrowDown,
  ArrowUp,
  Bot,
  Brain,
  CheckCircle2,
  CircleDashed,
  Clock3,
  Copy,
  Download,
  FilePlus2,
  FileText,
  GitBranch,
  History,
  Loader2,
  Plus,
  ShieldCheck,
  Sparkles,
  Trash2,
  Upload,
  UploadCloud,
  UserCheck,
  Wrench
} from 'lucide-react';
import { ThemeMode, ThemeToggle } from './ThemeToggle';
import { isAuthRequiredError, processService } from '../services/processService';
import { getRuntimeConfigValue } from '../config/runtimeConfig';
import { Toaster, toast } from 'sonner';

type AgentStepStatus = 'backlog' | 'ready' | 'in-progress' | 'waiting-human' | 'waiting-system' | 'completed' | 'failed';

interface AgentStep {
  id: string;
  title: string;
  description: string;
  reasoning: string;
  status: AgentStepStatus;
  owner: string;
  priority: 'low' | 'medium' | 'high';
  dependencies: string;
}

interface AgentBoardModelerProps {
  currentUser: string | null;
  onBack: () => void;
  onLogout: () => void;
  theme: ThemeMode;
  onToggleTheme: () => void;
  initialDefinition?: unknown;
}

interface AgentBoardState {
  processName: string;
  goal: string;
  instructions: string;
  constraints: string;
  tools: string;
  agents: string;
  memoryPolicy: string;
  providerId: string;
  modelName: string;
  endpoint: string;
  credentialRef: string;
  systemPrompt: string;
  promptTemplate: string;
  allowDynamicTasks: boolean;
  timeoutDays: number;
  steps: AgentStep[];
}

const columns: { id: AgentStepStatus; label: string; icon: React.ReactNode }[] = [
  { id: 'backlog', label: 'Backlog', icon: <CircleDashed className="h-4 w-4" /> },
  { id: 'ready', label: 'Ready', icon: <Sparkles className="h-4 w-4" /> },
  { id: 'in-progress', label: 'In Progress', icon: <Clock3 className="h-4 w-4" /> },
  { id: 'waiting-human', label: 'Waiting Human', icon: <UserCheck className="h-4 w-4" /> },
  { id: 'waiting-system', label: 'Waiting System', icon: <Wrench className="h-4 w-4" /> },
  { id: 'completed', label: 'Completed', icon: <CheckCircle2 className="h-4 w-4" /> },
  { id: 'failed', label: 'Failed', icon: <ShieldCheck className="h-4 w-4" /> }
];

const createStep = (index: number): AgentStep => ({
  id: `step_${Date.now()}_${index}`,
  title: 'New dynamic task',
  description: 'Describe the task the agent or participant may perform.',
  reasoning: 'Capture why this step exists so execution remains auditable.',
  status: 'backlog',
  owner: 'Planner Agent',
  priority: 'medium',
  dependencies: ''
});

const splitLines = (value: string) => value.split('\n').map(line => line.trim()).filter(Boolean);

const getRuntimeDefault = (key: string, fallback: string) =>
  getRuntimeConfigValue(key) ?? (import.meta.env[key] as string | undefined) ?? fallback;

const defaultSystemPrompt = 'You are an Easy BPM orchestration agent. Return concise, auditable decisions as JSON when possible.';
const defaultPromptTemplate = 'Goal: {{goal}}\nInstructions: {{instructions}}\nConstraints: {{constraints}}\nInputs: {{inputs}}\n\nDecide the next orchestration outcome and explain the reason.';
const defaultProviderEndpoint = (providerId: string) =>
  providerId === 'ollama' ? 'http://host.docker.internal:11434' : '';

const createBlankAgentState = (): AgentBoardState => {
  const defaultProviderId = getRuntimeDefault('EASY_BPM_MODELER_DEFAULT_AI_PROVIDER', 'gemini');
  return {
  processName: '',
  goal: '',
  instructions: '',
  constraints: '',
  tools: '',
  agents: '',
  memoryPolicy: '',
  providerId: defaultProviderId,
  modelName: getRuntimeDefault('EASY_BPM_MODELER_DEFAULT_AI_MODEL', 'gemini-3.5-flash'),
  endpoint: defaultProviderEndpoint(defaultProviderId),
  credentialRef: getRuntimeDefault('EASY_BPM_MODELER_DEFAULT_AI_CREDENTIAL_REF', '$GEMINI_API_KEY'),
  systemPrompt: defaultSystemPrompt,
  promptTemplate: defaultPromptTemplate,
  allowDynamicTasks: true,
  timeoutDays: 7,
  steps: []
  };
};

const toMultiline = (value: unknown): string => {
  if (Array.isArray(value)) return value.map(String).join('\n');
  return typeof value === 'string' ? value : '';
};

const validStepStatuses = new Set<AgentStepStatus>(columns.map(column => column.id));
const validPriorities = new Set<AgentStep['priority']>(['low', 'medium', 'high']);

const normalizeImportedSteps = (value: unknown): AgentStep[] => {
  if (!Array.isArray(value)) return [];
  return value.map((item, index) => {
    const step = item && typeof item === 'object' ? item as Record<string, unknown> : {};
    const status = typeof step.status === 'string' && validStepStatuses.has(step.status as AgentStepStatus)
      ? step.status as AgentStepStatus
      : 'backlog';
    const priority = typeof step.priority === 'string' && validPriorities.has(step.priority as AgentStep['priority'])
      ? step.priority as AgentStep['priority']
      : 'medium';

    return {
      id: typeof step.id === 'string' && step.id.trim() ? step.id : `step_imported_${index + 1}`,
      title: typeof step.title === 'string' ? step.title : 'Imported task',
      description: typeof step.description === 'string' ? step.description : '',
      reasoning: typeof step.reasoning === 'string' ? step.reasoning : '',
      status,
      owner: typeof step.owner === 'string' ? step.owner : '',
      priority,
      dependencies: typeof step.dependencies === 'string' ? step.dependencies : ''
    };
  });
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
    tools: toMultiline(imported.availableTools),
    agents: toMultiline(imported.participants),
    memoryPolicy: typeof imported.memoryPolicy === 'string' ? imported.memoryPolicy : '',
    providerId: typeof provider.providerId === 'string' ? provider.providerId : blank.providerId,
    modelName: typeof provider.modelName === 'string' ? provider.modelName : blank.modelName,
    endpoint: typeof provider.endpoint === 'string'
      ? provider.endpoint
      : defaultProviderEndpoint(typeof provider.providerId === 'string' ? provider.providerId : blank.providerId),
    credentialRef: typeof provider.credentialRef === 'string' ? provider.credentialRef : blank.credentialRef,
    systemPrompt: typeof provider.systemPrompt === 'string' ? provider.systemPrompt : blank.systemPrompt,
    promptTemplate: typeof provider.promptTemplate === 'string' ? provider.promptTemplate : blank.promptTemplate,
    allowDynamicTasks: typeof imported.allowDynamicTasks === 'boolean' ? imported.allowDynamicTasks : true,
    timeoutDays: typeof imported.timeoutDays === 'number' && imported.timeoutDays > 0 ? imported.timeoutDays : 7,
    steps: normalizeImportedSteps(imported.steps)
  };
};

export const AgentBoardModeler: React.FC<AgentBoardModelerProps> = ({
  currentUser,
  onBack,
  onLogout,
  theme,
  onToggleTheme,
  initialDefinition
}) => {
  const [agentState, setAgentState] = useState<AgentBoardState>(() => initialDefinition ? normalizeImportedAgent(initialDefinition) : createBlankAgentState());
  const [isDeploying, setIsDeploying] = useState(false);
  const importInputRef = useRef<HTMLInputElement>(null);
  const {
    processName,
    goal,
    instructions,
    constraints,
    tools,
    agents,
    memoryPolicy,
    providerId,
    modelName,
    endpoint,
    credentialRef,
    systemPrompt,
    promptTemplate,
    allowDynamicTasks,
    timeoutDays,
    steps
  } = agentState;

  const updateAgentState = (updates: Partial<AgentBoardState>) => {
    setAgentState(current => ({ ...current, ...updates }));
  };

  const handleProviderChange = (nextProviderId: string) => {
    updateAgentState({
      providerId: nextProviderId,
      endpoint: defaultProviderEndpoint(nextProviderId)
    });
  };

  const boardCounts = useMemo(() => {
    return columns.reduce<Record<AgentStepStatus, number>>((acc, column) => {
      acc[column.id] = steps.filter(step => step.status === column.id).length;
      return acc;
    }, {} as Record<AgentStepStatus, number>);
  }, [steps]);

  const updateStep = (id: string, updates: Partial<AgentStep>) => {
    updateAgentState({ steps: steps.map(step => step.id === id ? { ...step, ...updates } : step) });
  };

  const addStep = () => {
    updateAgentState({ steps: [...steps, createStep(steps.length + 1)] });
  };

  const deleteStep = (id: string) => {
    updateAgentState({ steps: steps.filter(item => item.id !== id) });
  };

  const duplicateStep = (id: string) => {
    const sourceIndex = steps.findIndex(step => step.id === id);
    if (sourceIndex < 0) return;

    const source = steps[sourceIndex];
    const duplicate: AgentStep = {
      ...source,
      id: `step_${Date.now()}_${sourceIndex + 2}`,
      title: `${source.title || 'Step'} copy`
    };

    updateAgentState({
      steps: [
        ...steps.slice(0, sourceIndex + 1),
        duplicate,
        ...steps.slice(sourceIndex + 1)
      ]
    });
  };

  const moveStep = (id: string, direction: -1 | 1) => {
    const currentIndex = steps.findIndex(step => step.id === id);
    const nextIndex = currentIndex + direction;
    if (currentIndex < 0 || nextIndex < 0 || nextIndex >= steps.length) return;

    const nextSteps = [...steps];
    const [movedStep] = nextSteps.splice(currentIndex, 1);
    nextSteps.splice(nextIndex, 0, movedStep);
    updateAgentState({ steps: nextSteps });
  };

  const buildDefinition = () => ({
      resourceType: 'AgentProcess',
      processKey: processName.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-') || 'agent-process',
      processName,
      goal,
      instructions,
      constraints: splitLines(constraints),
      availableTools: splitLines(tools),
      participants: splitLines(agents),
      memoryPolicy,
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
      allowDynamicTasks,
      timeoutDays,
      boardColumns: columns.map(column => column.id),
      steps,
      audit: {
        decisionTraceRequired: true,
        createdFrom: 'easy-bpm-modeler-agent-plan',
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
              <p className="mt-2 text-xs leading-relaxed text-slate-400">Goal-oriented execution where agents plan ordered work, wait, delegate, and explain decisions.</p>
            </div>
            <div className="space-y-2">
              <p className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Plan Status</p>
              {columns.map(column => (
                <div key={column.id} className="flex items-center justify-between rounded-md border border-white/[0.07] bg-white/[0.04] px-3 py-2">
                  <span className="flex items-center gap-2 text-xs text-slate-300">{column.icon}{column.label}</span>
                  <span className="text-xs font-semibold tabular-nums text-slate-400">{boardCounts[column.id]}</span>
                </div>
              ))}
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
              <label className="flex items-center justify-between rounded-md border border-white/[0.07] bg-white/[0.04] px-3 py-2 text-xs">
                <span>Allow dynamic tasks</span>
                <input
                  type="checkbox"
                  checked={allowDynamicTasks}
                  onChange={event => updateAgentState({ allowDynamicTasks: event.target.checked })}
                  className="h-4 w-4 rounded border-slate-500 text-blue-600"
                />
              </label>
              <label className="block text-[10px] font-bold uppercase tracking-widest text-slate-500">Timeout days</label>
              <input
                type="number"
                min={1}
                value={timeoutDays}
                onChange={event => updateAgentState({ timeoutDays: Number(event.target.value) || 1 })}
                className="w-full rounded-md border border-white/[0.08] bg-white/[0.06] px-3 py-2 text-xs text-slate-100 outline-none focus:border-blue-500"
              />
            </div>
          </div>
        </aside>

        <main className="min-w-0 flex-1 overflow-y-auto">
          <section className="border-b border-slate-200 bg-white px-6 py-5">
            <div className="grid gap-4 xl:grid-cols-[1.2fr_1fr_1fr]">
              <div className="space-y-2">
                <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Name</label>
                <input
                  value={processName}
                  onChange={event => updateAgentState({ processName: event.target.value })}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm font-semibold outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
                <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Goal</label>
                <textarea
                  value={goal}
                  onChange={event => updateAgentState({ goal: event.target.value })}
                  className="h-24 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>
              <div className="space-y-2">
                <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Instructions</label>
                <textarea
                  value={instructions}
                  onChange={event => updateAgentState({ instructions: event.target.value })}
                  className="h-36 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>
              <div className="space-y-2">
                <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Constraints</label>
                <textarea
                  value={constraints}
                  onChange={event => updateAgentState({ constraints: event.target.value })}
                  className="h-36 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>
            </div>
          </section>

          <section className="grid gap-4 border-b border-slate-200 bg-slate-50 px-6 py-5 lg:grid-cols-3">
            <div className="space-y-2">
              <label className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-widest text-slate-500"><Wrench className="h-3.5 w-3.5" />Available Tools</label>
              <textarea value={tools} onChange={event => updateAgentState({ tools: event.target.value })} className="h-28 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
            </div>
            <div className="space-y-2">
              <label className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-widest text-slate-500"><Brain className="h-3.5 w-3.5" />Agents</label>
              <textarea value={agents} onChange={event => updateAgentState({ agents: event.target.value })} className="h-28 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
            </div>
            <div className="space-y-2">
              <label className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-widest text-slate-500"><History className="h-3.5 w-3.5" />Memory and Audit</label>
              <textarea value={memoryPolicy} onChange={event => updateAgentState({ memoryPolicy: event.target.value })} className="h-28 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
            </div>
          </section>

          <section className="grid gap-4 border-b border-slate-200 bg-white px-6 py-5 lg:grid-cols-4">
            <div className="space-y-2">
              <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Provider</label>
              <select value={providerId} onChange={event => handleProviderChange(event.target.value)} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500">
                <option value="openai">OpenAI</option>
                <option value="anthropic">Anthropic</option>
                <option value="gemini">Gemini</option>
                <option value="azure-openai">Azure OpenAI</option>
                <option value="ollama">Ollama</option>
                <option value="custom-rest">Custom REST</option>
              </select>
            </div>
            <div className="space-y-2">
              <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Model</label>
              <input value={modelName} onChange={event => updateAgentState({ modelName: event.target.value })} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
            </div>
            <div className="space-y-2">
              <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Endpoint</label>
              <input value={endpoint} onChange={event => updateAgentState({ endpoint: event.target.value })} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" placeholder={providerId === 'ollama' ? 'http://host.docker.internal:11434' : 'Optional provider endpoint'} />
            </div>
            <div className="space-y-2">
              <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Credential Ref</label>
              <input value={credentialRef} onChange={event => updateAgentState({ credentialRef: event.target.value })} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm font-mono outline-none focus:border-blue-500" placeholder="$GEMINI_API_KEY" />
            </div>
            <div className="space-y-2">
              <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">System Prompt</label>
              <textarea value={systemPrompt} onChange={event => updateAgentState({ systemPrompt: event.target.value })} className="h-20 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
            </div>
            <div className="space-y-2 lg:col-span-4">
              <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Planner Prompt Template</label>
              <textarea value={promptTemplate} onChange={event => updateAgentState({ promptTemplate: event.target.value })} className="h-24 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm font-mono outline-none focus:border-blue-500" />
            </div>
          </section>

          <section className="px-6 py-5">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold text-slate-800">Execution Plan</h2>
                <p className="text-xs text-slate-500">Order the concrete steps the agent process should plan, execute, wait on, and audit.</p>
              </div>
              <button
                type="button"
                onClick={addStep}
                className="inline-flex items-center gap-2 rounded-md border border-slate-300 bg-white px-3 py-2 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-50"
              >
                <Plus className="h-4 w-4" />
                Step
              </button>
            </div>
            <div className="space-y-3">
              {steps.length === 0 ? (
                <div className="flex min-h-48 flex-col items-center justify-center gap-3 rounded-md border border-dashed border-slate-300 bg-white px-4 py-8 text-center">
                  <GitBranch className="h-8 w-8 text-slate-300" />
                  <div>
                    <h3 className="text-sm font-semibold text-slate-800">No planned steps yet</h3>
                    <p className="mt-1 text-xs text-slate-500">Add the first execution step to make this agent process deployable and auditable.</p>
                  </div>
                  <button
                    type="button"
                    onClick={addStep}
                    className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-3 py-2 text-xs font-semibold text-white shadow-sm transition-colors hover:bg-blue-500"
                  >
                    <Plus className="h-4 w-4" />
                    Add Step
                  </button>
                </div>
              ) : steps.map((step, index) => (
                <article key={step.id} className="rounded-md border border-slate-200 bg-white shadow-sm">
                  <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
                    <div className="flex min-w-0 items-center gap-3">
                      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-slate-900 text-xs font-semibold text-white tabular-nums">{index + 1}</span>
                      <div className="min-w-0">
                        <input
                          value={step.title}
                          onChange={event => updateStep(step.id, { title: event.target.value })}
                          className="w-full min-w-64 bg-transparent text-sm font-semibold text-slate-800 outline-none"
                        />
                        <span className="mt-1 flex items-center gap-1 text-[10px] font-mono text-slate-400"><GitBranch className="h-3 w-3" />{step.id}</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-1">
                      <button type="button" onClick={() => moveStep(step.id, -1)} disabled={index === 0} className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 text-slate-500 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40" title="Move up" aria-label="Move step up">
                        <ArrowUp className="h-4 w-4" />
                      </button>
                      <button type="button" onClick={() => moveStep(step.id, 1)} disabled={index === steps.length - 1} className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 text-slate-500 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40" title="Move down" aria-label="Move step down">
                        <ArrowDown className="h-4 w-4" />
                      </button>
                      <button type="button" onClick={() => duplicateStep(step.id)} className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 text-slate-500 transition-colors hover:bg-slate-50" title="Duplicate step" aria-label="Duplicate step">
                        <Copy className="h-4 w-4" />
                      </button>
                      <button type="button" onClick={() => deleteStep(step.id)} className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 text-slate-500 transition-colors hover:border-red-200 hover:bg-red-50 hover:text-red-600" title="Delete step" aria-label="Delete step">
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                  <div className="grid gap-4 p-4 xl:grid-cols-[1.2fr_1fr]">
                    <div className="grid gap-3 sm:grid-cols-2">
                      <label className="space-y-1 sm:col-span-2">
                        <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Description</span>
                        <textarea
                          value={step.description}
                          onChange={event => updateStep(step.id, { description: event.target.value })}
                          className="h-24 w-full resize-none rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                        />
                      </label>
                      <label className="space-y-1 sm:col-span-2">
                        <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Audit Reasoning</span>
                        <textarea
                          value={step.reasoning}
                          onChange={event => updateStep(step.id, { reasoning: event.target.value })}
                          className="h-20 w-full resize-none rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                        />
                      </label>
                    </div>
                    <div className="grid content-start gap-3 sm:grid-cols-2">
                      <label className="space-y-1">
                        <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Status</span>
                        <select value={step.status} onChange={event => updateStep(step.id, { status: event.target.value as AgentStepStatus })} className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500">
                          {columns.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}
                        </select>
                      </label>
                      <label className="space-y-1">
                        <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Priority</span>
                        <select value={step.priority} onChange={event => updateStep(step.id, { priority: event.target.value as AgentStep['priority'] })} className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500">
                          <option value="low">Low priority</option>
                          <option value="medium">Medium priority</option>
                          <option value="high">High priority</option>
                        </select>
                      </label>
                      <label className="space-y-1">
                        <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Owner</span>
                        <input value={step.owner} onChange={event => updateStep(step.id, { owner: event.target.value })} className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500" />
                      </label>
                      <label className="space-y-1">
                        <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Dependencies</span>
                        <input value={step.dependencies} onChange={event => updateStep(step.id, { dependencies: event.target.value })} placeholder="Step ids or external blockers" className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500" />
                      </label>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </section>

          <section className="border-t border-slate-200 bg-white px-6 py-5">
            <div className="flex flex-col gap-3 rounded-md border border-blue-100 bg-blue-50 p-4 text-sm text-blue-900 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-start gap-3">
                <FileText className="mt-0.5 h-4 w-4 shrink-0" />
                <p>This feature-flagged modeler resource deploys an Agent Process definition. Provider tokens stay outside the modeler: use a credential reference resolved by the backend.</p>
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
    </div>
  );
};
