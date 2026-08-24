import { fetchWithAuth } from './processService';
import { getModelerApiBaseUrl } from '../config/runtimeConfig';

const API_BASE_URL = getModelerApiBaseUrl();

export type FormDefinitionSummary = {
  id: number;
  formId: string;
  name: string;
  schema: Record<string, unknown>;
  version: number;
  createdAt?: string;
};

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
  },

  listLatest: async (): Promise<FormDefinitionSummary[]> => {
    const response = await fetchWithAuth(`${API_BASE_URL}/forms/latest-list`);
    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Load forms failed (${response.status}): ${body || response.statusText}`);
    }
    return response.json();
  },

  getById: async (id: number): Promise<FormDefinitionSummary> => {
    const response = await fetchWithAuth(`${API_BASE_URL}/forms/${id}`);
    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Load form failed (${response.status}): ${body || response.statusText}`);
    }
    return response.json();
  }
};
