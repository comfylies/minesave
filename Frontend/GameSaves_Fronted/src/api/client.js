import axios from 'axios'
import { ElMessage } from 'element-plus'
import { isRequestCancellation } from '../utils/requestCancellation'
import { createResponseErrorHandler } from '../utils/requestErrorHandler'

const client = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器：携带 Sa-Token
client.interceptors.request.use(
  config => {
    const token = localStorage.getItem('satoken')
    if (token) {
      config.headers['Authorization'] = token
    }
    return config
  },
  error => Promise.reject(error)
)

/**
 * 清除本地认证状态并跳转到登录页。
 * 后端重启后 Sa-Token 内存会话丢失，localStorage 里的旧 token 失效，
 * 401 响应触发此清理逻辑。
 */
function clearAuthAndRedirect() {
  localStorage.removeItem('satoken')
  localStorage.removeItem('currentUser')
  // 避免在登录页重复跳转
  if (window.location.pathname !== '/login') {
    window.location.href = '/login?redirect=' + encodeURIComponent(window.location.pathname)
  }
}

// 响应拦截器：解包 ApiResponse<T>，处理错误
client.interceptors.response.use(
  response => {
    // 文件预览/下载等端点返回二进制或文本，直接透传
    const contentType = response.headers['content-type'] || ''
    if (contentType.includes('text/plain') ||
        contentType.includes('application/octet-stream') ||
        contentType.includes('application/zip')) {
      return response
    }

    const data = response.data

    // 标准 ApiResponse 包装: { code, message, data }
    if (data && typeof data.code === 'number') {
      if (data.code === 200) {
        return data.data
      }
      // 后端返回 401 → 登录已过期
      if (data.code === 401) {
        clearAuthAndRedirect()
        return Promise.reject(new Error(data.message || '登录已过期'))
      }
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
    }

    return data
  },
  createResponseErrorHandler({
    // 路由切换、页面退后台与重连会主动中止长轮询，这不是用户可见错误。
    isCancellation: error => isRequestCancellation(error, axios.isCancel),
    onUnauthorized: clearAuthAndRedirect,
    showError: ElMessage.error
  })
)

export default client
