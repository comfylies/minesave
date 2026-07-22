<template>
  <header class="navbar" :class="navbarClass">
    <div class="navbar-inner container">
      <!-- 左侧：Logo + 导航链接 -->
      <div class="navbar-left">
        <router-link to="/" class="navbar-logo">
          <span class="logo-icon">💾</span>
          <span class="logo-text">MineSave</span>
        </router-link>
        <nav class="navbar-nav">
          <router-link to="/" class="nav-link" active-class="nav-link--active" exact-active-class="nav-link--active">首页</router-link>
          <router-link to="/browse" class="nav-link" active-class="nav-link--active">浏览</router-link>
          <el-badge v-if="auth.isLoggedIn" :value="messageStore.unreadCount" :hidden="messageStore.unreadCount === 0" :max="99">
            <router-link to="/messages" class="nav-link" active-class="nav-link--active">消息</router-link>
          </el-badge>
          <router-link v-if="auth.isLoggedIn" to="/upload" class="nav-link" active-class="nav-link--active">上传存档</router-link>
        </nav>
      </div>

      <!-- 右侧：用户菜单 -->
      <div class="navbar-right">
        <template v-if="auth.isLoggedIn">
          <!-- 用户下拉菜单 -->
          <el-dropdown trigger="click" popper-class="user-dropdown">
            <el-badge :value="pendingBadgeCount" :hidden="pendingBadgeCount === 0" :max="99">
              <span class="user-trigger">
                <el-avatar :size="32" :src="auth.currentUser?.avatarSmallUrl || auth.currentUser?.avatarUrl" icon="UserFilled" />
                <span class="user-name">{{ auth.currentUser?.nickname || auth.currentUser?.username }}</span>
                <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
              </span>
            </el-badge>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="$router.push('/my-saves')">
                  <el-icon><FolderOpened /></el-icon> 我的存档
                </el-dropdown-item>
                <el-dropdown-item @click="$router.push('/account')">
                  <el-icon><Setting /></el-icon> 账户中心
                </el-dropdown-item>
                <el-dropdown-item @click="$router.push(`/users/${auth.userId}`)">
                  <el-icon><User /></el-icon> 个人主页
                </el-dropdown-item>
                <el-dropdown-item v-if="auth.isAdmin" @click="$router.push('/admin/contact-messages')">
                  <el-icon><ChatDotSquare /></el-icon> 联系留言
                  <el-badge v-if="pendingBadgeCount > 0" :value="pendingBadgeCount" style="margin-left: 8px" />
                </el-dropdown-item>
                <el-dropdown-item v-if="auth.isAdmin" divided @click="$router.push('/admin')">
                  <el-icon><Setting /></el-icon> 管理后台
                </el-dropdown-item>
                <el-dropdown-item :divided="!auth.isAdmin" @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <router-link to="/login" class="nav-link">登录</router-link>
          <router-link to="/register" class="btn-outline">注册</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref, computed, inject, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useContactBadge } from '../../composables/useContactBadge'
import { useMessageStore } from '../../stores/messages'
import { useMessageRealtime } from '../../composables/useMessageRealtime'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const messageStore = useMessageStore()
useMessageRealtime()
const { pendingCount: pendingBadgeCount, refresh: refreshBadge } = useContactBadge()
const hidden = ref(false)
const isAtTop = ref(true)

// 从 HomePage provide 注入是否有 Hero 背景图（决定透明导航栏文字颜色）
const hasHeroBg = inject('hasHeroBg', ref(false))

/** 是否在首页 */
const isHome = computed(() => route.path === '/')

// Smart Header: 下滑隐藏，上滑出现 + 首页透明/毛玻璃切换
let lastScrollY = 0
const SCROLL_THRESHOLD = 60
const HERO_THRESHOLD = 100

function onScroll() {
  const currentY = window.scrollY
  isAtTop.value = currentY < HERO_THRESHOLD

  if (currentY < SCROLL_THRESHOLD) {
    hidden.value = false
  } else if (currentY > lastScrollY) {
    hidden.value = true
  } else if (currentY < lastScrollY) {
    hidden.value = false
  }
  lastScrollY = currentY
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  // 管理员时刷新待处理联系留言计数（红点）
  if (auth.isAdmin) {
    refreshBadge()
  }
  if (auth.isLoggedIn) messageStore.refreshUnread().catch(() => {})
})
onUnmounted(() => window.removeEventListener('scroll', onScroll))

