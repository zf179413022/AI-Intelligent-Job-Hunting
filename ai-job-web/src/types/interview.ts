export interface Interview {
  id: number
  userId: number
  resumeId: number
  position: string
  status: string
  score?: number | null
  startTime?: string | null
  endTime?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface InterviewMessage {
  id: number
  interviewId: number
  role: string
  content: string
  createdAt?: string
}

export interface InterviewReport {
  id: number
  interviewId: number
  totalScore: number
  javaScore: number
  mysqlScore: number
  redisScore: number
  springScore: number
  weakPoints: string[] | string
  suggestions: string[] | string
  summary: string
  createdAt?: string
  updatedAt?: string
}

export interface InterviewCreateRequest {
  resumeId: number
  position: string
}

export interface InterviewStartResult {
  interview: Interview
  questionMessage: InterviewMessage
}

export interface InterviewAnswerResult {
  interview: Interview
  evaluation: string
  nextQuestionMessage?: InterviewMessage | null
  shouldContinue: boolean
}

export function parseJsonList(value: string[] | string | null | undefined): string[] {
  if (value == null) return []
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter(Boolean)
  }
  const text = String(value).trim()
  if (!text) return []
  try {
    const parsed = JSON.parse(text)
    if (Array.isArray(parsed)) {
      return parsed.map((item) => String(item).trim()).filter(Boolean)
    }
  } catch {
    return text.split(',').map((item) => item.trim()).filter(Boolean)
  }
  return []
}

export function statusLabel(status: string) {
  switch (status) {
    case 'WAITING':
      return '待开始'
    case 'RUNNING':
      return '进行中'
    case 'COMPLETED':
      return '已完成'
    case 'CANCELLED':
      return '已取消'
    default:
      return status
  }
}

export function statusTagType(status: string) {
  switch (status) {
    case 'RUNNING':
      return 'warning'
    case 'COMPLETED':
      return 'success'
    case 'CANCELLED':
      return 'info'
    default:
      return ''
  }
}
