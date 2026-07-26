<template>
  <el-select
    :model-value="modelValue"
    @update:model-value="handleChange"
    multiple filterable remote :remote-method="searchTags"
    :multiple-limit="MAX_TAGS" :loading="loading" :disabled="disabled"
    placeholder="搜索或新建标签…" class="tag-selector"
  >
    <el-option-group v-if="featuredTags.length && !query" label="精选标签">
      <el-option v-for="tag in featuredTags" :key="tag.id" :label="tag.name" :value="tag.id" />
    </el-option-group>
    <el-option-group v-if="remoteTags.length" label="搜索结果">
      <el-option v-for="tag in remoteTags" :key="tag.id" :label="tag.name" :value="tag.id" />
    </el-option-group>
    <el-option v-if="canCreate" :label="`＋ 创建全站标签“${query}”`" :value="createValue" />
  </el-select>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { tagApi } from '../../api/tagApi'

const props = defineProps({ modelValue: { type: Array, default: () => [] }, disabled: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue'])
const MAX_TAGS = 5
const loading = ref(false)
const featuredTags = ref([])
const remoteTags = ref([])
const knownTags = ref([])
const query = ref('')
let debounceTimer
let requestVersion = 0

const selectedIds = computed(() => props.modelValue.filter(value => typeof value === 'number'))
const createValue = computed(() => `__create__:${query.value}`)
const canCreate = computed(() => query.value.length >= 2 && query.value.length <= 50 && selectedIds.value.length < MAX_TAGS && !knownTags.value.some(tag => tag.name.trim().toLowerCase() === query.value.toLowerCase()))

function mergeKnown(tags) {
  const byId = new Map(knownTags.value.map(tag => [tag.id, tag]))
  tags.forEach(tag => byId.set(tag.id, tag))
  knownTags.value = [...byId.values()]
}

async function fetchFeaturedTags() {
  loading.value = true
  try { featuredTags.value = await tagApi.featured(); mergeKnown(featuredTags.value) } finally { loading.value = false }
}

function searchTags(value) {
  query.value = value.trim()
  clearTimeout(debounceTimer)
  const version = ++requestVersion
  if (query.value.length < 2) { remoteTags.value = []; return }
  debounceTimer = setTimeout(async () => {
    loading.value = true
    try {
      const results = await tagApi.search(query.value)
      if (version === requestVersion) { remoteTags.value = results; mergeKnown(results) }
    } catch { if (version === requestVersion) ElMessage.warning('标签搜索暂时不可用') }
    finally { if (version === requestVersion) loading.value = false }
  }, 300)
}

async function loadSelectedTags(ids) {
  const missing = ids.filter(id => !knownTags.value.some(tag => tag.id === id)).slice(0, MAX_TAGS)
  if (missing.length) { try { mergeKnown(await tagApi.byIds(missing)) } catch { /* retry on next value change */ } }
}

async function handleChange(values) {
  const resolvedIds = values.filter(value => typeof value === 'number')
  if (values.includes(createValue.value) && canCreate.value) {
    try {
      const tag = await tagApi.create(query.value)
      mergeKnown([tag]); remoteTags.value = [tag, ...remoteTags.value.filter(item => item.id !== tag.id)]
      resolvedIds.push(tag.id)
    } catch { ElMessage.error('创建标签失败，请稍后重试') }
  }
  emit('update:modelValue', resolvedIds.slice(0, MAX_TAGS))
}

watch(() => props.modelValue, loadSelectedTags, { immediate: true })
onMounted(fetchFeaturedTags)
onBeforeUnmount(() => clearTimeout(debounceTimer))
</script>

<style scoped>.tag-selector { width: 100%; }</style>
