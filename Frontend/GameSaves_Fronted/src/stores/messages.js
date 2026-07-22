import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { messageApi } from '../api/messageApi'

export const useMessageStore = defineStore('messages', () => {
  const conversations = ref([])
  const activeConversationId = ref(null)
  const messagesByConversation = ref({})
  const unreadCount = ref(0)
  const loading = ref(false)
  const connectionState = ref('disconnected')

  const activeConversation = computed(() => conversations.value.find(item => item.id === activeConversationId.value) || null)
  const activeMessages = computed(() => messagesByConversation.value[activeConversationId.value] || [])

  async function loadConversations() {
    const page = await messageApi.getConversations({ page: 0, size: 50 })
    conversations.value = page.content || []
    unreadCount.value = conversations.value.reduce((sum, item) => sum + (item.unreadCount || 0), 0)
    return conversations.value
  }

  function mergeMessages(conversationId, incoming, older = false) {
    const current = messagesByConversation.value[conversationId] || []
    const byId = new Map(current.map(item => [item.id, item]))
    incoming.forEach(item => byId.set(item.id, item))
    const merged = [...byId.values()].sort((a, b) => a.id - b.id)
    messagesByConversation.value = { ...messagesByConversation.value, [conversationId]: merged }
    return older ? merged.slice(0, incoming.length).at(-1)?.id : merged.at(-1)?.id
  }

  async function selectConversation(conversationId) {
    activeConversationId.value = Number(conversationId)
    await refreshActive()
    await markActiveRead()
  }

  async function refreshActive() {
    if (!activeConversationId.value) return
    const page = await messageApi.getItems(activeConversationId.value, { size: 40 })
    mergeMessages(activeConversationId.value, page.content || [])
  }

  async function loadOlder() {
    const items = activeMessages.value
    if (!activeConversationId.value || !items.length) return
    const page = await messageApi.getItems(activeConversationId.value, { beforeId: items[0].id, size: 40 })
    mergeMessages(activeConversationId.value, page.content || [], true)
    return page.content?.length || 0
  }

  async function markActiveRead() {
    const conversation = activeConversation.value
    if (!conversation || !conversation.unreadCount) return
    await messageApi.markRead(conversation.id)
    conversation.unreadCount = 0
    unreadCount.value = conversations.value.reduce((sum, item) => sum + (item.unreadCount || 0), 0)
  }

  async function send(targetUserId, formData) {
    const message = await messageApi.send(targetUserId, formData)
    await loadConversations()
    activeConversationId.value = message.conversationId
    mergeMessages(message.conversationId, [message])
    return message
  }

  async function handleEvent() {
    await loadConversations()
    await refreshActive()
    await markActiveRead()
  }

  async function refreshUnread() {
    unreadCount.value = await messageApi.getUnreadCount()
  }

  function reset() {
    conversations.value = []
    activeConversationId.value = null
    messagesByConversation.value = {}
    unreadCount.value = 0
    connectionState.value = 'disconnected'
  }

  return { conversations, activeConversationId, messagesByConversation, unreadCount, loading, connectionState,
    activeConversation, activeMessages, loadConversations, selectConversation, refreshActive, loadOlder,
    markActiveRead, send, handleEvent, refreshUnread, reset }
})
