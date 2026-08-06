<template>
  <div class="student-dashboard">
    <div class="hero">
      <div>
        <div class="eyebrow">学生学习中心</div>
        <h2>今天该学什么，进度到哪了，一眼看清</h2>
        <p>这里把成绩趋势、知识盲区、学习路径和个性建议放在同一层，学生不需要翻找页面就能知道下一步怎么学。</p>
      </div>
      <n-card class="hero-card" :bordered="false">
        <div class="hero-card-title">当前身份</div>
        <div class="hero-card-value">{{ realName }}</div>
        <div class="hero-card-sub">你的学习节奏、弱项和建议会随着作业与课程进度实时更新。</div>
      </n-card>
    </div>
    <n-alert v-if="errorMessage" type="error" :bordered="false" :title="errorMessage">
      <template #action><n-button size="small" @click="load">重新加载</n-button></template>
    </n-alert>

    <n-grid :cols="3" :x-gap="16" :y-gap="16" responsive="screen">
      <n-grid-item v-for="c in overviewCards" :key="c.label">
        <n-card :bordered="false" class="stat-card">
          <div class="stat-value">{{ c.value }}</div>
          <div class="stat-label">{{ c.label }}</div>
        </n-card>
      </n-grid-item>
    </n-grid>

    <n-grid :cols="2" :x-gap="16" :y-gap="16" responsive="screen" class="chart-grid">
      <n-grid-item>
        <n-card :bordered="false" class="panel">
          <div class="panel-title">我的成绩趋势</div>
          <div ref="elTrend" class="chart"></div>
        </n-card>
      </n-grid-item>
      <n-grid-item>
        <n-card :bordered="false" class="panel">
          <div class="panel-title">知识盲区掌握度</div>
          <div class="radar-wrap">
            <div ref="elRadar" class="radar"></div>
            <div class="weak-list">
              <div class="weak-title">薄弱知识点</div>
              <n-tag v-for="gap in weakGaps" :key="gap.knowledgePoint" type="error" round>{{ gap.knowledgePoint }} · {{ gap.errorRate }}%</n-tag>
              <div v-if="weakGaps.length === 0" class="weak-none">全部掌握良好</div>
            </div>
          </div>
        </n-card>
      </n-grid-item>
    </n-grid>

    <n-card v-if="learningPath.length" :bordered="false" class="panel">
      <div class="panel-title">学习路径推荐</div>
      <n-steps vertical :current="activeStep" size="small">
        <n-step v-for="(step, i) in learningPath" :key="i" :title="step.title" :description="step.description">
          <template #icon>
            <n-tag round size="small" type="info">{{ step.status }}</n-tag>
          </template>
        </n-step>
      </n-steps>
    </n-card>

    <n-card :bordered="false" class="panel">
      <div class="panel-title">个性化学习建议</div>
      <div class="sug-grid">
        <n-card v-for="sug in suggestions" :key="sug.content" class="sug-card" :bordered="false">
          <div class="sug-type">{{ sug.type }}</div>
          <div class="sug-content">{{ sug.content }}</div>
        </n-card>
        <n-empty v-if="suggestions.length === 0" description="暂无建议，继续保持" />
      </div>
    </n-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { NAlert, NButton, NCard, NEmpty, NGrid, NGridItem, NStep, NSteps, NTag } from 'naive-ui'
import { useUserStore } from '../store/user'
import { getAnalyticsOverview } from '../api/dashboard'
import { initChart, applyOption, disposeChart, areaGradient, emptyGraphic, BRAND } from '../utils/chartTheme'

const userStore = useUserStore()
const realName = computed(() => userStore.realName)
const overview = ref(null)
const trendData = ref([])
const radarData = ref([])
const suggestions = ref([])
const weakGaps = ref([])
const errorMessage = ref('')
const learningPath = ref([])
const elTrend = ref(null)
const elRadar = ref(null)
let charts = []

