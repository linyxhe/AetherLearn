import request from '../utils/request'

// AI 教学建议接口（F-AI-ADVICE）

// 教学建议由 Python 编排服务调用大模型生成，耗时可达数十秒，须单独放宽超时
export function getTeacherAiAdvice() {
  return request.get('/ai-advice/teacher', { timeout: 90000 })
}
