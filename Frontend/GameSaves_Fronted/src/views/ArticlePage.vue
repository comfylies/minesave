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
      <ArticleMeta :article="articleStore.currentArticle">
        <template #actions>
          <el-button
            v-if="isOwner || auth.isAdmin"
            size="default"
            @click="showEditDialog = true"
          >
            <el-icon><Edit /></el-icon>
            编辑
          </el-button>
          <el-button
            v-if="isOwner || auth.isAdmin"
            size="default"
            type="danger"
            plain
            @click="handleDelete"
          >
            <el-icon><Delete /></el-icon>
            删除
          </el-button>
        </template>
      </ArticleMeta>

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

      <!-- README + 批注 同行布局 -->
      <div class="readme-row">
        <div class="article-main">
          <ReadmeRenderer
            ref="readmeRendererRef"
            :article-id="articleId"
            :html="articleStore.currentArticle.readmeContent"
            :raw="articleStore.currentArticle.readmeRaw"
            :loading="false"
            @select-comment="onSelectComment"
            @comment-created="onCommentCreated"
            @positions-updated="onPositionsUpdated"
          />
        </div>

        <div class="article-sidebar">
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
      </div>
    </template>

    <!-- 编辑对话框 -->
    <el-dialog v-model="showEditDialog" title="编辑存档" width="500px">
      <el-form :model="editForm" label-position="top">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="editForm.version" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

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
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import hljs from 'highlight.js'
import { useArticleStore } from '../stores/articles'
import { useFileStore } from '../stores/files'
import { useAuthStore } from '../stores/auth'
import { articleApi } from '../api/articleApi'
import { fileApi } from '../api/fileApi'
import ArticleMeta from '../components/article/ArticleMeta.vue'
import FileBrowser from '../components/article/FileBrowser.vue'
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

// 编辑对话框
const showEditDialog = ref(false)
const saving = ref(false)
const editForm = ref({ title: '', version: '', description: '' })

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

// ---- 编辑 ----
watch(showEditDialog, (val) => {
  if (val && articleStore.currentArticle) {
    editForm.value = {
      title: articleStore.currentArticle.title,
      version: articleStore.currentArticle.version,
      description: articleStore.currentArticle.description || ''
    }
  }
})

async function saveEdit() {
  saving.value = true
  try {
    await articleApi.update(articleId.value, editForm.value)
    ElMessage.success('存档信息已更新')
    showEditDialog.value = false
    await articleStore.fetchArticle(articleId.value)
  } catch (e) {
    // 错误已在拦截器中提示
  } finally {
    saving.value = false
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

// 页面重载后恢复滚动位置（由 ReadmeRenderer.reloadPage() 在 sessionStorage 中保存）
function restoreScrollPosition() {
  const key = `scrollY_${articleId.value}`
  const saved = sessionStorage.getItem(key)
  if (saved) {
    sessionStorage.removeItem(key)
    const y = parseInt(saved, 10)
    if (y > 0) {
      // 等待 README 渲染完成再恢复（marginPositions 非空表示卡片定位已就绪）
      const stop = watch(marginPositions, (positions) => {
        if (positions && positions.length > 0) {
          stop()
          requestAnimationFrame(() => window.scrollTo(0, y))
        }
      })
      // 兜底：2 秒后无论如何恢复
      setTimeout(() => { stop(); window.scrollTo(0, y) }, 2000)
    }
  }
}

onMounted(() => {
  loadArticle()
  restoreScrollPosition()
})

watch(() => route.params.articleId, () => {
  fileStore.reset()
  loadArticle()
})

onUnmounted(() => {
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

.readme-row {
  display: flex;
  gap: var(--spacing-lg);
  margin-top: var(--spacing-lg);
  align-items: flex-start;
}

.article-main {
  flex: 1;
  min-width: 0;
}

.article-sidebar {
  width: 300px;
  flex-shrink: 0;
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
  .readme-row {
    flex-direction: column;
  }
  .article-sidebar {
    width: 100%;
    position: static;
  }
}
</style>
