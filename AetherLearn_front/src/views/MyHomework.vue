<template>
  <div class="student-workbench">
    <div class="hero">
      <div>
        <div class="eyebrow">我的作业</div>
        <h2>课程作业与测验都在这里完成</h2>
        <p>先选课程，再进入作答。完成后能直接看到得分、解析和教师复核状态，减少来回切页的负担。</p>
      </div>
      <n-card class="hero-card" :bordered="false">
        <div class="hero-card-title">当前学习状态</div>
        <div class="hero-card-value">{{ assignments.length }} 份任务</div>
        <div class="hero-card-sub">作业、测验、提交与查看结果都在同一页面里完成。</div>
      </n-card>
    </div>

    <n-card :bordered="false" class="control-card">
      <n-space align="center" justify="space-between" wrap>
        <n-select v-model:value="selectedCourse" :options="courseOptions" placeholder="全部课程" clearable style="min-width: 240px" @update:value="loadAssignments" />
        <n-tag type="info" round>已加入课程</n-tag>
      </n-space>
    </n-card>

    <n-grid :cols="3" :x-gap="16" :y-gap="16" responsive="screen">
      <n-grid-item v-for="item in statCards" :key="item.label">
        <n-card :bordered="false" class="stat-card">
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </n-card>
      </n-grid-item>
    </n-grid>

    <n-card :bordered="false" class="panel">
      <n-table :single-line="false" :bordered="false">
        <thead>
          <tr>
            <th>作业</th>
            <th>类型</th>
            <th>总分</th>
            <th>截止时间</th>
            <th>我的成绩</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in pagedAssignments" :key="row.id">
            <td>{{ row.title }}</td>
            <td>
              <n-tag :type="row.type === 2 ? 'warning' : 'info'" round>{{ row.type === 2 ? '测验' : '作业' }}</n-tag>
            </td>
            <td>{{ row.totalScore }}</td>
            <td>{{ row.endTime || '—' }}</td>
            <td>
              <template v-if="resultMap[row.id]?.submitted">
                <span class="score">{{ resultMap[row.id]?.earnedScore }} / {{ row.totalScore }}</span>
              </template>
              <n-tag v-else type="default" round>未提交</n-tag>
            </td>
            <td>
              <n-space>
                <n-button v-if="!resultMap[row.id]?.submitted" size="small" type="primary" @click="openDo(row)">去作答</n-button>
                <n-button v-else size="small" secondary @click="openResult(row)">查看结果</n-button>
              </n-space>
            </td>
          </tr>
        </tbody>
      </n-table>
      <n-empty v-if="!loading && assignments.length === 0" description="暂无作业" />
      <div v-if="assignments.length > pageSize" class="pagination-wrap">
        <n-pagination v-model:page="currentPage" :page-size="pageSize" :item-count="assignments.length" />
      </div>
    </n-card>

    <n-modal v-model:show="doVisible" preset="card" :title="currentAssignment?.title || '作业'" class="work-modal">
      <template v-if="phase === 'answer'">
        <div v-if="!detail">
          <n-alert type="info" :bordered="false" title="正在加载题目…" />
        </div>
        <div v-for="(q, i) in detail?.questions || []" :key="q.id" class="q-block">
          <div class="q-title">
            <span class="q-seq">{{ i + 1 }}.</span>
            <n-tag v-if="q.type === 5" type="warning" round>{{ typeLabel(q.type) }}</n-tag>
            <n-tag v-else type="info" round>{{ typeLabel(q.type) }}</n-tag>
            <span class="q-score">{{ q.score }} 分</span>
            <span class="q-content">{{ q.content }}</span>
          </div>
          <div v-if="isChoiceQuestion(q) && choiceOptions(q).length" class="q-opts">
            <n-radio-group v-if="q.type === 1 || q.type === 3" v-model:value="answers[q.id]">
              <n-space vertical>
                <n-radio v-for="(o, idx) in choiceOptions(q)" :key="idx" :value="q.type === 3 ? o : letter(idx)">
                  {{ q.type === 3 ? o : letter(idx) + '. ' + o }}
                </n-radio>
              </n-space>
            </n-radio-group>
            <n-checkbox-group v-else-if="q.type === 2" v-model:value="answers[q.id]">
              <n-space vertical>
                <n-checkbox v-for="(o, idx) in choiceOptions(q)" :key="idx" :value="letter(idx)">{{ letter(idx) + '. ' + o }}</n-checkbox>
              </n-space>
            </n-checkbox-group>
          </div>
          <n-alert v-else-if="isChoiceQuestion(q)" type="warning" :bordered="false" title="这道选择题暂缺选项，请联系教师重新生成或编辑题目。" />
          <div v-else-if="isRichAnswerQuestion(q)" class="rich-answer">
            <Toolbar
              :editor="editorInstances[q.id]"
              :default-config="toolbarConfig"
              mode="default"
              class="wang-toolbar"
            />
            <Editor
              v-model="answers[q.id]"
              :default-config="editorConfig"
              mode="default"
              class="wang-editor"
              @on-created="(editor) => onEditorCreated(q.id, editor)"
            />
          </div>
          <n-input v-else v-model:value="answers[q.id]" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="请输入作答内容" />
        </div>
      </template>

      <template v-else>
        <div class="result-summary">
          <span class="rs-score">{{ currentResult?.earnedScore }} <small>/ {{ currentResult?.totalScore }}</small></span>
          <span class="muted">总分</span>
        </div>
        <div v-for="(item, i) in currentResult?.items || []" :key="i" class="r-block">
          <div class="r-title">
            <span class="r-seq">{{ i + 1 }}.</span>
            <n-tag v-if="item.type === 5" type="warning" round>{{ typeLabel(item.type) }}</n-tag>
            <n-tag v-else type="info" round>{{ typeLabel(item.type) }}</n-tag>
            <span class="r-content">{{ item.content }}</span>
          </div>
          <div class="r-line">
            <span class="muted">你的作答：</span><span class="answer-html" v-html="formatAnswer(item.yourAnswer)"></span>
            <n-tag v-if="item.correct === true" type="success" size="small" round>正确</n-tag>
            <n-tag v-else-if="item.correct === false" type="error" size="small" round>错误</n-tag>
            <n-tag v-else-if="item.gradeType === 2 && item.reviewStatus === 0" type="warning" size="small" round>AI 批改·待复核</n-tag>
            <n-tag v-else-if="item.gradeType === 2" type="info" size="small" round>教师已复核</n-tag>
            <span class="r-score">本题 {{ item.score }} 分</span>
          </div>
          <div class="r-line muted">参考答案：{{ item.standardAnswer }}</div>
          <div v-if="item.analysis" class="r-line muted">解析：{{ item.analysis }}</div>
          <div class="r-feedback">{{ item.feedback }}</div>
        </div>
      </template>

      <template #action>
        <n-space justify="end">
          <n-button @click="doVisible = false">关闭</n-button>
          <n-button v-if="phase === 'answer'" type="primary" :loading="submitting" @click="onSubmit">提交作答</n-button>
          <n-button v-else type="primary" @click="phase = 'answer'">重新作答</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
