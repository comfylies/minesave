<template><div class="messages-page"><ConversationList :conversations="store.conversations" :active-id="store.activeConversationId" @select="store.selectConversation"/><section class="thread"><header class="thread-header">{{ peerName || store.activeConversation?.peerNickname || store.activeConversation?.peerUsername || '消息' }}</header><MessageStream v-if="store.activeConversationId" ref="messageStream" :messages="store.activeMessages" :current-user-id="auth.userId" :has-older="store.hasOlderMessages" :history-purged="store.historyPurged" :loading-older="store.loadingOlder" @older="loadOlder"/><EmptyState v-else description="选择一个会话，或从用户主页发起私信"/><MessageComposer v-if="targetUserId" @send="send"/></section></div></template>
<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessageStore } from '../stores/messages'
import { useAuthStore } from '../stores/auth'
import { userApi } from '../api/userApi'
import ConversationList from '../components/message/ConversationList.vue'; import MessageStream from '../components/message/MessageStream.vue'; import MessageComposer from '../components/message/MessageComposer.vue'; import EmptyState from '../components/common/EmptyState.vue'
const route=useRoute(),router=useRouter(),store=useMessageStore(),auth=useAuthStore(); const peer=ref(null)
const messageStream = ref(null)
const targetUserId=computed(()=>store.activeConversation?.peerId || Number(route.query.peer)||null); const peerName=computed(()=>peer.value?.nickname||peer.value?.username||'')
async function openPeer(){const id=Number(route.query.peer);if(!id||id===auth.userId)return;peer.value=await userApi.getById(id);const existing=store.conversations.find(item=>item.peerId===id);if(existing)await store.selectConversation(existing.id)}
async function send({text,image}){const form=new FormData();if(text)form.append('content',text);if(image)form.append('image',image);const message=await store.send(targetUserId.value,form);await store.selectConversation(message.conversationId);router.replace({query:{}})}
async function loadOlder(){const count=await store.loadOlder();if(count>0){await nextTick();messageStream.value?.scrollToTop()}}
onMounted(async()=>{await store.loadConversations();await openPeer()});watch(()=>route.query.peer,openPeer)
</script>
<style scoped>
.messages-page{height:100%;min-height:0;display:grid;grid-template-columns:320px minmax(0,1fr);border:1px solid var(--color-border-primary);overflow:hidden;background:var(--color-bg-primary)}
.thread{min-width:0;min-height:0;display:flex;flex-direction:column}
.thread-header{padding:14px 16px;border-bottom:1px solid var(--color-border-primary);font-weight:600;flex:0 0 auto}
.thread :deep(.empty-state){flex:1;min-height:0;display:grid;place-content:center}
@media(max-width:720px){.messages-page{grid-template-columns:1fr;grid-template-rows:minmax(0,42%) minmax(0,58%)}.conversation-list{border-right:0!important;border-bottom:1px solid var(--color-border-primary)}}
</style>
