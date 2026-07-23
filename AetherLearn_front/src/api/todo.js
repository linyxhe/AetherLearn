import request from '../utils/request'

// 学习计划 / 待办接口（F-TODO）

export function listTodos(params = {}) {
  return request.get('/todos', { params })
}

export function saveTodo(data) {
  return request.post('/todos', data)
}

export function completeTodo(id) {
  return request.post(`/todos/${id}/complete`)
}

export function reopenTodo(id) {
  return request.post(`/todos/${id}/reopen`)
}

export function deleteTodo(id) {
  return request.delete(`/todos/${id}`)
}
