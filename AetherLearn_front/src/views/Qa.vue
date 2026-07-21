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
                <span v-if="m.costMs" class="cost">{{ m.costMs }} ms</span>
              </div>
              <!-- AI 回答：markdown 渲染 -->
              <div class="answer md-body" v-html="renderMd(m.content)"></div>
              <span v-if="m.typing" class="cursor">▊</span>
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
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { listCourses } from '../api/course'
import { askQaStream, qaHistory } from '../api/qa'

const courses = ref([])
const selectedCourse = ref(null)
const messages = ref([])
const question = ref('')
const asking = ref(false)
const scrollRef = ref(null)

// 配置 marked：启用 GFM（表格、任务列表等），换行转 <br>
marked.setOptions({
  gfm: true,
  breaks: true
})

/** 将 markdown 文本渲染为安全的 HTML */
function renderMd(text) {
  if (!text) return ''
  const html = marked.parse(text)
  return DOMPurify.sanitize(html)
}

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
    const result = []
    for (const r of (list || []).reverse()) {
      result.push({ role: 'user', content: r.question })
      result.push({
        role: 'ai',
        content: r.answer,
        useLlm: r.useLlm === 1,
        costMs: r.costMs || 0,
        sources: parseSources(r.sourceChunks)
      })
    }
    messages.value = result
    scrollToBottom()
  } catch (e) {
    // 忽略
  }
}

function parseSources(sourceChunks) {
  if (!sourceChunks) return []
  return sourceChunks.split(',').filter(Boolean).map(() => ({ docTitle: '历史记录', content: '（历史来源片段未缓存）' }))
}

// 流式发送
async function send() {
  const q = question.value.trim()
  if (!q) { ElMessage.warning('请输入问题'); return }
  if (!selectedCourse.value) { ElMessage.warning('请先选择课程'); return }

  // 用户气泡
  messages.value.push({ role: 'user', content: q })
  question.value = ''
  asking.value = true

  // 占位 AI 气泡（打字中状态）
  const aiIndex = messages.value.length
  messages.value.push({ role: 'ai', content: '', useLlm: false, costMs: 0, sources: [], typing: true })
  scrollToBottom()

  try {
    const resp = await askQaStream({ courseId: selectedCourse.value, question: q })

    if (!resp.ok) {
      throw new Error(`HTTP ${resp.status}`)
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() // 保留不完整的行

      let eventType = ''
      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventType = line.substring(6).trim()
        } else if (line.startsWith('data:')) {
          const data = line.substring(5).trim()
          if (data) {
            handleSseEvent(eventType, data, aiIndex)
          }
          eventType = ''
        }
      }
      scrollToBottom()
    }

    // 流结束后，处理 buffer 中残留的最后一段数据（最后一行可能没有 \n 结尾）
    if (buffer.trim()) {
      const lines = buffer.split('\n')
      let eventType = ''
      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventType = line.substring(6).trim()
        } else if (line.startsWith('data:')) {
          const data = line.substring(5).trim()
          if (data) {
            handleSseEvent(eventType, data, aiIndex)
          }
          eventType = ''
        }
      }
    }
  } catch (e) {
    const msg = messages.value[aiIndex]
    if (msg) messages.value[aiIndex] = { ...msg, content: '提问失败，请稍后重试。', typing: false }
  } finally {
    asking.value = false
    if (messages.value[aiIndex]) {
      messages.value[aiIndex] = { ...messages.value[aiIndex], typing: false }
    }
    scrollToBottom()
  }
}

