import { ref, watch } from 'vue'

const VIEW_MODE_KEY = 'gamesaving-view-mode'

// Global singleton — shared across all pages
const viewMode = ref(localStorage.getItem(VIEW_MODE_KEY) || 'list')

watch(viewMode, (val) => {
  localStorage.setItem(VIEW_MODE_KEY, val)
})

export function useViewMode() {
  function setViewMode(mode) {
    viewMode.value = mode
  }

  return { viewMode, setViewMode }
}
