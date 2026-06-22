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
  }
}
