<template>
  <div class="hw-wrap">
    <!-- 顶部：标题 + 课程筛选 -->
    <div class="head">
      <h2 class="page-title">我的作业</h2>
      <el-select v-model="selectedCourse" placeholder="全部课程" clearable style="width: 200px" @change="loadAssignments">
        <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
      </el-select>
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
        <el-table-column label="我的成绩" width="120">
          <template #default="{ row }">
            <span v-if="resultMap[row.id]?.submitted" class="score">
              {{ resultMap[row.id]?.earnedScore }} / {{ row.totalScore }}
            </span>
            <el-tag v-else type="info" size="small" effect="plain">未提交</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!resultMap[row.id]?.submitted" size="small" type="primary" @click="openDo(row)">去做作业</el-button>
            <el-button v-else size="small" @click="openResult(row)">查看结果</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && assignments.length === 0" description="暂无作业" />
    </div>

    <!-- 作答 / 结果对话框 -->
    <el-dialog v-model="doVisible" :title="currentAssignment?.title || '作业'" width="760px" top="5vh">
      <!-- 作答阶段 -->
      <div v-if="phase === 'answer'">
        <el-alert v-if="!detail" type="info" :closable="false" show-icon title="正在加载题目…" />
        <div v-for="(q, i) in detail?.questions || []" :key="q.id" class="q-block">
          <div class="q-title">
            <span class="q-seq">{{ i + 1 }}.</span>
            <span v-if="q.type === 5" class="tag-ai">{{ typeLabel(q.type) }}</span>
            <el-tag v-else size="small" effect="plain">{{ typeLabel(q.type) }}</el-tag>
            <span class="q-score">{{ q.score }} 分</span>
            <span class="q-content">{{ q.content }}</span>
          </div>
          <div v-if="q.options && q.options.length" class="q-opts">
            <el-radio-group v-if="q.type === 1 || q.type === 3" v-model="answers[q.id]">
              <el-radio v-for="(o, idx) in q.options" :key="idx" :value="q.type === 3 ? o : letter(idx)">
                {{ q.type === 3 ? o : letter(idx) + '. ' + o }}
              </el-radio>
            </el-radio-group>
            <el-checkbox-group v-else-if="q.type === 2" v-model="answers[q.id]">
              <el-checkbox v-for="(o, idx) in q.options" :key="idx" :value="letter(idx)">{{ letter(idx) + '. ' + o }}</el-checkbox>
            </el-checkbox-group>
          </div>
          <el-input v-else-if="q.type === 4" v-model="answers[q.id]" placeholder="请输入答案" />
          <el-input v-else-if="q.type === 5" v-model="answers[q.id]" type="textarea" :rows="3" placeholder="请输入作答内容" />
        </div>
      </div>

      <!-- 结果阶段 -->
      <div v-else-if="phase === 'result'" class="result-box">
        <div class="result-summary">
          <span class="rs-score">{{ currentResult?.earnedScore }} <small>/ {{ currentResult?.totalScore }}</small></span>
          <span class="muted">总分</span>
        </div>
        <div v-for="(item, i) in currentResult?.items || []" :key="i" class="r-block">
          <div class="r-title">
            <span class="r-seq">{{ i + 1 }}.</span>
            <span v-if="item.type === 5" class="tag-ai">{{ typeLabel(item.type) }}</span>
            <el-tag v-else size="small" effect="plain">{{ typeLabel(item.type) }}</el-tag>
            <span class="r-content">{{ item.content }}</span>
          </div>
          <div class="r-line">
            <span class="muted">你的作答：</span><b>{{ item.yourAnswer || '（空）' }}</b>
            <el-tag v-if="item.correct === true" type="success" size="small" effect="plain">正确</el-tag>
            <el-tag v-else-if="item.correct === false" type="danger" size="small" effect="plain">错误</el-tag>
            <el-tag v-else-if="item.gradeType === 2 && item.reviewStatus === 0" type="warning" size="small" effect="plain">AI 批改·待复核</el-tag>
            <el-tag v-else-if="item.gradeType === 2" type="info" size="small" effect="plain">教师已复核</el-tag>
            <span class="r-score">本题 {{ item.score }} 分</span>
          </div>
          <div class="r-line muted">参考答案：{{ item.standardAnswer }}</div>
          <div v-if="item.analysis" class="r-line muted">解析：{{ item.analysis }}</div>
          <div class="r-feedback">{{ item.feedback }}</div>
        </div>
      </div>

      <!-- 提交中：模拟 AI 批改加载 -->
      <div v-if="submitting" class="submitting">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>{{ submitTip }}</span>
      </div>

      <template #footer>
        <template v-if="phase === 'answer'">
          <el-button @click="doVisible = false">关闭</el-button>
          <el-button type="primary" :loading="submitting" @click="onSubmit">提交作答</el-button>
        </template>
        <template v-else>
          <el-button @click="doVisible = false">关闭</el-button>
          <el-button type="primary" @click="phase = 'answer'">重新作答</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { listCourses } from '../api/course'
import { listAssignments as fetchAssignments, getAssignmentDetail, submitAnswers, getAnswerResult } from '../api/homework'

