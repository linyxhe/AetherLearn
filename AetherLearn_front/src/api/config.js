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

// 获取 AI 动态配置（后端不会返回 API Key 原文）
export function getAiSettings() {
  return request.get('/admin/config/ai')
}

// 保存 AI 动态配置，API Key 为空时保留原值
export function updateAiSettings(data) {
  return request.put('/admin/config/ai', data)
}

// 使用已保存配置发起真实模型连接测试（真实调用模型，超时放宽到 60s）
export function testAiConnection() {
  return request.post('/admin/config/ai/test', null, { timeout: 60000 })
}
