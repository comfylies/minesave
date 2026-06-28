<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const router = useRouter()
const auth = useAuthStore()

/**
 * 应用启动时主动验证 localStorage 中的 token 是否仍然有效。
 * 后端重启后 Sa-Token 内存会话会丢失，前端 localStorage 里还留着旧 token，
 * 不主动检查的话用户要等到真正调用需登录的接口才会发现（体验差）。
 */
onMounted(async () => {
  if (!auth.token) return

  // 登录/注册页不检查（用户可能是刚登录完，token 还没存到 localStorage 就跳转了）
  const publicAuthPaths = ['/login', '/register']
  if (publicAuthPaths.includes(router.currentRoute.value.path)) return

  const ok = await auth.checkAuth()
  if (!ok) {
    // checkAuth 内部已经调了 logout() 清 localStorage
    router.push({ name: 'Login', query: { redirect: router.currentRoute.value.fullPath } })
  }
})
</script>
