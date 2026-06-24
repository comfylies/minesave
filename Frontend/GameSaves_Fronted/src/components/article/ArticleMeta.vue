<template>
  <div class="article-meta">
    <!-- 标题 -->
    <h1 class="article-title">{{ article.title }}</h1>

    <!-- 游戏 / 作者 / 版本 -->
    <div class="article-subtitle">
      <router-link :to="`/games/${article.gameId}`" class="meta-link">
        {{ article.gameName }}
      </router-link>
      <span class="meta-sep">/</span>
      <router-link :to="`/users/${article.userId}`" class="meta-link">
        {{ article.nickname }}
      </router-link>
      <span v-if="article.version" class="meta-sep">·</span>
      <span v-if="article.version" class="meta-text">版本 {{ article.version }}</span>
    </div>

    <!-- 操作按钮 -->
    <div class="article-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  article: { type: Object, required: true }
})
</script>

<style scoped>
.article-meta {
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--color-border-primary);
  margin-bottom: var(--spacing-md);
}

.article-title {
  font-size: var(--font-size-title);
  font-weight: 600;
  color: var(--color-body-text);
  margin-bottom: var(--spacing-xs);
  line-height: 1.3;
}

.article-subtitle {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-normal);
  color: var(--color-secondary-text);
  margin-bottom: var(--spacing-sm);
  flex-wrap: wrap;
}

.meta-link {
  color: var(--color-link);
  font-weight: 500;
  text-decoration: none;
}

.meta-link:hover {
  text-decoration: underline;
}

.meta-sep {
  color: var(--color-secondary-text);
}

.meta-text {
  color: var(--color-secondary-text);
}

.article-actions {
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}
</style>
