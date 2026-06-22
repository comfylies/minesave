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
          @click="$emit('preview', file)"
        >
          <span class="col-name">
            <FileIcon :type="file.fileType" />
            <span class="file-name">{{ file.name }}</span>
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
</style>
