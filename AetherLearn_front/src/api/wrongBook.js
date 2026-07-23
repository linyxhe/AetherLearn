import request from '../utils/request'

// 错题本接口（F-LEARN 扩展模块）

/** 查询当前学生错题本 */
export function listWrongBook(params = {}) {
  return request.get('/wrong-book/list', { params })
}
