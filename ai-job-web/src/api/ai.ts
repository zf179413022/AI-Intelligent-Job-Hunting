import request, { type RequestConfig } from '@/utils/request'
import type { ResumeAnalysisResult } from '@/types/ai'

export function analyzeResume(id: number) {
  return request.post(`/api/resumes/${id}/ai-analyze`) as Promise<ResumeAnalysisResult>
}

export function getResumeAnalysis(id: number, silent = false) {
  return request.get(`/api/resumes/${id}/ai-analysis`, {
    silent,
  } as RequestConfig) as Promise<ResumeAnalysisResult>
}
