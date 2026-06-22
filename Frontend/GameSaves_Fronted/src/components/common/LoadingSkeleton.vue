<template>
  <div class="loading-skeleton">
    <el-skeleton :rows="rows" :animated="animated" :loading="loading">
      <template #default>
        <slot />
      </template>
    </el-skeleton>
  </div>
</template>

<script setup>
defineProps({
  rows: { type: Number, default: 5 },
  animated: { type: Boolean, default: true },
  loading: { type: Boolean, default: true }
})
</script>

<style scoped>
.loading-skeleton {
  padding: var(--spacing-md) 0;
}
</style>
