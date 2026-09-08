export type DesktopFileFilter = {
  name: string;
  extensions: string[];
};

export type DesktopSaveRequest = {
  defaultPath?: string;
  filters?: DesktopFileFilter[];
  content: string;
};

export type DesktopOpenRequest = {
  filters?: DesktopFileFilter[];
};

export type DesktopOpenResult = {
  filePath: string;
  content: string;
};

export type EasyBpmDesktopBridge = {
  config?: Record<string, string | undefined>;
  saveTextFile: (request: DesktopSaveRequest) => Promise<{ filePath?: string; canceled?: boolean }>;
  openTextFile: (request?: DesktopOpenRequest) => Promise<DesktopOpenResult | { canceled: true }>;
};

declare global {
  interface Window {
    easyBpmDesktop?: EasyBpmDesktopBridge;
  }
}

export const getDesktopBridge = (): EasyBpmDesktopBridge | undefined => window.easyBpmDesktop;

export const saveTextFile = async (
  content: string,
  defaultPath: string,
  filters: DesktopFileFilter[]
): Promise<boolean> => {
  const bridge = getDesktopBridge();
  if (bridge) {
    const result = await bridge.saveTextFile({ content, defaultPath, filters });
    return !result.canceled;
  }

  const dataUrl = `data:text/plain;charset=utf-8,${encodeURIComponent(content)}`;
  const link = document.createElement('a');
  link.href = dataUrl;
  link.download = defaultPath;
  link.click();
  return true;
};

export const openTextFile = async (filters: DesktopFileFilter[]): Promise<DesktopOpenResult | null> => {
  const bridge = getDesktopBridge();
  if (!bridge) return null;
  const result = await bridge.openTextFile({ filters });
  return 'canceled' in result ? null : result;
};