import { NAlert, NButton, NCard, NCheckbox, NCheckboxGroup, NEmpty, NGrid, NGridItem, NInput, NModal, NPagination, NRadio, NRadioGroup, NSpace, NSelect, NTable, NTag, createDiscreteApi } from 'naive-ui'
import { Boot } from '@wangeditor/editor'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import { listCourses } from '../api/course'
import { listAssignments as fetchAssignments, getAssignmentDetail, submitAnswers, getAnswerResult } from '../api/homework'
import { uploadFile } from '../api/file'
import DOMPurify from 'dompurify'

const { message } = createDiscreteApi(['message'])
const courses = ref([])
const selectedCourse = ref(null)
const assignments = ref([])
const loading = ref(false)
const resultMap = reactive({})
const currentPage = ref(1)
const pageSize = 10
const pagedAssignments = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return assignments.value.slice(start, start + pageSize)
})
const courseOptions = computed(() => courses.value.map((c) => ({ label: c.courseName, value: c.id })))
const questionTypes = [
  { value: 1, label: '单选' }, { value: 2, label: '多选' }, { value: 3, label: '判断' }, { value: 4, label: '填空' }, { value: 5, label: '简答' }
]
const typeLabel = (t) => questionTypes.find((x) => x.value === t)?.label || '未知'
const statCards = computed(() => [
  { label: '作业总数', value: assignments.value.length },
  { label: '已提交', value: Object.values(resultMap).filter((i) => i.submitted).length },
  { label: '未提交', value: assignments.value.length - Object.values(resultMap).filter((i) => i.submitted).length }
])
const doVisible = ref(false)
const phase = ref('answer')
const currentAssignment = ref(null)
const detail = ref(null)
const answers = reactive({})
const currentResult = ref(null)
const submitting = ref(false)
const letter = (i) => String.fromCharCode(65 + i)
const editorInstances = shallowRef({})
const ATTACHMENT_MENU_KEY = 'insertAttachment'
let attachmentMenuRegistered = false

