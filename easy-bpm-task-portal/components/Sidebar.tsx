
import React from 'react';
import { LayoutDashboard, CheckSquare, PlayCircle, LogOut, Zap } from 'lucide-react';
import { controlButtonDangerClass, controlPanelMutedClass } from '../../shared/design-system/classes';

interface SidebarProps {
  currentView: string;
  onChangeView: (view: string) => void;
  currentUser: string;
  onLogout: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ currentView, onChangeView, currentUser, onLogout }) => {
  const menuItems = [
    { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { id: 'inbox', label: 'My Inbox', icon: CheckSquare },
    { id: 'processes', label: 'Start Process', icon: PlayCircle },
  ];

  return (
    <aside className="w-72 bg-[#11161f] text-white flex flex-col h-screen sticky top-0 border-r border-[#2d3748]">
      <div className="px-5 py-4 border-b border-[#2d3748]">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-md bg-[#7c8cff] flex items-center justify-center flex-shrink-0 border border-[#95a2ff]/40">
            <Zap size={16} className="text-white" />
          </div>
          <div>
            <h1 className="text-sm font-semibold tracking-tight text-white leading-none">EasyBPM Control Suite</h1>
            <p className="text-[11px] text-[#7b869b] mt-1 leading-none">Task Portal</p>
          </div>
        </div>
      </div>

      <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-[#7b869b] px-3 mb-3">Navigation</p>
        {menuItems.map((item) => {
          const Icon = item.icon;
          const active = currentView === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onChangeView(item.id)}
              className={`flex items-center w-full px-3 py-2.5 rounded-md text-sm font-medium transition-all relative border ${
                active
                  ? 'bg-[#1b2331] text-[#e6eaf2] border-[#364257]'
                  : 'text-[#a8b2c5] hover:bg-[#171d28] hover:text-white border-transparent'
              }`}
            >
              {active && (
                <span className="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 bg-[#7c8cff]" />
              )}
              <Icon size={16} className={`mr-3 flex-shrink-0 ${active ? 'text-[#7c8cff]' : 'text-[#7b869b]'}`} />
              {item.label}
            </button>
          );
        })}
      </nav>

      <div className="px-3 py-3 border-t border-[#2d3748]">
        <div className={`${controlPanelMutedClass} flex items-center gap-3 px-3 py-3 shadow-none`}>
          <div className="w-8 h-8 rounded-md bg-[#232b3a] border border-[#364257] flex items-center justify-center text-[10px] font-bold flex-shrink-0 text-[#e6eaf2]">
            {currentUser.substring(0, 2).toUpperCase()}
          </div>
          <div className="flex-1 overflow-hidden">
            <p className="text-xs font-semibold text-[#e6eaf2] truncate">{currentUser}</p>
            <p className="text-[11px] text-[#7b869b] leading-none mt-1">Task Operator</p>
          </div>
          <button
            onClick={onLogout}
            className={`${controlButtonDangerClass} min-h-0 h-8 w-8 p-0 flex-shrink-0`}
            title="Sign out"
          >
            <LogOut size={14} />
          </button>
        </div>
      </div>
    </aside>
  );
};
