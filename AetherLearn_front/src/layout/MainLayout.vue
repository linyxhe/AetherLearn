<template>
  <n-layout class="shell" has-sider>
    <n-layout-sider
      :collapsed="collapsed"
      :collapsed-width="72"
      :width="246"
      :native-scrollbar="false"
      class="sider"
      bordered
    >
      <div class="brand">
        <div class="brand-mark">{{ brandMark }}</div>
        <div v-if="!collapsed" class="brand-copy">
          <div class="brand-name">AetherLearn</div>
          <div class="brand-sub">{{ roleIntro }}</div>
        </div>
      </div>

      <div v-if="!collapsed" class="role-panel">
        <div class="role-title">{{ roleLabel }}</div>
        <div class="role-desc">{{ roleDescription }}</div>
      </div>

      <n-menu
        :collapsed="collapsed"
        :collapsed-width="72"
        :options="menuOptions"
        :value="activeMenu"
        :indent="18"
        :root-indent="18"
        :render-label="renderMenuLabel"
        class="nav"
        @update:value="onSelect"
      />
    </n-layout-sider>

    <n-layout>
      <n-layout-header class="header" bordered>
        <div class="header-left">
          <n-button quaternary circle size="small" @click="collapsed = !collapsed">
            <template #icon>
              <n-icon><Fold v-if="!collapsed" /><Expand v-else /></n-icon>
            </template>
          </n-button>
          <div class="header-copy">
            <div class="header-title">{{ headerTitle }}</div>
            <div class="header-subtitle">{{ headerSubtitle }}</div>
          </div>
        </div>
        <div class="header-right">
          <n-space align="center" :size="12">
            <n-tag v-if="isTeacher" type="success" round>教师工作台</n-tag>
            <n-tag v-else-if="isStudent" type="info" round>学生学习端</n-tag>
            <n-dropdown :options="dropdownOptions" @select="onCommand">
              <div class="user-box">
                <n-avatar round :size="34" :style="{ background: 'linear-gradient(135deg,#42B5BB,#87D8C9)' }">
                  {{ userInitial }}
                </n-avatar>
                <div class="user-copy">
                  <div class="user-name">{{ userStore.realName }}</div>
                  <div class="user-role">{{ roleText }}</div>
                </div>
                <n-icon><CaretBottom /></n-icon>
              </div>
            </n-dropdown>
          </n-space>
        </div>
      </n-layout-header>

      <n-layout-content class="content">
        <div class="content-shell">
          <router-view />
        </div>
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>

<script setup>
import { computed, h, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore, ROLE } from '../store/user'
import { logout as logoutApi } from '../api/auth'
import { NAvatar, NButton, NDropdown, NIcon, NLayout, NLayoutContent, NLayoutHeader, NLayoutSider, NMenu, NSpace, NTag, createDiscreteApi } from 'naive-ui'
import { Bell, DataLine, Reading, User, Files, EditPen, ChatDotRound, School, Fold, Expand, CaretBottom, WarningFilled, Calendar, TrendCharts, Memo, Share, MagicStick, Setting } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const { dialog } = createDiscreteApi(['dialog'])
const userStore = useUserStore()
const collapsed = ref(false)

const isTeacher = computed(() => userStore.role === ROLE.TEACHER)
const isStudent = computed(() => userStore.role === ROLE.STUDENT)
const isAdmin = computed(() => userStore.role === ROLE.ADMIN)
const roleText = computed(() => userStore.roleName || '用户')
const userInitial = computed(() => (userStore.realName?.[0] || userStore.user?.username?.[0] || 'U'))
const activeMenu = computed(() => route.path)
const headerTitle = computed(() => {
  if (isAdmin.value && route.path === '/dashboard') return '平台看板'
  return route.meta.title || 'AetherLearn'
})
const headerSubtitle = computed(() => {
  if (isTeacher.value) return '课程、章节、作业、知识库都围绕教学流展开'
  if (isStudent.value) return '从课程进入章节，按路径完成学习与反馈'
  return '平台账号、权限配置和运行状态集中维护'
})
const roleLabel = computed(() => {
  if (isTeacher.value) return '教师角色'
  if (isStudent.value) return '学生角色'
  return '管理员角色'
})
const roleDescription = computed(() => {
  if (isTeacher.value) return '适合课程编排、学习资源维护、作业发布与批改。'
  if (isStudent.value) return '适合课程进入、章节学习、答疑与作业完成。'
  return '适合系统配置、账号权限、平台运行维护，不参与教师课程编排。'
})
const roleIntro = computed(() => {
  if (isTeacher.value) return '教学编排与学习分析'
  if (isStudent.value) return '学习路径与进度反馈'
  return '系统维护与权限管理'
})
const brandMark = computed(() => (isTeacher.value ? 'T' : isStudent.value ? 'S' : 'A'))

