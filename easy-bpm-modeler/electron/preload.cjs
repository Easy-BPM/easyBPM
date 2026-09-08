const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('easyBpmDesktop', {
  config: {
    EASY_BPM_MODELER_RUNTIME_MODE: 'desktop',
    EASY_BPM_MODELER_AGENTIC_ORCHESTRATION: process.env.EASY_BPM_MODELER_AGENTIC_ORCHESTRATION || 'true'
  },
  saveTextFile: (request) => ipcRenderer.invoke('easy-bpm:save-text-file', request),
  openTextFile: (request) => ipcRenderer.invoke('easy-bpm:open-text-file', request)
});
