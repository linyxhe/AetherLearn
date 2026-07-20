<template>
  <div>
    <h2 class="page-title">学习中心</h2>
    <p class="welcome">你好，{{ userStore.realName }} 👋 这里是你的专属学习中心。</p>

    <!-- 学情概览占位（F-LEARN-01） -->
    <el-row :gutter="16" class="cards">
      <el-col :span="8" v-for="c in cards" :key="c.label">
        <div class="stat aeth-card">
          <div class="val" :style="{ color: c.color }">{{ c.value }}</div>
          <div class="lbl">{{ c.label }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 可视化学习路径占位（F-LEARN-03：Element Plus Steps + 推荐资料卡片） -->
    <div class="aeth-card">
      <h3>推荐学习路径</h3>
      <p class="hint">第三/五波将基于薄弱知识点，用 <b>Element Plus Steps 步骤条 + 推荐资料卡片</b> 呈现你的个性化路径。</p>
      <el-steps :active="1" finish-status="success" align-center>
        <el-step title="巩固概念" description="复习基础知识" />
        <el-step title="观看资料" description="推荐课程资料" />
        <el-step title="完成练习" description="对应习题训练" />
        <el-step title="自测检验" description="检验掌握度" />
      </el-steps>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '../store/user'

const userStore = useUserStore()
const cards = [
  { label: '平均正确率', value: '—', color: '#5b6ef5' },
  { label: '学习活跃度', value: '—', color: '#7c4dff' },
  { label: '已加入课程', value: '—', color: '#22c55e' }
]
</script>

<style scoped>
.page-title { margin: 0 0 4px; }
.welcome { color: var(--text-2); margin: 0 0 18px; }
.cards { margin-bottom: 16px; }
.stat { text-align: center; }
.stat .val { font-size: 30px; font-weight: 800; }
.stat .lbl { color: var(--text-2); margin-top: 4px; }
.hint { color: var(--text-2); }
</style>
