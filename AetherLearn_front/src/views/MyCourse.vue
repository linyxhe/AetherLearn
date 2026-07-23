<template>
  <div class="student-course">
    <div class="page-head">
      <div>
        <h2 class="page-title">我的课程</h2>
        <p class="page-subtitle">用邀请码加入课程，进入课程后按章节学习教师发布的资源。</p>
      </div>
      <n-card class="join-card" :bordered="false">
        <n-input-group>
          <n-input v-model:value="inviteCode" placeholder="输入课程邀请码" clearable @keyup.enter="onJoin" />
          <n-button type="primary" :loading="joining" @click="onJoin">加入</n-button>
        </n-input-group>
      </n-card>
    </div>

    <template v-if="!activeCourse">
      <n-spin :show="loading">
        <div class="course-grid">
          <n-card v-for="course in courses" :key="course.id" class="course-card" :bordered="false" @click="enterCourse(course)">
            <div class="course-cover" :style="coverStyle(course)">
              <n-tag round type="success">{{ course.courseCode || 'COURSE' }}</n-tag>
            </div>
            <div class="course-card-body">
              <div class="course-title-row">
                <div>
                  <h3>{{ course.courseName }}</h3>
                  <p>{{ course.description || '教师暂未填写课程简介。' }}</p>
                </div>
                <n-progress type="circle" :percentage="progressOf(course)" :height="6" :stroke-width="7" color="#42B5BB" />
              </div>
              <n-button secondary type="primary" block>进入课程</n-button>
            </div>
          </n-card>
        </div>
        <n-empty v-if="!loading && courses.length === 0" description="暂无课程，输入邀请码加入第一门课程" />
      </n-spin>
    </template>

    <template v-else>
      <div class="learn-head">
        <n-button secondary @click="activeCourse = null">返回课程列表</n-button>
        <div>
          <h2>{{ activeCourse.courseName }}</h2>
          <p>{{ activeCourse.description || '按章节完成课程学习。' }}</p>
        </div>
        <n-progress type="line" :percentage="progressOf(activeCourse)" :height="8" color="#42B5BB" />
      </div>

      <div class="learning-layout">
        <n-card class="chapter-panel" :bordered="false">
          <div class="panel-title">课程章节</div>
          <div class="chapter-list">
            <button
              v-for="chapter in activeChapters"
              :key="chapter.id"
              class="chapter-item"
              :class="{ active: activeChapter?.id === chapter.id, done: chapter.completed }"
              @click="openChapter(chapter)"
            >
              <span class="chapter-dot"></span>
              <span class="chapter-info">
                <b>{{ chapter.sortNo || 1 }}. {{ chapter.title }}</b>
                <small>{{ resourceLabel(chapter.resourceType) }} · {{ chapter.durationMinutes || 15 }} 分钟</small>
              </span>
              <n-tag v-if="chapter.completed" size="small" type="success" round>已完成</n-tag>
            </button>
          </div>
          <n-empty v-if="activeChapters.length === 0" description="教师暂未发布在线学习章节" />
        </n-card>

        <n-card class="learn-panel" :bordered="false">
          <template v-if="activeChapter">
            <div class="learn-title">
              <div>
                <n-tag type="info" round>{{ resourceLabel(activeChapter.resourceType) }}</n-tag>
                <h3>{{ activeChapter.title }}</h3>
              </div>
              <n-space>
                <n-button secondary @click="toggleReaderFullscreen">
                  {{ isReaderFullscreen ? '退出阅读模式' : '全屏阅读' }}
                </n-button>
                <n-button v-if="!activeChapter.completed" type="primary" @click="onComplete(activeCourse, activeChapter)">完成学习</n-button>
                <n-tag v-else type="success" round>已完成</n-tag>
              </n-space>
            </div>

            <div class="reader-shell" :class="{ fullscreen: isReaderFullscreen }">
              <div v-if="isReaderFullscreen" class="reader-toolbar">
                <div>
                  <n-tag type="info" round>{{ resourceLabel(activeChapter.resourceType) }}</n-tag>
                  <strong>{{ activeChapter.title }}</strong>
                </div>
                <n-space>
                  <n-button v-if="!activeChapter.completed" type="primary" secondary @click="onComplete(activeCourse, activeChapter)">完成学习</n-button>
                  <n-button type="primary" @click="toggleReaderFullscreen">退出阅读模式</n-button>
                </n-space>
              </div>
              <div class="resource-viewer">
                <video v-if="activeChapter.resourceType === 'VIDEO' && activeChapter.resourceUrl" controls :src="resolveResourceUrl(activeChapter.resourceUrl)"></video>
                <iframe v-else-if="activeChapter.resourceType === 'PDF' && activeChapter.resourceUrl" :src="resolveResourceUrl(activeChapter.resourceUrl)"></iframe>
                <div v-else-if="activeChapter.resourceUrl" class="file-resource">
                  <div class="file-icon">{{ resourceLabel(activeChapter.resourceType) }}</div>
                  <div>
                    <b>章节资料</b>
                    <p>浏览器可能无法直接预览 Word 或其他文件，可点击打开/下载。</p>
                    <a :href="resolveResourceUrl(activeChapter.resourceUrl)" target="_blank">打开资源文件</a>
                  </div>
                </div>
                <div v-else class="text-resource">
                  <b>文字学习内容</b>
                </div>
              </div>

              <div class="chapter-side">
                <div class="chapter-text">{{ activeChapter.content || '教师暂未填写文字说明，请查看章节资源。' }}</div>
                <div class="note-box">
                  <div class="note-head">
                    <div>
                      <b>我的章节笔记</b>
                      <span>只对自己可见，保存后可继续编辑。</span>
                    </div>
                    <n-button size="small" type="primary" :loading="noteSaving" @click="saveNote">保存笔记</n-button>
                  </div>
                  <n-input v-model:value="noteForm.title" placeholder="笔记标题" class="note-title-input" />
                  <n-input v-model:value="noteForm.content" type="textarea" :autosize="{ minRows: 5, maxRows: 10 }" placeholder="记录本章重点、疑问或复习计划" />
                  <n-checkbox v-model:checked="noteForm.favorite" class="note-favorite">收藏为重点章节</n-checkbox>
                </div>
                <div class="note-box">
                  <div class="note-head">
                    <div>
                      <b>本章小测</b>
                      <span>做完立即判分，错题会自动沉淀。</span>
                    </div>
                    <n-button size="small" secondary :loading="quizLoading" @click="loadQuiz">刷新题目</n-button>
                  </div>
                  <n-spin :show="quizLoading">
                    <div v-if="quizList.length" class="quiz-list">
                      <div v-for="quiz in quizList" :key="quiz.id" class="quiz-item">
                        <b>{{ quiz.seq || 1 }}. {{ quiz.content }}</b>
                        <div v-if="quiz.type === 1" class="quiz-options">
                          <n-radio-group v-model:value="quizAnswers[quiz.id]">
                            <n-space vertical size="small">
                              <n-radio
                                v-for="(option, index) in parseQuizOptions(quiz.options)"
                                :key="index"
                                :value="String.fromCharCode(65 + index)"
                              >
                                {{ String.fromCharCode(65 + index) }}. {{ option }}
                              </n-radio>
                            </n-space>
                          </n-radio-group>
                        </div>
                        <div v-else-if="quiz.type === 3" class="quiz-options">
                          <n-radio-group v-model:value="quizAnswers[quiz.id]">
                            <n-space vertical size="small">
                              <n-radio value="正确">正确</n-radio>
                              <n-radio value="错误">错误</n-radio>
                            </n-space>
                          </n-radio-group>
                        </div>
                        <div v-else-if="quiz.type === 2" class="quiz-options">
                          <n-checkbox-group v-model:value="quizAnswers[quiz.id]">
                            <n-space vertical size="small">
                              <n-checkbox
                                v-for="(option, index) in parseQuizOptions(quiz.options)"
                                :key="index"
                                :value="String.fromCharCode(65 + index)"
                              >
                                {{ String.fromCharCode(65 + index) }}. {{ option }}
                              </n-checkbox>
                            </n-space>
                          </n-checkbox-group>
                        </div>
                        <n-input
                          v-else
                          v-model:value="quizAnswers[quiz.id]"
                          placeholder="请输入答案"
                          class="quiz-input"
                        />
                        <small>{{ quiz.score || 5 }} 分 · {{ quiz.analysis || '完成后自动判分。' }}</small>
                      </div>
                      <n-button type="primary" :loading="quizSubmitting" @click="submitQuiz">提交小测</n-button>
                    </div>
                    <n-empty v-else description="教师暂未配置本章小测" />
                  </n-spin>
                  <div v-if="quizResult" class="quiz-result">
                    <b>提交结果</b>
                    <div v-for="item in quizResult" :key="item.quizId" class="quiz-result-item" :class="{ wrong: !item.correct }">
                      <span>{{ item.correct ? '正确' : '错误' }}</span>
                      <p>{{ item.feedback }}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
          <n-empty v-else description="请选择左侧章节开始学习" />
        </n-card>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import {
  createDiscreteApi,
  NButton,
  NCard,
  NEmpty,
  NInput,
  NInputGroup,
  NProgress,
  NRadio,
  NRadioGroup,
  NSpin,
  NSpace,
  NTag,
  NCheckbox,
  NCheckboxGroup
} from 'naive-ui'
import { listCourses, joinCourse, listCourseChapters, completeCourseChapter } from '../api/course'
import { getChapterNote, saveChapterNote } from '../api/note'
import { listChapterQuizzes, submitChapterQuiz } from '../api/chapterQuiz'

