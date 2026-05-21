import React from 'react';
import { Download, Upload, Cpu, AlertTriangle, Layout, FileText, Rocket } from 'lucide-react';
import { AppView } from '../types';

interface ToolbarProps {
  onClear: () => void;
  onExport: () => void;
  onDeploy: () => void;
  onImport: (data: any) => void;
  isExportDisabled?: boolean;
  validationErrors?: string[];
  validationWarnings?: string[];
  isDeploying?: boolean;
  currentView: AppView;
  onViewChange: (view: AppView) => void;
}

export const Toolbar: React.FC<ToolbarProps> = ({
  onClear,
  onExport,
  onDeploy,
  onImport,
  isExportDisabled,
  validationErrors = [],
  validationWarnings = [],
  isDeploying,
  currentView,
  onViewChange
}) => {
  const fileInputRef = React.useRef<HTMLInputElement>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const json = JSON.parse(event.target?.result as string);
        onImport(json);
      } catch (err) {
        alert('Invalid JSON file');
      }
    };
    reader.readAsText(file);
    // Reset input so the same file can be picked again
    e.target.value = '';
  };

  return (
    <div className="min-h-16 bg-white border-b border-slate-200 flex items-center justify-between px-6 py-2 z-10 relative gap-3 flex-wrap" style={{ boxShadow: '0 1px 3px 0 rgba(0,0,0,0.06), 0 1px 2px -1px rgba(0,0,0,0.04)' }}>
      <div className="flex items-center space-x-3">
        <div className="bg-gradient-to-br from-blue-500 to-indigo-600 p-2 rounded-lg shadow-md">
          <Cpu className="w-5 h-5 text-white" />
        </div>
        <div>
          <h1 className="text-base font-bold text-slate-800 leading-tight">Easy BPMN Modeler</h1>
          <p className="text-[10px] text-slate-400 font-medium tracking-wide uppercase">Process Automation Studio</p>
        </div>
      </div>

      <div className="flex items-center gap-2 flex-wrap justify-end">
        <input 
          type="file" 
          ref={fileInputRef} 
          onChange={handleFileChange} 
          accept=".json" 
          className="hidden" 
        />
        <button 
          onClick={onClear}
          className="px-3 py-1.5 text-sm font-medium text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-md transition-colors border border-transparent hover:border-slate-200"
        >
          New
        </button>
        <div className="h-5 w-px bg-slate-200 mx-1" />
        <div className="flex bg-slate-100 p-1 rounded-lg">
          <button 
            onClick={() => onViewChange('bpmn')}
            className={`flex items-center space-x-2 px-3 py-1.5 rounded-md text-sm font-medium transition-all ${
              currentView === 'bpmn' ? 'bg-white text-blue-600 shadow-sm ring-1 ring-slate-200/60' : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            <Layout className="w-4 h-4" />
            <span>Process Modeler</span>
          </button>
          <button 
            onClick={() => onViewChange('forms')}
            className={`flex items-center space-x-2 px-3 py-1.5 rounded-md text-sm font-medium transition-all ${
              currentView === 'forms' ? 'bg-white text-blue-600 shadow-sm ring-1 ring-slate-200/60' : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            <FileText className="w-4 h-4" />
            <span>Form Modeler</span>
          </button>
        </div>
        <div className="h-5 w-px bg-slate-200 mx-1" />
        <button 
          onClick={() => fileInputRef.current?.click()}
          className="flex items-center space-x-2 px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-100 rounded-md transition-colors border border-slate-200 hover:border-slate-300"
        >
          <Upload className="w-4 h-4" />
          <span>Import</span>
        </button>
        <div className="relative group">
          <div className="flex items-center gap-2">
            <button
              onClick={onDeploy}
              disabled={isExportDisabled || isDeploying}
              className={`flex items-center space-x-2 px-4 py-1.5 text-sm font-semibold rounded-md transition-all ${
                isExportDisabled || isDeploying
                  ? 'bg-slate-100 text-slate-400 cursor-not-allowed border border-slate-200'
                  : 'bg-gradient-to-r from-emerald-500 to-emerald-600 hover:from-emerald-600 hover:to-emerald-700 text-white shadow-sm shadow-emerald-200 border border-transparent'
              }`}
            >
              <Rocket className="w-4 h-4" />
              <span>{isDeploying ? 'Deploying...' : 'Deploy'}</span>
            </button>
            <button 
              onClick={onExport}
              disabled={isExportDisabled}
              className={`flex items-center space-x-2 px-3 py-1.5 text-sm font-medium rounded-md transition-all ${
                isExportDisabled 
                  ? 'bg-slate-50 text-slate-400 cursor-not-allowed border border-slate-200' 
                  : 'text-slate-600 hover:bg-slate-50 bg-white border border-slate-200 hover:border-slate-300'
              }`}
            >
              <Download className="w-4 h-4" />
              <span>Export</span>
              {isExportDisabled && <AlertTriangle className="w-3.5 h-3.5 text-orange-500" />}
            </button>
          </div>
          {isExportDisabled && (
            <div className="absolute top-full right-0 mt-2 p-3 bg-slate-800 text-white text-[10px] rounded shadow-xl opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-50 w-80 leading-tight space-y-2">
              <p className="font-semibold">Resolve validation errors before deploy/export.</p>
              {validationErrors.length > 0 && (
                <div>
                  <p className="text-red-300 font-semibold mb-1">Errors</p>
                  {validationErrors.slice(0, 4).map((err, idx) => (
                    <p key={`${idx}-${err}`}>- {err}</p>
                  ))}
                </div>
              )}
              {validationWarnings.length > 0 && (
                <div>
                  <p className="text-amber-300 font-semibold mb-1">Warnings</p>
                  {validationWarnings.slice(0, 2).map((warn, idx) => (
                    <p key={`${idx}-${warn}`}>- {warn}</p>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};