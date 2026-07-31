<template>
  <div class="course-admin">
    <div class="page-head">
      <div>
        <h2 class="page-title">课程管理</h2>
        <p class="page-subtitle">教师创建课程、生成邀请码、维护章节资源，再发布给学生在线学习。</p>
      </div>
      <n-button type="primary" size="large" @click="openCreate">新建课程</n-button>
    </div>

    <n-card class="workflow-card" :bordered="false">
      <n-steps :current="1" size="small">
        <n-step title="创建课程" description="填写课程基础信息" />
        <n-step title="编辑章节" description="上传视频、PDF、Word 或编写文字" />
        <n-step title="发布学习" description="生成邀请码并管理学生" />
        <n-step title="后续教学" description="继续添加作业与知识库" />
      </n-steps>
    </n-card>

    <n-card class="table-card" :bordered="false">
      <n-spin :show="loading">
        <div class="course-table-toolbar">
          <span>课程排序</span>
          <n-space :size="8">
            <n-select
              v-model:value="courseSortField"
              :options="courseSortFieldOptions"
              style="width: 132px"
              @update:value="onCourseSortChange"
            />
            <n-select
              v-model:value="courseSortOrder"
              :options="courseSortOrderOptions"
              style="width: 104px"
              @update:value="onCourseSortChange"
            />
          </n-space>
        </div>
        <n-table :bordered="false" :single-line="false">
          <thead>
            <tr>
              <th>课程</th>
              <th>邀请码</th>
              <th>状态</th>
              <th>说明</th>
              <th class="ops-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in pagedCourses" :key="row.id">
              <td>
                <div class="course-cell">
                  <div class="course-cover" :style="coverStyle(row)"></div>
                  <div>
                    <div class="course-name">{{ row.courseName }}</div>
                    <div class="course-code">{{ row.courseCode || '未设置编号' }}</div>
                  </div>
                </div>
              </td>
              <td>
                <n-tag v-if="row.inviteCode" type="success" round>{{ row.inviteCode }}</n-tag>
                <span v-else class="muted">未生成</span>
              </td>
              <td>
                <n-tag :type="row.status === 1 ? 'success' : 'default'" round>
                  {{ row.status === 1 ? '开课中' : '已下架' }}
                </n-tag>
              </td>
              <td class="desc-cell">{{ row.description || '暂无课程简介' }}</td>
              <td>
                <n-space :size="6" wrap>
                  <n-button size="small" secondary type="primary" @click="openChapters(row)">章节</n-button>
                  <n-button size="small" secondary @click="openStudents(row)">学生</n-button>
                  <n-button size="small" secondary @click="onInvite(row)">邀请码</n-button>
                  <n-button size="small" secondary @click="openEdit(row)">编辑</n-button>
                  <n-button size="small" secondary type="error" @click="onDelete(row)">删除</n-button>
                </n-space>
              </td>
            </tr>
          </tbody>
        </n-table>
        <n-empty v-if="!loading && courses.length === 0" description="暂无课程，先创建一门课程" />
        <div v-if="!loading && courses.length > coursePageSize" class="course-pagination">
          <span class="course-pagination-total">共 {{ courses.length }} 门课程</span>
          <n-pagination
            v-model:page="courseCurrentPage"
            :page-size="coursePageSize"
            :item-count="courses.length"
            :page-sizes="[5, 10, 20]"
            show-size-picker
            show-quick-jumper
            @update:page-size="onCoursePageSizeChange"
          />
        </div>
      </n-spin>
    </n-card>

    <n-modal v-model:show="courseModalVisible" preset="card" :title="isEdit ? '编辑课程' : '新建课程'" class="course-modal">
      <n-form label-placement="left" label-width="82" :model="form">
        <n-form-item label="课程名称" required>
          <n-input v-model:value="form.courseName" placeholder="如：Java 程序设计" />
        </n-form-item>
        <n-form-item label="课程编号">
          <n-input v-model:value="form.courseCode" placeholder="如：CS201" />
        </n-form-item>
        <n-form-item label="简介">
          <n-input v-model:value="form.description" type="textarea" :autosize="{ minRows: 3, maxRows: 5 }" />
        </n-form-item>
        <n-form-item label="封面">
          <UploadFile v-model="form.cover" biz-type="course" accept="image/*" />
        </n-form-item>
        <n-form-item label="状态">
          <n-switch v-model:value="statusOn">
            <template #checked>开课</template>
            <template #unchecked>下架</template>
          </n-switch>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="courseModalVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="onSave">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-drawer v-model:show="studentDrawerVisible" :width="460" placement="right">
      <n-drawer-content :title="`${drawerCourseName} · 学生名单`">
        <n-spin :show="studentsLoading">
          <div class="drawer-note">学生通过邀请码加入后会出现在这里，教师可以按课程移除学生。</div>
          <div class="student-search">
            <n-input
              v-model:value="studentKeyword"
              clearable
              placeholder="输入学号或姓名搜索"
            />
            <span v-if="studentKeyword.trim()">找到 {{ filteredStudents.length }} 名学生</span>
          </div>
          <n-table :bordered="false" :single-line="false">
            <thead>
              <tr>
                <th>姓名</th>
                <th>学号</th>
                <th>联系方式</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="student in filteredStudents" :key="student.id">
                <td>{{ student.realName || '-' }}</td>
                <td>{{ student.username }}</td>
                <td>{{ student.phone || student.email || '-' }}</td>
                <td><n-button size="small" text type="error" @click="onRemoveStudent(student)">移除</n-button></td>
              </tr>
            </tbody>
          </n-table>
          <n-empty
            v-if="!studentsLoading && filteredStudents.length === 0"
            :description="students.length === 0 ? '暂无学生加入' : '没有匹配的学生'"
          />
        </n-spin>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="chapterDrawerVisible" :width="660" placement="right">
      <n-drawer-content :title="`${drawerCourseName} · 章节编辑`">
        <div class="chapter-toolbar">
          <div>
            <div class="drawer-note">每个章节可以是文字、视频、PDF、Word 或其他资料，学生点击章节进入学习。</div>
          </div>
          <n-button type="primary" @click="openChapterCreate">新增章节</n-button>
        </div>
        <n-spin :show="chaptersLoading">
          <div class="chapter-list">
            <n-card v-for="chapter in chapters" :key="chapter.id" class="chapter-card" :bordered="false">
              <div class="chapter-card-head">
                <div>
                  <n-tag size="small" type="info" round>第 {{ chapter.sortNo || 1 }} 节</n-tag>
                  <n-tag size="small" :type="chapter.status === 1 ? 'success' : 'default'" round>
                    {{ chapter.status === 1 ? '已发布' : '草稿' }}
                  </n-tag>
                  <n-tag size="small" type="warning" round>{{ resourceLabel(chapter.resourceType) }}</n-tag>
                </div>
                <n-space :size="6">
                  <n-button size="small" secondary type="primary" @click="openQuizManager(chapter)">章节小测</n-button>
                  <n-button size="small" secondary @click="openChapterEdit(chapter)">编辑</n-button>
                  <n-button size="small" secondary type="error" @click="onDeleteChapter(chapter)">删除</n-button>
                </n-space>
              </div>
              <h3>{{ chapter.title }}</h3>
              <p>{{ chapter.content || '未填写文字说明' }}</p>
              <a v-if="chapter.resourceUrl" class="resource-link" :href="chapter.resourceUrl" target="_blank">打开章节资源</a>
            </n-card>
          </div>
          <n-empty v-if="!chaptersLoading && chapters.length === 0" description="暂无在线学习章节" />
        </n-spin>
      </n-drawer-content>
    </n-drawer>

    <n-modal v-model:show="chapterModalVisible" preset="card" :title="chapterForm.id ? '编辑章节' : '新增章节'" class="chapter-modal">
      <n-form label-placement="left" label-width="96" :model="chapterForm">
        <n-form-item label="章节标题" required>
          <n-input v-model:value="chapterForm.title" placeholder="如：基本数据类型与变量" />
        </n-form-item>
        <n-form-item label="资源类型">
          <n-select v-model:value="chapterForm.resourceType" :options="resourceOptions" />
        </n-form-item>
        <n-form-item label="上传资源" v-if="chapterForm.resourceType !== 'TEXT'">
          <div class="upload-parse-box">
            <UploadFile v-model="chapterForm.resourceUrl" biz-type="course" :accept="resourceAccept" @upload-success="onChapterResourceUploaded" />
            <n-button
              v-if="chapterForm.id && chapterForm.resourceUrl"
              secondary
              type="error"
              @click="onDeleteChapterResource"
            >
              删除当前文件
            </n-button>
            <n-button
              v-if="canParseResource"
              secondary
              type="primary"
              :loading="parsing"
              @click="onParseResource"
            >
              解析到文字内容
            </n-button>
          </div>
        </n-form-item>
        <n-form-item label="文字内容">
          <n-input
            v-model:value="chapterForm.content"
            type="textarea"
            :autosize="{ minRows: 6, maxRows: 10 }"
            placeholder="可编写本节目标、学习提示、正文讲解或资料说明"
          />
        </n-form-item>
        <n-form-item label="预计时长">
          <n-input-number v-model:value="chapterForm.durationMinutes" :min="1" :max="240" />
          <span class="form-hint">分钟</span>
        </n-form-item>
        <n-form-item label="排序">
          <n-input-number v-model:value="chapterForm.sortNo" :min="1" :max="999" />
        </n-form-item>
        <n-form-item label="状态">
          <n-switch v-model:value="chapterStatusOn">
            <template #checked>发布</template>
            <template #unchecked>草稿</template>
          </n-switch>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="chapterModalVisible = false">取消</n-button>
          <n-button type="primary" :loading="chapterSaving" @click="onSaveChapter">保存章节</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="quizModalVisible"
      preset="card"
      :title="quizEditorVisible ? (quizForm.id ? '编辑测试题' : '添加测试题') : `${quizChapter?.title || ''} · 章节小测`"
      class="quiz-modal"
    >
      <template v-if="!quizEditorVisible">
        <div class="quiz-toolbar">
          <div>
            <b>章节测试题</b>
            <p>学生学习本章时可完成小测，提交后会立即判分并沉淀错题。</p>
          </div>
          <n-button type="primary" @click="openQuizCreate">添加测试题</n-button>
        </div>
        <n-spin :show="quizLoading">
          <div v-if="quizList.length" class="quiz-question-list">
            <n-card v-for="quiz in quizList" :key="quiz.id" class="quiz-question-card" :bordered="false">
              <div class="quiz-question-head">
                <div>
                  <n-tag size="small" type="info" round>{{ quizTypeLabel(quiz.type) }}</n-tag>
                  <n-tag size="small" round>第 {{ quiz.seq || 1 }} 题</n-tag>
                  <n-tag size="small" type="warning" round>{{ quiz.score || 5 }} 分</n-tag>
                </div>
                <n-space :size="6">
                  <n-button size="small" secondary @click="openQuizEdit(quiz)">编辑</n-button>
                  <n-button size="small" secondary type="error" @click="onDeleteQuiz(quiz)">删除</n-button>
                </n-space>
              </div>
              <b class="quiz-question-content">{{ quiz.content }}</b>
              <div v-if="quiz.type === 1 || quiz.type === 2" class="quiz-preview-options">
                <span v-for="(option, index) in parseQuizOptions(quiz.options)" :key="index">
                  {{ optionLetter(index) }}. {{ option }}
                </span>
              </div>
              <p class="quiz-answer">答案：{{ quiz.answer || '未填写' }}</p>
              <p v-if="quiz.analysis" class="quiz-analysis">解析：{{ quiz.analysis }}</p>
            </n-card>
          </div>
          <n-empty v-else description="本章还没有测试题">
            <template #extra>
              <n-button type="primary" @click="openQuizCreate">添加第一道测试题</n-button>
            </template>
          </n-empty>
        </n-spin>
      </template>

      <template v-else>
        <div class="quiz-editor-note">
          当前章节：{{ quizChapter?.title || '未选择章节' }}。保存后，学生可在“我的课程”中完成本章小测。
        </div>
        <n-form label-placement="left" label-width="82" :model="quizForm">
          <n-form-item label="题目类型" required>
            <n-select v-model:value="quizForm.type" :options="quizTypeOptions" />
          </n-form-item>
          <n-form-item label="题干" required>
            <n-input
              v-model:value="quizForm.content"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 5 }"
              placeholder="请输入题目内容"
            />
          </n-form-item>
          <n-form-item v-if="quizNeedsOptions" label="选项" required>
            <div class="quiz-option-form">
              <div v-for="(_, index) in quizForm.options" :key="index" class="quiz-option-row">
                <span>{{ optionLetter(index) }}</span>
                <n-input v-model:value="quizForm.options[index]" :placeholder="`请输入选项 ${optionLetter(index)}`" />
              </div>
              <small>至少填写两项；请从 A 开始连续填写。</small>
            </div>
          </n-form-item>
          <n-form-item label="标准答案" required>
            <n-select
              v-if="quizForm.type === 1"
              v-model:value="quizForm.answer"
              :options="quizAnswerOptions"
              placeholder="请选择正确选项"
            />
            <n-checkbox-group v-else-if="quizForm.type === 2" v-model:value="quizForm.answerSelections">
              <n-space>
                <n-checkbox v-for="item in quizAnswerOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </n-checkbox>
              </n-space>
            </n-checkbox-group>
            <n-radio-group v-else-if="quizForm.type === 3" v-model:value="quizForm.answer">
              <n-space>
                <n-radio value="正确">正确</n-radio>
                <n-radio value="错误">错误</n-radio>
              </n-space>
            </n-radio-group>
            <n-input v-else v-model:value="quizForm.answer" placeholder="请输入填空题标准答案" />
          </n-form-item>
          <n-form-item label="分值" required>
            <n-input-number v-model:value="quizForm.score" :min="1" :max="100" :precision="0" />
            <span class="form-hint">分</span>
          </n-form-item>
          <n-form-item label="题目排序">
            <n-input-number v-model:value="quizForm.seq" :min="1" :max="999" :precision="0" clearable placeholder="留空时自动排在最后" />
          </n-form-item>
          <n-form-item label="答案解析">
            <n-input
              v-model:value="quizForm.analysis"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 4 }"
              placeholder="可选：学生答错后会看到此解析"
            />
          </n-form-item>
        </n-form>
      </template>

      <template #footer>
        <n-space justify="end">
          <template v-if="quizEditorVisible">
            <n-button @click="closeQuizEditor">返回题目列表</n-button>
            <n-button type="primary" :loading="quizSaving" @click="onSaveQuiz">
              {{ quizForm.id ? '保存修改' : '保存测试题' }}
            </n-button>
          </template>
          <template v-else>
            <n-button @click="quizModalVisible = false">关闭</n-button>
            <n-button type="primary" @click="openQuizCreate">添加测试题</n-button>
          </template>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import {
  createDiscreteApi,
  NButton,
  NCard,
  NCheckbox,
  NCheckboxGroup,
  NDrawer,
  NDrawerContent,
  NEmpty,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NPagination,
  NRadio,
  NRadioGroup,
  NSelect,
  NSpace,
  NSpin,
  NStep,
  NSteps,
  NSwitch,
  NTable,
  NTag
} from 'naive-ui'
import {
  listCourses,
  saveCourse,
  deleteCourse,
  generateInvite,
  listCourseStudents,
  removeCourseStudent,
  listCourseChapters,
  saveCourseChapter,
  deleteCourseChapter,
  deleteCourseChapterResource
} from '../api/course'
import { deleteChapterQuiz, listChapterQuizzes, saveChapterQuiz } from '../api/chapterQuiz'
import { parseUploadedFile } from '../api/file'
import UploadFile from '../components/UploadFile.vue'

