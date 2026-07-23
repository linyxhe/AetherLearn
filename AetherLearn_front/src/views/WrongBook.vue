<template>
  <div class="wrong-book">
    <div class="hero">
      <div>
        <div class="eyebrow">错题本</div>
        <h2>把错过的题重新收回来</h2>
        <p>作业错题和章节小测错题会放在同一页，按课程筛选后可以直接回看答案、解析和你的作答。</p>
      </div>
      <n-card class="hero-card" :bordered="false">
        <div class="hero-card-title">当前统计</div>
        <div class="hero-card-value">{{ totalCount }}</div>
        <div class="hero-card-sub">条错题</div>
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
        <n-select v-model:value="sourceType" :options="sourceOptions" clearable placeholder="按来源筛选" class="filter-select" />
        <n-button secondary @click="load">刷新</n-button>
      </n-space>
    </n-card>

    <n-tabs v-model:value="tabType" type="segment" size="small" class="tabs">
      <n-tab-pane name="ALL" tab="全部错题" />
      <n-tab-pane name="ASSIGNMENT" tab="作业错题" />
      <n-tab-pane name="CHAPTER_QUIZ" tab="章节小测" />
    </n-tabs>

    <n-spin :show="loading">
      <div v-if="filteredItems.length" class="list">
        <n-card
          v-for="item in filteredItems"
          :key="`${item.sourceType}-${item.questionId}`"
          class="item-card"
          :bordered="false"
          @click="openDetail(item)"
        >
          <div class="item-top">
            <div>
              <n-space align="center" size="small">
                <n-tag round :type="item.sourceType === 'ASSIGNMENT' ? 'warning' : 'info'">
                  {{ item.sourceType === 'ASSIGNMENT' ? '作业' : '章节小测' }}
                </n-tag>
                <n-tag round type="success">{{ item.courseName }}</n-tag>
              </n-space>
              <div class="item-title">{{ item.sourceTitle }}</div>
              <div class="item-content">{{ item.content }}</div>
            </div>
            <div class="item-score">
              <div class="score-num">{{ item.score ?? 0 }}</div>
              <div class="score-label">分</div>
            </div>
          </div>
          <div class="item-meta">
            <span>我的答案：{{ item.yourAnswer || '未作答' }}</span>
            <span>标准答案：{{ item.standardAnswer || '待补充' }}</span>
          </div>
        </n-card>
      </div>
      <n-empty v-else description="暂无错题，继续保持" />
    </n-spin>

    <n-drawer v-model:show="detailVisible" placement="right" width="460">
      <n-drawer-content :title="detailTitle" closable>
        <template v-if="activeItem">
          <div class="detail-meta">
            <n-tag round type="success">{{ activeItem.courseName }}</n-tag>
            <n-tag v-if="activeItem.sourceType === 'CHAPTER_QUIZ'" round type="info">章节小测</n-tag>
            <n-tag v-else round type="warning">作业错题</n-tag>
          </div>
          <div class="detail-block">
            <div class="detail-label">题干</div>
            <div class="detail-text">{{ activeItem.content }}</div>
          </div>
          <div class="detail-block">
            <div class="detail-label">我的答案</div>
            <div class="detail-text">{{ activeItem.yourAnswer || '未作答' }}</div>
          </div>
          <div class="detail-block">
            <div class="detail-label">标准答案</div>
            <div class="detail-text strong">{{ activeItem.standardAnswer || '待补充' }}</div>
          </div>
          <div class="detail-block">
            <div class="detail-label">解析 / 反馈</div>
            <div class="detail-text">{{ activeItem.analysis || '暂无解析' }}</div>
          </div>
          <div class="detail-actions">
            <n-button secondary @click="detailVisible = false">关闭</n-button>
          </div>
        </template>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { NButton, NCard, NDrawer, NDrawerContent, NEmpty, NGrid, NGridItem, NSelect, NSpace, NSpin, NTabPane, NTabs, NTag } from 'naive-ui'
import { listCourses } from '../api/course'
import { listWrongBook } from '../api/wrongBook'

const loading = ref(false)
const courses = ref([])
const items = ref([])
const courseId = ref(null)
const sourceType = ref(null)
const tabType = ref('ALL')
const detailVisible = ref(false)
const activeItem = ref(null)

