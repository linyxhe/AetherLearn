<template>
  <div class="profile-page">
    <div class="hero">
      <div>
        <div class="eyebrow">个人中心</div>
        <h2>把账号、学习档案和使用偏好放在一起</h2>
        <p>这里可以修改头像、姓名、邮箱、手机号和密码，也能查看当前角色的学习或教学概览。</p>
      </div>
      <n-card class="hero-card" :bordered="false">
        <div class="hero-card-title">当前身份</div>
        <div class="hero-card-value">{{ profile.realName || userStore.realName }}</div>
        <div class="hero-card-sub">{{ roleText }} · {{ profile.username }}</div>
      </n-card>
    </div>

    <n-grid :cols="3" :x-gap="16" :y-gap="16" responsive="screen">
      <n-grid-item v-for="item in summaryCards" :key="item.label">
        <n-card :bordered="false" class="stat-card">
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </n-card>
      </n-grid-item>
    </n-grid>

    <n-grid :cols="2" :x-gap="16" :y-gap="16" responsive="screen" class="content-grid">
      <n-grid-item>
        <n-card :bordered="false" class="panel">
          <div class="panel-title">账号资料</div>
          <n-form :model="profile" label-placement="left" label-width="88">
            <n-form-item label="头像">
              <div class="avatar-row">
                <div class="avatar-preview">
                  <img v-if="profile.avatar" :src="resolveUrl(profile.avatar)" alt="avatar" />
                  <span v-else>{{ avatarText }}</span>
                </div>
                <div class="avatar-actions">
                  <UploadFile
                    v-model="profile.avatar"
                    biz-type="avatar"
                    accept="image/*"
                    @upload-success="saveAvatar"
                  />
                  <div class="hint">头像上传成功后会立即保存，并同步显示到顶部导航栏。</div>
                </div>
              </div>
            </n-form-item>
            <n-form-item label="姓名">
              <n-input v-model:value="profile.realName" placeholder="请输入姓名" />
            </n-form-item>
            <n-form-item label="邮箱">
              <n-input v-model:value="profile.email" placeholder="请输入邮箱" />
            </n-form-item>
            <n-form-item label="手机号">
              <n-input v-model:value="profile.phone" placeholder="请输入手机号" />
            </n-form-item>
            <n-form-item label="新密码">
              <n-input v-model:value="profile.password" type="password" show-password-on="click" placeholder="留空表示不修改密码" />
            </n-form-item>
          </n-form>
          <template #footer>
            <n-space justify="space-between" align="center">
              <div class="footer-hint">保存后会同步更新当前登录状态。</div>
              <n-button type="primary" :loading="saving" @click="onSave">保存资料</n-button>
            </n-space>
          </template>
        </n-card>
      </n-grid-item>

      <n-grid-item>
        <n-card :bordered="false" class="panel">
          <div class="panel-title">使用偏好</div>
          <div class="pref-list">
            <div class="pref-row">
              <div>
                <div class="pref-title">默认阅读模式</div>
                <div class="pref-desc">课程章节打开时的默认阅读偏好。</div>
              </div>
              <n-select v-model:value="prefs.readerMode" :options="readerOptions" style="width: 160px" @update:value="savePrefs" />
            </div>
            <div class="pref-row">
              <div>
                <div class="pref-title">通知提醒</div>
                <div class="pref-desc">课程公告、作业提醒和批改完成时是否提醒。</div>
              </div>
              <n-switch v-model:value="prefs.notifyEnabled" @update:value="savePrefs" />
            </div>
          </div>
        </n-card>

        <n-card :bordered="false" class="panel">
          <div class="panel-title">档案摘要</div>
          <div v-if="isStudent" class="archive-box">
            <div class="archive-row"><span>平均正确率</span><b>{{ studentSummary.avgAccuracy }}%</b></div>
            <div class="archive-row"><span>学习活跃度</span><b>{{ studentSummary.activityCount }} 次</b></div>
            <div class="archive-row"><span>已加入课程</span><b>{{ studentSummary.courseCount }} 门</b></div>
            <div class="archive-row"><span>个性建议</span><b>{{ studentSummary.suggestionCount }} 条</b></div>
          </div>
          <div v-else class="archive-box">
            <div class="archive-row"><span>课程数</span><b>{{ teacherSummary.courseCount }}</b></div>
            <div class="archive-row"><span>学生数</span><b>{{ teacherSummary.studentCount }}</b></div>
            <div class="archive-row"><span>作业数</span><b>{{ teacherSummary.assignmentCount }}</b></div>
            <div class="archive-row"><span>问答数</span><b>{{ teacherSummary.qaCount }}</b></div>
          </div>
        </n-card>

        <n-card v-if="isStudent" :bordered="false" class="panel">
          <div class="panel-title">最近学习建议</div>
          <div class="suggestion-list">
            <div v-for="item in suggestions" :key="item.content" class="suggestion-item">
              <n-tag size="small" type="info" round>{{ item.type }}</n-tag>
              <span>{{ item.content }}</span>
            </div>
            <n-empty v-if="suggestions.length === 0" description="暂无建议" />
          </div>
        </n-card>
      </n-grid-item>
    </n-grid>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useUserStore, ROLE } from '../store/user'
