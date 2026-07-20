<template>
  <div class="coming aeth-card">
    <div class="illustration">🚧</div>
    <h2>{{ title }}（规划中）</h2>
    <p>该模块属于 MVP 后续波次（知识库 / 作业 / 问答 / 用户管理等），本波次（第一波）已完成登录鉴权、课程管理与文件上传。</p>
    <el-button type="primary" @click="goBack">返回首页</el-button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { homePathByRole } from '../store/user'

const route = useRoute()
const router = useRouter()
const title = computed(() => route.meta.title || '模块')
function goBack() {
  router.push(homePathByRole(useRoute().meta?.roles?.[0]) || '/dashboard')
}
</script>

<style scoped>
.coming {
  max-width: 520px;
  margin: 60px auto;
  text-align: center;
  line-height: 1.8;
}
.illustration { font-size: 64px; }
</style>
