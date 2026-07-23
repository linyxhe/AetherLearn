<template>
  <div class="advice-page">
    <div class="hero">
      <div>
        <div class="eyebrow">AI 教学建议</div>
        <h2>把看板数据压成下一步教学动作</h2>
        <p>系统会结合班级知识掌握、预警学生和完成率趋势，给出更具体的教学建议和跟进方向。</p>
      </div>
      <n-card class="hero-card" :bordered="false">
        <div class="hero-card-title">生成状态</div>
        <div class="hero-card-value">{{ advice?.aiGenerated ? 'AI' : '规则' }}</div>
        <div class="hero-card-sub">{{ advice?.aiGenerated ? '已调用模型生成建议' : '当前使用规则模板降级' }}</div>
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
            <div class="panel-title">总体判断</div>
            <div class="comment-box">{{ advice?.summary || '暂无建议' }}</div>
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card :bordered="false" class="panel">
            <div class="panel-title">AI 原文</div>
            <div class="comment-box pre">{{ advice?.aiText || '当前暂无大模型原文，已使用规则版建议。' }}</div>
          </n-card>
        </n-grid-item>
      </n-grid>

      <n-grid :cols="2" :x-gap="16" :y-gap="16" responsive="screen" class="section-grid">
        <n-grid-item>
          <n-card :bordered="false" class="panel">
            <div class="panel-title">优先知识点</div>
            <div class="chip-list">
              <n-tag v-for="item in advice?.focusPoints || []" :key="item" round type="warning">{{ item }}</n-tag>
            </div>
          </n-card>
        </n-grid-item>
        <n-grid-item>
          <n-card :bordered="false" class="panel">
            <div class="panel-title">预警学生</div>
            <div class="chip-list">
              <n-tag v-for="item in advice?.warningStudents || []" :key="item" round type="error">{{ item }}</n-tag>
            </div>
          </n-card>
        </n-grid-item>
      </n-grid>

      <n-card :bordered="false" class="panel">
        <div class="panel-title">建议动作</div>
        <n-steps vertical :current="0" size="small">
          <n-step v-for="(item, index) in advice?.actions || []" :key="index" :title="item" />
        </n-steps>
      </n-card>
    </n-spin>

    <div class="report-actions">
      <n-button type="primary" :loading="loading" @click="load">重新生成建议</n-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { NButton, NCard, NGrid, NGridItem, NSpin, NStep, NSteps, NTag } from 'naive-ui'
import { getTeacherAiAdvice } from '../api/aiAdvice'

const loading = ref(false)
const advice = ref(null)

const statCards = computed(() => {
  const o = advice.value?.overview
  if (!o) {
    return [
      { label: '课程数', value: '0' },
      { label: '学生数', value: '0' },
      { label: '作业数', value: '0' },
      { label: '问答数', value: '0' }
    ]
  }
  return [
    { label: '课程数', value: o.courseCount },
    { label: '学生数', value: o.studentCount },
    { label: '作业数', value: o.assignmentCount },
    { label: '问答数', value: o.qaCount }
  ]
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    advice.value = await getTeacherAiAdvice()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.advice-page { display: flex; flex-direction: column; gap: 16px; }
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
