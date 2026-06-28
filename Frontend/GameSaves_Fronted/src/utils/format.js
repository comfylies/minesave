/**
 * 共享格式化工具函数
 *
 * 来源：从 MySavesPage / GamePage / UserProfilePage / AdminGhosts / AdminArticles
 * / ArticleCard / FileBrowser / ArticleSidebar / AnnotationPanel 抽取去重。
 */

/**
 * 文件大小 → 人类可读字符串（B / KB / MB / GB）
 */
export function formatSize(bytes) {
  if (bytes == null || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

/**
 * 日期 → 相对时间（刚刚 / N 分钟前 / N 小时前 / N 天前 / 绝对日期）
 */
export function formatTime(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now - date
  const mins = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  if (hours < 24) return `${hours} 小时前`
  if (days < 30) return `${days} 天前`
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

/**
 * 日期 → 绝对日期时间（用于后台管理页面，如公告列表）
 */
export function formatDateTime(time) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

/**
 * 文本截断 + 省略号
 */
export function truncate(text, max) {
  if (!text) return ''
  return text.length > max ? text.slice(0, max) + '...' : text
}

/**
 * article.status → Element Plus tag type
 */
export function statusType(status) {
  const map = { READY: 'success', UPLOADING: 'info', EXTRACTING: 'warning', FAILED: 'danger' }
  return map[status] || 'info'
}

/**
 * article.status → 中文展示文本
 */
export function statusText(status) {
  const map = { READY: '就绪', UPLOADING: '上传中', EXTRACTING: '解压中', FAILED: '失败' }
  return map[status] || status
}
