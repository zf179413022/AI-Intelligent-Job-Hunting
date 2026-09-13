import request from '@/utils/request'
import { getToken } from '@/utils/auth'
import type {
  KnowledgeAskRequest,
  KnowledgeAskResult,
  KnowledgeChunk,
  KnowledgeDocument,
  KnowledgeIngestResult,
} from '@/types/knowledge'

export function listKnowledgeDocuments() {
  return request.get('/api/knowledge/documents') as Promise<KnowledgeDocument[]>
}

export function getKnowledgeDocument(id: number) {
  return request.get(`/api/knowledge/documents/${id}`) as Promise<KnowledgeDocument>
}

export function uploadKnowledgeDocument(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/api/knowledge/documents', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  }) as Promise<KnowledgeDocument>
}

export function parseKnowledgeDocument(id: number) {
  return request.post(`/api/knowledge/documents/${id}/parse`) as Promise<KnowledgeDocument>
}

export function ingestKnowledgeDocument(id: number) {
  return request.post(`/api/knowledge/documents/${id}/ingest`, null, {
    timeout: 180000,
  }) as Promise<KnowledgeIngestResult>
}

export function listKnowledgeChunks(id: number) {
  return request.get(`/api/knowledge/documents/${id}/chunks`) as Promise<KnowledgeChunk[]>
}

export function deleteKnowledgeDocument(id: number) {
  return request.delete(`/api/knowledge/documents/${id}`) as Promise<string>
}

/** RAG 5.6 / 5.7.2：完整问答（同步 JSON，非 SSE） */
export function askKnowledge(payload: KnowledgeAskRequest) {
  return request.post('/api/knowledge/ai/ask', payload, {
    timeout: 120000,
  }) as Promise<KnowledgeAskResult>
}

export interface AskStreamHandlers {
  onMeta?: (meta: {
    question: string
    topK: number
    hitCount: number
    sources: KnowledgeAskResult['sources']
  }) => void
  onDelta?: (content: string) => void
  onDone?: (result: KnowledgeAskResult) => void
  onError?: (message: string) => void
}

/**
 * RAG 5.9：POST /api/knowledge/ai/ask/stream
 * 事件：meta | delta | done | error
 */
export async function askKnowledgeStream(
  payload: KnowledgeAskRequest,
  handlers: AskStreamHandlers = {},
  signal?: AbortSignal,
): Promise<void> {
  const token = getToken()
  const response = await fetch('/api/knowledge/ai/ask/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
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
    const payloadText = data.trim()
    if (!payloadText) {
      eventName = 'message'
      return
    }

    if (eventName === 'meta') {
      handlers.onMeta?.(JSON.parse(payloadText))
    } else if (eventName === 'delta') {
      try {
        const json = JSON.parse(payloadText) as { content?: string }
        if (json.content) handlers.onDelta?.(json.content)
      } catch {
        handlers.onDelta?.(payloadText)
      }
    } else if (eventName === 'done') {
      finished = true
      handlers.onDone?.(JSON.parse(payloadText) as KnowledgeAskResult)
    } else if (eventName === 'error') {
      finished = true
      let message = '流式 RAG 失败'
      try {
        const json = JSON.parse(payloadText) as { message?: string }
        if (json.message) message = json.message
      } catch {
        message = payloadText
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
      if (line.startsWith(':')) continue
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
        continue
      }
      if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      }
    }
  }

  if (buffer.trim()) {
    flushEvent(buffer)
  }
  if (!finished) {
    handlers.onError?.('流式连接已关闭，但未收到完成事件')
  }
}
