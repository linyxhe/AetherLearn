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
      // 教师/管理员：看板入口，管理员展示平台视角，教师展示教学视角
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '数据看板', roles: [ROLE.ADMIN, ROLE.TEACHER] } },
      // 教师：教学业务工作台
      { path: 'ai-advice', name: 'AiAdvice', component: () => import('../views/AiAdvice.vue'), meta: { title: 'AI 教学建议', roles: [ROLE.TEACHER] } },
      { path: 'paper-builder', name: 'PaperBuilder', component: () => import('../views/PaperBuilder.vue'), meta: { title: '智能组卷', roles: [ROLE.TEACHER] } },
      // 学生：学习中心
      { path: 'student-dashboard', name: 'StudentDashboard', component: () => import('../views/StudentDashboard.vue'), meta: { title: '学习中心', roles: [ROLE.STUDENT] } },
      // 教师：课程建设与教学管理
      { path: 'course', name: 'Course', component: () => import('../views/Course.vue'), meta: { title: '课程管理', roles: [ROLE.TEACHER] } },
      // 管理员：用户管理（F-AUTH-03）
      { path: 'user', name: 'User', component: () => import('../views/UserAdmin.vue'), meta: { title: '用户管理', roles: [ROLE.ADMIN] } },
      // 管理员：系统配置（L7）
      { path: 'config', name: 'SystemConfig', component: () => import('../views/SystemConfig.vue'), meta: { title: '系统配置', roles: [ROLE.ADMIN] } },
      { path: 'knowledge', name: 'Knowledge', component: () => import('../views/Knowledge.vue'), meta: { title: '课程知识库', roles: [ROLE.TEACHER] } },
      { path: 'homework', name: 'Homework', component: () => import('../views/Homework.vue'), meta: { title: '作业管理', roles: [ROLE.TEACHER] } },
      { path: 'notice', name: 'Notice', component: () => import('../views/Notice.vue'), meta: { title: '课程公告', roles: [ROLE.TEACHER, ROLE.STUDENT] } },
      { path: 'knowledge-graph', name: 'KnowledgeGraph', component: () => import('../views/KnowledgeGraph.vue'), meta: { title: '知识点图谱', roles: [ROLE.TEACHER, ROLE.STUDENT] } },
      { path: 'my-course', name: 'MyCourse', component: () => import('../views/MyCourse.vue'), meta: { title: '我的课程', roles: [ROLE.STUDENT] } },
      { path: 'my-homework', name: 'MyHomework', component: () => import('../views/MyHomework.vue'), meta: { title: '我的作业', roles: [ROLE.STUDENT] } },
      { path: 'wrong-book', name: 'WrongBook', component: () => import('../views/WrongBook.vue'), meta: { title: '错题本', roles: [ROLE.STUDENT] } },
      { path: 'todo', name: 'Todo', component: () => import('../views/Todo.vue'), meta: { title: '学习计划', roles: [ROLE.STUDENT] } },
      { path: 'ai-report', name: 'AiReport', component: () => import('../views/AiReport.vue'), meta: { title: 'AI 学习报告', roles: [ROLE.STUDENT] } },
      { path: 'profile', name: 'Profile', component: () => import('../views/Profile.vue'), meta: { title: '个人中心', roles: [ROLE.ADMIN, ROLE.TEACHER, ROLE.STUDENT] } },
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
