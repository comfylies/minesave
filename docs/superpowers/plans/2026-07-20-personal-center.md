# Personal Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the self profile page into a personal center with safe profile editing, managed avatars, password changes, and verified email changes.

**Architecture:** Keep `/users/:userId` as the single profile route. Move all writes to `/api/account/*`, where the authenticated Sa-Token identity supplies the user ID. Introduce focused account DTOs and an `AvatarService`; preserve public profile reads and existing storage abstraction.

**Tech Stack:** Spring Boot 3.5, Java 17, JPA/Flyway, Sa-Token, BCrypt, ImageIO/TwelveMonkeys, Vue 3, Pinia, Element Plus.

---

### Task 1: Add migration and account request contracts

**Files:**
- Create: `Backend/GameSaves/src/main/resources/db/migration/V{next}__add_user_avatar_key.sql`
- Create: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/dto/request/ProfileUpdateRequest.java`
- Create: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/dto/request/ChangePasswordRequest.java`
- Create: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/dto/request/SendEmailChangeCodeRequest.java`
- Create: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/dto/request/ConfirmEmailChangeRequest.java`
- Modify: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/entity/User.java`

- [ ] Add a failing context/schema test asserting `User.avatarKey` is mapped and compilation fails before the field exists.
- [ ] Add `avatar_key VARCHAR(500) DEFAULT NULL` in the next Flyway migration; do not edit released V1.
- [ ] Define immutable request DTO validation: nickname 1–24 chars, bio ≤500, Mainland phone pattern when nonblank, password 6–100 chars, matching confirmation checked in service, and `@Email` for new email.
- [ ] Add `@Column(name = "avatar_key", length = 500)` to `User`.
- [ ] Run `mvn -q -DskipTests compile` from `Backend/GameSaves`.

### Task 2: Secure profile writes and remove obsolete user API

**Files:**
- Modify: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/controller/UserController.java`
- Modify: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/service/UserService.java`
- Modify: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/service/impl/UserServiceImpl.java`
- Modify: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/dto/response/UserResponse.java`
- Create: `Backend/GameSaves/src/test/java/com/gamesaves/gamesaves/service/UserServiceImplTest.java`

- [ ] Write failing tests for: another user's ID cannot be supplied to a write method; duplicate phone is rejected; nickname/bio are XSS-sanitized; a managed avatar key resolves through `StorageService.getPublicUrl`.
- [ ] Replace `PUT /api/users/{id}` with `PATCH /api/account/profile`; obtain `StpUtil.getLoginIdAsLong()` in the controller and never accept target user ID in the request.
- [ ] Update only nickname, bio, and phone in `ProfileUpdateRequest`; apply `XssFilter.sanitize` before persistence and preserve existing uniqueness checks.
- [ ] Remove public `GET /api/users` and obsolete front-end `userApi.login`, `userApi.register`, `userApi.getAll` callers.
- [ ] Run the new unit test and backend compile.

### Task 3: Implement managed avatar lifecycle

**Files:**
- Create: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/service/AvatarService.java`
- Create: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/service/impl/AvatarServiceImpl.java`
- Modify: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/controller/UserController.java`
- Modify: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/service/UserService.java`
- Modify: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/service/impl/UserServiceImpl.java`
- Create: `Backend/GameSaves/src/test/java/com/gamesaves/gamesaves/service/impl/AvatarServiceImplTest.java`

- [ ] Write failing tests using a generated in-memory PNG: valid image writes `avatars/{id}/...jpg`; non-image bytes, GIF/SVG and files over 2 MiB are rejected; replacing an avatar schedules deletion of the old managed key; deleting clears the key.
- [ ] Decode the multipart file with `ImageIO.read`, reject null/oversized dimensions, center-crop/scale to 512×512 JPEG using the existing image utility patterns, and store only through `StorageService`.
- [ ] Expose `POST /api/account/avatar` and `DELETE /api/account/avatar`, both tied to the current login ID.
- [ ] Preserve legacy `avatarUrl` when `avatarKey` is null; return storage-derived URL when a key exists.
- [ ] Run avatar tests and backend compile.

### Task 4: Add password and purpose-scoped email change flows

**Files:**
- Modify: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/service/EmailCodeService.java`
- Modify: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/service/AuthService.java`
- Modify: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/controller/AuthController.java`
- Modify: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/dto/request/EmailCodeRequest.java`
- Modify: `Backend/GameSaves/src/test/java/com/gamesaves/gamesaves/service/AuthServiceTest.java`

- [ ] Write failing tests: wrong current password cannot change password; changed password BCrypt-matches only the new secret; registration code cannot confirm an email change; duplicate new email is rejected; successful email change consumes its code.
- [ ] Add `EmailCodePurpose` (`REGISTER`, `LOGIN`, `CHANGE_EMAIL`) and key in-memory code/cooldown state by purpose plus normalized email; update existing registration/login callers to pass their purpose.
- [ ] Add account-only endpoints: `PUT /api/account/password`, `POST /api/account/email-code`, and `PUT /api/account/email`. Require current password for each security operation and graphical captcha for sending an email-change code.
- [ ] Use the existing `BCryptPasswordEncoder` and ensure no plaintext password or verification code is logged.
- [ ] Run focused authentication tests and backend compile.

### Task 5: Convert the self profile UI to personal center

**Files:**
- Modify: `Frontend/GameSaves_Fronted/src/views/UserProfilePage.vue`
- Modify: `Frontend/GameSaves_Fronted/src/api/userApi.js`
- Modify: `Frontend/GameSaves_Fronted/src/api/authApi.js`
- Modify: `Frontend/GameSaves_Fronted/src/stores/auth.js`
- Modify: `Frontend/GameSaves_Fronted/src/components/common/AppNavbar.vue`

- [ ] Add API functions matching the new account routes and remove obsolete user login/register/list functions.
- [ ] Add an auth-store `updateCurrentUser(user)` action that updates the ref and `localStorage` atomically after profile/avatar/email responses.
- [ ] When `isSelf`, render “个人中心”, place “编辑个人资料” beside the nickname, and open an Element Plus dialog for nickname, bio, phone, avatar upload/revert.
- [ ] Render a self-only account security card with independent password and email dialogs; validate confirmation locally, reuse captcha retrieval for email-code sending, and preserve form data on request errors.
- [ ] Keep non-self pages read-only and preserve the existing article list, pagination, and gallery/list toggle.
- [ ] Run `npm run build` from `Frontend/GameSaves_Fronted`.

### Task 6: Final regression checks and documentation

**Files:**
- Modify: `CLAUDE.md` only if API documentation is maintained there.
- Modify: `docs/superpowers/specs/2026-07-20-personal-center-design.md` if implementation decisions change the approved design.

- [ ] Run `mvn -q -DskipTests compile` from `Backend/GameSaves`.
- [ ] Run focused backend tests that do not require external mail delivery; note any MySQL/Meilisearch-dependent suite that cannot run locally.
- [ ] Run `npm run build` from `Frontend/GameSaves_Fronted` and resolve project-source warnings.
- [ ] Review `git diff --check` and `git status --short`, confirming only feature files plus pre-existing user changes are present.
- [ ] Do not stage or commit unrelated existing worktree changes.