function handleSseEvent(type, data, aiIndex) {
  const msg = messages.value[aiIndex]
  if (!msg) return

  if (type === 'sources') {
    try {
      messages.value[aiIndex] = { ...msg, sources: JSON.parse(data) }
    } catch (e) { /* ignore */ }
  } else if (type === 'chunk') {
    messages.value[aiIndex] = { ...msg, content: msg.content + data, useLlm: true }
  } else if (type === 'done') {
    try {
      const meta = JSON.parse(data)
      messages.value[aiIndex] = { ...msg, useLlm: meta.useLlm, costMs: meta.costMs, typing: false }
    } catch (e) {
      messages.value[aiIndex] = { ...msg, typing: false }
    }
  } else if (type === 'error') {
    messages.value[aiIndex] = { ...msg, content: data || '服务异常，请稍后重试', typing: false }
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
.bubble { max-width: 78%; padding: 12px 16px; border-radius: 14px; line-height: 1.7; word-break: break-word; }
.bubble.user { background: linear-gradient(135deg, var(--brand-1), var(--brand-2)); color: #fff; border-bottom-right-radius: 4px; white-space: pre-wrap; }
.bubble.ai { background: #f7f8fc; color: var(--text-1); border: 1px solid #eef0f7; border-bottom-left-radius: 4px; }
.meta { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.cost { color: var(--text-2); font-size: 12px; }
.cursor { display: inline-block; animation: blink 0.8s infinite; color: var(--brand-1); margin-left: 2px; }
@keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0; } }
.sources { margin-top: 10px; }
.source-item { margin-bottom: 10px; padding: 8px 10px; background: #fff; border-radius: 8px; border: 1px solid #eef0f7; }
.source-title { font-weight: 600; color: var(--brand-1); margin-bottom: 4px; font-size: 13px; }
.source-content { font-size: 13px; color: var(--text-2); max-height: 140px; overflow-y: auto; }
.chat-input { display: flex; gap: 10px; padding: 14px; border-top: 1px solid #eef0f7; align-items: flex-end; }
.chat-input .el-button { flex-shrink: 0; height: 54px; }

/* ===== Markdown 渲染样式（AI 回答气泡内） ===== */
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3),
.md-body :deep(h4) {
  margin: 0.6em 0 0.3em;
  font-weight: 700;
  line-height: 1.4;
}
.md-body :deep(h1) { font-size: 1.3em; }
.md-body :deep(h2) { font-size: 1.15em; }
.md-body :deep(h3) { font-size: 1.05em; }

.md-body :deep(p) {
  margin: 0.4em 0;
}

.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 0.4em 0;
  padding-left: 1.6em;
}
.md-body :deep(li) {
  margin: 0.15em 0;
}

.md-body :deep(code) {
  background: #e8eaf6;
  padding: 1px 5px;
  border-radius: 4px;
  font-family: 'Cascadia Code', 'Fira Code', Consolas, monospace;
  font-size: 0.9em;
  color: #5c6bc0;
}

.md-body :deep(pre) {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 12px 14px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 0.6em 0;
  font-size: 0.88em;
  line-height: 1.5;
}
.md-body :deep(pre code) {
  background: none;
  color: inherit;
  padding: 0;
  font-size: inherit;
}

.md-body :deep(blockquote) {
  margin: 0.5em 0;
  padding: 4px 12px;
  border-left: 3px solid var(--brand-1, #5b6ef5);
  background: #eef0f7;
  border-radius: 0 6px 6px 0;
  color: var(--text-2, #666);
}

.md-body :deep(table) {
  border-collapse: collapse;
  margin: 0.6em 0;
  width: 100%;
  font-size: 0.92em;
}
.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid #ddd;
  padding: 6px 10px;
  text-align: left;
}
.md-body :deep(th) {
  background: #eef0f7;
  font-weight: 600;
}

.md-body :deep(hr) {
  border: none;
  border-top: 1px solid #e0e0e0;
  margin: 0.8em 0;
}

.md-body :deep(a) {
  color: var(--brand-1, #5b6ef5);
  text-decoration: none;
}
.md-body :deep(a:hover) {
  text-decoration: underline;
}

.md-body :deep(strong) {
  font-weight: 700;
}
.md-body :deep(em) {
  font-style: italic;
}
</style>
