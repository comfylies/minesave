import client from './client'

export const siteSettingsApi = {
  /** 获取公开站点设置（如首页背景图） */
  get: () => client.get('/site-settings'),

  /** 管理员获取所有站点设置 */
  getAdmin: () => client.get('/admin/site-settings'),

  /** 管理员批量更新站点设置 */
  update: (settings) => client.put('/admin/site-settings', settings),

  /** 上传首页背景图片 */
  uploadBackground: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return client.post('/admin/site-settings/upload-background', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}
