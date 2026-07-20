<template>
  <div class="login-wrap">
    <!-- 左侧：品牌 + 标语（渐变背景） -->
    <div class="login-aside">
      <div class="brand">
        <div class="logo">Aether<span>Learn</span></div>
        <h1 class="slogan">AI 驱动的<br />智能教学辅助平台</h1>
        <p class="sub">RAG 知识检索 · 作业自动批改 · 学情数据分析</p>
        <ul class="points">
          <li>📚 课程知识库，随时智能答疑</li>
          <li>✅ 客观题全自动批改，主观题 AI 反馈</li>
          <li>📊 多角色数据看板，教学心中有数</li>
        </ul>
      </div>
    </div>

    <!-- 右侧：登录表单 -->
    <div class="login-main">
      <div class="login-card aeth-card">
        <h2 class="title">欢迎登录</h2>
        <p class="tip">请输入账号与初始口令（默认 123456）</p>
        <el-form :model="form" :rules="rules" ref="formRef" @keyup.enter="handleLogin">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="账号" size="large" :prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="密码" size="large" :prefix-icon="Lock" />
          </el-form-item>
          <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form>
        <div class="quick">
          <span>演示账号：</span>
          <el-tag @click="fill('admin')" class="tag">admin</el-tag>
          <el-tag @click="fill('teacher01')" class="tag" type="success">teacher01</el-tag>
          <el-tag @click="fill('student01')" class="tag" type="warning">student01</el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { login as loginApi } from '../api/auth'
import { useUserStore, homePathByRole } from '../store/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 快速填充演示账号（密码统一 123456）
function fill(name) {
  form.username = name
  form.password = '123456'
}

// 登录处理
async function handleLogin() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await loginApi({ username: form.username, password: form.password })
      // res = { token, user }
      userStore.setLogin(res.token, res.user)
      ElMessage.success('登录成功')
      // 按角色跳转首页
      router.push(homePathByRole(res.user.role))
    } catch (e) {
      // 错误提示由 request 拦截器统一处理
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-wrap {
  display: flex;
  height: 100vh;
  width: 100%;
}
/* 左侧品牌区 */
.login-aside {
  flex: 1;
  background: linear-gradient(135deg, #5b6ef5 0%, #7c4dff 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}
.brand { max-width: 420px; }
.logo { font-size: 34px; font-weight: 800; letter-spacing: 1px; }
.logo span { opacity: 0.85; }
.slogan { font-size: 40px; line-height: 1.3; margin: 28px 0 12px; font-weight: 700; }
.sub { font-size: 16px; opacity: 0.9; margin-bottom: 28px; }
.points { list-style: none; padding: 0; margin: 0; line-height: 2.2; font-size: 15px; opacity: 0.95; }

/* 右侧表单区 */
.login-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg);
}
.login-card { width: 380px; }
.title { font-size: 24px; margin: 0 0 6px; }
.tip { color: var(--text-2); margin: 0 0 22px; font-size: 13px; }
.login-btn { width: 100%; margin-top: 6px; background: linear-gradient(135deg, #5b6ef5, #7c4dff); border: none; }
.quick { margin-top: 18px; font-size: 13px; color: var(--text-2); }
.tag { cursor: pointer; margin-left: 6px; }
</style>
