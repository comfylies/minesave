import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import { articleApi } from '../api/articleApi'

export const useArticleStore = defineStore('articles', () => {
  const currentArticle = ref(null)
  const articleList = ref([])
  const pagination = reactive({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0
  })
  const loading = ref(false)
  const error = ref(null)

  async function fetchArticle(id) {
    loading.value = true
    error.value = null
    try {
      currentArticle.value = await articleApi.getById(id)
    } catch (e) {
      error.value = e.message || '加载存档详情失败'
      currentArticle.value = null
    } finally {
      loading.value = false
    }
  }

  async function fetchByGame(gameId, page = 0) {
    loading.value = true
    error.value = null
    try {
      const result = await articleApi.getByGame(gameId, page, pagination.size)
      articleList.value = result.content || []
      pagination.page = result.page
      pagination.totalElements = result.totalElements
      pagination.totalPages = result.totalPages
    } catch (e) {
      error.value = e.message || '加载存档列表失败'
      articleList.value = []
    } finally {
      loading.value = false
    }
  }

  async function fetchByUser(userId, page = 0) {
    loading.value = true
    error.value = null
    try {
      const result = await articleApi.getByUser(userId, page, pagination.size)
      articleList.value = result.content || []
      pagination.page = result.page
      pagination.totalElements = result.totalElements
      pagination.totalPages = result.totalPages
    } catch (e) {
      error.value = e.message || '加载存档列表失败'
      articleList.value = []
    } finally {
      loading.value = false
    }
  }

  async function deleteArticle(id) {
    await articleApi.delete(id)
  }

  return {
    currentArticle, articleList, pagination, loading, error,
    fetchArticle, fetchByGame, fetchByUser, deleteArticle
  }
})
