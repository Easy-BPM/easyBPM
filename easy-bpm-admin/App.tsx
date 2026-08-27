import React, { useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  ArrowRightLeft,
  CheckCircle2,
  Clock,
  GitBranch,
  Layers,
  Loader2,
  Lock,
  Search,
  Settings2,
  StopCircle,
  Trash2,
  User,
  Workflow
} from 'lucide-react';
import { Sidebar } from './components/Sidebar';
import { WorkflowCanvas } from './components/WorkflowCanvas';
import { CodeTaskExecutionListPage } from './components/CodeTaskExecutionListPage';
import { DashboardView } from './components/DashboardView';
import { IncidentListPage } from './components/IncidentListPage';
import { MaintenancePage } from './components/MaintenancePage';
import { SecurityAdminView } from './components/SecurityAdminView';
import { TaskResourcesView } from './components/TaskResourcesView';
import { ThemeMode, ThemeToggle } from './components/ThemeToggle';
import { adminService } from './services/adminService';
import { ProcessDefinition, ProcessInstance, ProcessInstanceEvent, ProcessVariable, WorkflowDefinition } from './types';
import { parseWorkflowDefinition } from './utils/bpmnXml';

const App: React.FC = () => {
  const [theme, setTheme] = useState<ThemeMode>(() => {
    const storedTheme = localStorage.getItem('easyBpmAdminTheme');
    if (storedTheme === 'light' || storedTheme === 'dark') return storedTheme;
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  });
  const [currentUser, setCurrentUser] = useState<string | null>(null);
  const [permissions, setPermissions] = useState<string[]>([]);
  const [currentView, setCurrentView] = useState('dashboard');
  const [selectedInstanceId, setSelectedInstanceId] = useState<number | null>(null);
  const [authLoading, setAuthLoading] = useState(true);

  useEffect(() => {
    localStorage.setItem('easyBpmAdminTheme', theme);
    document.documentElement.dataset.easyBpmAdminTheme = theme;
  }, [theme]);

  const toggleTheme = () => setTheme((current) => current === 'dark' ? 'light' : 'dark');

  useEffect(() => {
    const session = adminService.getSession();
    if (!session) {
      setAuthLoading(false);
      return;
    }

    setCurrentUser(session.username);
    setPermissions(session.permissions);
    adminService.me()
      .then(me => setPermissions(me.permissions))
      .catch(() => {
        adminService.clearSession();
        setCurrentUser(null);
        setPermissions([]);
      })
      .finally(() => setAuthLoading(false));
  }, []);

  useEffect(() => {
    const handleAuthExpired = () => {
      setCurrentUser(null);
      setPermissions([]);
      setCurrentView('dashboard');
    };

    window.addEventListener('easybpm-admin-auth-expired', handleAuthExpired);
    return () => window.removeEventListener('easybpm-admin-auth-expired', handleAuthExpired);
  }, []);

  const handleLogin = (username: string, perms: string[]) => {
    setCurrentUser(username);
    setPermissions(perms);
    setCurrentView('dashboard');
  };

  const handleLogout = () => {
    adminService.clearSession();
    setCurrentUser(null);
    setPermissions([]);
    setCurrentView('dashboard');
  };

  if (authLoading) {
    return <div className="min-h-screen flex items-center justify-center text-slate-600">Loading session...</div>;
  }

  if (!currentUser) {
    return <LoginView onLogin={handleLogin} theme={theme} onToggleTheme={toggleTheme} />;
  }

  if (!permissions.includes('ACCESS_BPM_ADMIN')) {
    return (
      <div className="admin-app min-h-screen flex items-center justify-center bg-slate-100" data-theme={theme}>
        <div className="bg-white border border-slate-200 rounded-xl p-6 text-center">
          <h2 className="text-lg font-semibold text-slate-800">Access denied</h2>
          <p className="text-slate-500 mt-1">Your account does not have BPM Admin access.</p>
          <button onClick={handleLogout} className="mt-4 px-4 py-2 bg-slate-900 text-white rounded-lg">Sign out</button>
        </div>
      </div>
    );
  }

  const renderView = () => {
    switch (currentView) {
      case 'dashboard':
        return <DashboardView onNavigate={setCurrentView} />;
      case 'instances':
        return <InstanceExplorerView initialInstanceId={selectedInstanceId} />;
      case 'incidents':
        return (
          <IncidentListPage
            currentUser={currentUser}
            onOpenInstance={(instanceId) => {
              setSelectedInstanceId(instanceId);
              setCurrentView('instances');
            }}
          />
        );
      case 'workflows':
        return <WorkflowCatalogView />;
      case 'code-tasks':
        return <CodeTaskExecutionListPage />;
      case 'maintenance':
        return <MaintenancePage />;
      case 'security-admin':
        return <SecurityAdminView />;
      case 'task-resources':
        return <TaskResourcesView />;
      default:
        return <DashboardView onNavigate={setCurrentView} />;
    }
  };

  return (
    <div className="admin-app flex min-h-screen bg-slate-50 font-sans" data-theme={theme}>
      <Sidebar
        currentView={currentView}
        onChangeView={setCurrentView}
        currentUser={currentUser}
        permissions={permissions}
        onLogout={handleLogout}
        theme={theme}
        onToggleTheme={toggleTheme}
      />
      <main className="flex-1 px-6 py-6 overflow-y-auto h-screen">
        <div className={`${currentView === 'dashboard' ? 'max-w-7xl' : 'max-w-5xl'} mx-auto`}>{renderView()}</div>
      </main>
    </div>
  );
};

