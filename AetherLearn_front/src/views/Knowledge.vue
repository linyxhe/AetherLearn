<template>
  <div>
    <div class="head">
      <h2 class="page-title">课程知识库</h2>
      <div class="head-right">
        <el-select v-model="selectedCourse" placeholder="选择课程" style="width: 220px" @change="onCourseChange">
          <el-option v-for="c in courses" :key="c.id" :label="c.courseName" :value="c.id" />
        </el-select>
      </div>
    </div>

    <el-alert
      v-if="!selectedCourse"
      type="info"
      :closable="false"
      show-icon
      title="请先在右上角选择一门课程，再上传课程资料"
      style="margin-bottom: 14px"
    />

    <div v-else>
      <!-- 上传区（F-KB-02 文档上传与解析） -->
      <div class="aeth-card upload-card">
        <el-upload
          drag
          :auto-upload="true"
          :show-file-list="false"
          :before-upload="beforeUpload"
          :http-request="customUpload"
          accept=".pdf,.docx,.md,.txt"
        >
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            将课程资料拖到此处，或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="tip">支持 PDF / Word(.docx) / Markdown / TXT，单文件 ≤ 20MB，上传后自动解析切片。</div>
          </template>
        </el-upload>
      </div>

      <!-- 文档列表（F-KB-01） -->
      <div class="aeth-card">
        <el-table :data="docs" v-loading="loading" stripe>
          <el-table-column prop="title" label="文档名称" min-width="200" show-overflow-tooltip />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag>{{ row.fileType?.toUpperCase() }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="110">
            <template #default="{ row }">{{ fmtSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="切片数" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status).type">{{ statusTag(row.status).text }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="上传时间" min-width="170" />
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="onDownload(row)">下载</el-button>
              <el-button size="small" link type="danger" @click="onDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && docs.length === 0" description="该课程暂无资料，上传一份试试" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { listCourses } from '../api/course'
import { uploadKnowledge, listKnowledge, deleteKnowledge } from '../api/knowledge'

const courses = ref([])
const selectedCourse = ref(null)
const docs = ref([])
const loading = ref(false)

// 加载教师/管理员管辖课程
async function loadCourses() {
  courses.value = await listCourses()
}

// 切换课程 → 重新拉取文档
function onCourseChange() {
  loadDocs()
}

// 列举知识文档
async function loadDocs() {
  if (!selectedCourse.value) return
  loading.value = true
  try {
    docs.value = await listKnowledge(selectedCourse.value)
  } finally {
    loading.value = false
  }
}

// 上传前校验类型与大小
function beforeUpload(file) {
  const okExt = ['pdf', 'docx', 'md', 'txt']
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (!okExt.includes(ext)) {
    ElMessage.error('仅支持 pdf / docx / md / txt 格式')
    return false
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('文件不能超过 20MB')
    return false
  }
  if (!selectedCourse.value) {
    ElMessage.warning('请先选择课程')
    return false
  }
  return true
}

// 自定义上传：复用带 token 的 request 工具
async function customUpload(options) {
  try {
    const data = await uploadKnowledge(selectedCourse.value, options.file)
    ElMessage.success(`上传成功，已解析 ${data?.chunkCount ?? 0} 个切片`)
    options.onSuccess(data)
    loadDocs()
  } catch (e) {
    // request 拦截器已提示错误
    options.onError(e)
  }
}

// 下载原文件（后端静态映射 /uploads/**）
function onDownload(row) {
  if (row.filePath) window.open(row.filePath, '_blank')
}

// 删除文档
async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除资料「${row.title}」吗？`, '提示', { type: 'warning' })
  await deleteKnowledge(row.id)
  ElMessage.success('已删除')
  loadDocs()
}

// 状态标签映射
function statusTag(status) {
  if (status === 1) return { type: 'success', text: '已解析' }
  if (status === 0) return { type: 'warning', text: '解析中' }
  return { type: 'danger', text: '失败' }
}

// 文件大小格式化
function fmtSize(bytes) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

onMounted(loadCourses)
</script>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.page-title { margin: 0; }
.upload-card { margin-bottom: 14px; }
.upload-icon { font-size: 48px; color: var(--brand-1); }
.tip { color: var(--text-2); font-size: 12px; margin-top: 8px; }
</style>
