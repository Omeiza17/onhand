import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Same-origin in dev: the browser calls /api, Vite forwards to Spring Boot.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
