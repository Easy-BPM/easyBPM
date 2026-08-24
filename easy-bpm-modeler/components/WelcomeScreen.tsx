import React from 'react';
import {
  Bot,
  Database,
  FileText,
  Import,
  Layout,
  Loader2,
  LogOut,
  Plus,
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

interface WelcomeScreenProps {
  onCreateProcess: () => void;
  onCreateForm: () => void;
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

const formatDate = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date);
};

export const WelcomeScreen: React.FC<WelcomeScreenProps> = ({
  onCreateProcess,
  onCreateForm,
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
  const [activeTab, setActiveTab] = React.useState<WorkspaceResourceKind>('process');
  const [query, setQuery] = React.useState('');

  const visibleTabs = tabs.filter(tab => tab.id !== 'agent' || isAgenticOrchestrationEnabled);
  const filteredResources = workspaceResources.filter(resource => {
    const matchesTab = resource.kind === activeTab;
    const searchText = `${resource.name} ${resource.key} ${resource.description || ''}`.toLowerCase();
    return matchesTab && searchText.includes(query.trim().toLowerCase());
  });

  return (
    <div className="welcome-modeler min-h-screen bg-[var(--modeler-bg)] text-[var(--modeler-text)]" data-theme={theme}>
      <div className="border-b border-[var(--modeler-border)] bg-[var(--modeler-surface)]/90 px-6 py-3 backdrop-blur-sm">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-600 text-white shadow-sm">
              <Layout className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-sm font-semibold text-[var(--modeler-text)]">Easy BPM Modeler</h1>
              <p className="text-xs text-[var(--modeler-text-muted)]">Process, form, and agent workspace</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <ThemeToggle theme={theme} onToggle={onToggleTheme} />
            {currentUser && (
              <div className="relative">
                <button
                  onClick={() => setShowUserMenu(!showUserMenu)}
                  className="flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium text-[var(--modeler-text-soft)] transition-colors hover:bg-[var(--modeler-surface-muted)] hover:text-[var(--modeler-text)]"
                  aria-label="User menu"
                >
                  <User className="h-4 w-4" />
                  {currentUser}
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
        </div>
      </div>

      <main className="mx-auto flex w-full max-w-7xl gap-6 px-6 py-8">
        <aside className="w-72 shrink-0 space-y-4">
          <section className="rounded-lg border border-[var(--modeler-border)] bg-[var(--modeler-surface)] p-4 shadow-sm">
            <p className="mb-3 text-xs font-bold uppercase tracking-wider text-[var(--modeler-text-muted)]">Create new</p>
            <div className="space-y-2">
              <button onClick={onCreateProcess} className="flex w-full items-center gap-3 rounded-md border border-blue-200 bg-blue-50 px-3 py-3 text-left transition-colors hover:bg-blue-100">
                <Workflow className="h-5 w-5 text-blue-600" />
                <div>
                  <p className="text-sm font-semibold text-slate-950">New Process</p>
                  <p className="text-xs text-slate-600">Model a BPMN workflow</p>
                </div>
              </button>
              <button onClick={onCreateForm} className="flex w-full items-center gap-3 rounded-md border border-emerald-200 bg-emerald-50 px-3 py-3 text-left transition-colors hover:bg-emerald-100">
                <FileText className="h-5 w-5 text-emerald-600" />
                <div>
                  <p className="text-sm font-semibold text-slate-950">New Form</p>
                  <p className="text-xs text-slate-600">Fields and validation</p>
                </div>
              </button>
              {isAgenticOrchestrationEnabled && onCreateAgentProcess && (
                <button onClick={onCreateAgentProcess} className="flex w-full items-center gap-3 rounded-md border border-cyan-200 bg-cyan-50 px-3 py-3 text-left transition-colors hover:bg-cyan-100">
                  <Bot className="h-5 w-5 text-cyan-600" />
                  <div>
                    <p className="text-sm font-semibold text-slate-950">New Agent</p>
                    <p className="text-xs text-slate-600">Agentic orchestration</p>
                  </div>
                </button>
              )}
            </div>
          </section>

          <section className="rounded-lg border border-[var(--modeler-border)] bg-[var(--modeler-surface)] p-4 shadow-sm">
            <div className="mb-3 flex items-center gap-2">
              <Database className="h-4 w-4 text-[var(--modeler-text-muted)]" />
              <p className="text-xs font-bold uppercase tracking-wider text-[var(--modeler-text-muted)]">Summary</p>
            </div>
            <div className="grid grid-cols-3 gap-2">
              {visibleTabs.map(tab => (
                <div key={tab.id} className="rounded-md bg-[var(--modeler-surface-muted)] p-2 text-center">
                  <p className="text-lg font-semibold text-[var(--modeler-text)]">{workspaceResources.filter(resource => resource.kind === tab.id).length}</p>
                  <p className="text-[10px] font-medium uppercase text-[var(--modeler-text-muted)]">{tab.label}</p>
                </div>
              ))}
            </div>
          </section>
        </aside>

        <section className="min-w-0 flex-1 rounded-lg border border-[var(--modeler-border)] bg-[var(--modeler-surface)] shadow-sm">
          <div className="border-b border-[var(--modeler-border)] p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold text-[var(--modeler-text)]">Database resources</h2>
                <p className="text-sm text-[var(--modeler-text-muted)]">Open the latest published version or start a new resource.</p>
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

            <div className="mt-4 flex flex-wrap items-center gap-3">
              <div className="inline-flex rounded-md border border-[var(--modeler-border)] bg-[var(--modeler-surface-muted)] p-1">
                {visibleTabs.map(tab => (
                  <button
                    key={tab.id}
                    onClick={() => setActiveTab(tab.id)}
                    className={`inline-flex items-center gap-2 rounded px-3 py-1.5 text-xs font-semibold transition-colors ${activeTab === tab.id ? 'bg-[var(--modeler-surface)] text-blue-600 shadow-sm' : 'text-[var(--modeler-text-muted)] hover:text-[var(--modeler-text)]'}`}
                  >
                    {tab.icon}
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
            <div className="border-b border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">
              {resourceLoadError}
            </div>
          )}

          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-[var(--modeler-surface-muted)] text-xs uppercase text-[var(--modeler-text-muted)]">
                <tr>
                  <th className="px-4 py-3 font-semibold">Name</th>
                  <th className="px-4 py-3 font-semibold">Type</th>
                  <th className="px-4 py-3 font-semibold">Key</th>
                  <th className="px-4 py-3 font-semibold">Version</th>
                  <th className="px-4 py-3 font-semibold">Updated</th>
                  <th className="px-4 py-3 text-right font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredResources.map(resource => (
                  <tr key={`${resource.kind}-${resource.id}`} className="border-t border-[var(--modeler-border)] transition-colors hover:bg-[var(--modeler-surface-muted)]">
                    <td className="max-w-xs px-4 py-3">
                      <p className="truncate font-semibold text-[var(--modeler-text)]">{resource.name}</p>
                      {resource.description && <p className="truncate text-xs text-[var(--modeler-text-muted)]">{resource.description}</p>}
                    </td>
                    <td className="px-4 py-3">
                      <span className="rounded bg-[var(--modeler-surface-muted)] px-2 py-1 text-xs font-medium text-[var(--modeler-text-soft)]">{kindLabel[resource.kind]}</span>
                    </td>
                    <td className="px-4 py-3 font-mono text-xs text-[var(--modeler-text-muted)]">{resource.key}</td>
                    <td className="px-4 py-3 text-[var(--modeler-text-soft)]">v{resource.version || 1}</td>
                    <td className="px-4 py-3 text-[var(--modeler-text-muted)]">{formatDate(resource.updatedAt)}</td>
                    <td className="px-4 py-3 text-right">
                      <button
                        onClick={() => onOpenResource?.(resource)}
                        className="inline-flex min-w-[72px] items-center justify-center gap-2 rounded-md bg-blue-600 px-3 py-2 text-xs font-semibold text-white transition-colors hover:bg-blue-500"
                      >
                        Open
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {!isLoadingResources && filteredResources.length === 0 && (
              <div className="flex flex-col items-center justify-center px-4 py-16 text-center">
                <Import className="mb-3 h-8 w-8 text-[var(--modeler-text-muted)]" />
                <h3 className="text-sm font-semibold text-[var(--modeler-text)]">No resources found</h3>
                <p className="mt-1 max-w-sm text-sm text-[var(--modeler-text-muted)]">Create a new resource or adjust the search to find published versions.</p>
                <button
                  onClick={activeTab === 'process' ? onCreateProcess : activeTab === 'form' ? onCreateForm : onCreateAgentProcess}
                  className="mt-4 inline-flex items-center gap-2 rounded-md bg-blue-600 px-3 py-2 text-xs font-semibold text-white transition-colors hover:bg-blue-500"
                >
                  <Plus className="h-4 w-4" />
                  Create {kindLabel[activeTab]}
                </button>
              </div>
            )}

            {isLoadingResources && (
              <div className="flex items-center justify-center gap-2 px-4 py-16 text-sm text-[var(--modeler-text-muted)]">
                <Loader2 className="h-4 w-4 animate-spin" />
                Loading resources...
              </div>
            )}
          </div>
        </section>
      </main>
    </div>
  );
};
