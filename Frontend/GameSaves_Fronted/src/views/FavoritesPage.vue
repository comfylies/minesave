<template>
  <div class="favorites-page">
    <div class="page-header"><h1>我的收藏</h1><el-radio-group v-model="viewMode" size="small"><el-radio-button value="list"><el-icon><List /></el-icon>列表</el-radio-button><el-radio-button value="gallery"><el-icon><Grid /></el-icon>画廊</el-radio-button></el-radio-group></div>
    <LoadingSkeleton v-if="articleStore.loading" :rows="8" />
    <el-alert v-else-if="articleStore.error" type="error" :title="articleStore.error" show-icon />
    <template v-else-if="articleStore.articleList.length">
      <table v-if="viewMode === 'list'" class="data-table"><thead><tr><th>标题</th><th>游戏</th><th>作者</th><th>下载</th><th>时间</th></tr></thead><tbody><tr v-for="article in articleStore.articleList" :key="article.id" class="article-row" @click="router.push(`/articles/${article.id}`)"><td>{{ article.title }}</td><td>{{ article.gameName }}</td><td>{{ article.nickname }}</td><td>{{ article.downloadCount || 0 }}</td><td>{{ formatTime(article.createdAt) }}</td></tr></tbody></table>
      <div v-else class="gallery-grid"><ArticleCard v-for="article in articleStore.articleList" :key="article.id" :article="article" /></div>
      <div class="pagination-wrap"><el-pagination v-model:current-page="currentPage" :page-size="articleStore.pagination.size" :total="articleStore.pagination.totalElements" layout="prev, pager, next" @current-change="page => articleStore.fetchFavorites(page - 1)" /></div>
    </template>
    <EmptyState v-else description="你还没有收藏存档" />
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useArticleStore } from '../stores/articles'
import { useViewMode } from '../composables/useViewMode'
import ArticleCard from '../components/article/ArticleCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import LoadingSkeleton from '../components/common/LoadingSkeleton.vue'
import { formatTime } from '../utils/format'

const router = useRouter()
const articleStore = useArticleStore()
const { viewMode } = useViewMode()
const currentPage = computed({ get: () => articleStore.pagination.page + 1, set: () => {} })
onMounted(() => articleStore.fetchFavorites())
</script>

<style scoped>
.favorites-page { padding:var(--spacing-lg) 0; }.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:var(--spacing-lg); border-bottom:1px solid var(--color-border-primary); padding-bottom:var(--spacing-md); }.page-header h1 { margin:0; }.article-row { cursor:pointer; }.article-row:hover { background:var(--color-bg-secondary); }.gallery-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(280px,1fr)); gap:var(--spacing-lg); }.pagination-wrap { display:flex; justify-content:center; padding:var(--spacing-lg) 0; }
</style>
