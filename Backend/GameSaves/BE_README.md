# GameSaves 后端架构文档

## 目录

1. [技术栈](#技术栈)
2. [项目结构](#项目结构)
3. [架构概览](#架构概览)
4. [API 接口清单](#api-接口清单)
5. [核心设计模式](#核心设计模式)
6. [非显而易见代码说明](#非显而易见代码说明)
7. [配置项说明](#配置项说明)
8. [数据库设计要点](#数据库设计要点)
9. [开发指南](#开发指南)

---

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.5.15 | 父 POM 管理版本 |
| Java | 17 | 编译目标 |
| Spring Data JPA | 6.2.x | Hibernate 实现 |
| MySQL Connector | 8.0+ | `mysql-connector-j` |
| HikariCP | 5.x | 连接池（Spring Boot 默认） |
| BCrypt | 6.2.x | `spring-security-crypto`（仅密码加密，无完整 Security） |
| Commons Compress | 1.26.0 | Apache ZIP 解析（比 JDK ZipFile 更宽容） |
| Lombok | 1.18.x | 编译期注解 |

## 项目结构

```
Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/
├── GameSavesApplication.java          # Spring Boot 入口
├── config/
│   ├── AsyncConfig.java               # 异步线程池（extractionExecutor）
│   ├── CorsConfig.java                # CORS 全局配置
│   ├── DataInitializer.java           # 启动时重置测试用户密码
│   └── WebMvcConfig.java              # 静态资源映射（/storage/**）
├── controller/
│   ├── ArticleController.java         # /api/articles — 文章 CRUD + 上传
│   ├── CommentController.java         # /api/comments — 批注 CRUD
│   ├── FileController.java            # /api/files — 文件浏览/预览/下载
│   ├── GameController.java            # /api/games — 游戏 CRUD
│   └── UserController.java            # /api/users — 登录/注册/用户管理
├── service/
│   ├── ArticleService.java            # 文章服务接口
│   ├── CommentService.java
│   ├── DownloadService.java           # 下载 + 双层限流接口
│   ├── FileExplorerService.java       # 文件浏览/内容/下载接口
│   ├── GameService.java
│   ├── SavingsService.java
│   ├── UserService.java
│   └── ZipExtractionService.java      # 异步 ZIP 解压服务
├── service/impl/
│   ├── ArticleServiceImpl.java        # ★ 核心上传逻辑
│   ├── CommentServiceImpl.java
│   ├── DownloadServiceImpl.java       # 双层限流实现
│   ├── FileExplorerServiceImpl.java   # ★ 文件浏览+下载路径计算
│   ├── GameServiceImpl.java
│   ├── SavingsServiceImpl.java
│   └── UserServiceImpl.java           # 登录/注册实现
├── entity/                            # JPA 实体（7 个）
├── repository/                         # Spring Data JPA Repository（6 个）
├── dto/
│   ├── request/                        # 入参 DTO（8 个）
│   └── response/                       # 出参 DTO（7 个 + PageDTO）
├── exception/                          # 全局异常处理
└── util/
    ├── FileHasher.java                 # 文件内容哈希
    ├── MarkdownRenderer.java           # 正则 Markdown→HTML
    ├── PathTraversalValidator.java     # 路径穿越防御
    ├── RateLimiter.java                # 内存滑动窗口限流
    ├── XssFilter.java                  # XSS 清洗
    ├── ZipExtractor.java               # ★ ZIP 解压核心（内容寻址存储）
    └── ZipValidator.java               # ZIP 魔数校验
```

## 架构概览

```
┌──────────────────────────────────────────────────────────┐
│                    前端 (Vue 3 + Vite)                     │
│                    localhost:3000                          │
└────────────┬─────────────────────────────────────────────┘
             │ HTTP (Vite Proxy → localhost:8080)
             ▼
┌──────────────────────────────────────────────────────────┐
│               ArticleController                           │
│  POST /api/articles  ─── multipart upload                 │
│  GET  /api/articles/{id}/status ─── 轮询解压进度           │
└──────┬───────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────┐    ┌──────────────────────┐
│ ArticleServiceImpl│───▶│ ZipExtractionService  │
│ (上传+存储)       │    │ (@Async 异步解压)      │
└──────────────────┘    └──────────┬───────────┘
       │                           │
       │ TransactionSynchronization│
       │ .afterCommit()            │
       │                           ▼
       │               ┌──────────────────────┐
       │               │    ZipExtractor       │
       │               │ (Commons Compress)    │
       │               │ UTF-8 → GBK 回退      │
       │               └──────────┬───────────┘
       │                          │
       ▼                          ▼
┌──────────────────┐    ┌──────────────────────┐
│   MySQL 8.0      │    │  本地文件系统          │
│   gamesaving     │    │  Database/{u}/{g}/{a}/│
│   7 张表          │    │  archive.zip         │
│                  │    │  extracted/{md5}.ext  │
└──────────────────┘    └──────────────────────┘
```

## API 接口清单

### 用户模块 `/api/users`

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/register` | 注册 | 无 |
| POST | `/login` | 登录（用户名或手机号） | 无 |
| GET | `/{id}` | 获取用户信息 | 无 |
| PUT | `/{id}` | 更新用户信息 | 需登录 |
| GET | `/` | 获取全部用户列表 | 无 |

### 游戏模块 `/api/games`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 获取全部游戏 |
| POST | `/` | 创建游戏 |
| PUT | `/{id}` | 更新游戏 |
| DELETE | `/{id}` | 删除游戏 |

### 文章模块 `/api/articles`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/` | 上传存档（multipart: metadata + file + 可选 readmeFile） |
| GET | `/{id}` | 获取文章详情 |
| PUT | `/{id}` | 更新文章信息 |
| DELETE | `/{id}` | 删除文章 |
| GET | `/{id}/status` | 轮询解压状态 |
| GET | `/game/{gameId}` | 按游戏分页浏览 |
| GET | `/user/{userId}` | 按用户分页浏览 |

### 文件模块 `/api/files`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/{articleId}/browse?path=` | GitHub 风格目录浏览 |
| GET | `/{articleId}/detail?path=` | 文件元数据 |
| GET | `/{articleId}/preview?path=` | 文本/二进制预览 |
| GET | `/{articleId}/download` | 下载原始 ZIP（限流） |

### 批注模块 `/api/comments`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/article/{articleId}` | 按文章获取批注 |
| POST | `/` | 创建批注 |
| DELETE | `/{id}` | 删除批注 |

### 统一响应格式

```json
// 成功
{ "code": 200, "message": "success description", "data": { ... } }
// 错误
{ "code": 400, "message": "error description", "data": null }
// 分页
{ "code": 200, "message": "success", "data": { "content": [...], "page": 0, "size": 20, "total": 100 } }
```

## 核心设计模式

### 1. 上传→存储→解压流水线

```
POST /api/articles (MultipartFile + JSON metadata)
    │
    ▼
ArticleServiceImpl.createArticle()
    ├── 校验 Game + User 存在
    ├── 解析 README（优先手写文本 > 上传.md文件 > ZIP中提取）
    ├── 构建 Article 实体（storageRoot 占位符）
    ├── save() → 获取 ID → 更新 storageRoot 完整路径
    ├── 保存 ZIP 到磁盘（storageBasePath/{userId}/{gameId}/{articleId}/）
    ├── TransactionSynchronization.afterCommit() → 注册异步解压回调
    └── 返回 ArticleDetailResponse
    │
    ▼ (事务提交后)
ZipExtractionService.extractAsync()
    ├── 创建 Savings 记录
    ├── ZipExtractor.extract() — 逐文件解压
    │   ├── ZipFile(UTF-8) → 失败 → ZipFile(GBK) → 失败 → ZipFile(default)
    │   ├── MD5 内容哈希 → {md5}.{ext} 物理存储
    │   ├── 自动创建目录节点 (is_directory=1)
    │   └── 统计 fileCount / totalSize
    ├── 更新 Savings（zipHash, fileManifestHash, 统计）
    ├── MarkdownRenderer.render(readmeRaw) → readmeContent
    └── completeArticle() → status=READY
```

### 2. 内容寻址存储（Content-Addressable Storage）

```
文件内容 → MD5 哈希 → {md5}.{ext} → extracted/ 目录
相同内容 = 相同哈希 = 只存一份（dedup）
virtual_path → physical_key 映射存在 saving_items 表
```

### 3. GitHub 风格目录浏览

```
parent_path + is_directory 联合索引实现高效目录切换：
  根目录文件: WHERE snapshot_id=1 AND parent_path='' AND is_directory=0
  子目录文件: WHERE snapshot_id=1 AND parent_path='region/' AND is_directory=0
  面包屑:    WHERE snapshot_id=1 AND parent_path='' AND is_directory=1
```

关键点：`saving_items` 表同时存储文件和目录节点，目录节点 `is_directory=1`、`file_size=0`、`physical_key=''`。

### 4. 双层下载限流

```
第 1 层（内存）: RateLimiter (ConcurrentHashMap<ip:articleId, count>)
                每 60s @Scheduled 清理 → 快速拒绝
第 2 层（数据库）: download_logs 表 COUNT(ip, downloaded_at > NOW()-60s)
                  > 5 次 → 拒绝 → 持久化审计
```

### 5. FK 混合策略

- **Article** 使用 writable `@ManyToOne` 到 Game 和 User（写入时查找，常驻加载）
- **其他实体** 使用独立 `Long` FK 列（`Savings.articleId`, `Comment.articleId/userId`）→ 避免 N+1 查询
- **只读回引**：`@ManyToOne(insertable=false, updatable=false)` — 仅用于 join 查询，不做 FK 来源

## 非显而易见代码说明

### `DataInitializer` — 启动时密码重置

> `config/DataInitializer.java`

为什么需要：SQL 测试数据的 BCrypt 哈希可能不是从 `password123` 生成的（尤其是 `$2b$` vs `$2a$` 前缀差异）。每次启动检查并重新编码，确保登录始终可用。`@Profile("!prod")` 限制仅非生产环境生效。

### `TransactionSynchronization.afterCommit()` — 异步解压延迟启动

> `service/impl/ArticleServiceImpl.java` 第 144 行

为什么需要：`ZipExtractionService.extractAsync()` 运行在独立线程池，如果它在主事务 COMMIT 之前启动，`articleRepository.findById()` 查不到刚插入的 Article（数据库隔离级别）。`afterCommit()` 确保解压线程在数据可见后启动。

### `ZipExtractor` 字符集回退

> `util/ZipExtractor.java`

为什么需要：JDK `ZipInputStream` 强制 UTF-8 读文件名；`ZipFile` 构造时可指定 charset 但对 CEN 头损坏的 ZIP 会直接抛异常。Apache Commons Compress `ZipFile` 更宽容 + 支持 GBK 编码的中文 Windows ZIP 文件。回退链：UTF-8 → GBK → 系统默认。

### `FileExplorerServiceImpl.getZipForDownload()` — 路径三层 fallback

> `service/impl/FileExplorerServiceImpl.java`

```java
// 1. 优先: 从配置的 storageBasePath + userId + gameId + articleId + zipFilename 动态计算
// 2. Fallback: article.storageRoot + article.zipFilename (兼容旧数据)
// 3. Last resort: savings.zipPath (极旧数据兼容)
```

为什么需要：`article.storageRoot` 可能存储了旧版相对路径（`Database/2/2/1/`），在 Tomcat 工作目录变化时解析失败。动态计算始终使用 `@PostConstruct` 解析过的绝对路径。

### `Paths.get(databasePath).toAbsolutePath().normalize()` — 路径解析

> 三个 Service 的 `@PostConstruct` 中

为什么需要：`application.yaml` 中 `database-path: ../../Database` 是相对路径。`toAbsolutePath()` 在启动时解析为绝对路径并缓存。后续所有文件操作使用绝对路径，避免 Tomcat 临时目录干扰。

### `@NoArgsConstructor` on DTOs

> 全部 8 个 Request DTO

为什么需要：Spring/Jackson 反序列化 JSON 需要无参构造函数。Lombok `@Data` 不生成无参构造（仅生成 getter/setter/RequiredArgsConstructor）。虽然 Java 编译器给无显式构造函数的类提供默认无参构造，但某些 Jackson 版本/Lombok 交互下会丢失。显式 `@NoArgsConstructor` 确保安全。

## 配置项说明

```yaml
app:
  storage:
    database-path: ../../Database   # 相对于 Backend/GameSaves/，生产环境用 APP_STORAGE_DATABASE_PATH 环境变量覆盖
  rate-limit:
    max-downloads-per-ip: 3         # 每 IP 每分钟最多下载次数（内存层）
    window-seconds: 60              # 限流时间窗口
  extraction:
    timeout-seconds: 30             # ZIP 解压超时（当前未使用——见 ZipExtractionService 注释）
    max-file-size: 209715200        # 最大上传 200MB
```

## 数据库设计要点

7 张表：`users` → `games` → `article` → `savings` → `saving_items` (+ `comments` + `download_logs`)

- `ddl-auto: validate` — 表必须由 SQL 脚本手动创建，JPA 不自动建表
- `saving_items` 的 `UNIQUE uk_snapshot_path(snapshot_id, virtual_path)` **严禁包含 user_id**
- `article.readme_raw` 存储原始 Markdown，`article.readme_content` 存储渲染后 HTML（以空间换时间）
- `saving_items.parent_path` 配合 `idx_snapshot_parent_dir` 索引实现高效的目录浏览查询

## 开发指南

### 启动命令

```bash
cd Backend/GameSaves

# 初始化数据库（仅首次）
mysql -u root -p3256 gamesaving < ../../gamesaving_database.sql
mysql -u root -p3256 gamesaving < ../../test_data_insert.sql

# 编译 + 启动
./mvnw clean compile
./mvnw spring-boot:run
```

### 添加新 API 端点

1. 在对应 Controller 添加方法
2. 在 Service 接口声明方法
3. 在 ServiceImpl 实现
4. DTO 用 Lombok `@Data` + 显式 `@NoArgsConstructor` `@AllArgsConstructor`
5. 异常统一抛出自定义异常（`BadRequestException` / `ResourceNotFoundException`），由 `GlobalExceptionHandler` 统一转 HTTP 状态码

### 添加新实体

1. 先写 SQL 建表脚本
2. 创建 Entity 类，`@Column` 与表字段精确匹配
3. `@PrePersist` / `@PreUpdate` 处理时间戳（不依赖数据库 DEFAULT）
4. `ddl-auto: validate` 模式下 JPA 启动时自动校验匹配

### 已知限制

- **Markdown 渲染**：当前使用简单正则 `MarkdownRenderer`，生产应换 `commonmark` 库
- **认证**：只有模拟登录（无 JWT/Session），生产需集成 Spring Security
- **测试**：只有一个 `contextLoads` 冒烟测试
- **数据库迁移**：无 Liquibase/Flyway，改表需手动写 SQL
