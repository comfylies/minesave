<template>
  <div class="following-page">
    <div class="page-header"><h1>我的关注</h1><p>你关注的发布者</p></div>
    <LoadingSkeleton v-if="followStore.loading" :rows="5" />
    <el-alert v-else-if="followStore.error" type="error" :title="followStore.error" show-icon />
    <template v-else-if="followStore.users.length">
      <div class="following-list"><FollowedUserCard v-for="user in followStore.users" :key="user.id" :user="user" @unfollow="handleUnfollow" /></div>
      <div class="pagination-wrap"><el-pagination v-model:current-page="currentPage" :page-size="followStore.pagination.size" :total="followStore.pagination.totalElements" layout="prev, pager, next" @current-change="page => followStore.fetchFollowing(page - 1)" /></div>
    </template>
    <EmptyState v-else description="你还没有关注发布者" />
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useFollowStore } from '../stores/follows'
import FollowedUserCard from '../components/user/FollowedUserCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import LoadingSkeleton from '../components/common/LoadingSkeleton.vue'

const followStore = useFollowStore()
const currentPage = computed({ get: () => followStore.pagination.page + 1, set: () => {} })
onMounted(() => followStore.fetchFollowing())
async function handleUnfollow(userId) { await followStore.unfollow(userId) }
</script>

<style scoped>
.following-page { padding:var(--spacing-lg) 0; }.page-header { margin-bottom:var(--spacing-lg); border-bottom:1px solid var(--color-border-primary); }.page-header h1 { margin:0; }.page-header p { color:var(--color-secondary-text); }.following-list { display:grid; gap:var(--spacing-md); }.pagination-wrap { display:flex; justify-content:center; padding:var(--spacing-lg) 0; }
</style>
