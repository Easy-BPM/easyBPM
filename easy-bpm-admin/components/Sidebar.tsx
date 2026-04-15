import React from 'react';
import { LayoutDashboard, Workflow, Search, LogOut } from 'lucide-react';

interface SidebarProps {
  currentView: string;
  onChangeView: (view: string) => void;
  currentUser: string;
  onLogout: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ currentView, onChangeView, currentUser, onLogout }) => {
  const menuItems = [
    { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { id: 'instances', label: 'Instance Search', icon: Search },
    { id: 'workflows', label: 'Deployed Workflows', icon: Workflow }
  ];

  return (
    <aside className="w-72 bg-slate-900 text-white flex flex-col h-screen sticky top-0">
      <div className="p-6 border-b border-slate-800">
        <h1 className="text-xl font-bold tracking-tight text-blue-400">Easy Admin</h1>
        <p className="text-xs text-slate-400 mt-1">Process Operations Console</p>
      </div>

      <nav className="flex-1 p-4 space-y-2">
        {menuItems.map((item) => {
          const Icon = item.icon;
          const active = currentView === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onChangeView(item.id)}
              className={`flex items-center w-full px-4 py-3 rounded-lg text-sm font-medium transition-colors ${
                active ? 'bg-blue-600 text-white shadow-lg shadow-blue-900/50' : 'text-slate-400 hover:bg-slate-800 hover:text-white'
              }`}
            >
              <Icon size={18} className="mr-3" />
              {item.label}
            </button>
          );
        })}
      </nav>

      <div className="p-4 border-t border-slate-800">
        <div className="flex items-center justify-between px-2 py-2">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-full bg-indigo-500 flex items-center justify-center text-xs font-bold shadow-md shadow-indigo-500/30">
              {currentUser.substring(0, 2).toUpperCase()}
            </div>
            <div className="overflow-hidden">
              <p className="text-sm font-medium text-white truncate max-w-[90px]">{currentUser}</p>
              <p className="text-xs text-slate-500">Admin session</p>
            </div>
          </div>
          <button
            onClick={onLogout}
            className="text-slate-500 hover:text-red-400 transition-colors p-1.5 hover:bg-slate-800 rounded-md"
            title="Logout"
          >
            <LogOut size={16} />
          </button>
        </div>
      </div>
    </aside>
  );
};