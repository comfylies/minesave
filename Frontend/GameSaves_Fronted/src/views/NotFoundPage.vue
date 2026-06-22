<template>
  <div class="not-found-page">
    <div class="not-found-card">
      <h1 class="not-found-code">404</h1>
      <p class="not-found-text">页面未找到</p>
      <p class="not-found-hint">你要找的页面可能已被移动、删除或从未存在。</p>
      <el-button type="primary" size="large" @click="$router.push('/')">
        返回首页
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found-page {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xxl);
}

.not-found-card {
  text-align: center;
  max-width: 500px;
}

.not-found-code {
  font-size: 96px;
  font-weight: 700;
  color: var(--color-border-primary);
  line-height: 1;
  margin-bottom: var(--spacing-md);
}

.not-found-text {
  font-size: var(--font-size-title);
  font-weight: 600;
  color: var(--color-body-text);
  margin-bottom: var(--spacing-sm);
}

.not-found-hint {
  font-size: var(--font-size-large);
  color: var(--color-secondary-text);
  margin-bottom: var(--spacing-lg);
}
</style>
