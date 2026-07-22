# Message UI Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the one-to-one message page viewport-fixed, align the navbar badge, suppress expected long-poll cancellation notices, and simplify message timestamps.

**Architecture:** Route metadata selects a chat-specific shell in `DefaultLayout`, which omits the footer and constrains scrolling to the conversation and message stream. The Axios client treats an explicitly aborted long-poll request as control flow, not an error. Message formatting remains local to `MessageBubble`.

**Tech Stack:** Vue 3 Composition API, Vue Router metadata, Element Plus, Axios, Vite.

---

## File structure

- `Frontend/GameSaves_Fronted/src/router/index.js`: mark the message route as a fixed chat layout.
- `Frontend/GameSaves_Fronted/src/layouts/DefaultLayout.vue`: hide the footer and page-level scrolling only for that route.
- `Frontend/GameSaves_Fronted/src/views/MessagesPage.vue`: make the message grid fill the chat shell and keep scroll within its panes.
- `Frontend/GameSaves_Fronted/src/components/message/MessageComposer.vue`: provide an upward-growing, vertically resizable composer.
- `Frontend/GameSaves_Fronted/src/components/message/MessageBubble.vue`: format timestamps without year or seconds.
- `Frontend/GameSaves_Fronted/src/components/common/AppNavbar.vue`: make the message badge a stable flex item aligned with adjacent navigation links.
- `Frontend/GameSaves_Fronted/src/api/client.js`: skip user-facing errors for Axios cancellation.

### Task 1: Add observable message-page layout contracts

**Files:** Modify `Frontend/GameSaves_Fronted/src/router/index.js`, `Frontend/GameSaves_Fronted/src/layouts/DefaultLayout.vue`, `Frontend/GameSaves_Fronted/src/views/MessagesPage.vue`.

- [ ] **Step 1: Add a failing route/layout assertion.** Create a lightweight Node test using `node --test` that reads the message route and shell source and asserts the route has `meta.chatLayout === true`, `AppFooter` is conditional on that metadata, and the chat page includes an internal scrolling stream.
- [ ] **Step 2: Run the assertion and verify it fails.** Run `node --test Frontend/GameSaves_Fronted/test/messages-layout.test.mjs`; expect failure because the route has no chat-layout metadata and the footer is unconditional.
- [ ] **Step 3: Implement the minimum route-specific shell.** Add `chatLayout: true` to the message route. In `DefaultLayout`, compute `isChatLayout`, conditionally render `AppFooter`, add `default-layout--chat` and `page-content--chat` classes, and make that shell use the remaining viewport height with `overflow: hidden`. In `MessagesPage`, give the grid and thread `min-height: 0`, make the message stream `flex: 1`/`overflow: auto`, and remove page-margin-driven height.
- [ ] **Step 4: Re-run the assertion.** Run `node --test Frontend/GameSaves_Fronted/test/messages-layout.test.mjs`; expect PASS.

### Task 2: Add composer and timestamp presentation behavior

**Files:** Modify `Frontend/GameSaves_Fronted/src/components/message/MessageComposer.vue`, `Frontend/GameSaves_Fronted/src/components/message/MessageBubble.vue`; extend `Frontend/GameSaves_Fronted/test/messages-layout.test.mjs`.

- [ ] **Step 1: Extend the failing test.** Assert the composer has `autosize` with bounded rows and `resize="vertical"`, and that the bubble formatter produces a `M月D日 HH:mm` timestamp rather than `toLocaleString()`.
- [ ] **Step 2: Run the test and verify it fails.** Run `node --test Frontend/GameSaves_Fronted/test/messages-layout.test.mjs`; expect failures for the missing composer props and old timestamp formatter.
- [ ] **Step 3: Implement the minimum presentation changes.** Use Element Plus textarea autosizing from 3 to 10 rows with vertical resize; let the composer grow upward because its parent remains bottom-aligned. Add a local `formatMessageTime` helper that emits month, day, hour, and minute only.
- [ ] **Step 4: Re-run the test.** Run `node --test Frontend/GameSaves_Fronted/test/messages-layout.test.mjs`; expect PASS.

### Task 3: Silence expected cancellation and align the navbar badge

**Files:** Modify `Frontend/GameSaves_Fronted/src/api/client.js`, `Frontend/GameSaves_Fronted/src/components/common/AppNavbar.vue`; extend `Frontend/GameSaves_Fronted/test/messages-layout.test.mjs`.

- [ ] **Step 1: Extend the failing test.** Assert the Axios response-error path returns early for `axios.isCancel(error)` or `error.code === 'ERR_CANCELED'`, and that the navbar message badge uses an explicit flex-alignment class.
- [ ] **Step 2: Run the test and verify it fails.** Run `node --test Frontend/GameSaves_Fronted/test/messages-layout.test.mjs`; expect failures for both missing guards.
- [ ] **Step 3: Implement the minimum fixes.** Return `Promise.reject(error)` before calling `ElMessage.error` for an expected cancellation. Apply a dedicated `message-nav-badge` class to the message badge and style it as an inline flex item aligned with the nav row.
- [ ] **Step 4: Verify the change.** Run `node --test Frontend/GameSaves_Fronted/test/messages-layout.test.mjs` and `npm run build` in `Frontend/GameSaves_Fronted`; expect both to succeed.

## Plan self-review

- Coverage: Tasks 1–3 cover all four requested changes: navigation alignment, fixed chat shell without footer, cancellation handling, and concise timestamp formatting.
- Scope: No backend or transport behavior changes are included; cancellation is only silenced at the UI error boundary.
- Consistency: `chatLayout`, `messages-layout.test.mjs`, and `ERR_CANCELED` use the same names throughout the plan.
