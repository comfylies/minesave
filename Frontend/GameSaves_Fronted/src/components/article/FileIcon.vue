<template>
  <span class="file-icon" :class="iconClass">
    {{ icon }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: { type: String, default: '' },
  isDirectory: { type: Boolean, default: false }
})

const icon = computed(() => {
  if (props.isDirectory) return '📁'
  return getFileIcon(props.type)
})

const iconClass = computed(() => ({
  'file-icon--dir': props.isDirectory,
  'file-icon--file': !props.isDirectory
}))

function getFileIcon(fileType) {
  if (!fileType) return '📄'

  const t = fileType.toLowerCase()
  if (['.json', '.jsonc'].includes(t)) return '📋'
  if (['.txt', '.md', '.conf', '.log', '.lock'].includes(t)) return '📝'
  if (['.dat', '.nbt'].includes(t)) return '🗄️'
  if (['.png', '.jpg', '.jpeg', '.gif', '.bmp', '.svg'].includes(t)) return '🖼️'
  if (['.mca', '.mcr'].includes(t)) return '🗺️'
  if (['.ojng'].includes(t)) return '📦'
  if (['.zip', '.rar', '.7z', '.gz'].includes(t)) return '📦'
  if (['.jar'].includes(t)) return '☕'
  if (['.js', '.ts', '.java', '.py', '.cpp', '.h'].includes(t)) return '💻'
  if (['.yaml', '.yml', '.toml', '.cfg', '.properties'].includes(t)) return '⚙️'
  if (['.xml', '.html', '.css'].includes(t)) return '🌐'
  return '📄'
}
</script>

<style scoped>
.file-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  font-size: 16px;
  flex-shrink: 0;
}

.file-icon--dir {
  color: #54aeff;
}

.file-icon--file {
  color: var(--color-secondary-text);
}
</style>