const courses = ref([])
const selectedCourse = ref(null)
const assignments = ref([])
const loading = ref(false)
const resultMap = reactive({}) // assignmentId -> { submitted, earnedScore }

const questionTypes = [
  { value: 1, label: '单选' }, { value: 2, label: '多选' },
  { value: 3, label: '判断' }, { value: 4, label: '填空' }, { value: 5, label: '简答' }
]
const typeLabel = (t) => questionTypes.find((x) => x.value === t)?.label || '未知'
const letter = (i) => String.fromCharCode(65 + i)

// 作答对话框状态
const doVisible = ref(false)
const phase = ref('answer') // answer | result
const currentAssignment = ref(null)
const detail = ref(null)
const answers = reactive({})
const currentResult = ref(null)
const submitting = ref(false)
const submitTip = ref('')

function resetAnswers() {
  Object.keys(answers).forEach((k) => delete answers[k])
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
  // 初始化各题答案容器（多选为数组）
  d.questions.forEach((q) => { if (q.type === 2) answers[q.id] = [] })
}

async function openResult(row) {
  currentAssignment.value = row
  phase.value = 'result'
  detail.value = null
  doVisible.value = true
  const res = await getAnswerResult(row.id)
  currentResult.value = res
}

async function onSubmit() {
  const qs = detail.value?.questions || []
  // 校验是否已提交
  const payload = {
    assignmentId: currentAssignment.value.id,
    answers: qs.map((q) => ({
      questionId: q.id,
      content: q.type === 2 ? (answers[q.id] || []).join('') : (answers[q.id] || '')
    }))
  }
  const hasEssay = qs.some((q) => q.type === 5)
  submitting.value = true
  try {
    if (hasEssay) {
      // 主观题：前端模拟 AI 批改加载，体现"AI 批改"流程
      submitTip.value = 'AI 正在批改主观题…'
      await new Promise((r) => setTimeout(r, 1500))
    }
    const res = await submitAnswers(payload)
    currentResult.value = res
    resultMap[currentAssignment.value.id] = { submitted: true, earnedScore: res.earnedScore }
    phase.value = 'result'
    ElMessage.success('提交成功，已自动批改')
  } catch (e) {
    // 忽略，错误提示由请求拦截器处理
  } finally {
    submitting.value = false
  }
}

// ============ 初始化 ============
async function loadAssignments() {
  loading.value = true
  try {
    assignments.value = await fetchAssignments(selectedCourse.value)
    // 并行查询各作业本人成绩，用于列表展示
    await Promise.all(assignments.value.map(async (a) => {
      try {
        const r = await getAnswerResult(a.id)
        resultMap[a.id] = { submitted: !!r.submitted, earnedScore: r.earnedScore }
      } catch (e) { resultMap[a.id] = { submitted: false, earnedScore: 0 } }
    }))
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
.muted { color: var(--text-2); }
/* 主观题（AI 批改）渐变徽标：本模块的签名元素 */
.tag-ai {
  display: inline-block; padding: 1px 9px; border-radius: 8px; font-size: 12px; line-height: 18px;
  color: #fff; background: linear-gradient(135deg, var(--brand-1), var(--brand-2));
  box-shadow: 0 2px 8px rgba(124, 77, 255, 0.25);
}
.score { font-weight: 700; color: var(--brand-1); }
.q-block { border: 1px solid #eef0f7; border-radius: 12px; padding: 14px; margin-bottom: 14px; background: #fafbff; }
.q-title { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
.q-seq { font-weight: 700; color: var(--brand-1); }
.q-score { color: var(--text-2); font-size: 13px; }
.q-content { font-weight: 500; }
.q-opts { display: flex; flex-direction: column; gap: 8px; padding-left: 18px; }
.r-block { border: 1px solid #eef0f7; border-radius: 12px; padding: 12px 14px; margin-bottom: 12px; background: #fafbff; }
.r-title { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; flex-wrap: wrap; }
.r-seq { font-weight: 700; color: var(--brand-1); }
.r-content { font-weight: 500; }
.r-line { margin: 4px 0; }
.r-score { margin-left: auto; color: var(--brand-1); font-weight: 600; }
.r-feedback { margin-top: 6px; color: var(--text-1); font-size: 13px; background: #fff; border: 1px dashed #e3e7f5; border-radius: 8px; padding: 8px 10px; }
.result-summary { display: flex; align-items: baseline; gap: 10px; margin-bottom: 14px; }
.rs-score { font-size: 28px; font-weight: 800; background: linear-gradient(135deg, var(--brand-1), var(--brand-2)); -webkit-background-clip: text; background-clip: text; color: transparent; }
.rs-score small { font-size: 14px; color: var(--text-2); }
.submitting { display: flex; align-items: center; gap: 10px; justify-content: center; padding: 20px; color: var(--brand-1); }
.is-loading { animation: rotating 1.4s linear infinite; }
@media (prefers-reduced-motion: reduce) { .is-loading { animation: none; } }
@keyframes rotating { from { transform: rotate(0); } to { transform: rotate(360deg); } }
</style>
