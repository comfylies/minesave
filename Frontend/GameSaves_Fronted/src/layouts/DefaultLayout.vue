<template>
  <div class="default-layout">
    <AppNavbar />
    <main class="page-content">
      <div :class="isWide ? 'container-wide' : 'container'">
        <router-view />
      </div>
    </main>
    <AppFooter />
    <Transition name="back-to-top-fade">
      <button
        v-if="showBackToTop"
        class="back-to-top"
        aria-label="回到顶部"
        @click="scrollToTop"
      >
        <el-icon><ArrowUp /></el-icon>
      </button>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import AppNavbar from '../components/common/AppNavbar.vue'
import AppFooter from '../components/common/AppFooter.vue'

const route = useRoute()
const isWide = computed(() => route.meta?.wide === true)

// 回到顶部按钮
const showBackToTop = ref(false)
let scrollHandler = null

onMounted(() => {
  scrollHandler = () => {
    showBackToTop.value = window.scrollY > 500
  }
  window.addEventListener('scroll', scrollHandler, { passive: true })
})

onBeforeUnmount(() => {
  if (scrollHandler) {
    window.removeEventListener('scroll', scrollHandler)
  }
})

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<style scoped>
.default-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  padding-top: var(--header-height);
}

.back-to-top {
  position: fixed;
  bottom: 32px;
  right: 32px;
  z-index: 101;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1px solid var(--color-border-primary);
  background: var(--color-bg-canvas);
  color: var(--color-body-text);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-md);
  transition: background var(--transition-fast), box-shadow var(--transition-fast);
  outline: none;
}

.back-to-top:hover {
  background: var(--color-bg-secondary);
  box-shadow: var(--shadow-lg);
}

.back-to-top:active {
  transform: scale(0.95);
}
</style>

<style>
/* Back-to-top transition — non-scoped for Vue <Transition> classes */
.back-to-top-fade-enter-active,
.back-to-top-fade-leave-active {
  transition: opacity 0.25s ease;
}

.back-to-top-fade-enter-from,
.back-to-top-fade-leave-to {
  opacity: 0;
}
</style>
