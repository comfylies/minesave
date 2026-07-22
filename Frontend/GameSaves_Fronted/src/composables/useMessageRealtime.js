import { onMounted, onUnmounted } from 'vue'
import { Client } from '@stomp/stompjs'
import { useMessageStore } from '../stores/messages'
import { messageApi } from '../api/messageApi'

export function useMessageRealtime() {
  const store = useMessageStore()
  const clientId = sessionStorage.getItem('message-client-id') || crypto.randomUUID()
  sessionStorage.setItem('message-client-id', clientId)
  let client
  let abortController
  let stopped = false
  let failures = 0

  function startPolling() {
    if (abortController || stopped) return
    store.connectionState = 'polling'
    abortController = new AbortController()
    const loop = async () => {
      while (!stopped && abortController) {
        try {
          const event = await messageApi.waitEvent({ clientId }, abortController.signal)
          if (event) await store.handleEvent(event)
        } catch (error) {
          if (error.name !== 'CanceledError') await new Promise(resolve => setTimeout(resolve, 1000))
        }
      }
      abortController = null
    }
    loop()
  }

  function stopPolling() {
    abortController?.abort()
    abortController = null
  }

  function connect() {
    if (stopped || !localStorage.getItem('satoken')) return
    if (document.hidden) return startPolling()
    stopPolling()
    client?.deactivate()
    client = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/messages`,
      connectHeaders: { Authorization: localStorage.getItem('satoken') || '' },
      reconnectDelay: 3000,
      onConnect: () => {
        failures = 0
        store.connectionState = 'connected'
        client.subscribe('/user/queue/messages', async () => store.handleEvent())
        store.handleEvent()
      },
      onWebSocketClose: () => {
        failures += 1
        if (failures >= 2) startPolling()
      },
      onStompError: () => { failures += 1; if (failures >= 2) startPolling() }
    })
    client.activate()
  }

  function onVisibility() {
    if (document.hidden) {
      setTimeout(() => { if (document.hidden) { client?.deactivate(); startPolling() } }, 60000)
    } else {
      stopPolling()
      connect()
    }
  }

  onMounted(() => { document.addEventListener('visibilitychange', onVisibility); connect() })
  onUnmounted(() => { stopped = true; document.removeEventListener('visibilitychange', onVisibility); stopPolling(); client?.deactivate() })
}
