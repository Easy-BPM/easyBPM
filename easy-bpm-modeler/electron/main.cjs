const { app, BrowserWindow, Menu, dialog, ipcMain } = require('electron');
const fs = require('fs/promises');
const path = require('path');

const isDev = process.env.EASY_BPM_DESKTOP_DEV === 'true';
const appIconPath = path.join(__dirname, '..', 'assets', 'icon.png');

const createWindow = async () => {
  const mainWindow = new BrowserWindow({
    width: 1440,
    height: 920,
    minWidth: 1100,
    minHeight: 720,
    title: 'Easy BPM Desktop Modeler',
    icon: appIconPath,
    backgroundColor: '#0f172a',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  if (isDev) {
    await mainWindow.loadURL(process.env.EASY_BPM_DESKTOP_DEV_URL || 'http://127.0.0.1:3000');
    mainWindow.webContents.openDevTools({ mode: 'detach' });
  } else {
    await mainWindow.loadFile(path.join(__dirname, '..', 'dist', 'index.html'));
  }
};

const createMenu = () => {
  const template = [
    ...(process.platform === 'darwin'
      ? [{
          label: app.name,
          submenu: [
            { role: 'about' },
            { type: 'separator' },
            { role: 'hide' },
            { role: 'hideOthers' },
            { role: 'unhide' },
            { type: 'separator' },
            { role: 'quit' }
          ]
        }]
      : []),
    {
      label: 'File',
      submenu: [
        { role: 'close' }
      ]
    },
    {
      label: 'View',
      submenu: [
        { role: 'reload' },
        { role: 'toggleDevTools' },
        { type: 'separator' },
        { role: 'resetZoom' },
        { role: 'zoomIn' },
        { role: 'zoomOut' },
        { type: 'separator' },
        { role: 'togglefullscreen' }
      ]
    }
  ];

  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
};

ipcMain.handle('easy-bpm:save-text-file', async (_event, request) => {
  const result = await dialog.showSaveDialog({
    defaultPath: request?.defaultPath,
    filters: request?.filters || [{ name: 'All Files', extensions: ['*'] }]
  });

  if (result.canceled || !result.filePath) {
    return { canceled: true };
  }

  await fs.writeFile(result.filePath, request.content || '', 'utf8');
  return { filePath: result.filePath };
});

ipcMain.handle('easy-bpm:open-text-file', async (_event, request) => {
  const result = await dialog.showOpenDialog({
    properties: ['openFile'],
    filters: request?.filters || [{ name: 'All Files', extensions: ['*'] }]
  });

  if (result.canceled || result.filePaths.length === 0) {
    return { canceled: true };
  }

  const filePath = result.filePaths[0];
  const content = await fs.readFile(filePath, 'utf8');
  return { filePath, content };
});

app.whenReady().then(async () => {
  app.setName('Easy BPM Desktop Modeler');
  if (process.platform === 'darwin' && app.dock) {
    app.dock.setIcon(appIconPath);
  }

  createMenu();
  await createWindow();

  app.on('activate', async () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      await createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
