/**
 * 批注边框覆盖层
 *
 * 在 README 内容上方渲染极简的绝对定位层，仅绘制每个批注的左侧 3px 色条。
 * 每个 annotation 仅创建 1 个 div，批量更新使用 rAF，ResizeObserver 自动重算位置。
 *
 * _render() 使用 in-place 更新策略：修改已有 DOM 元素的 style 属性，
 * 仅对新增/删除的 bar 做 createElement/remove。避免 innerHTML='' 清空
 * 触发的浏览器重排将页面滚动复位。
 */

const ROLE_BORDER_COLORS = {
  admin: '#e74c3c',
  uploader: '#3498db',
  user: '#27ae60'
}

const LAYER_CLASS = 'anno-border-layer'

export class BorderLayer {
  constructor(container) {
    this._container = container
    this._layer = null
    this._bars = new Map()
    this._resizeObserver = null
    this._rafId = null

    this._createLayer()

    this._resizeObserver = new ResizeObserver(() => this._scheduleRedraw())
    this._resizeObserver.observe(container)

    this._onScroll = () => this._scheduleRedraw()
    this._onResize = () => this._scheduleRedraw()
    container.addEventListener('scroll', this._onScroll, { passive: true })
    window.addEventListener('resize', this._onResize, { passive: true })
  }

  _createLayer() {
    if (!this._container) return

    const pos = window.getComputedStyle(this._container).position
    if (pos === 'static') {
      this._container.style.position = 'relative'
    }

    this._container.querySelector(`.${LAYER_CLASS}`)?.remove()

    const layer = document.createElement('div')
    layer.className = LAYER_CLASS
    layer.style.cssText = `
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      pointer-events: none;
      z-index: 1;
      overflow: hidden;
    `
    this._container.appendChild(layer)
    this._layer = layer
  }

  /** 批量设置所有边框条 */
  setBars(items) {
    this._bars.clear()
    for (const item of items) {
      this._bars.set(String(item.id), {
        top: item.top,
        height: Math.max(item.height, 4),
        role: item.role || 'user'
      })
    }
    this._render()
  }

  /** 追加一条 */
  addBar(id, top, height, role) {
    this._bars.set(String(id), {
      top,
      height: Math.max(height, 4),
      role: role || 'user'
    })
    this._render()
  }

  /** 移除一条 */
  removeBar(id) {
    this._bars.delete(String(id))
    this._render()
  }

  _scheduleRedraw() {
    if (this._rafId) return
    this._rafId = requestAnimationFrame(() => {
      this._rafId = null
      this._render()
    })
  }

  /**
   * In-place 更新：复用已有 DOM 元素，仅修改 style 属性。
   * 不执行 innerHTML='' 清空操作，避免触发浏览器重排复位滚动位置。
   */
  _render() {
    if (!this._layer || !this._container) return

    // 收集当前 DOM 中已有的 bar 元素，按 annotation-id 索引
    const existing = new Map()
    for (const child of this._layer.children) {
      const id = child.dataset.annotationId
      if (id) existing.set(id, child)
    }

    // 更新/创建 bars
    for (const [id, { top, height, role }] of this._bars) {
      let bar = existing.get(id)
      if (!bar) {
        bar = document.createElement('div')
        bar.className = 'anno-border-bar'
        bar.dataset.annotationId = id
        this._layer.appendChild(bar)
      }
      // 仅更新 style，不替换元素
      bar.style.cssText = `
        position: absolute;
        left: 0;
        top: ${top}px;
        width: 3px;
        height: ${height}px;
        border-radius: 2px;
        background: ${ROLE_BORDER_COLORS[role] || ROLE_BORDER_COLORS.user};
        opacity: 0.85;
      `
      existing.delete(id)
    }

    // 移除多余的 bars（annotation 已被删除的）
    for (const [, bar] of existing) {
      bar.remove()
    }
  }

  setVisible(visible) {
    if (this._layer) {
      this._layer.style.display = visible ? '' : 'none'
    }
    if (visible) {
      this._scheduleRedraw()
    }
  }

  redraw() {
    this._scheduleRedraw()
  }

  destroy() {
    if (this._rafId) {
      cancelAnimationFrame(this._rafId)
      this._rafId = null
    }
    if (this._resizeObserver) {
      this._resizeObserver.disconnect()
      this._resizeObserver = null
    }
    window.removeEventListener('resize', this._onResize)
    this._container?.removeEventListener('scroll', this._onScroll)
    this._layer?.remove()
    this._layer = null
    this._bars.clear()
    this._container = null
  }
}
