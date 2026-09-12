import request from '../utils/request'

// AI 学习报告接口（F-AI-REPORT）

// 报告由 Python 编排服务调用大模型生成，耗时可达数十秒，须单独放宽超时
export function getStudentAiReport() {
  return request.get('/ai-reports/student', { timeout: 90000 })
}
