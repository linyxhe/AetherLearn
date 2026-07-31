<template>
  <div class="config-page">
    <div class="hero">
      <div>
        <div class="eyebrow">系统配置</div>
        <h2>AI 模型连接</h2>
        <p>模型、接口地址和 API Key 保存后立即生效，不需要重启后端。保存并测试会发起一次真实模型请求。</p>
      </div>
      <div class="status-card" :class="{ active: settings.available }">
        <span class="status-dot"></span>
        <div>
          <div class="status-title">{{ settings.available ? '配置完整' : settings.enabled ? '等待补全' : '服务已关闭' }}</div>
          <div class="status-copy">{{ settings.statusMessage }}</div>
        </div>
      </div>
    </div>

    <el-alert
      v-if="testResult"
      :title="testResult.title"
      :description="testResult.description"
      :type="testResult.type"
      show-icon
      :closable="false"
      class="result-alert"
    />

    <div class="config-card" v-loading="loading">
      <div class="section-head">
        <div>
          <h3>连接参数</h3>
          <p>支持 OpenAI 兼容接口，例如 DeepSeek、OpenRouter 或其他兼容服务。</p>
        </div>
        <el-switch
          v-model="form.enabled"
          inline-prompt
          active-text="启用"
          inactive-text="关闭"
          style="--el-switch-on-color: #42b5bb"
        />
      </div>

      <el-form label-position="top" class="config-form" @submit.prevent>
        <el-form-item label="API 接口地址" required>
          <el-input
            v-model="form.baseUrl"
            :disabled="!form.enabled"
            placeholder="https://api.deepseek.com/v1"
            clearable
          />
          <div class="field-hint">必须以 http:// 或 https:// 开头，并指向 OpenAI 兼容接口根地址。</div>
        </el-form-item>

        <el-form-item label="模型名称" required>
          <el-input
            v-model="form.modelName"
            :disabled="!form.enabled"
            placeholder="例如：deepseek-chat"
            clearable
          />
          <div class="field-hint">填写服务商实际开放的模型标识，不是页面展示名称。</div>
        </el-form-item>

        <el-form-item label="API Key" required>
          <el-input
            v-model="form.apiKey"
            :disabled="!form.enabled"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="apiKeyPlaceholder"
          />
          <div class="field-hint">
            {{ settings.apiKeyConfigured ? '已配置密钥。留空会保留原值，后端不会向浏览器回传密钥。' : '尚未配置密钥，请输入后保存。' }}
          </div>
        </el-form-item>
      </el-form>

      <div class="actions">
        <div class="action-hint">“保存并测试”通常在 5–30 秒内返回；超时、密钥无效、模型不存在会给出对应提示。</div>
        <div class="button-group">
          <el-button :loading="saving" @click="onSave">保存配置</el-button>
          <el-button
            type="primary"
            :disabled="!form.enabled"
            :loading="testing"
            @click="onSaveAndTest"
          >
            保存并测试
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiSettings, testAiConnection, updateAiSettings } from '../api/config'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const testResult = ref(null)
const form = reactive({
  enabled: false,
  baseUrl: '',
  modelName: '',
  apiKey: ''
})
const settings = reactive({
  enabled: false,
  baseUrl: '',
  modelName: '',
  apiKeyConfigured: false,
  available: false,
  statusMessage: '正在读取配置…'
})

const apiKeyPlaceholder = computed(() =>
  settings.apiKeyConfigured ? '留空表示继续使用当前 API Key' : '请输入 API Key'
)

// 加载不含密钥原文的 AI 配置状态。
async function loadSettings() {
  loading.value = true
  try {
    const data = await getAiSettings()
    applySettings(data)
  } finally {
    loading.value = false
  }
}

// 将后端配置视图同步到表单与状态卡片。
function applySettings(data) {
  Object.assign(settings, data || {})
  form.enabled = Boolean(data?.enabled)
  form.baseUrl = data?.baseUrl || ''
  form.modelName = data?.modelName || ''
  form.apiKey = ''
}

