<template>
  <div class="file-browser">
    <!-- 面包屑导航 -->
    <BreadcrumbNav
      :items="breadcrumbs"
      @navigate="(path) => $emit('navigate', path)"
    />

    <!-- 文件表格 -->
    <div class="file-table-wrap">
      <!-- 表头 -->
      <div class="file-table-header">
        <span class="col-name">名称</span>
        <span class="col-size">大小</span>
      </div>

      <!-- 加载状态 -->
      <LoadingSkeleton v-if="loading" :rows="8" />

      <!-- 内容 -->
      <template v-else>
        <!-- 目录行 -->
        <div
          v-for="dir in sortedDirectories"
          :key="dir.virtualPath"
          class="file-row file-row--dir"
          @click="$emit('navigate', dir.virtualPath)"
        >
          <span class="col-name">
            <FileIcon :is-directory="true" />
            <span class="dir-name">{{ dir.name }}</span>
          </span>
          <span class="col-size">-</span>
        </div>

        <!-- 文件行 -->
        <div
          v-for="file in sortedFiles"
          :key="file.virtualPath"
          class="file-row file-row--file"
          :class="securityRowClass(file.securityLevel)"
          :title="securityTooltip(file)"
          @click="$emit('preview', file)"
        >
          <span class="col-name">
            <FileIcon :type="file.fileType" />
            <span class="file-name">{{ file.name }}</span>
            <span v-if="file.securityLevel === 'danger'" class="security-badge security-badge--danger">⚠ 可疑</span>
            <span v-else-if="file.securityLevel === 'warning'" class="security-badge security-badge--warning">注意</span>
          </span>
          <span class="col-size">{{ formatSize(file.fileSize) }}</span>
        </div>

        <!-- 空目录 -->
        <div
          v-if="sortedDirectories.length === 0 && sortedFiles.length === 0"
          class="file-row file-row--empty"
        >
          <span class="col-name">此目录为空</span>
        </div>
      </template>
    </div>

    <!-- 安全颜色图例 -->
    <div v-if="hasSecurityMarkedFiles" class="security-legend">
      <span class="security-legend__item security-legend__item--danger">
        <span class="security-legend__bar"></span>
        可执行文件（谨慎打开）
      </span>
      <span class="security-legend__item security-legend__item--warning">
        <span class="security-legend__bar"></span>
        脚本/库文件（注意来源）
      </span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import FileIcon from './FileIcon.vue'
import BreadcrumbNav from './BreadcrumbNav.vue'
import LoadingSkeleton from '../common/LoadingSkeleton.vue'

const props = defineProps({
  directories: { type: Array, default: () => [] },
  files: { type: Array, default: () => [] },
  breadcrumbs: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

defineEmits(['navigate', 'preview'])

// 目录按名称排序
const sortedDirectories = computed(() =>
  [...props.directories].sort((a, b) => a.name.localeCompare(b.name))
)

// 文件按名称排序
const sortedFiles = computed(() =>
  [...props.files].sort((a, b) => a.name.localeCompare(b.name))
)

// 是否有安全标记的文件（控制图例显示）
const hasSecurityMarkedFiles = computed(() =>
  props.files.some(f => f.securityLevel === 'danger' || f.securityLevel === 'warning')
)

function formatSize(bytes) {
  if (bytes == null || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return size.toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

function securityRowClass(level) {
  if (level === 'danger') return 'file-row--danger'
  if (level === 'warning') return 'file-row--warning'
  return ''
}

function securityTooltip(file) {
  if (file.isDirectory) return ''
  if (file.securityLevel === 'danger') {
    return '⚠ 高危文件 — 此类型文件可被双击执行，请确认来源可信后再下载打开'
  }
  if (file.securityLevel === 'warning') {
    return '⚠ 可疑文件 — 此文件可能包含可执行代码，请注意来源'
  }
  return ''
}
</script>

<style scoped>
.file-browser {
  border: 1px solid var(--color-border-primary);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.file-table-wrap {
  /* 容器 */
}

/* ---- 表头 ---- */
.file-table-header {
  display: flex;
  align-items: center;
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-bg-secondary);
  border-bottom: 1px solid var(--color-border-primary);
  font-size: var(--font-size-small);
  font-weight: 600;
  color: var(--color-secondary-text);
}

/* ---- 行 ---- */
.file-row {
  display: flex;
  align-items: center;
  padding: var(--spacing-sm) var(--spacing-md);
  border-bottom: 1px solid #f0f2f4;
  cursor: pointer;
  transition: background var(--transition-fast);
  min-height: 40px;
}

.file-row:last-child {
  border-bottom: none;
}

.file-row:hover {
  background: var(--color-bg-secondary);
}

.file-row--empty {
  padding: var(--spacing-xl) var(--spacing-md);
  justify-content: center;
  color: var(--color-secondary-text);
  cursor: default;
  font-size: var(--font-size-normal);
}

/* ---- 列 ---- */
.col-name {
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  overflow: hidden;
}

.col-size {
  width: 100px;
  text-align: right;
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  flex-shrink: 0;
}

/* ---- 目录名 ---- */
.dir-name {
  font-weight: 600;
  color: var(--color-link);
}

.dir-name:hover {
  text-decoration: underline;
}

/* ---- 文件名 ---- */
.file-name {
  color: var(--color-body-text);
}

/* ---- 安全颜色标记 ---- */

/* 危险文件行 */
.file-row--danger {
  border-left: 3px solid #e5534b;
  background: #fff5f5;
}

.file-row--danger:hover {
  background: #ffe8e8;
}

/* 警告文件行 */
.file-row--warning {
  border-left: 3px solid #d4a72c;
  background: #fffdf5;
}

.file-row--warning:hover {
  background: #fff9e0;
}

/* 安全标记徽章 */
.security-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.5;
  flex-shrink: 0;
  margin-left: 6px;
}

.security-badge--danger {
  color: #c9352b;
  background: #ffe8e8;
  border: 1px solid #f5c6c6;
}

.security-badge--warning {
  color: #9a6e1c;
  background: #fff9e0;
  border: 1px solid #f0d88a;
}

/* 图例 */
.security-legend {
  display: flex;
  gap: 16px;
  padding: var(--spacing-sm) var(--spacing-md);
  border-top: 1px solid #f0f2f4;
  background: var(--color-bg-secondary);
  font-size: 12px;
  color: var(--color-secondary-text);
}

.security-legend__item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.security-legend__bar {
  display: inline-block;
  width: 12px;
  height: 3px;
  border-radius: 1px;
  flex-shrink: 0;
}

.security-legend__item--danger .security-legend__bar {
  background: #e5534b;
}

.security-legend__item--warning .security-legend__bar {
  background: #d4a72c;
}
</style>
