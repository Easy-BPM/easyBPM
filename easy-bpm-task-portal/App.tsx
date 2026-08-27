import React, { useState, useEffect } from 'react';
import { Sidebar } from './components/Sidebar';
import { bpmService } from './services/bpmService';
import { DynamicForm } from './components/DynamicForm';
import { ThemeMode, ThemeToggle } from './components/ThemeToggle';
import { Task, ProcessDefinition, TaskStatus, Form, JsonSchemaProperty, TaskFilterOperator, TaskSearchFilter } from './types';
import {
  Play,
  CheckCircle2,
  Clock,
  ArrowRight,
  Loader2,
  Inbox,
  Lock,
  User,
  Save,
  Plus,
  Trash2,
  AlertCircle,
  ChevronRight,
  Zap,
  ListTodo,
  Unlock,
  Filter,
  Search,
  X
} from 'lucide-react';

type VariableType = 'string' | 'number' | 'boolean' | 'json';

interface VariableEntry {
  id: string;
  key: string;
  type: VariableType;
  value: string;
}

type InboxFilterField =
  | 'status'
  | 'assignee'
  | 'candidateUser'
  | 'candidateGroup'
  | 'processDefinition'
  | 'processInstanceId'
  | 'taskName'
  | 'createdAt'
  | 'taskVariable'
  | 'processVariable';

interface InboxFilterDraft {
  id: string;
  field: InboxFilterField;
  operator: TaskFilterOperator;
  value: string;
  variableName: string;
}

const FILTER_FIELD_OPTIONS: Array<{ value: InboxFilterField; label: string }> = [
  { value: 'status', label: 'State' },
  { value: 'assignee', label: 'Assignee' },
  { value: 'candidateUser', label: 'Candidate User' },
  { value: 'candidateGroup', label: 'Candidate Group' },
  { value: 'processDefinition', label: 'Process Definition' },
  { value: 'processInstanceId', label: 'Process Instance' },
  { value: 'taskName', label: 'Task Name' },
  { value: 'createdAt', label: 'Created Date' },
  { value: 'taskVariable', label: 'Task Variable' },
  { value: 'processVariable', label: 'Process Variable' }
];

const FILTER_FIELD_LABELS = Object.fromEntries(FILTER_FIELD_OPTIONS.map((option) => [option.value, option.label])) as Record<InboxFilterField, string>;

const OPERATOR_LABELS: Record<TaskFilterOperator, string> = {
  EQUALS: '=',
  NOT_EQUALS: '!=',
  IN: 'in',
  NOT_IN: 'not in',
  GREATER_THAN: '>',
  GREATER_THAN_OR_EQUAL: '>=',
  LESS_THAN: '<',
  LESS_THAN_OR_EQUAL: '<=',
  CONTAINS: 'contains',
  STARTS_WITH: 'starts with',
  ENDS_WITH: 'ends with'
};

const operatorOptionsForField = (field: InboxFilterField): TaskFilterOperator[] => {
  if (field === 'taskName') return ['CONTAINS', 'EQUALS', 'STARTS_WITH', 'ENDS_WITH'];
  if (field === 'createdAt' || field === 'processInstanceId') return ['EQUALS', 'GREATER_THAN_OR_EQUAL', 'LESS_THAN_OR_EQUAL', 'GREATER_THAN', 'LESS_THAN'];
  if (field === 'taskVariable' || field === 'processVariable') return ['EQUALS', 'NOT_EQUALS', 'CONTAINS', 'GREATER_THAN_OR_EQUAL', 'LESS_THAN_OR_EQUAL'];
  if (field === 'candidateGroup') return ['EQUALS', 'IN', 'NOT_IN'];
  return ['EQUALS', 'NOT_EQUALS', 'IN', 'NOT_IN'];
};

const createFilterDraft = (): InboxFilterDraft => ({
  id: `filter-${Date.now()}-${Math.random().toString(16).slice(2)}`,
  field: 'status',
  operator: 'EQUALS',
  value: TaskStatus.PENDING,
  variableName: ''
});

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

const defaultFormValue = (prop: JsonSchemaProperty) => {
  if (prop.type === 'boolean') return false;
  return '';
};

