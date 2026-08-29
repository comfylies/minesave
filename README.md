# 🎮 游戏存档分享平台（MineSave）

一个 GitHub 风格的**游戏存档分享平台**：支持存档文件的在线浏览、Markdown README 渲染、以及文本标注评论。用户可以上传存档压缩包（ZIP / 7z / tar / tar.gz），平台自动解压、安全校验、生成封面缩略图并建立全文检索，访客可以像浏览代码仓库一样浏览存档目录、在线阅读 README 并给文本添加批注，还可以下载整个存档包。

> 个人项目 · 前后端分离 · 中文界面

---

## ✨ 功能特性

- 🗂️ **GitHub 风格文件浏览** — 存档解压后以目录树展示，支持面包屑导航与在线预览（文本类文件）
- 📝 **Markdown README** — 存档自带 README 在线渲染（语法高亮），并支持**文本选择标注评论**（W3C Web Annotation 标准）
- 📦 **多格式压缩包解析** — ZIP / 7z / tar / tar.gz 自动解压，RAR 可识别；支持中文 GBK 编码回退、MD5 内容去重
- 🔒 **安全校验** — 文件魔数校验（拦截可执行文件/脚本）、路径穿越防护、危险存档安全等级标记
- 🖼️ **封面缩略图** — 自动生成 270p / 360p / 720p 多尺寸封面（中心裁剪 16:9）及 README 图片缩略图
- 🔍 **全文检索** — Meilisearch 驱动，游戏别名折叠搜索、游戏/存档混合排序
- ⬇️ **安全下载** — 多层限流 + IP 封禁、超大文件（>500MB）图形验证码、防批量抓取
- 💬 **实时私信** — WebSocket/STOMP 为主、HTTP 长轮询兜底的双通道实时 1 对 1 聊天，支持图片消息
- ❤️ **社交功能** — 文章投票（顶/踩）、收藏、用户关注
- 🏷️ **标签系统** — 全局标签池，多对多关联，参与检索
- 👤 **账户中心** — 头像上传（自动居中裁剪多尺寸）、资料编辑、密码修改
- 🛡️ **管理后台** — 用户 / 文章 / 公告 / 游戏 / 清理 / 幽灵文件诊断 / 站点设置 / 留言管理，操作全审计
- 🚀 **自动化部署** — GitHub Actions 推送即部署（腾讯云服务器）

---

## 🧱 技术栈

| 端 | 技术 |
|----|------|
| 后端 | Spring Boot 3.5.15 · Java 17 · Maven · Sa-Token 1.44.0（JWT 模式）· Flyway 11.7 · HikariCP |
| 前端 | Vue 3.5 · Vite 8 · Element Plus 2.14 · Pinia 3 · Vue Router 4 · Axios |
| 数据库 | MySQL 8.0（JPA `ddl-auto: validate` + Flyway 版本化迁移） |
| 搜索 | Meilisearch（单索引 `saves`，游戏 + 存档两类文档） |
| 存储 | 存储抽象层：本地文件系统 / AWS S3 兼容（MinIO 测试、腾讯云 COS 生产） |
| 压缩解析 | Apache Commons Compress 1.26（ZIP / 7z / tar） |
| 实时通信 | Spring WebSocket + STOMP，HTTP 长轮询兜底 |
| 文本标注 | @recogito/text-annotator 4.2 + CSS Custom Highlight API |
| 图片处理 | 纯 JDK `BufferedImage` + TwelveMonkeys ImageIO（WebP 支持） |
| 其他 | bcrypt（密码）· CommonMark + GFM（Markdown 渲染）· OkHttp · Lombok |

---

## 📁 项目结构

```
├── Backend/
│   └── GameSaves/              # Spring Boot 后端（com.gamesaves.gamesaves）
│       └── src/main/resources/db/migration/   # Flyway 版本化迁移 SQL
├── Frontend/
│   └── GameSaves_Fronted/      # Vue 3 前端
├── docs/                       # 设计与实施文档
├── .github/workflows/deploy.yml # 推送 main 自动部署
└── gamesaving_database.sql      # 数据库备份
```

---

## 🚀 快速开始

### 环境要求

- JDK 17 + Maven
- Node.js 20+
- MySQL 8.0
- Meilisearch（搜索，需单独启动，端口 7700）

### 1. 初始化数据库

Flyway 会在应用启动时自动执行迁移建表，只需先创建空库：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS gamesaving CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

数据库账号密码通过环境变量配置（如 `DB_PASSWORD`）。

### 2. 启动 Meilisearch

```bash
meilisearch --master-key <你的主密钥>   # 端口 7700，与配置 meilisearch.api-key 一致
```

### 3. 启动后端（:8080）

```bash
cd Backend/GameSaves

./mvnw clean compile                  # 构建
./mvnw spring-boot:run                # 本地模式启动（local 存储，默认）
./mvnw spring-boot:run -Dspring-boot.run.profiles=minio   # MinIO 存储
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod    # 腾讯云 COS 生产存储
```

