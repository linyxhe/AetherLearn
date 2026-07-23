import request from '../utils/request'

// 用户信息相关接口（F-AUTH-02 / F-PROFILE-01）
export function getUserInfo() {
  return request.get('/user/info')
}

export function updateUser(data) {
  return request.put('/user/update', data)
}
