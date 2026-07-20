import client from './client'

export const userApi = {
  getById: (id) => client.get(`/users/${id}`),

  updateProfile: (data) => client.patch('/account/profile', data),

  uploadAvatar: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return client.post('/account/avatar', formData)
  },

  removeAvatar: () => client.delete('/account/avatar')
}
