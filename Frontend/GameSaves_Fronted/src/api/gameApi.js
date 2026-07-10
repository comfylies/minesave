import client from './client'

export const gameApi = {
  /** 获取全部游戏列表 */
  getAll: () => client.get('/games'),

  /** 获取单个游戏详情 */
  getById: (id) => client.get(`/games/${id}`),

  /** 搜索游戏（含别名） */
  search: (query) => client.get('/games/search', { params: { q: query } }),

  /** 首页 Top-N：热门/最新 */
  getTop: (sort = 'newest', limit = 4) =>
    client.get('/games/top', { params: { sort, limit } }),

  /** 创建新游戏 */
  create: (data) => client.post('/games', data)
}
