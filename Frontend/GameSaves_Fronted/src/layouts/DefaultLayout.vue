<template>
  <div class="default-layout">
    <AppNavbar />
    <main class="page-content">
      <div :class="isWide ? 'container-wide' : 'container'">
        <router-view />
      </div>
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppNavbar from '../components/common/AppNavbar.vue'
import AppFooter from '../components/common/AppFooter.vue'

const route = useRoute()
const isWide = computed(() => route.meta?.wide === true)
</script>

<style scoped>
.default-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}
</style>
