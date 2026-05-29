import React from 'react';
import { ArrowLeft, Home, Save, Eye, Code, Download, Upload, User, LogOut } from 'lucide-react';
import { controlButtonClass, controlButtonDangerClass, controlButtonPrimaryClass, controlChipAccentClass, controlChipWarningClass } from '../../shared/design-system/classes';

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
  const statusToneClass = resourceType === 'process' ? controlChipAccentClass : 'control-chip control-chip-success';

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
    <div className="bg-[#151922] border-b border-[#2d3748] px-5 py-3 flex items-center justify-between">
      <div className="flex items-center gap-4">
        <button
          onClick={onBack}
          className={`${controlButtonClass} min-h-0 h-9 w-9 p-0 text-[#a8b2c5] hover:text-white`}
          title="Back to welcome screen"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>

        <div className="flex-1">
          <div className="flex items-center gap-3">
            <span className={statusToneClass}>{resourceType === 'process' ? 'Process Workspace' : 'Form Workspace'}</span>
            <h1 className="text-lg font-semibold text-white">{title}</h1>
            {hasUnsavedChanges && (
              <span className={controlChipWarningClass}>
                Unsaved
              </span>
            )}
          </div>
          {subtitle && (
            <p className="text-xs text-[#7b869b] mt-1">{subtitle}</p>
          )}
        </div>
      </div>

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
              className={`${controlButtonClass} text-[#a8b2c5]`}
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
            className={`${controlButtonClass} text-[#a8b2c5]`}
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
            className={controlButtonPrimaryClass}
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
            <div className="w-px h-6 bg-[#2d3748] mx-1"></div>
            <div className="relative z-40">
              <button
                onClick={() => setShowUserMenu(!showUserMenu)}
                className={`${controlButtonClass} text-[#a8b2c5]`}
                title="User menu"
              >
                <User className="w-4 h-4" />
                <span className="text-sm font-medium text-[#e6eaf2]">{currentUser}</span>
              </button>

              {showUserMenu && (
                <>
                  <div 
                    className="fixed inset-0 z-40" 
                    onClick={() => setShowUserMenu(false)}
                  />
                  <div className="absolute right-0 top-full mt-2 w-52 bg-[#1c2230] border border-[#364257] rounded-md shadow-xl z-50">
                    <div className="px-4 py-3 border-b border-[#2d3748]">
                      <p className="text-xs text-[#7b869b]">Logged in as</p>
                      <p className="text-sm font-semibold text-white truncate">{currentUser}</p>
                    </div>
                    <button
                      onClick={() => {
                        onLogout?.();
                        setShowUserMenu(false);
                      }}
                      className={`${controlButtonDangerClass} w-full justify-start rounded-none border-0 bg-transparent px-4`}
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
