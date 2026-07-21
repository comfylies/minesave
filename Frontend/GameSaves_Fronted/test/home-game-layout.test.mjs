import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const homePagePath = new URL('../src/views/HomePage.vue', import.meta.url)
const gameCardPath = new URL('../src/components/game/GameCard.vue', import.meta.url)

test('首页游戏区保持四个等宽卡片和固定间距', async () => {
  const source = await readFile(homePagePath, 'utf8')

  assert.match(
    source,
    /\.game-row\s*\{[\s\S]*?grid-template-columns:\s*repeat\(4,\s*minmax\(0,\s*1fr\)\);[\s\S]*?gap:\s*var\(--spacing-md\);/
  )
})

test('游戏卡片允许标题收缩并以省略号截断', async () => {
  const source = await readFile(gameCardPath, 'utf8')

  assert.match(source, /\.game-card\s*\{[\s\S]*?min-width:\s*0;/)
  assert.match(
    source,
    /\.game-name\s*\{[\s\S]*?white-space:\s*nowrap;[\s\S]*?overflow:\s*hidden;[\s\S]*?text-overflow:\s*ellipsis;/
  )
})
