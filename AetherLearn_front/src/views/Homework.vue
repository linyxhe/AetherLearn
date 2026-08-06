<template>
  <div class="teacher-workbench">
    <div class="hero">
      <div>
        <div class="eyebrow">作业管理</div>
        <h2>发布、批改、复核都在一个教师工作台完成</h2>
        <p>教师先选课程创建作业，再进入题目管理、AI 出题和学生复核。页面保留清晰的任务流，不把复杂操作压成一堆表格按钮。</p>
      </div>
      <n-card class="hero-card" :bordered="false">
        <div class="hero-card-title">当前工作量</div>
        <div class="hero-card-value">{{ assignments.length }} 份作业</div>
        <div class="hero-card-sub">作业数、进行中状态、待复核内容一目了然。</div>
      </n-card>
    </div>

    <n-card :bordered="false" class="control-card">
      <n-space align="center" justify="space-between" wrap>
        <n-space>
          <n-select v-model:value="selectedCourse" :options="courseOptions" placeholder="全部课程" clearable style="min-width: 240px" @update:value="onCourseFilterChange" />
          <n-button secondary :loading="loading" @click="refreshAssignments">刷新</n-button>
          <n-button type="primary" @click="openAssignmentCreate">新建作业</n-button>
        </n-space>
        <n-tag type="info" round>教学工作流</n-tag>
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
            <th>作业标题</th>
            <th>类型</th>
            <th>总分</th>
            <th>截止时间</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in pagedAssignments" :key="row.id">
            <td>{{ row.title }}</td>
            <td><n-tag :type="row.type === 2 ? 'warning' : 'info'" round>{{ row.type === 2 ? '测验' : '作业' }}</n-tag></td>
            <td>{{ row.totalScore }}</td>
            <td>{{ formatDisplayDate(row.endTime) || '—' }}</td>
            <td><n-tag :type="isAssignmentActive(row) ? 'success' : 'default'" round>{{ isAssignmentActive(row) ? '进行中' : '已结束' }}</n-tag></td>
            <td>
              <n-space>
                <n-button size="small" secondary @click="openQuestionManage(row)">题目管理</n-button>
                <n-button size="small" type="success" secondary @click="openReview(row)">批改</n-button>
                <n-button size="small" secondary @click="openAssignmentEdit(row)">编辑</n-button>
                <n-button size="small" secondary type="error" :loading="deletingAssignmentId === row.id" :disabled="deletingAssignmentId !== null && deletingAssignmentId !== row.id" @click="onDeleteAssignment(row)">删除</n-button>
              </n-space>
            </td>
          </tr>
        </tbody>
      </n-table>
      <n-empty v-if="!loading && assignments.length === 0" description="还没有作业，点击右上角新建第一份" />
      <div v-if="assignments.length" class="pagination-wrap">
        <span class="pagination-summary">共 {{ assignments.length }} 条</span>
        <n-pagination
          v-model:page="currentPage"
          v-model:page-size="pageSize"
          :item-count="assignments.length"
          :page-sizes="pageSizeOptions"
          :page-slot="5"
          show-quick-jumper
          show-size-picker
          @update:page="onPageChange"
          @update:page-size="onPageSizeChange"
        />
      </div>
    </n-card>

    <n-modal v-model:show="asmVisible" preset="card" :title="asmIsEdit ? '编辑作业' : '新建作业'" class="work-modal">
      <n-form label-placement="left" label-width="84" :model="asmForm">
        <n-form-item label="所属课程" required>
          <n-select v-model:value="asmForm.courseId" :options="courseOptions" placeholder="选择课程" />
        </n-form-item>
        <n-form-item label="标题" required><n-input v-model:value="asmForm.title" placeholder="如：Java 第一次作业" /></n-form-item>
        <n-form-item label="类型">
          <n-radio-group v-model:value="asmForm.type">
            <n-space>
              <n-radio :value="1">作业</n-radio>
              <n-radio :value="2">测验</n-radio>
            </n-space>
          </n-radio-group>
        </n-form-item>
        <n-form-item label="起止时间">
          <n-date-picker v-model:value="timeRange" type="datetimerange" clearable />
        </n-form-item>
        <n-form-item label="总分">
          <n-input-number v-model:value="asmForm.totalScore" :min="1" :max="1000" />
        </n-form-item>
        <n-form-item label="说明">
          <n-input v-model:value="asmForm.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-space justify="end">
          <n-button @click="asmVisible = false">取消</n-button>
          <n-button type="primary" :loading="asmSaving" @click="onSaveAssignment">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="qmVisible" preset="card" :title="`题目管理 · ${currentAssignment?.title || ''}`" class="work-modal">
      <div class="qm-head">
        <span class="muted">共 {{ questions.length }} 题 · 总分 {{ totalQuestionScore }}</span>
        <n-space>
          <n-button size="small" type="warning" secondary @click="openAiGenerate">AI 出题</n-button>
          <n-button size="small" type="primary" @click="openQuestionCreate">添加题目</n-button>
        </n-space>
      </div>
      <n-table :single-line="false" :bordered="false">
        <thead><tr><th>题型</th><th>题干</th><th>分值</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="row in questions" :key="row.id">
            <td><n-tag :type="row.type === 5 ? 'warning' : 'info'" round>{{ typeLabel(row.type) }}</n-tag></td>
            <td>{{ row.content }}</td>
            <td>{{ row.score }}</td>
            <td><n-space><n-button size="small" secondary @click="openQuestionEdit(row)">编辑</n-button><n-button size="small" secondary type="error" :loading="deletingQuestionId === row.id" :disabled="deletingQuestionId !== null && deletingQuestionId !== row.id" @click="onDeleteQuestion(row)">删除</n-button></n-space></td>
          </tr>
        </tbody>
      </n-table>
      <n-empty v-if="questions.length === 0" description="暂无题目" />
      <template #action>
        <n-space justify="end">
          <n-button @click="qmVisible = false">关闭</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="aiGenVisible" preset="card" title="AI 自动出题" class="mini-modal">
      <n-form label-placement="left" label-width="64" :model="aiGenForm">
        <n-form-item label="题型"><n-select v-model:value="aiGenForm.type" :options="questionTypeOptions" /></n-form-item>
        <n-form-item label="数量"><n-input-number v-model:value="aiGenForm.count" :min="1" :max="20" /></n-form-item>
        <n-alert v-if="aiGenLoading" type="info" :bordered="false">{{ aiGenStage }}</n-alert>
      </n-form>
      <template #action>
        <n-space justify="end">
          <n-button @click="aiGenVisible = false">取消</n-button>
          <n-button type="warning" :loading="aiGenLoading" @click="onAiGenerate">开始生成</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="qVisible" preset="card" :title="qIsEdit ? '编辑题目' : '添加题目'" class="work-modal">
      <n-form label-placement="left" label-width="84" :model="qForm">
        <n-form-item label="题型"><n-select v-model:value="qForm.type" :options="questionTypeOptions" @update:value="onQuestionTypeChange" /></n-form-item>
        <n-form-item label="题干" required><n-input v-model:value="qForm.content" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" /></n-form-item>
        <n-form-item v-if="qForm.type === 1 || qForm.type === 2" label="选项">
          <div class="opt-list">
            <div v-for="(opt, i) in qForm.options" :key="i" class="opt-row">
              <span class="opt-key">{{ letter(i) }}</span>
              <n-input v-model:value="qForm.options[i]" placeholder="选项内容" />
              <n-button text type="error" @click="removeOption(i)">×</n-button>
            </div>
            <n-button text type="primary" @click="addOption">+ 添加选项</n-button>
          </div>
        </n-form-item>
        <n-form-item label="标准答案" required>
          <n-input v-if="qForm.type === 4 || qForm.type === 5" v-model:value="qForm.answer" placeholder="标准答案/要点" />
          <n-select v-else-if="qForm.type === 3" v-model:value="qForm.answer" :options="[{label:'正确',value:'正确'},{label:'错误',value:'错误'}]" />
          <n-select v-else-if="qForm.type === 1" v-model:value="qForm.answer" :options="qForm.options.map((o, i) => ({ label: `${letter(i)} ${o}`, value: letter(i) }))" />
          <n-checkbox-group v-else v-model:value="multiAnswer">
            <n-space>
              <n-checkbox v-for="(opt, i) in qForm.options" :key="i" :value="letter(i)">{{ letter(i) }} {{ opt }}</n-checkbox>
            </n-space>
          </n-checkbox-group>
        </n-form-item>
        <n-form-item label="分值"><n-input-number v-model:value="qForm.score" :min="0" :max="100" /></n-form-item>
        <n-form-item label="知识点"><n-input v-model:value="qForm.knowledgePoint" /></n-form-item>
        <n-form-item label="解析"><n-input v-model:value="qForm.analysis" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" /></n-form-item>
      </n-form>
      <template #action>
        <n-space justify="end">
          <n-button @click="qVisible = false">取消</n-button>
          <n-button type="primary" :loading="qSaving" @click="onSaveQuestion">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="rvVisible" preset="card" :title="`批改 · ${currentAssignment?.title || ''}`" class="work-modal">
      <template v-if="!currentResult">
        <n-table :single-line="false" :bordered="false">
          <thead><tr><th>学生</th><th>得分</th><th>作答</th><th>待复核</th><th>提交时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in submissions" :key="row.studentId">
              <td>{{ row.studentName }}</td>
              <td>{{ row.earnedScore }} / {{ currentAssignment?.totalScore }}</td>
              <td>{{ row.answeredCount }} / {{ row.questionCount }}</td>
              <td><n-tag v-if="row.pendingReview > 0" type="warning" round>{{ row.pendingReview }} 题</n-tag><span v-else class="muted">无</span></td>
              <td>{{ row.submitTime || '—' }}</td>
              <td><n-button size="small" type="primary" secondary @click="openStudentResult(row)">复核</n-button></td>
            </tr>
          </tbody>
        </n-table>
        <n-empty v-if="!rvLoading && submissions.length === 0" description="暂无学生提交" />
      </template>
      <template v-else>
        <div class="rv-back">
          <n-button text type="primary" @click="currentResult = null">← 返回提交列表</n-button>
          <span class="muted">{{ currentResult.assignmentTitle }} · 总分 {{ currentResult.earnedScore }} / {{ currentResult.totalScore }}</span>
        </div>
        <div v-for="(item, i) in currentResult.items" :key="i" class="rv-item">
          <div class="rv-q">
            <span class="rv-seq">{{ i + 1 }}.</span>
            <n-tag v-if="item.type === 5" type="warning" round>{{ typeLabel(item.type) }}</n-tag>
            <n-tag v-else type="info" round>{{ typeLabel(item.type) }}</n-tag>
            <span class="rv-content">{{ item.content }}</span>
          </div>
          <div v-if="item.options && item.options.length" class="rv-opts">
            <span v-for="(o, idx) in item.options" :key="idx" class="rv-opt">{{ letter(idx) }}. {{ o }}</span>
          </div>
          <div class="rv-ans">
            <span class="muted">学生作答：</span><span class="answer-html" v-html="formatAnswer(item.yourAnswer)"></span>
            <n-tag v-if="item.correct === true" type="success" size="small" round>正确</n-tag>
            <n-tag v-else-if="item.correct === false" type="error" size="small" round>错误</n-tag>
            <n-tag v-else-if="item.gradeType === 2 && item.reviewStatus === 0" type="warning" size="small" round>AI 批改·待复核</n-tag>
            <n-tag v-else-if="item.gradeType === 2" type="info" size="small" round>教师已复核</n-tag>
          </div>
          <div class="rv-ans muted">参考答案：{{ item.standardAnswer }}</div>
          <div class="rv-feedback">{{ item.feedback }}</div>
          <div v-if="item.gradeType === 2" class="rv-edit">
            <span>本题得分：</span>
            <n-input-number v-model:value="item.score" :min="0" :max="currentAssignment?.totalScore" size="small" @update:value="markChanged(item)" />
            <n-input v-model:value="item.feedback" placeholder="复核反馈" style="width: 280px" @input="markChanged(item)" />
          </div>
        </div>
        <div class="rv-footer">
          <n-button type="primary" :loading="rvSaving" @click="saveReview">保存复核</n-button>
        </div>
      </template>
      <template #action>
        <n-space justify="end">
          <n-button @click="rvVisible = false">关闭</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NCheckbox, NCheckboxGroup, NDatePicker, NEmpty, NForm, NFormItem, NGrid, NGridItem, NInput, NInputNumber, NModal, NPagination, NRadio, NRadioGroup, NSpace, NSelect, NTable, NTag, createDiscreteApi } from 'naive-ui'