const round1 = (n) => Math.round(Number(n) * 10) / 10
const overviewCards = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    { label: '平均正确率', value: `${o.avgAccuracy.toFixed(1)}%` },
    { label: '学习活跃度', value: `${o.activityCount} 次` },
    { label: '已加入课程', value: `${o.courseCount} 门` }
  ]
})
const activeStep = computed(() => {
  const idx = learningPath.value.findIndex((s) => s.status === 1)
  return idx >= 0 ? idx : learningPath.value.filter((s) => s.status === 2).length
})

onMounted(load)
onBeforeUnmount(() => charts.forEach(disposeChart))

async function load() {
  errorMessage.value = ''
  try {
    const data = await getAnalyticsOverview()
    overview.value = data.overview || {}
    trendData.value = data.scoreTrend || []
    radarData.value = data.knowledgeGaps || []
    suggestions.value = data.suggestions || []
    learningPath.value = data.learningPath || []
    weakGaps.value = (radarData.value || []).filter((g) => g.weak).map((g) => ({ knowledgePoint: g.knowledgePoint, errorRate: Number(g.errorRate).toFixed(1) }))
    await nextTick()
    renderAll()
  } catch (error) {
    errorMessage.value = error?.message || '学习数据加载失败，请重试'
  }
}

function renderAll() {
  charts.forEach(disposeChart)
  charts = []
  renderTrend()
  renderRadar()
}

function renderTrend() {
  const chart = initChart(elTrend.value)
  charts.push(chart)
  const list = trendData.value
  if (!list.length) return applyOption(chart, emptyGraphic('暂无成绩数据'))
  applyOption(chart, {
    tooltip: { trigger: 'axis' },
    legend: { data: ['我的得分', '满分'], bottom: 0 },
    grid: { left: 30, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: list.map((i) => i.title) },
    yAxis: { type: 'value', name: '分数', min: 0, max: 100 },
    series: [
      { name: '我的得分', type: 'line', smooth: true, symbolSize: 6, lineStyle: { width: 3, color: BRAND.primary }, itemStyle: { color: BRAND.primary }, areaStyle: { color: areaGradient() }, data: list.map((i) => round1(i.score)) },
      { name: '满分', type: 'line', smooth: true, symbolSize: 6, lineStyle: { width: 2, color: BRAND.accent, type: 'dashed' }, itemStyle: { color: BRAND.accent }, data: list.map((i) => round1(i.fullScore)) }
    ]
  })
}

function renderRadar() {
  const chart = initChart(elRadar.value)
  charts.push(chart)
  const list = radarData.value
  if (!list.length) return applyOption(chart, emptyGraphic('暂无作答数据'))
  applyOption(chart, {
    tooltip: {},
    radar: { indicator: list.map((i) => ({ name: i.knowledgePoint, max: 100 })), radius: '65%' },
    series: [{
      type: 'radar',
      data: [{ value: list.map((i) => round1(i.mastery)), name: '掌握度(%)' }],
      areaStyle: { color: 'rgba(66, 181, 187, 0.25)' },
      lineStyle: { color: BRAND.primary, width: 2 },
      itemStyle: { color: BRAND.accent }
    }]
  })
}
</script>

<style scoped>
.student-dashboard { display: flex; flex-direction: column; gap: 16px; }
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
.chart { width: 100%; height: 280px; }
.radar-wrap { display: grid; grid-template-columns: 1fr 220px; gap: 16px; align-items: center; }
.radar { width: 100%; height: 280px; }
.weak-list { display: flex; flex-direction: column; gap: 10px; }
.weak-title { font-weight: 700; color: #18323d; }
.weak-none { color: #16a34a; font-weight: 600; }
.sug-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; }
.sug-card { background: #f7fbfb; }
.sug-type { font-size: 11px; color: #6b7280; font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; }
.sug-content { margin-top: 6px; line-height: 1.7; color: #1f2937; }
@media (max-width: 900px) {
  .hero, .radar-wrap { grid-template-columns: 1fr; }
}
</style>
