<template>
  <div class="upload-wrap">
    <el-upload
      drag
      :auto-upload="false"
      :show-file-list="false"
      accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      :disabled="loading"
      :on-change="onChange"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">
        拖拽文件到此处，或 <em>点击上传</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">仅支持 PDF / DOCX</div>
      </template>
    </el-upload>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { UploadFile } from 'element-plus'
import { ElMessage } from 'element-plus'
import { uploadResume } from '@/api/resume'

const emit = defineEmits<{
  success: []
}>()

const loading = ref(false)

async function onChange(uploadFile: UploadFile) {
  const raw = uploadFile.raw
  if (!raw) return

  const name = raw.name.toLowerCase()
  if (!name.endsWith('.pdf') && !name.endsWith('.docx')) {
    ElMessage.warning('仅支持 PDF 或 DOCX 文件')
    return
  }

  loading.value = true
  try {
    await uploadResume(raw)
    ElMessage.success('上传成功')
    emit('success')
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.upload-wrap {
  width: 100%;
}
</style>
