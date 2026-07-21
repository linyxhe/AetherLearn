<template>
  <div class="dash">
    <h2 class="page-title">数据看板</h2>
    <p class="welcome">你好，{{ realName }}（{{ roleText }}）👋 这里是教学数据总览。</p>

    <!-- F-DASH-01 概览数字卡 -->
    <el-row :gutter="16" class="cards">
      <el-col :span="6" v-for="c in overviewCards" :key="c.label">
        <div class="aeth-card stat">
          <div class="val text-gradient">{{ c.value }}</div>
          <div class="lbl">{{ c.label }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 2×2 图表栅格（F-DASH-02~05） -->
    <el-row :gutter="16" class="charts">
      <el-col :span="12">
        <div class="aeth-card chart-card">
          <div class="chart-head"><span class="dot"></span>成绩分布</div>
          <div ref="elScore" class="chart"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="aeth-card chart-card">
          <div class="chart-head"><span class="dot"></span>作业完成率趋势</div>
          <div ref="elTrend" class="chart"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="aeth-card chart-card">
          <div class="chart-head"><span class="dot"></span>班级知识点掌握度</div>
          <div ref="elRadar" class="chart"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="aeth-card chart-card">
          <div class="chart-head"><span class="dot"></span>近 7 天问答活跃度</div>
          <div ref="elQa" class="chart"></div>
        </div>
      </el-col>
    </el-row>

    <!-- F-DASH-06 学生排行榜 -->
    <div class="aeth-card chart-card rank-card">
      <div class="chart-head"><span class="dot"></span>学生综合表现排行榜（Top 10）</div>
      <div ref="elRank" class="chart rank-chart"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useUserStore } from '../store/user'
import { getDashboardStat } from '../api/dashboard'
import {
  initChart, applyOption, disposeChart, areaGradient, barGradient, emptyGraphic, BRAND
} from '../utils/chartTheme'

const userStore = useUserStore()
const realName = computed(() => userStore.realName)
const roleText = computed(() => userStore.roleName || '教师')

const stat = ref(null)
const elScore = ref(null)
const elTrend = ref(null)
const elRadar = ref(null)
const elQa = ref(null)
const elRank = ref(null)
let charts = []

const round1 = (n) => Math.round(Number(n) * 10) / 10

// F-DASH-01 概览数字卡
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
  try {
    const data = await getDashboardStat()
    stat.value = data
    await nextTick()
    renderAll()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
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

// F-DASH-02 成绩分布（环形饼图 + 中心总数）
function renderScore() {
  const chart = initChart(elScore.value)
  charts.push(chart)
  const list = stat.value?.scoreDistribution || []
  const total = list.reduce((s, i) => s + Number(i.count || 0), 0)
  if (!list.length || total === 0) {
    applyOption(chart, emptyGraphic('暂无成绩数据'))
    return
  }
  const colors = ['#ef4444', '#f59e0b', '#38bdf8', '#7c4dff', '#5b6ef5']
  applyOption(chart, {
    tooltip: { trigger: 'item', formatter: '{b}<br/>人数：{c}（{d}%）' },
    legend: { bottom: 0, icon: 'circle' },
    series: [{
      type: 'pie',
      radius: ['46%', '70%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      data: list.map((i, idx) => ({
        name: i.range, value: Number(i.count),
        itemStyle: { color: colors[idx % colors.length] }
      }))
    }],
    graphic: [
      { type: 'text', left: 'center', top: '37%', style: { text: String(total), fill: '#1f2330', fontSize: 26, fontWeight: 800 } },
      { type: 'text', left: 'center', top: '50%', style: { text: '提交总人数', fill: '#6b7280', fontSize: 12 } }
    ]
  })
}

