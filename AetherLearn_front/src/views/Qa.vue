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
                <n-tag v-if="m.retrievalStrategy === 'hybrid'" type="primary" size="small" round>混合检索</n-tag>
                <n-tag v-else-if="m.retrievalStrategy === 'bm25'" type="warning" size="small" round>BM25 降级</n-tag>
                <!-- 只有模型真的调用了工具才出现：0 轮代表它判断首轮资料够用，不必打扰用户 -->
                <n-tag v-if="m.agentToolRounds > 0" size="small" round :bordered="false" class="tag-agent">
                  Agent · {{ m.agentToolRounds }} 轮工具
                </n-tag>
                <span v-if="m.costMs" class="cost">{{ m.costMs }} ms</span>
              </div>
              <div v-if="showMetrics(m)" class="metrics">
                <span class="metric"><i class="dot dot-embed"></i>Embedding {{ formatMs(m.embeddingMs) }}</span>
                <span class="metric"><i class="dot dot-milvus"></i>Milvus {{ formatMs(m.milvusMs) }}</span>
                <!-- 精排被跳过时说"未启用"：写 0 ms 会被读成"精排很快"，那是反的 -->
                <span v-if="m.rerankStatus === 'skipped'" class="metric">
                  <i class="dot dot-muted"></i>Rerank 未启用
                </span>
                <span v-else class="metric"><i class="dot dot-rerank"></i>Rerank {{ formatMs(m.rerankMs) }}</span>
                <span class="metric metric-total">检索 {{ formatMs(m.retrievalMs) }}</span>
              </div>
              <!-- Agent 的过程：每次工具调用一行，运行中原地变绿，不做逐事件刷屏 -->
              <div v-if="m.steps && m.steps.length" class="steps">
                <div v-for="(s, si) in m.steps" :key="si" class="step" :class="s.status">
                  <i class="step-dot"></i>
                  <span class="step-label">{{ s.label }}</span>
                  <span v-if="s.status === 'done' && s.elapsedMs != null" class="step-time">{{ formatMs(s.elapsedMs) }}</span>
                </div>
              </div>
              <!-- 两条独立的降级说明：检索轴只影响来源，用低调的中性样式；生成轴影响答案本身，用警示样式 -->
              <div v-if="m.fallbackReason" class="notice">
                <i class="dot dot-muted"></i>
                <span>检索已降级为本地 BM25：{{ fallbackLabel(m.fallbackReason) }}</span>
              </div>
              <div v-if="llmNotice(m)" class="notice notice-warn">
                <i class="dot dot-warn"></i>
                <span>{{ llmNotice(m) }}</span>
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
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
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
let activeController = null
let activeRequestId = 0
let historyRequestId = 0

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
  if (selectedCourse.value && !courses.value.some((course) => course.id === selectedCourse.value)) {
    selectedCourse.value = null
    messages.value = []
    localStorage.removeItem('qa_selectedCourse')
  }
}
function onCourseChange() {
  activeRequestId += 1
  activeController?.abort()
  activeController = null
  asking.value = false
  messages.value = []
  if (selectedCourse.value) localStorage.setItem('qa_selectedCourse', selectedCourse.value)
  else localStorage.removeItem('qa_selectedCourse')
  loadHistory()
}
async function loadHistory() {
  if (!selectedCourse.value) return
  const courseAtRequest = selectedCourse.value
  const requestId = ++historyRequestId
  try {
    const list = await qaHistory(courseAtRequest)
    if (requestId !== historyRequestId || selectedCourse.value !== courseAtRequest) return
    const result = []
    for (const r of (list || []).reverse()) {
      result.push({ role: 'user', content: r.question })
      result.push({
        role: 'ai',
        content: r.answer,
        useLlm: r.useLlm === 1,
        costMs: r.costMs || 0,
        sources: r.sources || [],
        retrievalMs: 0,
        embeddingMs: 0,
        milvusMs: 0,
        rerankMs: 0,
        retrievalStrategy: '',
        rerankStatus: '',
        fallbackReason: '',
        llmProvider: '',
        llmFallbackReason: '',
        agentToolRounds: 0,
        steps: [],
        typing: false
      })
    }
    messages.value = result
    scrollToBottom()
  } catch {
    // 统一请求拦截器已提示错误，保留当前聊天内容避免整页空白。
  }
}
async function send() {
  if (asking.value) return
  const q = question.value.trim()
  if (!q) return
  if (!selectedCourse.value) return
  const requestId = ++activeRequestId
  const courseAtRequest = selectedCourse.value
  activeController?.abort()
  activeController = new AbortController()
  messages.value.push({ role: 'user', content: q })
  question.value = ''
  asking.value = true
  const aiIndex = messages.value.length
  messages.value.push({
    role: 'ai',
    content: '',
    useLlm: false,
    costMs: 0,
    sources: [],
    retrievalMs: 0,
    embeddingMs: 0,
    milvusMs: 0,
    rerankMs: 0,
    retrievalStrategy: '',
    rerankStatus: '',
    fallbackReason: '',
    llmProvider: '',
    llmFallbackReason: '',
    agentToolRounds: 0,
    steps: [],
    typing: true
  })
  scrollToBottom()
  try {
    const resp = await askQaStream({ courseId: courseAtRequest, question: q }, activeController.signal)
    if (!resp.ok) {
      let detail = `请求失败（${resp.status}）`
      try {
        const body = await resp.json()
        detail = body?.message || detail
      } catch {
        // 非 JSON 错误响应使用状态码提示。
      }
      throw new Error(detail)
    }
    if (!resp.body) throw new Error('服务器未返回问答流')
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
        if (eventType && eventData && requestId === activeRequestId && courseAtRequest === selectedCourse.value) {
          handleSseEvent(eventType, eventData, aiIndex)
        }
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
      if (eventType && eventData && requestId === activeRequestId && courseAtRequest === selectedCourse.value) {
        handleSseEvent(eventType, eventData, aiIndex)
      }
    }
  } catch (error) {
    if (error?.name !== 'AbortError' && requestId === activeRequestId && courseAtRequest === selectedCourse.value) {
      const msg = messages.value[aiIndex]
      if (msg) {
        messages.value[aiIndex] = { ...msg, content: error?.message || '问答服务暂时不可用，请稍后重试', typing: false }
        messages.value = [...messages.value]
      }
    }
  } finally {
    if (requestId === activeRequestId) {
      asking.value = false
      activeController = null
      const finalMsg = messages.value[aiIndex]
      if (finalMsg && finalMsg.typing) {
        messages.value[aiIndex] = { ...finalMsg, typing: false }
        messages.value = [...messages.value]
      }
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
  } else if (type === 'step') {
    try { messages.value[aiIndex] = { ...msg, steps: applyStepEvent(msg.steps || [], JSON.parse(data)) } } catch {}
  } else if (type === 'done') {
    try {
      const meta = JSON.parse(data)
      messages.value[aiIndex] = {
        ...msg,
        useLlm: meta.useLlm,
        costMs: meta.costMs,
        retrievalMs: meta.retrievalMs || 0,
        embeddingMs: meta.embeddingMs || 0,
        milvusMs: meta.milvusMs || 0,
        rerankMs: meta.rerankMs || 0,
        retrievalStrategy: meta.retrievalStrategy || '',
        rerankStatus: meta.rerankStatus || '',
        fallbackReason: meta.fallbackReason || '',
        llmProvider: meta.llmProvider || '',
        llmFallbackReason: meta.llmFallbackReason || '',
        agentToolRounds: meta.agentToolRounds || 0,
        // 收到 done 说明本轮已结束：把还在"运行中"的过程行收尾，避免留一个永远转圈的圆点
        steps: (msg.steps || []).map((s) => (s.status === 'running' ? { ...s, status: 'done' } : s)),
        typing: false
      }
    } catch {
      messages.value[aiIndex] = { ...msg, typing: false }
    }
  } else if (type === 'error') {
    messages.value[aiIndex] = { ...msg, content: data || '服务异常，请稍后重试', typing: false }
  }
}