const { message } = createDiscreteApi(['message'])

const courses = ref([])
const chapterMap = reactive({})
const loading = ref(false)
const joining = ref(false)
const inviteCode = ref('')
const activeCourse = ref(null)
const activeChapter = ref(null)
const isReaderFullscreen = ref(false)
const noteSaving = ref(false)
const noteForm = reactive({ id: null, title: '', content: '', favorite: false })
const quizLoading = ref(false)
const quizSubmitting = ref(false)
const quizList = ref([])
const quizResult = ref(null)
const quizAnswers = reactive({})

const activeChapters = computed(() => activeCourse.value ? (chapterMap[activeCourse.value.id] || []) : [])

async function load() {
  loading.value = true
  try {
    courses.value = await listCourses()
    await Promise.all(courses.value.map(async (course) => {
      chapterMap[course.id] = await listCourseChapters(course.id)
    }))
  } finally {
    loading.value = false
  }
}

async function onJoin() {
  if (!inviteCode.value) {
    message.warning('请输入课程邀请码')
    return
  }
  joining.value = true
  try {
    await joinCourse(inviteCode.value.toUpperCase())
    message.success('加入课程成功')
    inviteCode.value = ''
    await load()
  } finally {
    joining.value = false
  }
}

async function enterCourse(course) {
  activeCourse.value = course
  activeChapter.value = (chapterMap[course.id] || [])[0] || null
  await loadNote()
  await loadQuiz()
}

