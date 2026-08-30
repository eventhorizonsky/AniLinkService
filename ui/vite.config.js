import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vuetify from 'vite-plugin-vuetify'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8081'
  const imagesTarget = env.VITE_IMAGES_TARGET || apiTarget

  return {
    plugins: [
      vue(),
      vuetify({ autoImport: true })
    ],
    optimizeDeps: {
      // Vuetify 为纯 ESM，无需预打包；避免 autoImport 注入的深层导入
      // 在首次懒加载时触发 "new dependencies optimized" 导致的整页刷新。
      exclude: ['vuetify']
    },
    server: {
      port: Number(env.VITE_DEV_PORT) || 5173,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
          secure: false
        },
        '/images': {
          target: imagesTarget,
          changeOrigin: true,
          secure: false
        }
      }
    },
    build: {
      chunkSizeWarningLimit: 1000,
      rollupOptions: {
        output: {
          manualChunks: {
            vue: ['vue', 'vue-router'],
            vuetify: ['vuetify'],
            artplayer: ['artplayer', 'artplayer-plugin-danmuku', 'artplayer-plugin-vtt-thumbnail'],
            http: ['axios'],
            markdown: ['marked', 'dompurify']
          }
        }
      }
    }
  }
})
