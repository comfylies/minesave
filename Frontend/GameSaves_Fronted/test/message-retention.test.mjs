import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const root = new URL('../', import.meta.url)
const source = path => readFile(new URL(path, root), 'utf8')

test('expired image bubbles show the literal placeholder without loading image data', async () => {
  const bubble = await source('src/components/message/MessageBubble.vue')
  assert.match(bubble, /v-if="message\.imageExpired"/)
  assert.match(bubble, /&lt;图片已过期&gt;/)
  assert.match(bubble, /props\.message\.imageExpired \|\| imageUrl\.value/)
})

test('oldest retained history shows a cleanup divider', async () => {
  const stream = await source('src/components/message/MessageStream.vue')
  assert.match(stream, /historyPurged && !hasOlder/)
  assert.match(stream, /超过 30 天的消息已清理/)
})

test('conversation list labels a removed final message as cleaned', async () => {
  const list = await source('src/components/message/ConversationList.vue')
  assert.match(list, /消息已清理/)
})
