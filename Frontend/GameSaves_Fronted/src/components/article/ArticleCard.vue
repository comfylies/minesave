<template>
  <router-link :to="`/articles/${article.id}`" class="article-card">
    <!-- 封面图区域 (16:9) -->
    <div class="article-card-cover">
      <img
        v-if="article.coverImage"
        :src="coverSrc"
        :alt="article.title"
        class="cover-image"
        loading="lazy"
        @error="onCoverError"
      />
      <div v-else class="cover-placeholder">
        <el-icon :size="36"><FolderOpened /></el-icon>
        <span class="placeholder-game">{{ article.gameName }}</span>
      </div>
    </div>

    <!-- 信息区域 -->
    <div class="article-card-body">
      <h3 class="article-card-title" :title="article.title">{{ article.title }}</h3>
      <div class="article-card-tags">
        <TagDisplay :tags="article.tagNames || []" :max="2" />
      </div>
      <p v-if="article.description" class="article-card-desc">
        {{ truncate(article.description, 80) }}
      </p>
    </div>

    <!-- 元数据 -->
    <div class="article-card-footer">
      <span class="card-meta-item" :title="article.nickname">
        <el-icon><User /></el-icon>
        {{ article.nickname }}
      </span>
      <span class="card-meta-item">
        <el-icon><Folder /></el-icon>
        {{ formatSize(article.fileSize) }}
      </span>
      <span class="card-meta-item">
        <el-icon><Download /></el-icon>
        {{ article.downloadCount || 0 }}
      </span>
      <span class="card-meta-item card-meta-time">
        {{ formatTime(article.createdAt) }}
      </span>
    </div>
  </router-link>
</template>

<script setup>
import { ref, computed } from 'vue'
import TagDisplay from '../tag/TagDisplay.vue'
import { thumbUrl } from '../../utils/imageUrl'

const props = defineProps({
  article: { type: Object, required: true }
})

const coverFailed = ref(false)
const useOriginal = ref(false)

// Start with thumbnail; fall back to original on error
const coverSrc = computed(() => {
  if (useOriginal.value) return props.article.coverImage
  return thumbUrl(props.article.coverImage, 360) || props.article.coverImage
})

function onCoverError() {
  if (!useOriginal.value) {
    // Thumbnail not available (e.g. WebP) — fall back to original
    useOriginal.value = true
  } else {
    // Original also failed — show placeholder
    coverFailed.value = true
  }
}

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
</script>

<style scoped>
.article-card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border-primary);
  border-radius: var(--radius-md);
  background: var(--color-bg-canvas);
  overflow: hidden;
  cursor: pointer;
  text-decoration: none;
  color: inherit;
  transition: border-color var(--transition-fast),
              box-shadow var(--transition-fast),
              transform var(--transition-fast);
}

.article-card:hover {
  border-color: var(--color-link);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
  text-decoration: none;
}

/* ---- 封面图区域 ---- */
.article-card-cover {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: var(--color-bg-secondary);
  border-bottom: 1px solid var(--color-border-secondary);
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  color: var(--color-secondary-text);
}

.placeholder-game {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
}

/* ---- 信息区域 ---- */
.article-card-body {
  flex: 1;
  padding: var(--spacing-md);
}

.article-card-title {
  font-size: var(--font-size-large);
  font-weight: 600;
  color: var(--color-body-text);
  margin: 0 0 var(--spacing-xs) 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.article-card:hover .article-card-title {
  color: var(--color-link);
}

.article-card-tags {
  margin-bottom: var(--spacing-sm);
  min-height: 22px;
}

.article-card-desc {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  line-height: 1.5;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ---- 元数据 ---- */
.article-card-footer {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-md);
  border-top: 1px solid var(--color-border-secondary);
  flex-wrap: wrap;
}

.card-meta-item {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  white-space: nowrap;
}

.card-meta-time {
  margin-left: auto;
}
</style>
