import { defineConfig } from 'vite'

export default defineConfig({
  server: {
    proxy: {
      '/api/v1/config': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/api/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws/stomp/analysis': {
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