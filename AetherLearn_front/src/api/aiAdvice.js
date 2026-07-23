import request from '../utils/request'

// AI 教学建议接口（F-AI-ADVICE）

export function getTeacherAiAdvice() {
  return request.get('/ai-advice/teacher')
}