const normalizeFormVariables = (form: Form, data: Record<string, any>): Record<string, any> => {
  const normalized = { ...(data || {}) };
  Object.entries(form.schema.properties || {}).forEach(([key, prop]) => {
    if (normalized[key] === undefined || normalized[key] === null) {
      normalized[key] = defaultFormValue(prop);
    }
  });
  return normalized;
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
  const [authLoading, setAuthLoading] = useState(true);

  useEffect(() => {
    localStorage.setItem('easyBpmTaskPortalTheme', theme);
    document.documentElement.dataset.easyBpmTaskPortalTheme = theme;
  }, [theme]);

  const toggleTheme = () => setTheme((current) => current === 'dark' ? 'light' : 'dark');

  useEffect(() => {
    const session = bpmService.getSession();
    if (!session?.username) {
      setAuthLoading(false);
      return;
    }

    setCurrentUser(session.username);
    bpmService.me()
      .then((me) => setCurrentUser(me.username))
      .catch(() => {
        bpmService.clearSession();
        setCurrentUser(null);
        setCurrentView('inbox');
        setSelectedTaskId(null);
      })
      .finally(() => setAuthLoading(false));
  }, []);

  useEffect(() => {
    const handleAuthExpired = () => {
      setCurrentUser(null);
      setCurrentView('inbox');
      setSelectedTaskId(null);
    };

    window.addEventListener('easybpm-portal-auth-expired', handleAuthExpired);
    return () => window.removeEventListener('easybpm-portal-auth-expired', handleAuthExpired);
  }, []);

  const handleLogin = (username: string) => {
    setCurrentUser(username);
    setCurrentView('inbox');
  };

  const handleLogout = () => {
    bpmService.clearSession();
    setCurrentUser(null);
    setCurrentView('inbox');
    setSelectedTaskId(null);
  };

  const navigateToTask = (taskId: number) => {
    setSelectedTaskId(taskId);
    setCurrentView('task-detail');
  };

  if (authLoading) {
    return <div className="min-h-screen flex items-center justify-center text-slate-600">Loading session...</div>;
  }

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
      <main className="flex-1 p-6 overflow-y-auto h-screen">
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
      const session = await bpmService.login(username.trim(), password);
      onLogin(session.username);
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
            <img
              src="/easy-bpm-logo.png"
              alt="Easy BPM"
              className="mb-4 h-14 w-14 rounded-xl object-cover shadow-lg shadow-blue-600/40 ring-4 ring-blue-600/20"
            />
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
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<'all' | 'assigned' | 'completed'>('assigned');
  const [filterPanelOpen, setFilterPanelOpen] = useState(false);
  const [draftFilters, setDraftFilters] = useState<InboxFilterDraft[]>([]);
  const [activeFilters, setActiveFilters] = useState<InboxFilterDraft[]>([]);

  useEffect(() => {
    void loadTasks();
  }, [filter, currentUser, activeFilters]);

  const buildQuickFilters = (): TaskSearchFilter[] => {
    if (filter === 'completed') {
      return [
        { field: 'assignee', operator: 'EQUALS', value: currentUser },
        { field: 'status', operator: 'EQUALS', value: TaskStatus.COMPLETED }
      ];
    }

    const statusFilter: TaskSearchFilter = { field: 'status', operator: 'NOT_EQUALS', value: TaskStatus.COMPLETED };
    if (filter === 'assigned') {
      return [
        { field: 'assignee', operator: 'EQUALS', value: currentUser },
        statusFilter
      ];
    }

    return [statusFilter];
  };

  const parseFilterValue = (draft: InboxFilterDraft): string | number => {
    if (draft.field === 'processInstanceId') return Number(draft.value);
    return draft.value.trim();
  };

  const toSearchFilter = (draft: InboxFilterDraft): TaskSearchFilter => {
    const baseValue = parseFilterValue(draft);
    const usesValues = draft.operator === 'IN' || draft.operator === 'NOT_IN';
    const valueParts = String(draft.value)
      .split(',')
      .map((part) => part.trim())
      .filter(Boolean);

    if (draft.field === 'taskVariable' || draft.field === 'processVariable') {
      return {
        field: 'variable',
        scope: draft.field === 'taskVariable' ? 'TASK' : 'PROCESS',
        name: draft.variableName.trim(),
        operator: draft.operator,
        ...(usesValues ? { values: valueParts } : { value: baseValue })
      };
    }

    return {
      field: draft.field,
      operator: draft.operator,
      ...(usesValues ? { values: valueParts } : { value: baseValue })
    };
  };

  const validateDraftFilters = (filtersToValidate: InboxFilterDraft[]): string | null => {
    for (const draft of filtersToValidate) {
      if ((draft.field === 'taskVariable' || draft.field === 'processVariable') && !draft.variableName.trim()) {
        return `${FILTER_FIELD_LABELS[draft.field]} requires a variable name.`;
      }
      if (!draft.value.trim()) return `${FILTER_FIELD_LABELS[draft.field]} requires a value.`;
      if (draft.field === 'processInstanceId' && Number.isNaN(Number(draft.value))) {
        return 'Process Instance must be a number.';
      }
    }
    return null;
  };

  const loadTasks = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await bpmService.getTasks([...buildQuickFilters(), ...activeFilters.map(toSearchFilter)]);
      setTasks(data);
    } catch (error) {
      setError((error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const updateDraftFilter = (id: string, patch: Partial<InboxFilterDraft>) => {
    setDraftFilters((current) => current.map((draft) => {
      if (draft.id !== id) return draft;
      const next = { ...draft, ...patch };
      if (patch.field) {
        const operators = operatorOptionsForField(patch.field);
        next.operator = operators[0];
        next.value = patch.field === 'status' ? TaskStatus.PENDING : '';
        next.variableName = '';
      }
      return next;
    }));
  };

  const addDraftFilter = () => {
    setDraftFilters((current) => [...current, createFilterDraft()]);
    setFilterPanelOpen(true);
  };

  const applyFilters = () => {
    const validationError = validateDraftFilters(draftFilters);
    if (validationError) {
      setError(validationError);
      return;
    }
    setActiveFilters(draftFilters);
    setFilterPanelOpen(false);
  };

  const removeActiveFilter = (id: string) => {
    const next = activeFilters.filter((draft) => draft.id !== id);
    setActiveFilters(next);
    setDraftFilters(next);
  };

  const clearFilters = () => {
    setActiveFilters([]);
    setDraftFilters([]);
  };

  const describeFilter = (draft: InboxFilterDraft) => {
    const fieldLabel = FILTER_FIELD_LABELS[draft.field];
    const prefix = draft.field === 'taskVariable' || draft.field === 'processVariable'
      ? `${fieldLabel} ${draft.variableName}`
      : fieldLabel;
    return `${prefix} ${OPERATOR_LABELS[draft.operator]} ${draft.value}`;
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-slate-800">Task Inbox</h2>
          <p className="text-slate-500 text-sm mt-1">Open tasks, forms, and variable outputs</p>
        </div>

        <div className="flex flex-wrap gap-2">
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
          <button
            onClick={() => setFilterPanelOpen((open) => !open)}
            className="inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-slate-200 bg-white text-sm font-medium text-slate-700 hover:border-blue-200 hover:text-blue-700"
          >
            <Filter size={16} /> Filters
          </button>
        </div>
      </div>

      {(activeFilters.length > 0 || error) && (
        <div className="space-y-3">
          {activeFilters.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {activeFilters.map((activeFilter) => (
                <button
                  key={activeFilter.id}
                  onClick={() => removeActiveFilter(activeFilter.id)}
                  className="inline-flex items-center gap-1.5 rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-medium text-blue-700 hover:bg-blue-100"
                >
                  {describeFilter(activeFilter)}
                  <X size={12} />
                </button>
              ))}
              <button onClick={clearFilters} className="text-xs font-medium text-slate-500 hover:text-red-600 px-2">
                Clear all
              </button>
            </div>
          )}
          {error && (
            <div className="p-3 rounded-lg border border-red-200 bg-red-50 text-red-700 text-sm flex items-center gap-2">
              <AlertCircle size={16} />
              {error}
            </div>
          )}
        </div>
      )}

      {filterPanelOpen && (
        <div className="bg-white border border-slate-200 rounded-xl shadow-sm p-4 space-y-4">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-sm font-semibold text-slate-800 inline-flex items-center gap-2">
              <Search size={16} /> Task filters
            </h3>
            <button onClick={addDraftFilter} className="inline-flex items-center gap-1.5 text-sm font-medium text-blue-700 hover:text-blue-800">
              <Plus size={14} /> Add filter
            </button>
          </div>

          {draftFilters.length === 0 ? (
            <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-4 text-sm text-slate-500">
              No custom filters applied.
            </div>
          ) : (
            <div className="space-y-3">
              {draftFilters.map((draft) => {
                const operatorOptions = operatorOptionsForField(draft.field);
                return (
                  <div key={draft.id} className="grid grid-cols-1 md:grid-cols-12 gap-2 items-center">
                    <select
                      className="md:col-span-3 rounded-lg border px-3 py-2 text-sm border-slate-300"
                      value={draft.field}
                      onChange={(event) => updateDraftFilter(draft.id, { field: event.target.value as InboxFilterField })}
                    >
                      {FILTER_FIELD_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>{option.label}</option>
                      ))}
                    </select>
                    {(draft.field === 'taskVariable' || draft.field === 'processVariable') && (
                      <input
                        className="md:col-span-2 rounded-lg border px-3 py-2 text-sm border-slate-300"
                        placeholder="name"
                        value={draft.variableName}
                        onChange={(event) => updateDraftFilter(draft.id, { variableName: event.target.value })}
                      />
                    )}
                    <select
                      className={`${draft.field === 'taskVariable' || draft.field === 'processVariable' ? 'md:col-span-2' : 'md:col-span-3'} rounded-lg border px-3 py-2 text-sm border-slate-300`}
                      value={draft.operator}
                      onChange={(event) => updateDraftFilter(draft.id, { operator: event.target.value as TaskFilterOperator })}
                    >
                      {operatorOptions.map((operator) => (
                        <option key={operator} value={operator}>{OPERATOR_LABELS[operator]}</option>
                      ))}
                    </select>
                    {draft.field === 'status' ? (
                      <select
                        className="md:col-span-5 rounded-lg border px-3 py-2 text-sm border-slate-300"
                        value={draft.value}
                        onChange={(event) => updateDraftFilter(draft.id, { value: event.target.value })}
                      >
                        {Object.values(TaskStatus).map((status) => (
                          <option key={status} value={status}>{status}</option>
                        ))}
                      </select>
                    ) : (
                      <input
                        className="md:col-span-5 rounded-lg border px-3 py-2 text-sm border-slate-300"
                        placeholder={draft.operator === 'IN' || draft.operator === 'NOT_IN' ? 'comma,separated,values' : 'value'}
                        type={draft.field === 'createdAt' ? 'date' : draft.field === 'processInstanceId' ? 'number' : 'text'}
                        value={draft.value}
                        onChange={(event) => updateDraftFilter(draft.id, { value: event.target.value })}
                      />
                    )}
                    <button
                      className="md:col-span-1 inline-flex justify-center p-2 rounded-md border border-slate-200 text-slate-500 hover:text-red-600 hover:border-red-200"
                      onClick={() => setDraftFilters((current) => current.filter((item) => item.id !== draft.id))}
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                );
              })}
            </div>
          )}

          <div className="flex justify-end gap-2">
            <button onClick={clearFilters} className="px-3 py-2 rounded-lg border border-slate-200 text-sm font-medium text-slate-600 hover:bg-slate-50">
              Clear
            </button>
            <button onClick={applyFilters} className="px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-700">
              Apply Filters
            </button>
          </div>
        </div>
      )}

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
  const [savingDraft, setSavingDraft] = useState(false);
  const [unclaiming, setUnclaiming] = useState(false);
  const [claimNoticeOpen, setClaimNoticeOpen] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadData();
  }, [taskId]);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    setFormDef(null);

    try {
      const initialTask = await bpmService.getTaskById(taskId);
      const selectedTask = initialTask.status !== TaskStatus.COMPLETED && initialTask.assignee === null
        ? await bpmService.claimTask(taskId)
        : initialTask;

      if (initialTask.assignee === null && selectedTask.assignee === currentUser) {
        setClaimNoticeOpen(true);
      }

      setTask(selectedTask);

      const taskVariables = selectedTask.variables || {};
      setFormData(taskVariables);
      setVariableEntries(mapToVariableEntries(taskVariables));

      if (selectedTask.formDbId) {
        try {
          const loadedForm = await bpmService.getFormById(selectedTask.formDbId);
          setFormDef(loadedForm);
          setFormData(normalizeFormVariables(loadedForm, taskVariables));
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
      return normalizeFormVariables(formDef, formData);
    }

    const output: Record<string, any> = {};
    variableEntries
      .filter((entry) => entry.key.trim().length > 0)
      .forEach((entry) => {
        output[entry.key.trim()] = parseVariableValue(entry);
      });

    return output;
  };

  const validateFormData = (): string | null => {
    if (!formDef) return null;

    const schema = formDef.schema;
    const properties = schema.properties || {};

    const normalizedData = normalizeFormVariables(formDef, formData);

    for (const key of schema.required || []) {
      const prop = properties[key];
      const value = normalizedData[key];
      const label = prop?.title || key;

      if (prop?.readOnly) continue;

      if (prop?.type === 'boolean') {
        if (typeof value !== 'boolean') {
          return `${label} is required.`;
        }
        continue;
      }

      if (value === undefined || value === null || value === '') {
        return `${label} is required.`;
      }

      if (Array.isArray(value) && value.length === 0) {
        return `${label} is required.`;
      }
    }

    return null;
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

    const formValidationError = validateFormData();
    if (formValidationError) {
      setError(formValidationError);
      return;
    }

    const validationError = validateVariableEntries();
    if (validationError) {
      setError(validationError);
      return;
    }

    setSubmitting(true);
    setError(null);
    setActionMessage(null);

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

  const handleSaveDraft = async () => {
    if (!task) return;

    const validationError = validateVariableEntries();
    if (validationError) {
      setError(validationError);
      return;
    }

    setSavingDraft(true);
    setError(null);
    setActionMessage(null);

    try {
      const savedTask = await bpmService.saveTaskDraft(task.id, buildCompletionVariables());
      setTask(savedTask);
      setActionMessage('Draft saved.');
    } catch (draftError) {
      setError((draftError as Error).message);
    } finally {
      setSavingDraft(false);
    }
  };

  const handleUnclaim = async () => {
    if (!task) return;

    setUnclaiming(true);
    setError(null);
    setActionMessage(null);

    try {
      const updatedTask = await bpmService.unclaimTask(task.id);
      setTask(updatedTask);
      setActionMessage('Task unclaimed.');
    } catch (unclaimError) {
      setError((unclaimError as Error).message);
    } finally {
      setUnclaiming(false);
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
  const isOwner = task.assignee === currentUser;
  const isReadOnly = isCompleted || !isOwner;

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
              <span className="text-xs font-bold text-indigo-600 tracking-wider uppercase bg-indigo-50 px-2 py-1 rounded border border-indigo-100 inline-flex items-center gap-1">
                <User size={12} /> Owner: {task.assignee || 'Unassigned'}
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

          {actionMessage && (
            <div className="p-3 rounded-lg border border-emerald-200 bg-emerald-50 text-emerald-700 text-sm flex items-center gap-2">
              <CheckCircle2 size={16} />
              {actionMessage}
            </div>
          )}

          {formDef ? (
            <div className="space-y-3">
              <p className="text-sm text-slate-500">
                This task has a form. Form values will be submitted as task outputs and synchronized as process variables on completion.
              </p>
              <DynamicForm schema={formDef.schema} initialData={formData} onChange={setFormData} disabled={isReadOnly} taskId={task.id} processInstanceId={task.processInstanceId} />
            </div>
          ) : (
            <div className="space-y-3">
              <p className="text-sm text-slate-500">
                This task has no form definition. Edit existing task variables or create new ones. All submitted variables will be available as global process variables after completion.
              </p>
              <TaskVariableEditor entries={variableEntries} onChange={setVariableEntries} disabled={isReadOnly} />
            </div>
          )}
        </div>

        {!isCompleted && (
          <div className="p-6 bg-slate-50 border-t border-slate-100 flex flex-col sm:flex-row sm:justify-between gap-3">
            <button
              onClick={handleUnclaim}
              disabled={!isOwner || unclaiming || submitting || savingDraft}
              className="px-4 py-2 bg-white border border-slate-300 rounded-lg text-slate-700 font-medium hover:bg-slate-50 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {unclaiming ? <Loader2 className="animate-spin" size={16} /> : <Unlock size={16} />}
              Unclaim Task
            </button>
            <div className="flex flex-col sm:flex-row justify-end gap-3">
              <button
                onClick={onBack}
                className="px-4 py-2 bg-white border border-slate-300 rounded-lg text-slate-700 font-medium hover:bg-slate-50 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleSaveDraft}
                disabled={isReadOnly || savingDraft || submitting || unclaiming}
                className="px-4 py-2 bg-white border border-blue-200 text-blue-700 rounded-lg font-medium hover:bg-blue-50 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {savingDraft ? <Loader2 className="animate-spin" size={16} /> : <Save size={16} />}
                Save Draft
              </button>
              <button
                onClick={handleComplete}
                disabled={isReadOnly || submitting || savingDraft || unclaiming}
                className="px-6 py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 shadow-sm shadow-blue-200 transition-all disabled:opacity-50 flex items-center justify-center gap-2"
              >
                {submitting ? <Loader2 className="animate-spin" size={16} /> : <CheckCircle2 size={16} />}
                Complete Task
              </button>
            </div>
          </div>
        )}
      </div>

      {claimNoticeOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 px-4">
          <div className="w-full max-w-sm rounded-xl bg-white border border-slate-200 shadow-xl p-5">
            <div className="flex items-start gap-3">
              <div className="p-2 rounded-lg bg-blue-50 text-blue-600">
                <CheckCircle2 size={20} />
              </div>
              <div className="flex-1">
                <h2 className="text-base font-semibold text-slate-900">Task Claimed</h2>
                <p className="text-sm text-slate-600 mt-1">This task has been claimed and assigned to you.</p>
              </div>
            </div>
            <div className="mt-5 flex justify-end">
              <button
                onClick={() => setClaimNoticeOpen(false)}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700"
              >
                OK
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default App;
