<template>
  <div class="article-meta">
    <!-- 标题 -->
    <h1 class="article-title">{{ article.title }}</h1>

    <!-- 元数据行 -->
    <div class="article-subtitle">
      <router-link :to="`/games/${article.gameId}`" class="meta-link">
        {{ article.gameName }}
      </router-link>
      <span class="meta-sep">/</span>
      <router-link :to="`/users/${article.userId}`" class="meta-link">
        {{ article.nickname }}
      </router-link>
      <span class="meta-sep">·</span>
      <span class="meta-text">版本 {{ article.version }}</span>
    </div>

    <!-- 标签 -->
    <div v-if="article.tags && article.tags.length > 0" class="article-tags">
      <TagDisplay :tags="article.tags" />
    </div>

    <!-- 统计行 -->
    <div class="article-stats">
      <span v-if="article.fileCount != null" class="stat-item">
        <el-icon><Folder /></el-icon>
        {{ article.fileCount }} 个文件
      </span>
      <span v-if="article.totalExtractSize != null" class="stat-item">
        <el-icon><Files /></el-icon>
        {{ formatSize(article.totalExtractSize) }}
      </span>
      <span v-if="article.fileSize" class="stat-item">
        <el-icon><FolderOpened /></el-icon>
        压缩包 {{ formatSize(article.fileSize) }}
      </span>
      <span class="stat-item">
        <el-icon><Download /></el-icon>
        {{ article.downloadCount || 0 }} 次下载
      </span>
      <span class="stat-item">
        <el-icon><Clock /></el-icon>
        {{ formatTime(article.createdAt) }}
      </span>
      <span v-if="article.status" class="stat-item">
        <el-tag
          :type="statusType"
          size="small"
          effect="plain"
        >
          {{ statusText }}
        </el-tag>
      </span>
    </div>

    <!-- 操作按钮行 -->
    <div class="article-actions">
      <button
        class="btn-download"
        :disabled="downloading"
        @click="handleDownload"
      >
        <el-icon><Download /></el-icon>
        {{ downloading ? '下载中...' : '下载存档' }}
      </button>
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { fileApi } from '../../api/fileApi'
import TagDisplay from '../tag/TagDisplay.vue'

const props = defineProps({
  article: { type: Object, required: true }
})

const downloading = ref(false)

async function handleDownload() {
  downloading.value = true
  try {
    await fileApi.download(props.article.id)
  } finally {
    downloading.value = false
  }
}

const statusType = computed(() => {
  const map = { READY: 'success', FAILED: 'danger', EXTRACTING: 'warning', UPLOADING: 'info' }
  return map[props.article.status] || 'info'
})

const statusText = computed(() => {
  const map = { READY: '就绪', FAILED: '失败', EXTRACTING: '解压中', UPLOADING: '上传中' }
  return map[props.article.status] || props.article.status
})

function formatSize(bytes) {
  if (bytes == null || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
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

  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}
</script>

<style scoped>
.article-meta {
  padding-bottom: var(--spacing-lg);
  border-bottom: 1px solid var(--color-border-primary);
}

.article-title {
  font-size: var(--font-size-title);
  font-weight: 600;
  color: var(--color-body-text);
  margin-bottom: var(--spacing-sm);
  line-height: 1.3;
}

/* ---- 副标题（游戏/作者/版本） ---- */
.article-subtitle {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-large);
  color: var(--color-secondary-text);
  margin-bottom: var(--spacing-md);
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

/* ---- 标签 ---- */
.article-tags {
  margin-bottom: var(--spacing-sm);
}

/* ---- 统计行 ---- */
.article-stats {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-md);
  flex-wrap: wrap;
}

.stat-item {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
}

/* ---- 操作按钮 ---- */
.article-actions {
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.btn-download {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 6px 16px;
  font-size: var(--font-size-normal);
  font-weight: 500;
  color: var(--color-btn-primary-text);
  background: var(--color-btn-primary-bg);
  border: 1px solid var(--color-btn-primary-bg);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--transition-fast);
  text-decoration: none;
}

.btn-download:hover {
  background: var(--color-btn-primary-hover);
  text-decoration: none;
  color: #fff;
}
</style>
