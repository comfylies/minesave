import client from './client'

export const contactApi = {
  /** 提交联系留言 */
  submit(data) {
    return client.post('/contact', data)
  },

  /** 上传留言中的图片 */
  uploadImage(file) {
    const formData = new FormData()
    formData.append('file', file)
    return client.post('/contact/upload-image', formData)
  },

  /** 获取待处理留言数量（导航栏红点用） */
  getPendingCount() {
    return client.get('/contact/pending-count')
  }
}