const LoginView: React.FC<{ onLogin: (username: string, perms: string[]) => void; theme: ThemeMode; onToggleTheme: () => void }> = ({ onLogin, theme, onToggleTheme }) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username) return;
    setLoading(true);
    setError(null);
    try {
      const session = await adminService.login(username.trim(), password);
      onLogin(session.username, session.permissions);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="admin-app login-shell min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-950 via-slate-900 to-blue-950 p-4" data-theme={theme}>
      <div className="absolute right-5 top-5">
        <ThemeToggle theme={theme} onToggle={onToggleTheme} />
      </div>
      <div className="w-full max-w-md">
        {/* Card */}
        <div className="bg-white/5 backdrop-blur-sm border border-white/10 rounded-2xl p-8 shadow-2xl">
          <div className="flex flex-col items-center mb-8">
            <img
              src="/easy-bpm-logo.png"
              alt="Easy BPM"
              className="mb-4 h-14 w-14 rounded-xl object-cover shadow-lg shadow-blue-600/40 ring-4 ring-blue-600/20"
            />
            <h1 className="text-2xl font-bold text-white">Easy BPM Admin</h1>
            <p className="text-slate-400 mt-1 text-sm">Sign in to your operations console</p>
          </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">Username</label>
            <div className="relative">
              <User className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={16} />
              <input
                type="text"
                className="w-full pl-9 pr-4 py-2.5 rounded-lg border border-white/10 bg-white/5 text-white placeholder-slate-500 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all text-sm"
                placeholder="Enter admin username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">Password</label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={16} />
              <input
                type="password"
                className="w-full pl-9 pr-4 py-2.5 rounded-lg border border-white/10 bg-white/5 text-white placeholder-slate-500 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all text-sm"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 hover:bg-blue-500 text-white font-semibold py-2.5 rounded-lg transition-colors shadow-lg shadow-blue-600/30 flex items-center justify-center gap-2 mt-2 text-sm"
          >
            {loading ? <Loader2 className="animate-spin" size={18} /> : 'Sign In'}
          </button>
          {error && <p className="text-sm text-red-600">{error}</p>}
        </form>
        </div>
        <p className="text-center text-[11px] text-slate-600 mt-4">Easy BPM · Process Operations Console</p>
      </div>
    </div>
  );
};

