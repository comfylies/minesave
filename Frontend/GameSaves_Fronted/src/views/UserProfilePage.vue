<template>
  <div class="profile-page">
    <!-- 加载中 -->
    <LoadingSkeleton v-if="loadingUser" :rows="6" />

    <!-- 错误 -->
    <div v-else-if="userError" class="profile-error">
      <EmptyState description="加载用户信息失败">
        <el-button type="primary" @click="loadUserData">重试</el-button>
      </EmptyState>
    </div>

    <template v-else-if="user">
      <!-- 用户信息头部 -->
      <div class="profile-header">
        <el-avatar :size="80" :src="user.avatarUrl" icon="UserFilled" class="profile-avatar" />
        <div class="profile-info">
          <h1 class="profile-name">{{ user.nickname || user.username }}</h1>
          <p class="profile-username">@{{ user.username }}</p>
          <p v-if="user.bio" class="profile-bio">{{ user.bio }}</p>
          <div class="profile-meta">
            <span class="meta-item">
              <el-icon><Calendar /></el-icon>
              {{ formatDate(user.createdAt) }} 加入
            </span>
            <span v-if="user.role === 'admin'" class="meta-badge admin-badge">管理员</span>
          </div>
        </div>
      </div>

      <div class="profile-divider" />

      <!-- 用户存档列表 -->
      <div class="section-header">
        <h2 class="section-title">{{ user.nickname || user.username }} 的存档</h2>
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="list">
            <el-icon><List /></el-icon>
            列表
          </el-radio-button>
          <el-radio-button value="gallery">
            <el-icon><Grid /></el-icon>
            画廊
          </el-radio-button>
        </el-radio-group>
      </div>

      <LoadingSkeleton v-if="articleStore.loading" :rows="8" />

      <template v-else>
        <div v-if="articleStore.articleList.length > 0">
          <!-- 列表视图 -->
          <table v-if="viewMode === 'list'" class="data-table article-table">
            <thead>
              <tr>
                <th class="th-cover"></th>
                <th style="width: 36%">标题</th>
                <th style="width: 15%">游戏</th>
                <th style="width: 10%">版本</th>
                <th style="width: 8%">大小</th>
                <th style="width: 8%">下载</th>
                <th style="width: 18%">时间</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="article in articleStore.articleList"
                :key="article.id"
                class="article-row"
                @click="$router.push(`/articles/${article.id}`)"
              >
                <td class="col-cover">
                  <div class="cover-thumb">
                    <img
                      v-if="article.coverImage"
                      :src="thumbUrl(article.coverImage, 270)"
                      :alt="article.title"
                      class="cover-thumb-img"
                      loading="lazy"
                      @error="e => e.target.style.display = 'none'"
                    />
                    <el-icon v-else :size="20"><FolderOpened /></el-icon>
                  </div>
                </td>
                <td>
                  <span class="article-title-link">{{ article.title }}</span>
                </td>
                <td>
                  <router-link
                    :to="`/games/${article.gameId}`"
                    class="article-game"
                    @click.stop
                  >
                    {{ article.gameName }}
                  </router-link>
                </td>
                <td>
                  <span class="article-version">{{ article.version }}</span>
                </td>
                <td class="col-number">{{ formatSize(article.fileSize) }}</td>
                <td class="col-number">{{ article.downloadCount || 0 }}</td>
                <td class="col-time time-ago">{{ formatTime(article.createdAt) }}</td>
              </tr>
            </tbody>
          </table>

          <!-- 画廊视图 -->
          <div v-else class="gallery-grid">
            <ArticleCard
              v-for="article in articleStore.articleList"
              :key="article.id"
              :article="article"
            />
          </div>

          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="articleStore.pagination.size"
              :total="articleStore.pagination.totalElements"
              layout="prev, pager, next"
              @current-change="handlePageChange"
            />
          </div>
        </div>

        <EmptyState v-else :description="isSelf ? '你还没有上传存档' : '该用户暂无存档'">
          <el-button v-if="isSelf" type="primary" @click="$router.push('/upload')">上传存档</el-button>
        </EmptyState>
      </template>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { userApi } from '../api/userApi'
