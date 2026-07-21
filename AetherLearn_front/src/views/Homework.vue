<template>
  <div class="hw-wrap">
    <!-- 顶部：标题 + 课程筛选 + 新建 -->
    <div class="head">
      <h2 class="page-title">作业管理</h2>
      <div class="head-right">
        <el-select v-model="selectedCourse" placeholder="全部课程" clearable style="width: 200px" @change="loadAssignments">
          <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
        </el-select>
        <el-button type="primary" @click="openAssignmentCreate">+ 新建作业</el-button>
      </div>
    </div>

    <!-- 教师总览卡（轻量数据概览，呼应教师看板基调） -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-num">{{ assignments.length }}</div>
        <div class="stat-label">作业总数</div>
      </div>
      <div class="stat-card">
        <div class="stat-num grad">{{ activeCount }}</div>
        <div class="stat-label">进行中</div>
      </div>
      <div class="stat-card">
        <div class="stat-num">{{ endedCount }}</div>
        <div class="stat-label">已结束</div>
      </div>
    </div>

    <!-- 作业列表 -->
    <div class="aeth-card">
      <el-table :data="assignments" v-loading="loading" stripe>
        <el-table-column prop="title" label="作业标题" min-width="160" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.type === 2 ? 'warning' : 'primary'" effect="plain">
              {{ row.type === 2 ? '测验' : '作业' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalScore" label="总分" width="80" />
        <el-table-column label="截止时间" min-width="150">
          <template #default="{ row }">{{ row.endTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '进行中' : '已结束' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openQuestionManage(row)">题目管理</el-button>
            <el-button size="small" type="success" @click="openReview(row)">批改</el-button>
            <el-button size="small" @click="openAssignmentEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="onDeleteAssignment(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && assignments.length === 0" description="还没有作业，点击右上角新建第一份">
        <el-button type="primary" @click="openAssignmentCreate">+ 新建作业</el-button>
      </el-empty>
    </div>

    <!-- 新建/编辑作业对话框 -->
    <el-dialog v-model="asmVisible" :title="asmIsEdit ? '编辑作业' : '新建作业'" width="560px">
      <el-form :model="asmForm" label-width="84px">
        <el-form-item label="所属课程" required>
          <el-select v-model="asmForm.courseId" placeholder="选择课程" style="width: 100%">
            <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="asmForm.title" placeholder="如：Java 第一次作业" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="asmForm.type">
            <el-radio :value="1">作业</el-radio>
            <el-radio :value="2">测验</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="起止时间">
          <el-date-picker v-model="timeRange" type="datetimerange" range-separator="至"
            start-placeholder="开始" end-placeholder="截止" value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%" />
        </el-form-item>
        <el-form-item label="总分">
          <el-input-number v-model="asmForm.totalScore" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="asmForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="asmVisible = false">取消</el-button>
        <el-button type="primary" :loading="asmSaving" @click="onSaveAssignment">保存</el-button>
      </template>
    </el-dialog>

    <!-- 题目管理对话框 -->
    <el-dialog v-model="qmVisible" :title="`题目管理 · ${currentAssignment?.title || ''}`" width="820px" top="5vh">
      <div class="qm-head">
        <span class="muted">共 {{ questions.length }} 题 · 总分 {{ totalQuestionScore }}</span>
        <el-button type="primary" size="small" @click="openQuestionCreate">+ 添加题目</el-button>
      </div>
      <el-table :data="questions" stripe>
        <el-table-column label="题型" width="80">
          <template #default="{ row }">
            <span v-if="row.type === 5" class="tag-ai">{{ typeLabel(row.type) }}</span>
            <el-tag v-else effect="plain">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="题干" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content }}</template>
        </el-table-column>
        <el-table-column prop="score" label="分值" width="70" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="openQuestionEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="onDeleteQuestion(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="questions.length === 0" description="暂无题目" />
    </el-dialog>

    <!-- 添加/编辑题目对话框 -->
    <el-dialog v-model="qVisible" :title="qIsEdit ? '编辑题目' : '添加题目'" width="600px" append-to-body>
      <el-form :model="qForm" label-width="84px">
        <el-form-item label="题型">
          <el-select v-model="qForm.type" style="width: 100%" @change="onQuestionTypeChange">
            <el-option v-for="t in questionTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="题干" required>
          <el-input v-model="qForm.content" type="textarea" :rows="2" />
        </el-form-item>

        <!-- 单选/多选：选项编辑 -->
        <el-form-item v-if="qForm.type === 1 || qForm.type === 2" label="选项">
          <div class="opt-list">
            <div v-for="(opt, i) in qForm.options" :key="i" class="opt-row">
              <span class="opt-key">{{ letter(i) }}</span>
              <el-input v-model="qForm.options[i]" placeholder="选项内容" />
              <el-button text type="danger" @click="removeOption(i)">×</el-button>
            </div>
            <el-button text type="primary" @click="addOption">+ 添加选项</el-button>
          </div>
        </el-form-item>

        <!-- 标准答案：随题型变化 -->
        <el-form-item label="标准答案" required>
          <el-select v-if="qForm.type === 1" v-model="qForm.answer" placeholder="选择正确选项" style="width: 100%">
            <el-option v-for="(opt, i) in qForm.options" :key="i" :label="letter(i) + ' ' + opt" :value="letter(i)" />
          </el-select>
          <el-checkbox-group v-else-if="qForm.type === 2" v-model="multiAnswer">
            <el-checkbox v-for="(opt, i) in qForm.options" :key="i" :value="letter(i)">{{ letter(i) }} {{ opt }}</el-checkbox>
          </el-checkbox-group>
          <el-select v-else-if="qForm.type === 3" v-model="qForm.answer" placeholder="选择" style="width: 100%">
            <el-option label="正确" value="正确" />
            <el-option label="错误" value="错误" />
          </el-select>
          <el-input v-else-if="qForm.type === 4" v-model="qForm.answer" placeholder="填空答案（关键词）" />
          <el-input v-else v-model="qForm.answer" type="textarea" :rows="2" placeholder="标准答案/要点" />
        </el-form-item>

        <el-form-item label="分值">
          <el-input-number v-model="qForm.score" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="知识点">
          <el-input v-model="qForm.knowledgePoint" placeholder="如：面向对象" />
        </el-form-item>
        <el-form-item label="解析">
          <el-input v-model="qForm.analysis" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qVisible = false">取消</el-button>
        <el-button type="primary" :loading="qSaving" @click="onSaveQuestion">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批改对话框：提交情况 → 指定学生复核 -->
    <el-dialog v-model="rvVisible" :title="`批改 · ${currentAssignment?.title || ''}`" width="900px" top="5vh">
      <template v-if="!currentResult">
        <el-table :data="submissions" v-loading="rvLoading" stripe>
          <el-table-column prop="studentName" label="学生" width="120" />
          <el-table-column label="得分" width="90">
            <template #default="{ row }">{{ row.earnedScore }} / {{ currentAssignment?.totalScore }}</template>
          </el-table-column>
          <el-table-column label="作答" width="110">
            <template #default="{ row }">{{ row.answeredCount }} / {{ row.questionCount }}</template>
          </el-table-column>
          <el-table-column label="待复核" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.pendingReview > 0" type="warning">{{ row.pendingReview }} 题</el-tag>
              <span v-else class="muted">无</span>
            </template>
          </el-table-column>
          <el-table-column label="提交时间" min-width="150">
            <template #default="{ row }">{{ row.submitTime || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="openStudentResult(row)">复核</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!rvLoading && submissions.length === 0" description="暂无学生提交" />
      </template>

      <!-- 单名学生作答复核 -->
      <div v-else>
        <div class="rv-back">
          <el-button text type="primary" @click="currentResult = null">← 返回提交列表</el-button>
          <span class="muted">{{ currentResult.assignmentTitle }} · 总分 {{ currentResult.earnedScore }} / {{ currentResult.totalScore }}</span>
        </div>
        <div v-for="(item, i) in currentResult.items" :key="i" class="rv-item">
          <div class="rv-q">
            <span class="rv-seq">{{ i + 1 }}.</span>
            <span v-if="item.type === 5" class="tag-ai">{{ typeLabel(item.type) }}</span>
            <el-tag v-else size="small" effect="plain">{{ typeLabel(item.type) }}</el-tag>
            <span class="rv-content">{{ item.content }}</span>
          </div>
          <div v-if="item.options && item.options.length" class="rv-opts">
            <span v-for="(o, idx) in item.options" :key="idx" class="rv-opt">{{ letter(idx) }}. {{ o }}</span>
          </div>
          <div class="rv-ans">
            <span class="muted">学生作答：</span>
            <b>{{ item.yourAnswer || '（空）' }}</b>
            <el-tag v-if="item.correct === true" type="success" size="small" effect="plain">正确</el-tag>
            <el-tag v-else-if="item.correct === false" type="danger" size="small" effect="plain">错误</el-tag>
            <el-tag v-else-if="item.gradeType === 2 && item.reviewStatus === 0" type="warning" size="small" effect="plain">AI 批改·待复核</el-tag>
            <el-tag v-else-if="item.gradeType === 2" type="info" size="small" effect="plain">教师已复核</el-tag>
          </div>
          <div class="rv-ans muted">参考答案：{{ item.standardAnswer }}</div>
          <div class="rv-feedback">{{ item.feedback }}</div>
          <!-- 主观题可复核调分 -->
          <div v-if="item.gradeType === 2" class="rv-edit">
            <span>本题得分：</span>
            <el-input-number v-model="item.score" :min="0" :max="currentAssignment?.totalScore" size="small" />
            <el-input v-model="item.feedback" placeholder="复核反馈" size="small" style="width: 280px; margin-left: 10px"
              @input="markChanged(item)" />
          </div>
        </div>
        <div class="rv-footer">
          <el-button type="primary" :loading="rvSaving" @click="saveReview">保存复核</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCourses } from '../api/course'
import {
  listAssignments as fetchAssignments, saveAssignment, deleteAssignment,
  getAssignmentDetail, saveQuestion, deleteQuestion,
  getAnswerResult, getSubmissions, reviewAnswer
} from '../api/homework'

const courses = ref([])
const selectedCourse = ref(null)
const assignments = ref([])
const loading = ref(false)

const questionTypes = [
  { value: 1, label: '单选' }, { value: 2, label: '多选' },
  { value: 3, label: '判断' }, { value: 4, label: '填空' }, { value: 5, label: '简答' }
]
const typeLabel = (t) => questionTypes.find((x) => x.value === t)?.label || '未知'
const activeCount = computed(() => assignments.value.filter((a) => a.status === 1).length)
const endedCount = computed(() => assignments.value.filter((a) => a.status !== 1).length)

// ============ 作业 CRUD ============
const asmVisible = ref(false)
const asmIsEdit = ref(false)
const asmSaving = ref(false)
const asmForm = reactive({ id: null, courseId: null, title: '', type: 1, description: '', totalScore: 100 })
const timeRange = ref([])

function resetAsmForm() {
  Object.assign(asmForm, { id: null, courseId: null, title: '', type: 1, description: '', totalScore: 100 })
  timeRange.value = []
}
function openAssignmentCreate() {
  if (!courses.value.length) { ElMessage.warning('请先创建课程'); return }
  asmIsEdit.value = false
  resetAsmForm()
  asmVisible.value = true
}
function openAssignmentEdit(row) {
  asmIsEdit.value = true
  Object.assign(asmForm, {
    id: row.id, courseId: row.courseId, title: row.title, type: row.type,
    description: row.description, totalScore: row.totalScore
  })
  timeRange.value = row.startTime && row.endTime ? [row.startTime, row.endTime] : []
  asmVisible.value = true
}
async function onSaveAssignment() {
  if (!asmForm.courseId) { ElMessage.warning('请选择课程'); return }
  if (!asmForm.title) { ElMessage.warning('请填写标题'); return }
  asmSaving.value = true
  try {
    const payload = {
      ...asmForm,
      startTime: timeRange.value?.[0] || null,
      endTime: timeRange.value?.[1] || null
    }
    await saveAssignment(payload)
    ElMessage.success('保存成功')
    asmVisible.value = false
    await loadAssignments()
  } finally {
    asmSaving.value = false
  }
}
async function onDeleteAssignment(row) {
  await ElMessageBox.confirm(`确定删除作业「${row.title}」吗？`, '提示', { type: 'warning' })
  await deleteAssignment(row.id)
  ElMessage.success('已删除')
  await loadAssignments()
}

// ============ 题目管理 ============
const qmVisible = ref(false)
const currentAssignment = ref(null)
const questions = ref([])
const qVisible = ref(false)
const qIsEdit = ref(false)
const qSaving = ref(false)
const multiAnswer = ref([])
const qForm = reactive({
  id: null, assignmentId: null, type: 1, content: '', options: [], answer: '',
  analysis: '', score: 10, knowledgePoint: '', seq: null
})

const totalQuestionScore = computed(() => questions.value.reduce((s, q) => s + (q.score || 0), 0))

function openQuestionManage(row) {
  currentAssignment.value = row
  qmVisible.value = true
  loadQuestions(row.id)
}
async function loadQuestions(id) {
  const detail = await getAssignmentDetail(id)
  questions.value = detail.questions || []
}
function letter(i) { return String.fromCharCode(65 + i) }

function resetQForm() {
  Object.assign(qForm, {
    id: null, assignmentId: currentAssignment.value?.id, type: 1, content: '',
    options: ['', ''], answer: '', analysis: '', score: 10, knowledgePoint: '', seq: null
  })
  multiAnswer.value = []
}
function openQuestionCreate() {
  qIsEdit.value = false
  resetQForm()
  qVisible.value = true
}
function openQuestionEdit(row) {
  qIsEdit.value = true
  Object.assign(qForm, {
    id: row.id, assignmentId: row.assignmentId, type: row.type, content: row.content,
    options: row.options ? [...row.options] : (row.type === 1 || row.type === 2 ? ['', ''] : []),
    answer: row.answer || '', analysis: row.analysis || '', score: row.score,
    knowledgePoint: row.knowledgePoint || '', seq: row.seq
  })
  multiAnswer.value = row.type === 2 && row.answer ? row.answer.split('') : []
  qVisible.value = true
}
function onQuestionTypeChange() {
  // 切换题型时重置选项/答案，避免脏数据
  if (qForm.type === 1 || qForm.type === 2) {
    if (!qForm.options.length) qForm.options = ['', '']
  } else if (qForm.type === 3) {
    qForm.options = ['正确', '错误']
    qForm.answer = qForm.answer || '正确'
  } else {
    qForm.options = []
  }
  multiAnswer.value = []
}
function addOption() { qForm.options.push('') }
function removeOption(i) { qForm.options.splice(i, 1) }

async function onSaveQuestion() {
  if (!qForm.content) { ElMessage.warning('请填写题干'); return }
  if ((qForm.type === 1 || qForm.type === 2) && qForm.options.filter((o) => o && o.trim()).length < 2) {
    ElMessage.warning('请至少填写两个选项'); return
  }
  if (!qForm.answer) { ElMessage.warning('请填写标准答案'); return }
  qSaving.value = true
  try {
    const payload = { ...qForm }
    // 多选答案：将选中字母拼为字符串（如 ABC）
    if (payload.type === 2) payload.answer = (multiAnswer.value || []).join('')
    // 过滤空选项
    payload.options = payload.options.filter((o) => o && o.trim())
    if (payload.type !== 1 && payload.type !== 2) payload.options = []
    await saveQuestion(payload)
    ElMessage.success('保存成功')
    qVisible.value = false
    await loadQuestions(currentAssignment.value.id)
  } finally {
    qSaving.value = false
  }
}
async function onDeleteQuestion(row) {
  await ElMessageBox.confirm('确定删除该题目吗？', '提示', { type: 'warning' })
  await deleteQuestion(row.id)
  ElMessage.success('已删除')
  await loadQuestions(currentAssignment.value.id)
}

// ============ 批改 / 复核 ============
const rvVisible = ref(false)
const rvLoading = ref(false)
const submissions = ref([])
const currentResult = ref(null)
const rvSaving = ref(false)
const changedSet = ref(new Set())

function openReview(row) {
  currentAssignment.value = row
  currentResult.value = null
  rvVisible.value = true
  loadSubmissions(row.id)
}
async function loadSubmissions(id) {
  rvLoading.value = true
  try {
    submissions.value = await getSubmissions(id)
  } finally {
    rvLoading.value = false
  }
}
async function openStudentResult(row) {
  const res = await getAnswerResult(currentAssignment.value.id, row.studentId)
  // 复制一份用于编辑（含 answerId）
  currentResult.value = JSON.parse(JSON.stringify(res))
  currentResult.value.items.forEach((it) => { it._origScore = it.score; it._origFeedback = it.feedback })
  changedSet.value = new Set()
}
function markChanged(item) { changedSet.value.add(item.answerId) }

async function saveReview() {
  const items = currentResult.value.items.filter((it) => it.gradeType === 2)
  if (!items.length) { ElMessage.info('没有需要复核的主观题'); return }
  rvSaving.value = true
  try {
    for (const it of items) {
      await reviewAnswer({
        answerId: it.answerId,
        score: it.score,
        feedback: it.feedback,
        reviewStatus: 2
      })
    }
    ElMessage.success('复核完成')
    await loadSubmissions(currentAssignment.value.id)
    currentResult.value = null
  } finally {
    rvSaving.value = false
  }
}

// ============ 初始化 ============
async function loadAssignments() {
  loading.value = true
  try {
    assignments.value = await fetchAssignments(selectedCourse.value)
  } finally {
    loading.value = false
  }
}
async function loadCourses() {
  try { courses.value = await listCourses() } catch (e) { courses.value = [] }
}
onMounted(() => { loadCourses(); loadAssignments() })
</script>

<style scoped>
.hw-wrap { display: flex; flex-direction: column; }
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.page-title { margin: 0; }
.head-right { display: flex; align-items: center; gap: 10px; }
.muted { color: var(--text-2); }
/* 主观题（AI 批改）渐变徽标：本模块的签名元素 */
.tag-ai {
  display: inline-block; padding: 1px 9px; border-radius: 8px; font-size: 12px; line-height: 18px;
  color: #fff; background: linear-gradient(135deg, var(--brand-1), var(--brand-2));
  box-shadow: 0 2px 8px rgba(124, 77, 255, 0.25);
}
/* 教师总览卡 */
.stat-row { display: flex; gap: 14px; margin-bottom: 14px; }
.stat-card {
  flex: 1; background: var(--card-bg); border-radius: var(--radius); box-shadow: var(--shadow);
  padding: 16px 20px; display: flex; flex-direction: column; gap: 4px;
}
.stat-num { font-size: 26px; font-weight: 800; color: var(--text-1); line-height: 1.1; }
.stat-num.grad {
  background: linear-gradient(135deg, var(--brand-1), var(--brand-2));
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.stat-label { font-size: 13px; color: var(--text-2); }
.qm-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.opt-list { width: 100%; }
.opt-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.opt-key { width: 22px; height: 22px; border-radius: 50%; background: linear-gradient(135deg, var(--brand-1), var(--brand-2));
  color: #fff; display: flex; align-items: center; justify-content: center; font-size: 12px; flex-shrink: 0; }
.rv-back { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.rv-item { border: 1px solid #eef0f7; border-radius: 12px; padding: 12px 14px; margin-bottom: 12px; background: #fafbff; }
.rv-q { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.rv-seq { font-weight: 700; color: var(--brand-1); }
.rv-content { font-weight: 500; }
.rv-opts { display: flex; flex-wrap: wrap; gap: 8px 18px; margin: 6px 0; color: var(--text-2); }
.rv-ans { margin: 4px 0; }
.rv-feedback { margin: 6px 0; color: var(--text-1); font-size: 13px; }
.rv-edit { display: flex; align-items: center; margin-top: 8px; }
.rv-footer { text-align: right; margin-top: 8px; }
</style>
