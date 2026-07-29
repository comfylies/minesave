import client from './client'

export const followApi = {
  toggle: userId => client.post(`/users/${userId}/follow`),
  state: userId => client.get(`/users/${userId}/my-following`),
  list: (page = 0, size = 20) => client.get('/follows', { params: { page, size } })
}
