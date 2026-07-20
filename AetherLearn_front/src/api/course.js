import request from '../utils/request'

// 课程相关接口（F-COURSE 课程管理模块）
export function listCourses() {
  return request.get('/course/list')
}

export function saveCourse(data) {
  return request.post('/course', data)
}

export function deleteCourse(id) {
  return request.delete(`/course/${id}`)
}

export function generateInvite(id) {
  return request.post(`/course/${id}/invite`)
}

export function joinCourse(code) {
  return request.post('/course/join', null, { params: { code } })
}