/** 动态 navbar class */
const navbarClass = computed(() => ({
  'navbar--hidden': hidden.value,
  'navbar--hero': isHome.value && isAtTop.value,
  'navbar--glass': isHome.value && !isAtTop.value,
  'navbar--has-bg': hasHeroBg.value
}))

function handleLogout() {
  auth.logout()
  ElMessage.success('已退出登录')
  router.push('/')
}
</script>

<style scoped>
.navbar {
  height: var(--header-height);
  background: var(--color-header-bg);
  border-bottom: 1px solid var(--color-header-border);
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  transform: translateY(0);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              background 0.3s ease,
              border-color 0.3s ease;
}

.navbar--hidden {
  transform: translateY(-100%);
}

/* 首页顶部 — 完全透明，白色文字 */
.navbar--hero {
  background: transparent;
  border-bottom-color: transparent;
}

/* 有背景图时白色文字 + 阴影 */
.navbar--hero.navbar--has-bg .navbar-logo,
.navbar--hero.navbar--has-bg .nav-link,
.navbar--hero.navbar--has-bg .user-name,
.navbar--hero.navbar--has-bg .dropdown-arrow {
  color: #fff;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.35);
}

.navbar--hero.navbar--has-bg .nav-link:hover {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
}

.navbar--hero.navbar--has-bg .btn-outline {
  color: #fff;
  border-color: rgba(255, 255, 255, 0.5);
  background: transparent;
}

.navbar--hero.navbar--has-bg .btn-outline:hover {
  background: rgba(255, 255, 255, 0.15);
}

/* 首页滚动后 — 毛玻璃半透明 */
.navbar--glass {
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
}

/* 首页两种状态下，按钮 hover 改为半透明 */
.navbar--hero .nav-link:hover,
.navbar--glass .nav-link:hover {
  background: rgba(128, 128, 128, 0.25);
}

.navbar--hero .user-trigger:hover,
.navbar--glass .user-trigger:hover {
  background: rgba(128, 128, 128, 0.20);
}

.navbar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
}

/* ---- 左侧 ---- */
.navbar-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  flex: 1;
}

.navbar-logo {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-xlarge);
  font-weight: 700;
  color: var(--color-header-logo);
  text-decoration: none;
  transition: color 0.3s ease;
}

.navbar-logo:hover {
  text-decoration: none;
}

.logo-icon {
  font-size: 24px;
}

/* ---- 导航链接 ---- */
.navbar-nav {
  display: flex;
  gap: var(--spacing-xs);
}

.nav-link {
  padding: var(--spacing-sm) var(--spacing-md);
  font-size: var(--font-size-normal);
  font-weight: 500;
  color: var(--color-header-text);
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast), color var(--transition-fast);
  text-decoration: none;
}

.nav-link:hover {
  background: var(--color-bg-secondary);
  color: var(--color-header-text-hover);
  text-decoration: none;
}

.nav-link--active {
  color: var(--color-link);
}

/* ---- 右侧 ---- */
.navbar-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-md);
  flex: 1;
}

.btn-outline {
  padding: 6px 16px;
  font-size: var(--font-size-normal);
  font-weight: 500;
  color: var(--color-btn-outline-text);
  background: var(--color-btn-outline-bg);
  border: 1px solid var(--color-btn-outline-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--transition-fast), color 0.3s ease, border-color 0.3s ease;
  text-decoration: none;
}

.btn-outline:hover {
  background: var(--color-btn-outline-hover);
  text-decoration: none;
}

/* ---- 用户区域 ---- */
.user-trigger {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  cursor: pointer;
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast);
}

.user-trigger:hover {
  background: var(--color-bg-secondary);
}

.user-name {
  font-size: var(--font-size-normal);
  font-weight: 500;
  color: var(--color-body-text);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.3s ease;
}

.dropdown-arrow {
  font-size: 12px;
  color: var(--color-secondary-text);
  transition: color 0.3s ease;
}
</style>
