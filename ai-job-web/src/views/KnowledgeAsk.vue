<template>
  <div class="ask-page">
    <div class="page-card toolbar">
      <div>
        <h2 class="page-title">AI 知识问答</h2>
        <p class="hint">
          RAG + Sources；默认 SSE 流式输出（/ask/stream），同步 /ask 仍保留可用。
        </p>
      </div>
      <el-button @click="$router.push('/knowledge')">返回文档管理</el-button>
    </div>

    <div class="page-card ask-box">
      <el-input
        v-model="question"
        type="textarea"
        :rows="4"
        maxlength="1000"
        show-word-limit
        placeholder="请输入你的问题，例如：Spring Boot 自动配置是怎么工作的？"
        :disabled="asking"
        @keydown.ctrl.enter="onAsk"
      />
      <div class="ask-actions">
        <span class="tip">Ctrl + Enter 发送 · SSE 流式</span>
        <div class="ask-actions-right">
          <el-input-number v-model="topK" :min="1" :max="10" :step="1" size="small" />
          <span class="topk-label">Top-K</span>
          <el-button type="primary" :loading="asking" :disabled="!question.trim()" @click="onAsk">
            {{ asking ? '生成中...' : '提问' }}
          </el-button>
        </div>
      </div>
    </div>

    <el-alert
      v-if="errorMsg"
      class="error-alert"
      type="error"
      :title="errorMsg"
      show-icon
      :closable="true"
      @close="errorMsg = ''"
    />

    <div v-if="current" class="page-card result">
      <div class="section-title">本次回答</div>
      <div class="question-line">
        <span class="label">问题</span>
        <span>{{ current.question }}</span>
      </div>
      <div class="answer-block">
        <div class="label">
          AI
          <el-tag v-if="asking" size="small" type="warning" class="stream-tag">流式输出中</el-tag>
        </div>
        <div class="answer-text">{{ current.answer || (asking ? '…' : '') }}</div>
        <div class="meta">
          命中 {{ current.hitCount ?? 0 }} / Top-K {{ current.topK ?? topK }}
          <span v-if="current.qaId"> · qaId {{ current.qaId }}</span>
          <span v-if="current.provider"> · {{ current.provider }}</span>
        </div>
      </div>

      <div class="section-title">参考资料（Sources）</div>
      <el-empty
        v-if="!current.sources?.length && !asking"
        description="未检索到相关资料（知识库可能无覆盖该主题）"
        :image-size="72"
      />
      <div v-else-if="current.sources?.length" class="sources">
        <div v-for="(src, index) in current.sources" :key="`${src.chunkId}-${index}`" class="source-card">
          <div class="source-head">
            <el-icon><Document /></el-icon>
            <strong>{{ src.title || '未知文档' }}</strong>
            <el-tag size="small" type="info">chunkId {{ src.chunkId }}</el-tag>
            <el-tag size="small" type="success">相似度 {{ formatScore(src.score) }}</el-tag>
          </div>
          <p class="snippet">{{ src.snippet }}</p>
          <div class="source-foot">documentId {{ src.documentId }}</div>
        </div>
      </div>
    </div>

    <div v-if="history.length" class="page-card history">
      <div class="section-title">本页问答历史</div>
      <el-collapse>
        <el-collapse-item
          v-for="(item, index) in history"
          :key="item.qaId ?? index"
          :title="item.question"
          :name="String(item.qaId ?? index)"
        >
          <p class="history-answer">{{ item.answer }}</p>
          <div class="history-sources">
            <el-tag
              v-for="s in item.sources"
              :key="s.chunkId"
              size="small"
              class="history-tag"
            >
              {{ s.title }} #{{ s.chunkId }} ({{ formatScore(s.score) }})
            </el-tag>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { askKnowledgeStream } from '@/api/knowledge'
import type { KnowledgeAskResult } from '@/types/knowledge'

const question = ref('')
const topK = ref(5)
const asking = ref(false)
const errorMsg = ref('')
const current = ref<KnowledgeAskResult | null>(null)
const history = ref<KnowledgeAskResult[]>([])
let abortController: AbortController | null = null

function formatScore(score: number) {
  if (score == null || Number.isNaN(score)) return '-'
  return Number(score).toFixed(4)
}

async function onAsk() {
  const q = question.value.trim()
  if (!q || asking.value) return

  abortController?.abort()
  abortController = new AbortController()

  asking.value = true
  errorMsg.value = ''
  current.value = {
    question: q,
    answer: '',
    sources: [],
    topK: topK.value,
    hitCount: 0,
    provider: '',
    qaId: null,
  }

  try {
    await askKnowledgeStream(
      { question: q, topK: topK.value },
      {
        onMeta: (meta) => {
          if (!current.value) return
          current.value = {
            ...current.value,
            question: meta.question,
            topK: meta.topK,
            hitCount: meta.hitCount,
            sources: meta.sources || [],
          }
        },
        onDelta: (content) => {
          if (!current.value) return
          current.value = {
            ...current.value,
            answer: (current.value.answer || '') + content,
          }
        },
        onDone: (result) => {
          current.value = result
          history.value = [result, ...history.value].slice(0, 20)
          if (!result.sources?.length) {
            ElMessage.warning('未检索到相关资料，请检查知识库是否已入库相关文档')
          } else {
            ElMessage.success(`流式完成，引用 ${result.sources.length} 条资料`)
          }
        },
        onError: (message) => {
          errorMsg.value = message
          ElMessage.error(message)
        },
      },
      abortController.signal,
    )
  } catch (e: unknown) {
    if ((e as { name?: string })?.name === 'AbortError') {
      return
    }
    errorMsg.value = e instanceof Error ? e.message : 'RAG 流式问答失败'
  } finally {
    asking.value = false
    abortController = null
  }
}

onBeforeUnmount(() => {
  abortController?.abort()
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

.hint {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.ask-box {
  margin-bottom: 16px;
}

.ask-actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ask-actions-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tip,
.topk-label {
  color: var(--text-secondary);
  font-size: 13px;
}

.error-alert {
  margin-bottom: 16px;
}

.result,
.history {
  margin-bottom: 16px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}

.question-line {
  margin-bottom: 12px;
  line-height: 1.6;
}

.label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 48px;
  color: var(--text-secondary);
  font-weight: 600;
  margin-right: 8px;
}

.stream-tag {
  font-weight: 400;
}

.answer-block {
  background: #f7f9fc;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 20px;
}

.answer-text {
  margin-top: 8px;
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 15px;
  min-height: 1.5em;
}

.meta {
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-secondary);
}

.sources {
  display: grid;
  gap: 12px;
}

.source-card {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px 14px;
  background: #fff;
}

.source-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.snippet {
  margin: 0;
  color: #303133;
  line-height: 1.6;
  white-space: pre-wrap;
  font-size: 13px;
}

.source-foot {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.history-answer {
  white-space: pre-wrap;
  line-height: 1.6;
  margin: 0 0 10px;
}

.history-tag {
  margin: 0 6px 6px 0;
}

@media (max-width: 768px) {
  .toolbar,
  .ask-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .ask-actions-right {
    justify-content: flex-end;
  }
}
</style>
