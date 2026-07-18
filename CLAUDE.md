# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Game Save Sharing Platform (游戏存档分享平台) — GitHub-style file browsing + Markdown README + text annotation on game save files. Personal project.

## Common Commands

### Backend (`Backend/GameSaves/`)

```bash
./mvnw clean compile          # Build
./mvnw test                   # All tests (needs MySQL + Meilisearch)
./mvnw test -Dtest=ClassName  # Single test class
./mvnw spring-boot:run        # Start :8080 (profile: local, default)
./mvnw spring-boot:run -Dspring-boot.run.profiles=minio  # MinIO storage
./mvnw spring-boot:run -Dspring-boot.run.profiles=cos    # COS production storage
```

IntelliJ run configurations are pre-made in `.run/` (GameSaves [local], [minio], [cos]).

### Database

```bash
# First time only, from project root:
mysql -u root -p gamesaving < gamesaving_database.sql
```

MySQL 8.0, database `gamesaving`. JPA `ddl-auto: validate` — entities MUST match table schema; all tables created manually via SQL scripts. Set credentials via `DB_PASSWORD` env var.

**Migration SQL** files live either at the project root or in `Backend/GameSaves/` (e.g., `migration_add_site_settings.sql`). Apply them manually with `mysql` CLI.

### Frontend (`Frontend/GameSaves_Fronted/`)

```bash
npm run dev      # :3000, proxies /api and /storage → localhost:8080
npm run build    # Production build
```

## Backend Architecture

**Spring Boot 3.5.15 + Java 17 + Maven**. Package: `com.gamesaves.gamesaves`.

### Storage Abstraction (CRITICAL)

All file I/O goes through `StorageService` — never touch the filesystem directly. Two implementations, selected by `app.storage.type`:

| Value | Class | Use case |
|-------|-------|----------|
| `local` (default) | `LocalStorageServiceImpl` | Local filesystem, maps keys → `Database/{userId}/{gameId}/{articleId}/` with path traversal guard |
| `s3` | `S3StorageServiceImpl` | AWS S3-compatible (MinIO for test, Tencent COS for production). Uses AWS SDK v2. Production (COS) requires env vars: `COS_ACCESS_KEY`, `COS_SECRET_KEY`, `COS_APPID`. |

`S3Config` (`@ConditionalOnProperty("app.storage.type=s3")`) wires the `S3Client` bean with `@ConfigurationProperties("app.storage.s3")` — supports path-style (MinIO) and virtual-hosted-style (COS) access. Presigned URL expiration: 5 min for private, 7 days for public (matches JWT token TTL).

Key semantics:
- All keys use forward-slash format: `articles/{userId}/{gameId}/{articleId}/{filename}`
- `getLocalPath(key)` downloads remote files to temp when needed (e.g. archive extraction, ImageIO) — local mode returns the direct path
- `generatePresignedUrl(key, minutes)` for secure downloads; local mode falls back to `/storage/` paths
- Profile configs (`application-{local,minio,cos}.yaml`) wire the right implementation

### Auth (Sa-Token 1.44.0 + JWT)

