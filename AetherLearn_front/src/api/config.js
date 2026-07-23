import request from '../utils/request'

// 系统配置接口（L7 sys_config 表接入）
// 获取所有配置列表
export function listConfigs() {
  return request.get('/admin/config')
}

// 更新配置值
export function updateConfig(key, value, remark) {
  return request.put(`/admin/config/${key}`, null, {
    params: { value, remark }
  })
}
