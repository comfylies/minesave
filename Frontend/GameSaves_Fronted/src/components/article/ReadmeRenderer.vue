<template>
  <div class="readme-wrapper">
    <LoadingSkeleton v-if="loading" :rows="12" />

    <template v-else-if="renderedContent">
      <div class="readme-header">
        <span class="readme-title">📄 README.md</span>
        <span v-if="allComments.length" class="annotation-indicator">
          💬 {{ allComments.length }} 条批注
        </span>
      </div>
      <div
        ref="contentContainer"
        class="readme-content markdown-body"
        v-html="renderedContent"
        @click="handleAnchorClick"
      />
    </template>

    <EmptyState v-else description="此存档暂无 README" />

    <AnnotationPopup
      v-if="showPopup"
      :annotator="annotator"
      :article-id="articleId"
      :pending-selector="pendingSelector"
      @submit="onPopupSubmit"
      @cancel="onPopupCancel"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { marked } from 'marked'
import { ElMessage } from 'element-plus'
import hljs from 'highlight.js'
import EmptyState from '../common/EmptyState.vue'
import LoadingSkeleton from '../common/LoadingSkeleton.vue'
import AnnotationPopup from './AnnotationPopup.vue'
import { useTextAnnotator } from '../../composables/useTextAnnotator'
import { HighlightManager } from '../../composables/HighlightManager'
import { BorderLayer } from '../../composables/BorderLayer'
import { useArticleStore } from '../../stores/articles'
import { commentApi } from '../../api/commentApi'

