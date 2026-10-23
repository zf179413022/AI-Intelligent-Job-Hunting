<template>
  <div v-loading="loading" class="report-page">
    <div class="page-card header">
      <div>
        <h2 class="page-title">AI 面试报告</h2>
        <p class="sub">
          {{ interview?.position || '' }}
          <span v-if="interview?.endTime"> · {{ interview.endTime }}</span>
        </p>
      </div>
      <div class="actions">
        <el-button @click="router.push(`/interviews/${interviewId}`)">返回面试</el-button>
        <el-button @click="router.push('/interviews')">面试列表</el-button>
      </div>
    </div>

    <template v-if="report">
      <el-row :gutter="16">
        <el-col :xs="24" :md="8">
          <div class="page-card score-card">
            <div class="label">综合评分</div>
            <div class="score-row">
              <span class="score">{{ report.totalScore ?? 0 }}</span>
              <span class="unit">/ 100</span>
            </div>
            <el-progress
              :percentage="Math.min(100, Math.max(0, report.totalScore || 0))"
              :stroke-width="14"
              :show-text="false"
            />
          </div>
        </el-col>
        <el-col :xs="24" :md="16">
          <div class="page-card">
            <h3>能力维度</h3>
            <v-chart class="chart" :option="chartOption" autoresize />
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="mt">
        <el-col :xs="24" :md="8">
          <div class="page-card dim">
            <div>Java</div>
            <strong>{{ report.javaScore }}</strong>
          </div>
        </el-col>
        <el-col :xs="24" :md="8">
          <div class="page-card dim">
            <div>Spring</div>
            <strong>{{ report.springScore }}</strong>
          </div>
        </el-col>
        <el-col :xs="24" :md="8">
          <div class="page-card dim">
            <div>MySQL</div>
            <strong>{{ report.mysqlScore }}</strong>
          </div>
        </el-col>
      </el-row>
      <el-row :gutter="16" class="mt-sm">
        <el-col :xs="24" :md="8">
          <div class="page-card dim">
            <div>Redis</div>
            <strong>{{ report.redisScore }}</strong>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="mt">
        <el-col :xs="24" :md="12">
          <div class="page-card">
            <h3>薄弱点</h3>
            <ul v-if="weakPoints.length">
              <li v-for="(item, i) in weakPoints" :key="'w' + i">{{ item }}</li>
            </ul>
            <el-empty v-else description="暂无" :image-size="60" />
          </div>
        </el-col>
        <el-col :xs="24" :md="12">
          <div class="page-card">
            <h3>学习建议</h3>
            <ol v-if="suggestions.length">
              <li v-for="(item, i) in suggestions" :key="'s' + i">{{ item }}</li>
            </ol>
            <el-empty v-else description="暂无" :image-size="60" />
          </div>
        </el-col>
      </el-row>

      <div class="page-card mt">
        <h3>总体评价</h3>
        <p class="summary">{{ report.summary || '暂无' }}</p>
      </div>
    </template>

    <el-empty v-else-if="!loading" description="暂无报告，请先结束面试" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { RadarChart } from 'echarts/charts'
import { RadarComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { getInterview, getInterviewReport } from '@/api/interview'
import {
  parseJsonList,
  type Interview,
  type InterviewReport,
} from '@/types/interview'

use([CanvasRenderer, RadarChart, RadarComponent, TooltipComponent])

const route = useRoute()
const router = useRouter()

const interviewId = computed(() => Number(route.params.id))
const loading = ref(false)
const interview = ref<Interview | null>(null)
const report = ref<InterviewReport | null>(null)

const weakPoints = computed(() => parseJsonList(report.value?.weakPoints))
const suggestions = computed(() => parseJsonList(report.value?.suggestions))

const chartOption = computed(() => {
  const r = report.value
  return {
    color: ['#409EFF'],
    tooltip: {},
    radar: {
      indicator: [
        { name: 'Java', max: 100 },
        { name: 'Spring', max: 100 },
        { name: 'MySQL', max: 100 },
        { name: 'Redis', max: 100 },
      ],
      radius: '65%',
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: [
              r?.javaScore ?? 0,
              r?.springScore ?? 0,
              r?.mysqlScore ?? 0,
              r?.redisScore ?? 0,
            ],
            name: '维度得分',
            areaStyle: { opacity: 0.25 },
          },
        ],
      },
    ],
  }
})

async function loadAll() {
  if (!interviewId.value) return
  loading.value = true
  report.value = null
  try {
    interview.value = await getInterview(interviewId.value)
    report.value = await getInterviewReport(interviewId.value)
  } catch {
    report.value = null
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadAll()
})

watch(
  () => route.params.id,
  () => {
    void loadAll()
  },
)
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

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.score-card .label {
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.score-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
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

.chart {
  height: 280px;
  width: 100%;
}

h3 {
  margin: 0 0 12px;
  font-size: 16px;
}

.mt {
  margin-top: 16px;
}

.mt-sm {
  margin-top: 8px;
}

.dim {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.dim strong {
  font-size: 24px;
  color: var(--primary);
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
}

@media (max-width: 768px) {
  .header {
    flex-direction: column;
  }
}
</style>