import { listCourses } from '../api/course'
import { listAssignments as fetchAssignments, saveAssignment, deleteAssignment, getAssignmentDetail, saveQuestion, deleteQuestion, getAnswerResult, getSubmissions, reviewAnswer, autoGenerateQuestions } from '../api/homework'
import DOMPurify from 'dompurify'

const { message, dialog } = createDiscreteApi(['message', 'dialog'])
const courses = ref([])
const selectedCourse = ref(null)
const assignments = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const pageSizeOptions = [10, 20, 50]
const pageCount = computed(() => Math.max(1, Math.ceil(assignments.value.length / pageSize.value)))
const pagedAssignments = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return assignments.value.slice(start, start + pageSize.value)
})
const questionTypes = [{ value: 1, label: '单选' }, { value: 2, label: '多选' }, { value: 3, label: '判断' }, { value: 4, label: '填空' }, { value: 5, label: '简答' }]
const questionTypeOptions = questionTypes
const typeLabel = (t) => questionTypes.find((x) => x.value === t)?.label || '未知'
const courseOptions = computed(() => courses.value.map((c) => ({ label: c.courseName, value: c.id })))
const activeCount = computed(() => assignments.value.filter(isAssignmentActive).length)
const endedCount = computed(() => assignments.value.filter((a) => !isAssignmentActive(a)).length)
const statCards = computed(() => [{ label: '作业总数', value: assignments.value.length }, { label: '进行中', value: activeCount.value }, { label: '已结束', value: endedCount.value }])

