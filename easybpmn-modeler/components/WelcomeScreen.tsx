import React from 'react';
import { Layout, FileText, Plus, Zap, Shield, User, LogOut } from 'lucide-react';

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
    <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-blue-950 flex flex-col">
      {/* Top Navbar with User Profile */}
      <div className="bg-white/5 backdrop-blur-sm border-b border-white/10 px-6 py-4 flex items-center justify-between">
        <div className="flex-1">
          <h2 className="text-white font-semibold">Easy BPM Modeler</h2>
        </div>
        
        {currentUser && (
          <div className="relative">
            <button
              onClick={() => setShowUserMenu(!showUserMenu)}
              className="p-2 hover:bg-white/10 rounded-lg transition-colors text-slate-400 hover:text-white flex items-center gap-2"
              title="User menu"
            >
              <User className="w-4 h-4" />
              <span className="text-sm font-medium text-slate-300">{currentUser}</span>
            </button>

            {/* Dropdown Menu */}
            {showUserMenu && (
              <div className="absolute right-0 top-full mt-2 w-48 bg-slate-800 border border-white/10 rounded-lg shadow-xl z-50">
                <div className="px-4 py-3 border-b border-white/10">
                  <p className="text-xs text-slate-500">Logged in as</p>
                  <p className="text-sm font-semibold text-white truncate">{currentUser}</p>
                </div>
                {onLogout && (
                  <button
                    onClick={() => {
                      onLogout();
                      setShowUserMenu(false);
                    }}
                    className="w-full px-4 py-2 text-left text-sm text-slate-300 hover:text-white hover:bg-white/10 transition-colors flex items-center gap-2"
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

      {/* Main Content */}
      <div className="flex-1 flex items-center justify-center p-4 overflow-auto">
      <div className="w-full max-w-6xl">
        {/* Header */}
        <div className="text-center mb-16">
          <div className="flex items-center justify-center gap-3 mb-6">
            <div className="bg-blue-600 p-3 rounded-xl shadow-lg shadow-blue-600/40 ring-4 ring-blue-600/20">
              <Layout className="text-white" size={32} />
            </div>
            <h1 className="text-4xl font-bold text-white">Easy BPM Modeler</h1>
          </div>
          <p className="text-slate-400 text-lg">Design, deploy, and manage your business processes</p>
        </div>

        {/* Main Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mb-12">
          {/* Process Card */}
          <button
            onClick={onCreateProcess}
            className="group relative"
          >
            <div className="absolute -inset-0.5 bg-gradient-to-r from-blue-600 to-blue-500 rounded-2xl blur opacity-75 group-hover:opacity-100 transition duration-1000 group-hover:duration-200"></div>
            <div className="relative bg-white/5 backdrop-blur-sm border border-white/10 rounded-2xl p-8 hover:bg-white/10 transition-all cursor-pointer h-full">
              <div className="flex flex-col items-center text-center space-y-4">
                <div className="bg-blue-500/20 p-4 rounded-xl ring-4 ring-blue-500/20">
                  <Zap className="text-blue-400" size={40} />
                </div>
                <div>
                  <h2 className="text-2xl font-bold text-white mb-2">New Process</h2>
                  <p className="text-slate-400 text-sm">
                    Create a new BPMN process with tasks, gateways, and workflows
                  </p>
                </div>
                <div className="flex gap-2 text-xs text-slate-500 flex-wrap justify-center pt-4">
                  <span className="px-2 py-1 bg-slate-700/50 rounded">Tasks</span>
                  <span className="px-2 py-1 bg-slate-700/50 rounded">Gateways</span>
                  <span className="px-2 py-1 bg-slate-700/50 rounded">Variables</span>
                </div>
              </div>
            </div>
          </button>

          {/* Form Card */}
          <button
            onClick={onCreateForm}
            className="group relative"
          >
            <div className="absolute -inset-0.5 bg-gradient-to-r from-emerald-600 to-emerald-500 rounded-2xl blur opacity-75 group-hover:opacity-100 transition duration-1000 group-hover:duration-200"></div>
            <div className="relative bg-white/5 backdrop-blur-sm border border-white/10 rounded-2xl p-8 hover:bg-white/10 transition-all cursor-pointer h-full">
              <div className="flex flex-col items-center text-center space-y-4">
                <div className="bg-emerald-500/20 p-4 rounded-xl ring-4 ring-emerald-500/20">
                  <FileText className="text-emerald-400" size={40} />
                </div>
                <div>
                  <h2 className="text-2xl font-bold text-white mb-2">New Form</h2>
                  <p className="text-slate-400 text-sm">
                    Design form fields, layouts, and user input validation
                  </p>
                </div>
                <div className="flex gap-2 text-xs text-slate-500 flex-wrap justify-center pt-4">
                  <span className="px-2 py-1 bg-slate-700/50 rounded">Text Fields</span>
                  <span className="px-2 py-1 bg-slate-700/50 rounded">Tabs</span>
                  <span className="px-2 py-1 bg-slate-700/50 rounded">Validation</span>
                </div>
              </div>
            </div>
          </button>
        </div>

        {/* Features */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-12">
          <div className="bg-white/5 backdrop-blur-sm border border-white/10 rounded-xl p-6">
            <div className="flex items-start gap-4">
              <Zap className="text-blue-400 flex-shrink-0 mt-1" size={24} />
              <div>
                <h3 className="text-white font-semibold mb-2">Independent Resources</h3>
                <p className="text-slate-400 text-sm">
                  Create, manage, and deploy processes and forms completely independently
                </p>
              </div>
            </div>
          </div>

          <div className="bg-white/5 backdrop-blur-sm border border-white/10 rounded-xl p-6">
            <div className="flex items-start gap-4">
              <FileText className="text-emerald-400 flex-shrink-0 mt-1" size={24} />
              <div>
                <h3 className="text-white font-semibold mb-2">Rich Editing</h3>
                <p className="text-slate-400 text-sm">
                  Visual editors for processes and forms with drag-and-drop interfaces
                </p>
              </div>
            </div>
          </div>

          <div className="bg-white/5 backdrop-blur-sm border border-white/10 rounded-xl p-6">
            <div className="flex items-start gap-4">
              <Shield className="text-orange-400 flex-shrink-0 mt-1" size={24} />
              <div>
                <h3 className="text-white font-semibold mb-2">Deploy & Share</h3>
                <p className="text-slate-400 text-sm">
                  Export, import, and share your resources with your team
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="text-center text-slate-500 text-sm">
          <p>Easy BPM · Enterprise Process Management</p>
        </div>
      </div>
    </div>
    </div>
  );
};
