<template>
  <div class="empty-state">
    <el-empty :description="description" :image-size="imageSize">
      <template v-if="$slots.default">
        <slot />
      </template>
    </el-empty>
  </div>
</template>

<script setup>
defineProps({
  description: { type: String, default: '暂无数据' },
  imageSize: { type: Number, default: 80 }
})
</script>

<style scoped>
.empty-state {
  padding: var(--spacing-xxl) 0;
  text-align: center;
}
</style>
