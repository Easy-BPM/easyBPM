import React from 'react';
import {
  ArrowRight,
  Bot,
  Database,
  ExternalLink,
  FileText,
  HelpCircle,
  Home,
  Layout,
  Lightbulb,
  Loader2,
  LogOut,
  MoreVertical,
  RefreshCw,
  Search,
  User,
  Workflow
} from 'lucide-react';
import { ThemeMode, ThemeToggle } from './ThemeToggle';

export type WorkspaceResourceKind = 'process' | 'form' | 'agent';

export interface WorkspaceResource {
  id: string;
  kind: WorkspaceResourceKind;
  name: string;
  key: string;
  version?: number;
  updatedAt?: string;
  description?: string | null;
  payload?: unknown;
}

export interface ProcessTemplateDefinition {
  processId: string;
  processName: string;
  variables: Array<{
    name: string;
    type: string;
    initialValue: string;
  }>;
  nodes: Array<{
    id: string;
    type: string;
    name: string;
    position: { x: number; y: number };
    config?: Record<string, unknown>;
  }>;
  flows: Array<{
    from: string;
    to: string;
    condition?: string | null;
  }>;
}

interface WelcomeScreenProps {
  onCreateProcess: () => void;
  onCreateForm: () => void;
  onLoadProcessTemplate?: (definition: ProcessTemplateDefinition) => void;
  onCreateAgentProcess?: () => void;
  isAgenticOrchestrationEnabled?: boolean;
  workspaceResources?: WorkspaceResource[];
  isLoadingResources?: boolean;
  resourceLoadError?: string | null;
  onRefreshResources?: () => void;
  onOpenResource?: (resource: WorkspaceResource) => void;
  currentUser?: string | null;
  onLogout?: () => void;
  theme: ThemeMode;
  onToggleTheme: () => void;
}

const tabs: { id: WorkspaceResourceKind; label: string; icon: React.ReactNode }[] = [
  { id: 'process', label: 'Processes', icon: <Workflow className="h-4 w-4" /> },
  { id: 'form', label: 'Forms', icon: <FileText className="h-4 w-4" /> },
  { id: 'agent', label: 'Agents', icon: <Bot className="h-4 w-4" /> }
];

const kindLabel: Record<WorkspaceResourceKind, string> = {
  process: 'Process',
  form: 'Form',
  agent: 'Agent'
};

const kindStyles: Record<WorkspaceResourceKind, { icon: React.ReactNode; tone: string; pill: string }> = {
  process: {
    icon: <Workflow className="h-4 w-4" />,
    tone: 'bg-blue-500/15 text-blue-400',
    pill: 'bg-blue-500/15 text-blue-400'
  },
  form: {
    icon: <FileText className="h-4 w-4" />,
    tone: 'bg-emerald-500/15 text-emerald-400',
    pill: 'bg-emerald-500/15 text-emerald-400'
  },
  agent: {
    icon: <Bot className="h-4 w-4" />,
    tone: 'bg-violet-500/15 text-violet-400',
    pill: 'bg-violet-500/15 text-violet-400'
  }
};

