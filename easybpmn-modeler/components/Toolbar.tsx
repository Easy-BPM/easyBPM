import React from 'react';
import { Cpu, AlertTriangle } from 'lucide-react';
import { AppView } from '../types';

interface ToolbarProps {
  onClear: () => void;
  isExportDisabled?: boolean;
  validationErrors?: string[];
  validationWarnings?: string[];
  currentView: AppView;
  onViewChange: (view: AppView) => void;
}

export const Toolbar: React.FC<ToolbarProps> = ({
  onClear,
  isExportDisabled,
  validationErrors = [],
  validationWarnings = []
}) => {
  return (
    <div className="min-h-14 bg-white border-b border-slate-200 flex items-center justify-between px-6 py-2 shadow-sm z-10 relative gap-3 flex-wrap">
      <div className="flex items-center space-x-3">
        <div className="bg-blue-600 p-2 rounded-lg">
          <Cpu className="w-5 h-5 text-white" />
        </div>
        <div>
          <h1 className="text-sm font-semibold text-slate-800">Easy BPMN Modeler</h1>
        </div>
      </div>

      <div className="flex items-center gap-3 flex-wrap justify-end">
        <button 
          onClick={onClear}
          className="px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-100 rounded-md transition-colors"
        >
          New Canvas
        </button>

        {/* Validation Summary */}
        {(validationErrors.length > 0 || validationWarnings.length > 0) && (
          <div className="flex items-center gap-2">
            {validationErrors.length > 0 && (
              <div className="flex items-center gap-1 px-2 py-1 bg-red-50 rounded text-xs">
                <AlertTriangle className="w-3.5 h-3.5 text-red-500" />
                <span className="text-red-600 font-medium">{validationErrors.length} errors</span>
              </div>
            )}
            {validationWarnings.length > 0 && (
              <div className="flex items-center gap-1 px-2 py-1 bg-yellow-50 rounded text-xs">
                <AlertTriangle className="w-3.5 h-3.5 text-yellow-500" />
                <span className="text-yellow-600 font-medium">{validationWarnings.length} warnings</span>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};