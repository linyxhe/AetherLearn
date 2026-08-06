<template>
  <div class="notice-page">
    <div class="hero">
      <div>
        <div class="eyebrow">课程公告</div>
        <h2>课程通知、作业提醒、资料更新统一在这里</h2>
        <p>教师发布后，学生会按课程看到最新公告；系统后续也可以继续扩展成消息中心。</p>
      </div>
      <n-card class="hero-card" :bordered="false">
        <div class="hero-card-title">当前课程</div>
        <div class="hero-card-value">{{ currentCourseName || '未选择课程' }}</div>
        <div class="hero-card-sub">{{ canManage ? '教师或管理员可发布公告' : '学生查看已加入课程公告' }}</div>
      </n-card>
    </div>

    <n-card :bordered="false" class="panel">
      <n-space justify="space-between" align="center" wrap>
        <n-select v-model:value="selectedCourse" :options="courseOptions" placeholder="选择课程" clearable style="min-width: 260px" @update:value="loadNotices" />
        <n-button v-if="canManage" type="primary" @click="openCreate">发布公告</n-button>
      </n-space>
    </n-card>

    <n-card :bordered="false" class="panel">
      <div class="panel-title">公告列表</div>
      <div class="notice-list">
        <n-card v-for="item in notices" :key="item.id" class="notice-card" :bordered="false">
          <div class="notice-head">
            <div>
              <div class="notice-title">{{ item.title }}</div>
              <div class="notice-meta">
                <n-tag type="info" round size="small">{{ typeText(item.noticeType) }}</n-tag>
                <span>{{ item.createTime }}</span>
              </div>
            </div>
            <n-space v-if="canManage">
              <n-button size="small" secondary @click="openEdit(item)">编辑</n-button>
              <n-button size="small" secondary type="error" :loading="deletingId === item.id" :disabled="deletingId !== null && deletingId !== item.id" @click="onDelete(item)">删除</n-button>
            </n-space>
          </div>
          <div class="notice-content">{{ item.content }}</div>
        </n-card>
      </div>
      <n-empty v-if="!loading && notices.length === 0" description="暂无公告" />
    </n-card>

    <n-modal v-model:show="visible" preset="card" :title="form.id ? '编辑公告' : '发布公告'" class="notice-modal">
      <n-form :model="form" label-placement="left" label-width="84">
        <n-form-item label="课程">
          <n-select v-model:value="form.courseId" :options="courseOptions" placeholder="选择课程" />
        </n-form-item>
        <n-form-item label="类型">
          <n-select v-model:value="form.noticeType" :options="typeOptions" />
        </n-form-item>
        <n-form-item label="标题">
          <n-input v-model:value="form.title" placeholder="请输入公告标题" />
        </n-form-item>
        <n-form-item label="内容">
          <n-input v-model:value="form.content" type="textarea" :autosize="{ minRows: 5, maxRows: 12 }" placeholder="请输入公告内容" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-space justify="end">
          <n-button @click="visible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="onSave">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useUserStore, ROLE } from '../store/user'
import { NButton, NCard, NEmpty, NForm, NFormItem, NInput, NModal, NSelect, NSpace, NTag, createDiscreteApi } from 'naive-ui'
import { listCourses } from '../api/course'
import { listCourseNotices, saveCourseNotice, deleteCourseNotice } from '../api/notice'

const { message, dialog } = createDiscreteApi(['message', 'dialog'])
const userStore = useUserStore()
const canManage = computed(() => userStore.role === ROLE.TEACHER || userStore.role === ROLE.ADMIN)
const courses = ref([])
const notices = ref([])
const loading = ref(false)
const saving = ref(false)
const selectedCourse = ref(null)
const visible = ref(false)
const form = reactive({ id: null, courseId: null, title: '', content: '', noticeType: 'NOTICE', status: 1 })
const deletingId = ref(null)

