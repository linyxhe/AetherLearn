<template>
  <n-layout class="shell" has-sider>
    <n-layout-sider
      :collapsed="collapsed"
      :collapsed-width="72"
      :width="246"
      collapse-mode="width"
      :native-scrollbar="false"
      class="sider"
      bordered
    >
      <div class="brand" :class="{ 'brand-collapsed': collapsed }">
        <div class="brand-mark">{{ brandMark }}</div>
        <div v-if="!collapsed" class="brand-copy">
          <div class="brand-name">AetherLearn</div>
          <div class="brand-sub">{{ roleIntro }}</div>
        </div>
      </div>

<!--      <div v-if="!collapsed" class="role-panel">-->
<!--        <div class="role-title">{{ roleLabel }}</div>-->
<!--        <div class="role-desc">{{ roleDescription }}</div>-->
<!--      </div>-->

      <n-menu
        v-if="!collapsed"
        :collapsed="collapsed"
        :collapsed-width="72"
        :collapsed-icon-size="22"
        :options="menuOptions"
        :value="activeMenu"
        :indent="18"
        :root-indent="18"
        :render-label="renderMenuLabel"
        class="nav"
        @update:value="onSelect"
      />
      <div v-else class="collapsed-nav" aria-label="主导航">
        <n-tooltip v-for="item in visibleMenus" :key="item.path" placement="right">
          <template #trigger>
            <button
              type="button"
              class="collapsed-nav-item"
              :class="{ active: activeMenu === item.path }"
              :aria-label="menuTitle(item)"
              @click="onSelect(item.path)"
            >
              <n-icon :size="24"><component :is="item.icon" /></n-icon>
            </button>
          </template>
          {{ menuTitle(item) }}
        </n-tooltip>
      </div>
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
            <n-dropdown :options="dropdownOptions" trigger="click" :show-arrow="true" @select="onCommand">
              <div class="user-box">
                <n-avatar
                  v-if="userAvatarUrl"
                  round
                  :size="34"
                  :src="userAvatarUrl"
                  :style="{ background: 'linear-gradient(135deg,#42B5BB,#87D8C9)' }"
                />
                <n-avatar
                  v-else
                  round
                  :size="34"
                  :style="{ background: 'linear-gradient(135deg,#42B5BB,#87D8C9)' }"
                >
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
import { computed, h, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore, ROLE } from '../store/user'
import { logout as logoutApi } from '../api/auth'
import { getUserInfo } from '../api/user'
import { resolveApplicationUrl } from '../utils/url'
import { NAvatar, NButton, NDropdown, NIcon, NLayout, NLayoutContent, NLayoutHeader, NLayoutSider, NMenu, NSpace, NTag, NTooltip, createDiscreteApi } from 'naive-ui'
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
const userAvatarUrl = computed(() => {
  const avatar = userStore.user?.avatar
  if (!avatar) return undefined
  return resolveApplicationUrl(avatar)
})
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

// 布局初始化时从数据库刷新档案，避免本地登录缓存中的旧头像长期停留在顶部导航栏。
onMounted(async () => {
  try {
    const profile = await getUserInfo()
    if (profile) userStore.setUser(profile)
  } catch (_) {
    // 接口异常已由请求拦截器提示；保留当前本地档案作为降级展示。
  }
})

