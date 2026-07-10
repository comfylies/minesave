<template>
  <header class="navbar" :class="{ 'navbar--hidden': hidden }">
    <div class="navbar-inner container">
      <!-- 左侧：Logo + 导航链接 -->
      <div class="navbar-left">
        <router-link to="/" class="navbar-logo">
          <span class="logo-icon">💾</span>
          <span class="logo-text">MineSave</span>
        </router-link>
        <nav class="navbar-nav">
          <router-link to="/" class="nav-link" active-class="nav-link--active">首页</router-link>
          <router-link to="/" class="nav-link">浏览</router-link>
          <router-link v-if="auth.isLoggedIn" to="/upload" class="nav-link" active-class="nav-link--active">上传存档</router-link>
        </nav>
      </div>

      <!-- 中间：搜索框 -->
      <div class="navbar-center">
        <div class="search-box">
          <el-icon class="search-icon"><Search /></el-icon>
          <input
            v-model="searchQuery"
            class="search-input"
            placeholder="搜索游戏、存档..."
            @keyup.enter="doSearch"
          />
        </div>
      </div>

      <!-- 右侧：用户菜单 -->
      <div class="navbar-right">
        <template v-if="auth.isLoggedIn">
          <!-- 用户下拉菜单 -->
          <el-dropdown trigger="click" popper-class="user-dropdown">
            <span class="user-trigger">
              <el-avatar :size="32" :src="auth.currentUser?.avatarUrl" icon="UserFilled" />
              <span class="user-name">{{ auth.currentUser?.nickname || auth.currentUser?.username }}</span>
              <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="$router.push('/my-saves')">
                  <el-icon><FolderOpened /></el-icon> 我的存档
                </el-dropdown-item>
                <el-dropdown-item @click="$router.push(`/users/${auth.userId}`)">
                  <el-icon><User /></el-icon> 个人主页
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
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const auth = useAuthStore()
const searchQuery = ref('')
const hidden = ref(false)

// Smart Header: 下滑隐藏，上滑出现
let lastScrollY = 0
const SCROLL_THRESHOLD = 60

function onScroll() {
  const currentY = window.scrollY
  if (currentY < SCROLL_THRESHOLD) {
    hidden.value = false
  } else if (currentY > lastScrollY) {
    hidden.value = true
  } else if (currentY < lastScrollY) {
    hidden.value = false
  }
  lastScrollY = currentY
}

onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))
onUnmounted(() => window.removeEventListener('scroll', onScroll))

function doSearch() {
  const q = searchQuery.value.trim()
  if (q) {
    router.push({ path: '/search', query: { q } })
  }
}

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
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.navbar--hidden {
  transform: translateY(-100%);
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

/* ---- 中间搜索 ---- */
.navbar-center {
  display: flex;
  justify-content: center;
  padding: 0 16px;
  flex-shrink: 0;
}

.search-box {
  position: relative;
  width: 400px;
}

.search-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-secondary-text);
  font-size: 16px;
}

.search-input {
  width: 100%;
  height: 36px;
  padding: 0 12px 0 36px;
  font-size: var(--font-size-normal);
  color: var(--color-body-text);
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border-secondary);
  border-radius: 18px;
  outline: none;
  transition: border-color var(--transition-fast), background var(--transition-fast);
}

.search-input:focus {
  background: var(--color-bg-canvas);
  border-color: var(--color-link);
}

.search-input::placeholder {
  color: var(--color-secondary-text);
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
  transition: background var(--transition-fast);
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
}

.dropdown-arrow {
  font-size: 12px;
  color: var(--color-secondary-text);
}
</style>
