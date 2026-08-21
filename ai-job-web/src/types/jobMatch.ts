import type { Resume } from '@/types/resume'

export interface JobMatchRequest {
  resumeId: number
  jobName: string
  companyName?: string
  jobDescription: string
}

export interface JobMatch {
  id: number
  userId: number
  resumeId: number
  jobName: string
  companyName?: string
  jobDescription: string
  matchScore: number
  matchedSkills: string[] | string
  missingSkills: string[] | string
  advantages: string[] | string
  risks: string[] | string
  suggestions: string[] | string
  summary: string
  createdAt?: string
  updatedAt?: string
}

/** 解析 JSON 数组字符串或直接数组 */
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
    // 兼容逗号分隔
    return text.split(',').map((item) => item.trim()).filter(Boolean)
  }
  return []
}

export type { Resume }
