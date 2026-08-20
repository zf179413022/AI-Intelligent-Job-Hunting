<template>
  <div class="score-card page-card">
    <div class="label">综合评分</div>
    <div class="score-row">
      <span class="score">{{ score }}</span>
      <span class="total">/ 100</span>
    </div>
    <el-progress
      :percentage="score"
      :stroke-width="14"
      :color="progressColor"
      :show-text="false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  score: number
}>()

const progressColor = computed(() => {
  if (props.score >= 85) return '#67C23A'
  if (props.score >= 70) return '#409EFF'
  if (props.score >= 60) return '#E6A23C'
  return '#F56C6C'
})
</script>

<style scoped>
.score-card {
  min-height: 160px;
}

.label {
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.score-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 16px;
}

.score {
  font-size: 48px;
  font-weight: 700;
  color: var(--primary);
  line-height: 1;
}

.total {
  color: var(--text-secondary);
  font-size: 16px;
}
</style>
