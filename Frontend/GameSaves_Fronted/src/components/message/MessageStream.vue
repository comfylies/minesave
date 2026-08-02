<template><main ref="stream" class="message-stream"><button v-if="hasOlder" type="button" class="older" :disabled="loadingOlder" @click="$emit('older')">{{ loadingOlder ? '加载中…' : '加载更早消息' }}</button><div v-if="historyPurged && !hasOlder" class="history-purged"><span></span><em>超过 30 天的消息已清理</em><span></span></div><MessageBubble v-for="message in messages" :key="message.id" :message="message" :mine="message.senderId === currentUserId" /></main></template>
<script setup>
import { ref } from 'vue'
import MessageBubble from './MessageBubble.vue'
defineProps({ messages: { type: Array, default: () => [] }, currentUserId: Number, hasOlder: Boolean, loadingOlder: Boolean, historyPurged: Boolean })
const stream = ref(null)
function scrollToTop(){ stream.value?.scrollTo({ top: 0 }) }
defineExpose({ scrollToTop })
</script>
<style scoped>.message-stream{flex:1;min-height:0;overflow:auto;padding:16px;background:var(--color-bg-secondary)}.older{display:block;margin:0 auto 12px;border:0;background:none;color:var(--color-link);cursor:pointer}.older:disabled{cursor:wait;opacity:.65}.history-purged{display:flex;align-items:center;gap:8px;margin:10px 0 16px;color:var(--color-secondary-text);font-size:12px}.history-purged span{height:1px;flex:1;background:var(--color-border-primary)}.history-purged em{font-style:normal;white-space:nowrap}</style>
