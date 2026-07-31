<template>
  <!-- 统一文件上传组件（F-FILE-01） -->
  <el-upload
    class="upload-drag"
    drag
    :action="undefined"
    :http-request="customUpload"
    :show-file-list="false"
    :accept="accept"
  >
    <el-icon class="el-icon--upload"><upload-filled /></el-icon>
    <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
    <template #tip v-if="modelValue">
      <div class="preview">
        <el-image v-if="isImage" :src="resolveUrl(modelValue)" :preview-src-list="[resolveUrl(modelValue)]" style="width: 80px; height: 80px" fit="cover" />
        <el-link type="primary" :href="resolveUrl(modelValue)" target="_blank" v-else>{{ modelValue }}</el-link>
      </div>
    </template>
  </el-upload>
</template>

<script setup>
import { computed } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uploadFile } from '../api/file'

const props = defineProps({
  // 已上传文件访问路径（v-model）
  modelValue: { type: String, default: '' },
  // 业务分桶：avatar / course / knowledge / answer / export
  bizType: { type: String, required: true },
  accept: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'upload-success'])

const isImage = computed(() => /\.(png|jpe?g|gif|webp)$/i.test(props.modelValue || ''))

// 将 /uploads/... 的相对路径补全为可访问地址
function resolveUrl(path) {
  return path
}

// 覆盖默认上传行为，走我们自己的接口
async function customUpload({ file }) {
  try {
    const data = await uploadFile(file, props.bizType)
    emit('update:modelValue', data.url)
    emit('upload-success', data.url)
    ElMessage.success('上传成功')
  } catch (e) {
    // 错误由拦截器提示
  }
}
</script>

<style scoped>
.upload-drag { width: 100%; }
.preview {
  margin-top: 12px;
  padding: 8px;
  background: var(--surface, #f2fbfa);
  border-radius: var(--radius-sm, 8px);
}
</style>
