import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const frontend = new URL('../', import.meta.url)
const source = (path) => readFile(new URL(path, frontend), 'utf8')

test('tag selector uses focused API endpoints instead of loading every tag', async () => {
  const selector = await source('src/components/tag/TagSelector.vue')
  const api = await source('src/api/tagApi.js')

  assert.match(api, /featured:\s*\(\)\s*=>\s*client\.get\('\/tags\/featured'\)/)
  assert.match(api, /byIds:\s*\(ids\)\s*=>\s*client\.get\('\/tags\/by-ids'/)
  assert.match(selector, /remote-method="searchTags"/)
  assert.doesNotMatch(selector, /tagApi\.getAll\(\)/)
})

test('tag selector debounces remote queries and caps selection at five', async () => {
  const selector = await source('src/components/tag/TagSelector.vue')

  assert.match(selector, /const MAX_TAGS = 5/)
  assert.match(selector, /setTimeout\(async \(\) => \{/)
  assert.match(selector, /\}, 300\)/)
  assert.match(selector, /:multiple-limit="MAX_TAGS"/)
  assert.match(selector, /创建全站标签/)
})