// 在请求前给出可直接修正的必填项提示。
function validateForm() {
  if (!form.enabled) return true
  if (!form.baseUrl.trim()) {
    ElMessage.warning('请输入 API 接口地址')
    return false
  }
  if (!/^https?:\/\//i.test(form.baseUrl.trim())) {
    ElMessage.warning('API 接口地址必须以 http:// 或 https:// 开头')
    return false
  }
  if (!form.modelName.trim()) {
    ElMessage.warning('请输入模型名称')
    return false
  }
  if (!form.apiKey.trim() && !settings.apiKeyConfigured) {
    ElMessage.warning('请输入 API Key')
    return false
  }
  return true
}

// 保存表单；API Key 留空时由后端保留已有密钥。
async function saveSettings(showSuccess = true) {
  if (!validateForm()) return false
  const data = await updateAiSettings({
    enabled: form.enabled,
    baseUrl: form.baseUrl.trim(),
    modelName: form.modelName.trim(),
    apiKey: form.apiKey.trim()
  })
  applySettings(data)
  if (showSuccess) ElMessage.success('AI 配置已保存并立即生效')
  return true
}

// 仅保存配置。
async function onSave() {
  saving.value = true
  testResult.value = null
  try {
    await saveSettings()
  } finally {
    saving.value = false
  }
}

// 保存后发起一次真实连接测试，并展示可操作的结果。
async function onSaveAndTest() {
  testing.value = true
  testResult.value = null
  try {
    const saved = await saveSettings(false)
    if (!saved) return
    const data = await testAiConnection()
    testResult.value = {
      type: 'success',
      title: '模型连接成功',
      description: `模型响应：${data?.response || '连接成功'}`
    }
    ElMessage.success('模型连接成功')
  } catch (error) {
    testResult.value = {
      type: 'error',
      title: '模型连接失败',
      description: error?.message || '请根据页面提示检查配置后重试'
    }
  } finally {
    testing.value = false
  }
}

onMounted(loadSettings)
</script>

<style scoped>
.config-page { display: flex; flex-direction: column; gap: 16px; }
.hero { display: grid; grid-template-columns: 1.4fr 0.9fr; gap: 16px; align-items: stretch; }
.eyebrow { color: #4cb6c2; font-size: 12px; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; }
.hero h2 { margin: 8px 0 10px; font-size: 30px; color: #16313b; }
.hero p { margin: 0; color: #5f6b73; max-width: 62ch; line-height: 1.7; }
.status-card, .config-card {
  border: 1px solid rgba(66, 181, 187, 0.14);
  border-radius: 18px;
  background: rgba(255,255,255,0.92);
  box-shadow: 0 16px 40px rgba(48, 102, 107, 0.08);
}
.status-card { display: flex; align-items: flex-start; gap: 12px; padding: 22px; }
.status-dot { width: 10px; height: 10px; margin-top: 5px; border-radius: 50%; background: #a8b2b8; box-shadow: 0 0 0 6px rgba(168,178,184,0.13); flex: none; }
.status-card.active .status-dot { background: #42b5bb; box-shadow: 0 0 0 6px rgba(66,181,187,0.14); }
.status-title { color: #18323d; font-weight: 700; margin-bottom: 6px; }
.status-copy { color: #6b7280; font-size: 12px; line-height: 1.6; }
.result-alert { border-radius: 14px; }
.config-card { padding: 24px; }
.section-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; padding-bottom: 18px; border-bottom: 1px solid rgba(66,181,187,0.12); }
.section-head h3 { margin: 0 0 6px; color: #18323d; }
.section-head p { margin: 0; color: #6b7280; font-size: 13px; }
.config-form { max-width: 760px; padding-top: 22px; }
.field-hint { margin-top: 7px; color: #7a858c; font-size: 12px; line-height: 1.5; }
.actions { display: flex; justify-content: space-between; align-items: center; gap: 20px; padding-top: 18px; border-top: 1px solid rgba(66,181,187,0.12); }
.action-hint { color: #6b7280; font-size: 12px; line-height: 1.6; }
.button-group { display: flex; gap: 10px; flex: none; }
@media (max-width: 900px) {
  .hero { grid-template-columns: 1fr; }
  .actions { align-items: stretch; flex-direction: column; }
  .button-group { justify-content: flex-end; }
}
</style>
