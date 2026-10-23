<template>
  <div class="skill-chart page-card">
    <h3>技能分布</h3>
    <p class="hint">按技能类别统计数量（后端暂无单项评分，不做虚假等级）</p>
    <v-chart class="chart" :option="option" autoresize />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { RadarChart } from 'echarts/charts'
import {
  LegendComponent,
  RadarComponent,
  TooltipComponent,
} from 'echarts/components'
import VChart from 'vue-echarts'

use([CanvasRenderer, RadarChart, RadarComponent, LegendComponent, TooltipComponent])

const props = defineProps<{
  skills: string[]
}>()

function categorize(skill: string): string {
  const s = skill.toLowerCase()
  if (
    ['java', 'spring', 'mysql', 'redis', 'mybatis', 'jwt', 'maven', 'flowable', 'sm4'].some((k) =>
      s.includes(k),
    )
  ) {
    return '后端'
  }
  if (['vue', 'echarts', 'javascript', 'typescript', 'css', 'html'].some((k) => s.includes(k))) {
    return '前端'
  }
  if (
    ['python', 'fastapi', 'pytorch', 'yolo', 'resnet', 'tensor', 'ai', '深度学习'].some((k) =>
      s.includes(k),
    )
  ) {
    return 'AI'
  }
  if (['git', 'postman', 'docker', 'linux', 'nginx'].some((k) => s.includes(k))) {
    return '工具'
  }
  return '其他'
}

const categories = ['后端', '前端', 'AI', '工具', '其他'] as const

const counts = computed(() => {
  const map: Record<string, number> = {
    后端: 0,
    前端: 0,
    AI: 0,
    工具: 0,
    其他: 0,
  }
  for (const skill of props.skills) {
    map[categorize(skill)] += 1
  }
  return map
})

const option = computed(() => {
  const max = Math.max(3, ...Object.values(counts.value))
  return {
    color: ['#409EFF'],
    tooltip: {},
    radar: {
      indicator: categories.map((name) => ({ name, max })),
      radius: '65%',
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: categories.map((name) => counts.value[name]),
            name: '技能数量',
            areaStyle: { opacity: 0.25 },
          },
        ],
      },
    ],
  }
})
</script>

<style scoped>
h3 {
  margin: 0 0 4px;
  font-size: 16px;
}

.hint {
  margin: 0 0 8px;
  color: var(--text-secondary);
  font-size: 13px;
}

.chart {
  height: 320px;
  width: 100%;
}
</style>
