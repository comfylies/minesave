import client from './client'

export const authApi = {
  /** 获取图形验证码 */
  getCaptcha: () =>
    client.get('/auth/captcha'),

  /** 账号密码登录（带验证码） */
  login: ({ login, password, captchaKey, captchaCode }) =>
    client.post('/auth/login', { login, password, captchaKey, captchaCode }),

  /** 邮箱验证码登录 */
  loginWithEmail: ({ email, code }) =>
    client.post('/auth/login/email', { email, code }),

  /** 发送邮箱验证码（需先通过图形验证码） */
  sendEmailCode: (email, captchaKey, captchaCode) =>
    client.post('/auth/email-code', { email, captchaKey, captchaCode }),

  /** 用户注册（需图形验证码 + 邮箱验证码） */
  register: (data, captchaKey, captchaCode) =>
    client.post('/auth/register', data, {
      params: { captchaKey, captchaCode }
    }),

  /** 退出登录 */
  logout: () =>
    client.post('/auth/logout'),

  /** 检查登录状态 */
  checkLogin: () =>
    client.get('/auth/check')
}