IntelliJ 运行配置已预置在 `.run/`（GameSaves [local] / [minio] / [prod]）。

### 4. 启动前端（:3000）

```bash
cd Frontend/GameSaves_Fronted
npm install
npm run dev     # :3000，/api 与 /storage 代理到 localhost:8080
```

### 5. 测试账号（仅开发环境自动初始化）

`DataInitializer` 在非 prod 启动时确保以下测试用户存在（密码均为 `password123`）：

| 用户名 | 角色 |
|--------|------|
| `admin` | 管理员 |
| `player_one` | 普通用户 |
| `speedrunner` | 普通用户 |

---

## 🗄️ 数据库迁移（Flyway）

所有表结构变更通过版本化 SQL 管理，应用启动自动执行（JPA `ddl-auto: validate`，实体必须与表结构一致）：

- `V1__initial_schema.sql` — 全部 15 张表的初始建表语句
- `V2__add_user_avatar_key.sql` — 用户头像存储 key
- `V3__create_contact_messages.sql` — 联系 / 反馈留言表
- `V4__add_archive_security_and_terms_consent.sql` — 存档安全等级 + 条款同意字段
- `V5__create_direct_messaging.sql` — 私信系统（会话 / 消息 / 已读状态三张表）
- `V6__enforce_direct_conversation_participant_order.sql` — 会话参与者排序约束（userOneId < userTwoId）
- `V7__add_article_favorites_and_user_follows.sql` — 文章收藏 + 用户关注

新增迁移：在 `src/main/resources/db/migration/` 下创建 `V{序号}__{描述}.sql`，重启即自动执行。

```bash
# 查看迁移历史
mysql -u root -p -e "SELECT version, description, installed_on, success FROM gamesaving.flyway_schema_history;"
```

---

## ☁️ 存储抽象

所有文件 I/O 均通过 `StorageService`，通过 `app.storage.type` 选择实现：

| 值 | 实现类 | 用途 |
|----|--------|------|
| `local`（默认） | `LocalStorageServiceImpl` | 本地文件系统，key 映射到 `Database/{userId}/{gameId}/{articleId}/`，含路径穿越防护 |
| `s3` | `S3StorageServiceImpl` | AWS S3 兼容（MinIO 测试 / 腾讯云 COS 生产），生产需环境变量 `COS_SECRET_ID`、`COS_SECRET_KEY`、`COS_APPID` |

- Key 统一使用正斜杠格式：`articles/{userId}/{gameId}/{articleId}/{filename}`
- 私有文件预签名 URL 有效期 5 分钟，公开文件 7 天（与 JWT 有效期一致）
- 本地模式下下载回退到 `/storage/` 路径

---

## 🗺️ 核心功能模块

### 🔐 认证（Sa-Token + JWT）

- **密码 + 图形验证码登录**：用户名/邮箱 + 密码 + 验证码；连续失败 5 次锁定 30 分钟
- **邮箱验证码登录**：发码前需先通过图形验证码（防刷），90s 冷却、10 分钟有效、每邮箱每日最多 5 次、一次性使用
- **注册**：用户名 + 密码 + 邮箱 + 6 位邮箱验证码，唯一性预校验
- 登录态存于 `localStorage`（`satoken`），前端启动时向服务端校验 JWT 有效性
- 角色 → 权限：`admin` → `user:manage` + `article:manage`

### 🎮 游戏与别名系统

游戏含显示名 + 规范化名 + 搜索文本（名称 + 全部别名拼接）。`GameAlias` 支持别名（如 “Minecraft” ↔ “MC”），用户/管理员提交，状态分为 confirmed / pending / rejected。搜索时别名折叠进索引，用户输入任意名称都能找到游戏。

### 📤 存档上传与解析流程

```
POST /api/articles (multipart: 元数据 JSON + 存档包 + 可选 README.md)
  → 存档入库，创建 Article（状态 UPLOADING）
  → 事务提交后（afterCommit）异步触发解压
  → 魔数识别格式（ZIP / 7z / tar.gz / tar，RAR 可识别不解析）
  → 安全校验（魔数 + 路径穿越 + 危险类型拦截）
  → 逐文件入库，MD5 内容去重
  → 并行回传存储（COS 模式下 N 并发）
  → 生成封面缩略图与 README 图片缩略图
  → 状态置为 READY（或 FAILED），同步搜索索引
  → 前端轮询 GET /api/articles/{id}/status 展示实时进度
```

### 🔎 搜索（Meilisearch）

单索引 `saves`，两类文档：`game-{id}`、`article-{id}`。排序策略：游戏优先于存档，同类型按下载量降序。`rebuildAll()` 全量重建：所有游戏（含 0 存档的）+ 所有 READY 存档，分页执行。

### ⬇️ 下载系统

