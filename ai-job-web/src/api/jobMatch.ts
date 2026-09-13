import request from '@/utils/request'
import type { JobMatch, JobMatchRequest } from '@/types/jobMatch'

export function createJobMatch(data: JobMatchRequest) {
  return request.post('/api/job-matches', data) as Promise<JobMatch>
}

export function listJobMatches() {
  return request.get('/api/job-matches') as Promise<JobMatch[]>
}

export function getJobMatch(id: number) {
  return request.get(`/api/job-matches/${id}`) as Promise<JobMatch>
}

export function deleteJobMatch(id: number) {
  return request.delete(`/api/job-matches/${id}`) as Promise<string>
}
