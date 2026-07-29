import client from './client'

export const favoriteApi = {
  toggle: articleId => client.post(`/articles/${articleId}/favorite`),
  state: articleId => client.get(`/articles/${articleId}/my-favorite`),
  list: (page = 0, size = 20) => client.get('/favorites', { params: { page, size } })
}
