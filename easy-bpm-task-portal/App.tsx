import React, { useState, useEffect } from 'react';
import { Sidebar } from './components/Sidebar';
import { bpmService } from './services/bpmService';
import { DynamicForm } from './components/DynamicForm';
import { ThemeMode, ThemeToggle } from './components/ThemeToggle';
import { Task, ProcessDefinition, TaskStatus, Form } from './types';
import {
  Play,
  CheckCircle2,
  Clock,
  ArrowRight,
  Loader2,
  Inbox,
  Lock,
  User,
  ShieldCheck,
  Plus,
  Trash2,
  AlertCircle,
  ChevronRight,
  Zap,
  ListTodo
} from 'lucide-react';

type VariableType = 'string' | 'number' | 'boolean' | 'json';

interface VariableEntry {
  id: string;
  key: string;
  type: VariableType;
  value: string;
}

const parseVariableValue = (entry: VariableEntry): unknown => {
  if (entry.type === 'number') {
    return entry.value === '' ? 0 : Number(entry.value);
  }
  if (entry.type === 'boolean') {
    return entry.value.toLowerCase() === 'true';
  }
  if (entry.type === 'json') {
    if (!entry.value.trim()) return null;
    return JSON.parse(entry.value);
  }
  return entry.value;
};

const inferVariableType = (value: unknown): VariableType => {
  if (typeof value === 'number') return 'number';
  if (typeof value === 'boolean') return 'boolean';
  if (value !== null && typeof value === 'object') return 'json';
  return 'string';
};

const stringifyVariableValue = (value: unknown, type: VariableType): string => {
  if (value === null || value === undefined) return '';
  if (type === 'json') return JSON.stringify(value);
  return String(value);
};

const mapToVariableEntries = (variables: Record<string, any>): VariableEntry[] => {
  return Object.entries(variables).map(([key, value], index) => {
    const type = inferVariableType(value);
    return {
      id: `${key}-${index}-${Date.now()}`,
      key,
      type,
      value: stringifyVariableValue(value, type)
    };
  });
};

const App: React.FC = () => {
  const [theme, setTheme] = useState<ThemeMode>(() => {
    const storedTheme = localStorage.getItem('easyBpmTaskPortalTheme');
    if (storedTheme === 'light' || storedTheme === 'dark') return storedTheme;
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  });
  const [currentUser, setCurrentUser] = useState<string | null>(null);
  const [currentView, setCurrentView] = useState('inbox');
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);

  useEffect(() => {
    localStorage.setItem('easyBpmTaskPortalTheme', theme);
    document.documentElement.dataset.easyBpmTaskPortalTheme = theme;
  }, [theme]);

  const toggleTheme = () => setTheme((current) => current === 'dark' ? 'light' : 'dark');

  const handleLogin = (username: string) => {
    setCurrentUser(username);
    setCurrentView('inbox');
  };

  const handleLogout = () => {
    setCurrentUser(null);
    setCurrentView('inbox');
    setSelectedTaskId(null);
  };

  const navigateToTask = (taskId: number) => {
    setSelectedTaskId(taskId);
    setCurrentView('task-detail');
  };

  if (!currentUser) {
    return <LoginView onLogin={handleLogin} theme={theme} onToggleTheme={toggleTheme} />;
  }

  const renderView = () => {
    if (currentView === 'task-detail' && selectedTaskId) {
      return <TaskDetailView taskId={selectedTaskId} currentUser={currentUser} onBack={() => setCurrentView('inbox')} />;
    }

    switch (currentView) {
      case 'dashboard':
        return <DashboardView onNavigate={setCurrentView} currentUser={currentUser} />;
      case 'inbox':
        return <InboxView onSelectTask={navigateToTask} currentUser={currentUser} />;
      case 'processes':
        return <ProcessListView onViewInbox={() => setCurrentView('inbox')} />;
      default:
        return <InboxView onSelectTask={navigateToTask} currentUser={currentUser} />;
    }
  };

  return (
    <div className="task-portal-app flex min-h-screen bg-slate-50 font-sans" data-theme={theme}>
      <Sidebar
        currentView={currentView}
        onChangeView={setCurrentView}
        currentUser={currentUser}
        onLogout={handleLogout}
        theme={theme}
        onToggleTheme={toggleTheme}
      />
      <main className="flex-1 p-8 overflow-y-auto h-screen">
        <div className="max-w-5xl mx-auto">{renderView()}</div>
      </main>
    </div>
  );
};

