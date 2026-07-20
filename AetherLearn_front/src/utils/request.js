import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// Axios 统一封装（F-AUTH / 基础支撑）
// - 自动注入 Authorization: Bearer token
// - 统一处理 401（跳登录）与错误提示
const request = axios.create({
  baseURL: '/api',      // 由 vite 代理转发到后端 8080
  timeout: 15000
})

// 请求拦截：注入 Token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = token
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截：统一拆解 { code, message, data }
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 业务成功
    if (res.code === 200) {
      return res.data
    }
    // 业务失败
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || 'Error'))
  },
  (error) => {
    const status = error.response?.status
    const data = error.response?.data
    if (status === 401) {
      // 未认证/过期：清理本地状态并跳转登录
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      ElMessage.error(data?.message || '登录已过期，请重新登录')
      if (router.currentRoute.value.path !== '/login') {
        router.push('/login')
      }
    } else if (status === 403) {
      ElMessage.error('权限不足，无法访问')
    } else {
      ElMessage.error(data?.message || error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
