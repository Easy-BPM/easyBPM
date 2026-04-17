const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080';

export const processService = {
  deployProcess: async (payload: unknown): Promise<void> => {
    const response = await fetch(`${API_BASE_URL}/processes`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Deploy failed (${response.status}): ${body || response.statusText}`);
    }
  }
};
