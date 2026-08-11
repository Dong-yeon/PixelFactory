import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// 로컬 개발: /api, /ws를 oee-service(8081)로 프록시 — CORS 없이 동작.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8081',
      '/ws': { target: 'http://localhost:8081', ws: true },
    },
  },
});
