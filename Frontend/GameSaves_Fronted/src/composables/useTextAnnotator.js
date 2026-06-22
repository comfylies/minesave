import { ref, onUnmounted, watch } from 'vue'
import { createTextAnnotator } from '@recogito/text-annotator'
import '@recogito/text-annotator/text-annotator.css'

/**
 * recogito 封装 — 仅用于文本选区管理和事件系统。
 *
 * 渲染（高亮背景 + 虚线下划线 + 左侧色条）由 HighlightManager + BorderLayer 负责。
 * 这里传入一个 no-op 自定义 renderer，禁止 recogito 做任何 DOM 渲染，
 * 消除 SPANS renderer 的 O(n) redraw 开销和 innerHTML 清空行为。
 */

// no-op renderer：recogito 的内部状态管理（spatial index、hover、selection）正常运行，
// 但不产生任何 DOM 输出。全部视觉渲染交给我们自己的 HighlightManager + BorderLayer。
function createNoopRenderer() {
  return {
    destroy: () => {},
    redraw: () => {},
    setVisible: () => {},
    setFilter: () => {},
    setStyle: () => {}
  }
}

export function useTextAnnotator(containerRef, articleId) {
  const annotator = ref(null)
  const annotations = ref([])
  const selectedAnnotation = ref(null)
  const isSelecting = ref(false)
  const pendingSelector = ref(null)

  /**
   * 将后端批注转为 W3C Web Annotation 标准格式。
   *
   * 之前 BUG：selector 数组项缺少 type 字段，recogito 的 qe() 解析器
   * 在 switch(h.type) 时无法匹配 TextQuoteSelector / TextPositionSelector，
   * 导致 selector 解析失败 → 零 SPANS → 零高亮。
   *
   * 修复：拆分为两个标准 selector 并添加 type 字段。
   */
  function commentsToAnnotations(comments) {
    return comments.map(comment => {
      let anchor = {}
      try {
        anchor = typeof comment.anchor === 'string'
          ? JSON.parse(comment.anchor)
          : comment.anchor
      } catch { /* ignore */ }

      const quote = comment.selectedText || anchor.exact || ''
      const start = comment.quoteStart ?? anchor.start ?? 0
      const end = comment.quoteEnd ?? anchor.end ?? 0

      // 至少需要一个有效值，否则 recogito 无法定位
      if (!quote && end <= start) return null

      return {
        id: String(comment.id),
        bodies: [{
          id: `body-${comment.id}`,
          value: comment.content,
          created: comment.createdAt,
          creator: {
            id: String(comment.userId),
            name: comment.nickname || `User ${comment.userId}`
          },
          role: comment.role || 'user',
          userId: comment.userId
        }],
        target: {
          selector: [
            ...(quote ? [{ type: 'TextQuoteSelector', exact: quote }] : []),
            ...(end > start ? [{ type: 'TextPositionSelector', start, end }] : [])
          ]
        }
      }
    }).filter(Boolean) // 过滤掉 null
  }

  function init(onCreateComment, onSelectComment) {
    if (!containerRef.value) return

    if (annotator.value) {
      try { annotator.value.destroy() } catch { /* ignore */ }
      annotator.value = null
    }

    // 使用 no-op renderer：recogito 管理 selection / hover / spatial index，
    // 但不做任何 DOM 渲染
    const anno = createTextAnnotator(containerRef.value, {
      selectionMode: 'all',
      renderer: createNoopRenderer
    })

    anno.on('createAnnotation', (v3annotation) => {
      const selector = v3annotation?.target?.selector?.[0]
      if (selector && selector.quote) {
        pendingSelector.value = {
          annotationId: v3annotation.id,
          quote: selector.quote,
          start: selector.start || 0,
          end: selector.end || 0
        }
        isSelecting.value = true
        if (onCreateComment) onCreateComment(v3annotation)
      }
    })

    anno.on('selectAnnotation', (v3annotation) => {
      selectedAnnotation.value = v3annotation
      if (onSelectComment) onSelectComment(v3annotation)
    })

    anno.on('cancelSelected', () => {
      selectedAnnotation.value = null
      isSelecting.value = false
      pendingSelector.value = null
    })

    annotator.value = anno
  }

  function loadComments(comments) {
    if (!annotator.value || !comments?.length) return
    const annoAnnotations = commentsToAnnotations(comments)
    // 过滤掉无效的 annotation（无 quote 且 offsets 无效）
    if (!annoAnnotations.length) return
    annotations.value = annoAnnotations
    try {
      annotator.value.loadAnnotations(annoAnnotations)
    } catch (e) {
      console.warn('recogito loadAnnotations failed', e)
    }
  }

  function addComment(comment) {
    if (!annotator.value) return
    const annoAnnotations = commentsToAnnotations([comment])
    if (!annoAnnotations.length) return
    annotations.value.push(annoAnnotations[0])
    annotator.value.loadAnnotations([annoAnnotations[0]])
  }

  function removeComment(commentId) {
    if (!annotator.value) return
    const id = String(commentId)
    try {
      annotator.value.state.store.deleteAnnotation(id)
    } catch { /* ignore */ }
    annotations.value = annotations.value.filter(a => a.id !== id)
  }

  function scrollToComment(commentId) {
    if (!annotator.value) return
    const id = String(commentId)
    annotator.value.scrollIntoView(id)
    annotator.value.state.store.selectAnnotation(id)
  }

  function removePendingAnnotation(annotationId) {
    if (!annotator.value || !annotationId) return
    try {
      annotator.value.state.store.deleteAnnotation(annotationId)
    } catch { /* ignore */ }
    pendingSelector.value = null
    isSelecting.value = false
  }

  function updateAnnotationId(oldId, newId) {
    if (!annotator.value || !oldId || !newId) return
    try {
      annotator.value.state.store.updateAnnotation(oldId, { id: String(newId) })
    } catch { /* ignore */ }
  }

  function destroy() {
    if (annotator.value) {
      try { annotator.value.destroy() } catch { /* ignore */ }
      annotator.value = null
    }
    annotations.value = []
    selectedAnnotation.value = null
    isSelecting.value = false
    pendingSelector.value = null
  }

  watch(articleId, () => { destroy() })

  onUnmounted(() => { destroy() })

  return {
    annotator,
    annotations,
    selectedAnnotation,
    isSelecting,
    pendingSelector,
    init,
    loadComments,
    addComment,
    removeComment,
    scrollToComment,
    removePendingAnnotation,
    updateAnnotationId,
    destroy
  }
}
