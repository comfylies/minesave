import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import packageJson from '../package.json' with { type: 'json' }

const frontend = new URL('../', import.meta.url)

async function source(path) {
  return readFile(new URL(path, frontend), 'utf8')
}

test('messages route opts into the dedicated chat layout', async () => {
  const router = await source('src/router/index.js')
  assert.match(router, /path:\s*'messages'[\s\S]*?chatLayout:\s*true/)
})

test('chat layout hides the footer and constrains the page viewport', async () => {
  const layout = await source('src/layouts/DefaultLayout.vue')
  assert.match(layout, /v-if="!chatLayout"/)
  assert.match(layout, /page-content--chat/)
  assert.match(layout, /chatLayout\s*=\s*computed/)
  assert.match(layout, /\.default-layout--chat\s*\{[\s\S]*?height:\s*100vh\s*;\s*height:\s*100dvh/)
  assert.match(layout, /\.default-layout--chat\s*\{[\s\S]*?overflow:\s*hidden/)
  assert.match(layout, /\.page-content--chat\s*\{[\s\S]*?overflow:\s*hidden/)
})

test('global layout reserves stable scrollbar space for the chat route', async () => {
  const styles = await source('src/assets/styles/global.css')
  assert.match(styles, /html\s*\{[\s\S]*?scrollbar-gutter:\s*stable\s*;/)
  assert.match(styles, /@supports\s+not\s*\(scrollbar-gutter:\s*stable\)\s*\{\s*html\s*\{\s*overflow-y:\s*scroll\s*;/)
})

test('message page keeps only its panels scrollable and anchors the composer', async () => {
  const page = await source('src/views/MessagesPage.vue')
  const stream = await source('src/components/message/MessageStream.vue')
  const conversations = await source('src/components/message/ConversationList.vue')
  assert.match(page, /height:\s*100%/)
  assert.match(page, /min-height:\s*0/)
  assert.match(page, /\.messages-page\s*\{[\s\S]*?overflow:\s*hidden/)
  assert.match(page, /\.thread\s*\{[\s\S]*?min-height:\s*0/)
  assert.match(stream, /flex:\s*1/)
  assert.match(stream, /min-height:\s*0/)
  assert.match(stream, /overflow:\s*auto/)
  assert.match(conversations, /overflow:\s*auto/)
})

test('composer auto-grows like chat input and remains vertically resizable', async () => {
  const composer = await source('src/components/message/MessageComposer.vue')
  assert.match(composer, /:autosize="\{\s*minRows:\s*3,\s*maxRows:\s*10\s*\}"/)
  assert.match(composer, /resize:\s*vertical/)
  assert.match(composer, /\.composer\s*\{[\s\S]*?flex:\s*0\s+0\s+auto/)
  assert.match(composer, /\.composer\s*\{[\s\S]*?max-height:\s*45vh\s*;\s*max-height:\s*45dvh/)
  assert.match(composer, /\.composer\s*\{[\s\S]*?overflow-y:\s*auto/)
  assert.match(composer, /\.el-textarea__inner\)\s*\{[\s\S]*?max-height:\s*calc\(45vh\s*-\s*76px\)\s*;\s*max-height:\s*calc\(45dvh\s*-\s*76px\)/)
})

test('message timestamps omit the year and seconds', async () => {
  const bubble = await source('src/components/message/MessageBubble.vue')
  assert.match(bubble, /getMonth\(\)\s*\+\s*1/)
  assert.match(bubble, /getDate\(\)/)
  assert.match(bubble, /padStart\(2,\s*'0'\)/)
  assert.doesNotMatch(bubble, /toLocaleString\(\)/)
  assert.doesNotMatch(bubble, /getFullYear\(\)/)
  assert.doesNotMatch(bubble, /getSeconds\(\)/)
})

test('navbar badge is an explicit flex-aligned navigation item', async () => {
  const navbar = await source('src/components/common/AppNavbar.vue')
  assert.match(navbar, /class="nav-message-item"/)
  assert.match(navbar, /\.nav-message-item\s*\{[\s\S]*?display:\s*flex/)
  assert.match(navbar, /\.nav-message-item\s*\{[\s\S]*?align-items:\s*center/)
})

test('cancelled axios requests are rejected quietly before toast handling', async () => {
  const client = await source('src/api/client.js')
  assert.match(client, /isRequestCancellation\(error,\s*axios\.isCancel\)/)
  assert.match(client, /createResponseErrorHandler\(/)
  assert.match(client, /showError:\s*ElMessage\.error/)
})

test('request cancellation helper handles Axios and native cancellation shapes', async () => {
  const { isRequestCancellation } = await import(new URL('../src/utils/requestCancellation.js', import.meta.url))
  assert.equal(isRequestCancellation({ code: 'ERR_CANCELED' }), true)
  assert.equal(isRequestCancellation({ name: 'CanceledError' }), true)
  assert.equal(isRequestCancellation(new Error('network failure')), false)
  assert.equal(isRequestCancellation({ marker: 'axios' }, error => error.marker === 'axios'), true)
})

test('response error handler rejects expected cancellations without showing a toast', async () => {
  const { createResponseErrorHandler } = await import(new URL('../src/utils/requestErrorHandler.js', import.meta.url))
  const { isRequestCancellation } = await import(new URL('../src/utils/requestCancellation.js', import.meta.url))
  let shown = 0
  const handler = createResponseErrorHandler({
    isCancellation: isRequestCancellation,
    onUnauthorized: () => assert.fail('cancellation must not trigger authentication cleanup'),
    showError: () => { shown += 1 }
  })

  const codeCancellation = { code: 'ERR_CANCELED' }
  await assert.rejects(handler(codeCancellation), error => error === codeCancellation)
  const namedCancellation = { name: 'CanceledError' }
  await assert.rejects(handler(namedCancellation), error => error === namedCancellation)
  assert.equal(shown, 0)
})

test('response error handler shows one toast for an ordinary error and retains 401 handling', async () => {
  const { createResponseErrorHandler } = await import(new URL('../src/utils/requestErrorHandler.js', import.meta.url))
  let shown = 0
  let unauthorized = 0
  const handler = createResponseErrorHandler({
    isCancellation: () => false,
    onUnauthorized: () => { unauthorized += 1 },
    showError: () => { shown += 1 }
  })

  const ordinary = new Error('network failure')
  await assert.rejects(handler(ordinary), error => error === ordinary)
  assert.equal(shown, 1)
  assert.equal(unauthorized, 0)

  const unauthorizedError = { response: { status: 401 } }
  await assert.rejects(handler(unauthorizedError), error => error === unauthorizedError)
  assert.equal(unauthorized, 1)
  assert.equal(shown, 1)
})

test('package test command runs the Node source-inspection suite', () => {
  assert.equal(packageJson.scripts.test, 'node --test test/*.test.mjs')
})