function escapeHtml(text = '') {
  return String(text)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

// 处理附件选择、上传与插入编辑器链接
function pickAttachmentFile(editor) {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.zip,.rar,.7z,.csv'
  input.style.display = 'none'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    try {
      const data = await uploadFile(file, 'answer')
      const fileUrl = data?.url
      if (!fileUrl) {
        message.error('附件上传失败，未返回地址')
        return
      }
      editor.focus()
      editor.restoreSelection()
      editor.dangerouslyInsertHtml(
        `<p><a href="${escapeHtml(fileUrl)}" target="_blank" rel="noopener noreferrer">${escapeHtml(file.name || '附件')}</a></p>`
      )
      message.success('附件已插入')
    } catch (error) {
      message.error(error?.message || '附件上传失败')
    } finally {
      input.remove()
    }
  }
  document.body.appendChild(input)
  input.click()
}

// 注册 WangEditor 自定义“插入附件”菜单
function registerAttachmentMenu() {
  if (attachmentMenuRegistered) return
  attachmentMenuRegistered = true
  Boot.registerMenu(
    {
      key: ATTACHMENT_MENU_KEY,
      factory() {
        return {
          title: '插入附件',
          iconSvg:
            '<svg viewBox="0 0 1024 1024" width="16" height="16"><path fill="currentColor" d="M336 736a176 176 0 0 1 0-248l256-256a112 112 0 1 1 160 160L456 688a48 48 0 0 1-68-68l224-224a16 16 0 1 0-23-23L365 597a112 112 0 0 0 160 160l256-256a208 208 0 1 0-294-294L231 463a16 16 0 1 0 23 23l256-256a272 272 0 1 1 384 384l-256 256a176 176 0 0 1-248 0Z"/></svg>',
          tag: 'button',
          alwaysEnable: true,
          getValue() {
            return ''
          },
          isActive() {
            return false
          },
          isDisabled() {
            return false
          },
          exec(editor) {
            pickAttachmentFile(editor)
          }
        }
      }
    }
  )
}

registerAttachmentMenu()
const toolbarConfig = {
  insertKeys: {
    index: 999,
    keys: ATTACHMENT_MENU_KEY
  },
  excludeKeys: [
    'group-video',
    'insertTable',
    'codeBlock',
    'fullScreen'
  ]
}
const editorConfig = {
  placeholder: '在这里输入文字答案，可插入图片、文件链接并整理要点。',
  MENU_CONF: {
    uploadImage: {
      async customUpload(file, insertFn) {
        const data = await uploadFile(file, 'answer')
        insertFn(data.url, file.name || '作答图片', data.url)
      }
    },
    uploadAttachment: {
      async customUpload(file, insertFn) {
        const data = await uploadFile(file, 'answer')
        insertFn(file.name || '附件', data.url)
      }
    }
  }
}

const isChoiceQuestion = (q) => q.type === 1 || q.type === 2 || q.type === 3
const isRichAnswerQuestion = (q) => q.type === 4 || q.type === 5
const choiceOptions = (q) => {
  if (q.type === 3) return ['正确', '错误']
  return Array.isArray(q.options) ? q.options.filter((o) => o && String(o).trim()) : []
}

function onEditorCreated(id, editor) {
  editorInstances.value = { ...editorInstances.value, [id]: editor }
}

function formatAnswer(html) {
  if (!html) return '（空）'
  return DOMPurify.sanitize(html)
}

function resetAnswers() {
  Object.keys(answers).forEach((k) => delete answers[k])
  destroyEditors()
}

function destroyEditors() {
  Object.values(editorInstances.value).forEach((editor) => {
    if (editor && !editor.isDestroyed) editor.destroy()
  })
  editorInstances.value = {}
}

async function openDo(row) {
  currentAssignment.value = row
  phase.value = 'answer'
  detail.value = null
  currentResult.value = null
  resetAnswers()
  doVisible.value = true
  const d = await getAssignmentDetail(row.id)
  detail.value = d
  d.questions.forEach((q) => {
    if (q.type === 2) answers[q.id] = []
    else answers[q.id] = ''
  })
}
async function openResult(row) {
  currentAssignment.value = row
  phase.value = 'result'
  doVisible.value = true
  currentResult.value = await getAnswerResult(row.id)
}
async function onSubmit() {
  const qs = detail.value?.questions || []
  const payload = { assignmentId: currentAssignment.value.id, answers: qs.map((q) => ({ questionId: q.id, content: q.type === 2 ? (answers[q.id] || []).join('') : (answers[q.id] || '') })) }
  const hasEssay = qs.some((q) => q.type === 5)
  submitting.value = true
  try {
    if (hasEssay) await new Promise((r) => setTimeout(r, 1200))
    const res = await submitAnswers(payload)
    currentResult.value = res
    resultMap[currentAssignment.value.id] = { submitted: true, earnedScore: res.earnedScore }
    phase.value = 'result'
    message.success('提交成功')
  } finally {
    submitting.value = false
  }
}
async function loadAssignments() {
  loading.value = true
  try {
    assignments.value = await fetchAssignments(selectedCourse.value)
    await Promise.all(assignments.value.map(async (a) => {
      try {
        const r = await getAnswerResult(a.id)
        resultMap[a.id] = { submitted: !!r.submitted, earnedScore: r.earnedScore }
      } catch {
        resultMap[a.id] = { submitted: false, earnedScore: 0 }
      }
    }))
  } finally {
    loading.value = false
  }
}
async function loadCourses() { courses.value = await listCourses() }
onMounted(() => { loadCourses(); loadAssignments() })
onBeforeUnmount(() => destroyEditors())
</script>

