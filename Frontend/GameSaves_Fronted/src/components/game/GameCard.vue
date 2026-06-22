<template>
  <router-link :to="`/games/${game.id}`" class="game-card">
    <div class="game-card-body">
      <h3 class="game-name">{{ game.name }}</h3>
      <p class="game-desc">{{ game.description || '暂无描述' }}</p>
    </div>
    <div class="game-card-footer">
      <span class="game-meta">
        <el-icon><Folder /></el-icon>
        <span>{{ game.articleCount ?? 0 }} 个存档</span>
      </span>
      <span class="game-meta">
        <el-icon><Clock /></el-icon>
        <span>{{ formatDate(game.createdAt) }}</span>
      </span>
    </div>
  </router-link>
</template>

<script setup>
defineProps({
  game: { type: Object, required: true }
})

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}
</script>

<style scoped>
.game-card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border-primary);
  border-radius: var(--radius-md);
  padding: var(--spacing-lg);
  background: var(--color-bg-canvas);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
  cursor: pointer;
  text-decoration: none;
  color: inherit;
  min-height: 140px;
}

.game-card:hover {
  border-color: var(--color-link);
  box-shadow: var(--shadow-md);
  text-decoration: none;
}

.game-card-body {
  flex: 1;
}

.game-name {
  font-size: var(--font-size-xlarge);
  font-weight: 600;
  color: var(--color-link);
  margin-bottom: var(--spacing-sm);
}

.game-card:hover .game-name {
  text-decoration: underline;
}

.game-desc {
  font-size: var(--font-size-normal);
  color: var(--color-secondary-text);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.game-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--spacing-md);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-border-secondary);
}

.game-meta {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
}
</style>