const templates: Array<{
  title: string;
  description: string;
  icon: React.ReactNode;
  definition: ProcessTemplateDefinition;
}> = [
  {
    title: 'Approval Process',
    description: 'Simple approval flow',
    icon: <Workflow className="h-4 w-4" />,
    definition: {
      processId: 'approval_process_template',
      processName: 'Approval Process',
      variables: [
        { name: 'requestTitle', type: 'string', initialValue: '' },
        { name: 'approved', type: 'boolean', initialValue: 'false' }
      ],
      nodes: [
        { id: 'start_event', type: 'StartEvent', name: 'Start', position: { x: 80, y: 180 } },
        { id: 'submit_request', type: 'HumanTask', name: 'Submit Request', position: { x: 220, y: 160 }, config: { candidateGroups: 'REQUESTERS' } },
        { id: 'review_request', type: 'HumanTask', name: 'Review Request', position: { x: 420, y: 160 }, config: { candidateGroups: 'MANAGERS' } },
        { id: 'approval_decision', type: 'ExclusiveGateway', name: 'Approved?', position: { x: 640, y: 170 } },
        { id: 'approved_end', type: 'EndEvent', name: 'Approved', position: { x: 820, y: 100 } },
        { id: 'rejected_end', type: 'EndEvent', name: 'Rejected', position: { x: 820, y: 250 } }
      ],
      flows: [
        { from: 'start_event', to: 'submit_request' },
        { from: 'submit_request', to: 'review_request' },
        { from: 'review_request', to: 'approval_decision' },
        { from: 'approval_decision', to: 'approved_end', condition: '${approved} == true' },
        { from: 'approval_decision', to: 'rejected_end', condition: '${approved} == false' }
      ]
    }
  },
  {
    title: 'Procurement Process',
    description: 'Request, review and approve',
    icon: <User className="h-4 w-4" />,
    definition: {
      processId: 'procurement_process_template',
      processName: 'Procurement Process',
      variables: [
        { name: 'vendor', type: 'string', initialValue: '' },
        { name: 'requestAmount', type: 'number', initialValue: '0' },
        { name: 'approved', type: 'boolean', initialValue: 'false' }
      ],
      nodes: [
        { id: 'start_event', type: 'StartEvent', name: 'Start', position: { x: 80, y: 180 } },
        { id: 'create_purchase_request', type: 'HumanTask', name: 'Create Purchase Request', position: { x: 220, y: 160 }, config: { candidateGroups: 'REQUESTERS' } },
        { id: 'manager_approval', type: 'HumanTask', name: 'Manager Approval', position: { x: 460, y: 160 }, config: { candidateGroups: 'MANAGERS' } },
        { id: 'approval_decision', type: 'ExclusiveGateway', name: 'Approved?', position: { x: 690, y: 170 } },
        { id: 'create_purchase_order', type: 'ServiceTask', name: 'Create Purchase Order', position: { x: 860, y: 100 } },
        { id: 'approved_end', type: 'EndEvent', name: 'Completed', position: { x: 1080, y: 110 } },
        { id: 'rejected_end', type: 'EndEvent', name: 'Rejected', position: { x: 880, y: 260 } }
      ],
      flows: [
        { from: 'start_event', to: 'create_purchase_request' },
        { from: 'create_purchase_request', to: 'manager_approval' },
        { from: 'manager_approval', to: 'approval_decision' },
        { from: 'approval_decision', to: 'create_purchase_order', condition: '${approved} == true' },
        { from: 'create_purchase_order', to: 'approved_end' },
        { from: 'approval_decision', to: 'rejected_end', condition: '${approved} == false' }
      ]
    }
  },
  {
    title: 'Onboarding Process',
    description: 'New employee onboarding',
    icon: <User className="h-4 w-4" />,
    definition: {
      processId: 'onboarding_process_template',
      processName: 'Onboarding Process',
      variables: [
        { name: 'employeeName', type: 'string', initialValue: '' },
        { name: 'equipmentReady', type: 'boolean', initialValue: 'false' }
      ],
      nodes: [
        { id: 'start_event', type: 'StartEvent', name: 'Start', position: { x: 80, y: 180 } },
        { id: 'collect_employee_details', type: 'HumanTask', name: 'Collect Employee Details', position: { x: 220, y: 160 }, config: { candidateGroups: 'HR' } },
        { id: 'setup_accounts', type: 'ServiceTask', name: 'Set Up Accounts', position: { x: 470, y: 160 } },
        { id: 'prepare_equipment', type: 'HumanTask', name: 'Prepare Equipment', position: { x: 690, y: 160 }, config: { candidateGroups: 'IT' } },
        { id: 'orientation_session', type: 'HumanTask', name: 'Orientation Session', position: { x: 920, y: 160 }, config: { candidateGroups: 'HR' } },
        { id: 'end_event', type: 'EndEvent', name: 'Completed', position: { x: 1160, y: 170 } }
      ],
      flows: [
        { from: 'start_event', to: 'collect_employee_details' },
        { from: 'collect_employee_details', to: 'setup_accounts' },
        { from: 'setup_accounts', to: 'prepare_equipment' },
        { from: 'prepare_equipment', to: 'orientation_session' },
        { from: 'orientation_session', to: 'end_event' }
      ]
    }
  },
  {
    title: 'Leave Request Process',
    description: 'Employee leave management',
    icon: <FileText className="h-4 w-4" />,
    definition: {
      processId: 'leave_request_process_template',
      processName: 'Leave Request Process',
      variables: [
        { name: 'employeeId', type: 'string', initialValue: '' },
        { name: 'daysRequested', type: 'number', initialValue: '1' },
        { name: 'approved', type: 'boolean', initialValue: 'false' }
      ],
      nodes: [
        { id: 'start_event', type: 'StartEvent', name: 'Start', position: { x: 80, y: 180 } },
        { id: 'submit_leave_request', type: 'HumanTask', name: 'Submit Leave Request', position: { x: 220, y: 160 }, config: { candidateGroups: 'EMPLOYEES' } },
        { id: 'review_leave_request', type: 'HumanTask', name: 'Review Leave Request', position: { x: 470, y: 160 }, config: { candidateGroups: 'MANAGERS' } },
        { id: 'approval_decision', type: 'ExclusiveGateway', name: 'Approved?', position: { x: 700, y: 170 } },
        { id: 'approved_end', type: 'EndEvent', name: 'Approved', position: { x: 900, y: 100 } },
        { id: 'rejected_end', type: 'EndEvent', name: 'Rejected', position: { x: 900, y: 250 } }
      ],
      flows: [
        { from: 'start_event', to: 'submit_leave_request' },
        { from: 'submit_leave_request', to: 'review_leave_request' },
        { from: 'review_leave_request', to: 'approval_decision' },
        { from: 'approval_decision', to: 'approved_end', condition: '${approved} == true' },
        { from: 'approval_decision', to: 'rejected_end', condition: '${approved} == false' }
      ]
    }
  }
];

