/**
 * CSS Custom Highlight API 管理器
 *
 * 使用浏览器原生 CSS.highlights 渲染批注高亮（背景色 + 虚线下划线）。
 * 零 DOM 开销，浏览器 GPU 合成，resize 自动适应。
 *
 * 浏览器要求: Chrome 105+, Edge 105+
 * 不支持时静默降级（高亮不显示，但不影响其他功能）
 */

// 角色颜色配置
const ROLE_STYLES = {
  admin: {
    bg: 'rgba(231, 76, 60, 0.2)',
    activeBg: 'rgba(231, 76, 60, 0.42)',
    underline: '#e74c3c'
  },
  uploader: {
    bg: 'rgba(52, 152, 219, 0.2)',
    activeBg: 'rgba(52, 152, 219, 0.42)',
    underline: '#3498db'
  },
  user: {
    bg: 'rgba(39, 174, 96, 0.2)',
    activeBg: 'rgba(39, 174, 96, 0.42)',
    underline: '#27ae60'
  }
}

// 用于设置 ::highlight() CSS（通过插入 <style> 标签）
const HIGHLIGHT_CSS_ID = 'annotation-highlight-styles'

function injectHighlightStyles() {
  if (document.getElementById(HIGHLIGHT_CSS_ID)) return
  const style = document.createElement('style')
  style.id = HIGHLIGHT_CSS_ID
  style.textContent = Object.entries(ROLE_STYLES)
    .map(([role, { bg, activeBg, underline }]) =>
      `::highlight(anno-${role}) { background-color: ${bg}; text-decoration: underline dashed ${underline}; text-underline-offset: 2px; }\n` +
      `::highlight(anno-active-${role}) { background-color: ${activeBg}; }`
    )
    .join('\n')
  document.head.appendChild(style)
}

function removeHighlightStyles() {
  if (typeof document === 'undefined') return
  document.getElementById(HIGHLIGHT_CSS_ID)?.remove()
}

export class HighlightManager {
  /** @type {Map<string, Highlight>} */
  #highlights = new Map()

  /** @type {Map<string, Highlight>} */
  #activeHighlights = new Map()

  /** @type {Map<string, { range: Range, role: string }>} */
  #entries = new Map()

  #supported = false
  #visible = true
  #activeAnnotationId = null

  constructor() {
    this.#supported = typeof Highlight !== 'undefined' && typeof CSS !== 'undefined' && CSS.highlights
    if (this.#supported) {
      injectHighlightStyles()
      for (const role of Object.keys(ROLE_STYLES)) {
        const hl = new Highlight()
        CSS.highlights.set(`anno-${role}`, hl)
        this.#highlights.set(role, hl)

        const activeHl = new Highlight()
        CSS.highlights.set(`anno-active-${role}`, activeHl)
        this.#activeHighlights.set(role, activeHl)
      }
    }
  }

  /** 是否支持 CSS Highlight API */
  get supported() {
    return this.#supported
  }

  /**
   * 添加批注高亮
   * @param {string} id - 批注 ID
   * @param {Range} range - DOM Range
   * @param {'admin'|'uploader'|'user'} role
   */
  addAnnotation(id, range, role) {
    if (!range || range.collapsed) return false
    const key = String(id)
    const wasActive = this.#activeAnnotationId === key

    // 先移除旧条目（支持更新）
    this.removeAnnotation(key)

    this.#entries.set(key, { range, role })
    if (this.#supported) {
      const hl = this.#highlights.get(role)
      if (hl) hl.add(range)
    }
    if (wasActive) this.setActiveAnnotation(key)
    return true
  }

  /**
   * 移除批注高亮
   * @param {string} id
   */
  removeAnnotation(id) {
    const key = String(id)
    const entry = this.#entries.get(key)
    if (!entry) return
    if (this.#activeAnnotationId === key) {
      this.clearActiveAnnotation()
    }
    const hl = this.#highlights.get(entry.role)
    if (hl) {
      try { hl.delete(entry.range) } catch { /* range may have been GC'd */ }
    }
    this.#entries.delete(key)
  }

  /**
   * 清除所有高亮
   */
  clearAll() {
    if (this.#supported) {
      for (const hl of this.#highlights.values()) {
        hl.clear()
      }
      for (const activeHl of this.#activeHighlights.values()) {
        activeHl.clear()
      }
    }
    this.#entries.clear()
    this.#activeAnnotationId = null
  }

  /**
   * 设置当前活动批注，使其使用更醒目的高亮样式。
   * @param {string} id
   * @returns {boolean} 是否找到并设置了该批注
   */
  setActiveAnnotation(id) {
    this.clearActiveAnnotation()
    const key = String(id)
    const entry = this.#entries.get(key)
    if (!entry) return false

    this.#activeAnnotationId = key
    if (this.#visible) {
      const activeHl = this.#activeHighlights.get(entry.role)
      if (activeHl) {
        try { activeHl.add(entry.range) } catch { /* range may have been GC'd */ }
      }
    }
    return true
  }

  /**
   * 获取批注的 Range 与角色，供不支持 CSS Highlight API 时的视觉降级层使用。
   * @param {string} id
   */
  getAnnotation(id) {
    return this.#entries.get(String(id)) || null
  }

  /** 清除当前活动批注的额外高亮。 */
  clearActiveAnnotation() {
    if (this.#supported) {
      for (const activeHl of this.#activeHighlights.values()) {
        activeHl.clear()
      }
    }
    this.#activeAnnotationId = null
  }

  /** 获取当前活动批注 ID。 */
  getActiveAnnotationId() {
    return this.#activeAnnotationId
  }

  /**
   * 获取所有已注册的批注 ID
   */
  getEntryIds() {
    return [...this.#entries.keys()]
  }

  /**
   * 显示/隐藏所有高亮。
   * 隐藏时调用 Highlight.clear() 清除范围（Highlight 对象保留在 CSS.highlights 中），
   * 显示时从 #entries 中恢复所有 Range。使用标准 API，不操作样式表。
   * @param {boolean} visible
   */
  setVisible(visible) {
    this.#visible = visible
    if (!this.#supported) return
    if (visible) {
      for (const [, { range, role }] of this.#entries) {
        const hl = this.#highlights.get(role)
        if (hl) {
          try { hl.add(range) } catch { /* range may have been GC'd */ }
        }
      }
      const activeEntry = this.#entries.get(this.#activeAnnotationId)
      if (activeEntry) {
        const activeHl = this.#activeHighlights.get(activeEntry.role)
        if (activeHl) {
          try { activeHl.add(activeEntry.range) } catch { /* range may have been GC'd */ }
        }
      }
    } else {
      for (const hl of this.#highlights.values()) {
        hl.clear()
      }
      for (const activeHl of this.#activeHighlights.values()) {
        activeHl.clear()
      }
    }
  }

  /** 当前是否可见 */
  getVisible() {
    return this.#visible
  }

  /**
   * 销毁管理器，清理资源
   */
  destroy() {
    this.clearAll()
    for (const [role, hl] of this.#highlights) {
      try { CSS.highlights.delete(`anno-${role}`) } catch { /* */ }
    }
    for (const [role, activeHl] of this.#activeHighlights) {
      try { CSS.highlights.delete(`anno-active-${role}`) } catch { /* */ }
    }
    this.#highlights.clear()
    this.#activeHighlights.clear()
    removeHighlightStyles()
  }
}
