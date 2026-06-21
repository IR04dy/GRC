import { defineConfig } from 'vitest/config';
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
  // Sprint 4 §2.7 — sockjs-client (used by @stomp/stompjs) references `global`,
  // which exists in Node.js but not in browsers. Shim it to globalThis so the
  // CommonJS bundle loads cleanly. Also need to proxy `/ws` for STOMP.
  define: {
    global: 'globalThis',
  },
  server: {
    port: 5173,
    strictPort: false,
    // Bind to 0.0.0.0 so the dev server is reachable from other machines on
    // the LAN (not just localhost). allowedHosts:true lets Vite serve requests
    // whose Host header is the LAN IP / hostname instead of localhost.
    host: true,
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8084',
        changeOrigin: true,
        secure: false,
      },
      // STOMP / SockJS endpoint
      '/ws': {
        target: 'http://localhost:8084',
        changeOrigin: true,
        ws: true,
        secure: false,
      },
    },
  },
  build: {
    sourcemap: true,
    target: 'es2022',
    // PERF-FE-004: split the monolithic vendor bundle (~1.18 MB) into stable,
    // separately-cacheable chunks so a change to app code doesn't bust the
    // whole vendor cache and the browser can parallelise downloads. Charting
    // (recharts/d3) was already lazy-split; this buckets the remaining libs.
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined;
          if (id.includes('recharts') || id.includes('/d3-') || id.includes('victory')) return 'recharts';
          if (id.includes('react-router') || id.includes('@remix-run')) return 'router';
          if (id.includes('react-dom') || id.includes('/react/') || id.includes('/scheduler/')) return 'react';
          if (id.includes('lucide-react')) return 'icons';
          if (id.includes('@stomp') || id.includes('sockjs')) return 'realtime';
          if (id.includes('@tanstack')) return 'tanstack';
          return 'vendor';
        },
      },
    },
  },
  // WS14 (FUNC-001): frontend test runner. `npm test` -> vitest.
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
});
