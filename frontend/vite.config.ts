import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    cors: true,
  },
  build: {
    // サーバ側のLayout.ViteBuiltがmanifest.jsonを読んでエントリを解決する
    manifest: true,
    rollupOptions: {
      input: 'src/main.tsx',
    },
  },
})
