# Direct Message Retention Design

## Goal

Automatically reclaim private-message storage while leaving an unambiguous, privacy-preserving history boundary in the chat UI:

- Text messages are permanently retained for 30 days from `createdAt`.
- Image and image-with-text messages lose their stored original and thumbnail after 14 days.
- The image message row remains through day 30; its image position renders as `<图片已过期>`.
- A message's accompanying text remains visible until the complete row is removed at day 30.
- When users load the oldest retained messages of a conversation that has deleted history, the UI shows `超过 30 天的消息已清理` as a horizontal divider.

## Chosen Approach

Use soft expiry for the image asset and hard deletion for the complete message row.

This avoids retaining large image blobs after their 14-day window, avoids indefinitely accumulating tombstone rows after 30 days, and still gives users a clear explanation for the discontinuity in their loaded history. The divider is conversation metadata, not a retained copy of old message content.

Alternatives rejected:

1. Hide expired items only in the UI: does not reclaim database or object-storage capacity.
2. Convert every 30-day-old message into a tombstone: preserves a full timeline but leaves the database growing without limit.

## Data Model and Migration

Add Flyway migration `V8__add_direct_message_retention.sql`.

`direct_messages` gains:

- `image_expired_at DATETIME NULL`: non-null only after the message's original and thumbnail have been removed. It makes the image cleanup idempotent and allows the response to declare the expired state without exposing dead storage keys.

`direct_conversations` gains:

- `history_purged_at DATETIME NULL`: set when the first 30-day message batch is removed from the conversation. It is retained after all message rows are deleted so an empty or partially retained conversation can still show the boundary notice.

No old message content, image key, image dimension, or sender data is copied into the conversation table.

## Retention Job

Create a dedicated `DirectMessageRetentionScheduler` with an independently testable cleanup service. It runs daily at 03:15 server time and once shortly after application startup. Values are configurable under `app.messaging.retention`:

- `image-days: 14`
- `message-days: 30`
- `cron: "0 15 3 * * *"`
- `batch-size: 200`

Each run uses one captured `now` value. A message is image-expired when `createdAt <= now - 14 days`, has an image message type, and `imageExpiredAt` is null. For each candidate, the service deletes both nonblank image keys through `StorageService`, clears all image keys and dimensions, and records `imageExpiredAt`. Missing objects are treated as already cleaned, so retries remain safe.

After image cleanup, it processes messages where `createdAt <= now - 30 days`. For each affected conversation, it deletes the message rows and sets `historyPurgedAt` if it is null. It then recalculates the conversation's last retained message. If no message remains, it clears `lastMessageId` and `lastMessageAt`; the conversation row and read-state rows remain. This keeps conversation lists valid without exposing a deleted last message.

The cleanup is batched and transactional per batch. Object deletion is idempotent; a failed batch is logged and retried on the next scheduled/startup run. The service emits no real-time message event because clients receive the latest state on their normal conversation and history refreshes.

## History API

Replace the message-history endpoint's bare `PageDTO<DirectMessageResponse>` response with a `MessageHistoryResponse` that preserves the current page fields (`content`, `page`, `size`, `totalElements`) and adds:

- `historyPurged: boolean`: copied from `DirectConversation.historyPurgedAt != null`.

The endpoint keeps chronological message order and cursor semantics. It returns only retained message rows. `DirectMessageResponse` adds `imageExpired: boolean`, calculated from `imageExpiredAt != null`. For expired images it returns no image key or URL.

Image authorization endpoints continue to return not-found for expired images because their keys have been cleared; they never attempt to read a removed object.

## Frontend Behavior

`messages` Pinia state keeps a per-active-conversation `historyPurged` flag from the history response. `MessageStream` shows a divider only when both conditions are true:

1. there are no older retained messages to load; and
2. `historyPurged` is true.

Therefore it appears exactly at the top of the oldest retained history, whether that state is reached on first load or after repeated upward pagination. It does not appear for a new conversation that simply has no older messages.

`MessageBubble` renders an expired image message in original message order. In place of an image it shows the literal text `<图片已过期>`; for `IMAGE_WITH_TEXT`, it then shows the original text and timestamp. It must not make image-fetch or image-viewer requests for expired images.

For a conversation whose latest message was deleted, `ConversationList` receives a `null` last message and displays `消息已清理`. The conversation remains selectable and can receive new messages normally.

## Error Handling and Privacy

- Storage deletion errors are recorded with the message id and do not mark that image expired, allowing a later retry.
- An already-missing storage object is a successful cleanup condition.
- The scheduler never deletes a message newer than its configured cutoff.
- Deleted message text and image metadata are not retained in audit, conversation, event, or client state.
- Normal participant authorization remains required for all history and image endpoints.

## Tests

Backend tests cover: 14-day image expiry deletes both keys and retains text; image-only expiry returns `imageExpired`; 30-day deletion removes rows and maintains conversation metadata; empty conversations clear their stale last-message reference; pagination reports `historyPurged`; re-running cleanup after successful expiry is idempotent; storage deletion failure leaves the message eligible for retry.

Frontend tests, after adding the project test runner, cover: `<图片已过期>` for image-only and image-with-text messages; no image request for expired messages; divider visibility only at the oldest retained history; `消息已清理` preview for a conversation with no retained last message.
