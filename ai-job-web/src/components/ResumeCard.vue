<template>
  <el-card shadow="hover" class="resume-card">
    <div class="title">{{ resume.fileName }}</div>
    <div class="meta">
      <el-tag size="small" :type="statusType">{{ resume.status }}</el-tag>
      <span>{{ formatSize(resume.fileSize) }}</span>
      <span>{{ resume.createdAt }}</span>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Resume } from '@/types/resume'

const props = defineProps<{
  resume: Resume
}>()

const statusType = computed(() => {
  switch (props.resume.status) {
    case 'PARSED':
      return 'success'
    case 'PARSE_FAILED':
      return 'danger'
    case 'PARSING':
      return 'warning'
    default:
      return 'info'
  }
})

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped>
.resume-card {
  margin-bottom: 12px;
}

.title {
  font-weight: 600;
  margin-bottom: 8px;
  word-break: break-all;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--text-secondary);
  font-size: 13px;
  align-items: center;
}
</style>
