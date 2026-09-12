import request from '../utils/request'

// ============ 作业模块接口（F-HW 作业模块） ============

// 作业列表（教师/学生按角色自动区分，可按课程筛选）
export function listAssignments(courseId) {
  return request.get('/assignment/list', { params: courseId ? { courseId } : {} })
}

// 新建/编辑作业（教师）
export function saveAssignment(payload) {
  return request.post('/assignment', payload)
}

// 软删除作业（教师）
export function deleteAssignment(id) {
  return request.delete(`/assignment/${id}`)
}

// 作业详情（含题目；学生视角后端自动隐藏标准答案）
export function getAssignmentDetail(id) {
  return request.get(`/assignment/${id}/detail`)
}

// 新建/编辑题目（教师）
export function saveQuestion(payload) {
  return request.post('/assignment/question', payload)
}

// 删除题目（教师）
export function deleteQuestion(id) {
  return request.delete(`/assignment/question/${id}`)
}

// 学生提交作答（含主观题 AI 批改，后端预算 30s，须单独放宽超时）
export function submitAnswers(payload) {
  return request.post('/answer/submit', payload, { timeout: 60000 })
}

// 查看批改结果（学生本人 / 教师指定 studentId）
export function getAnswerResult(assignmentId, studentId) {
  return request.get('/answer/result', { params: studentId ? { assignmentId, studentId } : { assignmentId } })
}

// 教师查看某作业提交情况（按学生汇总）
export function getSubmissions(assignmentId) {
  return request.get('/answer/submissions', { params: { assignmentId } })
}

// 教师复核单题作答（调分/反馈/复核状态）
export function reviewAnswer(payload) {
  return request.post('/answer/review', payload)
}

// AI 自动出题（L1，教师）
export function autoGenerateQuestions(assignmentId, courseId, count = 5, type = 1) {
  // 出题由 Python 编排服务调用大模型生成，耗时可达数十秒，须单独放宽超时
  return request.post(`/assignment/${assignmentId}/auto-generate`, null, {
    params: { courseId, count, type },
    timeout: 90000
  })
}
