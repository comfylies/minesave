import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('../layouts/DefaultLayout.vue'),
    children: [
      { path: '', name: 'Home', component: () => import('../views/HomePage.vue'), meta: { noHeaderOffset: true } },
      { path: 'browse', name: 'Browse', component: () => import('../views/BrowsePage.vue'), meta: { wide: true } },
      { path: 'games/:gameId', name: 'Game', component: () => import('../views/GamePage.vue'), meta: { wide: true } },
      { path: 'articles/:articleId', name: 'Article', component: () => import('../views/ArticlePage.vue') },
      { path: 'my-saves', name: 'MySaves', component: () => import('../views/MySavesPage.vue'), meta: { requiresAuth: true } },
      { path: 'users/:userId', name: 'UserProfile', component: () => import('../views/UserProfilePage.vue') },
      { path: 'upload', name: 'Upload', component: () => import('../views/UploadPage.vue'), meta: { requiresAuth: true } }
    ]
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginPage.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/RegisterPage.vue')
  },
  // 搜索
  {
    path: '/search',
    component: () => import('../layouts/DefaultLayout.vue'),
    children: [
      { path: '', name: 'Search', component: () => import('../views/SearchPage.vue'), meta: { wide: true } }
    ]
  },
  // 管理后台路由
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', name: 'AdminDashboard', component: () => import('../views/admin/AdminDashboard.vue'), meta: { title: '仪表盘' } },
      { path: 'users', name: 'AdminUsers', component: () => import('../views/admin/AdminUsers.vue'), meta: { title: '用户管理' } },
      { path: 'articles', name: 'AdminArticles', component: () => import('../views/admin/AdminArticles.vue'), meta: { title: '文章管理' } },
      { path: 'announcements', name: 'AdminAnnouncements', component: () => import('../views/admin/AdminAnnouncements.vue'), meta: { title: '公告管理' } },
      { path: 'games', name: 'AdminGames', component: () => import('../views/admin/AdminGames.vue'), meta: { title: '游戏管理' } },
      { path: 'cleanup', name: 'AdminCleanup', component: () => import('../views/admin/AdminCleanup.vue'), meta: { title: '存档清理' } },
      { path: 'ghosts', name: 'AdminGhosts', component: () => import('../views/admin/AdminGhosts.vue'), meta: { title: '幽灵文章' } },
      { path: 'site-settings', name: 'AdminSiteSettings', component: () => import('../views/admin/AdminSiteSettings.vue'), meta: { title: '站点设置' } }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFoundPage.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

// 导航守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('satoken')
  const requiresAuth = to.matched.some(r => r.meta.requiresAuth)
  const requiresAdmin = to.matched.some(r => r.meta.requiresAdmin)

  // 需要登录的页面检查
  if (requiresAuth && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  // 管理后台检查 admin 角色
  if (requiresAdmin) {
    try {
      const userStr = localStorage.getItem('currentUser')
      if (!userStr || userStr === 'null' || userStr === 'undefined') {
        next({ name: 'Home' })
        return
      }
      const user = JSON.parse(userStr)
      if (user.role !== 'admin') {
        next({ name: 'Home' })
        return
      }
    } catch {
      next({ name: 'Home' })
      return
    }
  }

  next()
})

export default router
