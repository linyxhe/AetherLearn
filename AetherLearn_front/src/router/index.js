import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore, homePathByRole, ROLE } from '../store/user'

// 路由表（F-AUTH / 路由与权限守卫）
const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      // 教师/管理员：数据看板
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '数据看板', roles: [ROLE.ADMIN, ROLE.TEACHER] } },
      // 学生：学习中心
      { path: 'student-dashboard', name: 'StudentDashboard', component: () => import('../views/StudentDashboard.vue'), meta: { title: '学习中心', roles: [ROLE.STUDENT] } },
      // 课程管理（教师/管理员）
      { path: 'course', name: 'Course', component: () => import('../views/Course.vue'), meta: { title: '课程管理', roles: [ROLE.ADMIN, ROLE.TEACHER] } },
      // 以下为后续波次占位（第一波未实现，统一引导至规划页）
      { path: 'user', name: 'User', component: () => import('../views/ComingSoon.vue'), meta: { title: '用户管理', roles: [ROLE.ADMIN] } },
      { path: 'knowledge', name: 'Knowledge', component: () => import('../views/Knowledge.vue'), meta: { title: '课程知识库', roles: [ROLE.TEACHER] } },
      { path: 'homework', name: 'Homework', component: () => import('../views/Homework.vue'), meta: { title: '作业管理', roles: [ROLE.TEACHER] } },
      { path: 'my-course', name: 'MyCourse', component: () => import('../views/MyHomework.vue'), meta: { title: '我的作业', roles: [ROLE.STUDENT] } },
      { path: 'qa', name: 'Qa', component: () => import('../views/Qa.vue'), meta: { title: '智能问答', roles: [ROLE.STUDENT] } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：校验登录态与角色
router.beforeEach((to) => {
  const userStore = useUserStore()

  // 公开页面（登录页）
  if (to.meta.public) {
    // 已登录则直接进首页
    if (userStore.isLoggedIn) return homePathByRole(userStore.role)
    return true
  }

  // 未登录 → 跳登录
  if (!userStore.isLoggedIn) {
    return { path: '/login' }
  }

  // 角色校验：不匹配则回到本人首页
  const allowRoles = to.meta.roles
  if (allowRoles && !allowRoles.includes(userStore.role)) {
    return { path: homePathByRole(userStore.role) }
  }

  return true
})

export default router
