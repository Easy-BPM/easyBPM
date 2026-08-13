import path from 'path';
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const base = process.env.EASY_BPM_ADMIN_BASE_PATH ?? env.EASY_BPM_ADMIN_BASE_PATH ?? '/';
  return {
    base,
    server: {
      port: 3001,
      host: '0.0.0.0'
    },
    envPrefix: 'EASY_BPM_',
    plugins: [react()],
    define: {
      'process.env.API_KEY': JSON.stringify(env.GEMINI_API_KEY),
      'process.env.GEMINI_API_KEY': JSON.stringify(env.GEMINI_API_KEY)
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, '.')
      }
    }
  };
});
