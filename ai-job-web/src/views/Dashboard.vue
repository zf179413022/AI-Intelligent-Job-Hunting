<template>
  <div v-loading="loading" class="dashboard">
    <div class="page-card welcome">
      <h2 class="page-title">欢迎回来{{ userStore.username ? `，${userStore.username}` : '' }}</h2>
      <p>从真实接口汇总的简历与 AI 分析概览</p>
    </div>

    <el-row :gutter="16" class="stats">
      <el-col :xs="24" :sm="8">
        <div class="stat page-card">
          <div class="label">简历数量</div>
          <div class="value">{{ resumes.length }}</div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8">
        <div class="stat page-card">
          <div class="label">已解析简历</div>
          <div class="value">{{ parsedCount }}</div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8">
        <div class="stat page-card">
          <div class="label">最高简历评分</div>
          <div class="value">{{ maxScore == null ? '-' : maxScore }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <div class="page-card">
          <h3>最近简历</h3>
          <ResumeCard
            v-for="item in recentResumes"
            :key="item.id"
            :resume="item"
          />
          <el-empty v-if="!recentResumes.length" description="暂无简历" :image-size="80" />
        </div>
      </el-col>
      <el-col :xs="24" :md="12">
        <div class="page-card">
          <h3>最近 AI 建议</h3>
          <ul v-if="latestSuggestions.length" class="suggestions">
            <li v-for="(tip, index) in latestSuggestions" :key="index">{{ tip }}</li>
          </ul>
          <el-empty v-else description="暂无 AI 建议，请先分析简历" :image-size="80" />
          <div v-if="latestAnalysis" class="meta">
            来自简历 #{{ latestAnalysis.resumeId }} · 评分 {{ latestAnalysis.score }}
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ResumeCard from '@/components/ResumeCard.vue'
import { listResumes } from '@/api/resume'
import { getResumeAnalysis } from '@/api/ai'
import { useUserStore } from '@/stores/user'
import { toStringList, type ResumeAnalysisResult } from '@/types/ai'
import type { Resume } from '@/types/resume'

const userStore = useUserStore()
const loading = ref(false)
const resumes = ref<Resume[]>([])
const analyses = ref<ResumeAnalysisResult[]>([])

const parsedCount = computed(
  () => resumes.value.filter((item) => item.status === 'PARSED').length,
)

const recentResumes = computed(() =>
  [...resumes.value]
    .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))
    .slice(0, 5),
)

const maxScore = computed(() => {
  if (!analyses.value.length) return null
  return Math.max(...analyses.value.map((item) => item.score || 0))
})

const latestAnalysis = computed(() => {
  if (!analyses.value.length) return null
  return [...analyses.value].sort((a, b) =>
    String(b.createdAt || '').localeCompare(String(a.createdAt || '')),
  )[0]
})

const latestSuggestions = computed(() =>
  toStringList(latestAnalysis.value?.suggestions).slice(0, 4),
)

onMounted(async () => {
  loading.value = true
  try {
    resumes.value = await listResumes()
    const results = await Promise.allSettled(
      resumes.value.map((item) => getResumeAnalysis(item.id, true)),
    )
    analyses.value = results
      .filter((item): item is PromiseFulfilledResult<ResumeAnalysisResult> => item.status === 'fulfilled')
      .map((item) => item.value)
  } catch {
    resumes.value = []
    analyses.value = []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.welcome {
  margin-bottom: 16px;
}

.welcome p {
  margin: 0;
  color: var(--text-secondary);
}

.stats {
  margin-bottom: 16px;
}

.stat {
  margin-bottom: 16px;
}

.label {
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.value {
  font-size: 32px;
  font-weight: 700;
  color: var(--primary);
}

h3 {
  margin: 0 0 12px;
  font-size: 16px;
}

.suggestions {
  margin: 0;
  padding-left: 18px;
  line-height: 1.8;
}

.meta {
  margin-top: 12px;
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
