import React, { useMemo, useState } from 'react';
import {
  ArrowLeft,
  Bot,
  Brain,
  CheckCircle2,
  CircleDashed,
  Clock3,
  Download,
  FileText,
  GitBranch,
  History,
  Loader2,
  Plus,
  ShieldCheck,
  Sparkles,
  Trash2,
  UploadCloud,
  UserCheck,
  Wrench
} from 'lucide-react';
import { ThemeMode, ThemeToggle } from './ThemeToggle';
import { isAuthRequiredError, processService } from '../services/processService';
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

const initialSteps: AgentStep[] = [
  {
    id: 'step_customer_context',
    title: 'Investigate customer context',
    description: 'Review complaint details, account history, prior support interactions, and attachments.',
    reasoning: 'The planner needs enough context before it can decide whether refund, replacement, escalation, or more information is appropriate.',
    status: 'ready',
    owner: 'Research Agent',
    priority: 'high',
    dependencies: ''
  },
  {
    id: 'step_ask_customer',
    title: 'Ask customer for clarification',
    description: 'Request missing details when the root cause cannot be determined from available records.',
    reasoning: 'Human-in-the-loop input keeps the agent from guessing when evidence is incomplete.',
    status: 'waiting-human',
    owner: 'Support Specialist',
    priority: 'medium',
    dependencies: 'step_customer_context'
  },
  {
    id: 'step_resolution_plan',
    title: 'Generate resolution proposal',
    description: 'Produce a recommended resolution with evidence, policy references, and customer-facing wording.',
    reasoning: 'A decision step creates an auditable recommendation before execution.',
    status: 'backlog',
    owner: 'Decision Agent',
    priority: 'high',
    dependencies: 'step_customer_context'
  },
  {
    id: 'step_manager_approval',
    title: 'Request manager approval',
    description: 'Ask a manager to approve resolutions involving refunds above the configured threshold.',
    reasoning: 'The policy constraint requires human approval before high-value refunds can be executed.',
    status: 'backlog',
    owner: 'Supervisor Agent',
    priority: 'high',
    dependencies: 'step_resolution_plan'
  }
];

const defaultTools = ['CRM', 'Email', 'Knowledge Base', 'ERP', 'BPMN Process: Approve Refund'];
const defaultAgents = ['Planner Agent', 'Research Agent', 'Decision Agent', 'Execution Agent', 'Supervisor Agent'];

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

