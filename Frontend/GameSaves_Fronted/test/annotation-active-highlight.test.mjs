import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

class TestHighlight extends Set {}

const highlightManagerUrl = new URL('../src/composables/HighlightManager.js', import.meta.url)
const textAnnotatorUrl = new URL('../src/composables/useTextAnnotator.js', import.meta.url)
const readmeRendererUrl = new URL('../src/components/article/ReadmeRenderer.vue', import.meta.url)

test('cancelling an annotation notifies the optional cancel callback after clearing selection state', async () => {
  const source = await readFile(textAnnotatorUrl, 'utf8')

  assert.match(source, /function init\(onCreateComment, onSelectComment, onCancelComment\)/)
  assert.match(source, /anno\.on\('selectionChanged', \(annotations\) => \{[\s\S]*?if \(!annotations \|\| annotations\.length === 0\) \{[\s\S]*?selectedAnnotation\.value = null[\s\S]*?isSelecting\.value = false[\s\S]*?pendingSelector\.value = null[\s\S]*?if \(onCancelComment\) onCancelComment\(\)[\s\S]*?\}\s*\}/)
})

test('ReadmeRenderer synchronizes active highlights with annotation selection, cancellation, and panel scrolling', async () => {
  const source = await readFile(readmeRendererUrl, 'utf8')

  assert.match(
    source,
    /init\(\s*\(\) => \{ showPopup\.value = true \},\s*\(annotation\) => \{[\s\S]*?selectActiveAnnotation\(annotation\?\.id\)[\s\S]*?emit\('select-comment', annotation\)\s*\},\s*\(\) => \{\s*pendingActiveCommentId\.value = null\s*highlightManager\.clearActiveAnnotation\(\)\s*clearActiveOverlay\(\)\s*emit\('select-comment', null\)\s*\}\s*\)/
  )
  assert.match(
    source,
    /function scrollTo\(commentId\)\s*\{[\s\S]*?scrollToComment\(commentId\)[\s\S]*?if \(!selectActiveAnnotation\(commentId\)\) \{\s*pendingActiveCommentId\.value = commentId \?\? null[\s\S]*?\} else \{\s*pendingActiveCommentId\.value = null\s*\}\s*\}/
  )
})

test('ReadmeRenderer retries a panel selection made before highlight entries are rendered', async () => {
  const source = await readFile(readmeRendererUrl, 'utf8')

  assert.match(source, /const pendingActiveCommentId = ref\(null\)/)
  assert.match(
    source,
    /if \(!selectActiveAnnotation\(annotation\?\.id\)\) \{\s*pendingActiveCommentId\.value = annotation\?\.id \?\? null\s*\} else \{\s*pendingActiveCommentId\.value = null\s*\}/
  )
  assert.match(
    source,
    /function activatePendingComment\(\) \{\s*const commentId = pendingActiveCommentId\.value\s*if \(commentId == null\) return\s*scrollToComment\(commentId\)\s*if \(selectActiveAnnotation\(commentId\)\) \{\s*pendingActiveCommentId\.value = null\s*\}\s*\}/
  )
  assert.match(source, /renderAllHighlights\(comments, container\)\s*activatePendingComment\(\)/)
  assert.match(
    source,
    /async function refreshAnnotations\(\) \{[\s\S]*?renderAllHighlights\(comments, contentContainer\.value\)\s*activatePendingComment\(\)/
  )
  assert.match(
    source,
    /async function refreshAnnotations\(\) \{[\s\S]*?const activeCommentId = highlightManager\.getActiveAnnotationId\(\)\s*if \(pendingActiveCommentId\.value == null && activeCommentId != null\) \{\s*pendingActiveCommentId\.value = activeCommentId\s*\}[\s\S]*?renderAllHighlights\(comments, contentContainer\.value\)\s*activatePendingComment\(\)/
  )
})

test('ReadmeRenderer clears visual annotation state when refresh returns no comments', async () => {
  const source = await readFile(readmeRendererUrl, 'utf8')

  assert.match(
    source,
    /async function refreshAnnotations\(\) \{[\s\S]*?if \(comments\?\.length\) \{[\s\S]*?\} else \{\s*pendingActiveCommentId\.value = null\s*if \(contentContainer\.value\) \{\s*renderAllHighlights\(\[\], contentContainer\.value\)\s*\}\s*schedulePositionEmit\(\)\s*\}/
  )
})

test('ReadmeRenderer emits groups from annotation first client rects', async () => {
  const source = await readFile(readmeRendererUrl, 'utf8')

  assert.match(source, /import \{ groupAnnotationLines \} from '\.\.\/\.\.\/composables\/annotationLineGroups'/)
  assert.match(source, /const rects = range\.getClientRects\(\)/)
  assert.match(source, /rects\[0\]\.top - containerRect\.top/)
  assert.match(source, /positions: groupAnnotationLines\(candidates\)/)
})

test('AnnotationPanel renders compact line summaries and focused groups', async () => {
  const panelUrl = new URL('../src/components/article/AnnotationPanel.vue', import.meta.url)
  const source = await readFile(panelUrl, 'utf8')

  assert.match(source, /const focusedGroupId = ref\(null\)/)
  assert.match(source, /function openGroup\(group\)/)
  assert.match(source, /function closeFocusedGroup\(\)/)
  assert.match(source, /function summarySelectedText\(group\)/)
  assert.match(source, /group\.commentIds\.length/)
})

test('AnnotationPanel keeps focused details at their source line and uses a static panel label', async () => {
  const panelUrl = new URL('../src/components/article/AnnotationPanel.vue', import.meta.url)
  const source = await readFile(panelUrl, 'utf8')

  assert.match(source, /<h3 class="annotation-title">批注栏<\/h3>/)
  assert.doesNotMatch(source, /<span v-if="allComments\.length" class="annotation-count">/)
  assert.match(source, /class="focused-group"\s*:style="\{ top: focusedGroup\.top \+ 'px' \}"/)
  assert.doesNotMatch(source, /\.focused-group\s*\{[\s\S]*?inset:\s*0;/)
})

function installHighlightDom() {
  const elements = new Map()
  const originalHighlight = globalThis.Highlight
  const originalCss = globalThis.CSS
  const originalDocument = globalThis.document

  globalThis.Highlight = TestHighlight
  globalThis.CSS = { highlights: new Map() }
  globalThis.document = {
    getElementById(id) {
      return elements.get(id)
    },
    createElement() {
      return {
        remove() {
          elements.delete(this.id)
        }
      }
    },
    head: {
      appendChild(element) {
        elements.set(element.id, element)
      }
    }
  }

  return () => {
    globalThis.Highlight = originalHighlight
    globalThis.CSS = originalCss
    globalThis.document = originalDocument
  }
}

test('active annotation highlight follows the current selection', async (t) => {
  const restoreDom = installHighlightDom()
  t.after(restoreDom)

  const { HighlightManager } = await import(`${highlightManagerUrl.href}?active-highlight=${Date.now()}`)
  const manager = new HighlightManager()
  const userRange = { collapsed: false }
  const uploaderRange = { collapsed: false }

  manager.addAnnotation('user-note', userRange, 'user')
  manager.addAnnotation('uploader-note', uploaderRange, 'uploader')

  manager.setActiveAnnotation('user-note')

  assert.equal(manager.getActiveAnnotationId(), 'user-note')
  assert.ok(CSS.highlights.has('anno-active-user'))
  assert.ok(CSS.highlights.get('anno-active-user').has(userRange))

  manager.setActiveAnnotation('uploader-note')

  assert.equal(manager.getActiveAnnotationId(), 'uploader-note')
  assert.ok(CSS.highlights.has('anno-active-uploader'))
  assert.equal(CSS.highlights.get('anno-active-user').has(userRange), false)
  assert.ok(CSS.highlights.get('anno-active-uploader').has(uploaderRange))

  manager.removeAnnotation('uploader-note')

  assert.equal(manager.getActiveAnnotationId(), null)
  assert.equal(CSS.highlights.get('anno-active-uploader').size, 0)
})

test('replacing the active annotation preserves its active range', async (t) => {
  const restoreDom = installHighlightDom()
  t.after(restoreDom)

  const { HighlightManager } = await import(`${highlightManagerUrl.href}?active-replacement=${Date.now()}`)
  const manager = new HighlightManager()
  const firstRange = { collapsed: false }
  const oldSecondRange = { collapsed: false }
  const newSecondRange = { collapsed: false }

  manager.addAnnotation('first', firstRange, 'user')
  manager.addAnnotation('second', oldSecondRange, 'uploader')
  manager.setActiveAnnotation('second')

  manager.addAnnotation('second', newSecondRange, 'uploader')

  assert.equal(manager.getActiveAnnotationId(), 'second')
  assert.equal(CSS.highlights.get('anno-active-uploader').has(oldSecondRange), false)
  assert.ok(CSS.highlights.get('anno-active-uploader').has(newSecondRange))
})
