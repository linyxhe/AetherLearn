<template>
  <div>
    <div class="head">
      <h2 class="page-title">课程管理</h2>
      <el-button type="primary" @click="openCreate">+ 新建课程</el-button>
    </div>

    <!-- 课程列表（F-COURSE-02） -->
    <div class="aeth-card">
      <el-table :data="courses" v-loading="loading" stripe>
        <el-table-column prop="courseName" label="课程名称" min-width="140" />
        <el-table-column prop="courseCode" label="课程编号" width="120" />
        <el-table-column label="封面" width="90">
          <template #default="{ row }">
            <el-image v-if="row.cover" :src="row.cover" style="width: 56px; height: 36px" fit="cover" />
            <span v-else class="muted">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="简介" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '开课中' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && courses.length === 0" description="暂无课程，点击右上角新建" />
    </div>

    <!-- 新建/编辑对话框（F-COURSE-01） -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑课程' : '新建课程'" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="课程名称" required>
          <el-input v-model="form.courseName" placeholder="如：Java 程序设计" />
        </el-form-item>
        <el-form-item label="课程编号">
          <el-input v-model="form.courseCode" placeholder="如：CS201" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="封面">
          <!-- 封面上传：bizType=course，回显路径 -->
          <UploadFile v-model="form.cover" biz-type="course" accept="image/*" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="statusOn" active-text="开课" inactive-text="下架" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCourses, saveCourse, deleteCourse } from '../api/course'
import UploadFile from '../components/UploadFile.vue'

const courses = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)

const form = reactive({ id: null, courseName: '', courseCode: '', description: '', cover: '', status: 1 })
const statusOn = computed({
  get: () => form.status === 1,
  set: (v) => (form.status = v ? 1 : 0)
})

// 加载课程列表（按角色自动区分）
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
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function onSave() {
  if (!form.courseName) {
    ElMessage.warning('请填写课程名称')
    return
  }
  saving.value = true
  try {
    await saveCourse({ ...form })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除课程「${row.courseName}」吗？`, '提示', { type: 'warning' })
  await deleteCourse(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.page-title { margin: 0; }
.muted { color: var(--text-2); }
</style>
