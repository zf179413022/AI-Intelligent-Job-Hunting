export interface ResumeAnalysisResult {
  id?: number
  resumeId?: number
  userId?: number
  name: string
  skills: string[] | string
  score: number
  suggestions: string[] | string
  createdAt?: string
  updatedAt?: string
}

/** 统一把 skills / suggestions 转成数组（DB 存逗号串，AI 接口返回数组） */
export function toStringList(value: string[] | string | null | undefined): string[] {
  if (value == null) return []
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter(Boolean)
  }
  const text = String(value).trim()
  if (!text) return []
  return text
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}
