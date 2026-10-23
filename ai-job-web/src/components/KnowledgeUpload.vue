<template>
  <div class="upload-wrap">
    <el-upload
      drag
      :auto-upload="false"
      :show-file-list="false"
      accept=".pdf,.md,application/pdf,text/markdown,text/x-markdown"
      :disabled="loading"
      :on-change="onChange"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">
        拖拽 PDF / Markdown 到此处，或 <em>点击上传</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          支持 PDF / Markdown（.md）：表格、代码块、脚注会按结构解析；扫描版 PDF 需先 OCR
        </div>
      </template>
    </el-upload>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { UploadFile } from 'element-plus'
import { ElMessage } from 'element-plus'
import { uploadKnowledgeDocument } from '@/api/knowledge'

const emit = defineEmits<{
  success: []
  duplicate: [documentId: number]
}>()

const loading = ref(false)

function extractExistingDocumentId(error: unknown): number | null {
  const data = (error as { response?: { data?: { existingDocumentId?: number } } })?.response
    ?.data
  const id = data?.existingDocumentId
  return typeof id === 'number' && id > 0 ? id : null
}

function isAllowed(name: string) {
  const lower = name.toLowerCase()
  return lower.endsWith('.pdf') || lower.endsWith('.md')
}

async function onChange(uploadFile: UploadFile) {
  const raw = uploadFile.raw
  if (!raw) return

  if (!isAllowed(raw.name)) {
    ElMessage.warning('仅支持 PDF / Markdown（.pdf / .md）')
    return
  }

  loading.value = true
  try {
    await uploadKnowledgeDocument(raw)
    ElMessage.success('上传成功，已自动解析')
    emit('success')
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } })?.response?.status
    const existingId = extractExistingDocumentId(error)
    if (status === 409 && existingId != null) {
      emit('duplicate', existingId)
    }
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
