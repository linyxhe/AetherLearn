import { defineStore } from 'pinia'

// AetherLearn runs on the same origin as Echo Chat. Keep auth state namespaced
// so the two applications cannot accidentally reuse each other's session.
const TOKEN_KEY = 'aetherlearn_token'
const USER_KEY = 'aetherlearn_user'

// 角色编码常量（与后端 RoleConstant 保持一致）
export const ROLE = { ADMIN: 1, TEACHER: 2, STUDENT: 3 }

function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

// 根据角色返回首页路径
export function homePathByRole(role) {
  if (role === ROLE.STUDENT) return '/student-dashboard'
  return '/dashboard' // 教师/管理员
}

// 用户状态管理（F-AUTH / 状态管理）
// 存储 token 与用户信息（id, username, realName, role, avatar ...），并持久化到 localStorage。
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readStoredUser()
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    role: (state) => state.user?.role,
    roleName: (state) => state.user?.roleName,
    realName: (state) => state.user?.realName || state.user?.username
  },
  actions: {
    // 登录成功：写入 token 与用户信息，并持久化
    setLogin(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem(TOKEN_KEY, token)
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    },
    // 刷新本地用户信息（如修改昵称后）
    setUser(user) {
      this.user = user
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    },
    // 退出登录：清理状态
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
