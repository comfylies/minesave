import client from './client'

export const articleApi = {
  /** 分页获取某游戏下的存档列表 */
  getByGame: (gameId, page = 0, size = 20) =>
    client.get(`/articles/game/${gameId}`, { params: { page, size } }),

  /** 分页获取某用户的存档列表 */
  getByUser: (userId, page = 0, size = 20) =>
    client.get(`/articles/user/${userId}`, { params: { page, size } }),

  /** 获取存档详情（含 readmeContent + readmeRaw） */
  getById: (id) =>
    client.get(`/articles/${id}`),

  /** 获取存档处理状态 */
  getStatus: (id) =>
    client.get(`/articles/${id}/status`),

  /** 上传存档（Multipart: metadata + file + 可选 readmeFile） */
  create: (formData) =>
    client.post('/articles', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000  // 上传大文件允许 60s
    }),

  /** 更新存档信息 */
  update: (id, data) =>
    client.put(`/articles/${id}`, data),

  /** 删除存档 */
  delete: (id) =>
    client.delete(`/articles/${id}`)
}