/** 根据作业状态和起止时间计算实时展示状态，避免过期数据继续显示为进行中。 */
function isAssignmentActive(assignment) {
  if (!assignment || assignment.status !== 1) return false
  const now = Date.now()
  const start = assignment.startTime ? new Date(String(assignment.startTime).replace(' ', 'T')).getTime() : NaN
  const end = assignment.endTime ? new Date(String(assignment.endTime).replace(' ', 'T')).getTime() : NaN
  return (!Number.isFinite(start) || now >= start) && (!Number.isFinite(end) || now <= end)
}
const asmVisible = ref(false)
const asmIsEdit = ref(false)
const asmSaving = ref(false)
const asmForm = reactive({ id: null, courseId: null, title: '', type: 1, description: '', totalScore: 100 })
const timeRange = ref(null)
const qmVisible = ref(false)
const currentAssignment = ref(null)
const questions = ref([])
const qVisible = ref(false)
const qIsEdit = ref(false)
const qSaving = ref(false)
const multiAnswer = ref([])
const aiGenVisible = ref(false)
const aiGenLoading = ref(false)
const aiGenStage = ref('正在读取课程知识库并生成题目，请稍候…')
const aiGenForm = ref({ type: 1, count: 5 })
const qForm = reactive({ id: null, assignmentId: null, type: 1, content: '', options: [], answer: '', analysis: '', score: 10, knowledgePoint: '', seq: null })
const rvVisible = ref(false)
const rvLoading = ref(false)
const submissions = ref([])
const currentResult = ref(null)
const rvSaving = ref(false)
const deletingAssignmentId = ref(null)
const deletingQuestionId = ref(null)
const changedSet = ref(new Set())

