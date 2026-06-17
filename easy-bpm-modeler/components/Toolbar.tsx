import React from 'react';
import { Cpu, AlertTriangle } from 'lucide-react';
import { AppView } from '../types';
import { ThemeMode, ThemeToggle } from './ThemeToggle';

interface ToolbarProps {
  onClear: () => void;
  isExportDisabled?: boolean;
  validationErrors?: string[];
  validationWarnings?: string[];
  currentView: AppView;
  onViewChange: (view: AppView) => void;
  theme: ThemeMode;
  onToggleTheme: () => void;
}

export const Toolbar: React.FC<ToolbarProps> = ({
  onClear,
  isExportDisabled,
  validationErrors = [],
  validationWarnings = [],
  theme,
  onToggleTheme
}) => {
  return (
    <div className="modeler-toolbar min-h-14 border-b flex items-center justify-between px-6 py-2 z-10 relative gap-3 flex-wrap">
      <div className="flex items-center space-x-3">
        <div className="bg-blue-600 p-2 rounded-lg shadow-[0_0_24px_rgba(37,99,235,0.35)]">
          <Cpu className="w-5 h-5 text-white" />
        </div>
        <div>
          <h1 className="text-sm font-semibold modeler-heading">Easy BPMN Modeler</h1>
        </div>
      </div>

      <div className="flex items-center gap-3 flex-wrap justify-end">
        <ThemeToggle theme={theme} onToggle={onToggleTheme} />
        <button 
          onClick={onClear}
          className="modeler-ghost-button px-3 py-1.5 text-xs font-medium rounded-md transition-colors"
        >
          New Canvas
        </button>

        {/* Validation Summary */}
        {(validationErrors.length > 0 || validationWarnings.length > 0) && (
          <div className="flex items-center gap-2">
            {validationErrors.length > 0 && (
              <div className="flex items-center gap-1 px-2 py-1 bg-red-500/10 border border-red-500/30 rounded text-xs">
                <AlertTriangle className="w-3.5 h-3.5 text-red-500" />
                <span className="text-red-400 font-medium">{validationErrors.length} errors</span>
              </div>
            )}
            {validationWarnings.length > 0 && (
              <div className="flex items-center gap-1 px-2 py-1 bg-amber-500/10 border border-amber-500/30 rounded text-xs">
                <AlertTriangle className="w-3.5 h-3.5 text-yellow-500" />
                <span className="text-amber-400 font-medium">{validationWarnings.length} warnings</span>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