Token-based auth via Sa-Token with **JWT mode enabled** (`is-jwt: true`). Config classes: `SaTokenConfig` (route-level rules) + `SaTokenJwtConfig` (JWT plugin setup) + `StpInterfaceImpl` (role→permission mapping: admin → `user:manage` + `article:manage`). Token name: `Authorization` header. JWT timeout 7 days (no active/idle timeout — JWT is stateless, server restarts don't invalidate tokens).

**Route-level auth** (`SaTokenConfig`):
- `/api/auth/**` — public
- `GET /api/games/**`, `GET /api/articles/**`, `/api/files/**`, `GET /api/users/**`, `GET /api/comments/**` — public
- `POST/PUT/DELETE` on games/articles — requires login
- `/api/admin/**` — login + admin permission (`@SaCheckPermission`)
- All other `/api/**` — requires login

**Two login modes:**
1. **Password + captcha**: username/email + password + graphical captcha image. 5 max failed attempts → 30min account lock (`login_fails` table, tracked per-user with `locked_until` timestamp).
2. **Email verification code**: email + 6-digit code. Sending the code itself requires passing a graphical captcha first (defense against automated spam). Cooldown 90s between sends, TTL 10min (600s), max 5 sends/day per email via in-memory `ConcurrentHashMap` (resets daily). Codes are one-time-use and tracked in `EmailCodeService`.

**Registration**: username + password + email + 6-digit email verification code. Username/email/phone uniqueness validated before consuming the code. Field availability checking endpoint (`/api/auth/check-field`) provides real-time feedback with per-IP rate limiting (max 1 req/s).

**App.vue bootstrap**: on mount, validates the stored JWT against the server via `checkLogin()`. If invalid (e.g. server restart cleared Sa-Token memory sessions), clears localStorage and redirects to login.

### Entity Design — CRITICAL

- **Mixed FK strategy**: Article uses writable `@ManyToOne` to Game/User. ALL other entities use plain `Long` FK columns (e.g. `Savings.articleId`, `Comment.articleId`/`Comment.userId`) — avoids N+1 queries.
- **Read-only back-refs**: `@ManyToOne(insertable=false, updatable=false)` on Comment→User, Savings→Article, SavingItem→Savings — traversal convenience only, never the FK source.
- Timestamps via `@PrePersist`/`@PreUpdate`, not DB defaults.

### Game & Alias System

`Game` has `name` (display) + `normalizedName` (lowercase-trimmed, for exact matching) + `searchText` (name + all aliases concatenated, for Meilisearch). `GameAlias` stores alternative names (e.g. "Minecraft" ↔ "MC") with `source` (user/admin) and `status` (confirmed/pending/rejected). When searching, game aliases are folded into the index so users find the game regardless of which name they type.

### Article Upload Flow (key data flow)

```
POST /api/articles (multipart: metadata JSON + archive file + optional README .md)
  → ArticleServiceImpl saves archive to storage, creates Article (status=UPLOADING)
  → TransactionSynchronization.afterCommit() → triggers async extraction
  → ZipExtractionService.extractAsync() (@Async, 30s timeout)
    → Downloads archive from storage to temp (COS mode) or reads directly (local mode)
    → ArchiveFormat.detect() identifies format via magic bytes (ZIP/7z/tar/tar.gz)
    → Appropriate extractor: ZipExtractor / SevenZExtractor / TarArchiveExtractor
    → MagicNumberValidator checks file headers (reject dangerous types)
    → SavingItemRepository.saveAll()
    → Parallel upload of extracted files back to storage (COS: N concurrent)
    → ImageThumbnailService: generate 270p/360p/720p cover thumbnails, README image thumbnails
    → Article status → READY (or FAILED)
    → SearchSyncService indexes in Meilisearch
  → Frontend polls GET /api/articles/{id}/status
```

**Why `afterCommit()`**: the async extractor runs in a separate thread pool — if it starts before the main transaction commits, it won't see the new Article row (DB isolation level).

### Archive Extraction — Multi-Format

`ArchiveFormat` enum detects format via magic bytes (read order: ZIP → 7z → RAR → tar.gz → tar), with extension-based fallback. All extractors live in the `util/` package (not a separate `archive/` package). Supported formats:

| Format | Extensions | Extraction | Extractor class |
|--------|-----------|------------|-----------------|
| ZIP | `.zip` | ✅ | `ZipExtractor` |
| 7z | `.7z` | ✅ | `SevenZExtractor` |
| tar.gz | `.tar.gz`, `.tgz` | ✅ | `TarArchiveExtractor` |
| tar | `.tar` | ✅ | `TarArchiveExtractor` |
| RAR | `.rar` | ❌ (detected but not extracted) | — |

Key behaviors:
- **Charset fallback**: UTF-8 → GBK → system default (Chinese Windows archives often use GBK). ZIP uses Apache Commons Compress `ZipFile`, not JDK `ZipInputStream`.
- **Path traversal defense**: `PathTraversalValidator` rejects `../`, absolute paths, drive letters; `LocalStorageServiceImpl.resolvePath()` has containment guard.
- **Content dedup**: same MD5 → same `{md5}.{ext}` physical file → write once.
- **Magic number validation**: `MagicNumberValidator` checks file headers before extraction — blocks executables, DLLs, and other dangerous types. `SafePath` entity stores whitelisted paths per game for security color marking (files inside known safe paths get downgraded warnings).
- **Parallel extraction**: extracted files uploaded to storage in parallel via `parallelStream()` — critical for COS where each upload is a network round-trip.

### Meilisearch Search

Search engine for game + article discovery. Single index `saves` with two doc types: `game-{id}` and `article-{id}`.

- `SearchService.search()` — query parsing, result ranking (games before articles, then by download count desc)
- `SearchSyncService` — CRUD operations on the search index: `indexGame`, `indexArticle`, `deleteGame`, `deleteArticle`, `rebuildAll`
- Config at `meilisearch.host` (default `http://localhost:7700`), API key `meilisearch.api-key`
- `rebuildAll()` does full reindex: all games (including those with 0 articles) + all READY articles, paginated

### Image Thumbnails

`ImageThumbnailService` (pure JDK `BufferedImage`, no external imaging library):
- **Cover thumbnails**: center-crop to 16:9 → resize to 270p/360p/720p (short edge), JPEG quality 0.85
- **README image thumbnails**: proportional resize, 720px short edge, if original exceeds it
- **360h thumbnails**: proportional resize to 360px height
- Uses TwelveMonkeys ImageIO for WebP/JPEG format support (JDK doesn't decode WebP natively; if decode fails, thumbnails are skipped gracefully)

### Download System

`FileExplorerService.getZipForDownload()` resolves the article's archive via 3-layer fallback: 1) dynamic path from config `storageBasePath` + userId/gameId/articleId, 2) `article.storageRoot` (legacy), 3) `savings.zipPath` (very old data). Paths resolved to absolute via `@PostConstruct` to avoid Tomcat temp dir drift.

`DownloadService` tracks download counts and logs. Dual-layer rate limiting:
- **Memory layer** (`RateLimiter`): per-IP+article windowed counter (default 3 per 60s), plus per-IP+action global limiting (e.g. game creation: 3/60s → 30min IP ban). IP banning with TTL, scheduled eviction every 60s.
- **DB layer** (`DownloadLogRepository`): hard cap of 5 downloads per IP in 60s.
- **Other rate-limited actions**: email code sending (per-email 90s cooldown, 5/day max), field availability checking (per-IP 1s cooldown), cleanup manual trigger (60s interval), article full edit (2/day).

### Article Editing System

`POST /api/articles/{id}/edit` (multipart: optional metadata JSON + optional README .md + optional cover image). Supports three independent update dimensions:

- **Metadata**: title, version, description, tag associations (via `ArticleUpdateRequest`)
- **README replacement**: upload a new `.md` file to replace the article's README
- **Cover image replacement**: upload a new image; old thumbnails are deleted, new ones regenerated

Daily edit limit: `app.edit.max-daily-edits` (default 2). Tracked via `article.lastEditDate` and `article.dailyEditCount` — resets at midnight. `ArticleFullUpdateRequest` combines metadata + file fields in one multipart request. Frontend: `EditPage.vue` at `/articles/:articleId/edit` (requires auth, article owner only).

### Cleanup System

`CleanupScheduler` batch-processes FAILED and stale UPLOADING articles to prevent storage exhaustion. Three triggers:
- **Scheduled**: every N hours (configurable, default 3h)
- **Startup**: `@PostConstruct` one-shot cleanup on boot
- **Manual**: `POST /api/admin/cleanup/trigger` (admin only)

Batch-processed with independent transactions + configurable sleep between batches. Also deletes physical storage files when `app.cleanup.delete-physical-files=true`.

### Tag System

Global tag pool via `Tag` entity. Articles have many-to-many association with tags. `TagService` provides CRUD and search. Tags are indexed in Meilisearch for article discovery.

### Announcement System

`Announcement` entity with title/content/type/pinned fields. `AnnouncementService` provides CRUD. Frontend displays active announcements site-wide. Admin panel has full management UI.

### Admin Audit Log

`AdminAuditLog` entity records admin operations (action, target type/id, detail, admin username, IP address). Used for accountability — all admin panel modifications are logged. Pending migration SQL at `Backend/GameSaves/migration_add_audit_log.sql`.

### Ghost Article Diagnostics

Ghost articles = storage files without corresponding DB records (orphaned from failed uploads or game merges). `AdminGhosts.vue` + backend endpoint scan storage directories against the `articles` table to find and report ghosts.

### Site Settings System

Key-value store for site-wide configuration (`SiteSetting` entity, `site_settings` table). Currently supports `background_image_url` (homepage hero background). `SiteSettingController` provides public GET and admin POST endpoints. Frontend admin panel at `AdminSiteSettings.vue` (`/admin/site-settings`). Migration SQL at `Backend/GameSaves/migration_add_site_settings.sql`.

### DataInitializer (Startup Bootstrap)

`DataInitializer` is a `CommandLineRunner` that runs on startup in **non-prod profiles only**. It:
- Ensures 3 test users exist with correctly BCrypt-hashed passwords (`password123`): `admin`, `player_one`, `speedrunner`
- Rebuilds the Meilisearch search index via `SearchSyncService.rebuildAll()`
- Resets test user passwords on every startup to keep them in a known-good state regardless of SQL init file contents

### CORS & Static Resources

- **CORS**: `CorsConfig` reads `app.cors.allowed-origins` (default `*`). Uses `allowedOriginPatterns` with credentials enabled. Production should set a specific domain.
- **Static resources**: `WebMvcConfig` maps `/storage/**` → `Database/` directory (resolved absolute). Relevant for local storage mode; in COS mode it serves cached files (thumbnails, covers).

### Security Utilities

- **`XssFilter`**: regex-based sanitization removing `<script>`, event handlers, `javascript:`, `<iframe>`, `<object>`, `<embed>`, `<link>`, `<meta>`. Applied to all user inputs during registration and profile updates.
- **`MagicNumberValidator`**: checks file magic bytes against claimed extension. Blocked: MZ (`.exe`, `.dll`, `.sys`, etc.), ELF (`.so`, `.o`, etc.), SHEBANG (`.sh`, `.py`, `.rb`, etc.). Config at `app.security.magic-number.rules.*`.
- **`PathTraversalValidator`**: rejects `../`, absolute paths, drive letters in archive entry names.
- **`CaptchaUtil`**: pure JDK `java.awt` graphical captcha (130×48, 4 chars, rotation + noise + lines). No third-party library.

### API Convention

All responses: `{ "code": 200, "message": "...", "data": {...} }`. Pagination: `PageDTO<T>` with `content/page/size/total`. Exceptions → `GlobalExceptionHandler` maps to HTTP status codes (see `exception/` package).

### Service Implementation Pattern

Mixed pattern — intentional, not accidental:
- **Interface + impl**: `ArticleService`/`ArticleServiceImpl`, `GameService`/`GameServiceImpl`, `UserService`/`UserServiceImpl`, `CommentService`/`CommentServiceImpl`, `AnnouncementService`/`AnnouncementServiceImpl`, `TagService`/`TagServiceImpl`, `DownloadService`/`DownloadServiceImpl`, `FileExplorerService`/`FileExplorerServiceImpl`, `SavingsService`/`SavingsServiceImpl`, `AdminService`/`AdminServiceImpl`, `SiteSettingService`/`SiteSettingServiceImpl`, `AdminAuditLogService`/`AdminAuditLogServiceImpl`
- **Concrete `@Service` only** (no interface): `AuthService`, `EmailCodeService`, `CleanupScheduler`, `SearchService`, `SearchSyncService`, `ZipExtractionService`, `SafePathService` — these are standalone services where only one implementation will ever exist

### Test Infrastructure

**Backend**: 11 test classes under `src/test/`. Uses Spring Boot Test + JUnit 5. Tests require MySQL + Meilisearch running. Key test files:

| Test | What it covers |
|------|---------------|
| `AuthServiceTest` | Login, registration, captcha validation, account lockout |
| `CommentServiceTest` | Comment CRUD, ownership checks |
| `CleanupSchedulerTest` | Batch cleanup logic, transaction boundaries |
| `FileExplorerServiceTest` | Directory browsing, ZIP download path resolution |
| `TagServiceTest` | Tag CRUD, uniqueness enforcement |
| `LocalStorageServiceImplTest` | Storage CRUD, path resolution, traversal guard |
| `MagicNumberValidatorTest` | Magic byte detection, extension validation |
| `PathTraversalValidatorTest` | `../` rejection, absolute path blocking |
| `XssFilterTest` | Script/handler/iframe removal |
| `ZipExtractorTest` | ZIP/7z extraction, charset handling |
| `GameSavesApplicationTests` | Context loads |

**Frontend**: No test setup (no vitest, jest, or cypress configured).

### Key Dependencies

Apache Commons Compress 1.26 (ZIP/7z/tar), CommonMark 0.22 + GFM extensions (Markdown → HTML), BCrypt (password only, no full Spring Security), HikariCP (connection pool), Meilisearch Java SDK 0.14.4, AWS S3 SDK v2 2.29.52, TwelveMonkeys ImageIO 3.12 (WebP/JPEG thumbnails), Sa-Token 1.44.0 + sa-token-jwt plugin, OkHttp 4.12 (Meilisearch SDK HTTP client), Lombok.

## Frontend Architecture

**Vue 3.5 + Vite + Element Plus + Pinia**. Composition API (`<script setup>`).

### API Client (`api/client.js`) — CRITICAL

Axios response interceptor has dual-mode handling:
- **JSON** (`application/json`) → auto-unwraps `ApiResponse`, extracts `data.data`, callers get business objects directly
- **Binary/text** (`blob`, `text/plain`, `zip`) → returns full `response` object (with `headers`), caller handles `Content-Disposition` for filenames

401 handling: both HTTP 401 and `ApiResponse.code === 401` trigger auth cleanup + redirect to login — handles JWT expiry and token invalidation.

### Auth Persistence

JWT token stored as `satoken` in `localStorage`. User object also cached in `localStorage` as `currentUser` for UI display (role checks, avatar, etc.). Router guard reads `localStorage` directly (not Pinia) to avoid flicker on refresh.

`useAuthStore` (Pinia) manages login/logout/register flows. Logout clears both `satoken` and `currentUser`, and resets all other stores (articles, games, files) to prevent data leakage across sessions.

**Two login flows:**
1. **Password login**: username/email + password + captcha image (graphical captcha via `CaptchaUtil`)
2. **Email code login**: email → send code → enter 6-digit code

### Pinia Stores

| Store | Key state | Purpose |
|-------|-----------|---------|
| `auth` | `currentUser`, `token`, `isLoggedIn`, `isAdmin` | Auth state, login/logout/register actions |
| `articles` | `currentArticle`, `articleList`, `pagination` | Article CRUD, game/user-specific listings |
| `games` | Game list, search results | Game browsing and search |
| `files` | File tree, current path, breadcrumbs | File browser state for article file trees |

### API Modules (`api/`)

12 modules all using the shared Axios instance from `client.js`: `authApi`, `userApi`, `gameApi`, `articleApi`, `fileApi`, `commentApi`, `searchApi`, `tagApi`, `announcementApi`, `adminApi`, `siteSettingsApi`.

### Router & Layouts

Two layouts:
- **DefaultLayout** (`/`) — AppNavbar + AppFooter. Routes: Home (`/`, `noHeaderOffset` for hero), Browse (`/browse`, wide), Game (`/games/:gameId`, wide), Article (`/articles/:articleId`), Search (`/search`, wide), Upload (auth), MySaves (auth), UserProfile
- **AdminLayout** (`/admin/*`) — separate admin shell with sidebar, 8 sub-routes: Dashboard, Users, Articles, Announcements, Games, Cleanup, Ghosts, Site Settings

Router guard checks `satoken` in localStorage for `requiresAuth` routes, and `currentUser.role === 'admin'` for `requiresAdmin` routes. Redirects to login with `redirect` query param on missing auth.

### Text Annotation System

Text selection and commenting on file contents (README + preview-able text files). Built on `@recogito/text-annotator` (W3C Web Annotation standard):

- **`useTextAnnotator`** composable — wraps recogito for selection management and event system. Uses a **no-op custom renderer** — recogito handles spatial index/hover/selection but produces zero DOM output.
- **`HighlightManager`** — custom rendering via CSS Custom Highlight API: colored highlight backgrounds + dotted underlines on annotated text ranges. Zero DOM overhead, no reflow impact.
- **`BorderLayer`** — left-side color bars marking annotated paragraphs. Uses ResizeObserver + rAF for performance.
- **`AnnotationPanel`** — sidebar showing all annotations for the current file, with scroll-to-annotation.
- **`AnnotationPopup`** — floating popup when text is selected, for creating new annotations.
- **`useViewMode`** — global singleton composable for gallery/cover view toggle, persisted to localStorage.

Annotations are stored as `Comment` entities with W3C-compatible selectors (`TextQuoteSelector` + `TextPositionSelector`) serialized in the `anchor` JSON field. This split-selector approach fixes a historical bug where recogito couldn't resolve single-selector annotations.

### File Preview

Text files with extensions in `app.preview.allowed-extensions` (txt, md, json, xml, yml, yaml, log, csv, ini, cfg, conf, properties, html, css, js, ts, java, py, sh, bat, sql, nbt, mcmeta, etc.) can be previewed in-browser. Max preview size: 5 MB (`app.preview.max-size-bytes`). Binary/image files are served as downloads.

### Key Views

- **HomePage**: Google-style centered search hero with background image (configurable via Site Settings). `noHeaderOffset` meta — navbar overlays the hero.
- **BrowsePage**: Wide-layout browse/discover page for exploring games and saves.
- **ArticlePage**: 3-column GitHub-style layout — FileBrowser (tree) + ReadmeRenderer (Markdown with syntax highlighting via highlight.js) + ArticleSidebar (author, download button, tags, metadata). FileBrowser uses BreadcrumbNav for navigation and FileIcon for emoji-based file type icons.
- **UploadPage**: Archive upload + README dual-mode (hand-written Markdown OR upload .md file). Game creation embedded inline with abuse prevention.
- **EditPage** (`/articles/:articleId/edit`): Edit article metadata (title, version, description, tags), replace README file, or replace cover image. Daily edit limit enforced.
- **GamePage**: Game detail + article list with gallery/cover views (toggled via `useViewMode` composable, persisted to localStorage).
- **Admin**: 8 management views — Dashboard, Users, Articles, Announcements, Games, Cleanup (failed archive cleanup trigger + status), Ghosts (orphaned storage diagnostics), Site Settings

### Components

| Category | Components |
|----------|------------|
| **Common** | `AppNavbar` (smart hide-on-scroll, transparent/glass on homepage via inject), `AppFooter`, `EmptyState`, `LoadingSkeleton` |
| **Article** | `ArticleCard` (cover thumbnail + tags + download count), `ArticleMeta` (header bar), `ArticleSidebar` (right panel), `FileBrowser` (directory tree), `BreadcrumbNav`, `FileIcon` (emoji-based), `ReadmeRenderer` (Markdown → HTML + annotation integration), `AnnotationPanel` (sidebar comment list), `AnnotationPopup` (floating text selection popup) |
| **Game** | `GameCard` (game thumbnail + article count) |
| **Tag** | `TagDisplay` (inline chips with "+N" overflow), `TagSelector` (search/create multi-select) |
| **Announcement** | `AnnouncementModal` (Markdown dialog with prev/next navigation) |

### Frontend Utilities

- `utils/format.js` — file size, relative time, date formatting, text truncation, status mapping
- `utils/imageUrl.js` — thumbnail URL derivation (original URL → 270p/360p/720p variants)
- `assets/styles/variables.css` — design tokens (GitHub-style light theme: white bg, black text, border colors)
- `assets/styles/global.css` — global styles + Element Plus theme overrides
- `assets/styles/github-markdown.css` — Markdown rendering styles

### Key Dependencies

Vue 3.5, Vite 8, Element Plus 2.14, Pinia 3, Vue Router 4, Axios, marked (Markdown), highlight.js (syntax highlighting), @recogito/text-annotator 4.2 (text selection/annotation), bcryptjs (frontend password hashing before sending).

## Known Limitations

- No DB migration tool (manual SQL for schema changes). Migration SQL files live at project root or `Backend/GameSaves/` — apply with `mysql` CLI.
- No full Spring Security — only Sa-Token for auth. No `SecurityFilterChain` or custom `AuthenticationEntryPoint`.
- Meilisearch must be running separately for search to work (install + run `meilisearch` on port 7700 with master key matching config).
- RAR archives are detected but not extracted (only ZIP, 7z, tar, tar.gz).
- JWT mode means tokens can't be invalidated server-side before expiry — logout only clears client-side state.
- No frontend test setup (no vitest/jest configured).
- Frontend has no `.env` files — API base URL handled via Vite proxy (dev) or reverse proxy (production).
- `Database/` directory (uploaded files) is gitignored — not part of this repo.
- No Redis or distributed cache — all caching is in-memory `ConcurrentHashMap` (captchas, email codes, rate limit counters, site settings). Not suitable for multi-instance deployments.
- No WebSocket or server-sent events — article status is polled via `GET /api/articles/{id}/status`.
- No i18n — all UI text is hardcoded Chinese.
- No error tracking or analytics (no Sentry, Google Analytics, etc.).
- CORS defaults to `*` — must be configured for production.
- Some services are direct `@Service` classes without interfaces (`AuthService`, `EmailCodeService`, `CleanupScheduler`, `SearchService`, `SearchSyncService`, `ZipExtractionService`, `SafePathService`). Others follow interface+impl pattern. Mix is intentional (interfaces only where multiple implementations exist or are planned).
