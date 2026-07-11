<template>
  <div class="search-page">
    <!-- 搜索框 -->
    <div class="search-header">
      <div class="search-input-wrap">
        <el-icon class="search-icon-big"><Search /></el-icon>
        <input
          v-model="query"
          class="search-input-big"
          placeholder="搜索游戏、存档..."
          @keyup.enter="doSearch"
        />
      </div>
      <div class="search-header-row">
        <span v-if="totalHits >= 0" class="search-stats">
          约 {{ totalHits }} 条结果
        </span>
        <el-radio-group v-if="articleHits.length > 0 && totalHits >= 0" v-model="viewMode" size="small">
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
    </div>

    <!-- 搜索结果 -->
    <LoadingSkeleton v-if="loading" :rows="6" />

    <div v-else-if="error" class="search-error">
      <EmptyState :description="error">
        <el-button @click="doSearch">重试</el-button>
      </EmptyState>
    </div>

    <div v-else-if="hits.length === 0 && searched" class="search-empty">
      <el-empty description="未找到相关内容">
        <template #extra>
          <span class="empty-tip">试试其他关键词？</span>
        </template>
      </el-empty>
    </div>

    <div v-else class="search-results">
      <!-- 游戏结果 — 始终使用紧凑卡片 -->
      <div v-if="gameHits.length > 0" class="result-section">
        <h3 class="section-title">🎮 游戏</h3>
        <div
          v-for="hit in gameHits"
          :key="hit.id"
          class="result-card card"
          @click="$router.push(`/games/${hit.id}`)"
        >
          <div class="result-main">
            <h4 class="result-title" v-html="highlightOr(hit, 'name', hit.title)"></h4>
            <p class="result-desc" v-html="highlightOr(hit, 'description', hit.description)"></p>
          </div>
          <div class="result-meta">
            <span class="meta-badge">{{ hit.articleCount }} 个存档</span>
          </div>
        </div>
      </div>

      <!-- 存档结果 -->
      <div v-if="articleHits.length > 0" class="result-section">
        <h3 class="section-title">📦 存档</h3>

        <!-- 列表视图 -->
        <template v-if="viewMode === 'list'">
          <div
            v-for="hit in articleHits"
            :key="hit.id"
            class="result-card card"
            @click="$router.push(`/articles/${hit.id}`)"
          >
            <div class="result-main">
              <h4 class="result-title" v-html="highlightOr(hit, 'title', hit.title)"></h4>
              <p class="result-desc" v-html="highlightOr(hit, 'description', hit.description)"></p>
              <div class="result-tags">
                <span class="result-game" :title="hit.gameName">{{ hit.gameName }}</span>
                <el-tag
                  v-for="tag in hit.tags"
                  :key="tag"
                  size="small"
                  type="info"
                  class="tag-chip"
                >{{ tag }}</el-tag>
              </div>
            </div>
            <div class="result-meta">
              <span class="meta-badge">下载 {{ hit.downloadCount }}</span>
            </div>
          </div>
        </template>

        <!-- 画廊视图 — 使用 ArticleCard（搜索结果的 coverImage 字段需后端提供） -->
        <div v-else class="gallery-grid">
          <ArticleCard
            v-for="hit in articleHits"
            :key="hit.id"
            :article="hit"
          />
        </div>
      </div>

      <!-- 加载更多 -->
      <div v-if="hasMore" class="load-more">
        <el-button :loading="loadingMore" @click="loadMore">加载更多</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchApi } from '../api/searchApi'
import { useViewMode } from '../composables/useViewMode'
import LoadingSkeleton from '../components/common/LoadingSkeleton.vue'
import EmptyState from '../components/common/EmptyState.vue'
import ArticleCard from '../components/article/ArticleCard.vue'

const route = useRoute()
const router = useRouter()
const { viewMode } = useViewMode()

const query = ref(route.query.q || '')
const hits = ref([])
const totalHits = ref(-1)
const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const searched = ref(false)
const page = ref(0)

