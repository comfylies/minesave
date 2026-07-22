# Stable Scrollbar Gutter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the fixed navbar horizontally stationary when the message route suppresses document scrolling.

**Architecture:** Reserve browser scrollbar gutter width globally so a route-level `overflow: hidden` transition cannot alter the centered container width.

**Tech Stack:** CSS, Vue frontend Node test runner.

---

### Task 1: Reserve the document scrollbar gutter

**Files:** Modify `Frontend/GameSaves_Fronted/src/assets/styles/global.css`; modify `Frontend/GameSaves_Fronted/test/messages-layout.test.mjs`.

- [ ] **Step 1: Write the failing assertion.** Add a test that loads `global.css` and requires `html { scrollbar-gutter: stable; }`.
- [ ] **Step 2: Verify RED.** Run `npm test` in `Frontend/GameSaves_Fronted`; expect the new assertion to fail because the global gutter is not reserved.
- [ ] **Step 3: Implement the CSS.** Add `scrollbar-gutter: stable;` to the global `html` rule. Do not add route-specific padding or force a visible scrollbar.
- [ ] **Step 4: Verify GREEN.** Run `npm test` and `npm run build` in `Frontend/GameSaves_Fronted`; expect both to pass.