const { message, dialog } = createDiscreteApi(['message', 'dialog'])

const courses = ref([])
const loading = ref(false)
const courseCurrentPage = ref(1)
const coursePageSize = ref(10)
const courseSortField = ref('createTime')
const courseSortOrder = ref('desc')
const courseSortFieldOptions = [
  { label: '创建时间', value: 'createTime' },
  { label: '课程名称', value: 'courseName' },
  { label: '课程编号', value: 'courseCode' },
  { label: '课程状态', value: 'status' }
]
const courseSortOrderOptions = [
  { label: '降序', value: 'desc' },
  { label: '升序', value: 'asc' }
]
const sortedCourses = computed(() => [...courses.value].sort(compareCourses))
const pagedCourses = computed(() => {
  const start = (courseCurrentPage.value - 1) * coursePageSize.value
  return sortedCourses.value.slice(start, start + coursePageSize.value)
})
const courseModalVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive({ id: null, courseName: '', courseCode: '', description: '', cover: '', status: 1 })
const currentCourse = ref(null)

const studentDrawerVisible = ref(false)
const drawerCourseName = ref('')
const studentsLoading = ref(false)
const students = ref([])
const studentKeyword = ref('')
const filteredStudents = computed(() => {
  const keyword = studentKeyword.value.trim().toLowerCase()
  if (!keyword) return students.value
  return students.value.filter((student) => {
    const studentNo = String(student.username || '').toLowerCase()
    const realName = String(student.realName || '').toLowerCase()
    return studentNo.includes(keyword) || realName.includes(keyword)
  })
})

