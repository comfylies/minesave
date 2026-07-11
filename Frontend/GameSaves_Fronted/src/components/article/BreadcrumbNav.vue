<template>
  <div class="breadcrumb-nav">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item v-for="(crumb, index) in items" :key="crumb.path || 'root'">
        <a
          v-if="index < items.length - 1"
          href="javascript:void(0)"
          class="breadcrumb-link"
          @click.prevent="$emit('navigate', crumb.path)"
        >
          {{ crumb.name }}
        </a>
        <span v-else class="breadcrumb-current" :title="crumb.name">{{ crumb.name }}</span>
      </el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup>
defineProps({
  items: {
    type: Array,
    default: () => []
    // 每项: { name: 'root', path: '' }
  }
})

defineEmits(['navigate'])
</script>

<style scoped>
.breadcrumb-nav {
  padding: var(--spacing-md);
  font-size: var(--font-size-normal);
}

.breadcrumb-link {
  color: var(--color-link);
  font-weight: 500;
  text-decoration: none;
  max-width: 160px;
  display: inline-block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
}

.breadcrumb-link:hover {
  text-decoration: underline;
}

.breadcrumb-current {
  color: var(--color-body-text);
  font-weight: 600;
  max-width: 220px;
  display: inline-block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
}
</style>
