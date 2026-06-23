import React from 'react';
import { AlertTriangle, Database, LayoutDashboard, Workflow, Search, LogOut, Code2, Zap, UserRoundCheck } from 'lucide-react';
import { ThemeMode, ThemeToggle } from './ThemeToggle';

interface SidebarProps {
  currentView: string;
  onChangeView: (view: string) => void;
  currentUser: string;
  permissions: string[];
  onLogout: () => void;
  theme: ThemeMode;
  onToggleTheme: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ currentView, onChangeView, currentUser, permissions, onLogout, theme, onToggleTheme }) => {
  const canManageSecurity = permissions.includes('MANAGE_USERS') || permissions.includes('MANAGE_GROUPS');
  const menuItems = [
    { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { id: 'instances', label: 'Instance Search', icon: Search },
    { id: 'task-resources', label: 'Task Resources', icon: UserRoundCheck },
    { id: 'incidents', label: 'Incidents', icon: AlertTriangle },
    { id: 'workflows', label: 'Deployed Workflows', icon: Workflow },
    { id: 'code-tasks', label: 'Code Task Executions', icon: Code2 },
    { id: 'maintenance', label: 'Maintenance', icon: Database },
    ...(canManageSecurity ? [{ id: 'security-admin', label: 'Security', icon: LayoutDashboard }] : [])
  ];

  return (
    <aside className="app-sidebar w-64 bg-slate-950 text-white flex flex-col h-screen sticky top-0 border-r border-slate-800">
      {/* Brand header */}
      <div className="px-5 py-5 border-b border-slate-800/80">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center flex-shrink-0 shadow-lg shadow-blue-600/30">
            <Zap size={16} className="text-white" />
          </div>
          <div>
            <h1 className="text-sm font-bold tracking-tight text-white leading-none">Easy BPM Admin</h1>
            <p className="text-[10px] text-slate-500 mt-0.5 leading-none">Process Operations Console</p>
          </div>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-4 space-y-0.5 overflow-y-auto">
        <p className="text-[9px] font-bold uppercase tracking-widest text-slate-600 px-3 mb-3">Navigation</p>
        {menuItems.map((item) => {
          const Icon = item.icon;
          const active = currentView === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onChangeView(item.id)}
              className={`flex items-center w-full px-3 py-2.5 rounded-lg text-sm font-medium transition-all relative ${
                active
                  ? 'bg-blue-600/15 text-blue-400 border border-blue-600/20'
                  : 'text-slate-400 hover:bg-slate-800/60 hover:text-slate-200 border border-transparent'
              }`}
            >
              {active && (
                <span className="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 bg-blue-500 rounded-r-full" />
              )}
              <Icon size={16} className={`mr-3 flex-shrink-0 ${active ? 'text-blue-400' : ''}`} />
              {item.label}
            </button>
          );
        })}
      </nav>

      {/* User footer */}
      <div className="px-3 py-3 border-t border-slate-800/80">
        <div className="flex items-center gap-3 px-2 py-2 rounded-lg hover:bg-slate-800/50 transition-colors">
          <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-[10px] font-bold flex-shrink-0 shadow-md">
            {currentUser.substring(0, 2).toUpperCase()}
          </div>
          <div className="flex-1 overflow-hidden">
            <p className="text-xs font-semibold text-slate-200 truncate">{currentUser}</p>
            <p className="text-[10px] text-slate-500 leading-none mt-0.5">Administrator</p>
          </div>
          <button
            onClick={onLogout}
            className="text-slate-600 hover:text-red-400 transition-colors p-1 hover:bg-slate-800 rounded-md flex-shrink-0"
            title="Sign out"
          >
            <LogOut size={14} />
          </button>
          <ThemeToggle theme={theme} onToggle={onToggleTheme} />
        </div>
      </div>
    </aside>
  );
};
