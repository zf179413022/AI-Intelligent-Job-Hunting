import request from '@/utils/request'
import type { LoginRequest, LoginResult } from '@/types/user'

export function login(data: LoginRequest) {
  return request.post('/api/auth/login', data) as Promise<LoginResult>
}

export function register(data: LoginRequest & { email?: string }) {
  return request.post('/api/auth/register', data)
}
