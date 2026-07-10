<template>
  <div class="home-page">
    <!-- ====== Hero 全屏搜索区 ====== -->
    <section class="hero" :style="heroStyle">
      <div v-if="backgroundImage" class="hero-overlay"></div>
      <div class="hero-body">
        <h1 class="hero-title">MineSave</h1>

        <!-- 搜索框 -->
        <div class="hero-search">
          <el-icon class="hero-search-icon"><Search /></el-icon>
          <input
            v-model="searchQuery"
            class="hero-search-input"
            placeholder="搜索游戏、存档..."
            @keyup.enter="doSearch"
            autocomplete="off"
          />
        </div>

        <!-- 向下滚动提示 -->
        <div class="hero-scroll-hint" @click="scrollToContent">
          <el-icon :size="28"><ArrowDown /></el-icon>
        </div>
      </div>
    </section>

    <!-- ====== 内容区 ====== -->
    <section ref="contentSection" class="home-content container">
      <!-- 热门游戏 -->
      <div class="section-block">
        <div class="section-header">
          <h2 class="section-title">🔥 热门游戏</h2>
          <router-link to="/browse?sort=hot" class="view-more">
            查看更多 <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>
        <LoadingSkeleton v-if="loading" :rows="1" />
        <div v-else-if="hotGames.length > 0" class="game-row">
          <GameCard v-for="game in hotGames" :key="game.id" :game="game" />
        </div>
        <EmptyState v-else description="暂无热门游戏" />
      </div>

      <!-- 最新上传 -->
      <div class="section-block">
        <div class="section-header">
          <h2 class="section-title">🕐 最新上传</h2>
          <router-link to="/browse?sort=newest" class="view-more">
            查看更多 <el-icon><ArrowRight /></el-icon>
          </router-link>
        </div>
        <LoadingSkeleton v-if="loading" :rows="1" />
        <div v-else-if="newestGames.length > 0" class="game-row">
          <GameCard v-for="game in newestGames" :key="game.id" :game="game" />
        </div>
        <EmptyState v-else description="暂无最新游戏" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, provide } from 'vue'
import { useRouter } from 'vue-router'
import { gameApi } from '../api/gameApi'
import { siteSettingsApi } from '../api/siteSettingsApi'
import GameCard from '../components/game/GameCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import LoadingSkeleton from '../components/common/LoadingSkeleton.vue'

const router = useRouter()

const searchQuery = ref('')
const hotGames = ref([])
const newestGames = ref([])
const loading = ref(false)
const backgroundImage = ref('')
const contentSection = ref(null)

/** 是否有背景图（provide 给 AppNavbar 控制文字颜色） */
const hasHeroBg = computed(() => !!backgroundImage.value)
provide('hasHeroBg', hasHeroBg)

/** Hero 区域行内样式：有背景图时用 url，无背景图时纯色 */
const heroStyle = computed(() => {
  if (backgroundImage.value) {
    return {
      backgroundImage: `url(${backgroundImage.value})`,
      backgroundSize: 'cover',
      backgroundPosition: 'center',
      backgroundRepeat: 'no-repeat'
    }
  }
  return {}
})

function doSearch() {
  const q = searchQuery.value.trim()
  if (q) {
    router.push({ path: '/search', query: { q } })
  }
}

function scrollToContent() {
  contentSection.value?.scrollIntoView({ behavior: 'smooth' })
}

onMounted(async () => {
  loading.value = true
  try {
    const [hotRes, newestRes, settingsRes] = await Promise.all([
      gameApi.getTop('hot', 4),
      gameApi.getTop('newest', 4),
      siteSettingsApi.get().catch(() => ({ background_image_url: '' }))
    ])
    hotGames.value = hotRes
    newestGames.value = newestRes
    backgroundImage.value = settingsRes?.background_image_url || ''
  } catch {
    // 静默降级：首页游戏区块显示空状态
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
/* ---- Hero 全屏区 ---- */
.hero {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  background-color: var(--color-bg-secondary);
  overflow: hidden;
}

/* 有背景图时叠加半透明遮罩以提升文字可读性 */
.hero-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.38);
  z-index: 1;
}

.hero-body {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 18vh var(--spacing-xl) var(--spacing-xl);
}

.hero-title {
  font-size: 52px;
  font-weight: 800;
  margin: 0 0 var(--spacing-sm) 0;
  color: var(--color-header-logo);
  transition: color 0.3s ease;
  user-select: none;
  -webkit-user-select: none;
}

/* 有背景图时文字变白 */
.hero-overlay ~ .hero-body .hero-title {
  color: #fff;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
}

/* ---- 搜索框 ---- */
.hero-search {
  position: relative;
  width: 640px;
  max-width: 90vw;
  margin-top: var(--spacing-sm);
}

.hero-search-icon {
  position: absolute;
  left: 20px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 22px;
  color: var(--color-secondary-text);
  z-index: 1;
}

.hero-search-input {
  width: 100%;
  height: 56px;
  padding: 0 20px 0 54px;
  font-size: 18px;
  color: var(--color-body-text);
  background: var(--color-bg-canvas);
  border: 1px solid var(--color-border-primary);
  border-radius: 28px;
  outline: none;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
  box-shadow: var(--shadow-md);
  box-sizing: border-box;
}

.hero-search-input:focus {
  border-color: var(--color-link);
  box-shadow: 0 1px 6px rgba(9, 105, 218, 0.15), var(--shadow-md);
}

.hero-search-input::placeholder {
  color: var(--color-secondary-text);
}

/* ---- 向下滚动提示 ---- */
.hero-scroll-hint {
  position: absolute;
  bottom: 32px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2;
  cursor: pointer;
  color: var(--color-secondary-text);
  opacity: 0.6;
  transition: opacity var(--transition-fast), transform var(--transition-fast);
  animation: bounce 2s infinite;
}

.hero-overlay ~ .hero-scroll-hint,
.hero-overlay + .hero-body .hero-scroll-hint {
  color: #fff;
}

.hero-scroll-hint:hover {
  opacity: 1;
  transform: translateX(-50%) translateY(4px);
}

@keyframes bounce {
  0%, 100% { transform: translateX(-50%) translateY(0); }
  50% { transform: translateX(-50%) translateY(6px); }
}

/* ---- 内容区 ---- */
.home-content {
  padding: var(--spacing-xxl) 0;
}

.section-block {
  margin-bottom: var(--spacing-xxl);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-lg);
}

.section-title {
  font-size: var(--font-size-xlarge);
  font-weight: 600;
  color: var(--color-body-text);
  margin: 0;
}

.view-more {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-normal);
  font-weight: 500;
  color: var(--color-link);
  text-decoration: none;
  transition: color var(--transition-fast);
}

.view-more:hover {
  color: var(--color-link-hover);
  text-decoration: none;
}

/* ---- 游戏卡片行（4 列 → 响应式） ---- */
.game-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-md);
}

@media (max-width: 1024px) {
  .game-row {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .hero-title {
    font-size: 36px;
  }
  .hero-search-input {
    height: 48px;
    font-size: 16px;
  }
  .game-row {
    grid-template-columns: 1fr;
  }
}
</style>
