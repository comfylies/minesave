<template>
  <aside class="article-sidebar">
    <!-- 作者信息 -->
    <div class="sidebar-section">
      <router-link :to="`/users/${article.userId}`" class="sidebar-author">
        <el-avatar :size="32" :src="article.avatarUrl">
          {{ article.nickname?.charAt(0) }}
        </el-avatar>
        <span class="author-name">{{ article.nickname }}</span>
      </router-link>
    </div>

    <!-- 下载按钮 -->
    <div class="sidebar-section">
      <button
        class="sidebar-download-btn"
        :disabled="downloading || article.status !== 'READY'"
        @click="handleDownload"
      >
        <el-icon><Download /></el-icon>
        {{ downloading ? '下载中...' : '下载存档 (ZIP)' }}
      </button>
      <div class="download-count">
        <el-icon><Download /></el-icon>
        {{ article.downloadCount || 0 }} 次下载
      </div>
    </div>

    <!-- 标签 -->
    <div v-if="article.tags && article.tags.length > 0" class="sidebar-section">
      <TagDisplay :tags="article.tags" />
    </div>

    <!-- 文件统计 -->
    <div class="sidebar-section sidebar-stats">
      <div class="sidebar-stat">
        <el-icon><Folder /></el-icon>
        <span>{{ article.fileCount ?? '-' }} 个文件</span>
      </div>
      <div v-if="article.totalExtractSize != null" class="sidebar-stat">
        <el-icon><Files /></el-icon>
        <span>{{ formatSize(article.totalExtractSize) }}</span>
      </div>
      <div v-if="article.fileSize" class="sidebar-stat">
        <el-icon><FolderOpened /></el-icon>
        <span>压缩包 {{ formatSize(article.fileSize) }}</span>
      </div>
    </div>

    <!-- 游戏 -->
    <div class="sidebar-section">
      <router-link :to="`/games/${article.gameId}`" class="sidebar-game">
        {{ article.gameName }}
      </router-link>
      <span class="sidebar-version">版本 {{ article.version }}</span>
    </div>

    <!-- 时间 -->
    <div class="sidebar-section sidebar-time">
      <el-icon><Clock /></el-icon>
      <span>{{ formatTime(article.createdAt) }}</span>
    </div>

    <!-- 状态 -->
    <div v-if="article.status" class="sidebar-section">
      <el-tag :type="statusType" size="small" effect="plain">
        {{ statusText }}
      </el-tag>
    </div>

    <!-- 管理操作（编辑/删除） -->
    <div v-if="$slots.actions" class="sidebar-section">
      <slot name="actions" />
    </div>
  </aside>
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
.article-sidebar {
  width: 280px;
  flex-shrink: 0;
  padding: var(--spacing-md);
  border: 1px solid var(--color-border-primary);
  border-radius: var(--radius-md);
  background: var(--color-bg-primary);
}

.sidebar-section {
  padding-bottom: var(--spacing-md);
  margin-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--color-border-secondary);
}

.sidebar-section:last-child {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}

/* 作者 */
.sidebar-author {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  text-decoration: none;
  color: var(--color-body-text);
}

.author-name {
  font-weight: 600;
  font-size: var(--font-size-normal);
}

.sidebar-author:hover .author-name {
  color: var(--color-link);
}

/* 下载按钮 */
.sidebar-download-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 100%;
  padding: 8px 0;
  font-size: var(--font-size-normal);
  font-weight: 600;
  color: #fff;
  background: var(--color-btn-primary-bg);
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.sidebar-download-btn:hover:not(:disabled) {
  background: var(--color-btn-primary-hover);
}

.sidebar-download-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.download-count {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-secondary-text);
}

/* 统计 */
.sidebar-stats {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sidebar-stat {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-secondary-text);
}

/* 游戏 */
.sidebar-game {
  display: block;
  font-size: 13px;
  color: var(--color-link);
  font-weight: 500;
  text-decoration: none;
  margin-bottom: 2px;
}

.sidebar-game:hover {
  text-decoration: underline;
}

.sidebar-version {
  font-size: 12px;
  color: var(--color-secondary-text);
}

/* 时间 */
.sidebar-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-secondary-text);
}
</style>
