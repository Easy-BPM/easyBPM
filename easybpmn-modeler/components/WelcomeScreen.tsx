import React from 'react';
import { Layout, FileText, Zap, Shield, User, LogOut } from 'lucide-react';
import { controlButtonDangerClass, controlPanelClass } from '../../shared/design-system/classes';

interface WelcomeScreenProps {
  onCreateProcess: () => void;
  onCreateForm: () => void;
  onOpenRecent?: () => void;
  currentUser?: string | null;
  onLogout?: () => void;
}

export const WelcomeScreen: React.FC<WelcomeScreenProps> = ({
  onCreateProcess,
  onCreateForm,
  onOpenRecent,
  currentUser,
  onLogout
}) => {
  const [showUserMenu, setShowUserMenu] = React.useState(false);

  return (
    <div className="min-h-screen bg-[#151922] flex flex-col">
      <div className="bg-[#11161f] border-b border-[#2d3748] px-6 py-4 flex items-center justify-between">
        <div className="flex-1">
          <h2 className="text-white font-semibold">EasyBPM Control Suite</h2>
        </div>
        
        {currentUser && (
          <div className="relative">
            <button
              onClick={() => setShowUserMenu(!showUserMenu)}
              className="px-3 py-2 hover:bg-[#1b2331] rounded-md transition-colors text-[#a8b2c5] hover:text-white flex items-center gap-2 border border-[#2d3748]"
              title="User menu"
            >
              <User className="w-4 h-4" />
              <span className="text-sm font-medium text-slate-300">{currentUser}</span>
            </button>

            {showUserMenu && (
              <div className="absolute right-0 top-full mt-2 w-48 bg-[#1c2230] border border-[#364257] rounded-md shadow-xl z-50">
                <div className="px-4 py-3 border-b border-[#2d3748]">
                  <p className="text-xs text-[#7b869b]">Logged in as</p>
                  <p className="text-sm font-semibold text-white truncate">{currentUser}</p>
                </div>
                {onLogout && (
                  <button
                    onClick={() => {
                      onLogout();
                      setShowUserMenu(false);
                    }}
                    className={`${controlButtonDangerClass} w-full justify-start rounded-none border-0 bg-transparent px-4`}
                  >
                    <LogOut className="w-4 h-4" />
                    Logout
                  </button>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      <div className="flex-1 flex items-center justify-center p-4 overflow-auto">
      <div className="w-full max-w-6xl">
        <div className="text-center mb-12">
          <div className="flex items-center justify-center gap-3 mb-6">
            <div className="bg-[#7c8cff] p-3 rounded-md border border-[#95a2ff]/40">
              <Layout className="text-white" size={32} />
            </div>
            <h1 className="text-4xl font-semibold text-white">EasyBPM Control Suite</h1>
          </div>
          <p className="text-[#a8b2c5] text-lg">Design, deploy, and manage business processes with an enterprise control-surface workflow.</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mb-12">
          <button
            onClick={onCreateProcess}
            className={`${controlPanelClass} group p-8 h-full hover:border-[#7c8cff] transition-colors`}
          >
            <div className="flex flex-col items-center text-center space-y-4">
              <div className="bg-[rgba(124,140,255,0.14)] p-4 rounded-md border border-[rgba(124,140,255,0.34)]">
                <Zap className="text-[#7c8cff]" size={40} />
              </div>
              <div>
                <h2 className="text-2xl font-semibold text-white mb-2">New Process</h2>
                <p className="text-[#a8b2c5] text-sm">
                  Create a BPMN process with tasks, gateways, and workflow routing.
                </p>
              </div>
              <div className="flex gap-2 text-xs text-[#7b869b] flex-wrap justify-center pt-4">
                <span className="px-2 py-1 bg-[#11161f] border border-[#2d3748] rounded">Tasks</span>
                <span className="px-2 py-1 bg-[#11161f] border border-[#2d3748] rounded">Gateways</span>
                <span className="px-2 py-1 bg-[#11161f] border border-[#2d3748] rounded">Variables</span>
              </div>
            </div>
          </button>

          <button
            onClick={onCreateForm}
            className={`${controlPanelClass} group p-8 h-full hover:border-[#21c7a8] transition-colors`}
          >
            <div className="flex flex-col items-center text-center space-y-4">
              <div className="bg-[rgba(33,199,168,0.14)] p-4 rounded-md border border-[rgba(33,199,168,0.34)]">
                <FileText className="text-[#21c7a8]" size={40} />
              </div>
              <div>
                <h2 className="text-2xl font-semibold text-white mb-2">New Form</h2>
                <p className="text-[#a8b2c5] text-sm">
                  Design form fields, layouts, and input validation for task work queues.
                </p>
              </div>
              <div className="flex gap-2 text-xs text-[#7b869b] flex-wrap justify-center pt-4">
                <span className="px-2 py-1 bg-[#11161f] border border-[#2d3748] rounded">Text Fields</span>
                <span className="px-2 py-1 bg-[#11161f] border border-[#2d3748] rounded">Tabs</span>
                <span className="px-2 py-1 bg-[#11161f] border border-[#2d3748] rounded">Validation</span>
              </div>
            </div>
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-12">
          <div className={`${controlPanelClass} p-6`}>
            <div className="flex items-start gap-4">
              <Zap className="text-[#7c8cff] flex-shrink-0 mt-1" size={24} />
              <div>
                <h3 className="text-white font-semibold mb-2">Independent Resources</h3>
                <p className="text-[#a8b2c5] text-sm">
                  Create, manage, and deploy processes and forms completely independently
                </p>
              </div>
            </div>
          </div>

          <div className={`${controlPanelClass} p-6`}>
            <div className="flex items-start gap-4">
              <FileText className="text-[#21c7a8] flex-shrink-0 mt-1" size={24} />
              <div>
                <h3 className="text-white font-semibold mb-2">Rich Editing</h3>
                <p className="text-[#a8b2c5] text-sm">
                  Visual editors for processes and forms with drag-and-drop interfaces
                </p>
              </div>
            </div>
          </div>

          <div className={`${controlPanelClass} p-6`}>
            <div className="flex items-start gap-4">
              <Shield className="text-[#f2b84b] flex-shrink-0 mt-1" size={24} />
              <div>
                <h3 className="text-white font-semibold mb-2">Deploy & Share</h3>
                <p className="text-[#a8b2c5] text-sm">
                  Export, import, and share your resources with your team
                </p>
              </div>
            </div>
          </div>
        </div>

        <div className="text-center text-[#7b869b] text-sm">
          <p>EasyBPM Control Suite · Enterprise Process Management</p>
        </div>
      </div>
    </div>
    </div>
  );
};
