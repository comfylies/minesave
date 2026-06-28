import client from './client'

export const gameApi = {
  /** 获取全部游戏列表 */
  getAll: () => client.get('/games'),

  /** 获取单个游戏详情 */
  getById: (id) => client.get(`/games/${id}`),

  /** 搜索游戏（含别名） */
  search: (query) => client.get('/games/search', { params: { q: query } }),

  /** 创建新游戏 */
  create: (data) => client.post('/games', data)
}