const courseOptions = computed(() => courses.value.map((course) => ({ label: course.courseName, value: course.id })))
const sourceOptions = [
  { label: '作业错题', value: 'ASSIGNMENT' },
  { label: '章节小测', value: 'CHAPTER_QUIZ' }
]
const filteredItems = computed(() => items.value.filter((item) => {
  if (tabType.value !== 'ALL' && item.sourceType !== tabType.value) return false
  if (courseId.value && item.courseId !== courseId.value) return false
  if (sourceType.value && item.sourceType !== sourceType.value) return false
  return true
}))
const totalCount = computed(() => filteredItems.value.length)
const assignmentCount = computed(() => items.value.filter((i) => i.sourceType === 'ASSIGNMENT').length)
const chapterCount = computed(() => items.value.filter((i) => i.sourceType === 'CHAPTER_QUIZ').length)
const courseCount = computed(() => new Set(items.value.map((i) => i.courseId)).size)
const statCards = computed(() => [
  { label: '总错题', value: totalCount.value },
  { label: '作业错题', value: assignmentCount.value },
  { label: '章节小测', value: chapterCount.value },
  { label: '涉及课程', value: courseCount.value }
])
const detailTitle = computed(() => activeItem.value ? `${activeItem.value.sourceTitle} · 错题详情` : '错题详情')

onMounted(load)

async function load() {
  loading.value = true
  try {
    const [courseRows, wrongRows] = await Promise.all([
      listCourses(),
      listWrongBook()
    ])
    courses.value = courseRows || []
    items.value = wrongRows || []
  } finally {
    loading.value = false
  }
}

function openDetail(item) {
  activeItem.value = item
  detailVisible.value = true
}
</script>

<style scoped>
.wrong-book { display: flex; flex-direction: column; gap: 16px; }
.hero { display: grid; grid-template-columns: 1.4fr 0.8fr; gap: 16px; }
.eyebrow { color: #4cb6c2; font-size: 12px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.hero h2 { margin: 8px 0 10px; font-size: 30px; color: #16313b; }
.hero p { margin: 0; color: #5f6b73; max-width: 58ch; line-height: 1.7; }
.hero-card, .stat-card, .panel { border-radius: 18px; box-shadow: 0 16px 40px rgba(48, 102, 107, 0.08); }
.hero-card { background: linear-gradient(135deg, #f4fbfb 0%, #eaf8f7 100%); }
.hero-card-title { color: #5f6b73; font-size: 12px; margin-bottom: 8px; }
.hero-card-value { font-size: 30px; font-weight: 700; color: #18323d; margin-bottom: 6px; }
.hero-card-sub { color: #5f6b73; font-size: 13px; }
.stat-card { background: #fff; padding: 18px 20px; }
.stat-value { font-size: 28px; font-weight: 700; color: #18323d; }
.stat-label { color: #6b7280; margin-top: 6px; font-size: 13px; }
.filters { background: rgba(255,255,255,0.9); padding: 16px; }
.filter-select { width: 180px; }
.tabs { background: #fff; border-radius: 14px; padding: 6px; }
.list { display: grid; grid-template-columns: 1fr; gap: 14px; }
.item-card { background: #fff; border-radius: 18px; box-shadow: 0 14px 34px rgba(48, 102, 107, 0.08); cursor: pointer; transition: transform .2s ease, box-shadow .2s ease; }
.item-card:hover { transform: translateY(-2px); box-shadow: 0 18px 42px rgba(48, 102, 107, 0.12); }
.item-top { display: flex; justify-content: space-between; gap: 16px; }
.item-title { margin-top: 10px; font-size: 18px; font-weight: 700; color: #18323d; }
.item-content { margin-top: 8px; color: #5f6b73; line-height: 1.7; }
.item-score { min-width: 72px; text-align: center; align-self: center; }
.score-num { font-size: 32px; font-weight: 700; color: #42B5BB; line-height: 1; }
.score-label { color: #6b7280; margin-top: 4px; }
.item-meta { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 12px; color: #6b7280; font-size: 13px; }
.detail-meta { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
.detail-block { margin-bottom: 14px; }
.detail-label { font-size: 12px; color: #6b7280; margin-bottom: 6px; }
.detail-text { padding: 12px 14px; border: 1px solid #e6efef; border-radius: 12px; background: #f9fcfc; line-height: 1.7; color: #18323d; }
.detail-text.strong { color: #0f766e; font-weight: 700; }
.detail-actions { margin-top: 18px; display: flex; justify-content: flex-end; }
@media (max-width: 900px) {
  .hero { grid-template-columns: 1fr; }
  .filter-select { width: 100%; }
}
</style>
