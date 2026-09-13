import request from '@/utils/request'
import type { Resume } from '@/types/resume'

export function listResumes() {
  return request.get('/api/resumes') as Promise<Resume[]>
}

export function uploadResume(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/api/resumes/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }) as Promise<Resume>
}

export function deleteResume(id: number) {
  return request.delete(`/api/resumes/${id}`) as Promise<string>
}

export function parseResume(id: number) {
  return request.post(`/api/resumes/${id}/parse`) as Promise<Resume>
}
