<template>
  <div>
    <div class="head">
      <h2 class="page-title">用户管理</h2>
      <el-button type="primary" @click="openCreate">+ 新建用户</el-button>
    </div>

    <!-- 搜索与筛选 -->
    <div class="aeth-card filter-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索用户名/姓名"
        clearable
        style="width: 220px"
        @clear="load"
        @keyup.enter="load"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="roleFilter" placeholder="全部角色" clearable style="width: 130px" @change="load">
        <el-option label="管理员" :value="1" />
        <el-option label="教师" :value="2" />
        <el-option label="学生" :value="3" />
      </el-select>
      <el-button @click="load">查询</el-button>
    </div>

    <!-- 用户列表 -->
    <div class="aeth-card">
      <el-table :data="users" v-loading="loading" stripe>
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)">{{ roleName(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              size="small"
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="onToggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && users.length === 0" description="暂无用户数据" />

      <!-- 分页 -->
      <div class="pagination" v-if="total > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </div>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新建用户'" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="用户名" prop="username" required>
          <el-input v-model="form.username" placeholder="登录账号" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName" required>
          <el-input v-model="form.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="角色" prop="role" required>
          <el-select v-model="form.role" placeholder="请选择角色">
            <el-option label="管理员" :value="1" />
            <el-option label="教师" :value="2" />
            <el-option label="学生" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="选填" />
        </el-form-item>
        <el-form-item :label="isEdit ? '新密码' : '密码'">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="isEdit ? '留空则不修改' : '留空则使用默认密码 123456'"
            show-password
          />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getUserList, createUser, updateUser, deleteUser, updateUserStatus } from '../api/adminUser'

// 列表状态
const users = ref([])
const loading = ref(false)
const keyword = ref('')
const roleFilter = ref(null)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 表单状态
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const formRef = ref(null)
const form = reactive({
  username: '',
  realName: '',
  role: null,
  email: '',
  phone: '',
  password: ''
})

// 表单校验规则
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

// 角色显示
function roleName(role) {
  return { 1: '管理员', 2: '教师', 3: '学生' }[role] || '未知'
}
function roleTagType(role) {
  return { 1: 'danger', 2: 'primary', 3: 'success' }[role] || 'info'
}

// 加载用户列表
async function load() {
  loading.value = true
  try {
    const res = await getUserList({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
      role: roleFilter.value || undefined
    })
    users.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

// 重置表单
function resetForm() {
  Object.assign(form, { username: '', realName: '', role: null, email: '', phone: '', password: '' })
  editId.value = null
}

// 打开新建弹窗
function openCreate() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

// 打开编辑弹窗
function openEdit(row) {
  isEdit.value = true
  editId.value = row.id
  Object.assign(form, {
    username: row.username,
    realName: row.realName,
    role: row.role,
    email: row.email || '',
    phone: row.phone || '',
    password: ''
  })
  dialogVisible.value = true
}

// 保存（新建/编辑）
async function onSave() {
  // 表单校验
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    const data = { ...form }
    // 编辑时如果密码为空，不传密码字段
    if (isEdit.value && !data.password) {
      delete data.password
    }
    if (isEdit.value) {
      await updateUser(editId.value, data)
    } else {
      await createUser(data)
    }
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 删除用户
async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除用户「${row.realName}（${row.username}）」吗？此操作不可恢复。`, '警告', { type: 'error' })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  await load()
}

// 启用/禁用用户
async function onToggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 0 ? '禁用' : '启用'
  await ElMessageBox.confirm(`确定${action}用户「${row.realName}」吗？`, '提示', { type: 'warning' })
  await updateUserStatus(row.id, newStatus)
  ElMessage.success(`已${action}`)
  await load()
}

onMounted(load)
</script>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-title { margin: 0; }
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
