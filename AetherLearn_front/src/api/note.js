import request from '../utils/request'

// 章节笔记接口（F-NOTE）
export function getChapterNote(chapterId) {
  return request.get(`/chapter-notes/chapter/${chapterId}`)
}

export function listChapterNotes(courseId) {
  return request.get('/chapter-notes', { params: courseId ? { courseId } : {} })
}

export function saveChapterNote(data) {
  return request.post('/chapter-notes', data)
}

export function deleteChapterNote(id) {
  return request.delete(`/chapter-notes/${id}`)
}
