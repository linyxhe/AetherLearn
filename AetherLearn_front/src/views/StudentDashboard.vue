<template>
  <div class="learn">
    <h2 class="page-title">学习中心</h2>
    <p class="welcome">你好，{{ realName }} 👋 这里是你的专属学习情报中心。</p>

    <!-- F-LEARN-01 学情总览卡片 -->
    <el-row :gutter="16" class="cards">
      <el-col :span="8" v-for="c in overviewCards" :key="c.label">
        <div class="aeth-card stat">
          <div class="val text-gradient">{{ c.value }}</div>
          <div class="lbl">{{ c.label }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 成绩趋势折线图（F-LEARN-01） -->
    <div class="aeth-card chart-card">
      <div class="chart-head"><span class="dot"></span>我的成绩趋势</div>
      <div ref="elTrend" class="chart"></div>
    </div>

    <!-- 知识盲区：雷达图 + 薄弱点徽标列表（F-LEARN-02） -->
    <div class="aeth-card chart-card">
      <div class="chart-head"><span class="dot"></span>知识盲区掌握度</div>
      <div class="chart-body" style="display:flex; gap:16px;">
        <!-- 雷达图 -->
        <div ref="elRadar" class="radar"></div>
        <!-- 薄弱点列表 -->
        <div class="weak-list">
          <div class="weak-title">薄弱知识点（错误率 ≥ 40%）</div>
          <el-tag
            v-for="gap in weakGaps"
            :key="gap.knowledgePoint"
            type="danger"
            effect="dark"
            size="small"
            >{{ gap.knowledgePoint }}（{{ gap.errorRate }}%）</el-tag
          >
          <div v-if="weakGaps.length===0" class="weak-none">全部掌握良好 👏</div>
        </div>
      </div>
    </div>

    <!-- 个性化学习建议卡（F-LEARN-04） -->
    <div class="aeth-card">
      <div class="chart-head"><span class="dot"></span>个性化学习建议</div>
      <div class="sug-list">
        <el-card
          v-for="sug in suggestions"
          :key="sug.content"
          class="sug-item"
          shadow="hover"
          style="{ width: '100%', boxSizing: 'border-box' }"
        >
          <div class="sug-type">{{ sug.type }}</div>
          <div class="sug-content">{{ sug.content }}</div>
        </el-card>
        <div v-if="suggestions.length===0" class="sug-empty">暂无建议，继续保持！</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useUserStore } from '../store/user'
import { getAnalyticsOverview } from '../api/dashboard'
import {
  initChart, applyOption, disposeChart, areaGradient, barGradient, emptyGraphic, BRAND
} from '../utils/chartTheme'

const userStore = useUserStore()
const realName = computed(() => userStore.realName)

const overview = ref(null)
const trendData = ref([])
const radarData = ref([])
const suggestions = ref([])
const weakGaps = ref([])

const elTrend = ref(null)
const elRadar = ref(null)
let charts = []

const round1 = (n) => Math.round(Number(n) * 10) / 10

// F-LEARN-01 概览数字卡
const overviewCards = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    { label: '平均正确率', value: `${o.avgAccuracy.toFixed(1)}%` },
    { label: '学习活跃度', value: `${o.activityCount} 次` },
    { label: '已加入课程', value: `${o.courseCount} 门` }
  ]
})

onMounted(load)
onBeforeUnmount(() => charts.forEach(disposeChart))

async function load() {
  try {
    const data = await getAnalyticsOverview()
    overview.value = overview.value || {}
    overview.value.avgAccuracy = data.overview?.avgAccuracy ?? 0
    overview.value.activityCount = data.overview?.activityCount ?? 0
    overview.value.courseCount = data.overview?.courseCount ?? 0
    trendData.value = data.scoreTrend || []
    radarData.value = data.knowledgeGaps || []
    suggestions.value = data.suggestions || []
    // 计算弱项列表（错误率 >= 40%）
    weakGaps.value = (radarData.value || []).filter(g => g.weak).map(g => ({
      knowledgePoint: g.knowledgePoint,
      errorRate: Number(g.errorRate).toFixed(1)
    }))
    await nextTick()
    renderAll()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  }
}

function renderAll() {
  charts.forEach(disposeChart)
  charts = []
  renderTrend()
  renderRadar()
}

// F-LEARN-01 成绩趋势折线图（实际得分 / 满分）
function renderTrend() {
  const chart = initChart(elTrend.value)
  charts.push(chart)
  const list = trendData.value
  if (!list.length) {
    applyOption(chart, emptyGraphic('暂无成绩数据'))
    return
  }
  applyOption(chart, {
    tooltip: { trigger: 'axis' },
    legend: { data: ['我的得分', '满分'], bottom: 0 },
    grid: { left: 30, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: list.map((i) => i.title) },
    yAxis: { type: 'value', name: '分数', min: 0, max: 100 },
    series: [
      {
        name: '我的得分', type: 'line', smooth: true, symbolSize: 6,
        lineStyle: { width: 3, color: BRAND.primary },
        itemStyle: { color: BRAND.primary },
        areaStyle: { color: areaGradient() },
        data: list.map((i) => round1(i.score))
      },
      {
        name: '满分', type: 'line', smooth: true, symbolSize: 6,
        lineStyle: { width: 2, color: BRAND.accent, type: 'dashed' },
        itemStyle: { color: BRAND.accent },
        data: list.map((i) => round1(i.fullScore))
      }
    ]
  })
}

// F-LEARN-02 知识盲区雷达图
function renderRadar() {
  const chart = initChart(elRadar.value)
  charts.push(chart)
  const list = radarData.value
  if (!list.length) {
    applyOption(chart, emptyGraphic('暂无作答数据'))
    return
  }
  applyOption(chart, {
    tooltip: {},
    radar: {
      indicator: list.map((i) => ({
        name: i.knowledgePoint,
        max: 100
      })),
      radius: '65%'
    },
    series: [{
      type: 'radar',
      // 数据：掌握度（100 - 错误率），单系列
      data: [{ value: list.map((i) => round1(i.mastery)), name: '掌握度(%)' }],
      areaStyle: { color: 'rgba(91,110,245,0.25)' },
      lineStyle: { color: BRAND.primary, width: 2 },
      itemStyle: { color: BRAND.accent }
    }]
  })
}
</script>

<style scoped>
.page-title { margin: 0 0 4px; }
.welcome { color: var(--text-2); margin: 0 0 18px; }
.cards { margin-bottom: 16px; }
.stat { text-align: center; }
.stat .val { font-size: 30px; font-weight: 800; line-height: 1.1; }
.stat .lbl { color: var(--text-2); margin-top: 6px; }
.chart-card { position: relative; overflow: hidden; padding-top: 18px; margin-bottom: 16px; }
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
.chart { width: 100%; height: 280px; }
.radar { width: 48%; height: 280px; }
.weak-list { width: 52%; display: flex; flex-direction: column; gap: 8px; }
.weak-title { font-weight: 600; color: var(--text-1); margin-bottom: 4px; font-size: 14px; }
.weak-none { color: #22c55e; font-size: 14px; }
.sug-list { margin-top: 0; }
.sug-item { margin-bottom: 12px; }
.sug-type { font-size: 12px; color: #6b7280; margin-bottom: 4px; }
.sug-content { font-size: 14px; color: #1f2330; line-height: 1.6; }
.sug-empty { text-align: center; color: #6b7280; font-size: 14px; padding: 20px; }
</style>