const props = defineProps({
  articleId: { type: Number, default: 0 },
  html: { type: String, default: '' },
  raw: { type: String, default: '' },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['select-comment', 'comment-created', 'positions-updated'])

// ---- Markdown ----
function renderMarkdown(src, articleId) {
  const renderer = new marked.Renderer()
  renderer.image = function (token) {
    let url = token.href || ''
    if (url && !url.startsWith('http://') && !url.startsWith('https://') && !url.startsWith('data:') && !url.startsWith('/')) {
      let imgPath = url
      if (imgPath.toLowerCase().startsWith('images/')) imgPath = imgPath.substring('images/'.length)
      url = `/api/files/${articleId}/readme-image?path=${encodeURIComponent(imgPath)}`
    }
    const titleAttr = token.title ? ` title="${escapeAttr(token.title)}"` : ''
    return `<img src="${url}" alt="${escapeAttr(token.text || '')}"${titleAttr}>`
  }
  return marked.parse(src, {
    renderer, breaks: false, gfm: true,
    highlight: function (code, lang) {
      if (lang && hljs.getLanguage(lang)) {
        try { return hljs.highlight(code, { language: lang }).value } catch { /* fall */ }
      }
      try { return hljs.highlightAuto(code).value } catch { return code }
    }
  })
}

function escapeAttr(str) { return str.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;') }
function escapeHtml(text) { return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;') }

const renderedContent = computed(() => {
  const src = props.raw || props.html
  if (!src) return ''
  try { return renderMarkdown(src, props.articleId) } catch { return `<pre>${escapeHtml(src)}</pre>` }
})

function handleAnchorClick(e) {
  const target = e.target
  if (target.tagName === 'A' && target.getAttribute('href')?.startsWith('#')) {
    e.preventDefault()
    const el = document.getElementById(target.getAttribute('href').slice(1))
    if (el) el.scrollIntoView({ behavior: 'smooth' })
  }
}

// ---- 角色颜色 ----
const articleStore = useArticleStore()

function resolveColorRole(comment) {
  const role = comment.role || 'user'
  if (role === 'admin') return 'admin'
  const articleOwnerId = articleStore.currentArticle?.userId
  if (articleOwnerId && comment.userId === articleOwnerId) return 'uploader'
  return 'user'
}

// ---- 高亮渲染层（HighlightManager + BorderLayer） ----
const highlightManager = new HighlightManager()
/** @type {import('vue').Ref<BorderLayer|null>} */
const borderLayer = ref(null)

/**
 * 从字符偏移创建 DOM Range
 * 复用于 HighlightManager（添加 Range）和 BorderLayer（获取像素坐标）
 */
function createRangeFromOffsets(container, start, end) {
  if (start >= end) return null
  const textNodes = []
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, {
    acceptNode: () => NodeFilter.FILTER_ACCEPT
  })
  let node
  while (node = walker.nextNode()) {
    if (node.textContent.trim() || node.textContent.length >= 1) textNodes.push(node)
  }
  let currentPos = 0, startNode = null, startOffset = 0, endNode = null, endOffset = 0
  for (const tn of textNodes) {
    const len = tn.textContent.length
    if (!startNode && currentPos + len > start) { startNode = tn; startOffset = Math.max(0, start - currentPos) }
    if (!endNode && currentPos + len >= end) { endNode = tn; endOffset = Math.min(len, end - currentPos); break }
    currentPos += len
  }
  if (!startNode || !endNode) return null
  try {
    const range = document.createRange()
    range.setStart(startNode, Math.min(startOffset, startNode.textContent.length))
    range.setEnd(endNode, Math.min(endOffset, endNode.textContent.length))
    return range
  } catch { return null }
}

/**
 * 获取容器 padding 偏移。
 * getBoundingClientRect 使用 border-box 坐标系，
 * 但绝对定位子元素从 padding edge 开始，
 * 所以需要减去 padding-top 补偿。
 */
function getPaddingOffset(container) {
  const style = window.getComputedStyle(container)
  return parseFloat(style.paddingTop) || 0
}

/**
 * 渲染所有批注高亮（HighlightManager 背景+下划线 + BorderLayer 左边色条）
 */
function renderAllHighlights(comments, container) {
  // 清空旧高亮
  highlightManager.clearAll()

  const borderItems = []
  const containerRect = container.getBoundingClientRect()
  const padTop = getPaddingOffset(container)

  for (const comment of comments) {
    const start = comment.quoteStart ?? 0
    const end = comment.quoteEnd ?? 0
    if (end <= start) continue

    const range = createRangeFromOffsets(container, start, end)
    if (!range) continue

    const role = resolveColorRole(comment)

    // HighlightManager：添加 Range 到对应角色的 Highlight 对象
    highlightManager.addAnnotation(String(comment.id), range, role)

    // BorderLayer：计算 Range 的首行 top 和总高度
    // 使用 getClientRects 而非 getBoundingClientRect，能正确处理跨行文本
    const rects = range.getClientRects()
    if (rects.length > 0) {
      const first = rects[0]
      const last = rects[rects.length - 1]
      borderItems.push({
        id: comment.id,
        // rects 和 containerRect 都是 viewport 坐标，差值得相对位置
        // 减去 padding 补偿绝对定位的 padding edge 偏移
        top: Math.round(first.top - containerRect.top - padTop),
        height: Math.round(last.bottom - first.top),
        role
      })
    }
  }

  // 批量渲染边框条
  if (borderLayer.value) {
    borderLayer.value.setBars(borderItems)
  }
}

/**
 * 为单个批注添加高亮（新建批注时调用）
 */
function addHighlightForComment(comment, container) {
  const start = comment.quoteStart ?? 0
  const end = comment.quoteEnd ?? 0
  if (end <= start) return

  const range = createRangeFromOffsets(container, start, end)
  if (!range) return

  const role = resolveColorRole(comment)
  highlightManager.addAnnotation(String(comment.id), range, role)

  // BorderLayer：追加单条边框
  const rects = range.getClientRects()
  if (rects.length > 0 && borderLayer.value) {
    const containerRect = container.getBoundingClientRect()
    const padTop = getPaddingOffset(container)
    const first = rects[0]
    const last = rects[rects.length - 1]
    borderLayer.value.addBar(
      comment.id,
      Math.round(first.top - containerRect.top - padTop),
      Math.round(last.bottom - first.top),
      role
    )
  }
}

/**
 * 移除单个批注的高亮
 */
function removeHighlightForComment(commentId) {
  highlightManager.removeAnnotation(String(commentId))
  if (borderLayer.value) {
    borderLayer.value.removeBar(commentId)
  }
}

// ---- recogito（仅用于选区管理 + 事件） ----
const contentContainer = ref(null)
const showPopup = ref(false)

const {
  annotator, init, loadComments, scrollToComment,
  removeComment, removePendingAnnotation, updateAnnotationId, destroy,
  isSelecting, pendingSelector
} = useTextAnnotator(contentContainer, computed(() => props.articleId))

const allComments = ref([])

// ---- 位置计算（供 AnnotationPanel 卡片定位） ----
function computeAnnotationPositions() {
  const container = contentContainer.value
  if (!container) return { positions: [], readmeContentTop: 0 }
  const containerRect = container.getBoundingClientRect()
  const results = []
  for (const comment of allComments.value) {
    const start = comment.quoteStart ?? 0
    const end = comment.quoteEnd ?? 0
    if (end <= start) continue
    const range = createRangeFromOffsets(container, start, end)
    if (!range) continue
    const rect = range.getBoundingClientRect()
    results.push({ id: comment.id, top: Math.round(rect.top - containerRect.top), comment })
  }
  results.sort((a, b) => a.top - b.top)
  const cardH = 36, gap = 6
  for (let i = 1; i < results.length; i++) {
    if (results[i].top < results[i - 1].top + cardH + gap) results[i].top = results[i - 1].top + cardH + gap
  }
  return { positions: results, readmeContentTop: containerRect.top }
}

function emitPositions() {
  emit('positions-updated', computeAnnotationPositions())
}

// 使用 rAF 延迟触发，确保 DOM 已渲染
let rafId = null
function schedulePositionEmit() {
  if (rafId) return
  rafId = requestAnimationFrame(() => {
    rafId = null
    emitPositions()
  })
}

// ---- 生命周期 ----
watch(isSelecting, (val) => { if (val) showPopup.value = true })

let initialised = false
watch([renderedContent, contentContainer], async ([content, container]) => {
  if (!content || !container || initialised) return
  await nextTick()
  initialised = true

  // 初始化 recogito（仅用于选区管理）
  init(
    () => { showPopup.value = true },
    (annotation) => { emit('select-comment', annotation) }
  )

  // 初始化 BorderLayer
  if (borderLayer.value) borderLayer.value.destroy()
  borderLayer.value = new BorderLayer(container)

  // 加载批注：先加载到 recogito（用于 hover/click/scroll），再渲染视觉高亮
  if (props.articleId > 0) {
    try {
      const comments = await commentApi.getByArticle(props.articleId)
      allComments.value = comments || []
      if (comments?.length) {
        // recogito 加载（用于 hover / click / scrollIntoView）
        loadComments(comments)
        // 自定义渲染（HighlightManager + BorderLayer）
        await nextTick()
        renderAllHighlights(comments, container)
        schedulePositionEmit()
      }
    } catch { /* */ }
  }
})

// ---- 页面重载（保持滚动位置） ----
const router = useRouter()
function reloadPage() {
  // 保存当前滚动位置到 sessionStorage，刷新后恢复
  sessionStorage.setItem(`scrollY_${props.articleId}`, String(window.scrollY))
  router.go(0)
}

// ---- 新建批注 ----
async function onPopupSubmit(content) {
  if (!pendingSelector.value || !content) return
  const sel = pendingSelector.value
  const userStr = localStorage.getItem('currentUser')
  const user = userStr ? JSON.parse(userStr) : null
  if (!user?.id) { showPopup.value = false; return }

  try {
    const comment = await commentApi.create({
      articleId: props.articleId,
      userId: user.id,
      content,
      anchor: JSON.stringify({ exact: sel.quote, start: sel.start, end: sel.end }),
      selectedText: sel.quote,
      quoteStart: sel.start,
      quoteEnd: sel.end
    })
    updateAnnotationId(sel.annotationId, comment.id)
    await refreshAnnotations()
    await nextTick()
    if (contentContainer.value) {
      addHighlightForComment(comment, contentContainer.value)
    }
    emitPositions()
    emit('comment-created', comment)
    // 提交后刷新页面以确保状态一致、滚动正常
    reloadPage()
  } catch (e) {
    removePendingAnnotation(sel.annotationId)
    console.error('Save annotation failed:', e)
    ElMessage.error('批注保存失败: ' + (e?.message || '未知错误'))
  }
  showPopup.value = false
}

function onPopupCancel() {
  if (pendingSelector.value) removePendingAnnotation(pendingSelector.value.annotationId)
  showPopup.value = false
}

// ---- 对外方法 ----
function scrollTo(commentId) { scrollToComment(commentId) }

function removeLocalComment(commentId) {
  // recogito 状态清理
  removeComment(commentId)
  // 自定义渲染层清理
  removeHighlightForComment(commentId)
  // 本地数据清理
  allComments.value = allComments.value.filter(c => c.id !== commentId)
  schedulePositionEmit()
  // 删除后刷新页面以确保状态一致、滚动正常
  reloadPage()
}

async function handleDeleteComment(commentId) {
  const userStr = localStorage.getItem('currentUser')
  const user = userStr ? JSON.parse(userStr) : null
  try {
    await commentApi.delete(commentId, user?.id || 0)
    removeLocalComment(commentId)
  } catch { /* */ }
}

async function refreshAnnotations() {
  if (props.articleId <= 0) return
  try {
    const comments = await commentApi.getByArticle(props.articleId)
    allComments.value = comments || []
    if (comments?.length) {
      // 同步到 recogito（hover / click / scrollIntoView）
      loadComments(comments)
      // 重新渲染视觉高亮
      await nextTick()
      if (contentContainer.value) {
        renderAllHighlights(comments, contentContainer.value)
      }
      schedulePositionEmit()
    }
  } catch { /* */ }
}

defineExpose({ scrollTo, handleDeleteComment, removeLocalComment, refreshAnnotations })

function onResize() {
  if (allComments.value.length) {
    schedulePositionEmit()
    // BorderLayer 自动通过 ResizeObserver 处理
  }
}

if (typeof window !== 'undefined') {
  window.addEventListener('resize', onResize, { passive: true })
}
onBeforeUnmount(() => {
  destroy()
  highlightManager.destroy()
  if (borderLayer.value) {
    borderLayer.value.destroy()
    borderLayer.value = null
  }
  if (rafId) cancelAnimationFrame(rafId)
  if (typeof window !== 'undefined') window.removeEventListener('resize', onResize)
})
</script>

<style>
@import 'highlight.js/styles/github.css';
</style>

<style scoped>
.readme-wrapper {
  margin-top: var(--spacing-lg);
  border: 1px solid var(--color-border-primary);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.readme-header {
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-bg-secondary);
  border-bottom: 1px solid var(--color-border-primary);
  display: flex; align-items: center; justify-content: space-between;
}
.readme-title { font-size: var(--font-size-normal); font-weight: 600; color: var(--color-body-text); }
.annotation-indicator { font-size: 12px; color: var(--color-secondary-text); }
.readme-content { padding: var(--spacing-xl); }

.readme-content :deep(img) { max-width: 100%; height: auto; border-radius: var(--radius-sm); margin: var(--spacing-sm) 0; }
.readme-content :deep(pre) { background: #f6f8fa; border: 1px solid #d0d7de; border-radius: 6px; padding: var(--spacing-md); overflow-x: auto; margin: var(--spacing-md) 0; }
.readme-content :deep(pre code) { background: none; border: none; padding: 0; font-size: 13px; line-height: 1.5; font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace; }
.readme-content :deep(code) { background: #f0f0f0; padding: 2px 6px; border-radius: 3px; font-size: 0.9em; font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace; }
.readme-content :deep(table) { border-collapse: collapse; width: 100%; margin: var(--spacing-md) 0; }
.readme-content :deep(th), .readme-content :deep(td) { border: 1px solid #d0d7de; padding: 8px 12px; text-align: left; }
.readme-content :deep(th) { background: #f6f8fa; font-weight: 600; }
.readme-content :deep(blockquote) { border-left: 4px solid #d0d7de; padding-left: var(--spacing-md); margin: var(--spacing-md) 0; color: var(--color-secondary-text); }
</style>
