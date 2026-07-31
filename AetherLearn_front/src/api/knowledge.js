import request from '../utils/request'

// 课程知识库接口（F-KB 知识库模块）
// 上传：后端接收 @RequestParam("file") + courseId 查询参数，返回 KnowledgeDoc
/** 上传知识库资料，并通过回调反馈浏览器已传输的文件百分比。 */
export function uploadKnowledge(courseId, file, onProgress) {
  const form = new FormData()
  form.append('file', file)
  return request.post(`/knowledge/upload?courseId=${courseId}`, form, {
    onUploadProgress: (event) => {
      if (!event.total || !onProgress) return
      onProgress(Math.min(100, Math.round((event.loaded * 100) / event.total)))
    }
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

// 检索知识库切片（M2 知识库检索预览）
export function searchKnowledge(courseId, query, topK = 10) {
  return request.get('/knowledge/search', { params: { courseId, query, topK } })
}
