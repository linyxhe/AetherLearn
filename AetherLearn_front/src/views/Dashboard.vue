<template>
  <div>
    <h2 class="page-title">数据看板</h2>
    <p class="welcome">你好，{{ userStore.realName }}（{{ roleText }}）👋 欢迎使用 AetherLearn 教学辅助平台。</p>

    <!-- 概览数字卡（F-DASH-01） -->
    <el-row :gutter="16" class="cards">
      <el-col :span="6" v-for="c in cards" :key="c.label">
        <div class="stat aeth-card">
          <div class="val" :style="{ color: c.color }">{{ c.value }}</div>
          <div class="lbl">{{ c.label }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 第四波（看板）将在此接入 ECharts 图表 -->
    <div class="aeth-card chart-placeholder">
      <el-icon class="big"><DataLine /></el-icon>
      <p>成绩分布、作业完成率、知识点掌握度等 ECharts 图表将在 <b>第四波（看板）</b> 接入，</p>
      <p>届时可复用 Element Plus 官方 Dashboard 模板快速改造接口。</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { DataLine } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'

const userStore = useUserStore()
const roleText = computed(() => userStore.roleName || '用户')

// 占位统计（后续由 /dashboard/stat 接口返回真实数据）
const cards = [
  { label: '课程数', value: '—', color: '#5b6ef5' },
  { label: '学生数', value: '—', color: '#7c4dff' },
  { label: '作业数', value: '—', color: '#22c55e' },
  { label: '问答数', value: '—', color: '#f59e0b' }
]
</script>

<style scoped>
.page-title { margin: 0 0 4px; }
.welcome { color: var(--text-2); margin: 0 0 18px; }
.cards { margin-bottom: 16px; }
.stat { text-align: center; }
.stat .val { font-size: 32px; font-weight: 800; }
.stat .lbl { color: var(--text-2); margin-top: 4px; }
.chart-placeholder { text-align: center; color: var(--text-2); padding: 40px; line-height: 1.9; }
.chart-placeholder .big { font-size: 48px; color: #c7c9d9; }
</style>
