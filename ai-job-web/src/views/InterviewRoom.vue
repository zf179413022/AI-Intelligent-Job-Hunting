<template>
  <div v-loading="loading" class="room-page">
    <div class="page-card header">
      <div>
        <h2 class="page-title">AI 模拟面试</h2>
        <p class="sub">
          {{ interview?.position || '加载中...' }}
          <el-tag v-if="interview" size="small" class="status" :type="statusTagType(interview.status)">
            {{ statusLabel(interview.status) }}
          </el-tag>
        </p>
      </div>
      <div class="actions">
        <el-button @click="router.push('/interviews')">返回列表</el-button>
        <el-button
          v-if="interview?.status === 'WAITING'"
          type="primary"
          :loading="starting"
          @click="onStart"
        >
          开始出题
        </el-button>
        <el-button
          v-if="interview?.status === 'RUNNING'"
          type="warning"
          :loading="finishing"
          :disabled="answering"
          @click="onFinish"
        >
          结束并生成报告
        </el-button>
        <el-button
          v-if="interview?.status === 'COMPLETED'"
          type="success"
          @click="router.push(`/interviews/${interviewId}/report`)"
        >
          查看报告
        </el-button>
      </div>
    </div>

    <div v-if="currentQuestion && interview?.status === 'RUNNING'" class="page-card question-card">
      <div class="q-label">当前题目</div>
      <div class="q-text">{{ currentQuestion }}</div>
    </div>

    <div class="page-card chat-card">
      <div ref="chatBox" class="chat-box">
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="bubble"
          :class="msg.role === 'USER' ? 'mine' : 'ai'"
        >
          <div class="role">{{ roleTitle(msg) }}</div>
          <div class="content">{{ displayContent(msg) }}</div>
        </div>

        <div v-if="streaming" class="bubble ai streaming">
          <div class="role">AI 面试官</div>
          <div class="content">
            <template v-if="thinking && !streamText">
              <span class="thinking">AI 正在思考</span>
              <span class="dots">...</span>
            </template>
            <template v-else>
              {{ streamText }}<span class="cursor">▍</span>
            </template>
          </div>
        </div>

        <el-empty v-if="!messages.length && !loading && !streaming" description="暂无对话，请开始面试" />
      </div>

      <div v-if="lastEvaluation && !streaming" class="eval-card">
        <strong>本轮点评：</strong>
        {{ lastEvaluation }}
        <span v-if="shouldContinue === false" class="hint">（建议结束面试生成报告）</span>
      </div>

      <div v-if="interview?.status === 'RUNNING'" class="composer">
        <el-input
          v-model="answer"
          type="textarea"
          :rows="4"
          maxlength="5000"
          show-word-limit
          placeholder="输入你的回答..."
          :disabled="answering"
        />
        <div class="composer-actions">
          <el-button type="primary" :loading="answering" :disabled="!answer.trim() || answering" @click="onAnswer">
            {{ answering ? 'AI 生成中...' : '提交回答' }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  answerInterviewStream,
  finishInterview,
  getInterview,
  listInterviewMessages,
  startInterview,
} from '@/api/interview'
import {
  statusLabel,
  statusTagType,
  type Interview,
  type InterviewMessage,
} from '@/types/interview'

const route = useRoute()
const router = useRouter()

const interviewId = computed(() => Number(route.params.id))
const interview = ref<Interview | null>(null)
const messages = ref<InterviewMessage[]>([])
const loading = ref(false)
const starting = ref(false)
const answering = ref(false)
const finishing = ref(false)
const answer = ref('')
const lastEvaluation = ref('')
const shouldContinue = ref<boolean | null>(null)
const chatBox = ref<HTMLElement | null>(null)

const streaming = ref(false)
const thinking = ref(false)
const streamText = ref('')
let abortController: AbortController | null = null
let tempUserMsgId: number | null = null

const currentQuestion = computed(() => findLatestAiQuestion(messages.value))

function roleTitle(msg: InterviewMessage) {
  if (msg.role === 'USER') return '我的回答'
  if (msg.content?.includes('【点评】')) return 'AI 点评 / 追问'
  return 'AI 面试官'
}

function displayContent(msg: InterviewMessage) {
  return msg.content || ''
}

function findLatestAiQuestion(list: InterviewMessage[]) {
  for (let i = list.length - 1; i >= 0; i--) {
    const msg = list[i]
    if (msg.role?.toUpperCase() !== 'AI') continue
    const content = msg.content || ''
    const idx = content.lastIndexOf('【追问】')
    if (idx >= 0) {
      return content.slice(idx + '【追问】'.length).trim()
    }
    if (content.includes('【点评】') && content.includes('可以结束')) {
      continue
    }
    return content.trim()
  }
  return ''
}

async function scrollBottom() {
  await nextTick()
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
}

