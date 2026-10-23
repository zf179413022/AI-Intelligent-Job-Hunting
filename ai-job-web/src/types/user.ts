export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResult {
  token: string
  userId: number
  username: string
  role: string
}
