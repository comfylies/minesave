<template>
  <footer class="app-footer">
    <div class="container footer-inner">
      <div class="footer-left">
        <span class="footer-logo">💾 MineSave</span>
        <span class="footer-text">© 2026 MineSave</span>
        <router-link to="/contact" class="footer-link">联系我们</router-link>
      </div>
      <div class="footer-right">
        <a
          class="beian-link"
          href="https://beian.miit.gov.cn/"
          target="_blank"
          rel="noopener noreferrer"
        >苏ICP备2026043687号</a>
        <a
          class="beian-link"
          href="https://beian.mps.gov.cn/#/query/webSearch?code=32068202000848"
          target="_blank"
          rel="noopener noreferrer"
        >
          <img class="beian-icon" src="/beian-icon.png" alt="" />
          苏公网安备32068202000848号
        </a>
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

.beian-link {
  font-size: var(--font-size-small);
  color: var(--color-tertiary-text, #999);
  text-decoration: none;
  transition: color 0.2s;
}

.beian-link:hover {
  color: var(--color-secondary-text);
  text-decoration: underline;
}

.beian-icon {
  width: 14px;
  height: 14px;
  margin-right: 2px;
  vertical-align: text-bottom;
}

.footer-link {
  font-size: var(--font-size-small);
  color: var(--color-tertiary-text, #999);
  text-decoration: none;
  transition: color 0.2s;
}

.footer-link:hover {
  color: var(--color-link);
  text-decoration: underline;
}
</style>
