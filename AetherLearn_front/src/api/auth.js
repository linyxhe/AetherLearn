import request from '../utils/request'

// 认证相关接口（F-AUTH-01 登录）
export function login(data) {
  return request.post('/auth/login', data)
}
