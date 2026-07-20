<template>
  <el-container class="layout">
    <!-- 左侧可折叠菜单 -->
    <el-aside :width="collapsed ? '64px' : '210px'" class="aside">
      <div class="logo-mini">
        <span v-if="!collapsed">Aether<span>Learn</span></span>
        <span v-else>A</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="transparent"
        class="menu"
        @select="onSelect"
      >
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon v-if="m.icon"><component :is="m.icon" /></el-icon>
          <template #title>{{ m.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 右侧主体 -->
    <el-container>
      <!-- 顶部导航 -->
      <el-header class="header">
        <el-icon class="collapse-btn" @click="collapsed = !collapsed">
          <Fold v-if="!collapsed" />
          <Expand v-else />
        </el-icon>
        <div class="header-title">{{ headerTitle }}</div>
        <div class="header-right">
          <el-dropdown @command="onCommand">
            <span class="user-box">
              <el-avatar :size="32" class="avatar">{{ userInitial }}</el-avatar>
              <span class="uname">{{ userStore.realName }}（{{ roleText }}）</span>
              <el-icon><CaretBottom /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore, ROLE } from '../store/user'
import { ElMessageBox } from 'element-plus'
import { Fold, Expand, CaretBottom, DataLine, Reading, User, Files, EditPen, ChatDotRound, School } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)

// 用户首字母（头像占位）
const userInitial = computed(() => (userStore.realName?.[0] || userStore.user?.username?.[0] || 'U'))
const roleText = computed(() => userStore.roleName || '用户')
const headerTitle = computed(() => route.meta.title || 'AetherLearn')

// 当前激活菜单
const activeMenu = computed(() => route.path)

// 按角色动态菜单（F-AUTH-04 角色权限）
const allMenus = [
  { path: '/dashboard', title: '数据看板', icon: DataLine, roles: [ROLE.ADMIN, ROLE.TEACHER] },
  { path: '/course', title: '课程管理', icon: Reading, roles: [ROLE.ADMIN, ROLE.TEACHER] },
  { path: '/user', title: '用户管理', icon: User, roles: [ROLE.ADMIN] },
  { path: '/knowledge', title: '课程知识库', icon: Files, roles: [ROLE.TEACHER] },
  { path: '/homework', title: '作业管理', icon: EditPen, roles: [ROLE.TEACHER] },
  { path: '/student-dashboard', title: '学习中心', icon: School, roles: [ROLE.STUDENT] },
  { path: '/my-course', title: '我的课程', icon: Reading, roles: [ROLE.STUDENT] },
  { path: '/qa', title: '智能问答', icon: ChatDotRound, roles: [ROLE.STUDENT] }
]
const menus = computed(() => allMenus.filter((m) => m.roles.includes(userStore.role)))

function onSelect(path) {
  if (path !== route.path) router.push(path)
}

function onCommand(cmd) {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
      .then(() => {
        userStore.logout()
        router.push('/login')
      })
      .catch(() => {})
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside {
  background: linear-gradient(180deg, #5b6ef5 0%, #7c4dff 100%);
  transition: width 0.2s;
  overflow: hidden;
}
.logo-mini {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 800;
  font-size: 20px;
  letter-spacing: 1px;
}
.logo-mini span { opacity: 0.85; }
.menu { border-right: none; background: transparent !important; }
.menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.85);
}
.menu :deep(.el-menu-item.is-active) {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  border-radius: 8px;
}
.header {
  display: flex;
  align-items: center;
  background: #fff;
  box-shadow: var(--shadow);
  position: relative;
  z-index: 2;
}
.collapse-btn { cursor: pointer; font-size: 20px; color: var(--text-2); }
.header-title { margin-left: 16px; font-weight: 600; font-size: 16px; }
.header-right { margin-left: auto; }
.user-box { display: flex; align-items: center; cursor: pointer; gap: 8px; }
.avatar { background: linear-gradient(135deg, #5b6ef5, #7c4dff); color: #fff; }
.uname { font-size: 14px; color: var(--text-1); }
.main { background: var(--bg); padding: 20px; }
</style>
