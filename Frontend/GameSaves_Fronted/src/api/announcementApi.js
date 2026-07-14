import client from './client'

export const announcementApi = {
  /** 公开：获取所有启用的公告 */
  getActive() {
    return client.get('/announcements/active')
  },

  /** 管理员：获取所有公告 */
  listAll() {
    return client.get('/admin/announcements')
  },

  /** 管理员：创建公告 */
  create(data) {
    return client.post('/admin/announcements', data)
  },

  /** 管理员：更新公告 */
  update(id, data) {
    return client.put(`/admin/announcements/${id}`, data)
  },

  /** 管理员：删除公告 */
  delete(id) {
    return client.delete(`/admin/announcements/${id}`)
  },

  /** 管理员：切换启用/禁用 */
  toggleActive(id) {
    return client.put(`/admin/announcements/${id}/toggle`)
  },

  /** 管理员：上传公告图片 */
  uploadImage(file) {
    const formData = new FormData()
    formData.append('file', file)
    return client.post('/admin/announcements/upload-image', formData)
  },

  /** 管理员：上传 .md 文件，返回内容 */
  uploadMd(file) {
    const formData = new FormData()
    formData.append('file', file)
    return client.post('/admin/announcements/upload-md', formData)
  }
}
