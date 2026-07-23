import client from './client'

export const messageApi = {
  getConversations: params => client.get('/messages/conversations', { params }),
  getItems: (id, params) => client.get(`/messages/conversations/${id}/items`, { params }),
  send: (targetId, body) => client.post(`/messages/conversations/${targetId}/items`, body),
  markRead: id => client.post(`/messages/conversations/${id}/read`),
  getUnreadCount: () => client.get('/messages/unread-count'),
  getImageUrl: (id, thumbnail = true) => client.get(`/messages/items/${id}/image-url`, { params: { thumbnail } }),
  getImage: (id, thumbnail = true) => client.get(`/messages/items/${id}/image`, { params: { thumbnail }, responseType: 'blob' }),
  waitEvent: (params, signal) => client.get('/messages/events', { params, timeout: 30000, signal })
}
