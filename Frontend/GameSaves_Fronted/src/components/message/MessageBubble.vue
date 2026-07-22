<template>
  <div class="bubble-row" :class="{ mine: mine }"><div class="bubble">
    <img v-if="imageUrl" class="chat-image" :src="imageUrl" alt="聊天图片" @click="show = true" />
    <p v-if="message.content">{{ message.content }}</p><small>{{ formattedTime }}</small>
  </div><el-image-viewer v-if="show" :url-list="[originalUrl]" @close="show = false" /></div>
</template>
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { messageApi } from '../../api/messageApi'
const props = defineProps({ message: { type: Object, required: true }, mine: Boolean })
const show = ref(false)
const imageUrl = ref('')
const originalUrl = ref('')
const formattedTime = computed(() => {
  const date = new Date(props.message.createdAt)
  if (Number.isNaN(date.getTime())) return ''
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  return `${date.getMonth() + 1}月${date.getDate()}日 ${hours}:${minutes}`
})
onMounted(async () => {
  if (!props.message.imageOriginalKey) return
  const [thumb, original] = await Promise.all([messageApi.getImage(props.message.id, true), messageApi.getImage(props.message.id, false)])
  imageUrl.value = URL.createObjectURL(thumb)
  originalUrl.value = URL.createObjectURL(original)
})
onBeforeUnmount(() => { if (imageUrl.value) URL.revokeObjectURL(imageUrl.value); if (originalUrl.value) URL.revokeObjectURL(originalUrl.value) })
</script>
<style scoped>
.bubble-row{display:flex;margin:8px 0}.bubble-row.mine{justify-content:flex-end}.bubble{max-width:min(75%,560px);padding:8px 10px;border:1px solid var(--color-border-primary);border-radius:var(--radius-md);background:var(--color-bg-primary)}.mine .bubble{background:#dafbe1}.bubble p{margin:0;white-space:pre-wrap;overflow-wrap:anywhere}.bubble small{display:block;margin-top:4px;color:var(--color-secondary-text);font-size:11px}.chat-image{display:block;max-width:100%;max-height:300px;border-radius:6px;cursor:zoom-in}
</style>