/** 工具名 → 用户看得懂的动作名；未收录的用具名原文，不编造。 */
const TOOL_LABELS = {
  search_knowledge: '检索课程知识库',
  get_learning_progress: '查询本人学情'
}
function toolLabel(tool) {
  return TOOL_LABELS[tool] || tool || '执行工具'
}

/**
 * 把 step 事件折叠成"每次工具调用一行"。
 *
 * 后端一次工具调用会发三条事件（tool_calls / tool_start / tool_end），
 * 逐条渲染会让用户看到三行重复内容；这里只在 tool_start 建行，
 * tool_end 原地收尾——过程可见，但不刷屏。
 */
function applyStepEvent(steps, event) {
  if (!event || !event.type) return steps
  if (event.type === 'tool_start') {
    return [...steps, { tool: event.tool, label: `正在${toolLabel(event.tool)}…`, status: 'running', elapsedMs: null }]
  }
  if (event.type === 'tool_end') {
    // 从后往前找同名且仍在运行的最近一行（同一工具可能被调用多轮）
    for (let i = steps.length - 1; i >= 0; i -= 1) {
      if (steps[i].tool === event.tool && steps[i].status === 'running') {
        const next = [...steps]
        next[i] = event.ok === false
          ? { ...next[i], label: `${toolLabel(event.tool)}失败`, status: 'failed' }
          : { ...next[i], label: toolLabel(event.tool), status: 'done', elapsedMs: event.elapsed_ms ?? null }
        return next
      }
    }
    return steps
  }
  // tool_calls 与随后的 tool_start 信息重复，忽略即可
  return steps
}
/** 格式化毫秒指标，0 值显示为 0 ms，避免出现 undefined。 */
function formatMs(value) {
  const num = Number(value || 0)
  return `${num} ms`
}
/** 仅在检索阶段产生了可展示指标时显示耗时行。 */
function showMetrics(m) {
  return Number(m.retrievalMs || 0) > 0 || Number(m.embeddingMs || 0) > 0 || Number(m.milvusMs || 0) > 0
}
/** 把后端降级原因翻译成学生能看懂的说明。 */
/**
 * 降级原因 → 中文说明。
 * 覆盖两条链路：检索轴（HybridRetriever 回退 BM25）与生成轴（Python 编排服务调用大模型）。
 */
