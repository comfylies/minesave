<template>
  <aside class="article-sidebar">
    <!-- 作者信息 -->
    <div class="sidebar-section">
      <router-link :to="`/users/${article.userId}`" class="sidebar-author">
        <el-avatar :size="32" :src="article.avatarUrl">
          {{ article.nickname?.charAt(0) }}
        </el-avatar>
        <span class="author-name" :title="article.nickname">{{ article.nickname }}</span>
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
      <router-link :to="`/games/${article.gameId}`" class="sidebar-game" :title="article.gameName">
        {{ article.gameName }}
      </router-link>
      <span class="sidebar-version" :title="'版本 ' + article.version">版本 {{ article.version }}</span>
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

    <!-- 大文件下载验证码弹窗 -->
    <DownloadCaptchaModal
      v-model="showCaptchaModal"
      :article-id="article.id"
      :file-size="captchaFileSize"
      :daily-remaining="captchaDailyRemaining"
      @verified="handleCaptchaVerified"
      @cancel="handleCaptchaCancelled"
    />
  </aside>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { fileApi } from '../../api/fileApi'
import TagDisplay from '../tag/TagDisplay.vue'
import DownloadCaptchaModal from '../download/DownloadCaptchaModal.vue'
import { formatSize, formatTime } from '@/utils/format'

const props = defineProps({
  article: { type: Object, required: true }
})

const downloading = ref(false)

// ── 大文件下载验证码 ──
const showCaptchaModal = ref(false)
const captchaFileSize = ref(0)
const captchaDailyRemaining = ref(0)

async function handleDownload() {
  downloading.value = true
  try {
    // 1. 查询是否需要验证码
    const info = await fileApi.getDownloadInfo(props.article.id)

    if (info.requiresCaptcha) {
      // 需要验证码 — 弹窗
      captchaFileSize.value = info.fileSize
      captchaDailyRemaining.value = info.dailyRemaining
      showCaptchaModal.value = true
    } else {
      // 小文件 — 直接下载
      await fileApi.download(props.article.id)
    }
  } catch (e) {
    // 错误已在拦截器提示
  } finally {
    downloading.value = false
  }
}

// 验证码验证成功
async function handleCaptchaVerified({ downloadToken }) {
  showCaptchaModal.value = false
  try {
    await fileApi.download(props.article.id, downloadToken)
    ElMessage.success('下载已开始')
  } catch (e) {
    // 错误已在拦截器提示
  }
}

function handleCaptchaCancelled() {
  showCaptchaModal.value = false
}

const statusType = computed(() => {
  const map = { READY: 'success', FAILED: 'danger', EXTRACTING: 'warning', UPLOADING: 'info' }
  return map[props.article.status] || 'info'
})

const statusText = computed(() => {
  const map = { READY: '就绪', FAILED: '失败', EXTRACTING: '解压中', UPLOADING: '上传中' }
  return map[props.article.status] || props.article.status
})

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
  min-width: 0;
}

.author-name {
  font-weight: 600;
  font-size: var(--font-size-normal);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-game:hover {
  text-decoration: underline;
}

.sidebar-version {
  font-size: 12px;
  color: var(--color-secondary-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
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