const totalQuestionScore = computed(() => questions.value.reduce((s, q) => s + (q.score || 0), 0))

function letter(i) { return String.fromCharCode(65 + i) }
// 老师批改页把附件链接统一改成下载，避免直接打开文档预览
function formatAnswer(html) {
  if (!html) return '（空）'
  const doc = new DOMParser().parseFromString(String(html), 'text/html')
  doc.querySelectorAll('a[href]').forEach((a) => {
    a.setAttribute('download', '')
    a.removeAttribute('target')
    a.setAttribute('rel', 'noopener noreferrer')
  })
  return DOMPurify.sanitize(doc.body.innerHTML)
}
function resetAsmForm() { Object.assign(asmForm, { id: null, courseId: null, title: '', type: 1, description: '', totalScore: 100 }); timeRange.value = null }
function openAssignmentCreate() { if (!courses.value.length) return message.warning('请先创建课程'); asmIsEdit.value = false; resetAsmForm(); asmVisible.value = true }
function openAssignmentEdit(row) { asmIsEdit.value = true; Object.assign(asmForm, { id: row.id, courseId: row.courseId, title: row.title, type: row.type, description: row.description, totalScore: row.totalScore }); timeRange.value = row.startTime && row.endTime ? [new Date(row.startTime).getTime(), new Date(row.endTime).getTime()] : null; asmVisible.value = true }
async function onSaveAssignment() {
  if (!asmForm.courseId) return message.warning('请选择课程')
  if (!asmForm.title) return message.warning('请填写标题')
  asmSaving.value = true
  try {
    const payload = {
      ...asmForm,
      startTime: formatDateTime(timeRange.value?.[0]),
      endTime: formatDateTime(timeRange.value?.[1])
    }
    await saveAssignment(payload)
    message.success('保存成功')
    asmVisible.value = false
    await loadAssignments({ resetPage: !asmIsEdit.value })
  } finally { asmSaving.value = false }
}

