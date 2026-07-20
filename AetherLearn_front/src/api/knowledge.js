import request from '../utils/request'

// 课程知识库接口（F-KB 知识库模块）
// 上传：后端接收 @RequestParam("file") + courseId 查询参数，返回 KnowledgeDoc
export function uploadKnowledge(courseId, file) {
  const form = new FormData()
  form.append('file', file)
  return request.post(`/knowledge/upload?courseId=${courseId}`, form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 按课程列举知识文档
export function listKnowledge(courseId) {
  return request.get('/knowledge/list', { params: { courseId } })
}

// 软删除文档
export function deleteKnowledge(id) {
  return request.delete(`/knowledge/${id}`)
}
