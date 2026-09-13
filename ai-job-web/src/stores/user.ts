import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi } from '@/api/auth'
import {
  clearAuth,
  getToken,
  getUsername,
  setToken,
  setUsername,
} from '@/utils/auth'
import type { LoginRequest } from '@/types/user'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getToken())
  const username = ref<string | null>(getUsername())

  const isLoggedIn = computed(() => Boolean(token.value))

  async function login(payload: LoginRequest) {
    const result = await loginApi(payload)
    token.value = result.token
    username.value = result.username
    setToken(result.token)
    setUsername(result.username)
    return result
  }

  function logout() {
    token.value = null
    username.value = null
    clearAuth()
  }

  return {
    token,
    username,
    isLoggedIn,
    login,
    logout,
  }
})
