<template>
  <div class="profile-page">
    <LoadingSkeleton v-if="loadingUser" :rows="6" />
    <div v-else-if="userError" class="profile-error">
      <EmptyState description="加载用户信息失败"><el-button type="primary" @click="loadUserData">重试</el-button></EmptyState>
    </div>
    <template v-else-if="user">
      <div class="profile-header">
        <el-avatar :size="80" :src="user.avatarUrl" icon="UserFilled" class="profile-avatar" />
        <div class="profile-info">
          <h1 class="profile-name" :title="user.nickname || user.username">{{ user.nickname || user.username }}</h1>
          <p class="profile-username" :title="'@' + user.username">@{{ user.username }}</p>
          <p v-if="user.bio" class="profile-bio" :title="user.bio">{{ user.bio }}</p>
          <div class="profile-meta">
            <span class="meta-item"><el-icon><Calendar /></el-icon>{{ formatDate(user.createdAt) }} 加入</span>
            <span v-if="user.role === 'admin'" class="meta-badge admin-badge">管理员</span>
          </div>
        </div>
      </div>

      <div class="profile-divider" />
      <div class="section-header">
        <h2 class="section-title">{{ user.nickname || user.username }} 的存档</h2>
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="list"><el-icon><List /></el-icon>列表</el-radio-button>
          <el-radio-button value="gallery"><el-icon><Grid /></el-icon>画廊</el-radio-button>
        </el-radio-group>
      </div>

      <LoadingSkeleton v-if="articleStore.loading" :rows="8" />
      <template v-else>
        <div v-if="articleStore.articleList.length > 0">
          <table v-if="viewMode === 'list'" class="data-table article-table">
            <thead><tr><th class="th-cover"></th><th style="width: 36%">标题</th><th style="width: 15%">游戏</th><th style="width: 10%">版本</th><th style="width: 8%">大小</th><th style="width: 8%">下载</th><th style="width: 18%">时间</th></tr></thead>
            <tbody><tr v-for="article in articleStore.articleList" :key="article.id" class="article-row" @click="$router.push(`/articles/${article.id}`)">
              <td class="col-cover"><div class="cover-thumb"><img v-if="article.coverImage" :src="article.coverThumbnail || thumbUrl(article.coverImage, 270)" :alt="article.title" class="cover-thumb-img" loading="lazy" @error="e => e.target.style.display = 'none'" /><el-icon v-else :size="20"><FolderOpened /></el-icon></div></td>
              <td><span class="article-title-link" :title="article.title">{{ article.title }}</span></td>
              <td><router-link :to="`/games/${article.gameId}`" class="article-game" :title="article.gameName" @click.stop>{{ article.gameName }}</router-link></td>
              <td><span class="article-version" :title="article.version">{{ article.version }}</span></td>
              <td class="col-number">{{ formatSize(article.fileSize) }}</td><td class="col-number">{{ article.downloadCount || 0 }}</td><td class="col-time time-ago">{{ formatTime(article.createdAt) }}</td>
            </tr></tbody>
          </table>
          <div v-else class="gallery-grid"><ArticleCard v-for="article in articleStore.articleList" :key="article.id" :article="article" /></div>
          <div class="pagination-wrap"><el-pagination v-model:current-page="currentPage" :page-size="articleStore.pagination.size" :total="articleStore.pagination.totalElements" layout="prev, pager, next" @current-change="handlePageChange" /></div>
        </div>
        <EmptyState v-else :description="isSelf ? '你还没有上传存档。' : '该用户暂无存档。'"><el-button v-if="isSelf" type="primary" @click="$router.push('/upload')">上传存档</el-button></EmptyState>
      </template>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { userApi } from '../api/userApi'
import { useArticleStore } from '../stores/articles'
import { useAuthStore } from '../stores/auth'
import { useViewMode } from '../composables/useViewMode'
import EmptyState from '../components/common/EmptyState.vue'
import LoadingSkeleton from '../components/common/LoadingSkeleton.vue'
import ArticleCard from '../components/article/ArticleCard.vue'
import { thumbUrl } from '../utils/imageUrl'
import { formatSize, formatTime } from '@/utils/format'

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

function formatDate(dateStr) { return dateStr ? new Date(dateStr).toLocaleDateString('zh-CN') : '' }
function handlePageChange(page) { currentPage.value = page; articleStore.fetchByUser(userId.value, page - 1) }
async function loadUserData() {
  loadingUser.value = true; userError.value = null
  try { user.value = await userApi.getById(userId.value); await articleStore.fetchByUser(userId.value, 0) }
  catch (error) { userError.value = error.message }
  finally { loadingUser.value = false }
}
onMounted(loadUserData)
watch(() => route.params.userId, () => { currentPage.value = 1; loadUserData() })
</script>

<style scoped>
.profile-page { padding: var(--spacing-lg) 0; }.profile-error { padding: var(--spacing-xxl) 0; }
.profile-header { display: flex; gap: var(--spacing-lg); align-items: flex-start; }.profile-avatar { flex-shrink: 0; border: 1px solid var(--color-border-primary); }.profile-info { flex: 1; }
.profile-name { margin: 0; font-size: var(--font-size-title); font-weight: 600; color: var(--color-body-text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }.profile-username { font-size: var(--font-size-xlarge); color: var(--color-secondary-text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.profile-bio { font-size: var(--font-size-large); color: var(--color-body-text); margin-top: var(--spacing-sm); display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }.profile-meta { margin-top: var(--spacing-md); display: flex; align-items: center; gap: var(--spacing-md); }
.meta-item { display: inline-flex; align-items: center; gap: var(--spacing-xs); font-size: var(--font-size-small); color: var(--color-secondary-text); }.meta-badge { padding: 2px 8px; font-size: var(--font-size-small); border-radius: 12px; font-weight: 600; }.admin-badge { background: #fff3cd; color: #856404; }.profile-divider { border-top: 1px solid var(--color-border-primary); margin: var(--spacing-lg) 0; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--spacing-md); }.section-title { font-size: var(--font-size-xlarge); font-weight: 600; margin: 0; }.article-table { margin-bottom: var(--spacing-lg); }.article-row { cursor: pointer; }.th-cover { width: 48px; }.col-cover { padding: var(--spacing-xs) var(--spacing-sm) !important; }.cover-thumb { width: 36px; height: 36px; border-radius: 4px; overflow: hidden; background: var(--color-bg-secondary); display: flex; align-items: center; justify-content: center; color: var(--color-secondary-text); flex-shrink: 0; margin: 0 auto; }.cover-thumb-img { width: 100%; height: 100%; object-fit: cover; display: block; }.article-title-link, .article-game { display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: var(--color-link); }.article-title-link { font-weight: 600; }.article-row:hover .article-title-link { text-decoration: underline; }.article-game { font-size: var(--font-size-normal); }.article-version { font-size: var(--font-size-small); color: var(--color-secondary-text); font-family: ui-monospace, monospace; display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }.col-number { text-align: right; font-size: var(--font-size-small); color: var(--color-secondary-text); }.col-time { text-align: right; }.gallery-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: var(--spacing-lg); margin-bottom: var(--spacing-lg); }.pagination-wrap { display: flex; justify-content: center; padding: var(--spacing-lg) 0; }
@media (max-width: 640px) { .gallery-grid { grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: var(--spacing-md); } }
</style>
