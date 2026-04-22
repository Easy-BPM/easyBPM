import React, { useEffect, useState } from 'react';
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
  ShieldCheck,
  StopCircle,
  Trash2,
  User,
  Workflow
} from 'lucide-react';
import { Sidebar } from './components/Sidebar';
import { WorkflowCanvas } from './components/WorkflowCanvas';
import { CodeTaskExecutionListPage } from './components/CodeTaskExecutionListPage';
import { adminService } from './services/adminService';
import { ProcessDefinition, ProcessInstance, ProcessVariable, WorkflowDefinition } from './types';

const App: React.FC = () => {
  const [currentUser, setCurrentUser] = useState<string | null>(null);
  const [currentView, setCurrentView] = useState('dashboard');

  const handleLogin = (username: string) => {
    setCurrentUser(username);
    setCurrentView('dashboard');
  };

  const handleLogout = () => {
    setCurrentUser(null);
    setCurrentView('dashboard');
  };

  if (!currentUser) {
    return <LoginView onLogin={handleLogin} />;
  }

  const renderView = () => {
    switch (currentView) {
      case 'dashboard':
        return <DashboardView onNavigate={setCurrentView} />;
      case 'instances':
        return <InstanceExplorerView />;
      case 'workflows':
        return <WorkflowCatalogView />;
      case 'code-tasks':
        return <CodeTaskExecutionListPage />;
      default:
        return <DashboardView onNavigate={setCurrentView} />;
    }
  };

  return (
    <div className="flex min-h-screen bg-slate-50 font-sans">
      <Sidebar
        currentView={currentView}
        onChangeView={setCurrentView}
        currentUser={currentUser}
        onLogout={handleLogout}
      />
      <main className="flex-1 p-8 overflow-y-auto h-screen">
        <div className="max-w-6xl mx-auto">{renderView()}</div>
      </main>
    </div>
  );
};

const LoginView: React.FC<{ onLogin: (username: string) => void }> = ({ onLogin }) => {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username) return;
    setLoading(true);
    try {
      await new Promise((resolve) => setTimeout(resolve, 500));
      onLogin(username);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-100 p-4">
      <div className="bg-white p-8 rounded-2xl shadow-xl w-full max-w-md border border-slate-200">
        <div className="flex flex-col items-center mb-8">
          <div className="bg-blue-600 p-3 rounded-xl mb-4 shadow-lg shadow-blue-600/30">
            <ShieldCheck className="text-white" size={32} />
          </div>
          <h1 className="text-2xl font-bold text-slate-800">Easy Admin</h1>
          <p className="text-slate-500 mt-1">Operate and monitor BPM instances</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Username</label>
            <div className="relative">
              <User className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={18} />
              <input
                type="text"
                className="w-full pl-10 pr-4 py-2.5 rounded-lg border border-slate-300 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all"
                placeholder="Enter admin username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">Password</label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={18} />
              <input
                type="password"
                className="w-full pl-10 pr-4 py-2.5 rounded-lg border border-slate-300 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium py-2.5 rounded-lg transition-colors shadow-lg shadow-blue-600/20 flex items-center justify-center gap-2 mt-2"
          >
            {loading ? <Loader2 className="animate-spin" size={20} /> : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  );
};

const DashboardView: React.FC<{ onNavigate: (view: string) => void }> = ({ onNavigate }) => {
  return (
    <div className="space-y-6">
      <header className="mb-8">
        <h2 className="text-2xl font-bold text-slate-800">Operations Overview</h2>
        <p className="text-slate-500">Track live instances and act on workflow execution.</p>
      </header>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div
          onClick={() => onNavigate('instances')}
          className="bg-white p-6 rounded-xl shadow-sm border border-slate-200 cursor-pointer hover:shadow-md transition-shadow group"
        >
          <div className="flex items-center justify-between mb-4">
            <div className="p-3 bg-blue-100 text-blue-600 rounded-lg group-hover:bg-blue-600 group-hover:text-white transition-colors">
              <Search size={24} />
            </div>
            <span className="text-2xl font-bold text-slate-800">Live</span>
          </div>
          <h3 className="text-slate-600 font-medium">Find Instance</h3>
          <p className="text-xs text-slate-400 mt-1">Search by instance number</p>
        </div>

        <div
          onClick={() => onNavigate('workflows')}
          className="bg-white p-6 rounded-xl shadow-sm border border-slate-200 cursor-pointer hover:shadow-md transition-shadow group"
        >
          <div className="flex items-center justify-between mb-4">
            <div className="p-3 bg-emerald-100 text-emerald-600 rounded-lg group-hover:bg-emerald-600 group-hover:text-white transition-colors">
              <Workflow size={24} />
            </div>
            <span className="text-2xl font-bold text-slate-800">Catalog</span>
          </div>
          <h3 className="text-slate-600 font-medium">Deployed Workflows</h3>
          <p className="text-xs text-slate-400 mt-1">See deployed process definitions</p>
        </div>
      </div>
    </div>
  );
};

