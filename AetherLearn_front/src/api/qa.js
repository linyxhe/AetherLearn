import request from '../utils/request'

// 与 Axios 请求封装保持一致，兼容生产环境部署在 /aetherlearn/ 子路径。
const applicationBasePath = import.meta.env.BASE_URL.replace(/\/$/, '')

// 智能答疑接口（F-QA 智能答疑模块）

// 同步提问：data = { courseId, question }
export function askQa(data) {
  return request.post('/qa/ask', data)
}

// 流式提问（SSE）：返回 fetch Response，调用方用 ReadableStream 消费
export function askQaStream(data, signal) {
  const token = localStorage.getItem('aetherlearn_token') || ''
  // token 已包含 "Bearer " 前缀，直接使用
  // 必须设置 Accept: text/event-stream，否则 Vite 代理会缓冲响应导致流式失效
  return fetch(`${applicationBasePath}/api/qa/ask-stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
      Authorization: token
    },
    signal,
    body: JSON.stringify(data)
  })
}

// 问答历史（可按课程筛选）
export function qaHistory(courseId) {
  return request.get('/qa/history', { params: courseId ? { courseId } : {} })
}
