import client from './client'

export const gameApi = {
  /** 获取全部游戏列表 */
  getAll: () => client.get('/games'),

  /** 获取单个游戏详情 */
  getById: (id) => client.get(`/games/${id}`)
}
