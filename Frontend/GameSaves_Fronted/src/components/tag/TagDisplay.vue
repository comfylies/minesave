<template>
  <span v-if="tags && tags.length > 0" class="tag-display">
    <el-tag
      v-for="(tag, index) in visibleTags"
      :key="index"
      size="small"
      :type="tagType"
      class="tag-chip"
    >
      {{ typeof tag === 'string' ? tag : tag.name }}
    </el-tag>
    <el-tag v-if="hiddenCount > 0" size="small" :type="tagType" class="tag-chip tag-more">
      +{{ hiddenCount }}
    </el-tag>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  tags: { type: Array, default: () => [] },
  max: { type: Number, default: 2 },
  type: { type: String, default: 'info' }
})

const tagType = 'info'

const visibleTags = computed(() => {
  if (!props.tags) return []
  return props.tags.slice(0, props.max)
})

const hiddenCount = computed(() => {
  if (!props.tags) return 0
  return Math.max(0, props.tags.length - props.max)
})
</script>

<style scoped>
.tag-display {
  display: inline-flex;
  flex-wrap: nowrap;
  gap: 4px;
  align-items: center;
  vertical-align: middle;
  max-width: 100%;
}

.tag-chip {
  border-radius: var(--border-radius-sm, 4px);
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 80px;
}

.tag-more {
  opacity: 0.7;
  cursor: default;
}
</style>
