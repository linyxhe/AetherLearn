<template>
  <div class="paper-page">
    <div class="hero">
      <div>
        <div class="eyebrow">智能组卷</div>
        <h2>从课程知识库里快速长出一份题目草稿</h2>
        <p>选择课程和作业后，系统会基于对应知识点自动生成题目，适合老师快速铺设练习或测验初稿。</p>
      </div>
      <n-card class="hero-card" :bordered="false">
        <div class="hero-card-title">生成状态</div>
        <div class="hero-card-value">{{ generatedCount }}</div>
        <div class="hero-card-sub">道题</div>
      </n-card>
    </div>

    <n-card :bordered="false" class="panel controls">
      <n-space wrap align="center">
        <n-select v-model:value="courseId" :options="courseOptions" clearable placeholder="选择课程" class="filter-select" @update:value="loadAssignments" />
        <n-select v-model:value="assignmentId" :options="assignmentOptions" clearable placeholder="选择作业" class="filter-select" @update:value="loadPaperQuestions" />
        <n-select v-model:value="questionType" :options="typeOptions" class="filter-select" />
        <n-input-number v-model:value="count" :min="1" :max="20" />
        <n-button type="primary" :loading="generating" @click="generate">生成题目</n-button>
        <n-button secondary :disabled="!generated.length" @click="exportPaper">导出试卷</n-button>
      </n-space>
      <div class="workflow-hint">生成的题目会直接写入所选作业；学生可在线作答，教师也可在此导出不含答案的 Word 试卷。</div>
    </n-card>

    <n-grid :cols="4" :x-gap="16" :y-gap="16" responsive="screen">
      <n-grid-item v-for="card in statCards" :key="card.label">
        <n-card :bordered="false" class="stat-card">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </n-card>
      </n-grid-item>
    </n-grid>

    <n-spin :show="loading">
      <div v-if="generated.length" class="result-list">
        <n-card v-for="item in generated" :key="item.id" :bordered="false" class="result-card">
          <div class="result-top">
            <div>
              <n-tag round type="info">第 {{ item.seq }} 题</n-tag>
              <div class="result-title">{{ item.content }}</div>
            </div>
            <n-tag round type="success">{{ typeLabel(item.type) }}</n-tag>
          </div>
          <div class="result-meta">
            <span>标准答案：{{ item.answer || '—' }}</span>
            <span>分值：{{ item.score || 5 }}</span>
            <span>知识点：{{ item.knowledgePoint || '未命名' }}</span>
          </div>
          <div v-if="item.options?.length" class="option-list">
            <div v-for="(option, index) in item.options" :key="index" class="option-item">
              <span class="option-key">{{ optionLetter(index) }}</span>
              <span>{{ option }}</span>
            </div>
          </div>
          <div class="result-analysis">{{ item.analysis || '暂无解析' }}</div>
        </n-card>
      </div>
      <n-empty v-else description="选择作业后可查看已入卷题目，或生成新的题目" />
    </n-spin>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { NButton, NCard, NEmpty, NGrid, NGridItem, NInputNumber, NSelect, NSpace, NSpin, NTag } from 'naive-ui'
import { listCourses } from '../api/course'
import { autoGenerateQuestions, getAssignmentDetail, listAssignments } from '../api/homework'

const loading = ref(false)
const generating = ref(false)
const courses = ref([])
const assignments = ref([])
const courseId = ref(null)
const assignmentId = ref(null)
const questionType = ref(1)
const count = ref(5)
const generated = ref([])

const typeOptions = [
  { label: '单选题', value: 1 },
  { label: '多选题', value: 2 },
  { label: '判断题', value: 3 },
  { label: '填空题', value: 4 },
  { label: '简答题', value: 5 }
]
const courseOptions = computed(() => courses.value.map((course) => ({ label: course.courseName, value: course.id })))
const assignmentOptions = computed(() => assignments.value.map((item) => ({ label: item.title, value: item.id })))
const generatedCount = computed(() => generated.value.length)
const statCards = computed(() => [
  { label: '可选课程', value: courses.value.length },
  { label: '可选作业', value: assignments.value.length },
  { label: '当前题型', value: typeLabel(questionType.value) },
  { label: '生成数量', value: count.value }
])

onMounted(load)

async function load() {
  loading.value = true
  try {
    courses.value = await listCourses() || []
    await loadAssignments()
  } finally {
    loading.value = false
  }
}

async function loadAssignments() {
  assignments.value = await listAssignments(courseId.value) || []
  if (!assignments.value.some((a) => a.id === assignmentId.value)) {
    assignmentId.value = assignments.value[0]?.id || null
  }
  await loadPaperQuestions()
}

/** 读取所选作业的全部题目，使组卷结果可重复查看和导出。 */
async function loadPaperQuestions() {
  if (!assignmentId.value) {
    generated.value = []
    return
  }
  const detail = await getAssignmentDetail(assignmentId.value)
  generated.value = detail.questions || []
}

