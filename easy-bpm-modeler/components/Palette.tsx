import React from 'react';
import { NodeType } from '../types';
import { Bot, Circle, User, Settings, GitFork, Plus, Mail, Zap, Clock3, Layers, Code, Brain, Rows3 } from 'lucide-react';

interface PaletteProps {
  onDragStart: (event: React.DragEvent, type: NodeType) => void;
  isAgenticOrchestrationEnabled?: boolean;
}

export const Palette: React.FC<PaletteProps> = ({ onDragStart, isAgenticOrchestrationEnabled = false }) => {
  const groups: { title: string; items: { type: NodeType; label: string; icon: React.ReactNode; color: string }[] }[] = [
    {
      title: 'Participants',
      items: [
        { type: 'pool', label: 'Pool / Participant', icon: <Rows3 className="w-5 h-5" />, color: 'text-sky-600' },
      ]
    },
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
        { type: 'ai-task', label: 'Ask AI (BETA)', icon: <Brain className="w-5 h-5" />, color: 'text-pink-600' },
        { type: 'call-activity', label: 'Call Activity', icon: <Layers className="w-5 h-5" />, color: 'text-cyan-600' },
        ...(isAgenticOrchestrationEnabled
          ? [{ type: 'agent-process-call' as NodeType, label: 'Agent Process', icon: <Bot className="w-5 h-5" />, color: 'text-cyan-400' }]
          : []),
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
    <div className="w-64 bg-[#121920] border-r border-[#25313d] flex flex-col h-full z-10 shadow-[1px_0_0_rgba(255,255,255,0.04)]">
      <div className="p-4 border-b border-[#25313d] bg-[#121920]">
        <h2 className="text-sm font-bold text-slate-200 uppercase tracking-wider">Components</h2>
      </div>
      <div className="p-4 space-y-6 overflow-y-auto flex-1">
        {groups.map((group) => (
          <div key={group.title} className="space-y-2">
            <h3 className="text-[10px] font-bold text-slate-500 uppercase tracking-widest px-1">{group.title}</h3>
            <div className="grid grid-cols-1 gap-2">
              {group.items.map((item) => (
                <div
                  key={item.type}
                  draggable
                  onDragStart={(e) => onDragStart(e, item.type)}
                  className="flex items-center p-2.5 bg-white/[0.04] border border-white/[0.07] rounded-md cursor-grab hover:border-blue-500/70 hover:bg-white/[0.07] hover:shadow-[0_0_0_1px_rgba(59,130,246,0.18)] transition-all group"
                >
                  <div className={`mr-3 ${item.color} group-hover:scale-110 transition-transform flex-shrink-0`}>
                    {item.icon}
                  </div>
                  <span className="text-xs font-medium text-slate-300 truncate">{item.label}</span>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
      
      <div className="p-4 border-t border-[#25313d] bg-black/10">
        <p className="text-xs text-slate-400 leading-relaxed">
          <span className="font-semibold text-slate-300">Tip:</span> Drag components to canvas. Hover over a node to see connection points. Press <span className="font-mono bg-white/5 border border-white/10 rounded px-1 text-slate-300">Delete</span> to remove.
        </p>
      </div>
    </div>
  );
};
