<template>
  <div class="todo-page">
    <div class="hero">
      <div>
        <div class="eyebrow">学习计划</div>
        <h2>今天学什么，今晚补什么，一页安排好</h2>
        <p>把课程复习、小测重练、作业提醒和个人安排放在一起，学生能直接看到下一步该做什么。</p>
      </div>
      <n-card class="hero-card" :bordered="false">
        <div class="hero-card-title">待办概览</div>
        <div class="hero-card-value">{{ totalCount }}</div>
        <div class="hero-card-sub">条待办</div>
      </n-card>
    </div>

    <n-grid :cols="4" :x-gap="16" :y-gap="16" responsive="screen">
      <n-grid-item v-for="card in statCards" :key="card.label">
        <n-card :bordered="false" class="stat-card">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </n-card>
      </n-grid-item>
    </n-grid>

    <n-card :bordered="false" class="panel filters">
      <n-space wrap align="center">
        <n-select v-model:value="courseId" :options="courseOptions" clearable placeholder="按课程筛选" class="filter-select" />
        <n-select v-model:value="status" :options="statusOptions" clearable placeholder="按状态筛选" class="filter-select" />
        <n-button type="primary" @click="openCreate">新增待办</n-button>
        <n-button secondary @click="load">刷新</n-button>
      </n-space>
    </n-card>

    <n-spin :show="loading">
      <div v-if="filteredItems.length" class="list">
        <n-card v-for="item in filteredItems" :key="item.id" class="item-card" :bordered="false">
          <div class="item-top">
            <div>
              <n-space align="center" size="small">
                <n-tag round :type="priorityTag(item.priority)">{{ priorityLabel(item.priority) }}</n-tag>
                <n-tag v-if="item.courseName" round type="success">{{ item.courseName }}</n-tag>
                <n-tag round :type="item.status === 1 ? 'info' : 'default'">{{ item.status === 1 ? '已完成' : '进行中' }}</n-tag>
              </n-space>
              <div class="item-title">{{ item.title }}</div>
              <div class="item-content">{{ item.content || '暂无说明' }}</div>
            </div>
            <div class="item-actions">
              <n-button size="small" secondary @click="editTodo(item)">编辑</n-button>
              <n-button v-if="item.status === 0" size="small" type="primary" secondary @click="markComplete(item)">完成</n-button>
              <n-button v-else size="small" secondary @click="markReopen(item)">恢复</n-button>
              <n-button size="small" secondary type="error" @click="removeTodo(item)">删除</n-button>
            </div>
          </div>
          <div class="item-meta">
            <span>类型：{{ typeLabel(item.todoType) }}</span>
            <span>截止：{{ formatTime(item.dueTime) }}</span>
            <span>创建：{{ formatTime(item.createTime) }}</span>
          </div>
        </n-card>
      </div>
      <n-empty v-else description="还没有待办，先添加一条学习安排吧" />
    </n-spin>

    <n-modal v-model:show="editorVisible">
      <n-card class="editor-card" :bordered="false" title="待办编辑" closable @close="editorVisible = false">
        <div class="form-grid">
          <n-select v-model:value="form.courseId" :options="courseOptions" clearable placeholder="关联课程（可选）" />
          <n-select v-model:value="form.todoType" :options="todoTypeOptions" />
          <n-select v-model:value="form.priority" :options="priorityOptions" />
          <n-date-picker v-model:value="form.dueTime" type="datetime" clearable />
          <n-input v-model:value="form.title" placeholder="待办标题" class="span-2" />
          <n-input v-model:value="form.content" type="textarea" :autosize="{ minRows: 4, maxRows: 8 }" placeholder="待办说明" class="span-2" />
        </div>
        <template #footer>
          <n-space justify="end">
            <n-button secondary @click="editorVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="save">保存</n-button>
          </n-space>
        </template>
      </n-card>
    </n-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { createDiscreteApi, NButton, NCard, NDatePicker, NEmpty, NGrid, NGridItem, NInput, NModal, NSelect, NSpace, NSpin, NTag } from 'naive-ui'
import { listCourses } from '../api/course'
import { completeTodo, deleteTodo, listTodos, reopenTodo, saveTodo } from '../api/todo'

const { message, dialog } = createDiscreteApi(['message', 'dialog'])
const loading = ref(false)
const saving = ref(false)
const courses = ref([])
const todos = ref([])
const courseId = ref(null)
const status = ref(null)
const editorVisible = ref(false)
const editingId = ref(null)
const form = reactive({ courseId: null, title: '', content: '', todoType: 'PLAN', priority: 2, dueTime: null })