const chapterDrawerVisible = ref(false)
const chaptersLoading = ref(false)
const chapters = ref([])
const chapterModalVisible = ref(false)
const chapterSaving = ref(false)
const parsing = ref(false)
const chapterForm = reactive({
  id: null,
  courseId: null,
  title: '',
  content: '',
  resourceType: 'TEXT',
  resourceUrl: '',
  durationMinutes: 15,
  sortNo: 1,
  status: 1
})

const quizModalVisible = ref(false)
const quizEditorVisible = ref(false)
const quizLoading = ref(false)
const quizSaving = ref(false)
const quizList = ref([])
const quizChapter = ref(null)
const quizForm = reactive({
  id: null,
  courseId: null,
  chapterId: null,
  type: 1,
  content: '',
  options: ['', '', '', ''],
  answer: '',
  answerSelections: [],
  analysis: '',
  score: 5,
  seq: null
})

const resourceOptions = [
  { label: '文字章节', value: 'TEXT' },
  { label: '视频', value: 'VIDEO' },
  { label: 'PDF 文档', value: 'PDF' },
  { label: 'Word 文档', value: 'WORD' },
  { label: '其他文件', value: 'FILE' }
]
const quizTypeOptions = [
  { label: '单选题', value: 1 },
  { label: '多选题', value: 2 },
  { label: '判断题', value: 3 },
  { label: '填空题', value: 4 }
]

