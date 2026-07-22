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
      <el-button v-if="auth.isLoggedIn && auth.userId !== article.userId" size="small" plain class="message-author" @click="router.push(`/messages?peer=${article.userId}`)">私信</el-button>
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

    <!-- 好评/差评 -->
    <div class="sidebar-section sidebar-votes">
      <div class="vote-row">
        <button
          class="vote-btn"
          :class="{ 'vote-active': currentUserVote === 'UP' }"
          :disabled="voting"
          @click="handleVote('UP')"
          title="好评"
        >
          <svg class="vote-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14z"/>
            <path d="M3 11h4v11H3z"/>
          </svg>
          <span class="vote-label">好评</span>
          <span class="vote-count">{{ article.upvoteCount || 0 }}</span>
        </button>
        <button
          class="vote-btn"
          :class="{ 'vote-active': currentUserVote === 'DOWN' }"
          :disabled="voting"
          @click="handleVote('DOWN')"
          title="差评"
        >
          <svg class="vote-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3H10z"/>
            <path d="M21 13h-4V2h4z"/>
          </svg>
          <span class="vote-label">差评</span>
          <span class="vote-count">{{ article.downvoteCount || 0 }}</span>
        </button>
      </div>
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
    <DownloadNoticeDialog
      v-model="showDownloadNotice"
      :security-level="article.securityLevel"
      @confirm="proceedDownload"
    />
  </aside>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fileApi } from '../../api/fileApi'
import { voteApi } from '../../api/voteApi'
import { useAuthStore } from '../../stores/auth'
import TagDisplay from '../tag/TagDisplay.vue'
import DownloadCaptchaModal from '../download/DownloadCaptchaModal.vue'
import DownloadNoticeDialog from '../download/DownloadNoticeDialog.vue'
import { formatSize, formatTime } from '@/utils/format'

const props = defineProps({
  article: { type: Object, required: true }
})

const router = useRouter()
const auth = useAuthStore()

const downloading = ref(false)
const showDownloadNotice = ref(false)

// ── 投票状态 ──
const currentUserVote = ref(null)
const voting = ref(false)

onMounted(() => {
  if (auth.isLoggedIn) {
    voteApi.getMyVote(props.article.id).then(res => {
      currentUserVote.value = res.userVote
    }).catch(() => {})
  }
})

async function handleVote(voteType) {
  if (!auth.isLoggedIn) {
    router.push('/login?redirect=' + encodeURIComponent(router.currentRoute.value.fullPath))
    return
  }
  if (voting.value) return
  voting.value = true
  try {
    const result = await voteApi.vote(props.article.id, voteType)
    props.article.upvoteCount = result.upvoteCount
    props.article.downvoteCount = result.downvoteCount
    currentUserVote.value = result.userVote
  } catch (e) {
    // 错误已在拦截器处理
  } finally {
    voting.value = false
  }
}

// ── 大文件下载验证码 ──
const showCaptchaModal = ref(false)
const captchaFileSize = ref(0)
const captchaDailyRemaining = ref(0)

async function handleDownload() {
  showDownloadNotice.value = true
}

async function proceedDownload() {
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

.message-author {
  width: 100%;
  margin-top: var(--spacing-sm);
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

/* 好评/差评投票 */
.sidebar-votes {
  padding: 0;
}

.vote-row {
  display: flex;
  gap: 12px;
}

.vote-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  padding: 6px 10px;
  font-size: 13px;
  color: #9499a0;
  background: transparent;
  border: 1px solid #c9ccd0;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  justify-content: center;
}

.vote-btn:hover:not(:disabled) {
  background: #f4f5f7;
  border-color: #b0b3b8;
}

.vote-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.vote-btn .vote-icon {
  flex-shrink: 0;
}

.vote-btn .vote-label {
  font-size: 12px;
}

.vote-btn .vote-count {
  font-weight: 600;
  font-size: 12px;
  min-width: 16px;
  text-align: center;
}

/* Bilibili 粉色激活态 */
.vote-btn.vote-active {
  color: #fff;
  background: #FB7299;
  border-color: #FB7299;
}

.vote-btn.vote-active:hover:not(:disabled) {
  background: #e8628a;
  border-color: #e8628a;
}
</style>