const gameHits = computed(() => hits.value.filter(h => h.type === 'game'))
const articleHits = computed(() => hits.value.filter(h => h.type === 'article'))
const hasMore = computed(() => hits.value.length < totalHits.value)

function highlightOr(hit, field, fallback) {
  const formatted = hit.formatted || {}
  if (formatted[field]) return formatted[field]
  if (fallback) return escapeHtml(String(fallback))
  return ''
}

function escapeHtml(text) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

async function doSearch() {
  const q = query.value.trim()
  if (!q) return

  // Update URL without reload
  router.replace({ path: '/search', query: { q } })

  loading.value = true
  error.value = ''
  searched.value = true
  page.value = 0
  try {
    const result = await searchApi.search(q, 0)
    hits.value = result.hits || []
    totalHits.value = result.totalHits
  } catch (e) {
    error.value = e.message || '搜索失败'
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  const nextPage = page.value + 1
  try {
    const result = await searchApi.search(query.value.trim(), nextPage)
    hits.value.push(...(result.hits || []))
    page.value = nextPage
  } catch {
    // ignore
  } finally {
    loadingMore.value = false
  }
}

// Search on mount if query param present
watch(() => route.query.q, (newQ) => {
  if (newQ) {
    query.value = newQ
    doSearch()
  }
}, { immediate: true })
</script>

<style scoped>
.search-page {
  max-width: 800px;
  margin: 0 auto;
  padding: var(--spacing-lg) 0;
}

/* ---- 搜索框 ---- */
.search-header {
  margin-bottom: var(--spacing-lg);
}

.search-input-wrap {
  position: relative;
  margin-bottom: 8px;
}

.search-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
}

.search-icon-big {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 20px;
  color: var(--color-secondary-text);
}

.search-input-big {
  width: 100%;
  height: 48px;
  padding: 0 16px 0 48px;
  font-size: 18px;
  color: var(--color-body-text);
  background: var(--color-bg-canvas);
  border: 1px solid var(--color-border-primary);
  border-radius: 24px;
  outline: none;
  transition: border-color var(--transition-fast);
  box-sizing: border-box;
}

.search-input-big:focus {
  border-color: var(--color-link);
}

.search-stats {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  margin-left: 16px;
}

/* ---- 结果 ---- */
.result-section {
  margin-bottom: var(--spacing-xl);
}

.section-title {
  font-size: var(--font-size-large);
  font-weight: 600;
  margin-bottom: var(--spacing-md);
  color: var(--color-body-text);
}

.result-card {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: var(--spacing-md) var(--spacing-lg);
  margin-bottom: var(--spacing-sm);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.result-card:hover {
  background: var(--color-bg-secondary);
}

.result-main {
  flex: 1;
  min-width: 0;
}

.result-title {
  font-size: var(--font-size-large);
  font-weight: 600;
  color: var(--color-body-text);
  margin: 0 0 4px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-title :deep(em) {
  color: var(--color-link);
  font-style: normal;
  font-weight: 700;
}

.result-desc {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  margin: 0 0 8px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.result-desc :deep(em) {
  color: var(--color-link);
  font-style: normal;
}

.result-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.result-game {
  font-size: 12px;
  color: var(--color-link);
  font-weight: 500;
  max-width: 150px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: inline-block;
  vertical-align: middle;
}

.tag-chip {
  border-radius: 4px;
}

.result-meta {
  flex-shrink: 0;
  margin-left: 16px;
}

.meta-badge {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  white-space: nowrap;
}

/* ---- 画廊网格 ---- */
.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-lg);
}

@media (max-width: 640px) {
  .gallery-grid {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: var(--spacing-md);
  }
}

/* ---- 加载更多 ---- */
.load-more {
  text-align: center;
  padding: var(--spacing-lg);
}

/* ---- 空状态 ---- */
.search-empty, .search-error {
  padding: var(--spacing-xxl) 0;
}

.empty-tip {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
}
</style>
