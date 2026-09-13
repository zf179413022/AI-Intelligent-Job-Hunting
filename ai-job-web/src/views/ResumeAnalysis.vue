<template>
  <div v-loading="loading" class="analysis-page">
    <div class="page-card header-row">
      <div>
        <h2 class="page-title">简历 AI 分析</h2>
        <p v-if="analysis" class="sub">
          {{ analysis.name || '未识别姓名' }}
          <span v-if="analysis.createdAt"> · {{ analysis.createdAt }}</span>
        </p>
      </div>
      <el-button @click="router.push('/resumes')">返回列表</el-button>
    </div>

    <template v-if="analysis">
      <el-row :gutter="16">
        <el-col :xs="24" :md="8">
          <ScoreCard :score="analysis.score || 0" />
        </el-col>
        <el-col :xs="24" :md="16">
          <div class="page-card skills-card">
            <h3>技能标签</h3>
            <div class="tags">
              <el-tag
                v-for="skill in skills"
                :key="skill"
                class="tag"
                effect="plain"
                type="primary"
              >
                {{ skill }}
              </el-tag>
              <span v-if="!skills.length" class="empty">暂无技能数据</span>
            </div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="section">
        <el-col :xs="24" :md="12">
          <SkillChart :skills="skills" />
        </el-col>
        <el-col :xs="24" :md="12">
          <div class="page-card">
            <h3>AI 优化建议</h3>
            <ol v-if="suggestions.length" class="suggestions">
              <li v-for="(item, index) in suggestions" :key="index">{{ item }}</li>
            </ol>
            <el-empty v-else description="暂无建议" :image-size="80" />
          </div>
        </el-col>
      </el-row>
    </template>

    <el-empty v-else-if="!loading" description="该简历暂无 AI 分析结果，请先执行 AI 分析" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ScoreCard from '@/components/ScoreCard.vue'
import SkillChart from '@/components/SkillChart.vue'
import { getResumeAnalysis } from '@/api/ai'
import { toStringList, type ResumeAnalysisResult } from '@/types/ai'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const analysis = ref<ResumeAnalysisResult | null>(null)

const skills = computed(() => toStringList(analysis.value?.skills))
const suggestions = computed(() => toStringList(analysis.value?.suggestions))

async function loadAnalysis() {
  const id = Number(route.params.id)
  if (!id) return

  loading.value = true
  analysis.value = null
  try {
    analysis.value = await getResumeAnalysis(id, false)
  } catch {
    analysis.value = null
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadAnalysis()
})

watch(
  () => route.params.id,
  () => {
    void loadAnalysis()
  },
)
</script>

<style scoped>
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  gap: 12px;
}

.page-title {
  margin-bottom: 4px;
}

.sub {
  margin: 0;
  color: var(--text-secondary);
}

.skills-card {
  min-height: 160px;
  margin-bottom: 0;
}

.section {
  margin-top: 16px;
}

h3 {
  margin: 0 0 12px;
  font-size: 16px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  margin: 0;
}

.empty {
  color: var(--text-secondary);
}

.suggestions {
  margin: 0;
  padding-left: 20px;
  line-height: 1.8;
  color: var(--text);
}

@media (max-width: 768px) {
  .header-row {
    flex-direction: column;
  }

  .skills-card {
    margin-top: 16px;
  }
}
</style>
