import request from '@/utils/request'
import { getToken } from '@/utils/auth'
import type {
  Interview,
  InterviewAnswerResult,
  InterviewCreateRequest,
  InterviewMessage,
  InterviewReport,
  InterviewStartResult,
} from '@/types/interview'

export function createInterview(data: InterviewCreateRequest) {
  return request.post('/api/interviews', data) as Promise<Interview>
}

export function startInterview(id: number) {
  return request.post(`/api/interviews/${id}/start`, null, {
    timeout: 120000,
  }) as Promise<InterviewStartResult>
}

export function answerInterview(id: number, answer: string) {
  return request.post(
    `/api/interviews/${id}/answer`,
    { answer },
    { timeout: 120000 },
  ) as Promise<InterviewAnswerResult>
}

/** SSE done 事件载荷（与后端 SseEmitter 一致） */
export interface InterviewStreamDone {
  evaluation: string
  nextQuestion?: string
  shouldContinue: boolean
  message?: InterviewMessage | null
  interview?: Interview
}

export interface AnswerStreamHandlers {
  onDelta?: (content: string) => void
  onDone?: (result: InterviewStreamDone) => void
  onError?: (message: string) => void
}

/**
 * POST /api/interviews/{id}/answer/stream
 * 使用 fetch 读取 SSE（支持 Authorization），事件：delta | done | error
 */
export async function answerInterviewStream(
  id: number,
  answer: string,
  handlers: AnswerStreamHandlers = {},
  signal?: AbortSignal,
): Promise<void> {
  const token = getToken()
  const response = await fetch(`/api/interviews/${id}/answer/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ answer }),
    signal,
  })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new Error(text || `流式请求失败（${response.status}）`)
  }

  if (!response.body) {
    throw new Error('浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let eventName = 'message'
  let finished = false

  const flushEvent = (data: string) => {
    const payload = data.trim()
    if (!payload) {
      eventName = 'message'
      return
    }

    if (eventName === 'delta') {
      try {
        const json = JSON.parse(payload) as { content?: string }
        if (json.content) {
          handlers.onDelta?.(json.content)
        }
      } catch {
        handlers.onDelta?.(payload)
      }
    } else if (eventName === 'done') {
      finished = true
      const result = JSON.parse(payload) as InterviewStreamDone
      handlers.onDone?.(result)
    } else if (eventName === 'error') {
      finished = true
      let message = '流式面试失败'
      try {
        const json = JSON.parse(payload) as { message?: string }
        if (json.message) message = json.message
      } catch {
        message = payload
      }
      handlers.onError?.(message)
    }

    eventName = 'message'
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split(/\r?\n/)
    buffer = lines.pop() ?? ''

    let dataLines: string[] = []

    for (const line of lines) {
      if (line === '') {
        if (dataLines.length) {
          flushEvent(dataLines.join('\n'))
          dataLines = []
        }
        continue
      }
      if (line.startsWith(':')) {
        continue
      }
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      }
    }
  }

  if (buffer.trim()) {
    // 末尾无空行时的兜底
    const leftover = buffer
    if (leftover.startsWith('data:')) {
      flushEvent(leftover.slice(5).trim())
    }
  }

  if (!finished) {
    handlers.onError?.('流式连接已结束，但未收到完成事件')
  }
}

export function finishInterview(id: number) {
  return request.post(`/api/interviews/${id}/finish`, null, {
    timeout: 120000,
  }) as Promise<InterviewReport>
}

export function listInterviews() {
  return request.get('/api/interviews') as Promise<Interview[]>
}

export function getInterview(id: number) {
  return request.get(`/api/interviews/${id}`) as Promise<Interview>
}

export function listInterviewMessages(id: number) {
  return request.get(`/api/interviews/${id}/messages`) as Promise<InterviewMessage[]>
}

export function getInterviewReport(id: number) {
  return request.get(`/api/interviews/${id}/report`) as Promise<InterviewReport>
}

export function deleteInterview(id: number) {
  return request.delete(`/api/interviews/${id}`) as Promise<string>
}