const courseOptions = computed(() => courses.value.map((course) => ({ label: course.courseName, value: course.id })))
const statusOptions = [
  { label: '进行中', value: 0 },
  { label: '已完成', value: 1 }
]
const todoTypeOptions = [
  { label: '学习计划', value: 'PLAN' },
  { label: '复习安排', value: 'REVIEW' },
  { label: '作业提醒', value: 'HOMEWORK' },
  { label: '小测重练', value: 'QUIZ' }
]
const priorityOptions = [
  { label: '低优先级', value: 1 },
  { label: '中优先级', value: 2 },
  { label: '高优先级', value: 3 }
]
const filteredItems = computed(() => todos.value.filter((item) => {
  if (courseId.value && item.courseId !== courseId.value) return false
  if (status.value !== null && status.value !== undefined && item.status !== status.value) return false
  return true
}))
const totalCount = computed(() => filteredItems.value.length)
const activeCount = computed(() => filteredItems.value.filter((item) => item.status === 0).length)
const doneCount = computed(() => filteredItems.value.filter((item) => item.status === 1).length)
const highCount = computed(() => filteredItems.value.filter((item) => item.priority === 3).length)
const statCards = computed(() => [
  { label: '总待办', value: totalCount.value },
  { label: '进行中', value: activeCount.value },
  { label: '已完成', value: doneCount.value },
  { label: '高优先级', value: highCount.value }
])

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [courseRows, todoRows] = await Promise.all([listCourses(), listTodos()])
    courses.value = courseRows || []
    todos.value = todoRows || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { courseId: null, title: '', content: '', todoType: 'PLAN', priority: 2, dueTime: null })
  editorVisible.value = true
}

function editTodo(item) {
  editingId.value = item.id
  Object.assign(form, {
    courseId: item.courseId || null,
    title: item.title || '',
    content: item.content || '',
    todoType: item.todoType || 'PLAN',
    priority: item.priority || 2,
    dueTime: item.dueTime ? new Date(item.dueTime).getTime() : null
  })
  editorVisible.value = true
}

async function save() {
  if (!form.title.trim()) {
    message.warning('请先填写标题')
    return
  }
  saving.value = true
  try {
    await saveTodo({
      id: editingId.value,
      courseId: form.courseId,
      title: form.title,
      content: form.content,
      todoType: form.todoType,
      priority: form.priority,
      dueTime: formatDateTime(form.dueTime)
    })
    message.success('待办已保存')
    editorVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function markComplete(item) {
  await completeTodo(item.id)
  message.success('已完成')
  await load()
}

async function markReopen(item) {
  await reopenTodo(item.id)
  message.success('已恢复')
  await load()
}

async function removeTodo(item) {
  dialog.warning({
    title: '删除待办',
    content: `确定删除「${item.title}」吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await deleteTodo(item.id)
      message.success('已删除')
      await load()
    }
  })
}

function formatTime(value) {
  if (!value) return '未设置'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatDateTime(ts) {
  if (!ts) return null
  return formatTime(ts) + ':00'
}

function typeLabel(type) {
  const map = { PLAN: '学习计划', REVIEW: '复习安排', HOMEWORK: '作业提醒', QUIZ: '小测重练' }
  return map[type] || '学习计划'
}

function priorityLabel(priority) {
  const map = { 1: '低优先级', 2: '中优先级', 3: '高优先级' }
  return map[priority] || '中优先级'
}

function priorityTag(priority) {
  if (priority === 3) return 'error'
  if (priority === 1) return 'default'
  return 'warning'
}
</script>

<style scoped>
.todo-page { display: flex; flex-direction: column; gap: 16px; }
.hero { display: grid; grid-template-columns: 1.4fr 0.8fr; gap: 16px; }
.eyebrow { color: #4cb6c2; font-size: 12px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.hero h2 { margin: 8px 0 10px; font-size: 30px; color: #16313b; }
.hero p { margin: 0; color: #5f6b73; max-width: 58ch; line-height: 1.7; }
.hero-card, .stat-card, .panel, .editor-card { border-radius: 18px; box-shadow: 0 16px 40px rgba(48, 102, 107, 0.08); }
.hero-card { background: linear-gradient(135deg, #f4fbfb 0%, #eaf8f7 100%); }
.hero-card-title { color: #5f6b73; font-size: 12px; margin-bottom: 8px; }
.hero-card-value { font-size: 30px; font-weight: 700; color: #18323d; margin-bottom: 6px; }
.hero-card-sub { color: #5f6b73; font-size: 13px; }
.stat-card { background: #fff; padding: 18px 20px; }
.stat-value { font-size: 28px; font-weight: 700; color: #18323d; }
.stat-label { color: #6b7280; margin-top: 6px; font-size: 13px; }
.filters { background: rgba(255,255,255,0.9); padding: 16px; }
.filter-select { width: 180px; }
.list { display: grid; grid-template-columns: 1fr; gap: 14px; }
.item-card { background: #fff; border-radius: 18px; box-shadow: 0 14px 34px rgba(48, 102, 107, 0.08); }
.item-top { display: flex; justify-content: space-between; gap: 16px; }
.item-title { margin-top: 10px; font-size: 18px; font-weight: 700; color: #18323d; }
.item-content { margin-top: 8px; color: #5f6b73; line-height: 1.7; }
.item-actions { display: flex; flex-wrap: wrap; gap: 8px; align-self: flex-start; }
.item-meta { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 12px; color: #6b7280; font-size: 13px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.span-2 { grid-column: span 2; }
@media (max-width: 900px) {
  .hero { grid-template-columns: 1fr; }
  .filter-select { width: 100%; }
  .form-grid { grid-template-columns: 1fr; }
  .span-2 { grid-column: auto; }
}
</style>
