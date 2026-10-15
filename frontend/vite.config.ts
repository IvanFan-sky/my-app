import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 显式绑定 IPv4 回环。默认的 'localhost' 会先解析到 IPv6 的 ::1，
    // 在部分受限环境里绑定 ::1 会被拒绝（listen EACCES ::1:5173）。
    host: '127.0.0.1',
    // 刻意不用 Vite 默认的 5173：本机 Hyper-V 预留了 4990–5188 端口段
    // （netsh interface ipv4 show excludedportrange protocol=tcp 可查），
    // 5173/5174 都在其中，绑定必然报 EACCES。3000 实测可用。
    port: 3000,
    // 代理：前端写 /api/xxx，由 Vite 转发到后端 8080。
    // 这样浏览器里是同源请求，开发期不需要处理 CORS。
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