const formatDate = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return new Intl.DateTimeFormat('en', {
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date);
};

export const WelcomeScreen: React.FC<WelcomeScreenProps> = ({
  onCreateProcess,
  onCreateForm,
  onLoadProcessTemplate,
  onCreateAgentProcess,
  isAgenticOrchestrationEnabled = false,
  workspaceResources = [],
  isLoadingResources = false,
  resourceLoadError,
  onRefreshResources,
  onOpenResource,
  currentUser,
  onLogout,
  theme,
  onToggleTheme
}) => {
  const [showUserMenu, setShowUserMenu] = React.useState(false);
  const [isTemplateBrowserOpen, setIsTemplateBrowserOpen] = React.useState(false);
  const [activeTab, setActiveTab] = React.useState<WorkspaceResourceKind>('process');
  const [query, setQuery] = React.useState('');

  const visibleTabs = tabs.filter(tab => tab.id !== 'agent' || isAgenticOrchestrationEnabled);
  const filteredResources = workspaceResources.filter(resource => {
    const matchesTab = resource.kind === activeTab;
    const searchText = `${resource.name} ${resource.key} ${resource.description || ''}`.toLowerCase();
    return matchesTab && searchText.includes(query.trim().toLowerCase());
  });
  const deployedResources = filteredResources.slice(0, 5);
  const loadTemplate = (definition: ProcessTemplateDefinition) => {
    if (onLoadProcessTemplate) {
      onLoadProcessTemplate(definition);
    } else {
      onCreateProcess();
    }
    setIsTemplateBrowserOpen(false);
  };
  const sidebarItems = [
    { label: 'Welcome', icon: <Home className="h-4 w-4" />, active: true, onClick: undefined },
    { label: 'Modeler', icon: <Workflow className="h-4 w-4" />, active: false, onClick: onCreateProcess },
    { label: 'Forms', icon: <FileText className="h-4 w-4" />, active: false, onClick: onCreateForm },
    { label: 'Agents', icon: <Bot className="h-4 w-4" />, active: false, onClick: onCreateAgentProcess }
  ].filter(item => item.label !== 'Agents' || isAgenticOrchestrationEnabled);

  return (
    <div className="welcome-modeler min-h-screen bg-[var(--modeler-bg)] text-[var(--modeler-text)]" data-theme={theme}>
      <div className="flex min-h-screen">
        <aside className="hidden w-72 shrink-0 border-r border-[var(--modeler-border)] bg-[var(--modeler-surface)]/80 px-5 py-6 lg:flex lg:flex-col">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-600 text-white shadow-lg shadow-blue-600/25">
              <Layout className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-sm font-semibold text-[var(--modeler-text)]">Easy BPM Modeler</h1>
              <p className="text-xs text-[var(--modeler-text-muted)]">Modeling made simple.</p>
            </div>
          </div>

          <nav className="mt-10 space-y-2">
            {sidebarItems.map(item => (
              <button
                key={item.label}
                type="button"
                onClick={item.onClick}
                className={`flex w-full items-center gap-3 rounded-md px-4 py-3 text-left text-sm font-semibold transition-colors ${
                  item.active
                    ? 'bg-blue-600/20 text-blue-300'
                    : 'text-[var(--modeler-text-soft)] hover:bg-[var(--modeler-surface-muted)] hover:text-[var(--modeler-text)]'
                }`}
              >
                {item.icon}
                {item.label}
              </button>
            ))}
          </nav>

          <div className="mt-auto rounded-lg border border-[var(--modeler-border)] bg-[var(--modeler-surface-muted)] p-4">
            <div className="mb-3 flex items-center gap-2">
              <Lightbulb className="h-4 w-4 text-amber-400" />
              <p className="text-sm font-semibold text-[var(--modeler-text)]">Tip</p>
            </div>
            <p className="text-sm leading-5 text-[var(--modeler-text-muted)]">
              Start by creating a process or explore templates to speed up your work.
            </p>
            <button
              type="button"
              onClick={() => setIsTemplateBrowserOpen(true)}
              className="mt-4 inline-flex items-center gap-2 rounded-md border border-[var(--modeler-border)] bg-[var(--modeler-surface)] px-3 py-2 text-xs font-semibold text-[var(--modeler-text-soft)] transition-colors hover:bg-[var(--modeler-surface-muted)] hover:text-[var(--modeler-text)]"
            >
              Explore templates
              <ArrowRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </aside>

        <div className="min-w-0 flex-1">
          <header className="flex h-16 items-center justify-between border-b border-[var(--modeler-border)] bg-[var(--modeler-surface)]/70 px-6 backdrop-blur-sm">
            <div className="flex items-center gap-3 lg:hidden">
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-600 text-white">
                <Layout className="h-5 w-5" />
              </div>
              <div>
                <h1 className="text-sm font-semibold">Easy BPM Modeler</h1>
                <p className="text-xs text-[var(--modeler-text-muted)]">Workspace</p>
              </div>
            </div>
            <div className="hidden text-sm text-[var(--modeler-text-muted)] lg:block">
              Design, orchestrate and automate with ease
            </div>

            <div className="flex items-center gap-3">
              <button type="button" className="modeler-ghost-button flex h-9 w-9 items-center justify-center rounded-full transition-colors" title="Help" aria-label="Help">
                <HelpCircle className="h-4 w-4" />
              </button>
              <ThemeToggle theme={theme} onToggle={onToggleTheme} />
              {currentUser && (
                <div className="relative">
                  <button
                    onClick={() => setShowUserMenu(!showUserMenu)}
                    className="flex h-9 min-w-9 items-center justify-center rounded-full bg-blue-600/20 px-3 text-sm font-semibold text-blue-300 transition-colors hover:bg-blue-600/30"
                    aria-label="User menu"
                  >
                    {currentUser.slice(0, 1).toUpperCase()}
                  </button>
                  {showUserMenu && (
                    <div className="absolute right-0 top-full z-50 mt-2 w-48 rounded-lg border border-[var(--modeler-border)] bg-[var(--modeler-surface)] shadow-xl">
                      <div className="border-b border-[var(--modeler-border)] px-4 py-3">
                        <p className="text-xs text-[var(--modeler-text-muted)]">Logged in as</p>
                        <p className="truncate text-sm font-semibold text-[var(--modeler-text)]">{currentUser}</p>
                      </div>
                      {onLogout && (
                        <button
                          onClick={() => {
                            onLogout();
                            setShowUserMenu(false);
                          }}
                          className="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-[var(--modeler-text-soft)] transition-colors hover:bg-[var(--modeler-surface-muted)] hover:text-[var(--modeler-text)]"
                        >
                          <LogOut className="h-4 w-4" />
                          Logout
                        </button>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          </header>

          <main className="mx-auto w-full max-w-7xl px-6 py-8">
            <section className="grid gap-6 xl:grid-cols-[1fr_560px]">
              <div className="pt-4">
                <h2 className="text-3xl font-semibold tracking-normal text-[var(--modeler-text)]">
                  Welcome to Easy BPM Modeler
                </h2>
                <p className="mt-3 max-w-2xl text-base leading-7 text-[var(--modeler-text-muted)]">
                  Everything you need to design, automate and orchestrate powerful business processes.
                </p>
              </div>
              <div className="hidden min-h-36 items-center justify-center overflow-hidden rounded-lg border border-[var(--modeler-border)] bg-[var(--modeler-surface)]/60 xl:flex">
                <div className="relative h-28 w-[460px]">
                  <div className="absolute inset-0 bg-[radial-gradient(circle,var(--modeler-canvas-grid)_1px,transparent_1px)] [background-size:24px_24px]" />
                  <div className="absolute left-12 top-10 h-8 w-8 rounded-full border-2 border-blue-300" />
                  <div className="absolute left-28 top-14 h-px w-20 bg-blue-300/60" />
                  <div className="absolute left-48 top-8 h-12 w-28 rounded-md border border-blue-400 bg-blue-500/15" />
                  <div className="absolute left-80 top-9 h-10 w-10 rotate-45 border border-[var(--modeler-text-muted)]" />
                  <div className="absolute right-16 top-2 h-12 w-28 rounded-md border border-emerald-400 bg-emerald-500/15" />
                  <div className="absolute right-16 bottom-2 h-12 w-28 rounded-md border border-violet-400 bg-violet-500/15" />
                  <div className="absolute right-1 top-10 h-8 w-8 rounded-full border-2 border-red-500" />
                </div>
              </div>
            </section>

            <section className="mt-8 grid gap-5 lg:grid-cols-3">
              <article className="group overflow-hidden rounded-lg border border-blue-500/25 bg-blue-600/10 p-6 transition-colors hover:bg-blue-600/15">
                <div className="mb-5 flex h-12 w-12 items-center justify-center rounded-full bg-blue-600 text-white shadow-lg shadow-blue-600/20">
                  <Workflow className="h-6 w-6" />
                </div>
                <h3 className="text-lg font-semibold text-[var(--modeler-text)]">Modeler</h3>
                <p className="mt-2 min-h-16 text-sm leading-5 text-[var(--modeler-text-muted)]">
                  Create and edit BPMN diagrams visually. Design processes with gateways, events and flows.
                </p>
                <div className="mt-5 flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={onCreateProcess}
                    className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-blue-500"
                  >
                    Create Process
                    <ArrowRight className="h-4 w-4" />
                  </button>
                </div>
              </article>

              <article className="group overflow-hidden rounded-lg border border-emerald-500/25 bg-emerald-600/10 p-6 transition-colors hover:bg-emerald-600/15">
                <div className="mb-5 flex h-12 w-12 items-center justify-center rounded-full bg-emerald-600 text-white shadow-lg shadow-emerald-600/20">
                  <FileText className="h-6 w-6" />
                </div>
                <h3 className="text-lg font-semibold text-[var(--modeler-text)]">Forms</h3>
                <p className="mt-2 min-h-16 text-sm leading-5 text-[var(--modeler-text-muted)]">
                  Build forms with drag-and-drop. Add fields, validations and logic to collect the right data.
                </p>
                <div className="mt-5 flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={onCreateForm}
                    className="inline-flex items-center gap-2 rounded-md bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-500"
                  >
                    Create Form
                    <ArrowRight className="h-4 w-4" />
                  </button>
                </div>
              </article>

              {isAgenticOrchestrationEnabled && onCreateAgentProcess && (
                <article className="group overflow-hidden rounded-lg border border-violet-500/25 bg-violet-600/10 p-6 transition-colors hover:bg-violet-600/15">
                  <div className="mb-5 flex h-12 w-12 items-center justify-center rounded-full bg-violet-600 text-white shadow-lg shadow-violet-600/20">
                    <Bot className="h-6 w-6" />
                  </div>
                  <h3 className="text-lg font-semibold text-[var(--modeler-text)]">Agents</h3>
                  <p className="mt-2 min-h-16 text-sm leading-5 text-[var(--modeler-text-muted)]">
                    Configure AI agents to make decisions, interact with systems and automate tasks.
                  </p>
                  <div className="mt-5 flex flex-wrap items-center gap-2">
                    <button
                      type="button"
                      onClick={onCreateAgentProcess}
                      className="inline-flex items-center gap-2 rounded-md bg-violet-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-violet-500"
                    >
                      Create Agent
                      <ArrowRight className="h-4 w-4" />
                    </button>
                  </div>
                </article>
              )}
            </section>

            <section className="mt-6 grid gap-6 xl:grid-cols-[1.2fr_0.9fr]">
              <div className="rounded-lg border border-[var(--modeler-border)] bg-[var(--modeler-surface)] shadow-sm">
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--modeler-border)] px-5 py-4">
                  <div>
                    <h3 className="text-base font-semibold text-[var(--modeler-text)]">Deployed resources</h3>
                    <p className="text-xs text-[var(--modeler-text-muted)]">Open published versions from the database.</p>
                  </div>
                  <button
                    onClick={onRefreshResources}
                    disabled={isLoadingResources}
                    className="inline-flex items-center gap-2 rounded-md border border-[var(--modeler-border)] bg-[var(--modeler-input-bg)] px-3 py-2 text-xs font-semibold text-[var(--modeler-text-soft)] transition-colors hover:bg-[var(--modeler-surface-muted)] disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {isLoadingResources ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
                    Refresh
                  </button>
                </div>

                <div className="border-b border-[var(--modeler-border)] px-5 py-3">
                  <div className="flex flex-wrap items-center gap-3">
                    <div className="inline-flex rounded-md border border-[var(--modeler-border)] bg-[var(--modeler-surface-muted)] p-1">
                      {visibleTabs.map(tab => (
                        <button
                          key={tab.id}
                          onClick={() => setActiveTab(tab.id)}
                          className={`inline-flex items-center gap-2 rounded px-3 py-1.5 text-xs font-semibold transition-colors ${activeTab === tab.id ? 'bg-[var(--modeler-surface)] text-blue-500 shadow-sm' : 'text-[var(--modeler-text-muted)] hover:text-[var(--modeler-text)]'}`}
                        >
                          {tab.label}
                        </button>
                      ))}
                    </div>
                    <div className="relative min-w-64 flex-1">
                      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--modeler-placeholder)]" />
                      <input
                        value={query}
                        onChange={event => setQuery(event.target.value)}
                        className="w-full rounded-md border border-[var(--modeler-input-border)] bg-[var(--modeler-input-bg)] px-9 py-2 text-sm text-[var(--modeler-input-text)] outline-none transition-colors placeholder:text-[var(--modeler-placeholder)] focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
                        placeholder="Search by name, key, or description"
                      />
                    </div>
                  </div>
                </div>

                {resourceLoadError && (
                  <div className="border-b border-amber-500/20 bg-amber-500/10 px-5 py-3 text-sm text-amber-500">
                    {resourceLoadError}
                  </div>
                )}

                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm">
                    <thead className="bg-[var(--modeler-surface-muted)] text-[10px] uppercase text-[var(--modeler-text-muted)]">
                      <tr>
                        <th className="px-5 py-3 font-semibold">Name</th>
                        <th className="px-4 py-3 font-semibold">Type</th>
                        <th className="px-4 py-3 font-semibold">Updated</th>
                        <th className="px-4 py-3 text-right font-semibold" />
                      </tr>
                    </thead>
                    <tbody>
                      {deployedResources.map(resource => (
                        <tr key={`${resource.kind}-${resource.id}`} className="border-t border-[var(--modeler-border)] transition-colors hover:bg-[var(--modeler-surface-muted)]">
                          <td className="max-w-xs px-5 py-3">
                            <div className="flex items-center gap-3">
                              <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-md ${kindStyles[resource.kind].tone}`}>
                                {kindStyles[resource.kind].icon}
                              </span>
                              <div className="min-w-0">
                                <p className="truncate font-semibold text-[var(--modeler-text)]">{resource.name}</p>
                                <p className="truncate font-mono text-xs text-[var(--modeler-text-muted)]">v{resource.version || 1} · {resource.key}</p>
                              </div>
                            </div>
                          </td>
                          <td className="px-4 py-3">
                            <span className={`rounded px-2 py-1 text-xs font-medium ${kindStyles[resource.kind].pill}`}>{kindLabel[resource.kind]}</span>
                          </td>
                          <td className="px-4 py-3 text-[var(--modeler-text-muted)]">{formatDate(resource.updatedAt)}</td>
                          <td className="px-4 py-3 text-right">
                            <button
                              onClick={() => onOpenResource?.(resource)}
                              className="inline-flex h-8 w-8 items-center justify-center rounded-md text-[var(--modeler-text-muted)] transition-colors hover:bg-[var(--modeler-surface-muted)] hover:text-[var(--modeler-text)]"
                              title="Open resource"
                              aria-label="Open resource"
                            >
                              <MoreVertical className="h-4 w-4" />
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>

                  {!isLoadingResources && deployedResources.length === 0 && (
                    <div className="flex flex-col items-center justify-center px-4 py-14 text-center">
                      <Database className="mb-3 h-8 w-8 text-[var(--modeler-text-muted)]" />
                      <h4 className="text-sm font-semibold text-[var(--modeler-text)]">No resources found</h4>
                      <p className="mt-1 max-w-sm text-sm text-[var(--modeler-text-muted)]">Create a new resource or adjust the search to find published versions.</p>
                    </div>
                  )}

                  {isLoadingResources && (
                    <div className="flex items-center justify-center gap-2 px-4 py-14 text-sm text-[var(--modeler-text-muted)]">
                      <Loader2 className="h-4 w-4 animate-spin" />
                      Loading resources...
                    </div>
                  )}
                </div>
              </div>

              <div className="rounded-lg border border-[var(--modeler-border)] bg-[var(--modeler-surface)] p-5 shadow-sm">
                <div className="mb-5 flex items-center justify-between gap-3">
                  <div>
                    <h3 className="text-base font-semibold text-[var(--modeler-text)]">Start from template</h3>
                    <p className="text-xs text-[var(--modeler-text-muted)]">Quick process starters.</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setIsTemplateBrowserOpen(true)}
                    className="inline-flex items-center gap-2 text-xs font-semibold text-blue-400 transition-colors hover:text-blue-300"
                  >
                    View all
                    <ArrowRight className="h-3.5 w-3.5" />
                  </button>
                </div>
                <div className="space-y-2">
                  {templates.map(template => (
                    <button
                      key={template.title}
                      type="button"
                      onClick={() => loadTemplate(template.definition)}
                      className="flex w-full items-center gap-3 rounded-md border border-[var(--modeler-border)] bg-[var(--modeler-surface-muted)] px-3 py-3 text-left transition-colors hover:bg-blue-500/10"
                    >
                      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-blue-500/15 text-blue-400">
                        {template.icon}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-semibold text-[var(--modeler-text)]">{template.title}</span>
                        <span className="block truncate text-xs text-[var(--modeler-text-muted)]">{template.description}</span>
                      </span>
                      <ArrowRight className="h-4 w-4 text-[var(--modeler-text-muted)]" />
                    </button>
                  ))}
                </div>
              </div>
            </section>

            <section className="mt-6 flex flex-wrap items-center justify-between gap-4 rounded-lg border border-[var(--modeler-border)] bg-[var(--modeler-surface)] p-5">
              <div className="flex items-center gap-4">
                <div className="flex h-12 w-12 items-center justify-center rounded-md bg-blue-500/15 text-blue-400">
                  <Workflow className="h-6 w-6" />
                </div>
                <div>
                  <h3 className="font-semibold text-[var(--modeler-text)]">Model. Automate. Orchestrate.</h3>
                  <p className="text-sm text-[var(--modeler-text-muted)]">Build better processes, faster.</p>
                </div>
              </div>
              <button type="button" className="inline-flex items-center gap-2 rounded-md border border-[var(--modeler-border)] bg-[var(--modeler-surface-muted)] px-4 py-2 text-sm font-semibold text-[var(--modeler-text-soft)] transition-colors hover:bg-[var(--modeler-border)] hover:text-[var(--modeler-text)]">
                Documentation
                <ExternalLink className="h-4 w-4" />
              </button>
            </section>
          </main>
        </div>
      </div>
      {isTemplateBrowserOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4 py-6 backdrop-blur-sm">
          <div className="max-h-full w-full max-w-3xl overflow-hidden rounded-lg border border-[var(--modeler-border)] bg-[var(--modeler-surface)] shadow-2xl">
            <div className="flex items-start justify-between gap-4 border-b border-[var(--modeler-border)] px-5 py-4">
              <div>
                <h2 className="text-base font-semibold text-[var(--modeler-text)]">Process templates</h2>
                <p className="mt-1 text-sm text-[var(--modeler-text-muted)]">Choose a starter process and customize it in the modeler.</p>
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
              {templates.map(template => (
                <button
                  key={template.title}
                  type="button"
                  onClick={() => loadTemplate(template.definition)}
                  className="group flex min-h-36 flex-col items-start justify-between rounded-lg border border-[var(--modeler-border)] bg-[var(--modeler-surface-muted)] p-4 text-left transition-colors hover:border-blue-400 hover:bg-blue-500/10"
                >
                  <span className="flex items-center gap-3">
                    <span className="flex h-10 w-10 items-center justify-center rounded-md bg-blue-500/15 text-blue-400">
                      {template.icon}
                    </span>
                    <span>
                      <span className="block text-sm font-semibold text-[var(--modeler-text)]">{template.title}</span>
                      <span className="block text-xs text-[var(--modeler-text-muted)]">{template.description}</span>
                    </span>
                  </span>
                  <span className="mt-5 inline-flex items-center gap-2 text-xs font-semibold text-blue-400 transition-colors group-hover:text-blue-300">
                    Load template
                    <ArrowRight className="h-3.5 w-3.5" />
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
