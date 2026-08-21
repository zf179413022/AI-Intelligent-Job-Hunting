<template>
  <div class="list-page">
    <div class="page-card toolbar">
      <div>
        <h2 class="page-title">知识库文档</h2>
        <p class="hint">上传 PDF / Markdown → 解析 → 向量入库（Chroma）。入库完成后可去「AI 知识问答」提问。</p>
      </div>
      <div class="toolbar-actions">
        <el-button @click="$router.push('/knowledge/ask')">去问答</el-button>
        <el-button type="primary" @click="showUpload = true">上传文档</el-button>
      </div>
    </div>

    <div class="page-card">
      <el-table v-loading="loading" :data="documents" stripe empty-text="暂无知识库文档，请先上传 PDF 或 Markdown">
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="80" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">
            {{ formatSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column label="页数" width="80">
          <template #default="{ row }">
            {{ row.pageCount ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="Chunks" width="90">
          <template #default="{ row }">
            {{ row.chunkCount ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="160" />
        <el-table-column label="操作" min-width="340" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :loading="actionId === row.id && actionType === 'parse'"
              :disabled="Boolean(actionId)"
              @click="onParse(row.id)"
            >
              重新解析
            </el-button>
            <el-button
              link
              type="success"
              :loading="actionId === row.id && actionType === 'ingest'"
              :disabled="Boolean(actionId) || !canIngest(row.status)"
              @click="onIngest(row.id)"
            >
              {{ actionId === row.id && actionType === 'ingest' ? '入库中...' : '向量入库' }}
            </el-button>
            <el-button
              link
              type="warning"
              :disabled="Boolean(actionId)"
              @click="onViewChunks(row)"
            >
              查看 Chunk
            </el-button>
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

      <el-alert
        v-if="lastError"
        class="error-alert"
        type="error"
        :title="lastError"
        show-icon
        :closable="true"
        @close="lastError = ''"
      />
    </div>

    <el-dialog v-model="showUpload" title="上传知识库文档" width="520px" destroy-on-close>
      <KnowledgeUpload @success="onUploadSuccess" />
    </el-dialog>

    <el-drawer
      v-model="chunkDrawerVisible"
      :title="chunkDrawerTitle"
      size="52%"
      destroy-on-close
    >
      <el-table
        v-loading="chunkLoading"
        :data="chunks"
        stripe
        height="100%"
        empty-text="暂无分块，请先执行向量入库"
      >
        <el-table-column prop="chunkIndex" label="#" width="60" />
        <el-table-column prop="id" label="chunkId" width="90" />
        <el-table-column prop="vectorId" label="vectorId" width="120" show-overflow-tooltip />
        <el-table-column prop="tokenEstimate" label="字数" width="80" />
        <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import KnowledgeUpload from '@/components/KnowledgeUpload.vue'
import {
  deleteKnowledgeDocument,
  ingestKnowledgeDocument,
  listKnowledgeChunks,
  listKnowledgeDocuments,
  parseKnowledgeDocument,
} from '@/api/knowledge'
import type { KnowledgeChunk, KnowledgeDocument } from '@/types/knowledge'

const documents = ref<KnowledgeDocument[]>([])
const loading = ref(false)
const showUpload = ref(false)
const actionId = ref<number | null>(null)
const actionType = ref<'parse' | 'ingest' | 'delete' | null>(null)
const lastError = ref('')

const chunkDrawerVisible = ref(false)
const chunkLoading = ref(false)
const chunks = ref<KnowledgeChunk[]>([])
const activeDoc = ref<KnowledgeDocument | null>(null)

const chunkDrawerTitle = computed(() => {
  if (!activeDoc.value) return '分块列表'
  return `分块列表 · ${activeDoc.value.title}（${chunks.value.length}）`
})

function formatSize(size: number | null) {
  if (size == null) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function statusType(status: string) {
  switch (status) {
    case 'READY':
    case 'EMBEDDED':
      return 'success'
    case 'PARSED':
    case 'CHUNKED':
      return 'primary'
    case 'UPLOADED':
      return 'info'
    case 'FAILED':
      return 'danger'
    default:
      return 'warning'
  }
}

function canIngest(status: string) {
  return ['PARSED', 'CHUNKED', 'EMBEDDED', 'READY', 'FAILED'].includes(status)
}

async function loadList() {
  loading.value = true
  try {
    documents.value = await listKnowledgeDocuments()
  } catch {
    documents.value = []
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
  lastError.value = ''
  try {
    const doc = await parseKnowledgeDocument(id)
    if (doc.status === 'FAILED') {
      lastError.value = doc.errorMessage || '解析失败'
      ElMessage.error('解析失败')
    } else {
      ElMessage.success(`解析完成：${doc.status}`)
    }
    await loadList()
  } catch {
    // 拦截器已提示
  } finally {
    actionId.value = null
    actionType.value = null
  }
}

async function onIngest(id: number) {
  actionId.value = id
  actionType.value = 'ingest'
  lastError.value = ''
  try {
    const result = await ingestKnowledgeDocument(id)
    ElMessage.success(
      `入库成功：${result.chunkCount} chunks → Chroma ${result.chromaVectorCount} 向量`,
    )
    await loadList()
  } catch (e: unknown) {
    const msg =
      (e as { response?: { data?: { message?: string; error?: string } } })?.response?.data
        ?.message ||
      (e as { response?: { data?: { error?: string } } })?.response?.data?.error ||
      ''
    if (msg) lastError.value = String(msg)
  } finally {
    actionId.value = null
    actionType.value = null
  }
}

async function onViewChunks(doc: KnowledgeDocument) {
  activeDoc.value = doc
  chunkDrawerVisible.value = true
  chunkLoading.value = true
  chunks.value = []
  try {
    chunks.value = await listKnowledgeChunks(doc.id)
  } catch {
    chunks.value = []
  } finally {
    chunkLoading.value = false
  }
}

async function onDelete(id: number) {
  try {
    await ElMessageBox.confirm(
      '删除将同时移除 MySQL Chunk、Chroma 向量与本地文件，确认继续？',
      '删除知识库文档',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  actionId.value = id
  actionType.value = 'delete'
  try {
    await deleteKnowledgeDocument(id)
    ElMessage.success('删除成功')
    await loadList()
  } catch {
    // 拦截器已提示
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
  gap: 16px;
  margin-bottom: 16px;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.hint {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.error-alert {
  margin-top: 16px;
}

@media (max-width: 768px) {
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
