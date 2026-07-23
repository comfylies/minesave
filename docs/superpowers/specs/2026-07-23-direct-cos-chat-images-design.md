# Direct COS Delivery for Chat Images

## Goal

Eliminate the application-server image relay for direct messages. The browser must load chat thumbnails and expanded originals directly from COS after the application verifies that the viewer belongs to the conversation.

## Current cause

Each rendered `MessageBubble` currently requests both the thumbnail and the original through `GET /api/messages/items/{id}/image`. That endpoint validates membership, downloads the entire object from COS into a `byte[]`, then sends the same bytes to the browser. A page of image messages therefore creates two authenticated database checks and two COS-to-application transfers per image, including originals that the user has not opened.

Article covers, avatars, and the site background already use COS pre-signed URLs and are loaded directly by the browser. Chat images should use the same storage-delivery pattern while retaining conversation authorization.

## API and authorization

- Message-list responses populate `imageThumbnailUrl` only for messages containing an image. The service generates it after it has verified that the caller is a conversation participant.
- The thumbnail URL is a COS pre-signed URL with a five-minute lifetime, matching the existing private-download expiration.
- Add an authenticated endpoint that returns a fresh pre-signed URL for one requested image variant. It validates the caller through the existing `getImageKey` authorization path before returning the URL.
- `imageOriginalUrl` remains absent from message-list responses. The frontend requests it only after the user clicks a thumbnail.
- Keep `/storage/messages/**` blocked. A pre-signed URL is a short-lived bearer URL, but a participant can already save an image after viewing it; no unauthenticated application route is introduced.

## Frontend behavior

- A message bubble uses its supplied thumbnail URL directly as the image source. It no longer downloads either image through Axios during component mount.
- Clicking a thumbnail requests a fresh original-image URL, then opens the existing image viewer with that URL.
- If a thumbnail URL expires before the browser uses it, the bubble requests a refreshed thumbnail URL once and retries. Other image failures retain the ordinary broken-image behavior.
- The component does not create Blob object URLs for COS images, so it no longer needs to keep decoded originals alive for every rendered message.

## Scope boundaries

- Do not make COS objects public.
- Do not change message pagination, realtime event delivery, upload validation, image dimensions, or the 5 MiB upload limit.
- Do not route chat images through the existing `/storage/` resource handler.
- Do not add a CDN or alter cover/background image delivery in this change.

## Testing

- Add backend unit coverage that confirms a participant can receive a URL for the requested variant and that existing authorization remains the gate.
- Add frontend source-level coverage that the mounted bubble uses only the supplied thumbnail URL and fetches the original only from the click handler.
- Run focused backend tests, the frontend test suite, and the frontend production build.