async function openChapter(chapter) {
  activeChapter.value = chapter
  await loadNote()
  await loadQuiz()
}

function toggleReaderFullscreen() {
  isReaderFullscreen.value = !isReaderFullscreen.value
}

async function onComplete(course, chapter) {
  await completeCourseChapter(chapter.id)
  message.success('学习进度已更新')
  chapterMap[course.id] = await listCourseChapters(course.id)
  activeChapter.value = chapterMap[course.id].find((item) => item.id === chapter.id) || null
}

function resetNote() {
  Object.assign(noteForm, { id: null, title: '', content: '', favorite: false })
}

async function loadNote() {
  resetNote()
  if (!activeChapter.value?.id) return
  const note = await getChapterNote(activeChapter.value.id)
  if (note) {
    Object.assign(noteForm, {
      id: note.id,
      title: note.title || '',
      content: note.content || '',
      favorite: note.favorite === 1
    })
  }
}

async function saveNote() {
  if (!activeCourse.value?.id || !activeChapter.value?.id) return
  if (!noteForm.content.trim()) {
    message.warning('请先填写笔记内容')
    return
  }
  noteSaving.value = true
  try {
    const note = await saveChapterNote({
      courseId: activeCourse.value.id,
      chapterId: activeChapter.value.id,
      title: noteForm.title,
      content: noteForm.content,
      favorite: noteForm.favorite ? 1 : 0
    })
    noteForm.id = note.id
    message.success('笔记已保存')
  } finally {
    noteSaving.value = false
  }
}

async function loadQuiz() {
  quizList.value = []
  quizResult.value = null
  Object.keys(quizAnswers).forEach((key) => delete quizAnswers[key])
  if (!activeChapter.value?.id) return
  quizLoading.value = true
  try {
    quizList.value = await listChapterQuizzes(activeChapter.value.id)
    quizList.value.forEach((quiz) => {
      quizAnswers[quiz.id] = quiz.type === 2 ? [] : ''
    })
  } finally {
    quizLoading.value = false
  }
}