const InstanceExplorerView: React.FC<{ initialInstanceId?: number | null }> = ({ initialInstanceId }) => {
  const [instanceIdInput, setInstanceIdInput] = useState(initialInstanceId ? String(initialInstanceId) : '');
  const [loading, setLoading] = useState(false);
  const [instance, setInstance] = useState<ProcessInstance | null>(null);
  const [variables, setVariables] = useState<ProcessVariable[]>([]);
  const [newVarName, setNewVarName] = useState('');
  const [newVarValue, setNewVarValue] = useState('');
  const [moveFrom, setMoveFrom] = useState('');
  const [moveTo, setMoveTo] = useState('');
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [workflowDefinition, setWorkflowDefinition] = useState<WorkflowDefinition | null>(null);
  const [workflowLoading, setWorkflowLoading] = useState(false);
  const [workflowError, setWorkflowError] = useState<string | null>(null);
  const [activeInstanceTab, setActiveInstanceTab] = useState<'overview' | 'workflow'>('overview');
  const [parentInstance, setParentInstance] = useState<ProcessInstance | null>(null);
  const [childInstances, setChildInstances] = useState<ProcessInstance[]>([]);
  const [timelineEvents, setTimelineEvents] = useState<ProcessInstanceEvent[]>([]);
  const [hierarchyLoading, setHierarchyLoading] = useState(false);
  const [selectedChildId, setSelectedChildId] = useState<number | null>(null);
  const [childMapping, setChildMapping] = useState<any>(null);
  const isInstanceCompleted = instance?.status === 'COMPLETED';
  const effectiveNodeHistory = useMemo(() => {
    const rawHistory = instance?.nodeHistory ?? [];
    const startNodeId = workflowDefinition?.nodes?.find((node) => {
      const normalizedType = node.type.toLowerCase();
      return normalizedType === 'startevent' || normalizedType === 'start' || normalizedType === 'messagestartevent' || normalizedType === 'message-start';
    })?.id;

    if (!startNodeId || rawHistory.includes(startNodeId)) return rawHistory;
    return [startNodeId, ...rawHistory];
  }, [instance?.nodeHistory, workflowDefinition]);

  const loadDefinitionForInstance = async (targetInstance: ProcessInstance | null) => {
    setWorkflowDefinition(null);
    setWorkflowError(null);
    if (!targetInstance) return;

    const definitionId = targetInstance.processDefinitionId ?? targetInstance.processDefinition?.id;
    if (!definitionId) {
      setWorkflowError('Definition id is not available for this instance.');
      return;
    }

    setWorkflowLoading(true);
    try {
      const definition = await adminService.getProcessDefinitionById(definitionId);
      const rawDefinition = definition?.definitionXml || definition?.definitionJson;
      if (!rawDefinition) {
        setWorkflowError('Definition XML is not available for this deployed version.');
        return;
      }

      const parsed = parseWorkflowDefinition(rawDefinition);
      setWorkflowDefinition(parsed);
    } catch (error) {
      console.error(error);
      setWorkflowError('Failed to load workflow definition for this instance version.');
    } finally {
      setWorkflowLoading(false);
    }
  };

  const loadHierarchy = async (targetInstance: ProcessInstance | null) => {
    setParentInstance(null);
    setChildInstances([]);
    if (!targetInstance) return;

    setHierarchyLoading(true);
    try {
      // Load parent if this is a subprocess
      if (targetInstance.parentInstanceId) {
        const parent = await adminService.getParentInstance(targetInstance.id);
        setParentInstance(parent);
      }

      // Load children if this instance has subprocesses
      const children = await adminService.getChildInstances(targetInstance.id);
      setChildInstances(children);
    } catch (error) {
      console.error('Error loading hierarchy:', error);
    } finally {
      setHierarchyLoading(false);
    }
  };

  const loadChildMapping = async (parentId: number, childId: number) => {
    setChildMapping(null);
    setSelectedChildId(childId);
    try {
      const mapping = await adminService.getCallActivityMapping(parentId, childId);
      setChildMapping(mapping);
    } catch (error) {
      console.error('Error loading child mapping:', error);
    }
  };

  const getNodeDisplayName = (nodeId: string) => {
    if (!workflowDefinition?.nodes) return nodeId;
    const node = workflowDefinition.nodes.find(n => n.id === nodeId);
    if (node?.name) {
      return `${node.name} (${nodeId})`;
    }
    return nodeId;
  };

  const loadInstanceById = async (targetId: number) => {
    if (!targetId) return;

    setLoading(true);
    setActionMessage(null);
    try {
      const found = await adminService.findInstanceById(targetId);
      setInstance(found);
      setActiveInstanceTab('overview');
      if (found) {
        setVariables(await adminService.getProcessVariables(found.id));
        setTimelineEvents(await adminService.getProcessTimeline(found.id));
        await loadDefinitionForInstance(found);
        await loadHierarchy(found);
      } else {
        setVariables([]);
        setTimelineEvents([]);
        setWorkflowDefinition(null);
      }
    } catch (error) {
      console.error(error);
      setActionMessage('Failed to search process instance.');
      setWorkflowDefinition(null);
      setTimelineEvents([]);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async () => {
    const parsedId = Number(instanceIdInput);
    if (!parsedId) return;

    await loadInstanceById(parsedId);
  };

  useEffect(() => {
    if (!initialInstanceId) return;
    setInstanceIdInput(String(initialInstanceId));
  }, [initialInstanceId]);

  useEffect(() => {
    if (!initialInstanceId) return;
    loadInstanceById(initialInstanceId);
  }, [initialInstanceId]);

  const handleAssignVariable = async () => {
    if (!instance || !newVarName.trim()) return;
    if (isInstanceCompleted) {
      setActionMessage('Completed process instances cannot be edited.');
      return;
    }
    try {
      await adminService.assignProcessVariables(instance.id, {
        variables: { [newVarName.trim()]: newVarValue }
      });
      const refreshed = await adminService.getProcessVariables(instance.id);
      setVariables(refreshed);
      setNewVarName('');
      setNewVarValue('');
      setActionMessage('Variable assigned successfully.');
    } catch (error) {
      console.error(error);
      setActionMessage('Failed to assign variable.');
    }
  };

  const handleMoveNode = async () => {
    if (!instance || !moveFrom.trim() || !moveTo.trim()) return;
    try {
      await adminService.moveNode(instance.id, { fromNode: moveFrom, toNode: moveTo, reason: 'Manual admin operation' });
      const refreshed = await adminService.findInstanceById(instance.id);
      setInstance(refreshed);
      await loadDefinitionForInstance(refreshed);
      setActionMessage('Node moved successfully.');
    } catch (error) {
      console.error(error);
      setActionMessage('Failed to move node.');
    }
  };

  const handleStopInstance = async () => {
    if (!instance) return;
    try {
      await adminService.stopInstance(instance.id);
      const refreshed = await adminService.findInstanceById(instance.id);
      setInstance(refreshed);
      await loadDefinitionForInstance(refreshed);
      setActionMessage('Instance stopped successfully.');
    } catch (error) {
      console.error(error);
      setActionMessage('Failed to stop instance.');
    }
  };

  const handleDeleteInstance = async () => {
    if (!instance) return;
    const confirmed = window.confirm(`Delete instance #${instance.id}? This action cannot be undone.`);
    if (!confirmed) return;

    try {
      await adminService.deleteInstance(instance.id);
      setInstance(null);
      setVariables([]);
      setWorkflowDefinition(null);
      setActionMessage('Instance deleted successfully.');
    } catch (error) {
      console.error(error);
      setActionMessage('Failed to delete instance.');
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-slate-800">Instance Explorer</h2>
        <p className="text-slate-500 text-sm">Search a process by instance number, manage variables, and move nodes safely.</p>
      </div>

      <div className="bg-white border border-slate-200 rounded-xl p-5">
        <label className="block text-xs font-semibold uppercase tracking-widest text-slate-400 mb-2">Find Process Instance by Number</label>
        <div className="flex gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={15} />
            <input
              type="number"
              className="w-full pl-9 pr-4 rounded-lg border border-slate-300 py-2.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
              placeholder="e.g. 1001"
              value={instanceIdInput}
              onChange={(e) => setInstanceIdInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <button
            onClick={handleSearch}
            disabled={loading}
            className="bg-blue-600 hover:bg-blue-700 text-white px-5 py-2.5 rounded-lg text-sm font-semibold transition-colors flex items-center gap-2 disabled:opacity-60"
          >
            {loading ? <Loader2 className="animate-spin" size={15} /> : <><Search size={14} /> Search</>}
          </button>
        </div>
      </div>

      {actionMessage && (
        <div className={`rounded-lg px-4 py-3 text-sm flex items-center gap-2 border ${
          actionMessage.toLowerCase().includes('successfully') || actionMessage.toLowerCase().includes('success')
            ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
            : actionMessage.toLowerCase().includes('failed') || actionMessage.toLowerCase().includes('error')
            ? 'bg-red-50 text-red-800 border-red-200'
            : 'bg-slate-100 text-slate-700 border-slate-200'
        }`}>
          {actionMessage.toLowerCase().includes('successfully') || actionMessage.toLowerCase().includes('success') ? (
            <CheckCircle2 size={15} className="flex-shrink-0" />
          ) : actionMessage.toLowerCase().includes('failed') || actionMessage.toLowerCase().includes('error') ? (
            <AlertCircle size={15} className="flex-shrink-0" />
          ) : (
            <AlertCircle size={15} className="flex-shrink-0" />
          )}
          {actionMessage}
        </div>
      )}

      {instance && (
        <>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <MetricCard icon={<Layers size={20} />} title="Instance ID" value={String(instance.id)} tone="blue" />
            <MetricCard icon={<CheckCircle2 size={20} />} title="Status" value={instance.status} tone="emerald" />
            <MetricCard
              icon={<Clock size={20} />}
              title="Updated"
              value={new Date(instance.updatedAt).toLocaleString()}
              tone="purple"
            />
          </div>

          <div className="flex items-center gap-1 border-b border-slate-200" role="tablist" aria-label="Instance details">
            <button
              type="button"
              role="tab"
              aria-selected={activeInstanceTab === 'overview'}
              onClick={() => setActiveInstanceTab('overview')}
              className={`border-b-2 px-4 py-3 text-sm font-semibold transition-colors ${
                activeInstanceTab === 'overview'
                  ? 'border-blue-600 text-blue-700'
                  : 'border-transparent text-slate-500 hover:text-slate-800'
              }`}
            >
              Overview
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeInstanceTab === 'workflow'}
              onClick={() => setActiveInstanceTab('workflow')}
              className={`border-b-2 px-4 py-3 text-sm font-semibold transition-colors ${
                activeInstanceTab === 'workflow'
                  ? 'border-blue-600 text-blue-700'
                  : 'border-transparent text-slate-500 hover:text-slate-800'
              }`}
            >
              Workflow
            </button>
          </div>

          <div className={activeInstanceTab === 'overview' ? 'space-y-6' : 'hidden'}>

          {instance.status === 'FAILED' && (
            <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800 shadow-sm">
              <div className="flex items-center gap-2 font-semibold">
                <AlertCircle size={16} />
                Instance error
              </div>
              {instance.errorNodeId && (
                <p className="mt-2">
                  Node: <span className="font-mono">{getNodeDisplayName(instance.errorNodeId)}</span>
                </p>
              )}
              <pre className="mt-2 whitespace-pre-wrap break-words rounded-lg border border-red-100 bg-white/70 p-3 text-xs text-red-900">
                {instance.errorMessage || 'The instance failed, but no error message was recorded.'}
              </pre>
            </div>
          )}

          {/* Instance Hierarchy Breadcrumb */}
          {(parentInstance || childInstances.length > 0) && (
            <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm space-y-4">
              <h3 className="font-semibold text-slate-800 flex items-center gap-2">
                <GitBranch size={18} className="text-indigo-600" /> Instance Hierarchy
              </h3>

              {/* Parent Link */}
              {parentInstance && (
                <div className="flex items-center gap-2 pb-3 border-b border-slate-200">
                  <span className="text-xs uppercase tracking-wide text-slate-500 font-bold">Parent</span>
                  <button
                    onClick={() => setInstanceIdInput(String(parentInstance.id))}
                    className="text-sm px-3 py-1.5 rounded-lg bg-indigo-50 text-indigo-700 hover:bg-indigo-100 transition-colors border border-indigo-200 font-medium"
                  >
                    Instance #{parentInstance.id}
                  </button>
                  <span className="text-xs text-slate-500">{parentInstance.status}</span>
                  {instance.callActivityNodeId && <span className="text-xs px-2 py-1 rounded bg-slate-100 text-slate-600">Node: {instance.callActivityNodeId}</span>}
                </div>
              )}

              {/* Current Instance (with nesting level indicator) */}
              <div className="flex items-center gap-2 py-2 px-3 rounded-lg bg-blue-50 border border-blue-200">
                <span className="text-xs uppercase tracking-wide text-blue-600 font-bold">Current</span>
                <span className="text-sm px-3 py-1.5 rounded-lg bg-white text-blue-700 font-mono font-medium">Instance #{instance.id}</span>
                {instance.nestingLevel !== undefined && <span className="text-xs px-2 py-1 rounded bg-blue-100 text-blue-700">Level {instance.nestingLevel}</span>}
              </div>

              {/* Child Instances */}
              {childInstances.length > 0 && (
                <div className="pt-3 border-t border-slate-200 space-y-2">
                  <p className="text-xs uppercase tracking-wide text-slate-500 font-bold">Children ({childInstances.length})</p>
                  <div className="space-y-2">
                    {childInstances.map((child) => (
                      <button
                        key={child.id}
                        onClick={() => setInstanceIdInput(String(child.id))}
                        className="w-full flex items-center justify-between text-sm px-3 py-2.5 rounded-lg bg-emerald-50 text-emerald-700 hover:bg-emerald-100 transition-colors border border-emerald-200"
                      >
                        <span className="font-medium">Instance #{child.id}</span>
                        <span className="text-xs text-emerald-600">{child.status}</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm space-y-4">
              <h3 className="font-semibold text-slate-800 flex items-center gap-2">
                <Settings2 size={18} className="text-blue-600" /> Process Variables Assignment
              </h3>
              {isInstanceCompleted && (
                <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600 flex items-center gap-2">
                  <Lock size={15} className="text-slate-500" />
                  Variables are locked because this process instance is completed.
                </div>
              )}
              <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
                {variables.length === 0 ? (
                  <p className="text-sm text-slate-500">No variables found for this instance.</p>
                ) : (
                  variables.map((v) => (
                    <div key={v.name} className="flex items-center justify-between px-3 py-2 rounded-lg bg-slate-50 border border-slate-200">
                      <span className="font-mono text-sm text-slate-700">{v.name}</span>
                      <span className="text-sm text-slate-500 max-w-[55%] truncate">{String(v.value)}</span>
                    </div>
                  ))
                )}
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                <input
                  className="rounded-lg border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-100 disabled:text-slate-500 disabled:cursor-not-allowed"
                  placeholder="variableName"
                  value={newVarName}
                  disabled={isInstanceCompleted}
                  onChange={(e) => setNewVarName(e.target.value)}
                />
                <input
                  className="rounded-lg border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-100 disabled:text-slate-500 disabled:cursor-not-allowed"
                  placeholder="value"
                  value={newVarValue}
                  disabled={isInstanceCompleted}
                  onChange={(e) => setNewVarValue(e.target.value)}
                />
              </div>
              <button
                onClick={handleAssignVariable}
                disabled={isInstanceCompleted}
                className="bg-slate-900 hover:bg-slate-800 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors disabled:bg-slate-300 disabled:text-slate-500 disabled:cursor-not-allowed"
              >
                Assign Variable
              </button>
            </div>

            <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm space-y-4">
              <h3 className="font-semibold text-slate-800 flex items-center gap-2">
                <ArrowRightLeft size={18} className="text-emerald-600" /> Move Workflow Node
              </h3>
              <p className="text-sm text-slate-500">
                Move token from one node to another for recovery/operations scenarios.
              </p>
              <input
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                placeholder="fromNode (e.g. user-review)"
                value={moveFrom}
                onChange={(e) => setMoveFrom(e.target.value)}
              />
              <input
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                placeholder="toNode (e.g. manager-approval)"
                value={moveTo}
                onChange={(e) => setMoveTo(e.target.value)}
              />
              <button
                onClick={handleMoveNode}
                className="bg-emerald-600 hover:bg-emerald-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors"
              >
                Move Node
              </button>

              <div className="pt-3 border-t border-slate-200 space-y-2">
                <p className="text-xs uppercase tracking-wide text-slate-500">Instance Lifecycle</p>
                <div className="flex gap-2">
                  <button
                    onClick={handleStopInstance}
                    className="flex items-center gap-1.5 bg-amber-500 hover:bg-amber-600 text-white px-3 py-2 rounded-lg text-sm font-medium transition-colors"
                  >
                    <StopCircle size={16} /> Stop
                  </button>
                  <button
                    onClick={handleDeleteInstance}
                    className="flex items-center gap-1.5 bg-red-600 hover:bg-red-700 text-white px-3 py-2 rounded-lg text-sm font-medium transition-colors"
                  >
                    <Trash2 size={16} /> Delete
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm">
            <h3 className="font-semibold text-slate-800 mb-3 flex items-center gap-2">
              <GitBranch size={18} className="text-purple-600" /> Current Node History
            </h3>
            <div className="flex flex-wrap gap-2">
              {effectiveNodeHistory.map((node, idx) => (
                <span key={`${node}-${idx}`} className="px-3 py-1.5 rounded-full bg-purple-50 text-purple-700 text-xs font-medium border border-purple-200">
                  {idx + 1}. {getNodeDisplayName(node)}
                </span>
              ))}
            </div>
          </div>

          <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm space-y-4">
            <div className="flex items-center justify-between gap-3">
              <h3 className="font-semibold text-slate-800 flex items-center gap-2">
                <Clock size={18} className="text-blue-600" /> Process Timeline
              </h3>
              <span className="text-xs text-slate-500">{timelineEvents.length} events</span>
            </div>
            {timelineEvents.length === 0 ? (
              <p className="text-sm text-slate-500">No runtime events have been recorded for this instance yet.</p>
            ) : (
              <div className="space-y-3 max-h-[520px] overflow-y-auto pr-1">
                {timelineEvents.map((event) => (
                  <div key={event.id} className="flex gap-3">
                    <div className="flex flex-col items-center">
                      <div className={`w-2.5 h-2.5 rounded-full mt-1.5 ${
                        event.eventType.includes('FAILED') || event.eventType.includes('INCIDENT') ? 'bg-red-500' :
                        event.eventType.includes('COMPLETED') || event.eventType.includes('RESOLVED') ? 'bg-emerald-500' :
                        event.eventType.includes('TASK') ? 'bg-blue-500' :
                        'bg-slate-400'
                      }`} />
                      <div className="w-px flex-1 bg-slate-200 mt-1" />
                    </div>
                    <div className="flex-1 pb-3">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-xs font-semibold text-slate-700">{event.eventType}</span>
                        {event.nodeId && <span className="text-[11px] px-2 py-0.5 rounded bg-slate-100 text-slate-600 border border-slate-200">{event.nodeId}</span>}
                        {event.actor && <span className="text-[11px] text-slate-500">by {event.actor}</span>}
                      </div>
                      <p className="text-sm text-slate-700 mt-1">{event.message}</p>
                      {event.details && <p className="text-xs text-slate-500 mt-1 bg-slate-50 border border-slate-200 rounded px-2 py-1">{event.details}</p>}
                      <p className="text-[11px] text-slate-400 mt-1">{new Date(event.createdAt).toLocaleString()}</p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          </div>

          {activeInstanceTab === 'workflow' && (
          <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm space-y-4">
            <div className="flex items-center justify-between gap-3">
              <h3 className="font-semibold text-slate-800 flex items-center gap-2">
                <Workflow size={18} className="text-indigo-600" /> Workflow Path Visualizer
              </h3>
              {workflowLoading && <Loader2 className="animate-spin text-indigo-600" size={16} />}
            </div>
            <p className="text-sm text-slate-500">
              The canvas below uses the exact deployed process definition version linked to this instance and highlights its traveled path.
            </p>

            {workflowError && (
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-700">
                {workflowError}
              </div>
            )}

            {workflowDefinition && (
              <>
                <div className="flex flex-wrap items-center gap-3 text-xs">
                  <span className="px-2 py-1 rounded bg-slate-100 text-slate-700 border border-slate-200">Total nodes: {workflowDefinition.nodes?.length ?? 0}</span>
                  <span className="px-2 py-1 rounded bg-blue-50 text-blue-700 border border-blue-200">Visited nodes: {effectiveNodeHistory.length}</span>
                  <span className="px-2 py-1 rounded bg-emerald-50 text-emerald-700 border border-emerald-200">Current nodes: {instance.currentNode?.length ?? 0}</span>
                </div>
                <WorkflowCanvas
                  definition={workflowDefinition}
                  nodeHistory={effectiveNodeHistory}
                  currentNodes={instance.currentNode ?? []}
                  expanded
                />
              </>
            )}
          </div>
          )}

          <div className={activeInstanceTab === 'overview' ? 'space-y-6' : 'hidden'}>

          {/* Child Instances Inspection */}
          {childInstances.length > 0 && (
            <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm space-y-4">
              <h3 className="font-semibold text-slate-800 flex items-center gap-2">
                <Layers size={18} className="text-emerald-600" /> Child Process Instances ({childInstances.length})
              </h3>
              <p className="text-sm text-slate-500">
                Subprocesses invoked by this instance. Click to inspect details, variables, and execution history.
              </p>
              <div className="space-y-3">
                {childInstances.map((child) => (
                  <div key={child.id} className="border border-slate-200 rounded-lg p-4 hover:bg-slate-50 transition-colors">
                    <div className="flex items-start justify-between gap-4 mb-3">
                      <div className="flex-1">
                        <h4 className="font-medium text-slate-800">
                          Child Instance #{child.id}
                        </h4>
                        <p className="text-sm text-slate-500 mt-0.5">
                          {child.processDefinition?.name || 'Unknown Process'} (v{child.processDefinition?.version || '?'})
                        </p>
                        {child.callActivityNodeId && (
                          <p className="text-xs text-slate-400 mt-1">Called from node: <span className="font-mono bg-slate-100 px-1 rounded">{child.callActivityNodeId}</span></p>
                        )}
                      </div>
                      <div className="flex flex-col items-end gap-2">
                        <span className={`px-3 py-1 rounded-full text-xs font-medium border ${
                          child.status === 'COMPLETED' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' :
                          child.status === 'ACTIVE' ? 'bg-blue-50 text-blue-700 border-blue-200' :
                          child.status === 'FAILED' ? 'bg-red-50 text-red-700 border-red-200' :
                          'bg-slate-50 text-slate-700 border-slate-200'
                        }`}>
                          {child.status}
                        </span>
                        {child.nestingLevel !== undefined && (
                          <span className="text-xs text-slate-500 bg-slate-100 px-2 py-1 rounded">Level {child.nestingLevel}</span>
                        )}
                      </div>
                    </div>
                    <div className="grid grid-cols-3 gap-2 text-xs mb-3">
                      <div className="bg-slate-50 rounded p-2">
                        <p className="text-slate-500 uppercase tracking-wide font-bold">Created</p>
                        <p className="text-slate-700 font-mono text-[11px] mt-0.5">{new Date(child.createdAt).toLocaleString()}</p>
                      </div>
                      <div className="bg-slate-50 rounded p-2">
                        <p className="text-slate-500 uppercase tracking-wide font-bold">Updated</p>
                        <p className="text-slate-700 font-mono text-[11px] mt-0.5">{new Date(child.updatedAt).toLocaleString()}</p>
                      </div>
                      <div className="bg-slate-50 rounded p-2">
                        <p className="text-slate-500 uppercase tracking-wide font-bold">Node History</p>
                        <p className="text-slate-700 font-medium mt-0.5">{child.nodeHistory?.length ?? 0} nodes</p>
                      </div>
                    </div>
                    <button
                      onClick={() => {
                        setInstanceIdInput(String(child.id));
                        loadChildMapping(instance.id, child.id);
                      }}
                      className="w-full px-3 py-2 rounded-lg bg-emerald-50 text-emerald-700 hover:bg-emerald-100 transition-colors text-sm font-medium border border-emerald-200"
                    >
                      Inspect Instance
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Variable Mapping Display */}
          {childMapping && selectedChildId && (
            <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm space-y-4">
              <h3 className="font-semibold text-slate-800 flex items-center gap-2">
                <ArrowRightLeft size={18} className="text-amber-600" /> Variable Mappings for Instance #{selectedChildId}
              </h3>
              <p className="text-sm text-slate-500">
                Variables mapped from parent to child instance.
              </p>
              {childMapping.inputMappings && Object.keys(childMapping.inputMappings).length > 0 && (
                <div>
                  <h4 className="text-sm font-medium text-slate-700 mb-2">Input Mappings</h4>
                  <div className="space-y-1 text-xs text-slate-600">
                    {Object.entries(childMapping.inputMappings).map(([parentVar, childVar]) => (
                      <div key={parentVar}>{parentVar} → {childVar}</div>
                    ))}
                  </div>
                </div>
              )}
              {childMapping.outputMappings && Object.keys(childMapping.outputMappings).length > 0 && (
                <div>
                  <h4 className="text-sm font-medium text-slate-700 mb-2">Output Mappings</h4>
                  <div className="space-y-1 text-xs text-slate-600">
                    {Object.entries(childMapping.outputMappings).map(([childVar, parentVar]) => (
                      <div key={childVar}>{childVar} → {parentVar}</div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          </div>

        </>
      )}
    </div>
  );
};

const WorkflowCatalogView: React.FC = () => {
  const [processes, setProcesses] = useState<ProcessDefinition[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    adminService
      .getProcessDefinitions(0, 30)
      .then((res) => setProcesses(res.content))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-slate-800">Deployed Workflows</h2>
        <p className="text-slate-500 text-sm">Browse all deployed process definitions and versions.</p>
      </div>

      {loading ? (
        <div className="flex flex-col items-center justify-center py-20 text-slate-400 gap-3">
          <Loader2 className="animate-spin text-blue-500" size={28} />
          <p className="text-sm">Loading process definitions…</p>
        </div>
      ) : processes.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-slate-400 gap-3">
          <Workflow size={40} className="text-slate-300" />
          <p className="text-sm font-medium text-slate-500">No deployed workflows found</p>
          <p className="text-xs text-slate-400">Deploy a process from the BPMN Modeler to see it here.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {processes.map((proc) => (
            <div key={proc.id} className="bg-white p-5 rounded-xl border border-slate-200 hover:border-slate-300 hover:shadow-md transition-all group">
              <div className="flex justify-between items-start gap-3 mb-3">
                <div className="flex items-start gap-3 min-w-0">
                  <div className="p-2 bg-indigo-50 rounded-lg flex-shrink-0 group-hover:bg-indigo-100 transition-colors">
                    <Workflow size={16} className="text-indigo-600" />
                  </div>
                  <div className="min-w-0">
                    <h3 className="font-semibold text-slate-800 text-sm leading-tight truncate">{proc.name}</h3>
                    <p className="text-xs text-slate-500 mt-1 leading-relaxed line-clamp-2">{proc.description || 'No description provided.'}</p>
                  </div>
                </div>
                <span className="text-[10px] font-mono font-bold bg-slate-100 text-slate-600 px-2 py-1 rounded-md flex-shrink-0 border border-slate-200">v{proc.version}</span>
              </div>
              <div className="flex items-center gap-2 text-[11px] font-mono">
                <span className="px-2 py-1 rounded-md bg-slate-50 text-slate-500 border border-slate-200">#{proc.id}</span>
                {proc.key && (
                  <span className="px-2 py-1 rounded-md bg-blue-50 text-blue-600 border border-blue-100 font-semibold">{proc.key}</span>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

const MetricCard: React.FC<{ icon: React.ReactNode; title: string; value: string; tone: 'blue' | 'emerald' | 'purple' | 'red' }> = ({
  icon,
  title,
  value,
  tone
}) => {
  const toneStyles = {
    blue:    { icon: 'bg-blue-50 text-blue-600',    border: 'border-l-blue-500' },
    emerald: { icon: 'bg-emerald-50 text-emerald-600', border: 'border-l-emerald-500' },
    purple:  { icon: 'bg-purple-50 text-purple-600',  border: 'border-l-purple-500' },
    red:     { icon: 'bg-red-50 text-red-600',        border: 'border-l-red-500' }
  }[tone];

  return (
    <div className={`bg-white border border-slate-200 border-l-4 ${toneStyles.border} rounded-xl p-4 shadow-sm`}>
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-[10px] uppercase tracking-widest text-slate-400 font-semibold mb-1">{title}</p>
          <p className="text-base font-bold text-slate-800 leading-tight truncate max-w-[160px]" title={value}>{value}</p>
        </div>
        <div className={`p-2 rounded-lg flex-shrink-0 ${toneStyles.icon}`}>{icon}</div>
      </div>
    </div>
  );
};

export default App;