const statusOn = computed({
  get: () => form.status === 1,
  set: (v) => (form.status = v ? 1 : 0)
})
const chapterStatusOn = computed({
  get: () => chapterForm.status === 1,
  set: (v) => (chapterForm.status = v ? 1 : 0)
})
const resourceAccept = computed(() => {
  if (chapterForm.resourceType === 'VIDEO') return 'video/*'
  if (chapterForm.resourceType === 'PDF') return '.pdf,application/pdf'
  if (chapterForm.resourceType === 'WORD') return '.doc,.docx,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  return ''
})
const canParseResource = computed(() => {
  if (!chapterForm.resourceUrl) return false
  return /\.(pdf|docx|txt|md)$/i.test(chapterForm.resourceUrl)
})
const quizNeedsOptions = computed(() => quizForm.type === 1 || quizForm.type === 2)
const quizAnswerOptions = computed(() => {
  const options = quizForm.options.map((option) => String(option || '').trim())
  const firstEmptyIndex = options.findIndex((option) => !option)
  const visibleOptions = firstEmptyIndex < 0 ? options : options.slice(0, firstEmptyIndex)
  return visibleOptions.map((option, index) => ({
    label: `${optionLetter(index)}. ${option}`,
    value: optionLetter(index)
  }))
})

async function load() {
  loading.value = true
  try {
    courses.value = await listCourses()
    normalizeCoursePage()
  } finally {
    loading.value = false
  }
}