async function submitQuiz() {
  if (!activeCourse.value?.id || !activeChapter.value?.id || quizList.value.length === 0) return
  quizSubmitting.value = true
  try {
    quizResult.value = await submitChapterQuiz({
      courseId: activeCourse.value.id,
      chapterId: activeChapter.value.id,
      answers: quizList.value.map((quiz) => ({
        quizId: quiz.id,
        answer: Array.isArray(quizAnswers[quiz.id]) ? quizAnswers[quiz.id].join('') : (quizAnswers[quiz.id] || '')
      }))
    })
    message.success('小测已提交')
  } finally {
    quizSubmitting.value = false
  }
}

function progressOf(course) {
  const chapters = chapterMap[course.id] || []
  if (!chapters.length) return 0
  const done = chapters.filter((item) => item.completed).length
  return Math.round((done / chapters.length) * 100)
}

function coverStyle(course) {
  return course.cover
    ? { backgroundImage: `url(${resolveResourceUrl(course.cover)})` }
    : { background: 'linear-gradient(135deg, #42B5BB 0%, #87D8C9 100%)' }
}

function resourceLabel(type) {
  const map = { TEXT: '文字', VIDEO: '视频', PDF: 'PDF', WORD: 'Word', FILE: '文件' }
  return map[type || 'TEXT'] || '文字'
}

function parseQuizOptions(text) {
  if (!text) return []
  try {
    const value = JSON.parse(text)
    return Array.isArray(value) ? value : []
  } catch (e) {
    return []
  }
}

function resolveResourceUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  return url.startsWith('/') ? url : `/${url}`
}

onMounted(load)
onMounted(() => {
  document.addEventListener('keydown', handleReaderKeydown)
})
onUnmounted(() => {
  document.removeEventListener('keydown', handleReaderKeydown)
})

function handleReaderKeydown(event) {
  if (event.key === 'Escape' && isReaderFullscreen.value) {
    isReaderFullscreen.value = false
  }
}
</script>