/** 将日期选择器的毫秒时间戳转换为后端约定的本地日期时间字符串。 */
function formatDateTime(timestamp) {
  if (!timestamp) return null
  const date = new Date(timestamp)
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

/** 统一格式化列表接口返回的日期，兼容 ISO 与数据库日期字符串。 */
function formatDisplayDate(value) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value).replace('T', ' ') : formatDateTime(date.getTime())
}
async function onDeleteAssignment(row) {
  const ok = await dialog.warning({ title: '提示', content: `确定删除作业「${row.title}」吗？`, positiveText: '确定', negativeText: '取消' })
  if (!ok) return
  if (deletingAssignmentId.value !== null) return
  deletingAssignmentId.value = row.id
  await deleteAssignment(row.id).finally(() => { deletingAssignmentId.value = null })
  message.success('已删除')
  await loadAssignments()
}
async function openQuestionManage(row) { currentAssignment.value = row; qmVisible.value = true; const detail = await getAssignmentDetail(row.id); questions.value = detail.questions || [] }
function resetQForm() { Object.assign(qForm, { id: null, assignmentId: currentAssignment.value?.id, type: 1, content: '', options: ['', ''], answer: '', analysis: '', score: 10, knowledgePoint: '', seq: null }); multiAnswer.value = [] }
function openQuestionCreate() { qIsEdit.value = false; resetQForm(); qVisible.value = true }
function openAiGenerate() { aiGenForm.value = { type: 1, count: 5 }; aiGenVisible.value = true }
async function onAiGenerate() {
  if (!currentAssignment.value?.courseId) return message.warning('无法获取课程信息')
  const confirmed = await dialog.warning({
    title: '确认生成题目',
    content: `本次将生成 ${aiGenForm.value.count} 道题目，并直接加入当前作业。是否继续？`,
    positiveText: '继续生成',
    negativeText: '取消'
  })
  if (!confirmed) return
  aiGenStage.value = '正在读取课程知识库并生成题目，请稍候…'
  aiGenLoading.value = true
  try {
    await autoGenerateQuestions(currentAssignment.value.id, currentAssignment.value.courseId, aiGenForm.value.count, aiGenForm.value.type)
    message.success('生成完成')
    aiGenVisible.value = false
    const detail = await getAssignmentDetail(currentAssignment.value.id)
    questions.value = detail.questions || []
  } catch (error) {
    message.error(error?.message || 'AI 出题失败，请检查模型配置后重试')
  } finally { aiGenLoading.value = false }
}
function openQuestionEdit(row) { qIsEdit.value = true; Object.assign(qForm, { id: row.id, assignmentId: row.assignmentId || currentAssignment.value?.id, type: row.type, content: row.content, options: row.options ? [...row.options] : (row.type === 1 || row.type === 2 ? ['', ''] : []), answer: row.answer || '', analysis: row.analysis || '', score: row.score, knowledgePoint: row.knowledgePoint || '', seq: row.seq }); multiAnswer.value = row.type === 2 && row.answer ? row.answer.split('') : []; qVisible.value = true }
function onQuestionTypeChange() { if (qForm.type === 1 || qForm.type === 2) { if (!qForm.options.length) qForm.options = ['', '']; qForm.answer = '' } else if (qForm.type === 3) { qForm.options = ['正确', '错误']; qForm.answer = '正确' } else { qForm.options = []; qForm.answer = '' }; multiAnswer.value = [] }
function addOption() { qForm.options.push('') }
function removeOption(i) { qForm.options.splice(i, 1) }
async function onSaveQuestion() {
  if (!qForm.content) return message.warning('请填写题干')
  if ((qForm.type === 1 || qForm.type === 2) && qForm.options.filter((o) => o && o.trim()).length < 2) return message.warning('请至少填写两个选项')
  if (qForm.type === 2) qForm.answer = (multiAnswer.value || []).join('')
  if (!qForm.answer) return message.warning(qForm.type === 2 ? '请至少选择一个正确答案' : '请填写标准答案')
  qSaving.value = true
  try {
    const payload = { ...qForm, assignmentId: qForm.assignmentId || currentAssignment.value?.id, options: qForm.options.filter((o) => o && o.trim()) }
    if (payload.type !== 1 && payload.type !== 2) payload.options = []
    await saveQuestion(payload)
    message.success('保存成功')
    qVisible.value = false
    const detail = await getAssignmentDetail(currentAssignment.value.id)
    questions.value = detail.questions || []
  } finally { qSaving.value = false }
}
async function onDeleteQuestion(row) {
  const ok = await dialog.warning({ title: '提示', content: '确定删除该题目吗？', positiveText: '确定', negativeText: '取消' })
  if (!ok) return
  if (deletingQuestionId.value !== null) return
  deletingQuestionId.value = row.id
  await deleteQuestion(row.id).finally(() => { deletingQuestionId.value = null })
  message.success('已删除')
  const detail = await getAssignmentDetail(currentAssignment.value.id)
  questions.value = detail.questions || []
}
function openReview(row) { currentAssignment.value = row; currentResult.value = null; rvVisible.value = true; loadSubmissions(row.id) }
async function loadSubmissions(id) { rvLoading.value = true; try { submissions.value = await getSubmissions(id) } finally { rvLoading.value = false } }
async function openStudentResult(row) { currentResult.value = JSON.parse(JSON.stringify(await getAnswerResult(currentAssignment.value.id, row.studentId))); changedSet.value = new Set() }
function markChanged(item) { changedSet.value.add(item.answerId) }
async function saveReview() {
  const items = currentResult.value.items.filter((it) => it.gradeType === 2)
  if (!items.length) return message.info('没有需要复核的主观题')
  rvSaving.value = true
  try {
    for (const it of items) {
      // 未改分/反馈视为认可 AI 初评；改动后记录为教师调整分数
      const reviewStatus = changedSet.value.has(it.answerId) ? 2 : 1
      await reviewAnswer({ answerId: it.answerId, score: it.score, feedback: it.feedback, reviewStatus })
    }
    message.success('复核完成')
    await loadSubmissions(currentAssignment.value.id)
    currentResult.value = null
  } finally { rvSaving.value = false }
}
/** 根据当前列表数据将页码限制在有效范围内，避免删除末页数据后出现空白页。 */
function normalizeCurrentPage() {
  currentPage.value = Math.min(Math.max(currentPage.value, 1), pageCount.value)
}

