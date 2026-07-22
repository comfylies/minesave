<template><main ref="stream" class="message-stream" @scroll="onScroll"><button v-if="messages.length" class="older" @click="$emit('older')">加载更早消息</button><MessageBubble v-for="message in messages" :key="message.id" :message="message" :mine="message.senderId === currentUserId" /></main></template>
<script setup>
import { ref } from 'vue'
import MessageBubble from './MessageBubble.vue'
defineProps({ messages: { type: Array, default: () => [] }, currentUserId: Number })
const emit = defineEmits(['older']); const stream = ref(null)
function onScroll(){ if(stream.value?.scrollTop === 0) emit('older') }
</script>
<style scoped>.message-stream{min-height:380px;max-height:58vh;overflow:auto;padding:16px;background:var(--color-bg-secondary)}.older{display:block;margin:0 auto 12px;border:0;background:none;color:var(--color-link);cursor:pointer}</style>
