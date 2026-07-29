import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import { followApi } from '../api/followApi'

export const useFollowStore = defineStore('follows', () => {
  const users = ref([])
  const loading = ref(false)
  const error = ref(null)
  const pagination = reactive({ page: 0, size: 20, totalElements: 0, totalPages: 0 })

  async function fetchFollowing(page = 0) {
    loading.value = true
    error.value = null
    try {
      const result = await followApi.list(page, pagination.size)
      users.value = result.content || []
      pagination.page = result.page
      pagination.totalElements = result.totalElements
      pagination.totalPages = result.totalPages
    } catch (e) {
      users.value = []
      error.value = e.message || '加载关注列表失败'
    } finally {
      loading.value = false
    }
  }

  async function unfollow(userId) {
    const result = await followApi.toggle(userId)
    if (!result.following) {
      users.value = users.value.filter(user => user.id !== userId)
      pagination.totalElements = Math.max(0, pagination.totalElements - 1)
    }
    return result
  }

  function reset() {
    users.value = []
    loading.value = false
    error.value = null
    pagination.page = 0
    pagination.size = 20
    pagination.totalElements = 0
    pagination.totalPages = 0
  }

  return { users, loading, error, pagination, fetchFollowing, unfollow, reset }
})
