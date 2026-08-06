<template>
  <div class="teacher-dashboard">
    <div class="hero">
      <div>
        <div class="eyebrow">教师工作台</div>
        <h2>课程运行概览</h2>
        <p>今天的课程、作业、问答和学情预警集中在这里，便于教师快速判断下一步教学动作。</p>
      </div>
      <n-card class="hero-card" :bordered="false">
        <div class="hero-card-title">当前状态</div>
        <div class="hero-card-value">{{ realName }} / {{ roleText }}</div>
        <div class="hero-card-sub">聚焦课程编排、章节维护、作业批改和学生跟进。</div>
      </n-card>
    </div>
    <n-alert v-if="errorMessage" type="error" :bordered="false" :title="errorMessage">
      <template #action><n-button size="small" @click="load">重新加载</n-button></template>
    </n-alert>

    <n-grid :cols="4" :x-gap="16" :y-gap="16" responsive="screen">
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
          <div class="panel-title">成绩分布</div>
          <div ref="elScore" class="chart"></div>
        </n-card>
      </n-grid-item>
      <n-grid-item>
        <n-card :bordered="false" class="panel">
          <div class="panel-title">作业完成率趋势</div>
          <div ref="elTrend" class="chart"></div>
        </n-card>
      </n-grid-item>
      <n-grid-item>
        <n-card :bordered="false" class="panel">
          <div class="panel-title">班级知识掌握</div>
          <div ref="elRadar" class="chart"></div>
        </n-card>
      </n-grid-item>
      <n-grid-item>
        <n-card :bordered="false" class="panel">
          <div class="panel-title">近 7 天问答活跃</div>
          <div ref="elQa" class="chart"></div>
        </n-card>
      </n-grid-item>
    </n-grid>

    <n-card :bordered="false" class="panel">
      <div class="panel-title">学生综合表现</div>
      <div ref="elRank" class="rank-chart"></div>
      <div v-if="warnStudents.length" class="warn-box">
        <n-tag v-for="s in warnStudents" :key="s.studentName" type="error" round>{{ s.studentName }} · {{ s.warningReason }}</n-tag>
      </div>
    </n-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { NAlert, NButton, NCard, NGrid, NGridItem, NTag } from 'naive-ui'
import { useUserStore } from '../store/user'
import { getDashboardStat } from '../api/dashboard'
import { initChart, applyOption, disposeChart, areaGradient, barGradient, emptyGraphic, BRAND } from '../utils/chartTheme'

const userStore = useUserStore()
const realName = computed(() => userStore.realName)
const roleText = computed(() => userStore.roleName || '教师')

const stat = ref(null)
const warnStudents = ref([])
const errorMessage = ref('')
const elScore = ref(null)
const elTrend = ref(null)
const elRadar = ref(null)
const elQa = ref(null)
const elRank = ref(null)
let charts = []

const round1 = (n) => Math.round(Number(n) * 10) / 10
const overviewCards = computed(() => {
  const o = stat.value?.overview
  if (!o) return []
  return [
    { label: '课程数', value: o.courseCount },
    { label: '学生数', value: o.studentCount },
    { label: '作业数', value: o.assignmentCount },
    { label: '问答数', value: o.qaCount }
  ]
})

onMounted(load)
onBeforeUnmount(() => charts.forEach(disposeChart))

async function load() {
  errorMessage.value = ''
  try {
    const data = await getDashboardStat()
    stat.value = data
    warnStudents.value = (data.studentRanking || []).filter((s) => s.warning)
    await nextTick()
    renderAll()
  } catch (error) {
    errorMessage.value = error?.message || '看板数据加载失败，请重试'
  }
}

function renderAll() {
  charts.forEach(disposeChart)
  charts = []
  renderScore()
  renderTrend()
  renderRadar()
  renderQa()
  renderRank()
}

