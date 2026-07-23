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
  background: linear-gradient(135deg, #42B5BB 0%, #87D8C9 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  position: relative;
  overflow: hidden;
}
/* 品牌区装饰：大号半透明圆环 */
.login-aside::before {
  content: '';
  position: absolute;
  width: 520px;
  height: 520px;
  border: 2px solid rgba(255, 255, 255, 0.08);
  border-radius: 50%;
  top: -120px;
  right: -160px;
}
.login-aside::after {
  content: '';
  position: absolute;
  width: 360px;
  height: 360px;
  border: 2px solid rgba(255, 255, 255, 0.06);
  border-radius: 50%;
  bottom: -80px;
  left: -100px;
}
.brand { max-width: 420px; position: relative; z-index: 1; }
.logo {
  font-size: 32px;
  font-weight: 500;
  letter-spacing: -0.01em;
  font-family: var(--font-display);
}
.logo span { opacity: 0.85; }
.slogan {
  font-size: 38px;
  line-height: 1.25;
  margin: 32px 0 14px;
  font-weight: 500;
  letter-spacing: -0.01em;
  font-family: var(--font-display);
}
.sub {
  font-size: 15px;
  opacity: 0.85;
  margin-bottom: 32px;
  font-weight: 400;
  letter-spacing: 0.02em;
}
.points {
  list-style: none;
  padding: 0;
  margin: 0;
  line-height: 2.4;
  font-size: 14.5px;
  opacity: 0.92;
}
.points li {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 右侧表单区 */
.login-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface);
  position: relative;
}
/* 右侧装饰：点阵网格 */
.login-main::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle, rgba(66, 181, 187, 0.06) 1px, transparent 1px);
  background-size: 24px 24px;
  pointer-events: none;
}
.login-card {
  width: 400px;
  position: relative;
  z-index: 1;
  padding: 32px 28px;
}
.title {
  font-size: 22px;
  margin: 0 0 6px;
  font-family: var(--font-display);
  font-weight: 500;
}
.tip {
  color: var(--text-2);
  margin: 0 0 26px;
  font-size: 13px;
  line-height: 1.5;
}
.login-btn {
  width: 100%;
  margin-top: 8px;
  height: 44px;
  font-size: 15px;
  font-weight: 500;
  background: var(--brand-1);
  border: none;
  letter-spacing: 0.04em;
}
.login-btn:hover {
  background: var(--brand-deep);
}
.quick {
  margin-top: 20px;
  font-size: 13px;
  color: var(--text-2);
  display: flex;
  align-items: center;
  gap: 8px;
}
.tag {
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.tag:hover {
  opacity: 0.85;
}
</style>
