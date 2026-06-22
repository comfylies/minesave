import client from './client'

export const commentApi = {
  /** 获取文章所有批注 */
  getByArticle(articleId) {
    return client.get(`/comments/article/${articleId}`)
  },

  /** 创建批注 */
  create(data) {
    return client.post('/comments', data)
  },

  /** 删除批注 */
  delete(commentId, userId) {
    return client.delete(`/comments/${commentId}?userId=${userId}`)
  }
}
