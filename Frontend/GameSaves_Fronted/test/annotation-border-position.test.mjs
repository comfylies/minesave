import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readmeRendererPath = new URL('../src/components/article/ReadmeRenderer.vue', import.meta.url)

test('批注色条以 README 容器的内边缘为原点，不重复扣除内边距', async () => {
  const source = await readFile(readmeRendererPath, 'utf8')

  assert.doesNotMatch(source, /function getPaddingOffset\(/)
  const topCalculations = source.match(/Math\.round\(first\.top - containerRect\.top\)/g) || []
  assert.equal(topCalculations.length, 2)
})