function fallbackLabel(reason) {
  if (!reason) return '未知原因'
  const labels = {
    // 内部服务共有原因
    python_disabled: 'Python AI 服务未启用',
    python_unauthorized: 'AI 服务密钥不匹配（两端的内部密钥需保持一致）',
    python_not_found: 'AI 服务接口不存在',
    python_error: 'AI 服务调用异常',
    // 检索轴
    python_unavailable: 'AI 检索服务不可用',
    python_timeout: 'AI 检索服务响应超时',
    python_unreachable: 'AI 检索服务未启动或端口不通',
    python_empty: '向量库暂无匹配结果',
    python_unmapped: '检索结果未能关联到课程资料',
    invalid_parameter: '检索参数不合法',
    // 生成轴：未使用大模型
    ai_disabled: '管理员已在系统配置中关闭 AI',
    python_llm_empty: '模型返回了空内容',
    python_llm_timeout: '模型响应超时',
    python_llm_unavailable: '模型服务不可用',
    python_llm_bad_response: '模型返回格式错误',
    python_llm_config_incomplete: '模型接口地址、模型名或 API Key 未配置完整',
    python_llm_prompt_error: '提示词参数缺失（服务端模板与调用方不一致）',
    python_llm_unreachable: 'AI 编排服务未启动或端口不通',
    python_llm_error: '模型调用异常',
    python_retrieval_unavailable: 'AI 检索服务不可用（向量库未就绪）',
    // 生成轴：流式中途失败
    python_stream_interrupted: '生成过程中连接中断',
    python_stream_incomplete: '生成过程提前结束'
  }
  if (labels[reason]) return labels[reason]
  // 动态状态码：llm_http_502 / python_llm_http_502（生成轴）与 python_http_500（检索轴）
  const llmHttp = /^(?:python_)?llm_http_(\d+)$/.exec(reason)
  if (llmHttp) return `模型服务返回 HTTP ${llmHttp[1]}`
  const retrievalHttp = /^python_http_(\d+)$/.exec(reason)
  if (retrievalHttp) return `AI 检索服务返回 HTTP ${retrievalHttp[1]}`
  // Python 侧流式错误码标准化后的形态：python_llm_timeout / python_retrieval_unavailable
  if (reason.startsWith('python_llm_')) return `模型调用失败（${reason.slice(11)}）`
  return reason
}

