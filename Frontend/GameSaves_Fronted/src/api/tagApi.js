import client from './client'

export const tagApi = {
  /** 获取所有标签（可选过滤 source=admin / source=user） */
  getAll: (params = {}) => client.get('/tags', { params }),

  /** 模糊搜索标签 */
  search: (keyword) => client.get('/tags', { params: { q: keyword } }),

  /** 创建用户标签（需登录） */
  create: (name) => client.post('/tags', { name, source: 'user' }),

  /** Admin: 创建预设标签 */
  createPreset: (name) => client.post('/admin/tags', { name, source: 'admin' }),

  /** Admin: 更新标签 */
  update: (id, data) => client.put(`/admin/tags/${id}`, data),

  /** Admin: 删除标签 */
  delete: (id) => client.delete(`/admin/tags/${id}`)
}
