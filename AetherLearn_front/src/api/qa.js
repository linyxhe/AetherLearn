import request from '../utils/request'

// 智能答疑接口（F-QA 智能答疑模块）
// 提问：data = { courseId, question }
export function askQa(data) {
  return request.post('/qa/ask', data)
}

// 问答历史（可按课程筛选）
export function qaHistory(courseId) {
  return request.get('/qa/history', { params: courseId ? { courseId } : {} })
}
