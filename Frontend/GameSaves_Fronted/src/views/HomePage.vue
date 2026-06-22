<template>
  <div class="home-page">
    <!-- 标题区 -->
    <div class="home-header">
      <h1 class="page-title">探索游戏</h1>
      <p class="page-subtitle">浏览游戏存档，发现其他玩家的精彩时刻</p>
    </div>

    <!-- 搜索排序栏 -->
    <div class="home-toolbar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索游戏名称或描述..."
        :prefix-icon="Search"
        clearable
        size="large"
        class="search-input"
      />
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
    <div v-else-if="gameStore.error" class="home-error">
      <el-empty description="加载游戏列表失败">
        <el-button type="primary" @click="gameStore.fetchGames()">重试</el-button>
      </el-empty>
    </div>

    <!-- 游戏卡片网格 -->
    <div v-else-if="filteredGames.length > 0" class="game-grid">
      <GameCard
        v-for="game in filteredGames"
        :key="game.id"
        :game="game"
      />
    </div>

    <!-- 空状态 -->
    <EmptyState v-else description="暂无游戏" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { useGameStore } from '../stores/games'
import GameCard from '../components/game/GameCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import LoadingSkeleton from '../components/common/LoadingSkeleton.vue'

const gameStore = useGameStore()
const searchKeyword = ref('')
const sortBy = ref('name-asc')

// 纯前端搜索 + 排序
const filteredGames = computed(() => {
  let list = [...gameStore.games]

  // 搜索过滤
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    list = list.filter(g =>
      g.name.toLowerCase().includes(kw) ||
      (g.description && g.description.toLowerCase().includes(kw))
    )
  }

  // 排序
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

onMounted(() => {
  gameStore.fetchGames()
})
</script>

<style scoped>
.home-page {
  padding: var(--spacing-lg) 0;
}

.home-header {
  margin-bottom: var(--spacing-lg);
}

.page-title {
  font-size: var(--font-size-title);
  font-weight: 600;
  color: var(--color-body-text);
}

.page-subtitle {
  font-size: var(--font-size-normal);
  color: var(--color-secondary-text);
  margin-top: var(--spacing-xs);
}

/* ---- 搜索排序栏 ---- */
.home-toolbar {
  display: flex;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
  padding: var(--spacing-md) 0;
  border-bottom: 1px solid var(--color-border-secondary);
}

.search-input {
  flex: 1;
  max-width: 480px;
}

.sort-select {
  width: 180px;
  flex-shrink: 0;
}

/* ---- 错误 ---- */
.home-error {
  padding: var(--spacing-xxl) 0;
  text-align: center;
}

/* ---- 卡片网格 ---- */
.game-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: var(--spacing-md);
}
</style>
