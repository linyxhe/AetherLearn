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
            <tr v-for="row in courses" :key="row.id">
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
              <tr v-for="student in students" :key="student.id">
                <td>{{ student.realName || '-' }}</td>
                <td>{{ student.username }}</td>
                <td>{{ student.phone || student.email || '-' }}</td>
                <td><n-button size="small" text type="error" @click="onRemoveStudent(student)">移除</n-button></td>
              </tr>
            </tbody>
          </n-table>
          <n-empty v-if="!studentsLoading && students.length === 0" description="暂无学生加入" />
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
            <UploadFile v-model="chapterForm.resourceUrl" biz-type="course" :accept="resourceAccept" />
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import {
  createDiscreteApi,
  NButton,
  NCard,
  NDrawer,
  NDrawerContent,
  NEmpty,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
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
  deleteCourseChapter
} from '../api/course'
import { parseUploadedFile } from '../api/file'
import UploadFile from '../components/UploadFile.vue'

const { message, dialog } = createDiscreteApi(['message', 'dialog'])

const courses = ref([])
const loading = ref(false)
const courseModalVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive({ id: null, courseName: '', courseCode: '', description: '', cover: '', status: 1 })
const currentCourse = ref(null)

const studentDrawerVisible = ref(false)
const drawerCourseName = ref('')
const studentsLoading = ref(false)
const students = ref([])

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

const resourceOptions = [
  { label: '文字章节', value: 'TEXT' },
  { label: '视频', value: 'VIDEO' },
  { label: 'PDF 文档', value: 'PDF' },
  { label: 'Word 文档', value: 'WORD' },
  { label: '其他文件', value: 'FILE' }
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

async function load() {
  loading.value = true
  try {
    courses.value = await listCourses()
  } finally {
    loading.value = false
  }
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
.ops-col { width: 330px; }
.course-cell { display: flex; align-items: center; gap: 12px; }
.course-cover { width: 54px; height: 38px; border-radius: 8px; background-size: cover; background-position: center; flex: 0 0 auto; }
.course-name { font-weight: 700; }
.course-code, .muted { color: var(--text-2); font-size: 12px; }
.desc-cell { max-width: 260px; color: var(--text-2); }
.drawer-note { color: var(--text-2); font-size: 13px; line-height: 1.7; margin-bottom: 14px; }
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
:global(.course-modal) { width: min(620px, calc(100vw - 32px)); }
:global(.chapter-modal) { width: min(720px, calc(100vw - 32px)); }
@media (max-width: 720px) {
  .page-head { flex-direction: column; }
}
</style>
