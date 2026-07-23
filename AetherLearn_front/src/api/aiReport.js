import request from '../utils/request'

// AI 学习报告接口（F-AI-REPORT）

export function getStudentAiReport() {
  return request.get('/ai-reports/student')
}