function renderScore() {
  const chart = initChart(elScore.value)
  charts.push(chart)
  const list = stat.value?.scoreDistribution || []
  const total = list.reduce((s, i) => s + Number(i.count || 0), 0)
  if (!list.length || total === 0) return applyOption(chart, emptyGraphic('暂无成绩数据'))
  applyOption(chart, {
    tooltip: { trigger: 'item', formatter: '{b}<br/>人数：{c}（{d}%）' },
    legend: { bottom: 0, icon: 'circle' },
    series: [{
      type: 'pie',
      radius: ['46%', '70%'],
      center: ['50%', '44%'],
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      data: list.map((i, idx) => ({ name: i.range, value: Number(i.count), itemStyle: { color: ['#4cb6c2', '#7fd7c9', '#8eb8ff', '#f4c27d', '#f59ca8'][idx % 5] } }))
    }],
    graphic: [
      { type: 'text', left: 'center', top: '37%', style: { text: String(total), fill: '#213547', fontSize: 26, fontWeight: 600 } },
      { type: 'text', left: 'center', top: '50%', style: { text: '提交总人数', fill: '#6b7280', fontSize: 12 } }
    ]
  })
}

function renderTrend() {
  const chart = initChart(elTrend.value)
  charts.push(chart)
  const list = stat.value?.completionTrend || []
  if (!list.length) return applyOption(chart, emptyGraphic('暂无作业数据'))
  applyOption(chart, {
    tooltip: { trigger: 'axis' },
    legend: { data: ['完成率(%)', '平均分'], bottom: 0 },
    grid: { left: 44, right: 44, top: 20, bottom: 50 },
    xAxis: { type: 'category', data: list.map((i) => i.title), axisLabel: { interval: 0, rotate: list.length > 4 ? 20 : 0 } },
    yAxis: [
      { type: 'value', name: '完成率%', max: 100, axisLabel: { formatter: '{value}%' } },
      { type: 'value', name: '平均分', max: 100 }
    ],
    series: [
      { name: '完成率(%)', type: 'line', smooth: true, yAxisIndex: 0, symbolSize: 8, lineStyle: { width: 3, color: BRAND.primary }, itemStyle: { color: BRAND.primary }, areaStyle: { color: areaGradient() }, data: list.map((i) => round1(i.completionRate)) },
      { name: '平均分', type: 'line', smooth: true, yAxisIndex: 1, symbolSize: 8, lineStyle: { width: 3, color: BRAND.accent }, itemStyle: { color: BRAND.accent }, data: list.map((i) => round1(i.avgScore)) }
    ]
  })
}

function renderRadar() {
  const chart = initChart(elRadar.value)
  charts.push(chart)
  const list = stat.value?.knowledgeMastery || []
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

function renderQa() {
  const chart = initChart(elQa.value)
  charts.push(chart)
  const list = stat.value?.qaActivity || []
  if (!list.length) return applyOption(chart, emptyGraphic('近 7 天暂无问答'))
  applyOption(chart, {
    tooltip: { trigger: 'axis' },
    grid: { left: 30, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: list.map((i) => i.dateLabel) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'bar', barWidth: '46%', itemStyle: { borderRadius: [6, 6, 0, 0], color: barGradient() }, data: list.map((i) => Number(i.count)) }]
  })
}

function renderRank() {
  const chart = initChart(elRank.value)
  charts.push(chart)
  const list = (stat.value?.studentRanking || []).slice().reverse()
  if (!list.length) return applyOption(chart, emptyGraphic('暂无学生成绩'))
  applyOption(chart, {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 90, right: 60, top: 10, bottom: 20 },
    xAxis: { type: 'value', name: '平均分' },
    yAxis: { type: 'category', data: list.map((i) => i.studentName) },
    series: [{
      type: 'bar',
      barWidth: '55%',
      itemStyle: { borderRadius: [0, 6, 6, 0], color: barGradient() },
      label: { show: true, position: 'right', formatter: (p) => round1(list[p.dataIndex].avgScore) + ' 分' },
      data: list.map((i) => round1(i.avgScore))
    }]
  })
}
</script>

<style scoped>
.teacher-dashboard { display: flex; flex-direction: column; gap: 16px; }
.hero {
  display: grid;
  grid-template-columns: 1.4fr 0.9fr;
  gap: 16px;
  align-items: stretch;
}
.eyebrow { color: #4cb6c2; font-size: 12px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.hero h2 { margin: 8px 0 10px; font-size: 30px; }
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
.rank-chart { width: 100%; height: 360px; }
.warn-box { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 14px; }
@media (max-width: 900px) {
  .hero { grid-template-columns: 1fr; }
}
</style>
