/**
 * Derive thumbnail URL from original cover image URL (LEGACY FALLBACK).
 *
 * New code should prefer the server-provided {@code coverThumbnail} / {@code coverThumbnail720}
 * fields from the API response, which return pre-resolved thumbnail URLs and work correctly
 * with S3/COS presigned URLs.
 *
 * This function only works reliably when the original URL is a simple path
 * (e.g. {@code /storage/1/2/42/cover.png}). For presigned URLs with query parameters,
 * the regex cannot derive a valid thumbnail URL and returns the original unchanged.
 *
 * @param {string|null} originalUrl - the original image URL (path or URL)
 * @param {number} shortEdge - target short edge: 270 (480×270), 360 (640×360), 720 (1280×720)
 * @returns {string|null} thumbnail URL or null if no original
 */
export function thumbUrl(originalUrl, shortEdge = 360) {
  if (!originalUrl) return null
  return originalUrl.replace(/\.\w+$/, `_thumb_${shortEdge}.jpg`)
}
