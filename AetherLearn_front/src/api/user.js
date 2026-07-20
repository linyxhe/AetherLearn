import request from '../utils/request'

// 用户相关接口（F-AUTH-02 用户信息维护）
export function getUserInfo() {
  return request.get('/user/info')
}

export function updateUser(data) {
  return request.put('/user/update', data)
}
