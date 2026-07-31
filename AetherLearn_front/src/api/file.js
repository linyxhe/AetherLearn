import request from '../utils/request'

// 文件上传接口（F-FILE-01 统一上传）
// bizType 取值：avatar / course / knowledge / answer / export
export function uploadFile(file, bizType) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bizType', bizType)
  return request.post('/file/upload', formData)
}

// 解析已上传文件为文本（用于课程章节资料解析）
export function parseUploadedFile(url) {
  return request.get('/file/parse', { params: { url } })
}
