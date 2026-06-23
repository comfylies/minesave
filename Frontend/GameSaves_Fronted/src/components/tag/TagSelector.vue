<template>
  <el-select
    :model-value="modelValue"
    @update:model-value="handleChange"
    multiple
    filterable
    allow-create
    default-first-option
    :loading="loading"
    placeholder="搜索或创建标签..."
    class="tag-selector"
    :disabled="disabled"
  >
    <!-- 预设标签分组 -->
    <el-option-group v-if="presetTags.length > 0" label="预设标签">
      <el-option
        v-for="tag in presetTags"
        :key="tag.id"
        :label="tag.name"
        :value="tag.id"
      />
    </el-option-group>

    <!-- 用户标签分组 -->
    <el-option-group v-if="userTags.length > 0" label="用户标签">
      <el-option
        v-for="tag in userTags"
        :key="tag.id"
        :label="tag.name"
        :value="tag.id"
      />
    </el-option-group>
  </el-select>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { tagApi } from '../../api/tagApi'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const loading = ref(false)
const allTags = ref([])

const presetTags = computed(() => allTags.value.filter(t => t.source === 'admin'))
const userTags = computed(() => allTags.value.filter(t => t.source === 'user'))

async function fetchTags() {
  loading.value = true
  try {
    allTags.value = await tagApi.getAll()
  } finally {
    loading.value = false
  }
}

async function handleChange(values) {
  // Element Plus allow-create: values may contain string (new tag name)
  // instead of numeric ID. Create the tag then replace with its ID.
  const resolvedIds = []
  for (const val of values) {
    if (typeof val === 'number') {
      resolvedIds.push(val)
    } else if (typeof val === 'string') {
      // New tag - create it
      try {
        const tag = await tagApi.create(val.trim())
        // Add to local list immediately
        allTags.value.push(tag)
        resolvedIds.push(tag.id)
      } catch {
        // Creation failed, skip
      }
    }
  }
  emit('update:modelValue', resolvedIds)
}

onMounted(fetchTags)
</script>

<style scoped>
.tag-selector {
  width: 100%;
}
</style>