/** 切换课程筛选时回到第一页，并重新加载对应作业列表。 */
function onCourseFilterChange() {
  currentPage.value = 1
  loadAssignments()
}

/** 保留当前页刷新列表；若数据量变化则自动校正至有效页码。 */
function refreshAssignments() {
  loadAssignments()
}

/** 用户切换页码时同步保存当前页。 */
function onPageChange(page) {
  currentPage.value = page
}

/** 用户调整每页条数时返回第一页，确保浏览位置清晰可预期。 */
function onPageSizeChange(size) {
  pageSize.value = size
  currentPage.value = 1
}

/** 加载作业列表，并在筛选、保存、删除或刷新后维护有效页码。 */
async function loadAssignments({ resetPage = false } = {}) {
  if (resetPage) currentPage.value = 1
  loading.value = true
  try {
    assignments.value = await fetchAssignments(selectedCourse.value)
    normalizeCurrentPage()
  } finally {
    loading.value = false
  }
}
async function loadCourses() { courses.value = await listCourses() }
onMounted(() => { loadCourses(); loadAssignments() })
</script>

<style scoped>
.teacher-workbench { display: flex; flex-direction: column; gap: 16px; }
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
.pagination-wrap { display: flex; align-items: center; justify-content: flex-end; gap: 12px; flex-wrap: wrap; padding: 16px 6px 4px; }
.pagination-summary { color: #6b7280; font-size: 13px; white-space: nowrap; }
.muted { color: #6b7280; }
.qm-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.opt-list { width: 100%; display: flex; flex-direction: column; gap: 8px; }
.opt-row { display: flex; align-items: center; gap: 8px; }
.opt-key { width: 24px; height: 24px; border-radius: 50%; background: linear-gradient(135deg,#42B5BB,#87D8C9); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; flex-shrink: 0; }
.rv-back { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.rv-item { border: 1px solid #e4f4f2; border-radius: 14px; padding: 14px 16px; margin-bottom: 12px; background: #f7fbfb; }
.rv-q, .rv-ans { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.rv-q { margin-bottom: 6px; }
.rv-seq { font-weight: 700; color: #2f7f86; }
.rv-content { font-weight: 600; }
.rv-opts { display: flex; flex-wrap: wrap; gap: 8px 18px; margin: 6px 0; color: #5f6b73; font-size: 13.5px; }
.rv-feedback { margin: 8px 0 0; color: #1f2937; font-size: 13px; line-height: 1.6; }
.answer-html { display: inline-block; max-width: 100%; line-height: 1.6; }
.answer-html :deep(p) { margin: 0 0 4px; }
.answer-html :deep(ul) { margin: 4px 0; padding-left: 20px; }
.answer-html :deep(img) { max-width: min(100%, 520px); border-radius: 10px; display: block; margin: 8px 0; }
.answer-html :deep(a) { color: #2f7f86; font-weight: 700; }
.rv-edit { display: flex; align-items: center; margin-top: 10px; gap: 8px; }
.rv-footer { text-align: right; margin-top: 10px; }
.work-modal { width: min(900px, calc(100vw - 24px)); }
.mini-modal { width: min(420px, calc(100vw - 24px)); }
@media (max-width: 900px) { .hero { grid-template-columns: 1fr; } }
@media (max-width: 640px) {
  .pagination-wrap { justify-content: center; }
  .pagination-summary { width: 100%; text-align: center; }
  .pagination-wrap :deep(.n-pagination) { justify-content: center; }
}
</style>
