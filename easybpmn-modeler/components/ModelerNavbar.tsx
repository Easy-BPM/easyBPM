import React from 'react';
import { ArrowLeft, Home, Save, Eye, Code, Download, Upload, User, LogOut } from 'lucide-react';

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
  currentUser,
  onLogout
}) => {
  const [showUserMenu, setShowUserMenu] = React.useState(false);
  const fileInputRef = React.useRef<HTMLInputElement>(null);
  const accentColor = resourceType === 'process' ? 'blue' : 'emerald';
  const accentClass = resourceType === 'process' ? 'text-blue-400' : 'text-emerald-400';

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
    <div className="bg-white/5 backdrop-blur-sm border-b border-white/10 px-6 py-4 flex items-center justify-between">
      {/* Left: Back button and title */}
      <div className="flex items-center gap-4">
        <button
          onClick={onBack}
          className="p-2 hover:bg-white/10 rounded-lg transition-colors text-slate-400 hover:text-white"
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
            <h1 className="text-lg font-semibold text-black">{title}</h1>
            {hasUnsavedChanges && (
              <span className="text-xs px-2 py-1 bg-yellow-500/20 text-yellow-400 rounded">
                Unsaved
              </span>
            )}
          </div>
          {subtitle && (
            <p className="text-xs text-slate-500 mt-0.5">{subtitle}</p>
          )}
        </div>
      </div>

      {/* Right: Action buttons */}
      <div className="flex items-center gap-3">
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
              className="px-3 py-2 rounded-lg transition-colors text-slate-600 hover:bg-slate-100 hover:text-slate-900 flex items-center gap-2"
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
            className="px-3 py-2 rounded-lg transition-colors text-slate-600 hover:bg-slate-100 hover:text-slate-900 flex items-center gap-2"
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

        {/* User Profile Menu - separator and user button */}
        {currentUser && (
          <>
            <div className="w-px h-6 bg-white/10 mx-1"></div>
            <div className="relative z-40">
              <button
                onClick={() => setShowUserMenu(!showUserMenu)}
                className="p-2 hover:bg-white/10 rounded-lg transition-colors text-slate-400 hover:text-white flex items-center gap-2"
                title="User menu"
              >
                <User className="w-4 h-4" />
                <span className="text-sm font-medium text-slate-300">{currentUser}</span>
              </button>

              {/* Dropdown Menu - Fixed positioning to avoid overflow */}
              {showUserMenu && (
                <>
                  {/* Backdrop to close menu on click outside */}
                  <div 
                    className="fixed inset-0 z-40" 
                    onClick={() => setShowUserMenu(false)}
                  />
                  {/* Dropdown */}
                  <div className="absolute right-0 top-full mt-2 w-48 bg-slate-800 border border-white/10 rounded-lg shadow-xl z-50">
                    <div className="px-4 py-3 border-b border-white/10">
                      <p className="text-xs text-slate-500">Logged in as</p>
                      <p className="text-sm font-semibold text-white truncate">{currentUser}</p>
                    </div>
                    <button
                      onClick={() => {
                        onLogout?.();
                        setShowUserMenu(false);
                      }}
                      className="w-full px-4 py-2 text-left text-sm text-slate-300 hover:text-white hover:bg-white/10 transition-colors flex items-center gap-2"
                    >
                      <LogOut className="w-4 h-4" />
                      Logout
                    </button>
                  </div>
                </>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
};
