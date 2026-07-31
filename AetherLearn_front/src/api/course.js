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

/** 查询课程在线学习章节 */
export function listCourseChapters(courseId) {
  return request.get(`/course/${courseId}/chapters`)
}

/** 保存课程在线学习章节（教师/管理员） */
export function saveCourseChapter(data) {
  return request.post('/course/chapters', data)
}

/** 删除课程在线学习章节（教师/管理员） */
export function deleteCourseChapter(chapterId) {
  return request.delete(`/course/chapters/${chapterId}`)
}

/** 删除章节已上传的资源文件（教师/管理员）。 */
export function deleteCourseChapterResource(chapterId) {
  return request.delete(`/course/chapters/${chapterId}/resource`)
}

/** 完成课程章节学习（学生） */
export function completeCourseChapter(chapterId) {
  return request.post(`/course/chapters/${chapterId}/complete`)
}

/** 查询课程学生名单（教师/管理员） */
export function listCourseStudents(courseId) {
  return request.get(`/course/${courseId}/students`)
}

/** 移除课程学生（教师/管理员） */
export function removeCourseStudent(courseId, studentId) {
  return request.delete(`/course/${courseId}/students/${studentId}`)
}
