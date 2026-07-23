<template>
  <div class="qa-page">
    <div class="hero">
      <div>
        <div class="eyebrow">智能答疑</div>
        <h2>把课程里的问题直接问给助教</h2>
        <p>先选课程，再提问。回答会保留来源、检索和大模型状态，适合学生复习，也适合教师检查知识库是否覆盖到位。</p>
      </div>
      <n-space>
        <n-button tertiary type="primary" :disabled="!selectedCourse" @click="loadHistory">历史记录</n-button>
        <n-button tertiary type="success" @click="$router.push('/student-dashboard')">查看学情</n-button>
      </n-space>
    </div>

    <n-card :bordered="false" class="control-card">
      <n-space align="center" justify="space-between" wrap>
        <n-select v-model:value="selectedCourse" :options="courseOptions" placeholder="选择课程" style="min-width: 260px" @update:value="onCourseChange" />
        <n-tag v-if="selectedCourse" type="success" round>已选课程</n-tag>
      </n-space>
    </n-card>

    <n-alert v-if="!selectedCourse" type="info" :bordered="false" title="请先选择一门已加入的课程，再向智能助教提问" />

    <n-card v-else :bordered="false" class="chat-card">
      <div ref="scrollRef" class="chat-body">
        <n-empty v-if="messages.length === 0" description="向智能助教提问吧，例如：什么是闭包？" />
        <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
          <div class="bubble" :class="m.role">
            <template v-if="m.role === 'user'">{{ m.content }}</template>
            <template v-else>
              <div class="meta">
                <n-tag v-if="m.useLlm" type="success" size="small" round>大模型</n-tag>
                <n-tag v-else type="info" size="small" round>仅检索</n-tag>
                <span v-if="m.costMs" class="cost">{{ m.costMs }} ms</span>
              </div>
              <div v-if="m.typing" class="answer streaming-text" v-text="m.content"></div>
              <span v-if="m.typing" class="cursor">▊</span>
              <div v-if="!m.typing" class="answer md-body" v-html="renderMd(m.content)"></div>
              <n-collapse v-if="m.sources && m.sources.length" class="sources">
                <n-collapse-item :title="`参考来源（${m.sources.length}）`">
                  <div v-for="(s, idx) in m.sources" :key="idx" class="source-item">
                    <div class="source-title">资料 {{ idx + 1 }} · {{ s.docTitle }}</div>
                    <div class="source-content">{{ s.content }}</div>
                  </div>
                </n-collapse-item>
              </n-collapse>
            </template>
          </div>
        </div>
      </div>
      <div class="chat-input">
        <n-input
          v-model:value="question"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          placeholder="输入你的问题，回车发送（Shift+Enter 换行）"
          @keydown.enter.exact.prevent="send"
        />
        <n-button type="primary" :loading="asking" @click="send">发送</n-button>
      </div>
    </n-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { NAlert, NButton, NCard, NCollapse, NCollapseItem, NEmpty, NInput, NSelect, NSpace, NTag } from 'naive-ui'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { listCourses } from '../api/course'
import { askQaStream, qaHistory } from '../api/qa'

const courses = ref([])
const courseOptions = computed(() => courses.value.map((c) => ({ label: c.courseName, value: c.id })))
const selectedCourse = ref(Number(localStorage.getItem('qa_selectedCourse')) || null)
const messages = ref([])
const question = ref('')
const asking = ref(false)
const scrollRef = ref(null)

marked.setOptions({ gfm: true, breaks: true })

