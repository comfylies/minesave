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

  // ==================== 游戏合并 ====================

  /** 获取游戏管理列表（含封面、存档数、别名数、冲突） */
  getGames() {
    return client.get('/admin/games')
  },

  /** 合并两个游戏（source 合并到 target，source 被删除） */
  mergeGames(sourceId, targetId) {
    return client.post('/admin/games/merge', null, { params: { sourceId, targetId } })
  },

  /** 级联删除游戏及其所有存档 */
  deleteGame(gameId) {
    return client.delete(`/admin/games/${gameId}`)
  },

  // ==================== 文章批量操作 ====================

  /** 批量删除文章 */
  batchDeleteArticles(ids) {
    return client.delete('/admin/articles/batch', { data: ids })
  },

  // ==================== 幽灵文章诊断 ====================

  /** 扫描幽灵文章（数据库有记录但文件缺失） */
  scanGhostArticles() {
    return client.get('/admin/articles/ghosts')
  },

  /** 批量删除幽灵文章 */
  deleteGhostArticles(ids) {
    return client.delete('/admin/articles/ghosts', { data: ids })
  },

  // ==================== 审计日志 ====================

  /** 获取审计日志列表 */
  getAuditLogs(page = 0, size = 20) {
    return client.get('/admin/audit-logs', { params: { page, size } })
  },

  // ==================== 失败存档清理 ====================

  /** 手动触发清理 */
  triggerCleanup(mode = 'all') {
    return client.post('/admin/cleanup/trigger', null, { params: { mode } })
  },

  /** 查询清理进度 */
  getCleanupStatus() {
    return client.get('/admin/cleanup/status')
  },

  // ==================== 联系留言管理 ====================

  /** 获取联系留言列表 */
  listContactMessages(page = 0, size = 20, status = '') {
    return client.get('/admin/contact-messages', { params: { page, size, status } })
  },

  /** 获取单条联系留言详情 */
  getContactMessage(id) {
    return client.get(`/admin/contact-messages/${id}`)
  },

  /** 标记留言为已解决 */
  resolveContactMessage(id) {
    return client.put(`/admin/contact-messages/${id}/resolve`)
  },

  /** 关闭留言 */
  closeContactMessage(id) {
    return client.put(`/admin/contact-messages/${id}/close`)
  },

  /** 删除留言 */
  deleteContactMessage(id) {
    return client.delete(`/admin/contact-messages/${id}`)
  }
}
