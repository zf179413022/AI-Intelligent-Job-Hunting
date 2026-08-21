<template>
  <div class="interview-list-page">
    <div class="page-card header">
      <div>
        <h2 class="page-title">AI 模拟面试</h2>
        <p class="sub">选择已解析简历与岗位，开启多轮 AI 追问面试</p>
      </div>
    </div>

    <div class="page-card form-card">
      <el-form label-position="top">
        <el-form-item label="选择简历" required>
          <el-select
            v-model="form.resumeId"
            placeholder="请选择已解析的简历"
            style="width: 100%"
            filterable
          >
            <el-option
              v-for="item in parsedResumes"
              :key="item.id"
              :label="`${item.fileName} (#${item.id})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="面试岗位" required>
          <el-input
            v-model="form.position"
            maxlength="200"
            show-word-limit
            placeholder="例如：Java开发工程师"
          />
        </el-form-item>

        <el-button type="primary" :loading="starting" @click="onStart">
          {{ starting ? '正在创建并出题...' : '开始面试' }}
        </el-button>
      </el-form>
    </div>

    <div class="page-card">
      <h3>历史面试</h3>
      <el-table v-loading="loading" :data="records" stripe empty-text="暂无面试记录">
        <el-table-column prop="position" label="岗位" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="得分" width="90">
          <template #default="{ row }">
            {{ row.score == null ? '-' : row.score }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'WAITING' || row.status === 'RUNNING'"
              link
              type="primary"
              @click="goRoom(row)"
            >
              进入面试
            </el-button>
            <el-button
              v-if="row.status === 'COMPLETED'"
              link
              type="success"
              @click="router.push(`/interviews/${row.id}/report`)"
            >
              查看报告
            </el-button>
            <el-button
              link
              type="danger"
              :loading="deletingId === row.id"
              @click="onDelete(row.id)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listResumes } from '@/api/resume'
import {
  createInterview,
  deleteInterview,
  listInterviews,
  startInterview,
} from '@/api/interview'
import {
  statusLabel,
  statusTagType,
  type Interview,
} from '@/types/interview'
import type { Resume } from '@/types/resume'

const router = useRouter()
const resumes = ref<Resume[]>([])
const records = ref<Interview[]>([])
const loading = ref(false)
const starting = ref(false)
const deletingId = ref<number | null>(null)

const form = reactive({
  resumeId: undefined as number | undefined,
  position: 'Java开发工程师',
})

const parsedResumes = computed(() =>
  resumes.value.filter(
    (item) => item.status === 'PARSED' || (item.content != null && String(item.content).trim() !== ''),
  ),
)

async function loadResumes() {
  try {
    resumes.value = await listResumes()
    if (!form.resumeId && parsedResumes.value.length) {
      form.resumeId = parsedResumes.value[0].id
    }
  } catch {
    resumes.value = []
  }
}

async function loadHistory() {
  loading.value = true
  try {
    records.value = await listInterviews()
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
}

async function onStart() {
  if (!form.resumeId) {
    ElMessage.warning('请选择简历')
    return
  }
  const position = form.position.trim()
  if (!position) {
    ElMessage.warning('请填写面试岗位')
    return
  }

  starting.value = true
  try {
    const created = await createInterview({
      resumeId: form.resumeId,
      position,
    })
    await startInterview(created.id)
    ElMessage.success('面试已开始')
    await router.push(`/interviews/${created.id}`)
  } catch {
    // 拦截器已提示
  } finally {
    starting.value = false
  }
}

function goRoom(row: Interview) {
  void router.push(`/interviews/${row.id}`)
}

async function onDelete(id: number) {
  try {
    await ElMessageBox.confirm('确定删除这场面试吗？对话与报告将一并删除。', '提示', {
      type: 'warning',
    })
  } catch {
    return
  }

  deletingId.value = id
  try {
    await deleteInterview(id)
    ElMessage.success('删除成功')
    await loadHistory()
  } catch {
    // handled
  } finally {
    deletingId.value = null
  }
}

onMounted(() => {
  void loadResumes()
  void loadHistory()
})
</script>

<style scoped>
.header {
  margin-bottom: 16px;
}

.sub {
  margin: 0;
  color: var(--text-secondary);
}

.form-card {
  margin-bottom: 16px;
}

h3 {
  margin: 0 0 12px;
  font-size: 16px;
}
</style>