// F-DASH-03 作业完成率趋势（双轴折线）
function renderTrend() {
  const chart = initChart(elTrend.value)
  charts.push(chart)
  const list = stat.value?.completionTrend || []
  if (!list.length) {
    applyOption(chart, emptyGraphic('暂无作业数据'))
    return
  }
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
      {
        name: '完成率(%)', type: 'line', smooth: true, yAxisIndex: 0, symbolSize: 8,
        lineStyle: { width: 3, color: BRAND.primary }, itemStyle: { color: BRAND.primary },
        areaStyle: { color: areaGradient() }, data: list.map((i) => round1(i.completionRate))
      },
      {
        name: '平均分', type: 'line', smooth: true, yAxisIndex: 1, symbolSize: 8,
        lineStyle: { width: 3, color: BRAND.accent }, itemStyle: { color: BRAND.accent },
        data: list.map((i) => round1(i.avgScore))
      }
    ]
  })
}

// F-DASH-04 班级知识点掌握度（雷达图）
function renderRadar() {
  const chart = initChart(elRadar.value)
  charts.push(chart)
  const list = stat.value?.knowledgeMastery || []
  if (!list.length) {
    applyOption(chart, emptyGraphic('暂无作答数据'))
    return
  }
  applyOption(chart, {
    tooltip: {},
    radar: { indicator: list.map((i) => ({ name: i.knowledgePoint, max: 100 })), radius: '65%' },
    series: [{
      type: 'radar',
      data: [{ value: list.map((i) => round1(i.mastery)), name: '掌握度(%)' }],
      areaStyle: { color: 'rgba(91,110,245,0.25)' },
      lineStyle: { color: BRAND.primary, width: 2 },
      itemStyle: { color: BRAND.accent }
    }]
  })
}

// F-DASH-05 近 7 天问答活跃度（柱状）
function renderQa() {
  const chart = initChart(elQa.value)
  charts.push(chart)
  const list = stat.value?.qaActivity || []
  if (!list.length) {
    applyOption(chart, emptyGraphic('近 7 天暂无问答'))
    return
  }
  applyOption(chart, {
    tooltip: { trigger: 'axis' },
    grid: { left: 30, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: list.map((i) => i.dateLabel) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar', barWidth: '46%',
      itemStyle: { borderRadius: [6, 6, 0, 0], color: barGradient() },
      data: list.map((i) => Number(i.count))
    }]
  })
}

// F-DASH-06 学生排行榜（横向条形 Top10）
function renderRank() {
  const chart = initChart(elRank.value)
  charts.push(chart)
  const list = (stat.value?.studentRanking || []).slice().reverse() // 最高分置顶
  if (!list.length) {
    applyOption(chart, emptyGraphic('暂无学生成绩'))
    return
  }
  applyOption(chart, {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 90, right: 60, top: 10, bottom: 20 },
    xAxis: { type: 'value', name: '平均分' },
    yAxis: { type: 'category', data: list.map((i) => i.studentName) },
    series: [{
      type: 'bar', barWidth: '55%',
      itemStyle: { borderRadius: [0, 6, 6, 0], color: barGradient() },
      label: { show: true, position: 'right', formatter: (p) => round1(list[p.dataIndex].avgScore) + ' 分' },
      data: list.map((i) => round1(i.avgScore))
    }]
  })
}
</script>

<style scoped>
.page-title { margin: 0 0 4px; }
.welcome { color: var(--text-2); margin: 0 0 18px; }
.cards { margin-bottom: 16px; }
.stat { text-align: center; }
.stat .val { font-size: 32px; font-weight: 800; line-height: 1.1; }
.stat .lbl { color: var(--text-2); margin-top: 6px; }
.charts { margin-bottom: 0; }
/* 图表卡：顶部细渐变描边（签名元素） */
.chart-card {
  position: relative;
  overflow: hidden;
  padding-top: 18px;
  margin-bottom: 16px;
}
.chart-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--brand-1), var(--brand-2));
}
.chart-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: var(--text-1);
  margin-bottom: 12px;
  font-size: 15px;
}
.chart-head .dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: linear-gradient(135deg, var(--brand-1), var(--brand-2));
}
.chart { width: 100%; height: 300px; }
.rank-card { margin-top: 0; }
.rank-chart { height: 360px; }
</style>
