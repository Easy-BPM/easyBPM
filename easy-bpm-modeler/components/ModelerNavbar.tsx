import React from 'react';
import { AlertTriangle, ArrowLeft, Download, FilePlus2, Save, Upload } from 'lucide-react';
import { ThemeMode, ThemeToggle } from './ThemeToggle';
import { AppView } from '../types';

interface NavbarProps {
  title: string;
  subtitle?: string;
  resourceType: 'process' | 'form';
  onBack: () => void;
  onSave?: () => void;
  onExport?: () => void;
  onImport?: (data: any) => void;
  onCreateNew?: () => void;
  isSaving?: boolean;
  hasUnsavedChanges?: boolean;
  validationErrors?: string[];
  validationWarnings?: string[];
  currentView?: AppView;
  onViewChange?: (view: AppView) => void;
  currentUser?: string | null;
  onLogout?: () => void;
  theme: ThemeMode;
  onToggleTheme: () => void;
}

export const ModelerNavbar: React.FC<NavbarProps> = ({
  title,
  subtitle,
  resourceType,
  onBack,
  onSave,
  onExport,
  onImport,
  onCreateNew,
  isSaving,
  hasUnsavedChanges,
  validationErrors = [],
  validationWarnings = [],
  currentView,
  onViewChange,
  theme,
  onToggleTheme
}) => {
  const fileInputRef = React.useRef<HTMLInputElement>(null);
  const isModeler = resourceType === 'process' || resourceType === 'form';

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const text = event.target?.result as string;
        if (resourceType === 'process') {
          if (!text.trimStart().startsWith('<')) {
            throw new Error('Process imports must be BPMN XML');
          }
          onImport?.(text);
          return;
        }
        onImport?.(JSON.parse(text));
      } catch (err) {
        alert(resourceType === 'process' ? 'Invalid BPMN XML file' : 'Invalid JSON file');
      }
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  return (
    <div className={`${isModeler ? 'modeler-navbar' : 'bg-white/5 border-white/10'} backdrop-blur-sm border-b px-6 py-3 flex items-center justify-between gap-4`}>
      {/* Left: Back button and title */}
      <div className="flex min-w-0 items-center gap-4">
        <button
          onClick={onBack}
          className="modeler-ghost-button rounded-md p-2 transition-colors"
          title="Back to welcome screen"
          aria-label="Back to workspace"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>

        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            {resourceType === 'process' ? (
              <div className="h-2 w-2 shrink-0 rounded-full bg-blue-500"></div>
            ) : (
              <div className="h-2 w-2 shrink-0 rounded-full bg-emerald-500"></div>
            )}
            <h1 className={`truncate text-base font-semibold ${isModeler ? 'modeler-heading' : 'text-black'}`}>{title}</h1>
            {hasUnsavedChanges && (
              <span className="text-xs px-2 py-1 bg-yellow-500/20 text-yellow-400 rounded">
                Unsaved
              </span>
            )}
          </div>
          {subtitle && (
            <p className={`text-xs mt-0.5 ${isModeler ? 'modeler-muted' : 'text-slate-500'}`}>{subtitle}</p>
          )}
        </div>
      </div>

      {/* Right: Process/form controls */}
      <div className="flex shrink-0 items-center gap-2">
        {currentView && onViewChange && (
          <div className="inline-flex rounded-md border border-[var(--modeler-border)] bg-[var(--modeler-surface-muted)] p-0.5 text-xs">
            <button
              type="button"
              onClick={() => onViewChange('bpmn')}
              className={`rounded px-3 py-1.5 font-semibold transition-colors ${currentView === 'bpmn' ? 'bg-blue-600 text-white shadow-sm' : 'text-[var(--modeler-text-soft)] hover:text-[var(--modeler-text)]'}`}
            >
              BPMN
            </button>
            <button
              type="button"
              onClick={() => onViewChange('xml')}
              className={`rounded px-3 py-1.5 font-semibold transition-colors ${currentView === 'xml' ? 'bg-blue-600 text-white shadow-sm' : 'text-[var(--modeler-text-soft)] hover:text-[var(--modeler-text)]'}`}
            >
              XML
            </button>
          </div>
        )}

        {(validationErrors.length > 0 || validationWarnings.length > 0) && (
          <div className="flex items-center gap-1">
            {validationErrors.length > 0 && (
              <div className="flex items-center gap-1 rounded border border-red-500/30 bg-red-500/10 px-2 py-1 text-xs">
                <AlertTriangle className="h-3.5 w-3.5 text-red-500" />
                <span className="font-medium text-red-500">{validationErrors.length} errors</span>
              </div>
            )}
            {validationWarnings.length > 0 && (
              <div className="flex items-center gap-1 rounded border border-amber-500/30 bg-amber-500/10 px-2 py-1 text-xs">
                <AlertTriangle className="h-3.5 w-3.5 text-amber-500" />
                <span className="font-medium text-amber-500">{validationWarnings.length} warnings</span>
              </div>
            )}
          </div>
        )}

        {onCreateNew && (
          <button
            type="button"
            onClick={onCreateNew}
            className="modeler-ghost-button inline-flex items-center gap-2 rounded-md px-3 py-2 text-xs font-semibold transition-colors"
            title="Start a new process definition"
          >
            <FilePlus2 className="h-4 w-4" />
            New Process
          </button>
        )}

        {onImport && (
          <>
            <input 
              type="file" 
              ref={fileInputRef} 
              onChange={handleFileChange} 
              accept={resourceType === 'process' ? '.bpmn,.xml,application/xml,text/xml' : '.json,application/json'}
              className="hidden" 
            />
            <button
              onClick={() => fileInputRef.current?.click()}
              className="modeler-ghost-button flex items-center gap-2 rounded-md px-3 py-2 text-xs font-semibold transition-colors"
              title={resourceType === 'process' ? 'Import process from BPMN XML' : 'Import from JSON'}
            >
              <Upload className="w-4 h-4" />
              <span>Import</span>
            </button>
          </>
        )}
        {onExport && (
          <button
            onClick={onExport}
            className="modeler-ghost-button flex items-center gap-2 rounded-md px-3 py-2 text-xs font-semibold transition-colors"
            title={resourceType === 'process' ? 'Export process as BPMN XML' : 'Export as JSON'}
          >
            <Download className="w-4 h-4" />
            <span>Export</span>
          </button>
        )}
        <ThemeToggle theme={theme} onToggle={onToggleTheme} />
        {onSave && (
          <button
            onClick={onSave}
            disabled={isSaving}
            className={`flex items-center gap-2 rounded-md px-4 py-2 text-sm font-semibold transition-all ${
              resourceType === 'process'
                ? 'bg-blue-600 hover:bg-blue-500 text-white disabled:opacity-50'
                : 'bg-emerald-600 hover:bg-emerald-500 text-white disabled:opacity-50'
            }`}
          >
            {isSaving ? (
              <>
                <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                Deploying...
              </>
            ) : (
              <>
                <Save className="w-4 h-4" />
                Deploy
              </>
            )}
          </button>
        )}
      </div>
    </div>
  );
};