const allMenus = [
  { path: '/dashboard', title: '数据看板', adminTitle: '平台看板', icon: DataLine, roles: [ROLE.ADMIN, ROLE.TEACHER] },
  { path: '/ai-advice', title: 'AI 教学建议', icon: Memo, roles: [ROLE.TEACHER] },
  { path: '/paper-builder', title: '智能组卷', icon: MagicStick, roles: [ROLE.TEACHER] },
  { path: '/course', title: '课程管理', icon: Reading, roles: [ROLE.TEACHER] },
  { path: '/knowledge', title: '课程知识库', icon: Files, roles: [ROLE.TEACHER] },
  { path: '/homework', title: '作业管理', icon: EditPen, roles: [ROLE.TEACHER] },
  { path: '/notice', title: '课程公告', icon: Bell, roles: [ROLE.TEACHER, ROLE.STUDENT] },
  { path: '/knowledge-graph', title: '知识点图谱', icon: Share, roles: [ROLE.TEACHER, ROLE.STUDENT] },
  { path: '/student-dashboard', title: '学习中心', icon: School, roles: [ROLE.STUDENT] },
  { path: '/my-course', title: '我的课程', icon: Reading, roles: [ROLE.STUDENT] },
  { path: '/my-homework', title: '我的作业', icon: EditPen, roles: [ROLE.STUDENT] },
  { path: '/wrong-book', title: '错题本', icon: WarningFilled, roles: [ROLE.STUDENT] },
  { path: '/todo', title: '学习计划', icon: Calendar, roles: [ROLE.STUDENT] },
  { path: '/ai-report', title: 'AI 学习报告', icon: TrendCharts, roles: [ROLE.STUDENT] },
  { path: '/qa', title: '智能问答', icon: ChatDotRound, roles: [ROLE.STUDENT] },
  { path: '/profile', title: '个人中心', icon: User, roles: [ROLE.ADMIN, ROLE.TEACHER, ROLE.STUDENT] },
  { path: '/user', title: '用户管理', icon: User, roles: [ROLE.ADMIN] },
  { path: '/config', title: '系统配置', icon: Setting, roles: [ROLE.ADMIN] }
]
const menuOptions = computed(() => allMenus.filter((item) => item.roles.includes(userStore.role)).map((item) => ({
  label: isAdmin.value && item.adminTitle ? item.adminTitle : item.title,
  key: item.path,
  icon: () => h(NIcon, null, { default: () => h(item.icon) })
})))

const dropdownOptions = [
  { label: '个人中心', key: '/profile' },
  { label: '退出登录', key: 'logout' }
]

function renderMenuLabel(option) {
  return option.label
}

function onSelect(path) {
  if (path !== route.path) router.push(path)
}

function onCommand(cmd) {
  if (cmd === '/profile') {
    router.push('/profile')
    return
  }
  if (cmd === 'logout') {
    dialog.warning({
      title: '提示',
      content: '确定要退出登录吗？',
      positiveText: '退出',
      negativeText: '取消',
      onPositiveClick: async () => {
        await logoutApi()
        userStore.logout()
        router.push('/login')
      }
    })
  }
}
</script>

<style scoped>
.shell { height: 100vh; background: linear-gradient(180deg, #f7fbfb 0%, #eef7f7 100%); }
.sider {
  background:
    radial-gradient(circle at top, rgba(255,255,255,0.28), transparent 45%),
    linear-gradient(180deg, #2f7f86 0%, #42B5BB 46%, #87D8C9 100%);
  color: #fff;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 18px 14px;
}
.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: rgba(255,255,255,0.22);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
}
.brand-name { font-size: 18px; font-weight: 700; }
.brand-sub { font-size: 12px; opacity: 0.84; margin-top: 3px; }
.role-panel {
  margin: 0 18px 12px;
  padding: 14px;
  border-radius: 14px;
  background: rgba(255,255,255,0.12);
  backdrop-filter: blur(8px);
}
.role-title { font-weight: 700; margin-bottom: 4px; }
.role-desc { font-size: 12.5px; line-height: 1.6; opacity: 0.88; }
.nav :deep(.n-menu-item-content) { color: rgba(255,255,255,0.88); }
.nav :deep(.n-menu-item-content--selected) {
  color: #fff;
  background: rgba(255,255,255,0.18);
  border-radius: 12px;
}
.nav :deep(.n-menu-item-content:hover) { background: rgba(255,255,255,0.12); border-radius: 12px; }
.header {
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 22px;
  background: rgba(255,255,255,0.82);
  backdrop-filter: blur(14px);
}
.header-left { display: flex; align-items: center; gap: 12px; }
.header-copy { display: flex; flex-direction: column; gap: 2px; }
.header-title { font-size: 16px; font-weight: 700; color: #1f2937; }
.header-subtitle { font-size: 12px; color: #6b7280; }
.user-box { display: flex; align-items: center; gap: 10px; cursor: pointer; padding: 4px 8px; border-radius: 14px; }
.user-box:hover { background: rgba(66,181,187,0.08); }
.user-copy { text-align: left; }
.user-name { font-size: 13px; font-weight: 700; color: #1f2937; }
.user-role { font-size: 12px; color: #6b7280; }
.content { padding: 22px; }
.content-shell {
  min-height: calc(100vh - 116px);
  border-radius: 22px;
  background: rgba(255,255,255,0.72);
  box-shadow: 0 20px 60px rgba(33, 86, 91, 0.08);
  border: 1px solid rgba(255,255,255,0.8);
  padding: 22px;
}
@media (max-width: 900px) {
  .content { padding: 14px; }
  .content-shell { padding: 16px; border-radius: 18px; }
}
</style>