function normalizeMd(text) {
  return text.split(/(```[\s\S]*?```)/).map((seg, i) => (i % 2 === 1 ? seg : seg.replace(/^(#{1,6})([^\s#])/gm, '$1 $2'))).join('')
}
function renderMd(text) {
  if (!text) return ''
  let clean = text.replace(/<think>[\s\S]*?<\/think>/g, '').trim()
  if (!clean) return ''
  clean = normalizeMd(clean)
  try {
    return DOMPurify.sanitize(marked.parse(clean))
  } catch {
    return '<p>' + clean.replace(/\n/g, '<br>') + '</p>'
  }
}

async function loadCourses() {
  courses.value = await listCourses()
}
function onCourseChange() {
  messages.value = []
  if (selectedCourse.value) localStorage.setItem('qa_selectedCourse', selectedCourse.value)
  else localStorage.removeItem('qa_selectedCourse')
  loadHistory()
}
async function loadHistory() {
  if (!selectedCourse.value) return
  const list = await qaHistory(selectedCourse.value)
  const result = []
  for (const r of (list || []).reverse()) {
    result.push({ role: 'user', content: r.question })
    result.push({ role: 'ai', content: r.answer, useLlm: r.useLlm === 1, costMs: r.costMs || 0, sources: r.sources || [], typing: false })
  }
  messages.value = result
  scrollToBottom()
}
async function send() {
  const q = question.value.trim()
  if (!q) return
  if (!selectedCourse.value) return
  messages.value.push({ role: 'user', content: q })
  question.value = ''
  asking.value = true
  const aiIndex = messages.value.length
  messages.value.push({ role: 'ai', content: '', useLlm: false, costMs: 0, sources: [], typing: true })
  scrollToBottom()
  try {
    const resp = await askQaStream({ courseId: selectedCourse.value, question: q })
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split('\n\n')
      buffer = events.pop()
      for (const event of events) {
        if (!event.trim()) continue
        let eventType = ''
        const dataLines = []
        for (const line of event.split('\n')) {
          if (line.startsWith('event:')) eventType = line.substring(6).trim()
          else if (line.startsWith('data:')) dataLines.push(line.substring(5))
        }
        const eventData = dataLines.join('\n')
        if (eventType && eventData) handleSseEvent(eventType, eventData, aiIndex)
      }
      scrollToBottom()
    }
    if (buffer.trim()) {
      let eventType = ''
      const dataLines = []
      for (const line of buffer.split('\n')) {
        if (line.startsWith('event:')) eventType = line.substring(6).trim()
        else if (line.startsWith('data:')) dataLines.push(line.substring(5))
      }
      const eventData = dataLines.join('\n')
      if (eventType && eventData) handleSseEvent(eventType, eventData, aiIndex)
    }
  } finally {
    asking.value = false
    const finalMsg = messages.value[aiIndex]
    if (finalMsg && finalMsg.typing) {
      messages.value[aiIndex] = { ...finalMsg, typing: false }
      messages.value = [...messages.value]
    }
    await nextTick()
    scrollToBottom()
  }
}
function handleSseEvent(type, data, aiIndex) {
  const msg = messages.value[aiIndex]
  if (!msg) return
  if (type === 'sources') {
    try { messages.value[aiIndex] = { ...msg, sources: JSON.parse(data) } } catch {}
  } else if (type === 'chunk') {
    messages.value[aiIndex] = { ...msg, content: msg.content + data, useLlm: true, typing: true }
  } else if (type === 'done') {
    try {
      const meta = JSON.parse(data)
      messages.value[aiIndex] = { ...msg, useLlm: meta.useLlm, costMs: meta.costMs, typing: false }
    } catch {
      messages.value[aiIndex] = { ...msg, typing: false }
    }
  } else if (type === 'error') {
    messages.value[aiIndex] = { ...msg, content: data || '服务异常，请稍后重试', typing: false }
  }
}
function scrollToBottom() { nextTick(() => { if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight }) }
onMounted(async () => {
  await loadCourses()
  if (selectedCourse.value) loadHistory()
})
</script>

<style scoped>
.qa-page { display: flex; flex-direction: column; gap: 16px; }
.hero { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.eyebrow { color: #4cb6c2; font-size: 12px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.hero h2 { margin: 8px 0 10px; font-size: 28px; color: #16313b; }
.hero p { margin: 0; max-width: 60ch; color: #5f6b73; line-height: 1.7; }
.control-card, .chat-card { border-radius: 18px; box-shadow: 0 16px 40px rgba(48, 102, 107, 0.08); }
.chat-card { padding: 0; overflow: hidden; }
.chat-body { min-height: 420px; max-height: 68vh; overflow-y: auto; padding: 22px; background: linear-gradient(180deg, #fbfefe 0%, #f5fbfb 100%); }
.msg-row { display: flex; margin-bottom: 14px; }
.msg-row.user { justify-content: flex-end; }
.msg-row.ai { justify-content: flex-start; }
.bubble { max-width: 78%; padding: 14px 16px; border-radius: 18px; line-height: 1.75; word-break: break-word; font-size: 14px; }
.bubble.user { background: linear-gradient(135deg, #42B5BB, #7bd3ca); color: #fff; border-bottom-right-radius: 6px; }
.bubble.ai { background: #ffffff; color: #1f2937; border: 1px solid #d9edeb; border-bottom-left-radius: 6px; box-shadow: 0 10px 24px rgba(48, 102, 107, 0.06); }
.meta { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.cost { color: #8a98a1; font-size: 11.5px; }
.cursor { display: inline-block; animation: blink 0.8s infinite; color: #4cb6c2; margin-left: 2px; }
@keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0; } }
.streaming-text { white-space: pre-wrap; word-break: break-word; }
.sources { margin-top: 12px; }
.source-item { margin-bottom: 10px; padding: 10px 12px; background: #f7fbfb; border-radius: 10px; border: 1px solid #e4f4f2; }
.source-title { font-weight: 700; color: #2e7f86; margin-bottom: 4px; font-size: 12.5px; }
.source-content { font-size: 13px; color: #5f6b73; line-height: 1.6; }
.chat-input { display: flex; gap: 10px; padding: 16px 20px; border-top: 1px solid #e4f4f2; background: #fff; align-items: flex-end; }
.chat-input :deep(.n-input) { flex: 1; }
.answer :deep(h1), .answer :deep(h2), .answer :deep(h3) { margin: 0.5em 0 0.3em; }
.answer :deep(p) { margin: 0.35em 0; }
.answer :deep(code) { background: rgba(66,181,187,0.1); padding: 2px 6px; border-radius: 4px; }
.answer :deep(pre) { background: #0f172a; color: #e2e8f0; padding: 14px; border-radius: 12px; overflow-x: auto; }
</style>
