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

### Frontend (`Frontend/GameSaves_Fronted/`)

```bash
npm run dev      # :3000, proxies /api and /storage → localhost:8080
npm run build    # Production build
```

### Database

```bash
# First time only, from project root:
mysql -u root -p3256 gamesaving < gamesaving_database.sql
mysql -u root -p3256 gamesaving < test_data_insert.sql
```

MySQL 8.0, database `gamesaving`, user `root`, password `3256`. JPA `ddl-auto: validate` — entities MUST match table schema; all tables created manually via SQL scripts. Test users: `admin` / `player_one` / `speedrunner`, password `password123`.

## Backend Architecture

**Spring Boot 3.5.15 + Java 17 + Maven**. Package: `com.gamesaves.gamesaves`.

### Storage Abstraction (CRITICAL)

All file I/O goes through `StorageService` — never touch the filesystem directly. Two implementations, selected by `app.storage.type`:

| Value | Class | Use case |
|-------|-------|----------|
| `local` (default) | `LocalStorageServiceImpl` | Local filesystem, maps keys → `Database/{userId}/{gameId}/{articleId}/` with path traversal guard |
| `s3` | `S3StorageServiceImpl` | AWS S3-compatible (MinIO for test, Tencent COS for production). Uses AWS SDK v2. |

Key semantics:
- All keys use forward-slash format: `articles/{userId}/{gameId}/{articleId}/{filename}`
- `getLocalPath(key)` downloads remote files to temp when needed (e.g. ZIP extraction, ImageIO) — local mode returns the direct path
- `generatePresignedUrl(key, minutes)` for secure downloads; local mode falls back to `/storage/` paths
- Profile configs (`application-{local,minio,cos}.yaml`) wire the right implementation

### Auth (Sa-Token 1.44.0)

Token-based auth via Sa-Token (NOT Spring Security). Token name: `Authorization` header. Timeout 24h, active timeout 30min. `StpInterfaceImpl` loads role→permission mapping (admin → `user:manage` + `article:manage`).

**Route-level auth** (`SaTokenConfig`):
- `/api/auth/**` — public
- `GET /api/games/**`, `GET /api/articles/**`, `/api/files/**`, `GET /api/users/**`, `GET /api/comments/**` — public (but refresh token active time if logged in)
- `POST/PUT/DELETE` on games/articles — requires login
- `/api/admin/**` — login + admin permission (`@SaCheckPermission`)
- All other `/api/**` — requires login

**Login security**: 5 max failed attempts → 30min account lock (`login_fails` table), captcha gate on password login, email code cooldown 60s / TTL 5min.

### Entity Design — CRITICAL

- **Mixed FK strategy**: Article uses writable `@ManyToOne` to Game/User. ALL other entities use plain `Long` FK columns (e.g. `Savings.articleId`, `Comment.articleId`/`Comment.userId`) — avoids N+1 queries.
- **Read-only back-refs**: `@ManyToOne(insertable=false, updatable=false)` on Comment→User, Savings→Article, SavingItem→Savings — traversal convenience only, never the FK source.
- Timestamps via `@PrePersist`/`@PreUpdate`, not DB defaults.

### Game & Alias System

`Game` has `name` (display) + `normalizedName` (lowercase-trimmed, for exact matching) + `searchText` (name + all aliases concatenated, for Meilisearch). `GameAlias` stores alternative names (e.g. "Minecraft" ↔ "MC") with `source` (user/admin) and `status` (confirmed/pending/rejected). When searching, game aliases are folded into the index so users find the game regardless of which name they type.

### Article Upload Flow (key data flow)

```
POST /api/articles (multipart: metadata JSON + ZIP file + optional README .md)
  → ArticleServiceImpl saves ZIP to storage, creates Article (status=UPLOADING)
  → TransactionSynchronization.afterCommit() → triggers async extraction
  → ZipExtractionService.extractAsync() (@Async, 30s timeout)
    → Downloads ZIP from storage to temp (COS mode) or reads directly (local mode)
    → ZipExtractor: stream extraction, MD5 content-addressing, auto dir nodes
    → MagicNumberValidator checks file headers (reject dangerous types)
    → SavingItemRepository.saveAll()
    → Parallel upload of extracted files back to storage (COS: N concurrent)
    → ImageThumbnailService: generate 270p/360p/720p cover thumbnails, README image thumbnails
    → Article status → READY (or FAILED)
    → SearchSyncService indexes in Meilisearch
  → Frontend polls GET /api/articles/{id}/status
```

