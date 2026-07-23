import request from '../utils/request'

// 知识点图谱接口（F-GRAPH）

export function getKnowledgeGraph(courseId) {
  return request.get('/knowledge-graph', { params: courseId ? { courseId } : {} })
}
