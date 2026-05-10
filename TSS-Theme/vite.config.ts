import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

// Vite config — TSS GRC Platform UI v2
// - Dev server runs at http://localhost:5173
// - Backend API expected at http://localhost:8084 (Spring Boot, dev profile)
// - VITE_API_BASE override comes from .env.local (or shell)
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
  },
  server: {
    port: 5173,
    strictPort: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8084',
        changeOrigin: true,
        secure: false,
      },
    },
  },
  build: {
    sourcemap: true,
    target: 'es2022',
  },
});
