import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { clearAuth, getToken } from '@/utils/auth'

export interface RequestConfig extends AxiosRequestConfig {
  /** 为 true 时不弹全局错误提示（如 Dashboard 批量探测） */
  silent?: boolean
}

const request = axios.create({
  baseURL: '',
  timeout: 60000,
})

request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const silent = Boolean((error.config as RequestConfig | undefined)?.silent)
    if (silent) {
      return Promise.reject(error)
    }

    const status = error.response?.status
    const data = error.response?.data
    const message =
      (typeof data === 'string' && data) ||
      data?.message ||
      data?.error ||
      error.message

    if (status === 401) {
      clearAuth()
      ElMessage.error('登录已过期，请重新登录')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    } else if (status === 403) {
      ElMessage.error('没有权限执行该操作')
    } else if (status === 404) {
      ElMessage.error('请求的资源不存在')
    } else if (status === 400) {
      ElMessage.error(typeof message === 'string' ? message : '请求参数错误')
    } else if (status && status >= 500) {
      ElMessage.error(
        typeof message === 'string' ? message : '服务器错误，请稍后重试',
      )
    } else if (!error.response) {
      ElMessage.error('网络异常，请检查后端是否启动')
    } else {
      ElMessage.error(typeof message === 'string' ? message : '操作失败，请稍后重试')
    }

    return Promise.reject(error)
  },
)

export default request
