import request from '../utils/request'

// 课程公告接口（F-NOTIFY）
export function listCourseNotices(courseId) {
  return request.get('/course/notices', { params: courseId ? { courseId } : {} })
}

export function saveCourseNotice(data) {
  return request.post('/course/notices', data)
}

export function deleteCourseNotice(id) {
  return request.delete(`/course/notices/${id}`)
}
