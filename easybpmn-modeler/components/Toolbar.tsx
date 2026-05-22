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
    <div className="min-h-16 bg-white border-b border-slate-200 flex items-center justify-between px-6 py-2 shadow-sm z-10 relative gap-3 flex-wrap">
      <div className="flex items-center space-x-3">
        <div className="bg-blue-600 p-2 rounded-lg">
          <Cpu className="w-6 h-6 text-white" />
        </div>
        <div>
          <h1 className="text-lg font-bold text-slate-800">Easy BPMN Modeler</h1>
          <p className="text-xs text-slate-500">Professional Process Automation</p>
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
          className="px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 rounded-md transition-colors"
        >
          New
        </button>
        <div className="h-6 w-px bg-slate-300 mx-2" />
        <div className="flex bg-slate-100 p-1 rounded-lg">
          <button 
            onClick={() => onViewChange('bpmn')}
            className={`flex items-center space-x-2 px-3 py-1.5 rounded-md text-sm font-medium transition-all ${
              currentView === 'bpmn' ? 'bg-white text-blue-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            <Layout className="w-4 h-4" />
            <span>Process Modeler</span>
          </button>
          <button 
            onClick={() => onViewChange('forms')}
            className={`flex items-center space-x-2 px-3 py-1.5 rounded-md text-sm font-medium transition-all ${
              currentView === 'forms' ? 'bg-white text-blue-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            <FileText className="w-4 h-4" />
            <span>Form Modeler</span>
          </button>
        </div>
        <div className="h-6 w-px bg-slate-300 mx-2" />
        <button 
          onClick={() => fileInputRef.current?.click()}
          className="flex items-center space-x-2 px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 rounded-md transition-colors"
        >
          <Upload className="w-4 h-4" />
          <span>Import</span>
        </button>
        <div className="flex items-center gap-2">
          <button
            onClick={onDeploy}
            disabled={isExportDisabled || isDeploying}
            className={`flex items-center space-x-2 px-4 py-2 text-sm font-medium rounded-md transition-all ${
              isExportDisabled || isDeploying
                ? 'bg-slate-100 text-slate-400 cursor-not-allowed border border-slate-200'
                : 'bg-emerald-600 hover:bg-emerald-700 text-white border border-transparent'
            }`}
          >
            <Rocket className="w-4 h-4" />
            <span>{isDeploying ? 'Deploying...' : 'Deploy Process'}</span>
          </button>
          <button 
            onClick={onExport}
            disabled={isExportDisabled}
            className={`flex items-center space-x-2 px-4 py-2 text-sm font-medium rounded-md transition-all ${
              isExportDisabled 
                ? 'bg-slate-100 text-slate-400 cursor-not-allowed border border-slate-200' 
                : 'text-slate-600 hover:bg-slate-100 bg-white border border-transparent'
            }`}
          >
            <Download className="w-4 h-4" />
            <span>Export JSON</span>
            {isExportDisabled && <AlertTriangle className="w-3.5 h-3.5 text-orange-500" />}
          </button>
        </div>
      </div>
    </div>
  );
};