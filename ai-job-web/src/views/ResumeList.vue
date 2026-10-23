<template>
  <div class="list-page">
    <div class="page-card toolbar">
      <h2 class="page-title">我的简历</h2>
      <el-button type="primary" @click="showUpload = true">上传简历</el-button>
    </div>

    <div class="page-card">
      <el-table v-loading="loading" :data="resumes" stripe empty-text="暂无简历，请先上传">
        <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="100" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">
            {{ formatSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" min-width="160" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="320" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :loading="actionId === row.id && actionType === 'parse'"
              :disabled="Boolean(actionId)"
              @click="onParse(row.id)"
            >
              解析
            </el-button>
            <el-button
              link
              type="success"
              :loading="actionId === row.id && actionType === 'analyze'"
              :disabled="Boolean(actionId)"
              @click="onAnalyze(row.id)"
            >
              {{ actionId === row.id && actionType === 'analyze' ? '正在分析...' : 'AI分析' }}
            </el-button>
            <el-button link type="warning" @click="goAnalysis(row.id)">查看分析</el-button>
            <el-button
              link
              type="danger"
              :loading="actionId === row.id && actionType === 'delete'"
              :disabled="Boolean(actionId)"
              @click="onDelete(row.id)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showUpload" title="上传简历" width="520px" destroy-on-close>
      <ResumeUpload @success="onUploadSuccess" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ResumeUpload from '@/components/ResumeUpload.vue'
import { deleteResume, listResumes, parseResume } from '@/api/resume'
import { analyzeResume } from '@/api/ai'
import type { Resume } from '@/types/resume'

const router = useRouter()
const resumes = ref<Resume[]>([])
const loading = ref(false)
const showUpload = ref(false)
const actionId = ref<number | null>(null)
const actionType = ref<'parse' | 'analyze' | 'delete' | null>(null)

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function statusType(status: string) {
  switch (status) {
    case 'PARSED':
      return 'success'
    case 'PARSE_FAILED':
      return 'danger'
    case 'PARSING':
      return 'warning'
    default:
      return 'info'
  }
}

async function loadList() {
  loading.value = true
  try {
    resumes.value = await listResumes()
  } catch {
    resumes.value = []
  } finally {
    loading.value = false
  }
}

function onUploadSuccess() {
  showUpload.value = false
  void loadList()
}

async function onParse(id: number) {
  actionId.value = id
  actionType.value = 'parse'
  try {
    await parseResume(id)
    ElMessage.success('解析成功')
    await loadList()
  } catch {
    // handled
  } finally {
    actionId.value = null
    actionType.value = null
  }
}

async function onAnalyze(id: number) {
  actionId.value = id
  actionType.value = 'analyze'
  try {
    await analyzeResume(id)
    ElMessage.success('AI 分析完成')
    await router.push(`/resumes/${id}/analysis`)
  } catch {
    // handled
  } finally {
    actionId.value = null
    actionType.value = null
  }
}

function goAnalysis(id: number) {
  void router.push(`/resumes/${id}/analysis`)
}

async function onDelete(id: number) {
  try {
    await ElMessageBox.confirm('确定删除这份简历吗？', '提示', { type: 'warning' })
  } catch {
    return
  }

  actionId.value = id
  actionType.value = 'delete'
  try {
    await deleteResume(id)
    ElMessage.success('删除成功')
    await loadList()
  } catch {
    // handled
  } finally {
    actionId.value = null
    actionType.value = null
  }
}

onMounted(() => {
  void loadList()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.toolbar .page-title {
  margin: 0;
}

@media (max-width: 768px) {
  .toolbar {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