const InstanceExplorerView: React.FC = () => {
  const [instanceIdInput, setInstanceIdInput] = useState('');
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
  const [parentInstance, setParentInstance] = useState<ProcessInstance | null>(null);
  const [childInstances, setChildInstances] = useState<ProcessInstance[]>([]);
  const [hierarchyLoading, setHierarchyLoading] = useState(false);
  const [selectedChildId, setSelectedChildId] = useState<number | null>(null);
  const [childMapping, setChildMapping] = useState<any>(null);

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
      if (!definition?.definitionJson) {
        setWorkflowError('Definition JSON is not available for this deployed version.');
        return;
      }

      const parsed = JSON.parse(definition.definitionJson) as WorkflowDefinition;
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

  const handleSearch = async () => {
    const parsedId = Number(instanceIdInput);
    if (!parsedId) return;

    setLoading(true);
    setActionMessage(null);
    try {
      const found = await adminService.findInstanceById(parsedId);
      setInstance(found);
      if (found) {
        setVariables(await adminService.getProcessVariables(found.id));
        await loadDefinitionForInstance(found);
        await loadHierarchy(found);
      } else {
        setVariables([]);
        setWorkflowDefinition(null);
      }
    } catch (error) {
      console.error(error);
      setActionMessage('Failed to search process instance.');
      setWorkflowDefinition(null);
    } finally {
      setLoading(false);
    }
  };

  const handleAssignVariable = async () => {
    if (!instance || !newVarName.trim()) return;
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
        <p className="text-slate-500">Search a process by instance number, manage variables, and move nodes safely.</p>
      </div>

      <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm">
        <label className="block text-sm font-medium text-slate-700 mb-2">Find Process Instance by Number</label>
        <div className="flex gap-2">
          <input
            type="number"
            className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
            placeholder="e.g. 1001"
            value={instanceIdInput}
            onChange={(e) => setInstanceIdInput(e.target.value)}
          />
          <button
            onClick={handleSearch}
            disabled={loading}
            className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2.5 rounded-lg text-sm font-medium transition-colors"
          >
            {loading ? <Loader2 className="animate-spin" size={16} /> : 'Search'}
          </button>
        </div>
      </div>

      {actionMessage && (
        <div className="bg-slate-900 text-white rounded-lg px-4 py-3 text-sm flex items-center gap-2">
          <AlertCircle size={16} /> {actionMessage}
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
                  className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
                  placeholder="variableName"
                  value={newVarName}
                  onChange={(e) => setNewVarName(e.target.value)}
                />
                <input
                  className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
                  placeholder="value"
                  value={newVarValue}
                  onChange={(e) => setNewVarValue(e.target.value)}
                />
              </div>
              <button
                onClick={handleAssignVariable}
                className="bg-slate-900 hover:bg-slate-800 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors"
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
              {(instance.nodeHistory ?? []).map((node, idx) => (
                <span key={`${node}-${idx}`} className="px-3 py-1.5 rounded-full bg-purple-50 text-purple-700 text-xs font-medium border border-purple-200">
                  {idx + 1}. {node}
                </span>
              ))}
            </div>
          </div>

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
                  <span className="px-2 py-1 rounded bg-blue-50 text-blue-700 border border-blue-200">Visited nodes: {instance.nodeHistory?.length ?? 0}</span>
                  <span className="px-2 py-1 rounded bg-emerald-50 text-emerald-700 border border-emerald-200">Current nodes: {instance.currentNode?.length ?? 0}</span>
                </div>
                <WorkflowCanvas
                  definition={workflowDefinition}
                  nodeHistory={instance.nodeHistory ?? []}
                  currentNodes={instance.currentNode ?? []}
                />
              </>
            )}
          </div>

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
        <p className="text-slate-500">Browse all deployed process definitions and versions.</p>
      </div>

      {loading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="animate-spin text-blue-500" size={30} />
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {processes.map((proc) => (
            <div key={proc.id} className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm hover:shadow-md transition-shadow">
              <div className="flex justify-between items-start gap-3">
                <div>
                  <h3 className="font-bold text-lg text-slate-800">{proc.name}</h3>
                  <p className="text-sm text-slate-500 mt-1">{proc.description || 'No description provided.'}</p>
                </div>
                <span className="text-xs font-mono bg-slate-100 text-slate-600 px-2 py-1 rounded">v{proc.version}</span>
              </div>
              <div className="mt-4 flex items-center gap-2 text-xs text-slate-500 font-mono">
                <span className="px-2 py-1 rounded bg-slate-100">ID: {proc.id}</span>
                {proc.key && <span className="px-2 py-1 rounded bg-slate-100">KEY: {proc.key}</span>}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

const MetricCard: React.FC<{ icon: React.ReactNode; title: string; value: string; tone: 'blue' | 'emerald' | 'purple' }> = ({
  icon,
  title,
  value,
  tone
}) => {
  const toneClass = {
    blue: 'bg-blue-100 text-blue-700',
    emerald: 'bg-emerald-100 text-emerald-700',
    purple: 'bg-purple-100 text-purple-700'
  }[tone];

  return (
    <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-sm">
      <div className="flex items-center justify-between mb-3">
        <div className={`p-2 rounded-lg ${toneClass}`}>{icon}</div>
        <span className="text-lg font-bold text-slate-800">{value}</span>
      </div>
      <p className="text-xs uppercase tracking-wide text-slate-500">{title}</p>
    </div>
  );
};

export default App;