/** 根据当前课程总数修正页码，避免删除或刷新后停留在空白页。 */
function normalizeCoursePage() {
  const maxPage = Math.max(1, Math.ceil(courses.value.length / coursePageSize.value))
  if (courseCurrentPage.value > maxPage) {
    courseCurrentPage.value = maxPage
  }
}

/** 切换每页条数时回到第一页，确保课程列表展示稳定。 */
function onCoursePageSizeChange(pageSize) {
  coursePageSize.value = pageSize
  courseCurrentPage.value = 1
}

/** 比较两门课程，使排序作用于分页前的完整课程列表。 */
function compareCourses(left, right) {
  const field = courseSortField.value
  let result
  if (field === 'createTime') {
    const leftTime = Date.parse(left.createTime || '') || Number(left.id || 0)
    const rightTime = Date.parse(right.createTime || '') || Number(right.id || 0)
    result = leftTime - rightTime
  } else if (field === 'status') {
    result = Number(left.status || 0) - Number(right.status || 0)
  } else {
    result = String(left[field] || '').localeCompare(String(right[field] || ''), 'zh-CN', {
      numeric: true,
      sensitivity: 'base'
    })
  }
  if (result === 0) result = Number(left.id || 0) - Number(right.id || 0)
  return courseSortOrder.value === 'asc' ? result : -result
}

/** 修改课程排序条件后返回第一页，避免当前页内容突然为空。 */
function onCourseSortChange() {
  courseCurrentPage.value = 1
}

function resetForm() {
  Object.assign(form, { id: null, courseName: '', courseCode: '', description: '', cover: '', status: 1 })
}

function openCreate() {
  isEdit.value = false
  resetForm()
  courseModalVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, { ...row })
  courseModalVisible.value = true
}

