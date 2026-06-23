import client from './client'

export const searchApi = {
  /** 统一搜索 */
  search: (q, page = 0, size = 20) =>
    client.get('/search', { params: { q, page, size } })
}