const courseOptions = computed(() => courses.value.map((c) => ({ label: c.courseName, value: c.id })))
const currentCourseName = computed(() => courses.value.find((c) => c.id === selectedCourse.value)?.courseName || '')
const typeOptions = [
  { label: '公告', value: 'NOTICE' },
  { label: '作业提醒', value: 'HOMEWORK' },
  { label: '资料更新', value: 'RESOURCE' }
]

function typeText(type) {
  return typeOptions.find((item) => item.value === type)?.label || '公告'
}

async function loadCourses() {
  courses.value = await listCourses()
  if (selectedCourse.value == null && courses.value.length) {
    selectedCourse.value = courses.value[0].id
  }
}

async function loadNotices() {
  if (!selectedCourse.value) {
    notices.value = []
    return
  }
  loading.value = true
  try {
    notices.value = await listCourseNotices(selectedCourse.value)
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, { id: null, courseId: selectedCourse.value, title: '', content: '', noticeType: 'NOTICE', status: 1 })
}

function openCreate() {
  resetForm()
  visible.value = true
}

function openEdit(item) {
  Object.assign(form, { ...item })
  visible.value = true
}

async function onSave() {
  if (!form.courseId) return message.warning('请选择课程')
  if (!form.title) return message.warning('请输入标题')
  if (!form.content) return message.warning('请输入内容')
  saving.value = true
  try {
    await saveCourseNotice(form)
    message.success('公告已保存')
    visible.value = false
    await loadNotices()
  } finally {
    saving.value = false
  }
}

async function onDelete(item) {
  const ok = await dialog.warning({
    title: '提示',
    content: `确定删除公告「${item.title}」吗？`,
    positiveText: '确定',
    negativeText: '取消'
  })
  if (!ok) return
  if (deletingId.value !== null) return
  deletingId.value = item.id
  try {
    await deleteCourseNotice(item.id)
    message.success('已删除')
    await loadNotices()
  } catch (error) {
    message.error(error?.message || '删除失败，请重试')
  } finally {
    deletingId.value = null
  }
}

onMounted(async () => {
  await loadCourses()
  await loadNotices()
})
</script>

<style scoped>
.notice-page { display: flex; flex-direction: column; gap: 16px; }
.hero { display: grid; grid-template-columns: 1.4fr 0.9fr; gap: 16px; }
.eyebrow { color: #4cb6c2; font-size: 12px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.hero h2 { margin: 8px 0 10px; font-size: 30px; color: #16313b; }
.hero p { margin: 0; color: #5f6b73; max-width: 58ch; line-height: 1.7; }
.hero-card, .panel { border-radius: 18px; box-shadow: 0 16px 40px rgba(48, 102, 107, 0.08); }
.hero-card { background: linear-gradient(135deg, #f4fbfb 0%, #eaf8f7 100%); }
.hero-card-title { color: #5f6b73; font-size: 12px; margin-bottom: 8px; }
.hero-card-value { font-size: 22px; font-weight: 700; color: #18323d; margin-bottom: 6px; }
.hero-card-sub { color: #5f6b73; font-size: 13px; line-height: 1.6; }
.panel { background: rgba(255,255,255,0.9); }
.panel-title { font-weight: 700; color: #18323d; margin-bottom: 12px; }
.notice-list { display: flex; flex-direction: column; gap: 12px; }
.notice-card { background: #f7fbfb; }
.notice-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 8px; }
.notice-title { font-size: 16px; font-weight: 700; color: #18323d; }
.notice-meta { display: flex; align-items: center; gap: 10px; margin-top: 6px; color: #6b7280; font-size: 12px; flex-wrap: wrap; }
.notice-content { color: #1f2937; line-height: 1.8; white-space: pre-wrap; }
.notice-modal { width: min(760px, calc(100vw - 24px)); }
@media (max-width: 900px) {
  .hero { grid-template-columns: 1fr; }
}
</style>