const LoginView: React.FC<{ onLogin: (username: string) => void; theme: ThemeMode; onToggleTheme: () => void }> = ({ onLogin, theme, onToggleTheme }) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim()) return;

    setLoading(true);
    try {
      await bpmService.login(username.trim(), password);
      onLogin(username.trim());
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="task-portal-app login-shell min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-950 via-slate-900 to-blue-950 p-4" data-theme={theme}>
      <div className="absolute right-5 top-5">
        <ThemeToggle theme={theme} onToggle={onToggleTheme} />
      </div>
      <div className="w-full max-w-md">
        {/* Card */}
        <div className="bg-white/5 backdrop-blur-sm border border-white/10 rounded-2xl p-8 shadow-2xl">
          <div className="flex flex-col items-center mb-8">
            <div className="bg-blue-600 p-3 rounded-xl mb-4 shadow-lg shadow-blue-600/40 ring-4 ring-blue-600/20">
              <ShieldCheck className="text-white" size={28} />
            </div>
            <h1 className="text-2xl font-bold text-white">Easy BPM Task Portal</h1>
            <p className="text-slate-400 mt-1 text-sm">Sign in to open your work inbox</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1.5">Username</label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={16} />
                <input
                  type="text"
                  className="w-full pl-9 pr-4 py-2.5 rounded-lg border border-white/10 bg-white/5 text-white placeholder-slate-500 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all text-sm"
                  placeholder="Enter username"
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
                  placeholder="Optional"
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
          </form>
        </div>
        <p className="text-center text-[11px] text-slate-600 mt-4">Easy BPM · Task Execution Portal</p>
      </div>
    </div>
  );
};