<style scoped>
.student-course { color: var(--text-1); }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; margin-bottom: 18px; }
.page-title { margin: 0; font-size: 24px; }
.page-subtitle { margin: 6px 0 0; color: var(--text-2); font-size: 13px; }
.join-card { width: min(430px, 100%); border-radius: 12px; box-shadow: var(--shadow-xs); }
.course-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(330px, 1fr)); gap: 18px; }
.course-card { overflow: hidden; cursor: pointer; border-radius: 14px; box-shadow: var(--shadow-sm); transition: transform .2s var(--ease), box-shadow .2s var(--ease); }
.course-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); }
.course-cover { height: 96px; padding: 14px; display: flex; align-items: flex-end; background-size: cover; background-position: center; }
.course-card-body { padding-top: 4px; }
.course-title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.course-title-row h3 { margin: 0; font-size: 18px; }
.course-title-row p { margin: 8px 0 0; color: var(--text-2); line-height: 1.6; font-size: 13px; }
.learn-head { display: grid; grid-template-columns: auto 1fr 240px; align-items: center; gap: 18px; margin-bottom: 16px; }
.learn-head h2 { margin: 0; font-size: 22px; }
.learn-head p { margin: 5px 0 0; color: var(--text-2); font-size: 13px; }
.learning-layout { display: grid; grid-template-columns: 330px 1fr; gap: 18px; align-items: start; }
.chapter-panel, .learn-panel { border-radius: 14px; box-shadow: var(--shadow-sm); }
.panel-title { font-weight: 700; margin-bottom: 12px; }
.chapter-list { display: flex; flex-direction: column; gap: 10px; }
.chapter-item { display: grid; grid-template-columns: 16px 1fr auto; align-items: center; gap: 10px; width: 100%; padding: 12px; border: 1px solid var(--border-light); border-radius: 10px; background: #fff; text-align: left; cursor: pointer; transition: border .2s var(--ease), background .2s var(--ease); }
.chapter-item.active { border-color: var(--brand); background: #effafa; }
.chapter-dot { width: 12px; height: 12px; border-radius: 50%; border: 3px solid #c9ece9; }
.chapter-item.done .chapter-dot { background: var(--brand); border-color: var(--brand); }
.chapter-info { min-width: 0; }
.chapter-info b { display: block; color: var(--text-1); }
.chapter-info small { display: block; margin-top: 4px; color: var(--text-2); }
.learn-title { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.learn-title h3 { margin: 10px 0 0; font-size: 20px; }
.reader-shell { display: flex; flex-direction: column; gap: 16px; }
.reader-shell.fullscreen {
  position: fixed;
  inset: 16px;
  z-index: 1200;
  padding: 16px;
  background: #f3fbfb;
  border-radius: 20px;
  box-shadow: 0 24px 70px rgba(25, 59, 62, 0.18);
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(340px, 0.9fr);
  grid-template-rows: auto 1fr;
  gap: 16px;
}
.reader-shell.fullscreen .reader-toolbar { grid-column: 1 / -1; }
.reader-shell.fullscreen .resource-viewer { min-height: 0; height: 100%; }
.reader-shell.fullscreen .chapter-side {
  margin-top: 0;
  height: 100%;
  overflow: auto;
}
.reader-shell.fullscreen .file-resource { min-height: 100%; }
.reader-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 2px 2px 8px; }
.reader-toolbar > div { display: flex; align-items: center; gap: 10px; min-width: 0; }
.reader-toolbar strong { font-size: 16px; color: var(--text-1); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.resource-viewer { min-height: 220px; border: 1px solid var(--border-light); border-radius: 12px; background: #f8fcfc; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.reader-shell.fullscreen .resource-viewer,
.reader-shell.fullscreen .chapter-side {
  box-shadow: 0 8px 30px rgba(35, 82, 85, 0.06);
}
.resource-viewer video, .resource-viewer iframe { width: 100%; height: 100%; min-height: 420px; border: 0; background: #000; display: block; object-fit: contain; }
.file-resource { display: flex; align-items: center; gap: 18px; padding: 28px; }
.file-icon { width: 76px; height: 76px; border-radius: 18px; background: var(--brand-glow); color: var(--brand); display: flex; align-items: center; justify-content: center; font-weight: 800; }
.file-resource p { color: var(--text-2); }
.file-resource a { color: var(--brand); font-weight: 700; text-decoration: none; }
.text-resource { color: var(--text-2); }
.chapter-side { display: flex; flex-direction: column; gap: 14px; }
.chapter-text { margin-top: 16px; padding: 16px; border-radius: 12px; background: #fff; border: 1px solid var(--border-light); color: var(--text-1); line-height: 1.8; white-space: pre-wrap; }
.reader-shell.fullscreen .chapter-text { margin-top: 0; background: #fff; }
.note-box { padding: 16px; border-radius: 12px; background: #fff; border: 1px solid var(--border-light); }
.note-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.note-head b { display: block; color: var(--text-1); }
.note-head span { display: block; color: var(--text-2); font-size: 12px; margin-top: 3px; }
.note-title-input { margin-bottom: 10px; }
.note-favorite { margin-top: 10px; color: var(--text-2); }
.quiz-list { display: flex; flex-direction: column; gap: 12px; }
.quiz-item { padding: 12px; border: 1px solid var(--border-light); border-radius: 10px; background: #f8fcfc; }
.quiz-item b { display: block; color: var(--text-1); line-height: 1.6; }
.quiz-item small { display: block; margin-top: 8px; color: var(--text-2); line-height: 1.6; }
.quiz-options { margin-top: 10px; }
.quiz-input { margin-top: 10px; }
.quiz-result { margin-top: 14px; padding: 12px; border-radius: 10px; background: #effafa; border: 1px solid #ccefed; }
.quiz-result > b { display: block; margin-bottom: 8px; color: var(--text-1); }
.quiz-result-item { display: grid; grid-template-columns: 46px 1fr; gap: 10px; padding: 8px 0; border-top: 1px solid rgba(66, 181, 187, 0.14); }
.quiz-result-item:first-of-type { border-top: 0; }
.quiz-result-item span { color: #0e9f6e; font-weight: 700; }
.quiz-result-item.wrong span { color: #d97706; }
.quiz-result-item p { margin: 0; color: var(--text-2); line-height: 1.6; }
@media (max-width: 900px) {
  .page-head, .learn-head { grid-template-columns: 1fr; flex-direction: column; }
  .learning-layout { grid-template-columns: 1fr; }
  .reader-shell.fullscreen {
    inset: 12px;
    padding: 12px;
    grid-template-columns: 1fr;
    grid-template-rows: auto auto auto;
    overflow: auto;
  }
  .reader-shell.fullscreen .reader-toolbar,
  .reader-shell.fullscreen .resource-viewer,
  .reader-shell.fullscreen .chapter-side {
    grid-column: auto;
  }
}
</style>
