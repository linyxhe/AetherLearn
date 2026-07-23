<template>
  <div>
    <div class="head">
      <h2 class="page-title">系统配置</h2>
    </div>

    <div class="aeth-card">
      <el-table :data="configs" v-loading="loading" stripe>
        <el-table-column prop="configKey" label="配置键" min-width="180" />
        <el-table-column prop="configValue" label="配置值" min-width="200" />
        <el-table-column prop="remark" label="说明" min-width="250" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="onEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && configs.length === 0" description="暂无配置项" />
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" title="编辑配置" width="480px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="配置键">
          <el-input v-model="editForm.configKey" disabled />
        </el-form-item>
        <el-form-item label="配置值">
          <el-input v-model="editForm.configValue" placeholder="请输入配置值" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editForm.remark" placeholder="请输入说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listConfigs, updateConfig } from '../api/config'

const configs = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const editForm = ref({ configKey: '', configValue: '', remark: '' })

// 加载配置列表
async function loadConfigs() {
  loading.value = true
  try {
    configs.value = await listConfigs()
  } finally {
    loading.value = false
  }
}

// 打开编辑弹窗
function onEdit(row) {
  editForm.value = { ...row }
  dialogVisible.value = true
}

// 保存配置
async function onSave() {
  saving.value = true
  try {
    await updateConfig(editForm.value.configKey, editForm.value.configValue, editForm.value.remark)
    ElMessage.success('配置已更新')
    dialogVisible.value = false
    loadConfigs()
  } finally {
    saving.value = false
  }
}

onMounted(loadConfigs)
</script>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-title { margin: 0; }
</style>