async function loadAll() {
  if (!interviewId.value) return
  loading.value = true
  try {
    interview.value = await getInterview(interviewId.value)
    messages.value = await listInterviewMessages(interviewId.value)
    await scrollBottom()
  } catch {
    interview.value = null
    messages.value = []
  } finally {
    loading.value = false
  }
}

async function onStart() {
  starting.value = true
  try {
    const result = await startInterview(interviewId.value)
    interview.value = result.interview
    messages.value = await listInterviewMessages(interviewId.value)
    ElMessage.success('已生成第一题')
    await scrollBottom()
  } catch {
    // handled
  } finally {
    starting.value = false
  }
}

async function onAnswer() {
  const text = answer.value.trim()
  if (!text) {
    ElMessage.warning('请输入回答')
    return
  }
  if (answering.value) return

  answering.value = true
  streaming.value = true
  thinking.value = true
  streamText.value = ''
  lastEvaluation.value = ''

  tempUserMsgId = -Date.now()
  messages.value = [
    ...messages.value,
    {
      id: tempUserMsgId,
      interviewId: interviewId.value,
      role: 'USER',
      content: text,
    },
  ]
  answer.value = ''
  await scrollBottom()

  abortController?.abort()
  abortController = new AbortController()

  try {
    await answerInterviewStream(
      interviewId.value,
      text,
      {
        onDelta: (chunk) => {
          thinking.value = false
          streamText.value += chunk
          void scrollBottom()
        },
        onDone: async (result) => {
          streaming.value = false
          thinking.value = false
          streamText.value = ''
          tempUserMsgId = null

          lastEvaluation.value = result.evaluation || ''
          shouldContinue.value = result.shouldContinue
          if (result.interview) {
            interview.value = result.interview
          }
          messages.value = await listInterviewMessages(interviewId.value)
          await scrollBottom()

          if (result.shouldContinue === false) {
            ElMessage.info('本轮问题已足够，可以结束并生成报告')
          }
        },
        onError: async (message) => {
          streaming.value = false
          thinking.value = false
          streamText.value = ''
          if (tempUserMsgId != null) {
            messages.value = messages.value.filter((m) => m.id !== tempUserMsgId)
            tempUserMsgId = null
          }
          ElMessage.error(message || '流式面试失败')
        },
      },
      abortController.signal,
    )
  } catch (e) {
    streaming.value = false
    thinking.value = false
    streamText.value = ''
    if (tempUserMsgId != null) {
      messages.value = messages.value.filter((m) => m.id !== tempUserMsgId)
      tempUserMsgId = null
    }
    if ((e as Error)?.name !== 'AbortError') {
      ElMessage.error((e as Error)?.message || '流式请求失败')
    }
  } finally {
    answering.value = false
    abortController = null
  }
}

async function onFinish() {
  try {
    await ElMessageBox.confirm('确定结束面试并生成 AI 报告吗？', '提示', { type: 'warning' })
  } catch {
    return
  }

  finishing.value = true
  try {
    await finishInterview(interviewId.value)
    ElMessage.success('报告已生成')
    await router.push(`/interviews/${interviewId.value}/report`)
  } catch {
    // handled
  } finally {
    finishing.value = false
  }
}

onMounted(() => {
  void loadAll()
})

onBeforeUnmount(() => {
  abortController?.abort()
})

watch(
  () => route.params.id,
  () => {
    abortController?.abort()
    lastEvaluation.value = ''
    shouldContinue.value = null
    streaming.value = false
    streamText.value = ''
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
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.question-card {
  margin-bottom: 16px;
  border-left: 3px solid #409eff;
}

.q-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.q-text {
  line-height: 1.7;
  white-space: pre-wrap;
  color: #303133;
  font-weight: 500;
}

.chat-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-box {
  max-height: 52vh;
  overflow-y: auto;
  padding: 8px 4px;
  background: #fafbfc;
  border-radius: 8px;
}

.bubble {
  max-width: 88%;
  margin-bottom: 14px;
  padding: 12px 14px;
  border-radius: 12px;
  white-space: pre-wrap;
  line-height: 1.6;
}

.bubble.ai {
  background: #ecf5ff;
  color: #303133;
}

.bubble.mine {
  margin-left: auto;
  background: #409eff;
  color: #fff;
}

.bubble.streaming {
  border: 1px dashed #79bbff;
}

.role {
  font-size: 12px;
  opacity: 0.8;
  margin-bottom: 6px;
}

.thinking {
  color: #909399;
}

.dots {
  display: inline-block;
  animation: blink 1.2s infinite;
}

.cursor {
  display: inline-block;
  margin-left: 1px;
  color: #409eff;
  animation: blink 0.9s step-end infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.eval-card {
  padding: 10px 12px;
  background: #fdf6ec;
  border-radius: 8px;
  color: #606266;
  line-height: 1.6;
}

.hint {
  color: #e6a23c;
  margin-left: 6px;
}

.composer-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .header {
    flex-direction: column;
  }

  .chat-box {
    max-height: 45vh;
  }
}
</style>
