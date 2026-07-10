<template>
  <div class="browse-page">

    <!-- 搜索栏 -->
    <div class="browse-search">
      <el-icon class="browse-search-icon"><Search /></el-icon>
      <input
        v-model="searchQuery"
        class="browse-search-input"
        placeholder="搜索游戏、存档..."
        @keyup.enter="doSearch"
        autocomplete="off"
      />
    </div>

    <!-- 排序栏 -->
    <div class="browse-toolbar">
      <el-select
        v-model="sortBy"
        size="large"
        class="sort-select"
      >
        <el-option label="📋 按名称 A-Z" value="name-asc" />
        <el-option label="📋 按名称 Z-A" value="name-desc" />
        <el-option label="🕐 最近更新" value="updated" />
        <el-option label="📦 存档最多" value="articles" />
      </el-select>
    </div>

    <!-- 加载状态 -->
    <LoadingSkeleton v-if="gameStore.loading" :rows="6" />

    <!-- 错误状态 -->
    <div v-else-if="gameStore.error" class="browse-error">
      <el-empty description="加载游戏列表失败">
        <el-button type="primary" @click="gameStore.fetchGames()">重试</el-button>
      </el-empty>
    </div>

    <!-- 游戏卡片网格 -->
    <div v-else-if="sortedGames.length > 0" class="game-grid">
      <GameCard
        v-for="game in sortedGames"
        :key="game.id"
        :game="game"
      />
    </div>

    <!-- 空状态 -->
    <EmptyState v-else description="暂无游戏" />

    <!-- 站内公告弹窗（仅外部访问显示） -->
    <AnnouncementModal v-model="showAnnouncement" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useGameStore } from '../stores/games'
import GameCard from '../components/game/GameCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import LoadingSkeleton from '../components/common/LoadingSkeleton.vue'
import AnnouncementModal from '../components/announcement/AnnouncementModal.vue'

const gameStore = useGameStore()
const route = useRoute()
const router = useRouter()
const sortBy = ref('name-asc')
const searchQuery = ref('')

function doSearch() {
  const q = searchQuery.value.trim()
  if (q) {
    router.push({ path: '/search', query: { q } })
  }
}

// ---- 公告弹窗（仅外部访问显示） ----
const showAnnouncement = ref(false)

function getTodayKey() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function shouldShowAnnouncement() {
  if (localStorage.getItem('announcement_dismissed_date') === getTodayKey()) {
    return false
  }
  if (sessionStorage.getItem('announcement_shown_session') === 'true') {
    return false
  }
  try {
    const referrer = document.referrer
    if (referrer) {
      const referrerHost = new URL(referrer).host
      const currentHost = window.location.host
      if (referrerHost === currentHost) {
        return false
      }
    }
  } catch {
    return false
  }
  return true
}

const sortedGames = computed(() => {
  let list = [...gameStore.games]

  switch (sortBy.value) {
    case 'name-asc':
      list.sort((a, b) => a.name.localeCompare(b.name))
      break
    case 'name-desc':
      list.sort((a, b) => b.name.localeCompare(a.name))
      break
    case 'updated':
      list.sort((a, b) => new Date(b.updatedAt || b.createdAt) - new Date(a.updatedAt || a.createdAt))
      break
    case 'articles':
      list.sort((a, b) => (b.articleCount || 0) - (a.articleCount || 0))
      break
  }

  return list
})

/** 从 URL query 读取排序参数（首页"查看更多"跳转附带） */
function applySortFromQuery() {
  const sortParam = route.query.sort
  if (sortParam === 'hot') {
    sortBy.value = 'articles'
  } else if (sortParam === 'newest') {
    sortBy.value = 'updated'
  }
}

onMounted(() => {
  applySortFromQuery()
  gameStore.fetchGames()
  if (shouldShowAnnouncement()) {
    sessionStorage.setItem('announcement_shown_session', 'true')
    setTimeout(() => {
      showAnnouncement.value = true
    }, 300)
  }
})
</script>

<style scoped>
.browse-page {
  padding: var(--spacing-lg) 0;
}

/* ---- 搜索栏 ---- */
.browse-search {
  position: relative;
  margin-bottom: var(--spacing-lg);
}

.browse-search-icon {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 18px;
  color: var(--color-secondary-text);
  z-index: 1;
}

.browse-search-input {
  width: 100%;
  height: 48px;
  padding: 0 16px 0 44px;
  font-size: var(--font-size-large);
  color: var(--color-body-text);
  background: var(--color-bg-canvas);
  border: 1px solid var(--color-border-primary);
  border-radius: 24px;
  outline: none;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
  box-sizing: border-box;
}

.browse-search-input:focus {
  border-color: var(--color-link);
  box-shadow: 0 0 0 3px rgba(9, 105, 218, 0.1);
}

.browse-search-input::placeholder {
  color: var(--color-secondary-text);
}

.browse-toolbar {
  display: flex;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
  padding: var(--spacing-md) 0;
  border-bottom: 1px solid var(--color-border-secondary);
}

.sort-select {
  width: 180px;
  flex-shrink: 0;
}

.browse-error {
  padding: var(--spacing-xxl) 0;
  text-align: center;
}

.game-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: var(--spacing-md);
}
</style>
