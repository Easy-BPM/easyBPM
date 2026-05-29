import React from 'react';
import { Cpu, AlertTriangle } from 'lucide-react';
import { AppView } from '../types';
import { controlButtonClass, controlButtonPrimaryClass } from '../../shared/design-system/classes';

interface ToolbarProps {
  onClear: () => void;
  isExportDisabled?: boolean;
  validationErrors?: string[];
  validationWarnings?: string[];
  currentView: AppView;
  onViewChange: (view: AppView) => void;
  compactMode?: boolean;
  onToggleCompact?: () => void;
}

export const Toolbar: React.FC<ToolbarProps> = ({
  onClear,
  isExportDisabled,
  validationErrors = [],
  validationWarnings = [],
  compactMode,
  onToggleCompact
}) => {
  return (
    <div className="min-h-14 bg-[#11161f] border-b border-[#2d3748] flex items-center justify-between px-5 py-2 z-10 relative gap-3 flex-wrap">
      <div className="flex items-center space-x-3">
        <div className="bg-[#7c8cff] p-2 rounded-md border border-[#95a2ff]/40">
          <Cpu className="w-5 h-5 text-white" />
        </div>
        <div>
          <h1 className="text-sm font-semibold text-white">Process Modeler</h1>
        </div>
      </div>

      <div className="flex items-center gap-3 flex-wrap justify-end">
        <button 
          onClick={onClear}
          className={`${controlButtonClass} text-xs`}
        >
          New Canvas
        </button>
        {onToggleCompact && (
          <button aria-label="Toggle compact mode" onClick={onToggleCompact} className={compactMode ? controlButtonPrimaryClass : controlButtonClass}>
            {compactMode ? 'Compact Mode On' : 'Compact Mode Off'}
          </button>
        )}

        {(validationErrors.length > 0 || validationWarnings.length > 0) && (
          <div className="flex items-center gap-2">
            {validationErrors.length > 0 && (
              <div className="flex items-center gap-1 px-2 py-1 bg-[rgba(229,106,106,0.14)] border border-[rgba(229,106,106,0.34)] rounded text-xs">
                <AlertTriangle className="w-3.5 h-3.5 text-[#e56a6a]" />
                <span className="text-[#e56a6a] font-medium">{validationErrors.length} errors</span>
              </div>
            )}
            {validationWarnings.length > 0 && (
              <div className="flex items-center gap-1 px-2 py-1 bg-[rgba(242,184,75,0.14)] border border-[rgba(242,184,75,0.34)] rounded text-xs">
                <AlertTriangle className="w-3.5 h-3.5 text-[#f2b84b]" />
                <span className="text-[#f2b84b] font-medium">{validationWarnings.length} warnings</span>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};