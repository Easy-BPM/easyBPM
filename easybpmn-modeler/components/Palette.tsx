import React from 'react';
import { NodeType } from '../types';
import { Circle, User, Settings, GitFork, Plus, Mail, Zap, Clock3, Layers, Code } from 'lucide-react';

interface PaletteProps {
  onDragStart: (event: React.DragEvent, type: NodeType) => void;
}

export const Palette: React.FC<PaletteProps> = ({ onDragStart }) => {
  const groups: { title: string; items: { type: NodeType; label: string; icon: React.ReactNode; color: string }[] }[] = [
    {
      title: 'Events',
      items: [
        { type: 'start', label: 'Start Event', icon: <Circle className="w-5 h-5" />, color: 'text-green-600' },
        { type: 'end', label: 'End Event', icon: <Circle className="w-5 h-5 border-2 rounded-full" style={{ borderWidth: '3px' }} />, color: 'text-red-600' },
        {
          type: 'timer-event',
          label: 'Timer Event',
          icon: (
            <div className="relative w-5 h-5 flex items-center justify-center">
              <Circle className="w-5 h-5 stroke-[1px]" />
              <Circle className="w-3.5 h-3.5 absolute stroke-[1px]" />
              <Clock3 className="w-2.5 h-2.5 absolute" />
            </div>
          ),
          color: 'text-amber-600'
        },
      ]
    },
    {
      title: 'Activities',
      items: [
        { type: 'user-task', label: 'Human Task', icon: <User className="w-5 h-5" />, color: 'text-blue-600' },
        { type: 'api-task', label: 'API Task', icon: <Settings className="w-5 h-5" />, color: 'text-purple-600' },
        { type: 'service-task', label: 'Service Task', icon: <Zap className="w-5 h-5" />, color: 'text-amber-600' },
        { type: 'code-task', label: 'Code Task', icon: <Code className="w-5 h-5" />, color: 'text-indigo-600' },
        { type: 'call-activity', label: 'Call Activity', icon: <Layers className="w-5 h-5" />, color: 'text-cyan-600' },
      ]
    },
    {
      title: 'Gateways',
      items: [
        { type: 'gateway', label: 'Exclusive Gateway', icon: <GitFork className="w-5 h-5" />, color: 'text-orange-600' },
        { type: 'parallel-gateway', label: 'Parallel Gateway', icon: <Plus className="w-5 h-5" />, color: 'text-orange-600' },
      ]
    },
    {
      title: 'Messaging',
      items: [
        { 
          type: 'message-start', 
          label: 'Message Start', 
          icon: (
            <div className="relative w-5 h-5 flex items-center justify-center">
              <Circle className="w-5 h-5 stroke-[2px]" />
              <Mail className="w-2.5 h-2.5 absolute" />
            </div>
          ), 
          color: 'text-green-600' 
        },
        { 
          type: 'message-intermediate-catch', 
          label: 'Msg Catch', 
          icon: (
            <div className="relative w-5 h-5 flex items-center justify-center">
              <Circle className="w-5 h-5 stroke-[1px]" />
              <Circle className="w-3.5 h-3.5 absolute stroke-[1px]" />
              <Mail className="w-2.5 h-2.5 absolute" />
            </div>
          ), 
          color: 'text-blue-500' 
        },
        { 
          type: 'message-intermediate-throw', 
          label: 'Msg Throw', 
          icon: (
            <div className="relative w-5 h-5 flex items-center justify-center">
              <Circle className="w-5 h-5 stroke-[1px]" />
              <Circle className="w-3.5 h-3.5 absolute stroke-[1px]" />
              <Mail className="w-2.5 h-2.5 absolute fill-current" />
            </div>
          ), 
          color: 'text-blue-500' 
        },
      ]
    },
    {
      title: 'Boundary Events',
      items: [
        { 
          type: 'error-boundary', 
          label: 'Error Boundary', 
          icon: (
            <div className="relative w-5 h-5 flex items-center justify-center">
              <Circle className="w-5 h-5 stroke-[1px] border-dashed" />
              <Zap className="w-2.5 h-2.5 absolute text-red-500 fill-current" />
            </div>
          ), 
          color: 'text-slate-600' 
        },
        { 
          type: 'message-boundary', 
          label: 'Msg Boundary', 
          icon: (
            <div className="relative w-5 h-5 flex items-center justify-center">
              <Circle className="w-5 h-5 stroke-[1px] border-dashed" />
              <Mail className="w-2.5 h-2.5 absolute text-blue-500" />
            </div>
          ), 
          color: 'text-slate-600' 
        },
        {
          type: 'timer-boundary',
          label: 'Timer Boundary',
          icon: (
            <div className="relative w-5 h-5 flex items-center justify-center">
              <Circle className="w-5 h-5 stroke-[1px] border-dashed" />
              <Clock3 className="w-2.5 h-2.5 absolute text-amber-600" />
            </div>
          ),
          color: 'text-slate-600'
        },
      ]
    }
  ];

  return (
    <div className="w-56 bg-white border-r border-slate-200 flex flex-col h-full z-10">
      <div className="px-4 py-3 border-b border-slate-200">
        <h2 className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Components</h2>
      </div>
      <div className="px-3 py-3 space-y-5 overflow-y-auto flex-1">
        {groups.map((group) => (
          <div key={group.title} className="space-y-1.5">
            <h3 className="text-[9px] font-bold text-slate-400 uppercase tracking-widest px-1 mb-2">{group.title}</h3>
            <div className="grid grid-cols-1 gap-1">
              {group.items.map((item) => (
                <div
                  key={item.type}
                  draggable
                  onDragStart={(e) => onDragStart(e, item.type)}
                  className="flex items-center px-2.5 py-2 bg-slate-50 border border-slate-200 rounded-lg cursor-grab hover:border-blue-300 hover:bg-blue-50 hover:shadow-sm transition-all group"
                >
                  <div className={`mr-2.5 ${item.color} flex-shrink-0`}>
                    {item.icon}
                  </div>
                  <span className="text-xs font-medium text-slate-700 group-hover:text-blue-700 truncate transition-colors">{item.label}</span>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
      
      <div className="px-4 py-3 border-t border-slate-100 bg-slate-50">
        <p className="text-[10px] text-slate-400 leading-relaxed">
          Drag to canvas · Hover to connect · <span className="font-mono bg-white border border-slate-200 rounded px-1 text-slate-500">Del</span> to remove
        </p>
      </div>
    </div>
  );
};