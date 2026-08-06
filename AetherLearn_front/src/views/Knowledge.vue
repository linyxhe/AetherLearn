<template>
  <div class="knowledge-workbench">
    <div class="hero">
      <div>
        <div class="eyebrow">课程知识库</div>
        <h2>课程资料上传、检索与解析都在这里</h2>
        <p>教师先选课程，再上传 PDF、Word、Markdown 或 TXT。资料解析后的切片会直接服务于答疑和出题。</p>
      </div>
      <n-card class="hero-card" :bordered="false">
        <div class="hero-card-title">当前课程</div>
        <div class="hero-card-value">{{ selectedCourseName || '未选择课程' }}</div>
        <div class="hero-card-sub">知识库用于问答检索、章节参考和 AI 出题。</div>
      </n-card>
    </div>

    <n-card :bordered="false" class="control-card">
      <n-space align="center" justify="space-between" wrap>
        <n-select v-model:value="selectedCourse" :options="courseOptions" placeholder="选择课程" style="min-width: 260px" @update:value="onCourseChange" />
        <n-tag type="info" round>上传分桶：knowledge</n-tag>
      </n-space>
    </n-card>

    <n-alert v-if="!selectedCourse" type="info" :bordered="false" title="请先选择一门课程，再上传课程资料" />

    <template v-else>
      <n-card :bordered="false" class="panel">
        <div class="panel-title">检索预览</div>
        <n-space>
          <n-input v-model:value="searchQuery" placeholder="输入关键词搜索知识库切片" clearable @keyup.enter="onSearch" />
          <n-button type="primary" :loading="searchLoading" @click="onSearch">搜索</n-button>
        </n-space>
        <div class="search-results">
          <n-empty v-if="showSearchResults && searchResults.length === 0 && !searchLoading" description="未找到相关切片" />
          <div v-for="(item, idx) in searchResults" :key="idx" class="chunk-card">
            <div class="chunk-header">
              <n-tag size="small" type="info" round>来源：{{ item.docTitle }}</n-tag>
              <span class="chunk-index">#{{ idx + 1 }}</span>
            </div>
            <div class="chunk-content">{{ item.content }}</div>
          </div>
        </div>
      </n-card>

      <n-card :bordered="false" class="panel">
        <div class="panel-title">上传课程资料</div>
        <n-upload
          ref="uploadRef"
          :show-file-list="false"
          :max="1"
          :disabled="uploading"
          :custom-request="customUpload"
          :before-upload="beforeUpload"
          accept=".pdf,.docx,.md,.txt"
          drag
        >
          <n-upload-dragger>
            <div class="upload-box">
              <div class="upload-title">拖拽课程资料到这里，或点击上传</div>
              <template v-if="uploading">
                <n-progress type="line" :percentage="uploadProgress" :show-indicator="true" :height="8" processing />
                <div class="upload-sub">{{ uploadStage }}</div>
              </template>
              <div v-else class="upload-sub">支持 PDF / Word(.docx) / Markdown / TXT，单文件 ≤ 20MB。</div>
            </div>
          </n-upload-dragger>
        </n-upload>
      </n-card>

      <n-card :bordered="false" class="panel">
        <div class="panel-title">文档列表</div>
        <n-table :single-line="false" :bordered="false">
          <thead>
            <tr>
              <th>文档名称</th>
              <th>类型</th>
              <th>大小</th>
              <th>切片数</th>
              <th>状态</th>
              <th>上传时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in pagedDocs" :key="row.id">
              <td>{{ row.title }}</td>
              <td><n-tag round>{{ row.fileType?.toUpperCase() }}</n-tag></td>
              <td>{{ fmtSize(row.fileSize) }}</td>
              <td>{{ row.chunkCount }}</td>
              <td><n-tag :type="statusTag(row.status).type" round>{{ statusTag(row.status).text }}</n-tag></td>
              <td>{{ row.createTime }}</td>
              <td>
                <n-space>
                  <n-button size="small" secondary @click="onDownload(row)">下载</n-button>
                  <n-button size="small" secondary type="error" :loading="deletingId === row.id" :disabled="deletingId !== null && deletingId !== row.id" @click="onDelete(row)">删除</n-button>
                </n-space>
              </td>
            </tr>
          </tbody>
        </n-table>
        <n-empty v-if="!loading && docs.length === 0" description="该课程暂无资料，上传一份试试" />
        <div v-if="docs.length > pageSize" class="pagination-wrap">
          <n-pagination v-model:page="currentPage" :page-size="pageSize" :item-count="docs.length" />
        </div>
      </n-card>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { NAlert, NButton, NCard, NEmpty, NInput, NPagination, NProgress, NSpace, NSelect, NTable, NTag, NUpload, NUploadDragger, createDiscreteApi } from 'naive-ui'
import { listCourses } from '../api/course'
import { uploadKnowledge, listKnowledge, deleteKnowledge, searchKnowledge } from '../api/knowledge'
import { resolveApplicationUrl } from '../utils/url'

const { message, dialog } = createDiscreteApi(['message', 'dialog'])
const courses = ref([])
const selectedCourse = ref(null)
const docs = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = 10
const searchQuery = ref('')
const searchResults = ref([])
const searchLoading = ref(false)
const showSearchResults = ref(false)
const uploadRef = ref(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const deletingId = ref(null)
let docsRequestToken = 0
let searchRequestToken = 0
const uploadStage = ref('正在上传文件…')

const courseOptions = computed(() => courses.value.map((c) => ({ label: c.courseName, value: c.id })))
const selectedCourseName = computed(() => courses.value.find((c) => c.id === selectedCourse.value)?.courseName || '')
const pagedDocs = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return docs.value.slice(start, start + pageSize)
})

