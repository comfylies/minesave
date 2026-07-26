import assert from 'node:assert/strict'
import test from 'node:test'
import { groupAnnotationLines } from '../src/composables/annotationLineGroups.js'

test('groups annotations from the same rendered line and preserves document order', () => {
  const groups = groupAnnotationLines([
    { commentId: 31, top: 104 },
    { commentId: 32, top: 106 },
    { commentId: 33, top: 129 }
  ])

  assert.deepEqual(groups, [
    { id: 'line-31', top: 104, commentIds: [31, 32] },
    { id: 'line-33', top: 129, commentIds: [33] }
  ])
})

test('starts a new group outside the four-pixel line tolerance', () => {
  const groups = groupAnnotationLines([
    { commentId: 1, top: 10 },
    { commentId: 2, top: 15 }
  ])

  assert.equal(groups.length, 2)
  assert.deepEqual(groups.map(group => group.commentIds), [[1], [2]])
})
