<template>
  <div class="article-page">
    <!-- 状态：加载中 -->
    <LoadingSkeleton v-if="articleStore.loading && !articleStore.currentArticle" :rows="8" />

    <!-- 状态：错误 -->
    <div v-else-if="articleStore.error && !articleStore.currentArticle" class="article-error">
      <EmptyState description="加载存档失败">
        <el-button type="primary" @click="loadArticle">重试</el-button>
      </EmptyState>
    </div>

    <!-- 状态：数据就绪 -->
    <template v-else-if="articleStore.currentArticle">
      <ArticleMeta :article="articleStore.currentArticle" />

      <!-- 文件浏览 + 封面图 + 右侧信息栏 -->
      <div class="browse-row">
        <div class="browse-left">
          <!-- 文件浏览器 -->
          <FileBrowser
            :directories="fileStore.directories"
            :files="fileStore.files"
            :breadcrumbs="fileStore.breadcrumbs"
            :loading="fileStore.loading"
            @navigate="navigateToPath"
            @preview="previewFile"
          />

          <!-- 错误 -->
          <div v-if="fileStore.error && !fileStore.loading" class="file-error">
            <el-alert type="error" :title="fileStore.error" show-icon :closable="false" />
          </div>

          <!-- 封面图（720px 压缩图，失败回退原图） -->
          <div v-if="articleStore.currentArticle.coverImage && !coverFailed" class="cover-wrap">
            <img
              :src="coverSrc"
              :alt="articleStore.currentArticle.title"
              class="cover-image"
              @error="onCoverError"
            />
          </div>
        </div>

        <!-- 右侧信息栏 -->
        <ArticleSidebar :article="articleStore.currentArticle">
          <template v-if="isOwner || auth.isAdmin" #actions>
            <el-button size="small" @click="router.push(`/articles/${articleId}/edit`)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button size="small" type="danger" plain @click="handleDelete">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </ArticleSidebar>
      </div>

      <!-- 批注显隐开关（封面图下方、README 上方） -->
      <div class="annotations-toggle-bar">
        <el-button size="small" @click="toggleAnnotations">
          <el-icon><component :is="showAnnotations ? 'Hide' : 'View'" /></el-icon>
          {{ showAnnotations ? '隐藏批注' : '显示批注' }}
        </el-button>
        <span class="annotations-toggle-hint">按 Ctrl+B 切换</span>
      </div>

      <!-- README + 批注 同行布局 -->
      <div class="readme-row">
        <div class="article-main">
          <ReadmeRenderer
            ref="readmeRendererRef"
            :article-id="articleId"
            :raw="articleStore.currentArticle.readmeRaw"
            :loading="false"
            @select-comment="onSelectComment"
            @comment-created="onCommentCreated"
            @positions-updated="onPositionsUpdated"
          />
        </div>

        <Transition name="sidebar">
          <div v-show="showAnnotations" class="article-sidebar">
            <AnnotationPanel
              ref="annotationPanelRef"
              :article-id="articleId"
              :active-comment-id="activeCommentId"
              :margin-positions="marginPositions"
              :readme-content-top="readmeContentTop"
              @select-comment="onPanelSelectComment"
              @delete-comment="onDeleteComment"
            />
          </div>
        </Transition>
      </div>
    </template>

    <!-- 文件预览对话框 -->
    <el-dialog
      v-model="showPreview"
      :title="previewTitle"
      width="70%"
      :close-on-click-modal="true"
    >
      <div v-if="previewLoading" class="preview-loading">
        <el-skeleton :rows="15" animated />
      </div>
      <div v-else-if="previewError" class="preview-error">
        <el-empty :description="previewError" />
      </div>
      <div v-else class="preview-content">
        <pre v-if="previewText"><code v-html="highlightedCode" class="hljs"></code></pre>
        <el-empty v-else description="此文件无法在线预览" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import hljs from 'highlight.js'
