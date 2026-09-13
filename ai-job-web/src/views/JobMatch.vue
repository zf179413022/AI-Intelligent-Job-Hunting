<template>
  <div class="job-match-page">
    <div class="page-card header">
      <div>
        <h2 class="page-title">AI 岗位匹配</h2>
        <p class="sub">选择已解析简历，粘贴岗位 JD，由 DeepSeek 评估匹配度</p>
      </div>
      <el-button @click="router.push('/job-match/history')">匹配历史</el-button>
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

        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item label="岗位名称" required>
              <el-input
                v-model="form.jobName"
                maxlength="100"
                show-word-limit
                placeholder="例如：Java开发工程师"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="公司名称">
              <el-input
                v-model="form.companyName"
                maxlength="100"
                show-word-limit
                placeholder="例如：XX科技有限公司"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="岗位 JD（50~10000字）" required>
          <el-input
            v-model="form.jobDescription"
            type="textarea"
            :rows="8"
            maxlength="10000"
            show-word-limit
            placeholder="粘贴完整岗位描述（至少50字）..."
          />
        </el-form-item>

        <el-button type="primary" :loading="matching" @click="onMatch">
          {{ matching ? '正在AI匹配...' : '开始AI匹配' }}
        </el-button>
      </el-form>
    </div>

    <div v-if="result" class="result-block">
      <el-row :gutter="16">
        <el-col :xs="24" :md="8">
          <div class="page-card score-card">
            <div class="label">匹配度</div>
            <div class="score-row">
              <span class="score">{{ result.matchScore ?? 0 }}</span>
              <span class="unit">%</span>
            </div>
            <el-progress
              :percentage="Math.min(100, Math.max(0, result.matchScore || 0))"
              :stroke-width="14"
              :show-text="false"
            />
            <p class="meta">
              {{ result.jobName }}
              <span v-if="result.companyName"> · {{ result.companyName }}</span>
            </p>
          </div>
        </el-col>
        <el-col :xs="24" :md="16">
          <div class="page-card">
            <h3>技能匹配</h3>
            <div class="tags">
              <el-tag
                v-for="skill in matchedSkills"
                :key="'m-' + skill"
                type="success"
                effect="plain"
              >
                ✓ {{ skill }}
              </el-tag>
              <span v-if="!matchedSkills.length" class="empty">暂无</span>
            </div>
            <h3 class="mt">缺少技能</h3>
            <div class="tags">
              <el-tag
                v-for="skill in missingSkills"
                :key="'x-' + skill"
                type="warning"
                effect="plain"
              >
                ⚠ {{ skill }}
              </el-tag>
              <span v-if="!missingSkills.length" class="empty">暂无</span>
            </div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="mt-row">
        <el-col :xs="24" :md="8">
          <div class="page-card">
            <h3>个人优势</h3>
            <ul>
              <li v-for="(item, i) in advantages" :key="'a' + i">{{ item }}</li>
            </ul>
            <el-empty v-if="!advantages.length" :image-size="60" description="暂无" />
          </div>
        </el-col>
        <el-col :xs="24" :md="8">
          <div class="page-card">
            <h3>风险分析</h3>
            <ul>
              <li v-for="(item, i) in risks" :key="'r' + i">{{ item }}</li>
            </ul>
            <el-empty v-if="!risks.length" :image-size="60" description="暂无" />
          </div>
        </el-col>
        <el-col :xs="24" :md="8">
          <div class="page-card">
            <h3>AI 建议</h3>
            <ol>
              <li v-for="(item, i) in suggestions" :key="'s' + i">{{ item }}</li>
            </ol>
            <el-empty v-if="!suggestions.length" :image-size="60" description="暂无" />
          </div>
        </el-col>
      </el-row>

      <div class="page-card mt-row">
        <h3>总体评价</h3>
        <p class="summary">{{ result.summary || '暂无' }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listResumes } from '@/api/resume'
import { createJobMatch } from '@/api/jobMatch'
import { parseJsonList, type JobMatch } from '@/types/jobMatch'
import type { Resume } from '@/types/resume'

const router = useRouter()
const resumes = ref<Resume[]>([])
const matching = ref(false)
const result = ref<JobMatch | null>(null)

const form = reactive({
  resumeId: undefined as number | undefined,
  jobName: '',
  companyName: '',
  jobDescription: '',
})

const parsedResumes = computed(() =>
  resumes.value.filter(
    (item) => item.status === 'PARSED' || (item.content != null && String(item.content).trim() !== ''),
  ),
)

const matchedSkills = computed(() => parseJsonList(result.value?.matchedSkills))
const missingSkills = computed(() => parseJsonList(result.value?.missingSkills))
const advantages = computed(() => parseJsonList(result.value?.advantages))
const risks = computed(() => parseJsonList(result.value?.risks))
const suggestions = computed(() => parseJsonList(result.value?.suggestions))

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

async function onMatch() {
  if (!form.resumeId) {
    ElMessage.warning('请选择简历')
    return
  }

  const jobName = form.jobName.trim()
  const companyName = form.companyName.trim()
  const jobDescription = form.jobDescription.trim()

  if (jobName.length < 1 || jobName.length > 100) {
    ElMessage.warning('岗位名称长度需在 1~100 字')
    return
  }
  if (companyName.length > 100) {
    ElMessage.warning('公司名称长度不能超过 100 字')
    return
  }
  if (jobDescription.length < 50 || jobDescription.length > 10000) {
    ElMessage.warning('岗位JD长度需在 50~10000 字')
    return
  }

  matching.value = true
  try {
    result.value = await createJobMatch({
      resumeId: form.resumeId,
      jobName,
      companyName: companyName || undefined,
      jobDescription,
    })
    ElMessage.success('AI 匹配完成')
  } catch {
    // 拦截器已提示
  } finally {
    matching.value = false
  }
}

onMounted(() => {
  void loadResumes()
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

.form-card {
  margin-bottom: 16px;
}

.score-card .label {
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.score-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 12px;
}

.score {
  font-size: 48px;
  font-weight: 700;
  color: var(--primary);
  line-height: 1;
}

.unit {
  color: var(--text-secondary);
}

.meta {
  margin: 12px 0 0;
  color: var(--text-secondary);
}

h3 {
  margin: 0 0 12px;
  font-size: 16px;
}

.mt {
  margin-top: 16px;
}

.mt-row {
  margin-top: 16px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.empty {
  color: var(--text-secondary);
}

ul,
ol {
  margin: 0;
  padding-left: 18px;
  line-height: 1.8;
}

.summary {
  margin: 0;
  line-height: 1.8;
  color: var(--text);
}

@media (max-width: 768px) {
  .header {
    flex-direction: column;
  }
}
</style>
