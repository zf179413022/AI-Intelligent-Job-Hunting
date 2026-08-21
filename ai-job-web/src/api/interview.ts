import request from '@/utils/request'
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
