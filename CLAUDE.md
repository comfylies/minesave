# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Game Save Sharing Platform (游戏存档分享平台) — GitHub-style file browsing + Zhihu-style Markdown rendering + Word-style collaborative annotations for game save files. Graduation project.

## Repository Structure

```
GameSaving/
├── 任务书.md                    # Task spec (authoritative requirements doc)
├── gamesaving_database.sql     # Full schema: 8 tables + test data
├── test_data_insert.sql        # Additional Minecraft save test data + login_fails table
├── Database/                   # Physical file storage (mixed storage)
│   └── {userId}/{gameId}/{articleId}/
│       ├── archive.zip
│       ├── extracted/{md5}.{ext}  (游戏存档, content-addressed)
│       └── readme/               (README文档)
│           ├── README.md
│           └── images/           (README引用图片)
├── Backend/GameSaves/          # Spring Boot 3.5.15 + Java 17 + Maven
└── Frontend/GameSaves_Fronted/ # Vue 3 + Vite (fully implemented)
```

## Common Commands

### Backend (from `Backend/GameSaves/`)

```bash
# Build
./mvnw clean compile

# Run all tests (requires MySQL running)
./mvnw test

# Run a single test class
./mvnw test -Dtest=GameSavesApplicationTests

# Start server (port 8080)
./mvnw spring-boot:run

# Kill process on port 8080
cmd //c "taskkill /PID $(netstat -ano | grep ':8080.*LISTENING' | awk '{print $NF}') /F"

# Initialize database (run once, from project root)
mysql -u root -p3256 gamesaving < gamesaving_database.sql
mysql -u root -p3256 gamesaving < test_data_insert.sql
```

### Frontend (from `Frontend/GameSaves_Fronted/`)

```bash
npm run dev       # Start dev server (port 3000, proxies /api and /storage to localhost:8080)
npm run build     # Production build
npm run preview   # Preview production build
```

## Database

- **MySQL 8.0**, database `gamesaving`, user `root`, password `3256`
- **Initialize**: Execute `gamesaving_database.sql` then `test_data_insert.sql` from project root
- **JPA**: `ddl-auto: validate` — entities MUST match existing table schema exactly
- **8 tables**: `users`, `games`, `article`, `savings`, `saving_items`, `comments`, `download_logs`, `login_fails`
- Test users: `admin` / `player_one` / `speedrunner`, all password `password123`

## Backend Architecture

**Package**: `com.gamesaves.gamesaves`

### Authentication (Sa-Token)

The project uses **Sa-Token 1.44.0** for token-based authentication (NOT session-less as previously documented). Key details:

- **Token name**: `Authorization` (frontend sends in request header)
- **Timeout**: 24h (86400s), active timeout 30min (1800s)
- **Token style**: `random-64`
- **Login flow**: `AuthController` (`/api/auth`) handles login with password+captcha, email code login, registration, logout, and session check
- **Permission annotations**: `@SaCheckLogin` on controllers, `@SaCheckPermission("user:manage")` / `@SaCheckPermission("article:manage")` on admin endpoints
- **Permission loading**: `StpInterfaceImpl` loads role-based permissions (`admin` → `user:manage` + `article:manage`)
- **Login security**: 5 max failed attempts → 30min account lock (`login_fails` table); captcha gate on password login; email verification code cooldown (60s) and TTL (5min)
- **Email codes**: QQ SMTP via `EmailCodeService` (Spring Mail)

### Entity Design Pattern

