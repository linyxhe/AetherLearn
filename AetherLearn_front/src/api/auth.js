import request from '../utils/request'

// 认证相关接口（F-AUTH-01 登录/退出）
export function login(data) {
  return request.post('/auth/login', data)
}

export function logout() {
  return request.post('/auth/logout')
}
