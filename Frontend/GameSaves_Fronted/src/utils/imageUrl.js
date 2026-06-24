/**
 * Derive thumbnail URL from original cover image URL.
 * The number is the short edge (height for 16:9 landscape), like "360p".
 * E.g. "/storage/1/2/42/cover.png" → "/storage/1/2/42/cover_thumb_360.jpg"
 *
 * @param {string|null} originalUrl - the original image URL
 * @param {number} shortEdge - target short edge: 270 (480×270), 360 (640×360), 720 (1280×720)
 * @returns {string|null} thumbnail URL or null if no original
 */
export function thumbUrl(originalUrl, shortEdge = 360) {
  if (!originalUrl) return null
  return originalUrl.replace(/\.\w+$/, `_thumb_${shortEdge}.jpg`)
}