/**
 * 生成轴降级说明；不需要提示时返回空串。
 * <ul>
 *   <li>回答由模型生成但中途断流：文本已保留，只是可能不完整；</li>
 *   <li>完全没走大模型：下面的内容就是知识库检索结果本身。</li>
 * </ul>
 */
function llmNotice(m) {
  if (m.llmProvider === 'python' && m.llmFallbackReason) {
    return `回答可能不完整：${fallbackLabel(m.llmFallbackReason)}`
  }
  if (m.llmProvider === 'retrieval' && m.llmFallbackReason) {
    return `未使用大模型：${fallbackLabel(m.llmFallbackReason)}`
  }
  return ''
}
function scrollToBottom() { nextTick(() => { if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight }) }
onMounted(async () => {
  await loadCourses()
  if (selectedCourse.value) loadHistory()
})
onUnmounted(() => {
  activeRequestId += 1
  historyRequestId += 1
  activeController?.abort()
  activeController = null
})
</script>

<style scoped>
.qa-page { display: flex; flex-direction: column; gap: 16px; }
.hero { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; flex-wrap: wrap; }
.eyebrow { color: #5b6ef5; font-size: 12px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.hero h2 { margin: 8px 0 10px; font-size: 28px; color: #1f2440; }
.hero p { margin: 0; max-width: 60ch; color: #5f6480; line-height: 1.7; }
.control-card, .chat-card { border-radius: 18px; box-shadow: 0 16px 40px rgba(91, 110, 245, 0.10); }
.chat-card { padding: 0; overflow: hidden; }
.chat-body { min-height: 420px; max-height: 68vh; overflow-y: auto; padding: 22px; background: linear-gradient(180deg, #fbfbfe 0%, #f4f5fb 100%); }
.msg-row { display: flex; margin-bottom: 14px; }
.msg-row.user { justify-content: flex-end; }
.msg-row.ai { justify-content: flex-start; }
.bubble { max-width: min(78%, 100%); padding: 14px 16px; border-radius: 18px; line-height: 1.75; word-break: break-word; font-size: 14px; }
.bubble.user { background: linear-gradient(135deg, #5b6ef5, #7c4dff); color: #fff; border-bottom-right-radius: 6px; }
.bubble.ai { background: #ffffff; color: #1f2937; border: 1px solid #e6e8f5; border-bottom-left-radius: 6px; box-shadow: 0 10px 24px rgba(91, 110, 245, 0.08); }
.meta { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.cost { color: #8a90a6; font-size: 11.5px; }
.metrics { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
.metric { display: inline-flex; align-items: center; gap: 5px; padding: 2px 9px; border-radius: 999px; background: #f2f3fb; color: #5f6480; font-size: 11.5px; font-family: var(--font-mono, "Consolas", monospace); }
.metric-total { background: rgba(91, 110, 245, 0.12); color: #4b56c9; }
.dot { width: 6px; height: 6px; border-radius: 50%; display: inline-block; }
.dot-embed { background: #5b6ef5; }
.dot-milvus { background: #7c4dff; }
.dot-rerank { background: #22a06b; }
/* 降级说明：沿用页面既有的"圆点 + 药丸底"语言，只有圆点与底色区分严重程度 */
.notice { display: flex; align-items: flex-start; gap: 6px; margin-bottom: 8px; padding: 6px 10px; border-radius: 8px; background: #f2f3fb; border: 1px solid #e6e8f5; color: #5f6480; font-size: 12px; line-height: 1.55; }
.notice .dot { margin-top: 6px; flex: none; }
.notice-warn { background: #fff7ed; border-color: #fed7aa; color: #b45309; }
.dot-muted { background: #8a90a6; }
.dot-warn { background: #b45309; }
/* Agent 过程：一行一次工具调用；行间细线让顺序读起来是流程而不是列表 */
.steps { display: flex; flex-direction: column; margin-bottom: 8px; }
.step { position: relative; display: flex; align-items: center; gap: 8px; padding: 2px 0 2px 16px; font-size: 12px; color: #5f6480; }
.step::before { content: ''; position: absolute; left: 5px; top: 0; bottom: 0; width: 1px; background: #e6e8f5; }
.step:first-child::before { top: 50%; }
.step:last-child::before { bottom: 50%; }
.step-dot { position: absolute; left: 2px; width: 7px; height: 7px; border-radius: 50%; background: #c9cfe8; }
.step.running .step-dot { background: #5b6ef5; animation: step-pulse 1.1s ease-in-out infinite; }
.step.running .step-label { color: #4b56c9; }
.step.done .step-dot { background: #22a06b; }
.step.failed .step-dot { background: #b45309; }
.step.failed .step-label { color: #b45309; }
.step-time { font-family: var(--font-mono, "Consolas", monospace); font-size: 11px; color: #8a90a6; }
@keyframes step-pulse { 0%, 100% { opacity: 1; transform: scale(1); } 50% { opacity: 0.45; transform: scale(0.82); } }
@media (prefers-reduced-motion: reduce) { .step.running .step-dot { animation: none; } }
/* 品牌蓝紫渐变：气泡里唯一的高饱和点，标出这是项目的旗舰能力 */
.tag-agent { background: linear-gradient(135deg, #5b6ef5, #7c4dff); color: #fff; font-weight: 600; }
.cursor { display: inline-block; animation: blink 0.8s infinite; color: #5b6ef5; margin-left: 2px; }
@keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0; } }
.streaming-text { white-space: pre-wrap; word-break: break-word; }
.sources { margin-top: 12px; }
.source-item { margin-bottom: 10px; padding: 10px 12px; background: #f7f8fd; border-radius: 10px; border: 1px solid #e6e8f5; }
.source-title { font-weight: 700; color: #4b56c9; margin-bottom: 4px; font-size: 12.5px; }
.source-content { font-size: 13px; color: #5f6480; line-height: 1.6; }
.chat-input { display: flex; gap: 10px; padding: 16px 20px; border-top: 1px solid #e6e8f5; background: #fff; align-items: flex-end; }
.chat-input :deep(.n-input) { flex: 1; }
.answer :deep(h1), .answer :deep(h2), .answer :deep(h3) { margin: 0.5em 0 0.3em; }
.answer :deep(p) { margin: 0.35em 0; }
.answer :deep(code) { background: rgba(91, 110, 245, 0.1); padding: 2px 6px; border-radius: 4px; }
.answer :deep(pre) { background: #0f172a; color: #e2e8f0; padding: 14px; border-radius: 12px; overflow-x: auto; }

@media (max-width: 640px) {
  .hero h2 { font-size: 22px; }
  .bubble { max-width: 100%; }
  .chat-body { padding: 14px; }
  .chat-input { padding: 12px; }
  .metric { font-size: 11px; }
}
</style>
