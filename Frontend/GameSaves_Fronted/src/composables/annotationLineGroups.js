export const ANNOTATION_LINE_TOLERANCE_PX = 4

/**
 * 将落在同一条浏览器视觉行上的批注归并为一个展示组。
 * `top` 是相对于 README 容器的首行 Y 坐标。
 */
export function groupAnnotationLines(candidates, tolerance = ANNOTATION_LINE_TOLERANCE_PX) {
  const sorted = [...candidates]
    .filter(({ commentId, top }) => commentId != null && Number.isFinite(top))
    .sort((left, right) => left.top - right.top || left.commentId - right.commentId)

  const groups = []
  for (const candidate of sorted) {
    const group = groups.at(-1)
    if (!group || candidate.top - group.top > tolerance) {
      groups.push({
        id: `line-${candidate.commentId}`,
        top: candidate.top,
        commentIds: [candidate.commentId]
      })
    } else {
      group.commentIds.push(candidate.commentId)
    }
  }

  return groups
}
