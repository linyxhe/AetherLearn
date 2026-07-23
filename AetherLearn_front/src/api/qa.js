import request from '../utils/request'

// 智能答疑接口（F-QA 智能答疑模块）

// 同步提问：data = { courseId, question }
export function askQa(data) {
  return request.post('/qa/ask', data)
}

// 流式提问（SSE）：返回 fetch Response，调用方用 ReadableStream 消费
export function askQaStream(data) {
  const token = localStorage.getItem('token') || ''
  // token 已包含 "Bearer " 前缀，直接使用
  // 必须设置 Accept: text/event-stream，否则 Vite 代理会缓冲响应导致流式失效
  return fetch('/api/qa/ask-stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
      Authorization: token
    },
    body: JSON.stringify(data)
  })
}

// 问答历史（可按课程筛选）
export function qaHistory(courseId) {
  return request.get('/qa/history', { params: courseId ? { courseId } : {} })
}