const allMenus = [
  { path: '/dashboard', title: '数据看板', adminTitle: '平台看板', icon: DataLine, roles: [ROLE.ADMIN, ROLE.TEACHER] },
  { path: '/ai-advice', title: 'AI 教学建议', icon: Memo, roles: [ROLE.TEACHER] },
  { path: '/paper-builder', title: '智能组卷', icon: MagicStick, roles: [ROLE.TEACHER] },
  { path: '/course', title: '课程管理', icon: Reading, roles: [ROLE.TEACHER, ROLE.ADMIN] },
  { path: '/knowledge', title: '课程知识库', icon: Files, roles: [ROLE.TEACHER, ROLE.ADMIN] },
  { path: '/homework', title: '作业管理', icon: EditPen, roles: [ROLE.TEACHER, ROLE.ADMIN] },
  { path: '/notice', title: '课程公告', icon: Bell, roles: [ROLE.TEACHER, ROLE.ADMIN, ROLE.STUDENT] },
  { path: '/knowledge-graph', title: '知识点图谱', icon: Share, roles: [ROLE.TEACHER, ROLE.ADMIN, ROLE.STUDENT] },
  { path: '/student-dashboard', title: '学习中心', icon: School, roles: [ROLE.STUDENT] },
  { path: '/my-course', title: '我的课程', icon: Reading, roles: [ROLE.STUDENT] },
  { path: '/my-notes', title: '我的笔记', icon: Memo, roles: [ROLE.STUDENT] },
  { path: '/my-homework', title: '我的作业', icon: EditPen, roles: [ROLE.STUDENT] },
  { path: '/wrong-book', title: '错题本', icon: WarningFilled, roles: [ROLE.STUDENT] },
  { path: '/todo', title: '学习计划', icon: Calendar, roles: [ROLE.STUDENT] },
  { path: '/ai-report', title: 'AI 学习报告', icon: TrendCharts, roles: [ROLE.STUDENT] },
  { path: '/qa', title: '智能问答', icon: ChatDotRound, roles: [ROLE.STUDENT] },
  { path: '/profile', title: '个人中心', icon: User, roles: [ROLE.ADMIN, ROLE.TEACHER, ROLE.STUDENT] },
  { path: '/user', title: '用户管理', icon: User, roles: [ROLE.ADMIN] },
  { path: '/config', title: '系统配置', icon: Setting, roles: [ROLE.ADMIN] }
]
const visibleMenus = computed(() => allMenus.filter((item) => item.roles.includes(userStore.role)))
const menuOptions = computed(() => visibleMenus.value.map((item) => ({
  label: menuTitle(item),
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

// 根据当前角色提供一致的菜单文案，展开与折叠状态共用。
function menuTitle(item) {
  return isAdmin.value && item.adminTitle ? item.adminTitle : item.title
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
        try {
          await logoutApi()
        } catch (_) {
          // 服务端注销失败时仍清理本地会话，避免用户被卡在当前账号。
        } finally {
          userStore.logout()
          router.push('/login')
        }
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
.brand-collapsed { justify-content: center; padding: 20px 0 14px; }
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
.nav :deep(.n-menu-item-content__icon),
.nav :deep(.n-menu-item-content__icon .n-icon),
.nav :deep(.n-menu-item-content__icon svg) {
  color: rgba(255,255,255,0.96) !important;
  fill: currentColor !important;
  filter: drop-shadow(0 1px 2px rgba(17, 77, 83, 0.18));
}
.nav :deep(.n-menu-item-content--collapsed) {
  width: 46px;
  margin: 0 auto;
  padding-left: 0 !important;
  justify-content: center;
}
.nav :deep(.n-menu-item-content--collapsed .n-menu-item-content__icon) {
  margin-right: 0 !important;
}
.nav :deep(.n-menu-item-content--selected) {
  color: #fff;
  background: rgba(255,255,255,0.22);
  border-radius: 12px;
  box-shadow: inset 3px 0 0 rgba(255,255,255,0.92);
}
.nav :deep(.n-menu-item-content:hover) { background: rgba(255,255,255,0.12); border-radius: 12px; }
.collapsed-nav { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 8px 13px; }
.collapsed-nav-item {
  width: 46px;
  height: 46px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  color: rgba(255,255,255,0.96);
  background: transparent;
  border: 0;
  border-radius: 12px;
  cursor: pointer;
  transition: background-color .18s ease, transform .18s ease;
}
.collapsed-nav-item :deep(svg) { fill: currentColor; filter: drop-shadow(0 1px 2px rgba(17, 77, 83, 0.18)); }
.collapsed-nav-item:hover { background: rgba(255,255,255,0.14); transform: translateY(-1px); }
.collapsed-nav-item:focus-visible { outline: 2px solid #fff; outline-offset: 2px; }
.collapsed-nav-item.active { background: rgba(255,255,255,0.24); box-shadow: inset 3px 0 0 rgba(255,255,255,0.92); }
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
