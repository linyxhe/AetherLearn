<template>
  <div class="graph-page">
    <div class="hero">
      <div>
        <div class="eyebrow">知识点图谱</div>
        <h2>从课程看到知识结构</h2>
        <p>课程作为中心节点，题目里出现的知识点围绕展开，节点大小代表覆盖次数，适合复习和教学结构检查。</p>
      </div>
      <n-card class="hero-card" :bordered="false">
        <div class="hero-card-title">图谱规模</div>
        <div class="hero-card-value">{{ graph.nodes?.length || 0 }}</div>
        <div class="hero-card-sub">个节点</div>
      </n-card>
    </div>

    <n-card :bordered="false" class="panel filters">
      <n-space wrap align="center">
        <n-select v-model:value="courseId" :options="courseOptions" clearable placeholder="按课程筛选" class="filter-select" @update:value="loadGraph" />
        <n-button secondary @click="loadGraph">刷新图谱</n-button>
      </n-space>
    </n-card>

    <n-card :bordered="false" class="graph-card">
      <n-spin :show="loading">
        <div ref="chartEl" class="chart"></div>
      </n-spin>
    </n-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { NButton, NCard, NSelect, NSpace, NSpin } from 'naive-ui'
import { listCourses } from '../api/course'
import { getKnowledgeGraph } from '../api/knowledgeGraph'
import { applyOption, disposeChart, emptyGraphic, initChart } from '../utils/chartTheme'

const loading = ref(false)
const courses = ref([])
const graph = ref({ nodes: [], edges: [] })
const courseId = ref(null)
const chartEl = ref(null)
let chart = null

const courseOptions = computed(() => courses.value.map((course) => ({ label: course.courseName, value: course.id })))

onMounted(async () => {
  courses.value = await listCourses()
  await loadGraph()
})
onBeforeUnmount(() => disposeChart(chart))

async function loadGraph() {
  loading.value = true
  try {
    graph.value = await getKnowledgeGraph(courseId.value)
    await nextTick()
    renderGraph()
  } finally {
    loading.value = false
  }
}

function renderGraph() {
  chart = initChart(chartEl.value)
  const nodes = graph.value.nodes || []
  const edges = graph.value.edges || []
  if (!nodes.length) return applyOption(chart, emptyGraphic('暂无知识点数据'))
  applyOption(chart, {
    tooltip: {
      formatter: (params) => params.dataType === 'edge' ? params.data.label : `${params.data.name}<br/>覆盖次数：${params.data.value}`
    },
    legend: { data: ['课程', '知识点'], bottom: 0 },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      categories: [{ name: '课程' }, { name: '知识点' }],
      force: { repulsion: 240, edgeLength: 110 },
      label: { show: true, position: 'right' },
      lineStyle: { color: '#9ad7d3', width: 2, curveness: 0.12 },
      data: nodes.map((node) => ({
        id: node.id,
        name: node.name,
        value: node.value,
        category: node.category === 'course' ? 0 : 1,
        symbolSize: node.category === 'course' ? 58 : Math.max(30, 24 + node.value * 8),
        itemStyle: { color: node.category === 'course' ? '#42B5BB' : '#87D8C9' }
      })),
      links: edges.map((edge) => ({ source: edge.source, target: edge.target, label: edge.label }))
    }]
  })
}
</script>

<style scoped>
.graph-page { display: flex; flex-direction: column; gap: 16px; }
.hero { display: grid; grid-template-columns: 1.4fr 0.8fr; gap: 16px; }
.eyebrow { color: #4cb6c2; font-size: 12px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.hero h2 { margin: 8px 0 10px; font-size: 30px; color: #16313b; }
.hero p { margin: 0; color: #5f6b73; max-width: 58ch; line-height: 1.7; }
.hero-card, .panel, .graph-card { border-radius: 18px; box-shadow: 0 16px 40px rgba(48, 102, 107, 0.08); }
.hero-card { background: linear-gradient(135deg, #f4fbfb 0%, #eaf8f7 100%); }
.hero-card-title { color: #5f6b73; font-size: 12px; margin-bottom: 8px; }
.hero-card-value { font-size: 30px; font-weight: 700; color: #18323d; margin-bottom: 6px; }
.hero-card-sub { color: #5f6b73; font-size: 13px; }
.filters { background: rgba(255,255,255,0.9); padding: 16px; }
.filter-select { width: 220px; }
.graph-card { background: #fff; }
.chart { width: 100%; height: 620px; }
@media (max-width: 900px) {
  .hero { grid-template-columns: 1fr; }
  .filter-select { width: 100%; }
  .chart { height: 520px; }
}
</style>