import { useArticleStore } from '../stores/articles'
import { useFileStore } from '../stores/files'
import { useAuthStore } from '../stores/auth'
import { fileApi } from '../api/fileApi'
import { thumbUrl } from '../utils/imageUrl'

// Cover image fallback: server 720p thumbnail → client derivation → original
const coverFailed = ref(false)
const useOriginal = ref(false)
const coverSrc = computed(() => {
  const img = articleStore.currentArticle?.coverImage
  if (!img) return ''
  if (useOriginal.value) return img
  return articleStore.currentArticle?.coverThumbnail720 || thumbUrl(img, 720) || img
})
function onCoverError() {
  if (!useOriginal.value) {
    useOriginal.value = true
  } else {
    coverFailed.value = true
  }
}
import ArticleMeta from '../components/article/ArticleMeta.vue'
import FileBrowser from '../components/article/FileBrowser.vue'
import ArticleSidebar from '../components/article/ArticleSidebar.vue'
import ReadmeRenderer from '../components/article/ReadmeRenderer.vue'
import AnnotationPanel from '../components/article/AnnotationPanel.vue'
import EmptyState from '../components/common/EmptyState.vue'
import LoadingSkeleton from '../components/common/LoadingSkeleton.vue'

const route = useRoute()
const router = useRouter()
const articleStore = useArticleStore()
const fileStore = useFileStore()
const auth = useAuthStore()

const articleId = computed(() => Number(route.params.articleId) || 0)
const isOwner = computed(() => auth.userId === articleStore.currentArticle?.userId)

// 批注模块引用
const readmeRendererRef = ref(null)
const annotationPanelRef = ref(null)
const activeCommentId = ref(null)
const marginPositions = ref([])
const readmeContentTop = ref(0)
const showAnnotations = ref(true)

// 批注显隐切换
function toggleAnnotations() {
  showAnnotations.value = !showAnnotations.value
  if (readmeRendererRef.value) {
    readmeRendererRef.value.setAnnotationsVisible(showAnnotations.value)
  }
  // v-show 切换后等 Vue 更新完成，触发布局重算刷新批注卡定位
  nextTick(() => {
    window.dispatchEvent(new Event('resize'))
  })
}

// Ctrl+B 快捷键
function handleKeydown(e) {
  if (e.ctrlKey && (e.key === 'b' || e.key === 'B')) {
    e.preventDefault()
    toggleAnnotations()
  }
}

// 批注事件处理
function onSelectComment(annotation) {
  activeCommentId.value = annotation?.id ? Number(annotation.id) : null
}

function onCommentCreated(comment) {
  // 新批注已由 ReadmeRenderer 内部加入本地列表并触发 scheduleUpdate()
  // 只需通知 AnnotationPanel 追加到本地列表，无需重新请求后端
  if (annotationPanelRef.value) {
    annotationPanelRef.value.addCommentToList(comment)
  }
}

function onPanelSelectComment(commentId) {
  activeCommentId.value = commentId
  if (readmeRendererRef.value) {
    readmeRendererRef.value.scrollTo(commentId)
  }
}

function onDeleteComment(commentId) {
  // 后端已由 AnnotationPanel 删除，ReadmeRenderer 只需清理本地状态
  if (readmeRendererRef.value) {
    readmeRendererRef.value.removeLocalComment(commentId)
  }
}

function onPositionsUpdated({ positions, readmeContentTop: contentTop }) {
  marginPositions.value = positions || []
  readmeContentTop.value = contentTop || 0
}

// 文件预览
const showPreview = ref(false)
const previewTitle = ref('')
const previewLoading = ref(false)
const previewError = ref('')
const previewText = ref('')
const previewFileType = ref('')
const highlightedCode = computed(() => {
  if (!previewText.value) return ''
  const lang = previewFileType.value?.replace('.', '') || ''
  if (lang && hljs.getLanguage(lang)) {
    return hljs.highlight(previewText.value, { language: lang }).value
  }
  return hljs.highlightAuto(previewText.value).value
})

