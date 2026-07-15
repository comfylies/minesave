import client from './client'

export const voteApi = {
  /** 获取当前用户对某文章的投票状态 */
  getMyVote(articleId) {
    return client.get(`/articles/${articleId}/my-vote`)
  },

  /** 投票：voteType = 'UP' | 'DOWN' */
  vote(articleId, voteType) {
    return client.post(`/articles/${articleId}/vote`, { voteType })
  }
}
