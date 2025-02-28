import { defineConfig } from 'vite'

export default defineConfig({
  server: {
    proxy: {
      '/config': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws/analysis': {
        target: 'ws://localhost:8080',
        ws: true
      },
      '/ws/exchange': {
        target: 'ws://localhost:8080',
        ws: true
      }
    }
  }
}) 