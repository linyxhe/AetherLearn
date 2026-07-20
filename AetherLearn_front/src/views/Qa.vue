<template>
  <div class="qa-wrap">
    <!-- 顶部：课程选择 -->
    <div class="head">
      <h2 class="page-title">智能答疑</h2>
      <div class="head-right">
        <el-select v-model="selectedCourse" placeholder="选择课程" style="width: 220px" @change="onCourseChange">
          <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
        </el-select>
        <el-button text type="primary" :disabled="!selectedCourse" @click="loadHistory">历史记录</el-button>
      </div>
    </div>

    <el-alert
      v-if="!selectedCourse"
      type="info"
      :closable="false"
      show-icon
      title="请先选择一门已加入的课程，再向智能助教提问"
      style="margin-bottom: 14px"
    />

    <!-- 对话区 -->
    <div v-else class="aeth-card chat-card">
      <div ref="scrollRef" class="chat-body">
        <el-empty v-if="messages.length === 0" description="向智能助教提问吧，例如：什么是闭包？" />

        <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
          <div class="bubble" :class="m.role">
            <template v-if="m.role === 'user'">{{ m.content }}</template>
            <template v-else>
              <div class="meta">
                <el-tag v-if="m.useLlm" type="success" size="small" effect="plain">大模型</el-tag>
                <el-tag v-else type="info" size="small" effect="plain">仅检索</el-tag>
                <span class="cost">{{ m.costMs }} ms</span>
              </div>
              <div class="answer">{{ m.content }}</div>
              <el-collapse v-if="m.sources && m.sources.length" class="sources">
                <el-collapse-item :title="`参考来源（${m.sources.length}）`">
                  <div v-for="(s, idx) in m.sources" :key="idx" class="source-item">
                    <div class="source-title">【资料 {{ idx + 1 }}】{{ s.docTitle }}</div>
                    <div class="source-content">{{ s.content }}</div>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </template>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-input">
        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="输入你的问题，回车发送（Shift+Enter 换行）"
          @keydown.enter.exact.prevent="send"
        />
        <el-button type="primary" :loading="asking" @click="send">发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listCourses } from '../api/course'
import { askQa, qaHistory } from '../api/qa'

const courses = ref([])
const selectedCourse = ref(null)
const messages = ref([])
const question = ref('')
const asking = ref(false)
const scrollRef = ref(null)

async function loadCourses() {
  courses.value = await listCourses()
}

function onCourseChange() {
  messages.value = []
  loadHistory()
}

// 加载历史问答，回填到对话区
async function loadHistory() {
  if (!selectedCourse.value) return
  try {
    const list = await qaHistory(selectedCourse.value)
    messages.value = (list || []).map((r) => ({
      role: 'ai',
      content: r.answer,
      useLlm: r.useLlm === 1,
      costMs: r.costMs || 0,
      sources: parseSources(r.sourceChunks)
    }))
    scrollToBottom()
  } catch (e) {
    // 忽略
  }
}

// 将后端 "1,2,3" 形式的 sourceChunks 仅作为占位（无内容时为空）
function parseSources(sourceChunks) {
  if (!sourceChunks) return []
  return sourceChunks.split(',').filter(Boolean).map((id) => ({ docTitle: '历史记录', content: '（历史来源片段未缓存）' }))
}

async function send() {
  const q = question.value.trim()
  if (!q) {
    ElMessage.warning('请输入问题')
    return
  }
  if (!selectedCourse.value) {
    ElMessage.warning('请先选择课程')
    return
  }
  // 用户气泡
  messages.value.push({ role: 'user', content: q })
  question.value = ''
  asking.value = true
  // 占位 AI 气泡
  const aiIndex = messages.value.length
  messages.value.push({ role: 'ai', content: '正在思考…', useLlm: false, costMs: 0, sources: [] })
  scrollToBottom()
  try {
    const res = await askQa({ courseId: selectedCourse.value, question: q })
    messages.value[aiIndex] = {
      role: 'ai',
      content: res.answer,
      useLlm: res.useLlm,
      costMs: res.costMs || 0,
      sources: res.sources || []
    }
  } catch (e) {
    messages.value[aiIndex] = {
      role: 'ai',
      content: '提问失败，请稍后重试。',
      useLlm: false,
      costMs: 0,
      sources: []
    }
  } finally {
    asking.value = false
    scrollToBottom()
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  })
}

onMounted(loadCourses)
</script>

<style scoped>
.qa-wrap { display: flex; flex-direction: column; height: 100%; }
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.page-title { margin: 0; }
.head-right { display: flex; align-items: center; gap: 10px; }
.chat-card { flex: 1; display: flex; flex-direction: column; min-height: 0; padding: 0; }
.chat-body { flex: 1; overflow-y: auto; padding: 20px; min-height: 320px; }
.msg-row { display: flex; margin-bottom: 16px; }
.msg-row.user { justify-content: flex-end; }
.msg-row.ai { justify-content: flex-start; }
.bubble { max-width: 78%; padding: 12px 16px; border-radius: 14px; line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
.bubble.user { background: linear-gradient(135deg, var(--brand-1), var(--brand-2)); color: #fff; border-bottom-right-radius: 4px; }
.bubble.ai { background: #f7f8fc; color: var(--text-1); border: 1px solid #eef0f7; border-bottom-left-radius: 4px; }
.meta { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.cost { color: var(--text-2); font-size: 12px; }
.sources { margin-top: 10px; }
.source-item { margin-bottom: 10px; padding: 8px 10px; background: #fff; border-radius: 8px; border: 1px solid #eef0f7; }
.source-title { font-weight: 600; color: var(--brand-1); margin-bottom: 4px; font-size: 13px; }
.source-content { font-size: 13px; color: var(--text-2); max-height: 140px; overflow-y: auto; }
.chat-input { display: flex; gap: 10px; padding: 14px; border-top: 1px solid #eef0f7; align-items: flex-end; }
.chat-input .el-button { flex-shrink: 0; height: 54px; }
</style>
