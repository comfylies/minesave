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
        <h1 class="page-title">{{ gameStore.currentGame.name }}</h1>
        <p class="game-description">{{ gameStore.currentGame.description || '暂无描述' }}</p>
        <div class="game-stats">
          <span class="stat-item">
            <el-icon><Folder /></el-icon>
            {{ articleStore.pagination.totalElements }} 个存档
          </span>
        </div>
      </div>

      <div class="game-divider" />

      <!-- 存档列表加载 -->
      <LoadingSkeleton v-if="articleStore.loading" :rows="8" />

      <!-- 存档列表 -->
      <template v-else>
        <div v-if="articleStore.articleList.length > 0">
          <table class="data-table article-table">
            <thead>
              <tr>
                <th style="width: 40%">标题</th>
                <th style="width: 15%">版本</th>
                <th style="width: 15%">作者</th>
                <th style="width: 10%">大小</th>
                <th style="width: 10%">下载</th>
                <th style="width: 10%">时间</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="article in articleStore.articleList"
                :key="article.id"
                class="article-row"
                @click="$router.push(`/articles/${article.id}`)"
              >
                <td class="col-title">
                  <span class="article-title-link">{{ article.title }}</span>
                  <span class="article-desc">{{ truncate(article.description, 60) }}</span>
                </td>
                <td>
                  <span class="article-version">{{ article.version }}</span>
                </td>
                <td>
                  <router-link
                    :to="`/users/${article.userId}`"
                    class="article-author"
                    @click.stop
                  >
                    {{ article.nickname }}
                  </router-link>
                </td>
                <td class="col-number">{{ formatSize(article.fileSize) }}</td>
                <td class="col-number">{{ article.downloadCount || 0 }}</td>
                <td class="col-time time-ago">{{ formatTime(article.createdAt) }}</td>
              </tr>
            </tbody>
          </table>

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
import EmptyState from '../components/common/EmptyState.vue'
import LoadingSkeleton from '../components/common/LoadingSkeleton.vue'

const route = useRoute()
const gameStore = useGameStore()
const articleStore = useArticleStore()
const auth = useAuthStore()

const gameId = ref(Number(route.params.gameId) || 0)
const currentPage = ref(1)

function formatSize(bytes) {
  if (bytes == null || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

function formatTime(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
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

function truncate(text, max) {
  if (!text) return ''
  return text.length > max ? text.slice(0, max) + '...' : text
}

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
}

.game-description {
  font-size: var(--font-size-large);
  color: var(--color-secondary-text);
  margin-top: var(--spacing-sm);
  line-height: 1.6;
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

/* ---- 表格 ---- */
.article-table {
  margin-bottom: var(--spacing-lg);
}

.article-row {
  cursor: pointer;
}

.col-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.article-title-link {
  font-weight: 600;
  color: var(--color-link);
  font-size: var(--font-size-large);
}

.article-row:hover .article-title-link {
  text-decoration: underline;
}

.article-desc {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 320px;
}

.article-version {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  font-family: ui-monospace, monospace;
}

.article-author {
  font-size: var(--font-size-normal);
  color: var(--color-link);
}

.col-number {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  text-align: right;
}

.col-time {
  text-align: right;
}

/* ---- 分页 ---- */
.pagination-wrap {
  display: flex;
  justify-content: center;
  padding: var(--spacing-lg) 0;
}
</style>
