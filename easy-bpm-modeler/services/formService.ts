import { fetchWithAuth } from './processService';

const API_BASE_URL = (import.meta.env.EASY_BPM_MODELER_API_BASE_URL as string | undefined) ?? 'http://localhost:8080';

export const formService = {
  deploy: async (payload: unknown): Promise<void> => {
    const response = await fetchWithAuth(`${API_BASE_URL}/forms`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Deploy form failed (${response.status}): ${body || response.statusText}`);
    }
  }
};
