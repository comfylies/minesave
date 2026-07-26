# README 批注点击高亮 — 问题解决文档

## 原始问题

点击文章页右侧批注卡片后，左侧被批注的文字**没有任何视觉变化**，用户无法判断当前选中了哪条批注。

## 根因分析

经过代码审查和运行时诊断，发现了 **4 层问题**：

### 1. recogito v4 API 不兼容 → 流程中断

`useTextAnnotator.js` 的 `scrollToComment` 调用了 `annotator.value.state.store.selectAnnotation(id)`，但 recogito v4 已删除此方法。调用抛出 `TypeError`，导致 `scrollTo` 函数在 `scrollToComment` 处中断，后续的 `selectActiveAnnotation`（负责渲染高亮覆盖层）**从未执行**。

只有 `scrollIntoView`（页面滚动）成功执行，所以用户看到页面滚动了但无高亮。

### 2. recogito v4 事件名变更 → 事件回调失效

- `selectAnnotation` 事件 → v4 改为 `clickAnnotation`
- `cancelSelected` 事件 → v4 中不存在，deselect 通过 `selectionChanged` 事件通知

原有 `anno.on('selectAnnotation', ...)` 和 `anno.on('cancelSelected', ...)` 在 v4 中均为死代码。

### 3. CSS Custom Highlight API 不可用 → 需要降级方案

Codex 浏览器中 `CSS.highlights === false`，无法使用 `::highlight()` CSS 渲染。原 `HighlightManager` 在不支持的浏览器中不存储批注的 Range 数据，导致降级覆盖层无法获取文本位置。

### 4. 覆盖层未渲染 → 需要完整降级路径

即使前 3 个问题修复，也需要一套完整的 DOM 覆盖层渲染机制——在批注文字上叠加彩色半透明色块和虚线框。

---

## 解决方案

### 架构：双路径渲染

| 路径 | 机制 | 条件 |
|------|------|------|
| CSS Custom Highlight API | `::highlight(anno-active-*)` 更高不透明度背景 | `CSS.highlights` 可用 |
| DOM 覆盖层降级 | `.active-annotation-overlay` 内绝对定位 div | 所有浏览器 |

两条路径通过 `HighlightManager` 统一管理：批注 Range 始终存储在 `#entries` Map 中，无论浏览器是否支持 CSS Highlight API。

### 修改文件一览

| 文件 | 改动 |
|------|------|
| `HighlightManager.js` | `#entries` 始终存储 Range（不再依赖 `#supported`）；新增 `setActiveAnnotation`/`clearActiveAnnotation`/`getActiveAnnotationId`/`getAnnotation`/`getEntryIds`；active highlight 的 CSS 只保留高不透明度背景，去掉波浪下划线 |
| `useTextAnnotator.js` | `scrollToComment` 修复为 v4 API（`getAnnotation` + `setSelected`）；try-catch 包裹 `scrollIntoView` 和 `setSelected`；事件名从 `selectAnnotation` → `clickAnnotation`、`cancelSelected` → `selectionChanged` |
| `ReadmeRenderer.vue` | 新增 DOM 覆盖层（`.active-annotation-overlay` + `renderActiveOverlay`）；`selectActiveAnnotation` 包装函数；`pendingActiveCommentId` 异步重试机制；document 级 click 监听取消选中（带 300ms 防竞态窗口）；`refreshAnnotations` 时保持/恢复 active 状态；resize 时重渲染覆盖层 |
| `annotation-active-highlight.test.mjs` | 6 个 Node 源码契约测试 |

### 关键实现细节

**DOM 覆盖层渲染**（`renderActiveOverlay`）：
```
1. 从 HighlightManager.#entries 获取批注的 Range 和角色
2. Range.getClientRects() → 获取文字在页面上的像素矩形
3. 每个矩形创建一个绝对定位的 div（position: absolute）
4. 样式：role 色半透明背景(0.42) + 2px dashed outline + 1px outline-offset
5. 覆盖层 z-index: 1, pointer-events: none（不拦截交互）
```

**点击取消选中**（`onDocumentClick`）：
```
1. 记录上次程序化选中的时间戳
2. 300ms 内的 document click 直接跳过（防止 scrollTo 竞态）
3. 非程序化点击 → requestAnimationFrame + setTimeout(100ms) → 检查 getSelected()
4. 选中为空 → 清除 active 状态 + 覆盖层
```

**异步重试**（`pendingActiveCommentId`）：
```
右侧卡片可能比 README 批注 Range 更早加载
→ 首次 setActiveAnnotation 失败时暂存 id
→ renderAllHighlights 完成后调用 activatePendingComment() 重试
→ refreshAnnotations 后恢复之前的 active 状态
```

---

## 验证结果

- **6/6 契约测试通过**：选中/取消/重试/刷新/替换 全部覆盖
- **vite build 成功**：无新增编译错误
- **浏览器验证**：点击批注卡片 → 文字显示角色色虚线框 + 半透明背景；点击空白处 → 虚线框消失
- **零后端开销**：所有改动均为纯前端 DOM 操作和事件监听
