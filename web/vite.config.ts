import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    // Chạy local: API Spring Boot ở cổng 8080
    proxy: { '/api': 'http://localhost:8080' },
  },
});
