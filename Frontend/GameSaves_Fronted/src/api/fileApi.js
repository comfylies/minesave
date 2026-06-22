import client from './client'
import { ElMessage } from 'element-plus'

export const fileApi = {
  /** 浏览目录 */
  browse: (articleId, path = '') =>
    client.get(`/files/${articleId}/browse`, { params: { path } }),

  /** 获取文件详情 */
  getDetail: (articleId, path) =>
    client.get(`/files/${articleId}/detail`, { params: { path } }),

  /** 获取文件预览 URL（直接用于 fetch 文本内容） */
  previewUrl: (articleId, path) =>
    `/api/files/${articleId}/preview?path=${encodeURIComponent(path)}`,

  /** 下载 ZIP 文件 URL（直接用于 <a href>） */
  downloadUrl: (articleId) =>
    `/api/files/${articleId}/download`,

  /**
   * 触发浏览器下载 ZIP 文件（通过 JS 控制，带错误处理）
   * @returns {Promise<boolean>} true = 下载成功, false = 失败
   */
  async download(articleId) {
    try {
      const response = await client.get(`/files/${articleId}/download`, {
        responseType: 'blob'
      })

      // 拦截器对二进制响应返回完整 response 对象
      const blob = response.data
      if (!blob || blob.size === 0) {
        ElMessage.error('下载失败：文件为空')
        return false
      }

      // 从 Content-Disposition 头解析文件名
      const disposition = response.headers['content-disposition'] || ''
      const filenameMatch = disposition.match(/filename="?([^";\n]+)"?/)
      const filename = filenameMatch
        ? decodeURIComponent(filenameMatch[1])
        : `archive-${articleId}.zip`

      // 创建 Blob URL 并触发下载
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = filename
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(url)

      ElMessage.success('开始下载')
      return true
    } catch (e) {
      // 错误已在拦截器中提示（限流、文件不存在等）
      return false
    }
  }
}
