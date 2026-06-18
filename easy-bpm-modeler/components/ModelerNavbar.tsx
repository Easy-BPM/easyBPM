import React from 'react';
import { ArrowLeft, Save, Download, Upload } from 'lucide-react';
import { ThemeMode, ThemeToggle } from './ThemeToggle';

interface NavbarProps {
  title: string;
  subtitle?: string;
  resourceType: 'process' | 'form';
  onBack: () => void;
  onSave?: () => void;
  onExport?: () => void;
  onImport?: (data: any) => void;
  isSaving?: boolean;
  hasUnsavedChanges?: boolean;
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
  isSaving,
  hasUnsavedChanges,
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
        const json = JSON.parse(event.target?.result as string);
        onImport?.(json);
      } catch (err) {
        alert('Invalid JSON file');
      }
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  return (
    <div className={`${isModeler ? 'modeler-navbar' : 'bg-white/5 border-white/10'} backdrop-blur-sm border-b px-6 py-4 flex items-center justify-between`}>
      {/* Left: Back button and title */}
      <div className="flex items-center gap-4">
        <button
          onClick={onBack}
          className="text-slate-400 hover:text-white hover:bg-white/10 p-2 rounded-lg transition-colors"
          title="Back to welcome screen"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>

        <div className="flex-1">
          <div className="flex items-center gap-2">
            {resourceType === 'process' ? (
              <div className="w-2 h-2 bg-blue-500 rounded-full"></div>
            ) : (
              <div className="w-2 h-2 bg-emerald-500 rounded-full"></div>
            )}
            <h1 className={`text-lg font-semibold ${isModeler ? 'modeler-heading' : 'text-black'}`}>{title}</h1>
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

      {/* Right: Action buttons */}
      <div className="flex items-center gap-3">
        <ThemeToggle theme={theme} onToggle={onToggleTheme} />
        {onImport && (
          <>
            <input 
              type="file" 
              ref={fileInputRef} 
              onChange={handleFileChange} 
              accept=".json" 
              className="hidden" 
            />
            <button
              onClick={() => fileInputRef.current?.click()}
              className={`px-3 py-2 rounded-lg transition-colors flex items-center gap-2 ${isModeler ? 'text-slate-300 hover:bg-white/10 hover:text-white' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'}`}
              title="Import process from JSON"
            >
              <Upload className="w-4 h-4" />
              <span className="text-sm font-medium">Import</span>
            </button>
          </>
        )}
        {onExport && (
          <button
            onClick={onExport}
            className={`px-3 py-2 rounded-lg transition-colors flex items-center gap-2 ${isModeler ? 'text-slate-300 hover:bg-white/10 hover:text-white' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'}`}
            title="Export process as JSON"
          >
            <Download className="w-4 h-4" />
            <span className="text-sm font-medium">Export</span>
          </button>
        )}
        {onSave && (
          <button
            onClick={onSave}
            disabled={isSaving}
            className={`px-4 py-2 rounded-lg font-medium transition-all flex items-center gap-2 ${
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