import { NButton, NCard, NEmpty, NForm, NFormItem, NGrid, NGridItem, NInput, NSelect, NSpace, NSwitch, NTag, createDiscreteApi } from 'naive-ui'
import UploadFile from '../components/UploadFile.vue'
import { getUserInfo, updateUser } from '../api/user'
import { getDashboardStat, getAnalyticsOverview } from '../api/dashboard'
import { resolveApplicationUrl } from '../utils/url'

const { message } = createDiscreteApi(['message'])
const userStore = useUserStore()
const saving = ref(false)
const avatarSaving = ref(false)
const profile = reactive({
  username: '',
  realName: '',
  avatar: '',
  email: '',
  phone: '',
  password: ''
})
const prefs = reactive({
  readerMode: localStorage.getItem('aether-reader-mode') || 'double',
  notifyEnabled: localStorage.getItem('aether-notify-enabled') !== 'false'
})
const readerOptions = [
  { label: '左右双栏', value: 'double' },
  { label: '沉浸阅读', value: 'immersive' },
  { label: '普通模式', value: 'normal' }
]
const summaryCards = ref([])
const suggestions = ref([])
const studentSummary = reactive({ avgAccuracy: 0, activityCount: 0, courseCount: 0, suggestionCount: 0 })
const teacherSummary = reactive({ courseCount: 0, studentCount: 0, assignmentCount: 0, qaCount: 0 })

const isStudent = computed(() => userStore.role === ROLE.STUDENT)
const roleText = computed(() => userStore.roleName || '用户')
const avatarText = computed(() => (profile.realName || userStore.realName || 'U').slice(0, 1))

function resolveUrl(url) {
  return resolveApplicationUrl(url)
}

async function loadProfile() {
  const info = await getUserInfo()
  Object.assign(profile, info || {})
  // 页面刷新后以数据库档案覆盖本地登录快照，确保顶部导航栏同步显示最新头像。
  if (info) userStore.setUser(info)
  profile.password = ''
}

async function loadSummary() {
  if (isStudent.value) {
    const data = await getAnalyticsOverview()
    const o = data?.overview || {}
    studentSummary.avgAccuracy = Number(o.avgAccuracy || 0).toFixed(1)
    studentSummary.activityCount = o.activityCount || 0
    studentSummary.courseCount = o.courseCount || 0
    studentSummary.suggestionCount = (data?.suggestions || []).length
    suggestions.value = data?.suggestions || []
    summaryCards.value = [
      { label: '平均正确率', value: `${studentSummary.avgAccuracy}%` },
      { label: '学习活跃度', value: `${studentSummary.activityCount} 次` },
      { label: '已加入课程', value: `${studentSummary.courseCount} 门` }
    ]
  } else {
    const data = await getDashboardStat()
    const o = data?.overview || {}
    teacherSummary.courseCount = o.courseCount || 0
    teacherSummary.studentCount = o.studentCount || 0
    teacherSummary.assignmentCount = o.assignmentCount || 0
    teacherSummary.qaCount = o.qaCount || 0
    summaryCards.value = [
      { label: '课程数', value: teacherSummary.courseCount },
      { label: '学生数', value: teacherSummary.studentCount },
      { label: '作业数', value: teacherSummary.assignmentCount }
    ]
  }
}