- **Mixed FK approach**: Article uses writable `@ManyToOne` to Game and User (they're looked up during creation and stay loaded). All other entities use plain `Long` FK columns (e.g., `Savings.articleId`, `Comment.articleId`/`Comment.userId`, `SavingItem.snapshotId`) — these avoid N+1 queries and support the index-based query patterns from the task spec.
- **Read-only back-references**: `@ManyToOne(insertable=false, updatable=false)` on Comment→User (for nickname joins), Savings→Article, SavingItem→Savings. These are traversal convenience only — never use them as the FK source in queries.
- All timestamps use `@PrePersist`/`@PreUpdate` (DB defaults are for fallback only)

### Core Data Flow: Article Upload
```
POST /api/articles (MultipartFile + metadata)
  → ArticleServiceImpl saves ZIP to disk, creates Article (UPLOADING)
  → ZipExtractionService.extractAsync() (async, 30s timeout)
    → ZipExtractor: stream extraction, MD5 content-addressing, auto dir nodes
    → SavingItemRepository.saveAll() — batch insert
    → completeArticle() — saves readmeRaw + readmeContent + READY status
  → Frontend polls GET /api/articles/{id}/status
```

### Key Index (for file browsing performance)
`saving_items` table has `idx_snapshot_parent_dir (snapshot_id, parent_path, is_directory)` — all directory browsing queries hit this index with `type=ref`, not full scan.

### ZIP Extraction Security
- Magic number validation (`PK\x03\x04`, `PK\x05\x06`, `PK\x07\x08`)
- Path traversal prevention (rejects `../`, absolute paths, drive letters)
- 30s timeout via `CompletableFuture.orTimeout()`, marks FAILED on timeout
- Content dedup: same MD5 → skip disk write

### Rate Limiting (dual-layer)
1. Memory: `RateLimiter` (ConcurrentHashMap, `@Scheduled` eviction every 60s)
2. Database: `download_logs` table query on IP + time window
Memory cap: 3 downloads/minute per IP; DB hard cap: 5 downloads/minute per IP (configurable via `app.rate-limit.*`)

### API Response Format

All endpoints return `ApiResponse<T>`:
```json
// Success:
{ "code": 200, "message": "success description", "data": { ... } }
// Error:
{ "code": 400, "message": "error description", "data": null }
```
Pagination uses `PageDTO<T>`: `{ "content": [...], "page": 0, "size": 20, "total": 100 }`.

### Controller & Service Summary

| Controller | Route prefix | Auth required | Service(s) |
|---|---|---|---|
| `AuthController` | `/api/auth` | Public | `AuthService` + `EmailCodeService` |
| `UserController` | `/api/users` | Mixed | `UserService` |
| `ArticleController` | `/api/articles` | Mixed | `ArticleService` |
| `FileController` | `/api/files` | Public | `FileExplorerService` + `DownloadService` |
| `CommentController` | `/api/comments` | Mixed | `CommentService` |
| `GameController` | `/api/games` | Mixed | `GameService` |
| `AdminController` | `/api/admin` | Login + Permission | `AdminService` |

Additional service classes (no interface): `AuthService`, `EmailCodeService`, `ZipExtractionService`

Key endpoints:
- `POST /api/auth/login` — password login with captcha, returns Sa-Token token
- `POST /api/auth/login/email` — email verification code login
- `POST /api/auth/register` — user registration
- `POST /api/auth/email-code` — send email verification code (60s cooldown, 5min TTL)
- `GET /api/auth/captcha` — get graphical captcha (base64 image + key)
- `GET /api/auth/check` — check current login session
- `POST /api/articles` — multipart upload (metadata JSON part + file part + readmeFile part), triggers async extraction
- `GET /api/articles/{id}/status` — poll for extraction progress (UPLOADING→EXTRACTING→READY/FAILED)
- `GET /api/articles/{id}/readme` — get rendered README HTML
- `GET /api/files/{articleId}/browse?path=` — GitHub-style directory listing
- `GET /api/files/{articleId}/preview?path=` — text/binary file preview
- `GET /api/files/{articleId}/download` — rate-limited ZIP download
- `GET /api/comments/article/{articleId}` — Word-style annotations with nickname join
- `GET /api/admin/dashboard` — admin dashboard stats (requires `user:manage` permission)
- `PUT /api/admin/users/{id}/ban` — toggle user ban status

### Global Exception Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps domain exceptions to HTTP status codes:
- `ResourceNotFoundException` → 404
- `BadRequestException` → 400
- `CaptchaValidationException` → 400 (extends BadRequestException)
- `AccountLockedException` → 429
- `RateLimitException` → 429 (Too Many Requests)
- `ExtractionTimeoutException` → 408 (Request Timeout)
- `PathTraversalException` → 400
- `FileProcessingException` → 500
- `MaxUploadSizeExceededException` → 413 (Payload Too Large)
- `DataIntegrityViolationException` → 409 (Conflict)
- `MethodArgumentNotValidException` → 400 (with field error mapping)
- `Exception` (catch-all) → 500

### Config Classes (6)

| Class | Purpose |
|---|---|
| `AsyncConfig` | `extractionExecutor` thread pool: core=2, max=4, queue=10, CallerRunsPolicy |
| `SaTokenConfig` | Route interceptors: `/api/admin/**` requires login, static resources excluded |
| `StpInterfaceImpl` | Permission/role loading for Sa-Token (admin → `user:manage`, `article:manage`) |
| `CorsConfig` | CORS allow-all for dev |
| `WebMvcConfig` | Static resource handlers for `/storage/**` |
| `DataInitializer` | `@Profile("!prod")` — resets test user passwords on startup |

### Utility Classes (8)

| Class | Purpose |
|---|---|
| `ZipExtractor` | Stream-based ZIP extraction with MD5 content-addressing |
| `ZipValidator` | Magic number validation for ZIP files |
| `PathTraversalValidator` | Path sanitization (rejects `../`, absolute paths, drive letters) |
| `MarkdownRenderer` | Regex-based Markdown→HTML (consider replacing with commonmark) |
| `FileHasher` | MD5/SHA file hashing utilities |
| `RateLimiter` | In-memory per-IP rate limiter with scheduled eviction |
| `CaptchaUtil` | Graphical captcha generation |
| `XssFilter` | XSS input sanitization |

### Custom Application Properties (application.yaml)

```yaml
app:
  storage:
    database-path: ../../Database            # Mixed storage root (relative to Backend/GameSaves/)
  rate-limit:
    max-downloads-per-ip: 3                  # Memory layer cap
    window-seconds: 60                       # Rate limit window
  extraction:
    timeout-seconds: 30                      # ZIP extraction async timeout
    max-file-size: 209715200                 # 200MB max upload
  login:
    max-fail-count: 5                        # Account lock after N failures
    lock-minutes: 30                         # Lock duration
    captcha-enabled: true                    # Require captcha on login
    email-code-cooldown: 60                  # Resend cooldown (seconds)
    email-code-ttl: 300                      # Code validity (seconds)
```

## Frontend Architecture

The frontend is a **fully implemented** Vue 3 + Vite SPA at `Frontend/GameSaves_Fronted/`.

### Stack
- **Vue 3.5** (Composition API) + **Vite 8**
- **Pinia 3.0** (state management)
- **Vue Router 4.6** (routing with navigation guards)
- **Element Plus 2.14** (UI component library)
- **Axios 1.18** (HTTP client)
- **Marked 18.0** + **Highlight.js** (Markdown rendering)
- **@recogito/text-annotator 4.2** (text selection annotations — Word-style commenting)

### Dev Setup
- Dev server runs on **port 3000**
- Vite proxy: `/api` → `localhost:8080`, `/storage` → `localhost:8080`
- Run with `npm run dev` from `Frontend/GameSaves_Fronted/`

### Directory Structure
```
Frontend/GameSaves_Fronted/src/
├── api/               # 8 API modules (client, authApi, articleApi, commentApi, fileApi, gameApi, userApi, adminApi)
├── components/        # Reusable components (AppNavbar, AppFooter, GameCard, FileBrowser, AnnotationPanel, ReadmeRenderer, etc.)
├── composables/       # useTextAnnotator.js (text annotation composable)
├── layouts/           # DefaultLayout, AdminLayout
├── router/            # Vue Router with auth guards (requiresAuth, requiresAdmin)
├── stores/            # 4 Pinia stores (auth, articles, games, files)
└── views/             # Page components
    ├── HomePage, GamePage, ArticlePage, LoginPage, RegisterPage
    ├── UploadPage, MySavesPage, UserProfilePage, NotFoundPage
    └── admin/         # AdminDashboard, AdminUsers, AdminArticles
```

### Auth Flow (Frontend)
- Login/register via `AuthController` endpoints — receives Sa-Token in response
- Token stored via `authStore`, sent in `Authorization` header on subsequent requests
- Router guards: `requiresAuth` redirects to login, `requiresAdmin` checks role
- Auth check on app mount via `GET /api/auth/check`

### Key Components
- **FileBrowser** — GitHub-style tree browsing, calls `/api/files/{articleId}/browse`
- **AnnotationPanel** — Word-style text annotations on README, uses `@recogito/text-annotator`
- **ReadmeRenderer** — Renders Markdown with syntax highlighting

## Known Limitations
- Markdown rendering uses simple regex-based `MarkdownRenderer`. Add `commonmark` dependency for production quality (frontend uses `marked` library and is fine).
- Tests are minimal (single `contextLoads` smoke test). Add unit/integration tests before production.
- No database migration tool (Liquibase/Flyway). Schema changes require manual SQL execution.
