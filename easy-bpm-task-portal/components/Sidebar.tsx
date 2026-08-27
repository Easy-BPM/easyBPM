
import React from 'react';
import { LayoutDashboard, CheckSquare, PlayCircle, LogOut, SquareStack } from 'lucide-react';
import { ThemeMode, ThemeToggle } from './ThemeToggle';

interface SidebarProps {
  currentView: string;
  onChangeView: (view: string) => void;
  currentUser: string;
  onLogout: () => void;
  theme: ThemeMode;
  onToggleTheme: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ currentView, onChangeView, currentUser, onLogout, theme, onToggleTheme }) => {
  const menuItems = [
    { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { id: 'inbox', label: 'My Inbox', icon: CheckSquare },
    { id: 'processes', label: 'Start Process', icon: PlayCircle },
  ];

  return (
    <aside className="app-sidebar w-64 bg-slate-950 text-white flex flex-col h-screen sticky top-0 border-r border-slate-800">
      {/* Brand header */}
      <div className="px-5 py-5 border-b border-slate-800/80">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded bg-blue-600 flex items-center justify-center flex-shrink-0">
            <SquareStack size={16} className="text-white" />
          </div>
          <div>
            <h1 className="text-sm font-semibold tracking-tight text-white leading-none">Easy BPM</h1>
            <p className="text-[10px] text-slate-500 mt-1 leading-none">Task Portal</p>
          </div>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-4 space-y-0.5 overflow-y-auto">
        <p className="text-[10px] font-semibold uppercase tracking-widest text-slate-500 px-3 mb-3">Work</p>
        {menuItems.map((item) => {
          const Icon = item.icon;
          const active = currentView === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onChangeView(item.id)}
              className={`flex items-center w-full px-3 py-2.5 rounded text-sm font-medium transition-all relative ${
                active
                  ? 'bg-slate-900 text-white border border-slate-700'
                  : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200 border border-transparent'
              }`}
            >
              {active && (
                <span className="absolute left-0 top-0 h-full w-0.5 bg-blue-500" />
              )}
              <Icon size={16} className={`mr-3 flex-shrink-0 ${active ? 'text-blue-400' : ''}`} />
              {item.label}
            </button>
          );
        })}
      </nav>

      {/* User footer */}
      <div className="px-3 py-3 border-t border-slate-800/80">
        <div className="flex items-center gap-3 px-2 py-2 rounded border border-slate-800 bg-slate-950/80">
          <div className="w-7 h-7 rounded bg-slate-800 border border-slate-700 flex items-center justify-center text-[10px] font-semibold flex-shrink-0">
            {currentUser.substring(0, 2).toUpperCase()}
          </div>
          <div className="flex-1 overflow-hidden">
            <p className="text-xs font-semibold text-slate-200 truncate">{currentUser}</p>
            <p className="text-[10px] text-slate-500 leading-none mt-0.5">Task Operator</p>
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
