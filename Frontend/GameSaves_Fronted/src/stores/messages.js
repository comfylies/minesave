import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { messageApi } from '../api/messageApi'

export const useMessageStore = defineStore('messages', () => {
  const conversations = ref([])
  const activeConversationId = ref(null)
  const messagesByConversation = ref({})
  const unreadCount = ref(0)
  const loading = ref(false)
  const loadingOlder = ref(false)
  const hasOlderMessages = ref(false)
  const historyPurged = ref(false)
  const connectionState = ref('disconnected')
  const eventCursor = ref(null)
  const eventEpoch = ref(null)
  let eventStateStorageKey = null

  const activeConversation = computed(() => conversations.value.find(item => item.id === activeConversationId.value) || null)
  const activeMessages = computed(() => messagesByConversation.value[activeConversationId.value] || [])

  function currentEventStateStorageKey() {
    try {
      const user = JSON.parse(localStorage.getItem('currentUser') || 'null')
      return user?.id ? `message-event-state:${user.id}` : null
    } catch {
      return null
    }
  }

  function restoreEventState() {
    const storageKey = currentEventStateStorageKey()
    if (!storageKey || storageKey === eventStateStorageKey) return

    eventStateStorageKey = storageKey
    eventCursor.value = null
    eventEpoch.value = null
    try {
      const saved = JSON.parse(sessionStorage.getItem(storageKey) || 'null')
      if (Number.isFinite(saved?.cursor) && typeof saved?.epoch === 'string') {
        eventCursor.value = saved.cursor
        eventEpoch.value = saved.epoch
      }
    } catch {
      sessionStorage.removeItem(storageKey)
    }
  }

  function advanceEventState(event) {
    restoreEventState()
    if (!Number.isFinite(event?.cursor) || typeof event?.epoch !== 'string') return

    if (eventEpoch.value !== event.epoch) {
      eventEpoch.value = event.epoch
      eventCursor.value = event.cursor
    } else if (event.cursor > (eventCursor.value || 0)) {
      eventCursor.value = event.cursor
    } else {
      return
    }
    if (eventStateStorageKey) {
      sessionStorage.setItem(eventStateStorageKey, JSON.stringify({ epoch: eventEpoch.value, cursor: eventCursor.value }))
    }
  }

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
    const incoming = page.content || []
    mergeMessages(activeConversationId.value, incoming)
    hasOlderMessages.value = (page.totalElements || 0) > incoming.length
    historyPurged.value = Boolean(page.historyPurged)
  }

  async function loadOlder() {
    const items = activeMessages.value
    if (!activeConversationId.value || !items.length || loadingOlder.value || !hasOlderMessages.value) return 0

    loadingOlder.value = true
    try {
      const page = await messageApi.getItems(activeConversationId.value, { beforeId: items[0].id, size: 40 })
      const incoming = page.content || []
      mergeMessages(activeConversationId.value, incoming, true)
      hasOlderMessages.value = (page.totalElements || 0) > incoming.length
      historyPurged.value = Boolean(page.historyPurged)
      return incoming.length
    } finally {
      loadingOlder.value = false
    }
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

  async function handleEvent(event) {
    await loadConversations()
    await refreshActive()
    await markActiveRead()
    // A failed REST refresh leaves this event unacknowledged for the next long-poll request.
    advanceEventState(event)
  }

  async function refreshUnread() {
    unreadCount.value = await messageApi.getUnreadCount()
  }

  function reset() {
    conversations.value = []
    activeConversationId.value = null
    messagesByConversation.value = {}
    unreadCount.value = 0
    loadingOlder.value = false
    hasOlderMessages.value = false
    historyPurged.value = false
    connectionState.value = 'disconnected'
    eventCursor.value = null
    eventEpoch.value = null
    if (eventStateStorageKey) sessionStorage.removeItem(eventStateStorageKey)
    eventStateStorageKey = null
  }

  return { conversations, activeConversationId, messagesByConversation, unreadCount, loading, loadingOlder, hasOlderMessages, historyPurged, connectionState, eventCursor, eventEpoch,
    activeConversation, activeMessages, loadConversations, selectConversation, refreshActive, loadOlder,
    markActiveRead, send, handleEvent, refreshUnread, restoreEventState, reset }
})
