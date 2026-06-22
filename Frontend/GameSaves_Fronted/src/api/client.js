import axios from 'axios'
import { ElMessage } from 'element-plus'

const client = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
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
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
    }

    return data
  },
  error => {
    const msg = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default client
