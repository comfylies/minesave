import client from './client'

export const userApi = {
  /** 用户登录（返回 LoginResponse: { tokenName, tokenValue, user }） */
  login: (login, password) =>
    client.post('/users/login', { login, password }),

  /** 用户注册（返回 UserResponse） */
  register: (data) =>
    client.post('/users/register', data),

  /** 获取用户信息 */
  getById: (id) =>
    client.get(`/users/${id}`),

  /** 更新用户信息 */
  update: (id, data) =>
    client.put(`/users/${id}`, data),

  /** 获取全部用户列表 */
  getAll: () =>
    client.get('/users')
}
