<template>
  <div class="bubble-row" :class="{ mine: mine }"><div class="bubble">
    <div v-if="message.imageExpired" class="image-expired">&lt;图片已过期&gt;</div>
    <img v-else-if="imageUrl" class="chat-image" :src="imageUrl" alt="聊天图片" @click="openImage" @error="refreshThumbnail" />
    <p v-if="message.content">{{ message.content }}</p><small>{{ formattedTime }}</small>
  </div><el-image-viewer v-if="show && originalUrl" :url-list="[originalUrl]" @close="closeImage" /></div>
</template>
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { messageApi } from '../../api/messageApi'
const props = defineProps({ message: { type: Object, required: true }, mine: Boolean })
const show = ref(false)
const imageUrl = ref(props.message.imageThumbnailUrl || '')
const originalUrl = ref('')
const thumbnailRefreshed = ref(false)
const loadingOriginal = ref(false)
const formattedTime = computed(() => {
  const date = new Date(props.message.createdAt)
  if (Number.isNaN(date.getTime())) return ''
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  return `${date.getMonth() + 1}月${date.getDate()}日 ${hours}:${minutes}`
})
onMounted(loadLocalThumbnail)
onBeforeUnmount(() => {
  revokeObjectUrl(imageUrl.value)
  revokeObjectUrl(originalUrl.value)
})
async function loadLocalThumbnail() {
  if (props.message.imageExpired || imageUrl.value || !props.message.imageThumbnailKey) return
  try {
    imageUrl.value = URL.createObjectURL(await messageApi.getImage(props.message.id, true))
  } catch {
    // The shared API client has already surfaced a real image loading failure.
  }
}
async function refreshThumbnail() {
  if (props.message.imageExpired || thumbnailRefreshed.value || !props.message.imageThumbnailKey) return
  thumbnailRefreshed.value = true
  try {
    revokeObjectUrl(imageUrl.value)
    imageUrl.value = props.message.imageThumbnailUrl
      ? await messageApi.getImageUrl(props.message.id, true)
      : URL.createObjectURL(await messageApi.getImage(props.message.id, true))
  } catch {
    // The shared API client has already surfaced a real refresh failure.
  }
}
async function openImage() {
  if (props.message.imageExpired || loadingOriginal.value) return
  loadingOriginal.value = true
  try {
    revokeObjectUrl(originalUrl.value)
    originalUrl.value = props.message.imageThumbnailUrl
      ? await messageApi.getImageUrl(props.message.id, false)
      : URL.createObjectURL(await messageApi.getImage(props.message.id, false))
    show.value = true
  } catch {
    // Keep the viewer closed when its freshly authorized URL cannot be issued.
  } finally {
    loadingOriginal.value = false
  }
}
function closeImage() {
  show.value = false
  revokeObjectUrl(originalUrl.value)
  originalUrl.value = ''
}
function revokeObjectUrl(url) {
  if (url?.startsWith('blob:')) URL.revokeObjectURL(url)
}
</script>
<style scoped>
.bubble-row{display:flex;margin:8px 0}.bubble-row.mine{justify-content:flex-end}.bubble{max-width:min(75%,560px);padding:8px 10px;border:1px solid var(--color-border-primary);border-radius:var(--radius-md);background:var(--color-bg-primary)}.mine .bubble{background:#dafbe1}.bubble p{margin:0;white-space:pre-wrap;overflow-wrap:anywhere}.bubble small{display:block;margin-top:4px;color:var(--color-secondary-text);font-size:11px}.chat-image{display:block;max-width:100%;max-height:300px;border-radius:6px;cursor:zoom-in}.image-expired{color:var(--color-secondary-text);font-size:13px}
</style>
