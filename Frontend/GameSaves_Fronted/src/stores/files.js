import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fileApi } from '../api/fileApi'

export const useFileStore = defineStore('files', () => {
  const currentPath = ref('')
  const files = ref([])
  const directories = ref([])
  const breadcrumbs = ref([])
  const loading = ref(false)
  const error = ref(null)

  async function browse(articleId, path = '') {
    loading.value = true
    error.value = null
    try {
      const result = await fileApi.browse(articleId, path)
      currentPath.value = result.currentPath
      files.value = result.files || []
      directories.value = result.directories || []
      breadcrumbs.value = result.breadcrumbs || []
    } catch (e) {
      error.value = e.message || '加载文件列表失败'
      files.value = []
      directories.value = []
      breadcrumbs.value = []
    } finally {
      loading.value = false
    }
  }

  function reset() {
    currentPath.value = ''
    files.value = []
    directories.value = []
    breadcrumbs.value = []
    error.value = null
  }

  return { currentPath, files, directories, breadcrumbs, loading, error, browse, reset }
})
