<template>
  <div class="report-page">
    <div class="hero">
      <div>
        <div class="eyebrow">AI 学习报告</div>
        <h2>把本周学习情况整理成一份可执行的复盘</h2>
        <p>报告会结合成绩趋势、知识盲区、学习路径和个性建议生成总结，既能看结果，也能看下一步该怎么走。</p>
      </div>
      <n-card class="hero-card" :bordered="false">
        <div class="hero-card-title">生成状态</div>
        <div class="hero-card-value">{{ report?.aiGenerated ? 'AI' : '规则' }}</div>
        <div class="hero-card-sub">{{ report?.aiGenerated ? '已调用模型生成文本' : '当前使用规则模板降级' }}</div>
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

    <n-spin :show="loading">
      <n-grid :cols="2" :x-gap="16" :y-gap="16" responsive="screen">
        <n-grid-item>
          <n-card :bordered="false" class="panel">
            <div class="panel-title">总体评价</div>
            <div class="comment-box">{{ report?.overallComment || '暂无报告' }}</div>
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card :bordered="false" class="panel">
            <div class="panel-title">AI 原文</div>
            <div class="comment-box pre">{{ report?.aiText || '当前暂无大模型原文，已使用规则版报告。' }}</div>
          </n-card>
        </n-grid-item>
      </n-grid>

      <n-grid :cols="2" :x-gap="16" :y-gap="16" responsive="screen" class="section-grid">
        <n-grid-item>
          <n-card :bordered="false" class="panel">
            <div class="panel-title">学习优势</div>
            <div class="chip-list">
              <n-tag v-for="item in report?.strengths || []" :key="item" round type="success">{{ item }}</n-tag>
            </div>
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card :bordered="false" class="panel">
            <div class="panel-title">薄弱点</div>
            <div class="chip-list">
              <n-tag v-for="item in report?.weaknesses || []" :key="item" round type="error">{{ item }}</n-tag>
            </div>
          </n-card>
        </n-grid-item>
      </n-grid>

      <n-card :bordered="false" class="panel">
        <div class="panel-title">下一步行动</div>
        <n-steps vertical :current="0" size="small">
          <n-step v-for="(item, index) in report?.nextActions || []" :key="index" :title="item" />
        </n-steps>
      </n-card>
    </n-spin>

    <div class="report-actions">
      <n-button type="primary" :loading="loading" @click="load">重新生成报告</n-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { NButton, NCard, NGrid, NGridItem, NSpin, NStep, NSteps, NTag } from 'naive-ui'
import { getStudentAiReport } from '../api/aiReport'

const loading = ref(false)
const report = ref(null)

const statCards = computed(() => {
  const r = report.value
  if (!r?.overview) {
    return [
      { label: '平均正确率', value: '0%' },
      { label: '平均分', value: '0' },
      { label: '活跃次数', value: '0' },
      { label: '课程数', value: '0' }
    ]
  }
  return [
    { label: '平均正确率', value: `${Number(r.overview.avgAccuracy || 0).toFixed(1)}%` },
    { label: '平均分', value: Number(r.overview.avgScore || 0).toFixed(1) },
    { label: '活跃次数', value: r.overview.activityCount || 0 },
    { label: '课程数', value: r.overview.courseCount || 0 }
  ]
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    report.value = await getStudentAiReport()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.report-page { display: flex; flex-direction: column; gap: 16px; }
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
.panel { background: rgba(255,255,255,0.9); }
.panel-title { font-weight: 700; color: #18323d; margin-bottom: 12px; }
.comment-box { min-height: 120px; padding: 14px 16px; border-radius: 14px; background: #f8fcfc; color: #24343d; line-height: 1.8; }
.comment-box.pre { white-space: pre-wrap; }
.chip-list { display: flex; flex-wrap: wrap; gap: 10px; }
.section-grid { margin-top: 4px; }
.report-actions { display: flex; justify-content: flex-end; }
@media (max-width: 900px) {
  .hero { grid-template-columns: 1fr; }
}
</style>
