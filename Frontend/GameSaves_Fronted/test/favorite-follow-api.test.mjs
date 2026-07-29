import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const frontend = new URL('../', import.meta.url)
const source = (path) => readFile(new URL(path, frontend), 'utf8')

test('favorite API uses article state and dedicated list endpoints', async () => {
  const api = await source('src/api/favoriteApi.js')

  assert.match(api, /client\.post\(`\/articles\/\$\{articleId\}\/favorite`\)/)
  assert.match(api, /client\.get\(`\/articles\/\$\{articleId\}\/my-favorite`\)/)
  assert.match(api, /client\.get\('\/favorites', \{ params: \{ page, size \} \}\)/)
})

test('follow API uses publisher state and dedicated list endpoints', async () => {
  const api = await source('src/api/followApi.js')

  assert.match(api, /client\.post\(`\/users\/\$\{userId\}\/follow`\)/)
  assert.match(api, /client\.get\(`\/users\/\$\{userId\}\/my-following`\)/)
  assert.match(api, /client\.get\('\/follows', \{ params: \{ page, size \} \}\)/)
})

test('navbar keeps the added following and favorites labels on one line', async () => {
  const navbar = await source('src/components/common/AppNavbar.vue')

  assert.match(navbar, /\.navbar-nav\s*\{[\s\S]*?flex-wrap:\s*nowrap/)
  assert.match(navbar, /\.nav-link\s*\{[\s\S]*?white-space:\s*nowrap/)
})
