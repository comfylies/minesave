import { onMounted, onUnmounted } from 'vue'
import { Client } from '@stomp/stompjs'
import { useMessageStore } from '../stores/messages'
import { messageApi } from '../api/messageApi'

const RECONNECT_DELAY_MS = 3000
const BACKGROUND_WS_GRACE_MS = 60000

export function useMessageRealtime() {
  const store = useMessageStore()
  const clientId = sessionStorage.getItem('message-client-id') || crypto.randomUUID()
  sessionStorage.setItem('message-client-id', clientId)

  let client = null
  let pollingController = null
  let reconnectTimer = null
  let backgroundTimer = null
  let stopped = false
  let socketGeneration = 0

  function clearReconnectTimer() {
    if (reconnectTimer) clearTimeout(reconnectTimer)
    reconnectTimer = null
  }

  function stopPolling() {
    pollingController?.abort()
    pollingController = null
  }

  function stopWebSocket() {
    socketGeneration += 1
    const activeClient = client
    client = null
    activeClient?.deactivate()
  }

  function startPolling() {
    if (stopped || pollingController || !localStorage.getItem('satoken')) return
    store.restoreEventState()
    store.connectionState = 'polling'
    const controller = new AbortController()
    pollingController = controller

    const loop = async () => {
      while (!stopped && pollingController === controller && !controller.signal.aborted) {
        try {
          const event = await messageApi.waitEvent({
            clientId,
            epoch: store.eventEpoch ?? undefined,
            cursor: store.eventCursor ?? undefined
          }, controller.signal)
          if (event) await store.handleEvent(event)
        } catch (error) {
          if (error.name !== 'CanceledError' && !controller.signal.aborted) {
            await new Promise(resolve => setTimeout(resolve, 1000))
          }
        }
      }
      if (pollingController === controller) pollingController = null
    }
    loop()
  }

  function scheduleReconnect() {
    if (stopped || document.hidden || reconnectTimer || !localStorage.getItem('satoken')) return
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connect()
    }, RECONNECT_DELAY_MS)
  }

  function handleSocketFailure(activeClient, generation) {
    if (stopped || client !== activeClient || generation !== socketGeneration) return
    stopWebSocket()
    startPolling()
    scheduleReconnect()
  }

  function parseEvent(message) {
    try {
      return JSON.parse(message.body)
    } catch {
      return null
    }
  }

  function connect() {
    if (stopped || document.hidden || !localStorage.getItem('satoken') || client) {
      if (document.hidden) startPolling()
      return
    }

    clearReconnectTimer()
    const generation = ++socketGeneration
    const activeClient = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/messages`,
      connectHeaders: { Authorization: localStorage.getItem('satoken') || '' },
      reconnectDelay: 0,
      onConnect: () => {
        if (stopped || client !== activeClient || generation !== socketGeneration) return
        stopPolling()
        store.connectionState = 'connected'
        activeClient.subscribe('/user/queue/messages', message => {
          store.handleEvent(parseEvent(message)).catch(() => {})
        })
        store.handleEvent().catch(() => {})
      },
      onWebSocketClose: () => handleSocketFailure(activeClient, generation),
      onStompError: () => handleSocketFailure(activeClient, generation)
    })

    client = activeClient
    store.connectionState = 'connecting'
    activeClient.activate()
  }

  function onVisibility() {
    if (document.hidden) {
      clearReconnectTimer()
      backgroundTimer = setTimeout(() => {
        if (document.hidden) {
          stopWebSocket()
          startPolling()
        }
      }, BACKGROUND_WS_GRACE_MS)
    } else {
      if (backgroundTimer) clearTimeout(backgroundTimer)
      backgroundTimer = null
      stopPolling()
      connect()
    }
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', onVisibility)
    connect()
  })

  onUnmounted(() => {
    stopped = true
    document.removeEventListener('visibilitychange', onVisibility)
    clearReconnectTimer()
    if (backgroundTimer) clearTimeout(backgroundTimer)
    stopPolling()
    stopWebSocket()
  })
}