- 3 层回退定位存档包：动态配置路径 → `article.storageRoot`（旧）→ `savings.zipPath`（远古数据）
- 双层限流：内存层（每 IP + 文章 3 次/60s + 全局 IP 封禁）、DB 层（每 IP 60s 内硬上限 5 次）
- **大文件验证码**：超过 `app.download.captcha-threshold`（500 MB）需图形验证码，通过后发放一次性下载令牌（5 分钟有效），每 IP 每日最多 3 次大文件下载

### 💬 私信系统

- **REST API**（`/api/messages`）：会话列表、游标分页历史、发送（文本 + 可选图片）、标记已读、未读总数、长轮询事件
- **实时双通道**：STOMP over WebSocket（`/ws/messages`，CONNECT 帧携带 JWT 认证）+ HTTP 长轮询（`/api/messages/events`，25s 超时）兜底
- **消息限流**：突发层每 IP 5 条/5s（30s IP 封禁）、持续层每用户 20 条/60s
- **聊天图片**：≤5 MiB，魔数校验（JPEG/PNG/WebP），自动生成 720px 边长缩略图
- 生产部署要求反向代理转发 WebSocket `Upgrade` 头并保留 CONNECT 帧的 `Authorization` 头

### 📝 文本标注系统

基于 @recogito/text-annotator（W3C Web Annotation 标准）：

- 选中文本即出现浮动气泡创建标注；标注存为 `Comment` 实体，采用 `TextQuoteSelector` + `TextPositionSelector` 双选择器（修复单选择器无法解析的历史问题）
- **HighlightManager**：CSS Custom Highlight API 高亮，零 DOM 开销
- **BorderLayer**：左侧色条标记被标注段落（ResizeObserver + rAF）
- **AnnotationPanel**：侧栏列出当前文件全部标注，点击跳转

### ✏️ 文章编辑

`POST /api/articles/{id}/edit`（multipart）支持三个独立维度：元数据（标题/版本/描述/标签）、替换 README、替换封面图（旧缩略图自动删除并重新生成）。每日编辑上限 2 次，前端 `EditPage.vue` 仅文章所有者可访问。

### 👍 投票 / ❤️ 收藏 / 👥 关注

- **投票**：顶/踩切换逻辑（再点取消、反向切换、新增创建），计数原子更新，限流 30 次/60s/IP
- **收藏**：`/api/articles/{id}/favorite` 切换收藏，`/favorites` 分页查看
- **关注**：`/api/users/{id}/follow` 切换关注，`/follows` 分页查看；未登录时 `isFollowing` 返回 false

### 🛡️ 管理后台（`/admin`）

仪表盘、用户管理、文章管理、公告管理、游戏管理（含合并）、失败存档清理、幽灵文件诊断（存储孤儿文件扫描）、站点设置（如首页背景图）、联系留言处理。所有管理操作写入审计日志（`AdminAuditLog`）。

---

## 🧪 测试

后端共 27 个测试类（JUnit 5 + Spring Boot Test），覆盖认证锁定、评论、清理调度、文件浏览、标签、存储与路径防护、魔数校验、XSS 过滤、ZIP/7z 解压、私信并发、长轮询、收藏/关注切换、头像裁剪、安全分级等。**测试需要 MySQL + Meilisearch 运行**：

```bash
cd Backend/GameSaves
./mvnw test                    # 全部测试
./mvnw test -Dtest=ClassName   # 单个测试类
```

> 前端暂未配置测试框架（无 vitest / jest / cypress）。

---

## 🚀 部署（GitHub Actions）

`.github/workflows/deploy.yml`：每次推送到 `main` 自动部署，按路径过滤分别触发前后端任务。目标：腾讯云服务器 `ubuntu@118.25.51.239`，需配置 `SERVER_SSH_KEY` 密钥。

- **后端任务**（并发互斥）：Java 17 → `mvn clean package`（跳过测试）→ scp JAR 到服务器 → `systemctl restart gamesaving` → 健康检查
- **前端任务**：Node 20 → `npm ci && npm run build` → 上传 `dist` → 原子切换目录 → `nginx -t && systemctl reload nginx`

---

## ⚠️ 已知限制

- 未引入完整 Spring Security，仅 Sa-Token 做认证
- Meilisearch 需单独安装运行，搜索功能依赖其可用
- RAR 压缩包仅识别、不解析（支持 ZIP / 7z / tar / tar.gz）
- JWT 无状态，服务端无法提前吊销令牌，退出登录仅清理客户端状态
- 无 Redis / 分布式缓存 — 验证码、限流计数、下载令牌、站点设置均为内存 `ConcurrentHashMap`，不支持多实例部署
- 无 i18n，全部 UI 为中文硬编码
- 无错误监控 / 埋点分析
- CORS 默认 `*`，生产环境需配置具体域名
- `Database/` 目录（上传文件）已 gitignore，不属于仓库内容

---

## 📄 说明

个人项目，仅供学习交流。如对项目感兴趣或发现问题，欢迎提交 Issue 或 PR。
