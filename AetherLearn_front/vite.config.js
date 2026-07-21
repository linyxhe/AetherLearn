import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// Vite 配置（AetherLearn 前端）
// 开发期将 /api 代理到后端（Spring Boot 8085），避免跨域。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8085',
        changeOrigin: true,
        // SSE 流式代理：手动转发数据流，绕过 http-proxy 默认的响应缓冲
        selfHandleResponse: true,
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes, req, res) => {
            if (req.headers.accept === 'text/event-stream') {
              // SSE 请求：逐 chunk 立即写入客户端，不缓冲
              res.writeHead(proxyRes.statusCode, proxyRes.headers)
              proxyRes.on('data', (chunk) => {
                try { res.write(chunk) } catch (_) { /* 连接已断开 */ }
              })
              proxyRes.on('end', () => res.end())
              proxyRes.on('error', () => { try { res.end() } catch (_) {} })
            } else {
              // 普通请求：收集完整响应后一次性返回
              const chunks = []
              proxyRes.on('data', (chunk) => chunks.push(chunk))
              proxyRes.on('end', () => {
                const body = Buffer.concat(chunks)
                res.writeHead(proxyRes.statusCode, proxyRes.headers)
                res.end(body)
              })
              proxyRes.on('error', () => {
                res.writeHead(502)
                res.end('Proxy Error')
              })
            }
          })
          proxy.on('error', (_err, _req, res) => {
            res.writeHead(502)
            res.end('Proxy Error')
          })
        }
      },
      '/uploads': {
        target: 'http://localhost:8085',
        changeOrigin: true
      }
    }
  }
})