export const AgentBoardModeler: React.FC<AgentBoardModelerProps> = ({
  currentUser,
  onBack,
  onLogout,
  theme,
  onToggleTheme
}) => {
  const [processName, setProcessName] = useState('Customer Support Resolution');
  const [goal, setGoal] = useState('Resolve customer complaint and ensure customer satisfaction.');
  const [instructions, setInstructions] = useState('Investigate the issue, determine root cause, and provide an appropriate resolution.');
  const [constraints, setConstraints] = useState('Refunds above $500 require approval\nAlways contact the customer\nFollow company policies');
  const [tools, setTools] = useState(defaultTools.join('\n'));
  const [agents, setAgents] = useState(defaultAgents.join('\n'));
  const [memoryPolicy, setMemoryPolicy] = useState('Persist conversations, decisions, tool outputs, artifacts, and final rationale for audit and replanning.');
  const [providerId, setProviderId] = useState('openai');
  const [modelName, setModelName] = useState('gpt-4o-mini');
  const [credentialRef, setCredentialRef] = useState('$OPENAI_API_KEY');
  const [systemPrompt, setSystemPrompt] = useState('You are an Easy BPM orchestration agent. Return concise, auditable decisions as JSON when possible.');
  const [promptTemplate, setPromptTemplate] = useState('Goal: {{goal}}\nInstructions: {{instructions}}\nConstraints: {{constraints}}\nInputs: {{inputs}}\n\nDecide the next orchestration outcome and explain the reason.');
  const [allowDynamicTasks, setAllowDynamicTasks] = useState(true);
  const [timeoutDays, setTimeoutDays] = useState(7);
  const [steps, setSteps] = useState<AgentStep[]>(initialSteps);
  const [isDeploying, setIsDeploying] = useState(false);

  const boardCounts = useMemo(() => {
    return columns.reduce<Record<AgentStepStatus, number>>((acc, column) => {
      acc[column.id] = steps.filter(step => step.status === column.id).length;
      return acc;
    }, {} as Record<AgentStepStatus, number>);
  }, [steps]);

  const updateStep = (id: string, updates: Partial<AgentStep>) => {
    setSteps(current => current.map(step => step.id === id ? { ...step, ...updates } : step));
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
        createdFrom: 'easy-bpm-modeler-agent-board',
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
              <p className="mt-2 text-xs leading-relaxed text-slate-400">Goal-oriented execution where agents create, reorder, wait, delegate, and explain work dynamically.</p>
            </div>
            <div className="space-y-2">
              <p className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Board Status</p>
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
                  onChange={event => setAllowDynamicTasks(event.target.checked)}
                  className="h-4 w-4 rounded border-slate-500 text-blue-600"
                />
              </label>
              <label className="block text-[10px] font-bold uppercase tracking-widest text-slate-500">Timeout days</label>
              <input
                type="number"
                min={1}
                value={timeoutDays}
                onChange={event => setTimeoutDays(Number(event.target.value) || 1)}
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
                  onChange={event => setProcessName(event.target.value)}
                  className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm font-semibold outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
                <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Goal</label>
                <textarea
                  value={goal}
                  onChange={event => setGoal(event.target.value)}
                  className="h-24 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>
              <div className="space-y-2">
                <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Instructions</label>
                <textarea
                  value={instructions}
                  onChange={event => setInstructions(event.target.value)}
                  className="h-36 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>
              <div className="space-y-2">
                <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Constraints</label>
                <textarea
                  value={constraints}
                  onChange={event => setConstraints(event.target.value)}
                  className="h-36 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>
            </div>
          </section>

          <section className="grid gap-4 border-b border-slate-200 bg-slate-50 px-6 py-5 lg:grid-cols-3">
            <div className="space-y-2">
              <label className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-widest text-slate-500"><Wrench className="h-3.5 w-3.5" />Available Tools</label>
              <textarea value={tools} onChange={event => setTools(event.target.value)} className="h-28 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
            </div>
            <div className="space-y-2">
              <label className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-widest text-slate-500"><Brain className="h-3.5 w-3.5" />Agents</label>
              <textarea value={agents} onChange={event => setAgents(event.target.value)} className="h-28 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
            </div>
            <div className="space-y-2">
              <label className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-widest text-slate-500"><History className="h-3.5 w-3.5" />Memory and Audit</label>
              <textarea value={memoryPolicy} onChange={event => setMemoryPolicy(event.target.value)} className="h-28 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
            </div>
          </section>

          <section className="grid gap-4 border-b border-slate-200 bg-white px-6 py-5 lg:grid-cols-4">
            <div className="space-y-2">
              <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Provider</label>
              <select value={providerId} onChange={event => setProviderId(event.target.value)} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500">
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
              <input value={modelName} onChange={event => setModelName(event.target.value)} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
            </div>
            <div className="space-y-2">
              <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Credential Ref</label>
              <input value={credentialRef} onChange={event => setCredentialRef(event.target.value)} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm font-mono outline-none focus:border-blue-500" placeholder="$OPENAI_API_KEY" />
            </div>
            <div className="space-y-2">
              <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">System Prompt</label>
              <textarea value={systemPrompt} onChange={event => setSystemPrompt(event.target.value)} className="h-20 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500" />
            </div>
            <div className="space-y-2 lg:col-span-4">
              <label className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Planner Prompt Template</label>
              <textarea value={promptTemplate} onChange={event => setPromptTemplate(event.target.value)} className="h-24 w-full resize-none rounded-md border border-slate-300 px-3 py-2 text-sm font-mono outline-none focus:border-blue-500" />
            </div>
          </section>

          <section className="px-6 py-5">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold text-slate-800">Agent Board</h2>
                <p className="text-xs text-slate-500">Draft dynamic work items, decisions, approvals, and tool executions before runtime support lands.</p>
              </div>
              <button
                type="button"
                onClick={() => setSteps(current => [...current, createStep(current.length + 1)])}
                className="inline-flex items-center gap-2 rounded-md border border-slate-300 bg-white px-3 py-2 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-50"
              >
                <Plus className="h-4 w-4" />
                Step
              </button>
            </div>
            <div className="grid min-w-[1180px] grid-cols-7 gap-3">
              {columns.map(column => (
                <div key={column.id} className="rounded-md border border-slate-200 bg-white">
                  <div className="flex items-center justify-between border-b border-slate-200 px-3 py-2">
                    <span className="flex items-center gap-2 text-xs font-semibold text-slate-700">{column.icon}{column.label}</span>
                    <span className="text-xs text-slate-400">{boardCounts[column.id]}</span>
                  </div>
                  <div className="min-h-64 space-y-3 p-3">
                    {steps.filter(step => step.status === column.id).map(step => (
                      <article key={step.id} className="space-y-2 rounded-md border border-slate-200 bg-slate-50 p-3 shadow-sm">
                        <input
                          value={step.title}
                          onChange={event => updateStep(step.id, { title: event.target.value })}
                          className="w-full bg-transparent text-xs font-semibold text-slate-800 outline-none"
                        />
                        <textarea
                          value={step.description}
                          onChange={event => updateStep(step.id, { description: event.target.value })}
                          className="h-16 w-full resize-none rounded border border-slate-200 bg-white px-2 py-1.5 text-xs text-slate-600 outline-none focus:border-blue-400"
                        />
                        <textarea
                          value={step.reasoning}
                          onChange={event => updateStep(step.id, { reasoning: event.target.value })}
                          className="h-14 w-full resize-none rounded border border-slate-200 bg-white px-2 py-1.5 text-xs text-slate-600 outline-none focus:border-blue-400"
                        />
                        <div className="grid grid-cols-1 gap-2">
                          <select value={step.status} onChange={event => updateStep(step.id, { status: event.target.value as AgentStepStatus })} className="rounded border border-slate-200 bg-white px-2 py-1.5 text-xs">
                            {columns.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}
                          </select>
                          <input value={step.owner} onChange={event => updateStep(step.id, { owner: event.target.value })} className="rounded border border-slate-200 bg-white px-2 py-1.5 text-xs" />
                          <select value={step.priority} onChange={event => updateStep(step.id, { priority: event.target.value as AgentStep['priority'] })} className="rounded border border-slate-200 bg-white px-2 py-1.5 text-xs">
                            <option value="low">Low priority</option>
                            <option value="medium">Medium priority</option>
                            <option value="high">High priority</option>
                          </select>
                          <input value={step.dependencies} onChange={event => updateStep(step.id, { dependencies: event.target.value })} placeholder="Dependencies" className="rounded border border-slate-200 bg-white px-2 py-1.5 text-xs" />
                        </div>
                        <div className="flex items-center justify-between pt-1">
                          <span className="flex items-center gap-1 text-[10px] font-mono text-slate-400"><GitBranch className="h-3 w-3" />{step.id}</span>
                          <button type="button" onClick={() => setSteps(current => current.filter(item => item.id !== step.id))} className="text-slate-400 transition-colors hover:text-red-500" title="Delete step" aria-label="Delete step">
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        </div>
                      </article>
                    ))}
                  </div>
                </div>
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
