import { fetchWithAuth } from './processService';
import { getModelerApiBaseUrl } from '../config/runtimeConfig';

const API_BASE_URL = getModelerApiBaseUrl();

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
