import client from './client'

export const adminApi = {
  /** 获取仪表盘统计数据 */
  getDashboard() {
    return client.get('/admin/dashboard')
  },

  /** 获取用户列表 */
  listUsers(page = 0, size = 20, keyword = '') {
    return client.get('/admin/users', { params: { page, size, keyword } })
  },

  /** 封禁/解封用户 */
  toggleUserBan(userId) {
    return client.put(`/admin/users/${userId}/ban`)
  },

  /** 获取文章列表 */
  listArticles(page = 0, size = 20, keyword = '', status = '') {
    return client.get('/admin/articles', { params: { page, size, keyword, status } })
  },

  /** 删除文章 */
  deleteArticle(articleId) {
    return client.delete(`/admin/articles/${articleId}`)
  },

  // ==================== 游戏标准结构管理 ====================

  /** 上传游戏标准结构 ZIP */
  uploadSafeStructure(gameId, file) {
    const formData = new FormData()
    formData.append('file', file)
    return client.post(`/admin/games/${gameId}/safe-structure`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  /** 查询游戏标准结构状态 */
  getSafeStructure(gameId) {
    return client.get(`/admin/games/${gameId}/safe-structure`)
  },

  // ==================== 失败存档清理 ====================

  /** 手动触发清理 */
  triggerCleanup(mode = 'all') {
    return client.post('/admin/cleanup/trigger', null, { params: { mode } })
  },

  /** 查询清理进度 */
  getCleanupStatus() {
    return client.get('/admin/cleanup/status')
  }
}
