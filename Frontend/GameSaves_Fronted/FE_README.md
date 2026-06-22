# GameSaves 前端架构文档

## 目录

1. [技术栈](#技术栈)
2. [项目结构](#项目结构)
3. [架构概览](#架构概览)
4. [路由设计](#路由设计)
5. [状态管理 (Pinia Stores)](#状态管理-pinia-stores)
6. [API 层设计](#api-层设计)
7. [组件树](#组件树)
8. [非显而易见代码说明](#非显而易见代码说明)
9. [开发指南](#开发指南)

---

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Vue | 3.x | Composition API (`<script setup>`) |
| Vite | 5.x | 构建工具 + 开发服务器 |
| Vue Router | 4.x | SPA 路由 |
| Pinia | 2.x | 状态管理 |
| Axios | 1.x | HTTP 客户端 |
| Element Plus | 2.x | UI 组件库 |
| highlight.js | 11.x | 代码高亮 |

## 项目结构

```
Frontend/GameSaves_Fronted/src/
├── main.js                             # Vue 入口
├── App.vue                             # 根组件
├── api/
│   ├── client.js                       # ★ Axios 实例 + 响应拦截器
│   ├── articleApi.js                   # 文章 API（上传/CRUD/状态轮询）
│   ├── commentApi.js                   # 批注 API
│   ├── fileApi.js                      # ★ 文件 API（浏览/预览/下载）
│   ├── gameApi.js                      # 游戏 API
│   └── userApi.js                      # 用户 API（登录/注册）
├── stores/                             # Pinia 状态管理
│   ├── auth.js                         # 用户认证状态
│   ├── articles.js                     # 文章列表/详情状态
│   ├── files.js                        # 文件浏览器状态
│   └── games.js                        # 游戏列表状态
├── router/
│   └── index.js                        # ★ 路由配置 + 导航守卫
├── views/                              # 页面级组件
│   ├── HomePage.vue                    # 首页（游戏列表）
│   ├── LoginPage.vue                   # 登录页
│   ├── RegisterPage.vue                # 注册页
│   ├── GamePage.vue                    # 游戏详情 + 存档列表
│   ├── ArticlePage.vue                 # ★ 存档详情（文件浏览+README+批注）
│   ├── UploadPage.vue                  # ★ 上传存档（ZIP + README 双模式）
│   ├── MySavesPage.vue                 # 我的存档
│   ├── UserProfilePage.vue             # 用户主页
│   └── NotFoundPage.vue                # 404
├── components/
│   ├── common/                         # 通用组件
│   │   ├── AppNavbar.vue               # 顶部导航栏
│   │   ├── AppFooter.vue               # 底部
│   │   ├── EmptyState.vue              # 空状态占位
│   │   └── LoadingSkeleton.vue         # 加载骨架屏
│   ├── game/
│   │   └── GameCard.vue                # 游戏卡片
│   └── article/                        # 存档相关组件
│       ├── ArticleMeta.vue             # ★ 存档元信息 + 下载按钮
│       ├── FileBrowser.vue             # ★ GitHub 风格文件浏览器
│       ├── FileIcon.vue                # 文件类型图标
│       ├── BreadcrumbNav.vue           # 面包屑导航
│       ├── AnnotationPanel.vue         # 批注侧边栏
│       └── ReadmeRenderer.vue          # Markdown 渲染区
└── assets/styles/                      # 全局样式
    ├── global.css
    ├── variables.css
    └── github-markdown.css
```

## 架构概览

```
┌────────────────────────────────────────────────────┐
│                    Vue Router                        │
│  /login → LoginPage    / → HomePage                 │
│  /register → RegisterPage                           │
│  /games/:gameId → GamePage                          │
│  /articles/:articleId → ArticlePage                  │
│  /upload → UploadPage    /my-saves → MySavesPage    │
│  /users/:userId → UserProfilePage                   │
└──────────┬─────────────────────────────────────────┘
           │
           ▼
┌────────────────────────────────────────────────────┐
│                  Pinia Stores                        │
│  auth ─── currentUser, isLoggedIn, login/register   │
│  articles ─── articleList, currentArticle           │
│  files ─── files, directories, breadcrumbs          │
│  games ─── games list                                │
└──────────┬─────────────────────────────────────────┘
           │
           ▼
┌────────────────────────────────────────────────────┐
│               API Layer (Axios)                      │
│  client.js ─── 统一 baseURL=/api, 拦截器解包         │
│  Response interceptor:                              │
│    • ApiResponse<T> → 自动解包 data.data             │
│    • 二进制响应 → 透传完整 response（用于下载）       │
│    • 错误 → ElMessage.error + Promise.reject         │
└──────────┬─────────────────────────────────────────┘
           │
           ▼
┌────────────────────────────────────────────────────┐
│           Vite Dev Proxy → localhost:8080            │
└────────────────────────────────────────────────────┘
```

## 路由设计

```javascript
// 路由表
'/'                    → HomePage         # 首页
'/games/:gameId'       → GamePage         # 游戏详情 + 存档列表
'/articles/:articleId' → ArticlePage      # 存档详情
'/my-saves'            → MySavesPage      # 我的存档（需登录）
'/upload'              → UploadPage       # 上传存档（需登录）
'/users/:userId'       → UserProfilePage  # 用户主页
'/login'               → LoginPage        # 登录
'/register'            → RegisterPage     # 注册
'/:pathMatch(.*)*'     → NotFoundPage     # 404

// 导航守卫
router.beforeEach((to, from, next) => {
  // 检查 localStorage.currentUser
  // requiresAuth 路由未登录 → 重定向到 /login?redirect=原路径
})
```

## 状态管理 (Pinia Stores)

### authStore (`stores/auth.js`)

```javascript
state: {
  currentUser: null | UserResponse   // 从 localStorage 恢复
}
getters: {
  isLoggedIn    // !!currentUser
  userId         // currentUser?.id
  isAdmin        // currentUser?.role === 'admin'
}
actions: {
  login(login, password)    // POST /api/users/login → 存 localStorage
  register(data)            // POST /api/users/register → 存 localStorage
  logout()                  // 清 localStorage + currentUser
}
```

**关键细节**：当前无 JWT Token，登录成功后将整个 `UserResponse` 对象存入 `localStorage`。`loadUser()` 在 store 初始化时从 `localStorage` 恢复。导航守卫通过检查 `localStorage.currentUser` 判断登录状态（不依赖 Pinia 响应式，避免刷新闪烁）。

### articleStore (`stores/articles.js`)

```javascript
state: {
  articleList: [],           // 列表页数据
  currentArticle: null,      // 详情页数据
  pagination: { page, size, totalElements },
  loading: false,
  error: null
}
actions: {
  fetchByGame(gameId, page)  // GET /api/articles/game/{gameId}
  fetchByUser(userId, page)  // GET /api/articles/user/{userId}
  fetchArticle(id)           // GET /api/articles/{id}
  deleteArticle(id)          // DELETE /api/articles/{id}
}
```

### fileStore (`stores/files.js`)

```javascript
state: {
  currentPath: '',           // 当前浏览路径
  files: [],                 // 文件列表
  directories: [],           // 子目录列表
  breadcrumbs: [],           // 面包屑路径
  loading: false,
  error: null
}
actions: {
  browse(articleId, path)    // GET /api/files/{id}/browse?path=
  reset()                    // 切换文章时清空
}
```

## API 层设计

### client.js — Axios 拦截器

```javascript
// 请求
baseURL: '/api'
timeout: 30000

// 响应拦截器（★ 核心逻辑）
response => {
  const contentType = response.headers['content-type']

  // 规则 1: 二进制/文本响应 → 透传完整 response 对象
  //   用于: 文件下载 (blob)、文本预览 (text/plain)
  if (contentType.includes('text/plain') ||
      contentType.includes('application/octet-stream') ||
      contentType.includes('application/zip')) {
    return response   // ← 注意：返回整个 response，不是 response.data
  }

  // 规则 2: ApiResponse 包装 → 解包
  //   后端统一格式 { code: 200, message: "...", data: {...} }
  //   拦截器自动提取 data.data
  const data = response.data
  if (data && typeof data.code === 'number') {
    if (data.code === 200) return data.data     // ← 只返回业务数据
    ElMessage.error(data.message)
    return Promise.reject(new Error(data.message))
  }

  return data
}

// 错误拦截器
error => {
  // HTTP 4xx/5xx → ElMessage.error
  const msg = error.response?.data?.message || error.message
  ElMessage.error(msg)
  return Promise.reject(error)
}
```

### fileApi.download() — JS 触发浏览器下载

```javascript
// 为什么不用 <a href>：无法处理错误（限流、404 等）
async download(articleId) {
  // 1. Axios 请求 blob 响应
  const response = await client.get(`/files/${articleId}/download`, {
    responseType: 'blob'
  })

  // 2. 从 Content-Disposition 解析文件名
  const blob = response.data
  const disposition = response.headers['content-disposition']
  const filename = parseFilename(disposition) || `archive-${articleId}.zip`

  // 3. 创建 Blob URL → <a> click → 回收 URL
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url; link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
```

## 组件树

### ArticlePage 布局

```
ArticlePage
├── LoadingSkeleton           (loading 状态)
├── EmptyState                (error 状态)
└── (数据就绪)
    ├── ArticleMeta           # 标题/版本/作者/统计 + 下载按钮
    ├── .article-body
    │   ├── .article-main
    │   │   ├── FileBrowser   # 文件表格 + 面包屑
    │   │   │   ├── BreadcrumbNav
    │   │   │   └── FileIcon (per row)
    │   │   └── ReadmeRenderer # Markdown HTML 渲染
    │   └── .article-sidebar
    │       └── AnnotationPanel # 批注列表
    └── el-dialog (预览)
        └── <pre><code>     # highlight.js 代码高亮
```

### UploadPage README 双模式

```
UploadPage
├── el-form
│   ├── el-select (游戏选择)
│   ├── el-input (标题/版本)
│   ├── el-input textarea (描述)
│   ├── README 区域
│   │   ├── el-radio-group (手写 Markdown | 上传 .md)
│   │   ├── [手写] el-input textarea (Markdown 输入)
│   │   └── [上传] el-upload (.md 拖拽)
│   └── el-upload (ZIP 拖拽)
└── 处理进度 (el-alert + el-progress)
```

## 非显而易见代码说明

### 响应拦截器的双模式处理

> `api/client.js`

拦截器根据 `Content-Type` 分流：
- **JSON 响应**（application/json）→ 解包 `ApiResponse`，提取 `data.data`，页面直接拿到业务对象
- **二进制/文本响应**（blob, text/plain, zip）→ 返回完整 `response` 对象（含 headers），调用方自行处理

这允许 `fileApi.download()` 从 `response.headers['content-disposition']` 解析文件名，同时 `fileApi.previewUrl()` 返回文本内容。

### `Number(route.params.id) || 0` — NaN 防护

> `views/ArticlePage.vue`, `views/GamePage.vue`

Vue Router 参数是字符串。`Number(undefined)` → `NaN` → 后端收到 `GET /api/articles/NaN` → 500。`|| 0` 兜底让请求发不出去（或后端返回 400 而非 500）。

### 登录状态持久化

> `stores/auth.js` + `router/index.js`

- **Store 初始化**：`loadUser()` 从 `localStorage.currentUser` 恢复
- **导航守卫**：直接读 `localStorage`（不依赖 Pinia 响应式，避免刷新时闪烁重定向）
- **登录成功后**：同时写 `localStorage` + Pinia state

### 上传 FormData 组装

> `views/UploadPage.vue` `handleUpload()`

```javascript
// metadata JSON 部分用 File 对象（非 Blob）确保浏览器设置正确的 Content-Type
const jsonStr = JSON.stringify(metadata)
formData.append('metadata', new File([jsonStr], 'metadata.json', { type: 'application/json' }))
formData.append('file', selectedFile.value)
// 可选 README 文件
if (readmeMode === 'upload' && selectedReadmeFile) {
  formData.append('readmeFile', selectedReadmeFile)
}
```

为什么用 `File` 而非 `Blob`：某些浏览器对 `Blob` 在 FormData 中的 Content-Type 处理不一致，`File` 对象更可靠。

### 下载按钮防抖

> `components/article/ArticleMeta.vue`

```javascript
const downloading = ref(false)
async function handleDownload() {
  downloading.value = true
  try { await fileApi.download(props.article.id) }
  finally { downloading.value = false }
}
// 按钮: :disabled="downloading"
```

点击后禁用按钮防止重复请求触发限流。

## 开发指南

### 启动命令

```bash
cd Frontend/GameSaves_Fronted
npm install        # 首次
npm run dev        # 启动开发服务器 (localhost:3000)
```

### Vite 代理配置

```javascript
// vite.config.js
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',  // 后端地址
      changeOrigin: true
    }
  }
}
```

所有 `/api/*` 请求代理到后端，开发时无 CORS 问题。

### 添加新页面

1. `views/` 创建 `.vue` 文件
2. `router/index.js` 注册路由
3. 如需状态管理 → `stores/` 创建 store
4. 如需 API → `api/` 添加方法

### 添加新 API 方法

```javascript
// api/xxxApi.js
import client from './client'
export const xxxApi = {
  getList: () => client.get('/xxx'),
  create: (data) => client.post('/xxx', data),
}
// 响应自动解包 ApiResponse，页面直接拿到业务数据
```
