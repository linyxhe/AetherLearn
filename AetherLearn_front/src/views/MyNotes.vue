<template>
  <div class="my-notes-page">
    <div class="notes-hero">
      <div>
        <div class="eyebrow">学习笔记</div>
        <h2>把每一章的重点，留在自己的复习路径里</h2>
        <p>这里汇总你在课程学习时保存的私人笔记。按课程筛选、收藏重点内容，或回到对应章节继续编辑。</p>
      </div>
      <div class="notes-count-card">
        <span>已保存笔记</span>
        <strong>{{ notes.length }}</strong>
        <small>{{ favoriteCount }} 篇重点收藏</small>
      </div>
    </div>

    <n-card :bordered="false" class="notes-toolbar">
      <n-space align="center" justify="space-between" wrap>
        <n-space align="center" wrap>
          <n-select
            v-model:value="selectedCourse"
            clearable
            :options="courseOptions"
            placeholder="全部课程"
            style="width: 250px"
            @update:value="loadNotes"
          />
          <n-checkbox v-model:checked="onlyFavorites">只看重点收藏</n-checkbox>
        </n-space>
        <n-button secondary :loading="loading" @click="load">刷新笔记</n-button>
      </n-space>
    </n-card>

    <n-spin :show="loading">
      <div v-if="visibleNotes.length" class="note-grid">
        <n-card
          v-for="note in visibleNotes"
          :key="note.id"
          :bordered="false"
          class="note-card"
          :class="{ favorite: note.favorite === 1 }"
        >
          <div class="note-meta">
            <n-tag size="small" type="info" round>{{ courseName(note.courseId) }}</n-tag>
            <span>{{ chapterName(note) }}</span>
            <n-tag v-if="note.favorite === 1" size="small" type="warning" round>重点</n-tag>
          </div>
          <h3>{{ note.title || '章节笔记' }}</h3>
          <p class="note-content">{{ note.content }}</p>
          <div class="note-foot">
            <span>最近编辑：{{ formatTime(note.updateTime || note.createTime) }}</span>
            <n-space :size="8">
              <n-button size="small" secondary type="error" @click="onDelete(note)">删除</n-button>
              <n-button size="small" type="primary" @click="continueEdit(note)">继续编辑</n-button>
            </n-space>
          </div>
        </n-card>
      </div>
      <n-empty v-else :description="onlyFavorites ? '还没有收藏重点笔记' : '还没有学习笔记'">
        <template #extra>
          <n-button type="primary" @click="goMyCourse">去学习章节并记录笔记</n-button>
        </template>
      </n-empty>
    </n-spin>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCard, NCheckbox, NEmpty, NSelect, NSpace, NSpin, NTag, createDiscreteApi } from 'naive-ui'
import { listCourses, listCourseChapters } from '../api/course'
import { deleteChapterNote, listChapterNotes } from '../api/note'

const router = useRouter()
const { message, dialog } = createDiscreteApi(['message', 'dialog'])
const courses = ref([])
const notes = ref([])
const loading = ref(false)
const selectedCourse = ref(null)
const onlyFavorites = ref(false)
const chapterMap = reactive({})

const courseOptions = computed(() => courses.value.map((course) => ({
  label: course.courseName,
  value: course.id
})))
const visibleNotes = computed(() => notes.value.filter((note) => !onlyFavorites.value || note.favorite === 1))
const favoriteCount = computed(() => notes.value.filter((note) => note.favorite === 1).length)

/** 加载课程、章节名称映射和当前学生的笔记列表。 */
async function load() {
  loading.value = true
  try {
    courses.value = await listCourses()
    await Promise.all(courses.value.map(async (course) => {
      chapterMap[course.id] = await listCourseChapters(course.id)
    }))
    await loadNotes()
  } finally {
    loading.value = false
  }
}

/** 按当前课程筛选条件重新查询笔记。 */
async function loadNotes() {
  notes.value = await listChapterNotes(selectedCourse.value || undefined)
}

/** 根据课程 ID 返回学生可识别的课程名称。 */
function courseName(courseId) {
  return courses.value.find((course) => course.id === courseId)?.courseName || '原课程已不可用'
}

/** 根据课程与章节 ID 返回章节名称，避免笔记列表只显示数字 ID。 */
function chapterName(note) {
  const chapter = (chapterMap[note.courseId] || []).find((item) => item.id === note.chapterId)
  return chapter?.title || '原章节已不可用'
}

/** 统一格式化笔记的最近编辑时间。 */
function formatTime(value) {
  if (!value) return '刚刚保存'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 16)
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
  })
}

/** 跳回指定课程章节，并由学习页自动定位到该章节的笔记编辑区。 */
function continueEdit(note) {
  router.push({
    path: '/my-course',
    query: { courseId: String(note.courseId), chapterId: String(note.chapterId) }
  })
}

/** 跳转到课程学习页，方便在新章节中记录笔记。 */
function goMyCourse() {
  router.push('/my-course')
}

/** 二次确认后删除自己的笔记，并刷新当前列表。 */
function onDelete(note) {
  dialog.warning({
    title: '删除笔记',
    content: `确定删除笔记“${note.title || '章节笔记'}”吗？删除后不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await deleteChapterNote(note.id)
      message.success('笔记已删除')
      await loadNotes()
    }
  })
}

onMounted(load)
</script>

<style scoped>
.my-notes-page { display: flex; flex-direction: column; gap: 16px; color: var(--text-1); }
.notes-hero { display: grid; grid-template-columns: minmax(0, 1fr) 230px; gap: 18px; align-items: stretch; }
.eyebrow { color: #319ca7; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.notes-hero h2 { margin: 8px 0 10px; color: #18323d; font-size: 28px; letter-spacing: -.02em; }
.notes-hero p { max-width: 62ch; margin: 0; color: var(--text-2); line-height: 1.75; }
.notes-count-card { display: flex; flex-direction: column; justify-content: center; min-height: 126px; padding: 18px 20px; border-radius: 16px; background: linear-gradient(135deg, #e7f7f6, #f9fcfb); box-shadow: var(--shadow-sm); }
.notes-count-card span, .notes-count-card small { color: var(--text-2); font-size: 12px; }
.notes-count-card strong { margin: 5px 0; color: #0f766e; font-size: 36px; line-height: 1; }
.notes-toolbar, .note-card { border-radius: 14px; box-shadow: var(--shadow-xs); }
.note-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(290px, 1fr)); gap: 14px; }
.note-card { position: relative; overflow: hidden; min-height: 224px; background: #fff; }
.note-card.favorite::before { position: absolute; top: 0; bottom: 0; left: 0; width: 4px; background: #e9a23b; content: ''; }
.note-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; min-height: 24px; color: var(--text-2); font-size: 12px; }
.note-card h3 { margin: 14px 0 8px; color: #18323d; font-size: 17px; }
.note-content { display: -webkit-box; margin: 0; overflow: hidden; color: #53656d; line-height: 1.75; -webkit-box-orient: vertical; -webkit-line-clamp: 4; }
.note-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 18px; color: var(--text-2); font-size: 12px; }
.note-foot > span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 720px) {
  .notes-hero { grid-template-columns: 1fr; }
  .note-foot { align-items: flex-start; flex-direction: column; }
}
</style>
