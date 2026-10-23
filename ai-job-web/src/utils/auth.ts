const TOKEN_KEY = 'token'
const USERNAME_KEY = 'username'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export function getUsername(): string | null {
  return localStorage.getItem(USERNAME_KEY)
}

export function setUsername(username: string): void {
  localStorage.setItem(USERNAME_KEY, username)
}

export function clearUsername(): void {
  localStorage.removeItem(USERNAME_KEY)
}

export function clearAuth(): void {
  clearToken()
  clearUsername()
}