async function generate() {
  if (!assignmentId.value || !courseId.value) {
    return
  }
  generating.value = true
  try {
    await autoGenerateQuestions(assignmentId.value, courseId.value, count.value, questionType.value)
    await loadPaperQuestions()
  } finally {
    generating.value = false
  }
}

/** 返回题目选项的字母编号。 */
function optionLetter(index) {
  return String.fromCharCode(65 + index)
}

/** 将当前作业题目导出为可用 Microsoft Word 打开的试卷文件，不包含标准答案。 */
function exportPaper() {
  const assignment = assignments.value.find((item) => item.id === assignmentId.value)
  if (!assignment || !generated.value.length) return
  const safeText = (value) => String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  const questionHtml = generated.value.map((item, index) => {
    const options = (item.options || []).map((option, optionIndex) =>
      `<p class="option">${optionLetter(optionIndex)}. ${safeText(option)}</p>`
    ).join('')
    return `<section class="question"><p><strong>${index + 1}. ${safeText(item.content)}</strong>（${item.score || 5} 分）</p>${options}<p class="answer-line">答：____________________________________________________________</p></section>`
  }).join('')
  const totalScore = assignment.totalScore || generated.value.reduce((total, item) => total + (item.score || 0), 0)
  const courseName = courseOptions.value.find((item) => item.value === courseId.value)?.label || ''
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>${safeText(assignment.title)}</title><style>body{font-family:"Microsoft YaHei",sans-serif;color:#111;line-height:1.8;margin:36px}.title{text-align:center;font-size:22px;font-weight:700}.meta{margin:18px 0 24px;border-bottom:1px solid #999;padding-bottom:10px}.question{page-break-inside:avoid;margin:16px 0}.option{margin:4px 0 4px 24px}.answer-line{margin-top:12px;color:#333}</style></head><body><div class="title">${safeText(assignment.title)}</div><div class="meta">课程：${safeText(courseName)}　　总分：${totalScore} 分</div>${questionHtml}</body></html>`
  const blob = new Blob(['\ufeff', html], { type: 'application/msword;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${assignment.title}-试卷.doc`
  link.click()
  URL.revokeObjectURL(url)
}

function typeLabel(type) {
  const map = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '填空题', 5: '简答题' }
  return map[type] || '单选题'
}
</script>

<style scoped>
.paper-page { display: flex; flex-direction: column; gap: 16px; }
.hero { display: grid; grid-template-columns: 1.4fr 0.8fr; gap: 16px; }
.eyebrow { color: #4cb6c2; font-size: 12px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.hero h2 { margin: 8px 0 10px; font-size: 30px; color: #16313b; }
.hero p { margin: 0; color: #5f6b73; max-width: 58ch; line-height: 1.7; }
.hero-card, .stat-card, .panel, .result-card { border-radius: 18px; box-shadow: 0 16px 40px rgba(48, 102, 107, 0.08); }
.hero-card { background: linear-gradient(135deg, #f4fbfb 0%, #eaf8f7 100%); }
.hero-card-title { color: #5f6b73; font-size: 12px; margin-bottom: 8px; }
.hero-card-value { font-size: 30px; font-weight: 700; color: #18323d; margin-bottom: 6px; }
.hero-card-sub { color: #5f6b73; font-size: 13px; }
.panel { background: rgba(255,255,255,0.9); }
.controls { padding: 16px; }
.workflow-hint { margin-top: 12px; padding: 10px 12px; border-left: 3px solid #42b5bb; border-radius: 0 10px 10px 0; background: #f4fbfb; color: #52656c; font-size: 13px; line-height: 1.6; }
.filter-select { width: 220px; }
.stat-card { background: #fff; padding: 18px 20px; }
.stat-value { font-size: 28px; font-weight: 700; color: #18323d; }
.stat-label { color: #6b7280; margin-top: 6px; font-size: 13px; }
.result-list { display: flex; flex-direction: column; gap: 14px; }
.result-card { background: #fff; }
.result-top { display: flex; justify-content: space-between; gap: 16px; }
.result-title { margin-top: 10px; font-size: 18px; font-weight: 700; color: #18323d; line-height: 1.7; }
.result-meta { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 12px; color: #6b7280; font-size: 13px; }
.option-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 20px; margin-top: 14px; padding: 13px 15px; border-radius: 12px; background: #f6fbfb; color: #334155; }
.option-item { display: flex; gap: 8px; line-height: 1.6; }
.option-key { color: #2f7f86; font-weight: 700; }
.result-analysis { margin-top: 10px; padding-top: 10px; border-top: 1px solid #edf4f4; color: #5f6b73; line-height: 1.7; }
@media (max-width: 900px) {
  .hero { grid-template-columns: 1fr; }
  .filter-select { width: 100%; }
  .option-list { grid-template-columns: 1fr; }
}
</style>
