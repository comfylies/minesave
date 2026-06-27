import { defineStore } from 'pinia'
import { ref } from 'vue'
import { gameApi } from '../api/gameApi'

export const useGameStore = defineStore('games', () => {
  const games = ref([])
  const currentGame = ref(null)
  const loading = ref(false)
  const error = ref(null)

  async function fetchGames() {
    loading.value = true
    error.value = null
    try {
      games.value = await gameApi.getAll()
    } catch (e) {
      error.value = e.message || '加载游戏列表失败'
      games.value = []
    } finally {
      loading.value = false
    }
  }

  async function fetchGame(id) {
    loading.value = true
    error.value = null
    try {
      currentGame.value = await gameApi.getById(id)
    } catch (e) {
      error.value = e.message || '加载游戏详情失败'
      currentGame.value = null
    } finally {
      loading.value = false
    }
  }

  function reset() {
    games.value = []
    currentGame.value = null
    loading.value = false
    error.value = null
  }

  return { games, currentGame, loading, error, fetchGames, fetchGame, reset }
})
