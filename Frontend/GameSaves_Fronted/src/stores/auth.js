import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../api/authApi'
import { userApi } from '../api/userApi'

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref(loadUser())
  const token = ref(loadToken())

  function loadUser() {
    try {
      const stored = localStorage.getItem('currentUser')
      if (stored && stored !== 'null' && stored !== 'undefined') {
        return JSON.parse(stored)
      }
    } catch (e) {
      // ignore
    }
    return null
  }

  function loadToken() {
    return localStorage.getItem('satoken') || null
  }

  const isLoggedIn = computed(() => !!token.value && !!currentUser.value)
  const userId = computed(() => currentUser.value?.id)
  const isAdmin = computed(() => currentUser.value?.role === 'admin')

  function saveAuth(loginResponse) {
    token.value = loginResponse.tokenValue
    currentUser.value = loginResponse.user
    localStorage.setItem('satoken', loginResponse.tokenValue)
    localStorage.setItem('currentUser', JSON.stringify(loginResponse.user))
  }

  /** 账号密码登录（带图形验证码） */
  async function login(login, password, captchaKey, captchaCode) {
    const result = await authApi.login({ login, password, captchaKey, captchaCode })
    saveAuth(result)
    return result
  }

  /** 邮箱验证码登录 */
  async function loginWithEmail(email, code) {
    const result = await authApi.loginWithEmail({ email, code })
    saveAuth(result)
    return result
  }

  async function register(data, captchaKey, captchaCode) {
    const result = await authApi.register(data, captchaKey, captchaCode)
    saveAuth(result)
    return result
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch (e) {
      // ignore
    }
    currentUser.value = null
    token.value = null
    localStorage.removeItem('currentUser')
    localStorage.removeItem('satoken')
  }

  /** 检查登录状态（从服务器验证） */
  async function checkAuth() {
    if (!token.value) return false
    try {
      const user = await authApi.checkLogin()
      currentUser.value = user
      localStorage.setItem('currentUser', JSON.stringify(user))
      return true
    } catch (e) {
      logout()
      return false
    }
  }

  return {
    currentUser, token, isLoggedIn, userId, isAdmin,
    login, loginWithEmail, register, logout, checkAuth, saveAuth
  }
})
