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
      <div class="readme-content-shell">
        <div
          ref="contentContainer"
          class="readme-content markdown-body"
          v-html="renderedContent"
          @click="handleAnchorClick"
        />
        <div ref="activeOverlayRef" class="active-annotation-overlay" aria-hidden="true" />
      </div>
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
import { marked } from 'marked'
import { ElMessage } from 'element-plus'
import hljs from 'highlight.js'
import EmptyState from '../common/EmptyState.vue'
import LoadingSkeleton from '../common/LoadingSkeleton.vue'
import AnnotationPopup from './AnnotationPopup.vue'
import { useTextAnnotator } from '../../composables/useTextAnnotator'
import { HighlightManager } from '../../composables/HighlightManager'
import { BorderLayer } from '../../composables/BorderLayer'
import { groupAnnotationLines } from '../../composables/annotationLineGroups'
import { useArticleStore } from '../../stores/articles'
import { commentApi } from '../../api/commentApi'

const props = defineProps({
  articleId: { type: Number, default: 0 },
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
    return `<img src="${url}" alt="${escapeAttr(token.text || '')}" loading="lazy"${titleAttr}>`
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
  // Plan B: always render from raw Markdown via marked (GFM)
  const src = props.raw
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

let lastProgrammaticSelect = 0

function onDocumentClick() {
  // 如果是程序化选中（scrollTo 刚被调用），跳过取消检查。
  // 否则 recogito 的 pointerdown 清空选中状态和 scrollTo 的 setSelected 会产生竞态。
  if (Date.now() - lastProgrammaticSelect < 300) return

  const activeIdBefore = highlightManager.getActiveAnnotationId()
  if (activeIdBefore == null) return

  // 用 rAF + setTimeout 确保 recogito 状态已稳定
  requestAnimationFrame(() => {
    setTimeout(() => {
      const selected = annotator.value?.getSelected?.()
      if (!selected || selected.length === 0) {
        if (highlightManager.getActiveAnnotationId() != null) {
          pendingActiveCommentId.value = null
          highlightManager.clearActiveAnnotation()
          clearActiveOverlay()
          emit('select-comment', null)
        }
      }
    }, 100)
  })
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
const activeOverlayRef = ref(null)

const ACTIVE_OVERLAY_STYLES = {
  admin: { background: 'rgba(231, 76, 60, 0.42)', line: '#e74c3c' },
  uploader: { background: 'rgba(52, 152, 219, 0.42)', line: '#3498db' },
  user: { background: 'rgba(39, 174, 96, 0.42)', line: '#27ae60' }
}

function clearActiveOverlay() {
  if (activeOverlayRef.value) activeOverlayRef.value.replaceChildren()
}

function renderActiveOverlay(commentId) {
  const container = contentContainer.value
  const overlay = activeOverlayRef.value
  clearActiveOverlay()
  if (!container || !overlay || commentId == null) {
    console.debug('[ActiveOverlay] skip — container=%o overlay=%o commentId=%o', !!container, !!overlay, commentId)
    return
  }
  const entry = highlightManager.getAnnotation(commentId)
  if (!entry) {
    console.debug('[ActiveOverlay] entry not found for commentId=%o. Known entries: %o', commentId, highlightManager.getEntryIds())
    return
  }

  const containerRect = container.getBoundingClientRect()
  const rects = entry.range.getClientRects()
  const style = ACTIVE_OVERLAY_STYLES[entry.role] || ACTIVE_OVERLAY_STYLES.user
  let blockCount = 0
  let firstPos = null
  for (const rect of rects) {
    if (!rect.width || !rect.height) continue
    const left = rect.left - containerRect.left
    const top = rect.top - containerRect.top
    if (!firstPos) firstPos = { left: Math.round(left), top: Math.round(top), w: Math.round(rect.width), h: Math.round(rect.height) }
    const block = document.createElement('div')
    Object.assign(block.style, {
      position: 'absolute', left: `${left}px`, top: `${top}px`,
      width: `${rect.width}px`, height: `${rect.height}px`, boxSizing: 'border-box',
      backgroundColor: style.background,
      outline: `2px dashed ${style.line}`,
      outlineOffset: '1px',
      borderRadius: '3px'
    })
    overlay.appendChild(block)
    blockCount++
  }
  console.debug('[ActiveOverlay] commentId=%o role=%s rectCount=%d blocks=%d firstBlock=%o',
    commentId, entry.role, rects.length, blockCount, firstPos)
}

function selectActiveAnnotation(commentId) {
  if (!highlightManager.setActiveAnnotation(commentId)) return false
  renderActiveOverlay(commentId)
  return true
}

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
 * 渲染所有批注高亮（HighlightManager 背景+下划线 + BorderLayer 左边色条）
 */
function renderAllHighlights(comments, container) {
  // 清空旧高亮
  highlightManager.clearAll()
  clearActiveOverlay()

  const borderItems = []
  const containerRect = container.getBoundingClientRect()
  let rangeFailCount = 0

  for (const comment of comments) {
    const start = comment.quoteStart ?? 0
    const end = comment.quoteEnd ?? 0
    if (end <= start) continue

    const range = createRangeFromOffsets(container, start, end)
    if (!range) { rangeFailCount++; continue }

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
        // rects 和 containerRect 都是 viewport 坐标，差值得到相对容器内边缘的位置
        top: Math.round(first.top - containerRect.top),
        height: Math.round(last.bottom - first.top),
        role
      })
    }
  }

  if (rangeFailCount > 0) {
    console.debug('[HighlightManager] renderAllHighlights: %d/%d comments failed range creation',
      rangeFailCount, comments.length)
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
    const first = rects[0]
    const last = rects[rects.length - 1]
    borderLayer.value.addBar(
      comment.id,
      Math.round(first.top - containerRect.top),
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
const pendingActiveCommentId = ref(null)

function activatePendingComment() {
  const commentId = pendingActiveCommentId.value
  if (commentId == null) return
  scrollToComment(commentId)
  if (selectActiveAnnotation(commentId)) {
    pendingActiveCommentId.value = null
  }
}

// Plan B: 本地 py 缓存 — 创建时直接测量存入，resize 时批量更新
// 优先于 anchor.py，解决 DOM Range 重建的漂移问题
const pyCache = ref(new Map())

// ---- 位置计算（供 AnnotationPanel 卡片定位） ----
function computeAnnotationPositions() {
  const container = contentContainer.value
  if (!container) return { positions: [], readmeContentTop: 0 }
  const containerRect = container.getBoundingClientRect()
  const candidates = []

  for (const comment of allComments.value) {
    const range = createRangeFromOffsets(container, comment.quoteStart ?? 0, comment.quoteEnd ?? 0)
    let top = pyCache.value.get(comment.id) ?? null
    if (range) {
      const rects = range.getClientRects()
      if (rects.length > 0) top = Math.round(rects[0].top - containerRect.top)
    }

    if (top != null) candidates.push({ commentId: comment.id, top })
  }

  return { positions: groupAnnotationLines(candidates), readmeContentTop: containerRect.top }
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
    (annotation) => {
      if (!selectActiveAnnotation(annotation?.id)) {
        pendingActiveCommentId.value = annotation?.id ?? null
      } else {
        pendingActiveCommentId.value = null
      }
      emit('select-comment', annotation)
    },
    () => {
      pendingActiveCommentId.value = null
      highlightManager.clearActiveAnnotation()
      clearActiveOverlay()
      emit('select-comment', null)
    }
  )

  // 初始化 BorderLayer
  if (borderLayer.value) borderLayer.value.destroy()
  borderLayer.value = new BorderLayer(container)

  // 加载批注：先加载到 recogito（用于 hover/click/scroll），再渲染视觉高亮
  if (props.articleId > 0) {
    try {
      const comments = await commentApi.getByArticle(props.articleId)
      allComments.value = comments || []
      // Plan B: 从 anchor JSON 提取 py 填充本地缓存
      if (comments) {
        for (const c of comments) {
          try {
            const anchor = typeof c.anchor === 'string' ? JSON.parse(c.anchor) : c.anchor
            if (typeof anchor?.py === 'number') pyCache.value.set(c.id, anchor.py)
          } catch { /* anchor parse error */ }
        }
      }
      if (comments?.length) {
        // recogito 加载（用于 hover / click / scrollIntoView）
        loadComments(comments)
        await nextTick()
        // 自定义渲染（HighlightManager + BorderLayer）
        renderAllHighlights(comments, container)
        activatePendingComment()
        // Plan B: 首次加载时，为所有没有 anchor.py 的老批注预填充 pyCache。
        // 此时 DOM 最干净，Range 测量最可靠。后续增量操作中不再依赖 fallback。
        await nextTick()
        const containerRect = container.getBoundingClientRect()
        for (const comment of comments) {
          if (pyCache.value.has(comment.id)) continue // 已有 anchor.py 的跳过
          const start = comment.quoteStart ?? 0
          const end = comment.quoteEnd ?? 0
          if (end <= start) continue
          const range = createRangeFromOffsets(container, start, end)
          if (range) {
            pyCache.value.set(comment.id, Math.round(range.getBoundingClientRect().top - containerRect.top))
          }
        }
        schedulePositionEmit()
      }
    } catch { /* */ }
  }
})

// ---- 新建批注 ----
async function onPopupSubmit(content) {
  if (!pendingSelector.value || !content) return
  const sel = pendingSelector.value
  const userStr = localStorage.getItem('currentUser')
  const user = userStr ? JSON.parse(userStr) : null
  if (!user?.id) { showPopup.value = false; return }

  // Plan B: 在 recogito 做任何额外操作前，立刻测量选中文字的像素 Y 坐标。
  // 此时 DOM 处于干净状态（recogito 刚捕获选区，尚未调用 loadAnnotations），
  // TreeWalker 输出与 offset 一致，测量值可靠。
  let positionY = 0
  if (contentContainer.value) {
    const range = createRangeFromOffsets(contentContainer.value, sel.start, sel.end)
    if (range) {
      const containerRect = contentContainer.value.getBoundingClientRect()
      positionY = Math.round(range.getBoundingClientRect().top - containerRect.top)
    }
  }

  try {
    const comment = await commentApi.create({
      articleId: props.articleId,
      userId: user.id,
      content,
      anchor: JSON.stringify({ exact: sel.quote, start: sel.start, end: sel.end, py: positionY }),
      selectedText: sel.quote,
      quoteStart: sel.start,
      quoteEnd: sel.end
    })
    updateAnnotationId(sel.annotationId, comment.id)
    // 增量追加到本地列表
    allComments.value = [...allComments.value, comment]
    // Plan B: 新批注的 py 已存入 anchor，也写入本地缓存
    pyCache.value.set(comment.id, positionY)
    // recogito 加载（扫描文本节点建立 spatial index，用于 hover/click/scroll）
    loadComments(allComments.value)
    await nextTick()
    if (contentContainer.value) {
      addHighlightForComment(comment, contentContainer.value)
    }
    // Plan B: computeAnnotationPositions 会从 pyCache 读取新批注的 py，
    // 不再依赖受 recogito 扫描影响的 TreeWalker Range 重建。
    // 使用 schedulePositionEmit（rAF）确保 DOM 布局完全稳定后再测量 readmeContentTop
    schedulePositionEmit()
    emit('comment-created', comment)
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
function scrollTo(commentId) {
  lastProgrammaticSelect = Date.now()
  console.debug('[ActiveOverlay] scrollTo called with commentId=%o', commentId)
  scrollToComment(commentId)
  if (!selectActiveAnnotation(commentId)) {
    pendingActiveCommentId.value = commentId ?? null
    console.debug('[ActiveOverlay] selectActiveAnnotation failed — pendingActiveCommentId=%o', pendingActiveCommentId.value)
  } else {
    pendingActiveCommentId.value = null
  }
}

function removeLocalComment(commentId) {
  // recogito 状态清理
  removeComment(commentId)
  // 自定义渲染层清理
  removeHighlightForComment(commentId)
  // Plan B: 清理 py 缓存
  pyCache.value.delete(commentId)
  // 本地数据清理
  allComments.value = allComments.value.filter(c => c.id !== commentId)
  schedulePositionEmit()
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
    // Plan B: 从 anchor JSON 提取 py 填充本地缓存
    if (comments) {
      for (const c of comments) {
        try {
          const anchor = typeof c.anchor === 'string' ? JSON.parse(c.anchor) : c.anchor
          if (typeof anchor?.py === 'number') pyCache.value.set(c.id, anchor.py)
        } catch { /* anchor parse error */ }
      }
    }
    if (comments?.length) {
      // 同步到 recogito（hover / click / scrollIntoView）
      loadComments(comments)
      await nextTick()
      if (contentContainer.value) {
        const activeCommentId = highlightManager.getActiveAnnotationId()
        if (pendingActiveCommentId.value == null && activeCommentId != null) {
          pendingActiveCommentId.value = activeCommentId
        }
        renderAllHighlights(comments, contentContainer.value)
        activatePendingComment()
      }
      // Plan B: 为所有没有 anchor.py 的老批注预填充 pyCache
      await nextTick()
      const container = contentContainer.value
      if (container) {
        const containerRect = container.getBoundingClientRect()
        for (const comment of comments) {
          if (pyCache.value.has(comment.id)) continue
          const start = comment.quoteStart ?? 0
          const end = comment.quoteEnd ?? 0
          if (end <= start) continue
          const range = createRangeFromOffsets(container, start, end)
          if (range) {
            pyCache.value.set(comment.id, Math.round(range.getBoundingClientRect().top - containerRect.top))
          }
        }
      }
      schedulePositionEmit()
    } else {
      pendingActiveCommentId.value = null
      if (contentContainer.value) {
        renderAllHighlights([], contentContainer.value)
      }
      schedulePositionEmit()
    }
  } catch { /* */ }
}

/**
 * 统一控制批注视觉层的显隐（高亮 + 边框条）。
 * 由父组件 ArticlePage 的批注开关调用。
 */
function setAnnotationsVisible(visible) {
  highlightManager.setVisible(visible)
  if (activeOverlayRef.value) activeOverlayRef.value.style.display = visible ? '' : 'none'
  if (borderLayer.value) {
    borderLayer.value.setVisible(visible)
  }
}

defineExpose({ scrollTo, handleDeleteComment, removeLocalComment, refreshAnnotations, setAnnotationsVisible })

function onResize() {
  if (!allComments.value.length) return
  // Plan B: resize 时文本重排，py 会失效。清除缓存 → Range 重测 → 写入新 py。
  // 此时 recogito 处于稳态（没有正在进行的选区创建），Range 重建结果可靠。
  pyCache.value.clear()
  // 用 Range 测量更新 pyCache（computeAnnotationPositions 的 fallback 路径）
  const container = contentContainer.value
  if (container) {
    for (const comment of allComments.value) {
      const start = comment.quoteStart ?? 0
      const end = comment.quoteEnd ?? 0
      if (end <= start) continue
      const range = createRangeFromOffsets(container, start, end)
      if (!range) continue
      const containerRect = container.getBoundingClientRect()
      const py = Math.round(range.getBoundingClientRect().top - containerRect.top)
      pyCache.value.set(comment.id, py)
    }
  }
  schedulePositionEmit()
  const activeCommentId = highlightManager.getActiveAnnotationId()
  if (activeCommentId != null) renderActiveOverlay(activeCommentId)
  // BorderLayer 自动通过 ResizeObserver 处理
}

if (typeof window !== 'undefined') {
  window.addEventListener('resize', onResize, { passive: true })
  document.addEventListener('click', onDocumentClick, { passive: true })
}
onBeforeUnmount(() => {
  destroy()
  highlightManager.destroy()
  if (borderLayer.value) {
    borderLayer.value.destroy()
    borderLayer.value = null
  }
  if (rafId) cancelAnimationFrame(rafId)
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', onResize)
    document.removeEventListener('click', onDocumentClick)
  }
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
.readme-content-shell { position: relative; }
.readme-content { padding: var(--spacing-xl); }
.active-annotation-overlay { position: absolute; inset: 0; pointer-events: none; z-index: 1; }

.readme-content :deep(img) { max-width: 100%; height: auto; border-radius: var(--radius-sm); margin: var(--spacing-sm) 0; }
.readme-content :deep(pre) { background: #f6f8fa; border: 1px solid #d0d7de; border-radius: 6px; padding: var(--spacing-md); overflow-x: auto; margin: var(--spacing-md) 0; }
.readme-content :deep(pre code) { background: none; border: none; padding: 0; font-size: 13px; line-height: 1.5; font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace; }
.readme-content :deep(code) { background: #f0f0f0; padding: 2px 6px; border-radius: 3px; font-size: 0.9em; font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace; }
.readme-content :deep(table) { border-collapse: collapse; width: 100%; margin: var(--spacing-md) 0; }
.readme-content :deep(th), .readme-content :deep(td) { border: 1px solid #d0d7de; padding: 8px 12px; text-align: left; }
.readme-content :deep(th) { background: #f6f8fa; font-weight: 600; }
.readme-content :deep(blockquote) { border-left: 4px solid #d0d7de; padding-left: var(--spacing-md); margin: var(--spacing-md) 0; color: var(--color-secondary-text); }
</style>
