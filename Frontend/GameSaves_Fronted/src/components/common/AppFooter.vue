<template>
  <footer class="app-footer">
    <div class="container footer-inner">
      <div class="footer-left">
        <span class="footer-logo">💾 GameSaves</span>
        <span class="footer-text">© 2026 GameSaves</span>
      </div>
      <div class="footer-right">
        <span class="footer-text">ICP备2026000001号-X</span>
        <span class="footer-text">公安备案 11010000000001号</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  border-top: 1px solid var(--color-border-primary);
  padding: var(--spacing-lg) 0;
  margin-top: auto;
  background: var(--color-bg-tertiary);
}

.footer-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.footer-logo {
  font-weight: 600;
  color: var(--color-body-text);
}

.footer-text {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
}

.footer-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
}
</style>
