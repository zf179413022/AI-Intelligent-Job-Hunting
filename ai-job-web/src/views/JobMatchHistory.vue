<template>
  <div class="history-page">
    <div class="page-card header">
      <div>
        <h2 class="page-title">岗位匹配历史</h2>
        <p class="sub">查看与删除本人的 AI 匹配记录</p>
      </div>
      <el-button type="primary" @click="router.push('/job-match')">新建匹配</el-button>
    </div>

    <div class="page-card">
      <el-table v-loading="loading" :data="records" stripe empty-text="暂无匹配记录">
        <el-table-column prop="jobName" label="岗位名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="companyName" label="公司" min-width="140" show-overflow-tooltip />
        <el-table-column label="匹配度" width="100">
          <template #default="{ row }">
            <el-tag :type="scoreType(row.matchScore)">{{ row.matchScore }}%</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="分析时间" min-width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="onView(row.id)">查看</el-button>
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

    <el-dialog v-model="detailVisible" title="匹配详情" width="720px" destroy-on-close>
      <template v-if="detail">
        <p class="detail-title">
          {{ detail.jobName }}
          <span v-if="detail.companyName"> · {{ detail.companyName }}</span>
          · 匹配度 {{ detail.matchScore }}%
        </p>
        <h4>技能匹配</h4>
        <div class="tags">
          <el-tag v-for="s in parseJsonList(detail.matchedSkills)" :key="'m' + s" type="success">
            {{ s }}
          </el-tag>
        </div>
        <h4>缺少技能</h4>
        <div class="tags">
          <el-tag v-for="s in parseJsonList(detail.missingSkills)" :key="'x' + s" type="warning">
            {{ s }}
          </el-tag>
        </div>
        <h4>总体评价</h4>
        <p>{{ detail.summary }}</p>
        <h4>AI 建议</h4>
        <ol>
          <li v-for="(s, i) in parseJsonList(detail.suggestions)" :key="i">{{ s }}</li>
        </ol>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteJobMatch, getJobMatch, listJobMatches } from '@/api/jobMatch'
import { parseJsonList, type JobMatch } from '@/types/jobMatch'

const router = useRouter()
const loading = ref(false)
const records = ref<JobMatch[]>([])
const deletingId = ref<number | null>(null)
const detailVisible = ref(false)
const detail = ref<JobMatch | null>(null)

function scoreType(score: number) {
  if (score >= 85) return 'success'
  if (score >= 70) return 'info'
  if (score >= 60) return 'warning'
  return 'danger'
}

async function loadList() {
  loading.value = true
  try {
    records.value = await listJobMatches()
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
}

async function onView(id: number) {
  try {
    detail.value = await getJobMatch(id)
    detailVisible.value = true
  } catch {
    // handled
  }
}

async function onDelete(id: number) {
  try {
    await ElMessageBox.confirm('确定删除这条匹配记录吗？', '提示', { type: 'warning' })
  } catch {
    return
  }

  deletingId.value = id
  try {
    await deleteJobMatch(id)
    ElMessage.success('删除成功')
    await loadList()
  } catch {
    // handled
  } finally {
    deletingId.value = null
  }
}

onMounted(() => {
  void loadList()
})
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.sub {
  margin: 0;
  color: var(--text-secondary);
}

.detail-title {
  font-weight: 600;
  margin-bottom: 12px;
}

h4 {
  margin: 12px 0 8px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

ol {
  margin: 0;
  padding-left: 18px;
  line-height: 1.8;
}
</style>
