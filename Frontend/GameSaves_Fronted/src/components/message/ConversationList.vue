<template>
  <aside class="conversation-list">
    <button v-for="conversation in conversations" :key="conversation.id" class="conversation"
      :class="{ active: conversation.id === activeId }" @click="$emit('select', conversation.id)">
      <el-avatar :size="38" :src="conversation.peerAvatarUrl" icon="UserFilled" />
      <span class="conversation-text"><strong>{{ conversation.peerNickname || conversation.peerUsername }}</strong>
        <small>{{ conversation.lastMessage?.content || (conversation.lastMessage ? '[图片]' : '') }}</small></span>
      <el-badge :value="conversation.unreadCount" :hidden="!conversation.unreadCount" :max="99" />
    </button>
    <p v-if="!conversations.length" class="empty">暂无会话</p>
  </aside>
</template>
<script setup>
defineProps({ conversations: { type: Array, default: () => [] }, activeId: Number })
defineEmits(['select'])
</script>
<style scoped>
.conversation-list{border-right:1px solid var(--color-border-primary);min-height:480px}.conversation{width:100%;display:flex;gap:10px;align-items:center;padding:12px;border:0;border-bottom:1px solid var(--color-border-secondary);background:transparent;text-align:left;cursor:pointer}.conversation:hover,.conversation.active{background:var(--color-bg-secondary)}.conversation-text{min-width:0;flex:1;display:grid;gap:3px}.conversation-text strong,.conversation-text small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.conversation-text small,.empty{color:var(--color-secondary-text);font-size:12px}.empty{text-align:center;padding:24px}
</style>