async function onSave() {
  if (!profile.realName) {
    message.warning('请输入姓名')
    return
  }
  saving.value = true
  try {
    const payload = {
      realName: profile.realName,
      avatar: profile.avatar,
      email: profile.email,
      phone: profile.phone,
      password: profile.password || ''
    }
    const data = await updateUser(payload)
    userStore.setUser(data)
    message.success('资料已更新')
    profile.password = ''
  } finally {
    saving.value = false
  }
}

// 头像文件上传完成后立即写入用户表，避免用户遗漏“保存资料”操作。
async function saveAvatar(url) {
  if (!url || avatarSaving.value) return
  avatarSaving.value = true
  try {
    profile.avatar = url
    const data = await updateUser({ avatar: url })
    userStore.setUser(data)
    message.success('头像已更新')
  } finally {
    avatarSaving.value = false
  }
}

function savePrefs() {
  localStorage.setItem('aether-reader-mode', prefs.readerMode)
  localStorage.setItem('aether-notify-enabled', String(prefs.notifyEnabled))
  message.success('偏好已保存')
}

onMounted(async () => {
  await loadProfile()
  await loadSummary()
})
</script>

<style scoped>
.profile-page { display: flex; flex-direction: column; gap: 16px; }
.hero { display: grid; grid-template-columns: 1.4fr 0.9fr; gap: 16px; }
.eyebrow { color: #4cb6c2; font-size: 12px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.hero h2 { margin: 8px 0 10px; font-size: 30px; color: #16313b; }
.hero p { margin: 0; color: #5f6b73; max-width: 58ch; line-height: 1.7; }
.hero-card, .stat-card, .panel { border-radius: 18px; box-shadow: 0 16px 40px rgba(48, 102, 107, 0.08); }
.hero-card { background: linear-gradient(135deg, #f4fbfb 0%, #eaf8f7 100%); }
.hero-card-title { color: #5f6b73; font-size: 12px; margin-bottom: 8px; }
.hero-card-value { font-size: 22px; font-weight: 700; color: #18323d; margin-bottom: 6px; }
.hero-card-sub { color: #5f6b73; font-size: 13px; line-height: 1.6; }
.stat-card { background: #fff; padding: 18px 20px; }
.stat-value { font-size: 30px; font-weight: 700; color: #18323d; }
.stat-label { color: #6b7280; margin-top: 6px; font-size: 13px; }
.panel { background: rgba(255,255,255,0.9); }
.panel-title { font-weight: 700; color: #18323d; margin-bottom: 12px; }
.content-grid { align-items: start; }
.avatar-row { display: flex; align-items: center; gap: 16px; }
.avatar-preview { width: 92px; height: 92px; border-radius: 18px; background: linear-gradient(135deg, #42B5BB, #87D8C9); display: flex; align-items: center; justify-content: center; color: #fff; font-size: 30px; font-weight: 700; overflow: hidden; flex: none; }
.avatar-preview img { width: 100%; height: 100%; object-fit: cover; display: block; }
.avatar-actions { flex: 1; min-width: 0; }
.hint, .footer-hint { color: #6b7280; font-size: 12px; line-height: 1.6; }
.pref-list { display: flex; flex-direction: column; gap: 16px; }
.pref-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 0; border-bottom: 1px solid rgba(66,181,187,0.12); }
.pref-row:last-child { border-bottom: 0; }
.pref-title { font-weight: 700; color: #18323d; }
.pref-desc { color: #6b7280; font-size: 12px; margin-top: 4px; }
.archive-box { display: flex; flex-direction: column; gap: 12px; }
.archive-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 14px; border-radius: 14px; background: #f7fbfb; color: #1f2937; }
.archive-row b { color: #18323d; }
.suggestion-list { display: flex; flex-direction: column; gap: 10px; }
.suggestion-item { display: flex; gap: 10px; align-items: flex-start; line-height: 1.6; color: #1f2937; }
@media (max-width: 900px) {
  .hero, .content-grid { grid-template-columns: 1fr; }
  .avatar-row, .pref-row { flex-direction: column; align-items: flex-start; }
}
</style>
