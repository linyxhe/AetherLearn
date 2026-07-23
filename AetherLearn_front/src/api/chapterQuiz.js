import request from '../utils/request'

// 章节小测接口（F-LEARN 扩展模块）

/** 查询章节小测题目 */
export function listChapterQuizzes(chapterId) {
  return request.get(`/chapter-quiz/chapter/${chapterId}`)
}

/** 保存章节小测题目（教师/管理员） */
export function saveChapterQuiz(data) {
  return request.post('/chapter-quiz/question', data)
}

/** 删除章节小测题目（教师/管理员） */
export function deleteChapterQuiz(id) {
  return request.delete(`/chapter-quiz/question/${id}`)
}

/** 提交章节小测（学生） */
export function submitChapterQuiz(data) {
  return request.post('/chapter-quiz/submit', data)
}