**Why `afterCommit()`**: the async extractor runs in a separate thread pool — if it starts before the main transaction commits, it won't see the new Article row (DB isolation level).

### Meilisearch Search

Search engine for game + article discovery. Single index `saves` with two doc types: `game-{id}` and `article-{id}`.

- `SearchService.search()` — query parsing, result ranking (games before articles, then by download count desc)
- `SearchSyncService` — CRUD operations on the search index: `indexGame`, `indexArticle`, `deleteGame`, `deleteArticle`, `rebuildAll`
- Config at `meilisearch.host` (default `http://localhost:7700`), API key `meilisearch.api-key`
- `rebuildAll()` does full reindex: all games (including those with 0 articles) + all READY articles, paginated

### ZIP Extraction Gotchas

- **Charset fallback**: UTF-8 → GBK → system default (Chinese Windows ZIPs often use GBK). Uses Apache Commons Compress `ZipFile`, not JDK `ZipInputStream`.
- **Path traversal defense**: `PathTraversalValidator` rejects `../`, absolute paths, drive letters; `LocalStorageServiceImpl.resolvePath()` has containment guard.
- **Content dedup**: same MD5 → same `{md5}.{ext}` physical file → write once.
- **Magic number validation**: `MagicNumberValidator` checks file headers before extraction — blocks executables, DLLs, and other dangerous types. `SafePath` entity stores whitelisted paths per game for security color marking (files inside known safe paths get downgraded warnings).
- **Parallel extraction**: extracted files uploaded to storage in parallel via `parallelStream()` — critical for COS where each upload is a network round-trip.

### Image Thumbnails

`ImageThumbnailService` (pure JDK `BufferedImage`, no external imaging library):
- **Cover thumbnails**: center-crop to 16:9 → resize to 270p/360p/720p (short edge), JPEG quality 0.85
- **README image thumbnails**: proportional resize, 720px short edge, if original exceeds it
- **360h thumbnails**: proportional resize to 360px height
- Uses TwelveMonkeys ImageIO for WebP/JPEG format support (JDK doesn't decode WebP natively; if decode fails, thumbnails are skipped gracefully)

### API Convention

All responses: `{ "code": 200, "message": "...", "data": {...} }`. Pagination: `PageDTO<T>` with `content/page/size/total`. Exceptions → `GlobalExceptionHandler` maps to HTTP status codes (see `exception/` package).

### Download Path Resolution (3-layer fallback)

`FileExplorerServiceImpl.getZipForDownload()` tries: 1) dynamic path from config `storageBasePath` + userId/gameId/articleId, 2) `article.storageRoot` (legacy), 3) `savings.zipPath` (very old data). Paths resolved to absolute via `@PostConstruct` to avoid Tomcat temp dir drift.

### Key Dependencies

Apache Commons Compress 1.26 (ZIP), CommonMark 0.22 + GFM extensions (Markdown → HTML), BCrypt (password only, no full Spring Security), HikariCP (connection pool), Meilisearch Java SDK 0.14.4, AWS S3 SDK v2 2.29.52, TwelveMonkeys ImageIO 3.12 (WebP/JPEG thumbnails).

## Frontend Architecture

**Vue 3.5 + Vite + Element Plus + Pinia**. Composition API (`<script setup>`).

### API Client (`api/client.js`) — CRITICAL

Axios response interceptor has dual-mode handling:
- **JSON** (`application/json`) → auto-unwraps `ApiResponse`, extracts `data.data`, callers get business objects directly
- **Binary/text** (`blob`, `text/plain`, `zip`) → returns full `response` object (with `headers`), caller handles `Content-Disposition` for filenames

### Auth Persistence

Login stores full `UserResponse` in `localStorage`. Router guard reads `localStorage` directly (not Pinia) to avoid flicker on refresh. No JWT — entire user object persisted.

### Key Views

- **ArticlePage**: 3-column layout — FileBrowser + ReadmeRenderer + AnnotationPanel
- **UploadPage**: ZIP upload + README dual-mode (hand-written Markdown OR upload .md file)

## Known Limitations

- No DB migration tool (manual SQL for schema changes). Migration SQL files live in project root when needed (e.g. `migration_game_aliases.sql`).
- No full Spring Security — only Sa-Token for auth.
- Meilisearch must be running separately for search to work (install + run `meilisearch` on port 7700 with master key matching config).
