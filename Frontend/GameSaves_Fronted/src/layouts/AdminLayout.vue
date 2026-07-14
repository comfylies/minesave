<template>
  <el-container class="admin-layout">
    <!-- 侧边栏 -->
    <el-aside width="220px" class="admin-aside">
      <div class="admin-logo">
        <el-icon :size="24"><Monitor /></el-icon>
        <span class="logo-text">管理后台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#334155"
        text-color="#b0bccf"
        active-text-color="#7db9fa"
        class="admin-menu"
      >
        <el-menu-item index="/admin">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/articles">
          <el-icon><Document /></el-icon>
          <span>文章管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/announcements">
          <el-icon><Bell /></el-icon>
          <span>公告管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/contact-messages">
          <el-icon><ChatDotSquare /></el-icon>
          <span>联系留言</span>
        </el-menu-item>
        <el-menu-item index="/admin/games">
          <el-icon><Platform /></el-icon>
          <span>游戏管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/cleanup">
          <el-icon><Delete /></el-icon>
          <span>存档清理</span>
        </el-menu-item>
        <el-menu-item index="/admin/ghosts">
          <el-icon><Warning /></el-icon>
          <span>幽灵文章</span>
        </el-menu-item>
        <el-menu-item index="/admin/site-settings">
          <el-icon><PictureFilled /></el-icon>
          <span>站点设置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 右侧主体 -->
    <el-container>
      <!-- 顶部栏 -->
      <el-header class="admin-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/admin' }">管理后台</el-breadcrumb-item>
            <el-breadcrumb-item v-if="pageTitle">{{ pageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-tag v-if="auth.currentUser" type="warning" effect="dark" class="header-username-tag">
            {{ auth.currentUser.username }}
          </el-tag>
          <el-button text size="small" @click="$router.push('/')">
            <el-icon><Back /></el-icon>
            返回前台
          </el-button>
        </div>
      </el-header>

      <!-- 主内容 -->
      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => route.meta?.title || '')
</script>

<style scoped>
.admin-layout {
  height: 100vh;
  background: var(--color-bg-secondary);
}

.admin-aside {
  background-color: #334155;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.06);
}

.admin-logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #f1f5f9;
  font-size: 18px;
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.logo-text {
  white-space: nowrap;
  letter-spacing: 0.5px;
}

.admin-menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
}

.admin-header {
  background: var(--color-bg-canvas);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-lg);
  box-shadow: var(--shadow-sm);
  height: 60px;
  z-index: 10;
  flex-shrink: 0;
}

.admin-header .header-left {
  display: flex;
  align-items: center;
}

.admin-header .header-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.header-username-tag {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-main {
  padding: var(--spacing-lg);
  overflow-y: auto;
  background: var(--color-bg-secondary);
}
</style>