import { useArticleStore } from '../stores/articles'
import { useAuthStore } from '../stores/auth'
import { useViewMode } from '../composables/useViewMode'
import EmptyState from '../components/common/EmptyState.vue'
import LoadingSkeleton from '../components/common/LoadingSkeleton.vue'
import ArticleCard from '../components/article/ArticleCard.vue'
import { thumbUrl } from '../utils/imageUrl'

const route = useRoute()
const articleStore = useArticleStore()
const auth = useAuthStore()
const { viewMode } = useViewMode()

const user = ref(null)
const loadingUser = ref(false)
const userError = ref(null)
const currentPage = ref(1)

const userId = computed(() => Number(route.params.userId))
const isSelf = computed(() => auth.userId === userId.value)

function formatSize(bytes) {
  if (bytes == null || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0; let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

function formatTime(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr); const now = new Date()
  const diff = now - date
  const mins = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  if (hours < 24) return `${hours} 小时前`
  if (days < 30) return `${days} 天前`
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

function handlePageChange(page) {
  currentPage.value = page
  articleStore.fetchByUser(userId.value, page - 1)
}

async function loadUserData() {
  loadingUser.value = true
  userError.value = null
  try {
    user.value = await userApi.getById(userId.value)
    await articleStore.fetchByUser(userId.value, 0)
  } catch (e) {
    userError.value = e.message
  } finally {
    loadingUser.value = false
  }
}

onMounted(loadUserData)

watch(() => route.params.userId, () => {
  currentPage.value = 1
  loadUserData()
})
</script>

<style scoped>
.profile-page {
  padding: var(--spacing-lg) 0;
}

.profile-error {
  padding: var(--spacing-xxl) 0;
}

/* ---- 头部 ---- */
.profile-header {
  display: flex;
  gap: var(--spacing-lg);
  align-items: flex-start;
}

.profile-avatar {
  flex-shrink: 0;
  border: 1px solid var(--color-border-primary);
}

.profile-info {
  flex: 1;
}

.profile-name {
  font-size: var(--font-size-title);
  font-weight: 600;
  color: var(--color-body-text);
}

.profile-username {
  font-size: var(--font-size-xlarge);
  color: var(--color-secondary-text);
}

.profile-bio {
  font-size: var(--font-size-large);
  color: var(--color-body-text);
  margin-top: var(--spacing-sm);
}

.profile-meta {
  margin-top: var(--spacing-md);
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
}

.meta-badge {
  padding: 2px 8px;
  font-size: var(--font-size-small);
  border-radius: 12px;
  font-weight: 600;
}

.admin-badge {
  background: #fff3cd;
  color: #856404;
}

.profile-divider {
  border-top: 1px solid var(--color-border-primary);
  margin: var(--spacing-lg) 0;
}

/* ---- 存档列表 ---- */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-md);
}

.section-title {
  font-size: var(--font-size-xlarge);
  font-weight: 600;
  margin: 0;
}

.article-table {
  margin-bottom: var(--spacing-lg);
}

.article-row {
  cursor: pointer;
}

/* ---- 封面缩略图 ---- */
.th-cover { width: 48px; }

.col-cover {
  padding: var(--spacing-xs) var(--spacing-sm) !important;
}

.cover-thumb {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  overflow: hidden;
  background: var(--color-bg-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-secondary-text);
  flex-shrink: 0;
  margin: 0 auto;
}

.cover-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.article-title-link {
  font-weight: 600;
  color: var(--color-link);
}

.article-row:hover .article-title-link {
  text-decoration: underline;
}

.article-game {
  font-size: var(--font-size-normal);
  color: var(--color-link);
}

.article-version {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  font-family: ui-monospace, monospace;
}

.col-number {
  text-align: right;
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
}

.col-time {
  text-align: right;
}

/* ---- 画廊网格 ---- */
.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

@media (max-width: 640px) {
  .gallery-grid {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: var(--spacing-md);
  }
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  padding: var(--spacing-lg) 0;
}
</style>