// ---- 导航 ----
function navigateToPath(path) {
  fileStore.browse(articleId.value, path)
}

async function previewFile(file) {
  previewTitle.value = file.name
  showPreview.value = true
  previewLoading.value = true
  previewError.value = ''
  previewText.value = ''
  previewFileType.value = file.fileType || ''

  if (!file.isText) {
    previewError.value = '此文件无法在线预览，请下载存档后查看'
    previewLoading.value = false
    return
  }

  try {
    const url = fileApi.previewUrl(articleId.value, file.virtualPath)
    const resp = await fetch(url)
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    previewText.value = await resp.text()
  } catch (e) {
    previewError.value = `加载文件失败: ${e.message}`
  } finally {
    previewLoading.value = false
  }
}

// ---- 删除 ----
async function handleDelete() {
  try {
    await ElMessageBox.confirm(
      '确定要删除此存档吗？此操作不可撤销。',
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await articleStore.deleteArticle(articleId.value)
    ElMessage.success('存档已删除')
    router.push('/')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// ---- 初始化 ----
async function loadArticle() {
  await articleStore.fetchArticle(articleId.value)
  fileStore.reset()
  fileStore.browse(articleId.value, '')
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  loadArticle()
})

watch(() => route.params.articleId, () => {
  fileStore.reset()
  loadArticle()
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  fileStore.reset()
})
</script>

<style scoped>
.article-page {
  padding: var(--spacing-lg) 0;
}

.article-error {
  padding: var(--spacing-xxl) 0;
}

/* ---- 文件浏览 + 封面 + 信息栏 同行 ---- */
.browse-row {
  display: flex;
  gap: var(--spacing-md);
  align-items: flex-start;
  margin-bottom: var(--spacing-lg);
}

.browse-left {
  flex: 1;
  min-width: 0;
}

/* ---- 封面图 ---- */
.cover-wrap {
  margin-top: var(--spacing-md);
  border: 1px solid var(--color-border-primary);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.cover-image {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

/* ---- 批注显隐开关 ---- */
.annotations-toggle-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: 0;
  padding-bottom: var(--spacing-sm);
}

.annotations-toggle-hint {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
}

/* ---- README + 批注 ---- */
.readme-row {
  display: flex;
  gap: var(--spacing-lg);
  align-items: flex-start;
}

.article-main {
  flex: 1;
  min-width: 0;
}

.article-sidebar {
  width: 280px;
  flex-shrink: 0;
}

/* Vue <Transition> 侧边栏滑入/滑出 */
.sidebar-enter-active,
.sidebar-leave-active {
  transition: width 0.3s ease, opacity 0.3s ease;
  overflow: hidden;
}

.sidebar-enter-from,
.sidebar-leave-to {
  width: 0 !important;
  opacity: 0;
}

.file-error {
  margin-top: var(--spacing-md);
}

/* ---- 预览 ---- */
.preview-loading {
  padding: var(--spacing-lg);
}

.preview-error {
  padding: var(--spacing-xl);
}

.preview-content pre {
  max-height: 60vh;
  overflow: auto;
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border-secondary);
  border-radius: var(--radius-sm);
  padding: var(--spacing-md);
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

.preview-content code {
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
}

/* ---- 响应式 ---- */
@media (max-width: 900px) {
  .browse-row {
    flex-direction: column;
  }

  .readme-row {
    flex-direction: column;
  }

  .article-sidebar {
    width: 100%;
    position: static;
  }

  /* 移动端侧边栏上下收起 */
  .sidebar-enter-active,
  .sidebar-leave-active {
    transition: max-height 0.3s ease, opacity 0.3s ease;
    overflow: hidden;
  }

  .sidebar-enter-from,
  .sidebar-leave-to {
    max-height: 0 !important;
    width: 100% !important;
    opacity: 0;
  }
}
</style>