<style scoped>
.student-workbench { display: flex; flex-direction: column; gap: 16px; }
.hero { display: grid; grid-template-columns: 1.4fr 0.9fr; gap: 16px; }
.eyebrow { color: #4cb6c2; font-size: 12px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.hero h2 { margin: 8px 0 10px; font-size: 28px; color: #16313b; }
.hero p { margin: 0; max-width: 60ch; color: #5f6b73; line-height: 1.7; }
.hero-card, .stat-card, .panel, .control-card { border-radius: 18px; box-shadow: 0 16px 40px rgba(48, 102, 107, 0.08); }
.hero-card { background: linear-gradient(135deg, #f4fbfb 0%, #eaf8f7 100%); }
.hero-card-title { color: #5f6b73; font-size: 12px; margin-bottom: 8px; }
.hero-card-value { font-size: 22px; font-weight: 700; color: #18323d; margin-bottom: 6px; }
.hero-card-sub { color: #5f6b73; font-size: 13px; line-height: 1.6; }
.stat-card { background: #fff; padding: 18px 20px; }
.stat-value { font-size: 30px; font-weight: 700; color: #18323d; }
.stat-label { color: #6b7280; margin-top: 6px; font-size: 13px; }
.panel { background: rgba(255,255,255,0.9); padding: 6px; }
.score { font-weight: 700; color: #2f7f86; }
.pagination-wrap { display: flex; justify-content: flex-end; padding: 16px 0 4px; }
.q-block { border: 1px solid #e4f4f2; border-radius: 14px; padding: 16px; margin-bottom: 14px; background: #f7fbfb; }
.q-title { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
.q-seq { font-weight: 700; color: #2f7f86; }
.q-score { color: #8a98a1; font-size: 12.5px; }
.q-content { font-weight: 600; }
.q-opts { padding-left: 20px; }
.rich-answer { margin-top: 10px; border: 1px solid #d9edeb; border-radius: 14px; background: #fff; overflow: hidden; }
.wang-toolbar { border-bottom: 1px solid #e4f4f2; background: #f2fbfa; }
.wang-editor { min-height: 180px; }
.rich-answer :deep(.w-e-text-container) { min-height: 180px !important; }
.rich-answer :deep(.w-e-text-placeholder) { color: #9aa7ad; font-style: normal; }
.rich-answer :deep(.w-e-bar-item button:hover) { background: rgba(66,181,187,0.12); }
.answer-html { display: inline-block; max-width: 100%; line-height: 1.6; }
.answer-html :deep(p) { margin: 0 0 4px; }
.answer-html :deep(ul) { margin: 4px 0; padding-left: 20px; }
.answer-html :deep(img) { max-width: min(100%, 520px); border-radius: 10px; display: block; margin: 8px 0; }
.answer-html :deep(a) { color: #2f7f86; font-weight: 700; }
.result-summary { display: flex; align-items: baseline; gap: 10px; margin-bottom: 18px; }
.rs-score { font-size: 32px; font-weight: 700; color: #18323d; }
.rs-score small { font-size: 14px; color: #6b7280; font-weight: 500; }
.r-block { border: 1px solid #e4f4f2; border-radius: 14px; padding: 14px 16px; margin-bottom: 12px; background: #f7fbfb; }
.r-title { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.r-seq { font-weight: 700; color: #2f7f86; }
.r-content { font-weight: 600; }
.r-line { margin: 4px 0; font-size: 13.5px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.r-score { margin-left: auto; color: #2f7f86; font-weight: 700; }
.r-feedback { margin-top: 8px; color: #1f2937; font-size: 13px; background: #fff; border: 1px dashed #d9edeb; border-radius: 12px; padding: 10px 12px; line-height: 1.6; }
.work-modal { width: min(860px, calc(100vw - 24px)); }
@media (max-width: 900px) { .hero { grid-template-columns: 1fr; } }
</style>
