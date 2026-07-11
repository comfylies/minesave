<template>
  <div class="game-page">
    <!-- 加载游戏详情 -->
    <LoadingSkeleton v-if="gameStore.loading && !gameStore.currentGame" :rows="4" />

    <!-- 错误 -->
    <div v-else-if="gameStore.error && !gameStore.currentGame" class="game-error">
      <EmptyState description="加载游戏详情失败">
        <el-button type="primary" @click="gameStore.fetchGame(gameId)">重试</el-button>
      </EmptyState>
    </div>

    <!-- 游戏信息和存档列表 -->
    <template v-else-if="gameStore.currentGame">
      <!-- 游戏信息头部 -->
      <div class="game-header">
        <h1 class="page-title" :title="gameStore.currentGame.name">{{ gameStore.currentGame.name }}</h1>
        <p class="game-description" :title="gameStore.currentGame.description">{{ gameStore.currentGame.description || '暂无描述' }}</p>
        <div class="game-stats">
          <span class="stat-item">
            <el-icon><Folder /></el-icon>
            {{ articleStore.pagination.totalElements }} 个存档
          </span>
        </div>
      </div>

      <div class="game-divider" />

      <!-- 视图切换工具栏 -->
      <div class="view-toolbar">
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
        <el-button v-if="auth.isLoggedIn" type="primary" size="small" @click="$router.push('/upload')">
          <el-icon><Upload /></el-icon>
          上传存档
        </el-button>
      </div>

      <!-- 存档列表加载 -->
      <LoadingSkeleton v-if="articleStore.loading" :rows="8" />

      <!-- 存档内容 -->
      <template v-else>
        <div v-if="articleStore.articleList.length > 0">
          <!-- 列表视图 -->
          <table v-if="viewMode === 'list'" class="data-table article-table">
            <thead>
              <tr>
                <th class="th-cover"></th>
                <th class="th-title">标题</th>
                <th class="th-tags">标签</th>
                <th class="th-version">版本</th>
                <th class="th-author">作者</th>
                <th class="th-size">大小</th>
                <th class="th-dl">下载</th>
                <th class="th-time">时间</th>
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
                      :src="article.coverThumbnail || thumbUrl(article.coverImage, 270)"
                      :alt="article.title"
                      class="cover-thumb-img"
                      loading="lazy"
                      @error="e => e.target.style.display = 'none'"
                    />
                    <el-icon v-else :size="20"><FolderOpened /></el-icon>
                  </div>
                </td>
                <td class="col-title">
                  <span class="article-title-link" :title="article.title">{{ article.title }}</span>
                  <span class="article-desc" :title="article.description">{{ truncate(article.description, 60) }}</span>
                </td>
                <td class="col-tags">
                  <TagDisplay :tags="article.tagNames" :max="2" />
                </td>
                <td class="col-version">
                  <span class="article-version" :title="article.version">{{ article.version }}</span>
                </td>
                <td class="col-author">
                  <router-link
                    :to="`/users/${article.userId}`"
                    class="article-author"
                    :title="article.nickname"
                    @click.stop
                  >{{ article.nickname }}</router-link>
                </td>
                <td class="col-number">{{ formatSize(article.fileSize) }}</td>
                <td class="col-number">{{ article.downloadCount || 0 }}</td>
                <td class="col-time">{{ formatTime(article.createdAt) }}</td>
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

          <!-- 分页 -->
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="articleStore.pagination.size"
              :total="articleStore.pagination.totalElements"
              layout="prev, pager, next"
              :pager-count="7"
              @current-change="handlePageChange"
            />
          </div>
        </div>

        <!-- 暂无存档 -->
        <EmptyState v-else description="此游戏暂无存档">
          <el-button v-if="auth.isLoggedIn" type="primary" @click="$router.push('/upload')">
            成为第一个上传者
          </el-button>
        </EmptyState>
      </template>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useGameStore } from '../stores/games'
import { useArticleStore } from '../stores/articles'
import { useAuthStore } from '../stores/auth'
import { useViewMode } from '../composables/useViewMode'
import EmptyState from '../components/common/EmptyState.vue'
import LoadingSkeleton from '../components/common/LoadingSkeleton.vue'
import TagDisplay from '../components/tag/TagDisplay.vue'
import ArticleCard from '../components/article/ArticleCard.vue'
import { thumbUrl } from '../utils/imageUrl'
import { formatSize, formatTime, truncate } from '@/utils/format'

const route = useRoute()
const gameStore = useGameStore()
const articleStore = useArticleStore()
const auth = useAuthStore()
const { viewMode } = useViewMode()

const gameId = ref(Number(route.params.gameId) || 0)
const currentPage = ref(1)

function handlePageChange(page) {
  currentPage.value = page
  articleStore.fetchByGame(gameId.value, page - 1)
}

onMounted(() => {
  gameStore.fetchGame(gameId.value)
  articleStore.fetchByGame(gameId.value, 0)
})

watch(() => route.params.gameId, (newId) => {
  gameId.value = Number(newId)
  gameStore.fetchGame(gameId.value)
  articleStore.fetchByGame(gameId.value, 0)
  currentPage.value = 1
})
</script>

<style scoped>
.game-page {
  padding: var(--spacing-lg) 0;
}

/* ---- 头部 ---- */
.game-header {
  margin-bottom: var(--spacing-md);
  min-width: 0;
}

.game-header .page-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.game-description {
  font-size: var(--font-size-large);
  color: var(--color-secondary-text);
  margin-top: var(--spacing-sm);
  line-height: 1.6;
  word-break: break-word;
  overflow-wrap: break-word;
}

.game-stats {
  margin-top: var(--spacing-md);
  display: flex;
  gap: var(--spacing-lg);
}

.stat-item {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
}

.game-divider {
  border-top: 1px solid var(--color-border-primary);
  margin: var(--spacing-lg) 0;
}

.game-error {
  padding: var(--spacing-xxl) 0;
}

/* ---- 视图切换工具栏 ---- */
.view-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-md);
}

/* ---- 表格 ---- */
.article-table {
  table-layout: fixed;
  width: 100%;
  margin-bottom: var(--spacing-lg);
}

/* Column widths */
.th-cover  { width: 48px; }
.th-title  { width: 28%; }
.th-tags   { width: 13%; }
.th-version { width: 11%; }
.th-author { width: 11%; }
.th-size   { width: 8%; }
.th-dl     { width: 8%; }
.th-time   { width: 14%; }

/* Unified center alignment */
.article-table th,
.article-table td {
  text-align: center;
  vertical-align: middle;
}

.article-row {
  cursor: pointer;
}

.article-table td {
  overflow: hidden;
  white-space: nowrap;
}

/* Title column — left-align for readability */
.article-table th:nth-child(2),
.article-table .col-title {
  text-align: left;
  white-space: normal;
}

/* ---- 封面缩略图 ---- */
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

/* ---- 标题 ---- */
.article-title-link {
  display: block;
  font-weight: 600;
  color: var(--color-link);
  font-size: var(--font-size-large);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.article-row:hover .article-title-link {
  text-decoration: underline;
}

.article-desc {
  display: block;
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 2px;
}

.article-version {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  font-family: ui-monospace, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
}

.article-author {
  font-size: var(--font-size-normal);
  color: var(--color-link);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
}

.col-number {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
}

.col-time {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
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

/* ---- 分页 ---- */
.pagination-wrap {
  display: flex;
  justify-content: center;
  padding: var(--spacing-lg) 0;
}
</style>
