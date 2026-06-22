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
    underline: '#e74c3c'
  },
  uploader: {
    bg: 'rgba(52, 152, 219, 0.2)',
    underline: '#3498db'
  },
  user: {
    bg: 'rgba(39, 174, 96, 0.2)',
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
    .map(([role, { bg, underline }]) =>
      `::highlight(anno-${role}) { background-color: ${bg}; text-decoration: underline dashed ${underline}; text-underline-offset: 2px; }`
    )
    .join('\n')
  document.head.appendChild(style)
}

function removeHighlightStyles() {
  document.getElementById(HIGHLIGHT_CSS_ID)?.remove()
}

export class HighlightManager {
  /** @type {Map<string, Highlight>} */
  #highlights = new Map()

  /** @type {Map<string, { range: Range, role: string }>} */
  #entries = new Map()

  #supported = false

  constructor() {
    this.#supported = typeof Highlight !== 'undefined' && typeof CSS !== 'undefined' && CSS.highlights
    if (this.#supported) {
      injectHighlightStyles()
      for (const role of Object.keys(ROLE_STYLES)) {
        const hl = new Highlight()
        CSS.highlights.set(`anno-${role}`, hl)
        this.#highlights.set(role, hl)
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
    if (!this.#supported || !range || range.collapsed) return false
    const hl = this.#highlights.get(role)
    if (!hl) return false

    // 先移除旧条目（支持更新）
    this.removeAnnotation(id)

    hl.add(range)
    this.#entries.set(String(id), { range, role })
    return true
  }

  /**
   * 移除批注高亮
   * @param {string} id
   */
  removeAnnotation(id) {
    if (!this.#supported) return
    const key = String(id)
    const entry = this.#entries.get(key)
    if (!entry) return
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
    if (!this.#supported) return
    for (const hl of this.#highlights.values()) {
      hl.clear()
    }
    this.#entries.clear()
  }

  /**
   * 获取所有已注册的批注 ID
   */
  getEntryIds() {
    return [...this.#entries.keys()]
  }

  /**
   * 销毁管理器，清理资源
   */
  destroy() {
    this.clearAll()
    for (const [role, hl] of this.#highlights) {
      try { CSS.highlights.delete(`anno-${role}`) } catch { /* */ }
    }
    this.#highlights.clear()
    removeHighlightStyles()
  }
}
