<template><main ref="stream" class="message-stream"><button v-if="hasOlder" type="button" class="older" :disabled="loadingOlder" @click="$emit('older')">{{ loadingOlder ? '加载中…' : '加载更早消息' }}</button><MessageBubble v-for="message in messages" :key="message.id" :message="message" :mine="message.senderId === currentUserId" /></main></template>
<script setup>
import { ref } from 'vue'
import MessageBubble from './MessageBubble.vue'
defineProps({ messages: { type: Array, default: () => [] }, currentUserId: Number, hasOlder: Boolean, loadingOlder: Boolean })
const stream = ref(null)
function scrollToTop(){ stream.value?.scrollTo({ top: 0 }) }
defineExpose({ scrollToTop })
</script>
<style scoped>.message-stream{flex:1;min-height:0;overflow:auto;padding:16px;background:var(--color-bg-secondary)}.older{display:block;margin:0 auto 12px;border:0;background:none;color:var(--color-link);cursor:pointer}.older:disabled{cursor:wait;opacity:.65}</style>