async function onSave() {
  if (!form.courseName) {
    message.warning('请填写课程名称')
    return
  }
  saving.value = true
  try {
    const saved = await saveCourse({ ...form })
    message.success('保存成功')
    courseModalVisible.value = false
    await load()
    if (!isEdit.value) {
      const course = saved || courses.value.find((item) => item.courseName === form.courseName)
      if (course) await openChapters(course)
    }
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  const ok = await confirmAction(`确定删除课程“${row.courseName}”吗？`)
  if (!ok) return
  await deleteCourse(row.id)
  message.success('已删除')
  await load()
}

async function onInvite(row) {
  const code = await generateInvite(row.id)
  message.success(`邀请码：${code}`)
  await load()
}

async function openStudents(row) {
  currentCourse.value = row
  drawerCourseName.value = row.courseName
  studentKeyword.value = ''
  studentDrawerVisible.value = true
  studentsLoading.value = true
  try {
    students.value = await listCourseStudents(row.id)
  } finally {
    studentsLoading.value = false
  }
}

async function onRemoveStudent(row) {
  const ok = await confirmAction(`确定将“${row.realName || row.username}”从课程中移除吗？`)
  if (!ok) return
  await removeCourseStudent(currentCourse.value.id, row.id)
  message.success('已移除')
  students.value = await listCourseStudents(currentCourse.value.id)
}

async function openChapters(row) {
  currentCourse.value = row
  drawerCourseName.value = row.courseName
  chapterDrawerVisible.value = true
  await loadChapters()
}

async function loadChapters() {
  chaptersLoading.value = true
  try {
    chapters.value = await listCourseChapters(currentCourse.value.id)
  } finally {
    chaptersLoading.value = false
  }
}

function resetChapterForm() {
  Object.assign(chapterForm, {
    id: null,
    courseId: currentCourse.value.id,
    title: '',
    content: '',
    resourceType: 'TEXT',
    resourceUrl: '',
    durationMinutes: 15,
    sortNo: chapters.value.length + 1,
    status: 1
  })
}

function openChapterCreate() {
  resetChapterForm()
  chapterModalVisible.value = true
}

function openChapterEdit(chapter) {
  Object.assign(chapterForm, { ...chapter, resourceType: chapter.resourceType || 'TEXT', resourceUrl: chapter.resourceUrl || '' })
  chapterModalVisible.value = true
}

/** 打开指定章节的小测管理面板，并加载教师可见的题目与标准答案。 */
async function openQuizManager(chapter) {
  quizChapter.value = chapter
  quizEditorVisible.value = false
  quizModalVisible.value = true
  await loadChapterQuizzes()
}

/** 查询当前章节已经配置的小测题目。 */
async function loadChapterQuizzes() {
  if (!quizChapter.value?.id) return
  quizLoading.value = true
  try {
    quizList.value = await listChapterQuizzes(quizChapter.value.id)
  } finally {
    quizLoading.value = false
  }
}

/** 重置测试题表单，新增题目时固定关联当前章节。 */
function resetQuizForm() {
  Object.assign(quizForm, {
    id: null,
    courseId: quizChapter.value?.courseId || currentCourse.value?.id || null,
    chapterId: quizChapter.value?.id || null,
    type: 1,
    content: '',
    options: ['', '', '', ''],
    answer: '',
    answerSelections: [],
    analysis: '',
    score: 5,
    seq: null
  })
}

/** 进入新增测试题表单。 */
function openQuizCreate() {
  resetQuizForm()
  quizEditorVisible.value = true
}

/** 将已有题目回填到表单，以便教师调整题干、选项和答案。 */
function openQuizEdit(quiz) {
  const options = parseQuizOptions(quiz.options).slice(0, 4)
  while (options.length < 4) options.push('')
  const answer = quiz.answer || ''
  Object.assign(quizForm, {
    id: quiz.id,
    courseId: quizChapter.value?.courseId || currentCourse.value?.id || quiz.courseId,
    chapterId: quizChapter.value?.id || quiz.chapterId,
    type: Number(quiz.type) || 1,
    content: quiz.content || '',
    options,
    answer,
    answerSelections: Number(quiz.type) === 2
      ? [...new Set(String(answer).toUpperCase().match(/[A-Z0-9]/g) || [])]
      : [],
    analysis: quiz.analysis || '',
    score: quiz.score || 5,
    seq: quiz.seq || null
  })
  quizEditorVisible.value = true
}

/** 关闭测试题编辑器，返回当前章节的题目列表。 */
function closeQuizEditor() {
  quizEditorVisible.value = false
}

/** 保存测试题，并按题型整理后端需要的选项与标准答案格式。 */
async function onSaveQuiz() {
  const content = String(quizForm.content || '').trim()
  if (!content) {
    message.warning('请输入题干')
    return
  }

  const score = Number(quizForm.score)
  if (!Number.isFinite(score) || score < 1) {
    message.warning('分值必须大于 0')
    return
  }

  let options = []
  let answer = ''
  if (quizNeedsOptions.value) {
    const rawOptions = quizForm.options.map((item) => String(item || '').trim())
    const firstEmptyIndex = rawOptions.findIndex((item) => !item)
    if (rawOptions.filter(Boolean).length < 2) {
      message.warning('单选题和多选题至少填写两个选项')
      return
    }
    if (firstEmptyIndex >= 0 && rawOptions.slice(firstEmptyIndex + 1).some(Boolean)) {
      message.warning('选项请从 A 开始连续填写')
      return
    }
    options = rawOptions.filter(Boolean)
    const allowedAnswers = options.map((_, index) => optionLetter(index))
    answer = quizForm.type === 2
      ? [...new Set(quizForm.answerSelections)].sort().join('')
      : String(quizForm.answer || '').trim().toUpperCase()
    if (!answer) {
      message.warning('请选择标准答案')
      return
    }
    if ([...answer].some((item) => !allowedAnswers.includes(item))) {
      message.warning('标准答案必须来自已填写的选项')
      return
    }
  } else if (quizForm.type === 3) {
    answer = String(quizForm.answer || '').trim()
    if (!['正确', '错误'].includes(answer)) {
      message.warning('请填写“正确”或“错误”作为判断题答案')
      return
    }
  } else {
    answer = String(quizForm.answer || '').trim()
    if (!answer) {
      message.warning('请输入填空题标准答案')
      return
    }
  }

  quizSaving.value = true
  try {
    await saveChapterQuiz({
      id: quizForm.id || undefined,
      courseId: quizChapter.value?.courseId || currentCourse.value?.id,
      chapterId: quizChapter.value?.id,
      type: quizForm.type,
      content,
      options,
      answer,
      analysis: String(quizForm.analysis || '').trim(),
      score,
      seq: quizForm.seq || undefined
    })
    message.success(quizForm.id ? '测试题已修改' : '测试题已添加')
    quizEditorVisible.value = false
    await loadChapterQuizzes()
  } finally {
    quizSaving.value = false
  }
}

/** 删除一题章节小测，确认后刷新当前章节题目列表。 */
async function onDeleteQuiz(quiz) {
  const ok = await confirmAction(`确定删除测试题“${quiz.content}”吗？`)
  if (!ok) return
  await deleteChapterQuiz(quiz.id)
  message.success('测试题已删除')
  await loadChapterQuizzes()
}

/** 解析接口返回的选项 JSON，异常数据按空选项处理。 */
function parseQuizOptions(text) {
  if (!text) return []
  try {
    const options = JSON.parse(text)
    return Array.isArray(options) ? options.map((item) => String(item || '')) : []
  } catch (e) {
    return []
  }
}

/** 返回题目选项字母标识。 */
function optionLetter(index) {
  return String.fromCharCode(65 + index)
}

/** 返回教师端展示的题型名称。 */
function quizTypeLabel(type) {
  return quizTypeOptions.find((item) => item.value === Number(type))?.label || '测试题'
}

/** 上传成功后根据扩展名更新资源类型，保证学生端使用匹配的预览或下载方式。 */
function onChapterResourceUploaded(url) {
  chapterForm.resourceUrl = url || chapterForm.resourceUrl
  const extension = (url || '').split('?')[0].split('.').pop()?.toLowerCase()
  const typeMap = {
    pdf: 'PDF',
    doc: 'WORD',
    docx: 'WORD',
    mp4: 'VIDEO',
    webm: 'VIDEO',
    mov: 'VIDEO'
  }
  if (typeMap[extension]) {
    chapterForm.resourceType = typeMap[extension]
  }
}

/** 删除当前章节的资源文件，并同步清空数据库资源地址。 */
async function onDeleteChapterResource() {
  if (!chapterForm.id || !chapterForm.resourceUrl) return
  const ok = await confirmAction('确定删除当前章节文件吗？删除后不可恢复。')
  if (!ok) return
  await deleteCourseChapterResource(chapterForm.id)
  chapterForm.resourceUrl = ''
  message.success('章节文件已删除，可重新上传新文件')
  await loadChapters()
}

async function onSaveChapter() {
  if (!chapterForm.title) {
    message.warning('请填写章节标题')
    return
  }
  if (chapterForm.resourceType !== 'TEXT' && !chapterForm.resourceUrl) {
    message.warning('请上传章节资源文件')
    return
  }
  if (chapterForm.resourceType === 'TEXT' && !chapterForm.content) {
    message.warning('请填写文字内容')
    return
  }
  chapterSaving.value = true
  try {
    await saveCourseChapter({ ...chapterForm })
    message.success('章节保存成功')
    chapterModalVisible.value = false
    await loadChapters()
  } finally {
    chapterSaving.value = false
  }
}

async function onParseResource() {
  if (!chapterForm.resourceUrl) {
    message.warning('请先上传章节资源')
    return
  }
  parsing.value = true
  try {
    const data = await parseUploadedFile(chapterForm.resourceUrl)
    chapterForm.content = data.text || ''
    message.success('文档已解析到文字内容，可继续编辑')
  } finally {
    parsing.value = false
  }
}

async function onDeleteChapter(chapter) {
  const ok = await confirmAction(`确定删除章节“${chapter.title}”吗？`)
  if (!ok) return
  await deleteCourseChapter(chapter.id)
  message.success('章节已删除')
  await loadChapters()
}

function confirmAction(content) {
  return new Promise((resolve) => {
    dialog.warning({
      title: '提示',
      content,
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
      onClose: () => resolve(false)
    })
  })
}

function coverStyle(course) {
  return course.cover
    ? { backgroundImage: `url(${course.cover})` }
    : { background: 'linear-gradient(135deg, #42B5BB 0%, #87D8C9 100%)' }
}

function resourceLabel(type) {
  return resourceOptions.find((item) => item.value === type)?.label || '文字章节'
}

onMounted(load)
</script>

<style scoped>
.course-admin { color: var(--text-1); }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 24px; }
.page-subtitle { margin: 6px 0 0; color: var(--text-2); font-size: 13px; }
.workflow-card { margin-bottom: 16px; border-radius: 10px; box-shadow: var(--shadow-xs); }
.table-card { border-radius: 10px; box-shadow: var(--shadow-sm); }
.course-table-toolbar { display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin-bottom: 14px; }
.course-table-toolbar > span { color: var(--text-2); font-size: 13px; }
.course-pagination { display: flex; align-items: center; justify-content: flex-end; gap: 14px; margin-top: 18px; }
.course-pagination-total { color: var(--text-2); font-size: 13px; }
.ops-col { width: 330px; }
.course-cell { display: flex; align-items: center; gap: 12px; }
.course-cover { width: 54px; height: 38px; border-radius: 8px; background-size: cover; background-position: center; flex: 0 0 auto; }
.course-name { font-weight: 700; }
.course-code, .muted { color: var(--text-2); font-size: 12px; }
.desc-cell { max-width: 260px; color: var(--text-2); }
.drawer-note { color: var(--text-2); font-size: 13px; line-height: 1.7; margin-bottom: 14px; }
.student-search { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.student-search span { flex: 0 0 auto; color: var(--text-2); font-size: 12px; }
.chapter-toolbar { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.chapter-list { display: flex; flex-direction: column; gap: 12px; }
.chapter-card { background: #f8fcfc; border-radius: 10px; }
.chapter-card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.chapter-card-head > div:first-child { display: flex; gap: 6px; flex-wrap: wrap; }
.chapter-card h3 { margin: 12px 0 8px; font-size: 16px; }
.chapter-card p { margin: 0; color: var(--text-2); line-height: 1.7; white-space: pre-wrap; }
.resource-link { display: inline-block; margin-top: 10px; color: var(--brand); font-weight: 600; text-decoration: none; }
.form-hint { margin-left: 8px; color: var(--text-2); font-size: 13px; }
.upload-parse-box { width: 100%; display: flex; flex-direction: column; gap: 10px; }
.quiz-toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; padding: 12px 14px; border: 1px solid #d7eeee; border-radius: 10px; background: #f4fbfb; }
.quiz-toolbar b { color: var(--text-1); }
.quiz-toolbar p { margin: 5px 0 0; color: var(--text-2); font-size: 13px; line-height: 1.6; }
.quiz-question-list { display: flex; flex-direction: column; gap: 10px; }
.quiz-question-card { border-radius: 10px; background: #f8fcfc; }
.quiz-question-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.quiz-question-head > div:first-child { display: flex; flex-wrap: wrap; gap: 6px; }
.quiz-question-content { display: block; margin-top: 12px; color: var(--text-1); line-height: 1.65; }
.quiz-preview-options { display: grid; gap: 5px; margin-top: 9px; color: var(--text-2); font-size: 13px; }
.quiz-answer, .quiz-analysis { margin: 9px 0 0; color: var(--text-2); font-size: 13px; line-height: 1.65; }
.quiz-answer { color: #0f766e; font-weight: 600; }
.quiz-editor-note { margin-bottom: 16px; padding: 10px 12px; border-left: 3px solid var(--brand); border-radius: 0 8px 8px 0; background: #f4fbfb; color: var(--text-2); font-size: 13px; line-height: 1.6; }
.quiz-option-form { width: 100%; display: grid; gap: 9px; }
.quiz-option-row { display: grid; grid-template-columns: 28px minmax(0, 1fr); align-items: center; gap: 8px; }
.quiz-option-row > span { display: grid; width: 28px; height: 28px; place-items: center; border-radius: 50%; background: #e5f5f5; color: #0f766e; font-size: 13px; font-weight: 700; }
.quiz-option-form small { color: var(--text-2); font-size: 12px; }
:global(.course-modal) { width: min(620px, calc(100vw - 32px)); }
:global(.chapter-modal) { width: min(720px, calc(100vw - 32px)); }
:global(.quiz-modal) { width: min(760px, calc(100vw - 32px)); }
@media (max-width: 720px) {
  .page-head { flex-direction: column; }
  .course-table-toolbar { align-items: flex-start; flex-direction: column; }
  .course-pagination { align-items: flex-start; flex-direction: column; }
  .quiz-toolbar { flex-direction: column; }
  .quiz-question-head { align-items: flex-start; flex-direction: column; }
}
</style>