async function loadCourses() {
  courses.value = await listCourses()
}
async function loadDocs() {
  if (!selectedCourse.value) return
  const requestToken = ++docsRequestToken
  loading.value = true
  try {
    const result = await listKnowledge(selectedCourse.value)
    if (requestToken === docsRequestToken) {
      docs.value = result || []
      const maxPage = Math.max(1, Math.ceil(docs.value.length / pageSize))
      currentPage.value = Math.min(currentPage.value, maxPage)
    }
  } catch (error) {
    if (requestToken === docsRequestToken) message.error(error?.message || '课程资料加载失败，请重试')
  } finally {
    if (requestToken === docsRequestToken) loading.value = false
  }
}
function onCourseChange() {
  currentPage.value = 1
  showSearchResults.value = false
  searchQuery.value = ''
  loadDocs()
}
async function onSearch() {
  if (!searchQuery.value.trim()) {
    message.warning('请输入搜索关键词')
    return
  }
  if (!selectedCourse.value) {
    message.warning('请先选择课程')
    return
  }
  const requestToken = ++searchRequestToken
  searchLoading.value = true
  showSearchResults.value = true
  try {
    const result = await searchKnowledge(selectedCourse.value, searchQuery.value.trim(), 10)
    if (requestToken === searchRequestToken) searchResults.value = result || []
  } catch (error) {
    if (requestToken === searchRequestToken) message.error(error?.message || '知识库检索失败，请重试')
  } finally {
    if (requestToken === searchRequestToken) searchLoading.value = false
  }
}
function beforeUpload({ file }) {
  const okExt = ['pdf', 'docx', 'md', 'txt']
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (!okExt.includes(ext)) {
    message.error('仅支持 pdf / docx / md / txt 格式')
    return false
  }
  if (file.file?.size > 20 * 1024 * 1024) {
    message.error('文件不能超过 20MB')
    return false
  }
  if (!selectedCourse.value) {
    message.warning('请先选择课程')
    return false
  }
  return true
}
async function customUpload({ file, onError, onFinish }) {
  uploading.value = true
  uploadProgress.value = 0
  uploadStage.value = '正在上传文件…'
  try {
    const data = await uploadKnowledge(selectedCourse.value, file.file, (percentage) => {
      uploadProgress.value = percentage
      if (percentage >= 100) {
        uploadStage.value = '文件已上传，正在解析并建立可检索切片…'
      }
    })
    uploadProgress.value = 100
    message.success(`上传成功，已生成 ${data?.chunkCount ?? 0} 个可检索切片，可供智能问答调用`)
    onFinish()
    uploadRef.value?.clear()
    loadDocs()
  } catch (e) {
    onError?.(e)
    uploadStage.value = '上传失败，请检查网络或文件格式后重试'
    message.error(e?.message || '课程资料上传失败')
    uploadRef.value?.clear()
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}
function onDownload(row) {
  if (row.filePath) window.open(resolveApplicationUrl(row.filePath), '_blank')
}
async function onDelete(row) {
  const ok = await dialog.warning({
    title: '提示',
    content: `确定删除资料「${row.title}」吗？`,
    positiveText: '确定',
    negativeText: '取消'
  })
  if (!ok) return
  if (deletingId.value !== null) return
  deletingId.value = row.id
  try {
    await deleteKnowledge(row.id)
  } catch (error) {
    message.error(error?.message || '删除失败，请重试')
    deletingId.value = null
    return
  }
  deletingId.value = null
  message.success('已删除')
  loadDocs()
}
function statusTag(status) {
  if (status === 1) return { type: 'success', text: '已解析' }
  if (status === 0) return { type: 'warning', text: '解析中' }
  return { type: 'error', text: '失败' }
}
function fmtSize(bytes) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}
onMounted(loadCourses)
</script>

<style scoped>
.knowledge-workbench { display: flex; flex-direction: column; gap: 16px; }
.hero { display: grid; grid-template-columns: 1.4fr 0.9fr; gap: 16px; }
.eyebrow { color: #4cb6c2; font-size: 12px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.hero h2 { margin: 8px 0 10px; font-size: 28px; color: #16313b; }
.hero p { margin: 0; max-width: 60ch; color: #5f6b73; line-height: 1.7; }
.hero-card, .panel, .control-card { border-radius: 18px; box-shadow: 0 16px 40px rgba(48, 102, 107, 0.08); }
.hero-card { background: linear-gradient(135deg, #f4fbfb 0%, #eaf8f7 100%); }
.hero-card-title { color: #5f6b73; font-size: 12px; margin-bottom: 8px; }
.hero-card-value { font-size: 22px; font-weight: 700; color: #18323d; margin-bottom: 6px; }
.hero-card-sub { color: #5f6b73; font-size: 13px; line-height: 1.6; }
.panel { background: rgba(255,255,255,0.9); }
.panel-title { font-weight: 700; color: #18323d; margin-bottom: 12px; }
.search-results { margin-top: 16px; display: flex; flex-direction: column; gap: 10px; }
.chunk-card { background: #f7fbfb; border: 1px solid #e4f4f2; border-radius: 14px; padding: 14px; }
.chunk-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.chunk-index { color: #8a98a1; font-size: 12px; }
.chunk-content { color: #1f2937; line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
.upload-box { text-align: center; padding: 18px 10px; }
.upload-title { font-weight: 700; color: #18323d; }
.upload-sub { margin-top: 6px; color: #5f6b73; font-size: 13px; }
.pagination-wrap { display: flex; justify-content: flex-end; padding-top: 16px; }
@media (max-width: 900px) { .hero { grid-template-columns: 1fr; } }
</style>