const DashboardView: React.FC<{ onNavigate: (view: string) => void; currentUser: string }> = ({ onNavigate, currentUser }) => {
  type QuickAction = {
    id: string;
    icon: React.ElementType;
    label: string;
    description: string;
    accent: 'blue' | 'emerald' | 'purple';
    badge: string;
    navigable: boolean;
  };

  const quickActions: QuickAction[] = [
    {
      id: 'inbox',
      icon: Inbox,
      label: 'My Inbox',
      description: 'View and complete your assigned tasks. Open forms, edit variables, and advance process flows.',
      accent: 'blue',
      badge: 'Tasks',
      navigable: true
    },
    {
      id: 'processes',
      icon: Play,
      label: 'Start Process',
      description: 'Trigger new BPM process instances from available deployed workflow definitions.',
      accent: 'emerald',
      badge: 'Launch',
      navigable: true
    },
    {
      id: 'variable-sync',
      icon: CheckCircle2,
      label: 'Variable Sync',
      description: 'Task outputs are automatically synchronized as global process variables on completion.',
      accent: 'purple',
      badge: 'Auto',
      navigable: false
    }
  ];

  const accentMap = {
    blue:    { icon: 'bg-blue-100 text-blue-600',    hover: 'group-hover:bg-blue-600 group-hover:text-white',    badge: 'bg-blue-50 text-blue-600 border-blue-200'   },
    emerald: { icon: 'bg-emerald-100 text-emerald-600', hover: 'group-hover:bg-emerald-600 group-hover:text-white', badge: 'bg-emerald-50 text-emerald-600 border-emerald-200' },
    purple:  { icon: 'bg-purple-100 text-purple-600',  hover: 'group-hover:bg-purple-600 group-hover:text-white',  badge: 'bg-purple-50 text-purple-600 border-purple-200'  }
  };

  return (
    <div className="space-y-8">
      <header>
        <h2 className="text-2xl font-bold text-slate-800">Welcome back, {currentUser}</h2>
        <p className="text-slate-500 text-sm mt-1">Task execution workspace for active BPM instances.</p>
      </header>

      <div className="grid grid-cols-1 gap-4">
        {quickActions.map(({ id, icon: Icon, label, description, accent, badge, navigable }) => {
          const colors = accentMap[accent];
          return (
            <div
              key={id}
              onClick={navigable ? () => onNavigate(id) : undefined}
              className={`bg-white p-5 rounded-xl border border-slate-200 transition-all group flex items-start gap-4 ${
                navigable ? 'cursor-pointer hover:border-slate-300 hover:shadow-md' : 'cursor-default opacity-80'
              }`}
            >
              <div className={`p-3 rounded-xl flex-shrink-0 transition-colors ${colors.icon} ${navigable ? colors.hover : ''}`}>
                <Icon size={20} />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-0.5">
                  <span className="text-sm font-semibold text-slate-800">{label}</span>
                  <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded border ${colors.badge}`}>{badge}</span>
                </div>
                <p className="text-xs text-slate-500 leading-relaxed">{description}</p>
              </div>
              {navigable && <ChevronRight size={16} className="text-slate-300 group-hover:text-slate-500 flex-shrink-0 mt-1 transition-colors" />}
            </div>
          );
        })}
      </div>
    </div>
  );
};

const InboxView: React.FC<{ onSelectTask: (id: number) => void; currentUser: string }> = ({ onSelectTask, currentUser }) => {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<'all' | 'assigned' | 'completed'>('assigned');

  useEffect(() => {
    void loadTasks();
  }, [filter, currentUser]);

  const loadTasks = async () => {
    setLoading(true);
    try {
      const data = await bpmService.getTasks(filter === 'all' ? undefined : currentUser);

      let filtered = data;
      if (filter === 'completed') {
        filtered = data.filter((task) => task.status === TaskStatus.COMPLETED);
      } else {
        filtered = data.filter((task) => task.status !== TaskStatus.COMPLETED);
      }
      setTasks(filtered);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-slate-800">Task Inbox</h2>
          <p className="text-slate-500 text-sm mt-1">Open tasks, forms, and variable outputs</p>
        </div>

        <div className="flex bg-slate-200 p-1 rounded-lg self-start">
          {(['assigned', 'all', 'completed'] as const).map((current) => (
            <button
              key={current}
              onClick={() => setFilter(current)}
              className={`px-4 py-1.5 rounded-md text-sm font-medium transition-all ${
                filter === current ? 'bg-white text-blue-700 shadow-sm' : 'text-slate-600 hover:text-slate-800'
              }`}
            >
              {current.charAt(0).toUpperCase() + current.slice(1)}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center py-20">
          <Loader2 className="animate-spin text-blue-500" size={32} />
        </div>
      ) : tasks.length === 0 ? (
        <div className="text-center py-20 bg-white rounded-xl border border-dashed border-slate-300">
          <ListTodo className="mx-auto text-slate-300 mb-3" size={48} />
          <p className="text-slate-500 font-medium">No tasks found in this category.</p>
          <p className="text-slate-400 text-sm mt-1">Tasks assigned to you will appear here.</p>
        </div>
      ) : (
        <div className="grid gap-3">
          {tasks.map((task) => (
            <div
              key={task.id}
              onClick={() => onSelectTask(task.id)}
              className="group bg-white p-5 rounded-xl border border-slate-200 hover:border-blue-300 hover:shadow-md transition-all cursor-pointer flex items-center justify-between"
            >
              <div className="flex items-start gap-4">
                <div
                  className={`mt-1.5 w-2.5 h-2.5 rounded-full ${
                    task.status === TaskStatus.COMPLETED ? 'bg-green-500' : 'bg-blue-500 shadow-sm shadow-blue-400'
                  }`}
                />
                <div>
                  <h4 className="font-semibold text-slate-800 group-hover:text-blue-600 transition-colors text-lg">{task.name || `Task ${task.id}`}</h4>
                  <p className="text-sm text-slate-500 line-clamp-1">{task.description}</p>
                  <div className="flex items-center gap-4 mt-2 text-xs text-slate-400">
                    <span className="flex items-center gap-1">
                      <Clock size={12} /> {new Date(task.createdAt).toLocaleDateString()}
                    </span>
                    <span className="px-2 py-0.5 bg-slate-100 rounded text-slate-600 font-mono">ID: {task.id}</span>
                    <span className="px-2 py-0.5 bg-slate-100 rounded text-slate-600 font-mono">Process ID: {task.processInstanceId}</span>
                  </div>
                </div>
              </div>
              <ArrowRight size={20} className="text-slate-300 group-hover:text-blue-500 transform group-hover:translate-x-1 transition-all" />
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

const ProcessListView: React.FC<{ onViewInbox: () => void }> = ({ onViewInbox }) => {
  const [processes, setProcesses] = useState<ProcessDefinition[]>([]);
  const [starting, setStarting] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    bpmService
      .getProcesses()
      .then((result) => setProcesses(result.content))
      .catch((loadError) => setError((loadError as Error).message));
  }, []);

  const handleStart = async (key: string) => {
    setStarting(key);
    setError(null);
    try {
      await bpmService.startProcess(key);
      setTimeout(() => onViewInbox(), 500);
    } catch (startError) {
      setError((startError as Error).message);
      setStarting(null);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-slate-800">Start Process</h2>
        <p className="text-slate-500 text-sm mt-1">Initiate new workflows from the task portal.</p>
      </div>

      {error && (
        <div className="p-3 rounded-lg border border-red-200 bg-red-50 text-red-700 text-sm flex items-center gap-2">
          <AlertCircle size={16} className="flex-shrink-0" />
          {error}
        </div>
      )}

      {processes.length === 0 && !error ? (
        <div className="text-center py-20 bg-white rounded-xl border border-dashed border-slate-300">
          <Zap className="mx-auto text-slate-300 mb-3" size={48} />
          <p className="text-slate-500 font-medium">No processes deployed yet.</p>
          <p className="text-slate-400 text-sm mt-1">Deploy a process via the BPMN Modeler to get started.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {processes.map((processDefinition) => (
            <div
              key={processDefinition.id}
              className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm flex flex-col justify-between hover:border-slate-300 hover:shadow-md transition-all"
            >
              <div className="flex items-start gap-4">
                <div className="p-2.5 bg-emerald-100 text-emerald-600 rounded-xl flex-shrink-0">
                  <Play size={18} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap mb-1">
                    <h3 className="font-bold text-slate-800 text-sm">{processDefinition.processName || processDefinition.key}</h3>
                    <span className="text-[10px] font-mono bg-blue-50 text-blue-600 border border-blue-200 px-1.5 py-0.5 rounded">{processDefinition.key}</span>
                    <span className="text-[10px] bg-slate-100 text-slate-500 px-1.5 py-0.5 rounded">v{processDefinition.version}</span>
                  </div>
                  <p className="text-xs text-slate-500 line-clamp-2">{processDefinition.description}</p>
                </div>
              </div>
              <div className="mt-4 flex justify-end">
                <button
                  onClick={() => handleStart(processDefinition.key)}
                  disabled={starting === processDefinition.key}
                  className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-50 shadow-sm shadow-blue-200"
                >
                  {starting === processDefinition.key ? <Loader2 className="animate-spin" size={14} /> : <Play size={14} />}
                  Start
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

const TaskVariableEditor: React.FC<{
  entries: VariableEntry[];
  onChange: (entries: VariableEntry[]) => void;
  disabled: boolean;
}> = ({ entries, onChange, disabled }) => {
  const updateEntry = (entryId: string, patch: Partial<VariableEntry>) => {
    onChange(entries.map((entry) => (entry.id === entryId ? { ...entry, ...patch } : entry)));
  };

  const addEntry = () => {
    onChange([
      ...entries,
      {
        id: `var-${Date.now()}`,
        key: '',
        type: 'string',
        value: ''
      }
    ]);
  };

  const removeEntry = (entryId: string) => {
    onChange(entries.filter((entry) => entry.id !== entryId));
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-slate-800">Task Variables</h3>
        {!disabled && (
          <button
            onClick={addEntry}
            className="inline-flex items-center gap-1 px-3 py-1.5 text-sm rounded-md bg-blue-600 text-white hover:bg-blue-700"
          >
            <Plus size={14} /> Add Variable
          </button>
        )}
      </div>

      {entries.length === 0 ? (
        <div className="p-4 text-sm bg-slate-50 border border-slate-200 rounded-lg text-slate-500">
          No variables yet. Add one and it will be sent as output when completing the task.
        </div>
      ) : (
        <div className="space-y-3">
          {entries.map((entry) => (
            <div key={entry.id} className="grid grid-cols-1 md:grid-cols-12 gap-2 items-center">
              <input
                className="md:col-span-4 rounded-lg border px-3 py-2 text-sm border-slate-300"
                placeholder="variableName"
                value={entry.key}
                disabled={disabled}
                onChange={(event) => updateEntry(entry.id, { key: event.target.value })}
              />

              <select
                className="md:col-span-3 rounded-lg border px-3 py-2 text-sm border-slate-300"
                value={entry.type}
                disabled={disabled}
                onChange={(event) => updateEntry(entry.id, { type: event.target.value as VariableType })}
              >
                <option value="string">string</option>
                <option value="number">number</option>
                <option value="boolean">boolean</option>
                <option value="json">json</option>
              </select>

              {entry.type === 'boolean' ? (
                <select
                  className="md:col-span-4 rounded-lg border px-3 py-2 text-sm border-slate-300"
                  value={entry.value.toLowerCase() === 'true' ? 'true' : 'false'}
                  disabled={disabled}
                  onChange={(event) => updateEntry(entry.id, { value: event.target.value })}
                >
                  <option value="true">true</option>
                  <option value="false">false</option>
                </select>
              ) : (
                <input
                  className="md:col-span-4 rounded-lg border px-3 py-2 text-sm border-slate-300"
                  placeholder={entry.type === 'json' ? '{"key":"value"}' : 'value'}
                  value={entry.value}
                  disabled={disabled}
                  onChange={(event) => updateEntry(entry.id, { value: event.target.value })}
                />
              )}

              {!disabled && (
                <button
                  className="md:col-span-1 inline-flex justify-center p-2 rounded-md border border-slate-200 text-slate-500 hover:text-red-600 hover:border-red-200"
                  onClick={() => removeEntry(entry.id)}
                >
                  <Trash2 size={14} />
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

const TaskDetailView: React.FC<{ taskId: number; onBack: () => void; currentUser: string }> = ({ taskId, onBack, currentUser }) => {
  const [task, setTask] = useState<Task | null>(null);
  const [formDef, setFormDef] = useState<Form | null>(null);
  const [formData, setFormData] = useState<Record<string, any>>({});
  const [variableEntries, setVariableEntries] = useState<VariableEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadData();
  }, [taskId]);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    setFormDef(null);

    try {
      const selectedTask = await bpmService.getTaskById(taskId);
      setTask(selectedTask);

      const taskVariables = selectedTask.variables || {};
      setFormData(taskVariables);
      setVariableEntries(mapToVariableEntries(taskVariables));

      if (selectedTask.formDbId) {
        try {
          const loadedForm = await bpmService.getFormById(selectedTask.formDbId);
          setFormDef(loadedForm);
        } catch (loadFormError) {
          setError((loadFormError as Error).message);
        }
      }
    } catch (loadError) {
      setError((loadError as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const buildCompletionVariables = (): Record<string, any> => {
    if (formDef) {
      return formData;
    }

    const output: Record<string, any> = {};
    variableEntries
      .filter((entry) => entry.key.trim().length > 0)
      .forEach((entry) => {
        output[entry.key.trim()] = parseVariableValue(entry);
      });

    return output;
  };

  const validateVariableEntries = (): string | null => {
    if (formDef) return null;

    const usedKeys = new Set<string>();
    for (const entry of variableEntries) {
      const key = entry.key.trim();
      if (!key) {
        return 'All variable rows must have a key.';
      }
      if (usedKeys.has(key)) {
        return `Duplicate variable key: ${key}`;
      }
      usedKeys.add(key);
      if (entry.type === 'json' && entry.value.trim()) {
        try {
          JSON.parse(entry.value);
        } catch {
          return `Invalid JSON for variable ${key}`;
        }
      }
    }

    return null;
  };

  const handleComplete = async () => {
    if (!task) return;

    const validationError = validateVariableEntries();
    if (validationError) {
      setError(validationError);
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      await bpmService.completeTask(task.id, {
        assignee: currentUser,
        variables: buildCompletionVariables()
      });
      onBack();
    } catch (submitError) {
      setError((submitError as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  if (loading || !task) {
    return (
      <div className="flex justify-center pt-20">
        <Loader2 className="animate-spin text-blue-500" size={32} />
      </div>
    );
  }

  const isCompleted = task.status === TaskStatus.COMPLETED;

  return (
    <div className="max-w-3xl mx-auto space-y-6 pb-20">
      <button onClick={onBack} className="text-sm text-slate-500 hover:text-blue-600 flex items-center gap-1 transition-colors">
        &larr; Back to Inbox
      </button>

      <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
        <div className="p-6 border-b border-slate-100 bg-slate-50/50 flex justify-between items-start">
          <div>
            <div className="flex gap-2 mb-2">
              <span className="text-xs font-bold text-blue-600 tracking-wider uppercase bg-blue-50 px-2 py-1 rounded border border-blue-100">
                Instance #{task.processInstanceId}
              </span>
              <span className="text-xs font-bold text-slate-500 tracking-wider uppercase bg-slate-100 px-2 py-1 rounded border border-slate-200">
                Task #{task.id}
              </span>
              {(task.formId || formDef?.formId) && (
                <span className="text-xs font-bold text-emerald-600 tracking-wider uppercase bg-emerald-50 px-2 py-1 rounded border border-emerald-100">
                  Form ID: {task.formId || formDef?.formId}
                </span>
              )}
            </div>
            <h1 className="text-2xl font-bold text-slate-900 mt-2">{task.name || 'Untitled Task'}</h1>
            <p className="text-slate-500 mt-1">{task.description}</p>
          </div>
          {isCompleted && (
            <div className="bg-green-100 text-green-700 px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1">
              <CheckCircle2 size={12} /> COMPLETED
            </div>
          )}
        </div>

        <div className="p-6 space-y-5">
          {error && (
            <div className="p-3 rounded-lg border border-red-200 bg-red-50 text-red-700 text-sm flex items-center gap-2">
              <AlertCircle size={16} />
              {error}
            </div>
          )}

          {formDef ? (
            <div className="space-y-3">
              <p className="text-sm text-slate-500">
                This task has a form. Form values will be submitted as task outputs and synchronized as process variables on completion.
              </p>
              <DynamicForm schema={formDef.schema} initialData={formData} onChange={setFormData} disabled={isCompleted} taskId={task.id} processInstanceId={task.processInstanceId} />
            </div>
          ) : (
            <div className="space-y-3">
              <p className="text-sm text-slate-500">
                This task has no form definition. Edit existing task variables or create new ones. All submitted variables will be available as global process variables after completion.
              </p>
              <TaskVariableEditor entries={variableEntries} onChange={setVariableEntries} disabled={isCompleted} />
            </div>
          )}
        </div>

        {!isCompleted && (
          <div className="p-6 bg-slate-50 border-t border-slate-100 flex justify-end gap-3">
            <button
              onClick={onBack}
              className="px-4 py-2 bg-white border border-slate-300 rounded-lg text-slate-700 font-medium hover:bg-slate-50 transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={handleComplete}
              disabled={submitting}
              className="px-6 py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 shadow-sm shadow-blue-200 transition-all flex items-center gap-2"
            >
              {submitting ? <Loader2 className="animate-spin" size={16} /> : <CheckCircle2 size={16} />}
              Complete Task
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default App;
