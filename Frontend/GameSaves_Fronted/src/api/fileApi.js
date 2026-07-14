import client from './client'

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
  downloadUrl: (articleId, token) => {
    const base = `/api/files/${articleId}/download`
    return token ? `${base}?token=${encodeURIComponent(token)}` : base
  },

  /** 查询下载信息 — 是否需要验证码、文件大小、每日剩余次数 */
  getDownloadInfo: (articleId) =>
    client.get(`/files/${articleId}/download/info`),

  /** 验证下载验证码并获取下载 token */
  verifyDownloadCaptcha: (articleId, captchaKey, captchaCode) =>
    client.post(`/files/${articleId}/download/verify-captcha`, { captchaKey, captchaCode }),

  /**
   * 触发浏览器下载 ZIP 文件。
   * 后端返回 302 重定向到实际下载地址（本地 /storage/... 或 COS 预签名 URL）。
   * 浏览器会透明跟随重定向链并开始下载。
   *
   * @param {number} articleId
   * @param {string} [token] 大文件下载验证码 token（可选）
   * @returns {Promise<boolean>} true = 下载已触发
   */
  async download(articleId, token) {
    // 使用隐藏 <a> 标签触发 — 浏览器原生跟随 302 重定向
    // 错误情况（429 限流 / 404 不存在）由后端直接返回，浏览器会打开对应页面
    const link = document.createElement('a')
    link.href = this.downloadUrl(articleId, token)
    link.target = '_blank'
    link.rel = 'noopener'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    return true
  }
}
