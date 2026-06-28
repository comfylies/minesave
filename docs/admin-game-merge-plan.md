# 管理员游戏合并功能 — 实现计划

> 创建日期: 2026-06-27 | 预计工时: 后端 2h + 前端 3h

## 一、目标

管理员后台新增「游戏管理」页面，支持：
- 查看所有游戏（含封面缩略图、存档数、别名数、冲突标记）
- 合并两个重复游戏（文章迁移、别名继承、删除 source）
- 封面取自该游戏下最早存档的封面图（360px 高 WebP）

---

## 二、实现步骤

### Step 1: 后端 — 360px 高封面缩略图

**文件**: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/util/ImageThumbnailService.java`

- 新增方法 `generateThumbnail360h(Path source)` → 生成 `{name}_360h.webp` 到源文件同级目录
- 规格：高 360px，宽按比例，WebP 格式
- 如果已有 `generateReadmeThumbnail`（720w），加一个高度模式的变体即可

**文件**: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/service/impl/ArticleServiceImpl.java`

- 封面图上传后，额外调 `generateThumbnail360h(coverPath)` 生成 `cover_360h.webp`
- 上传到 storage：key 为 `articles/{uid}/{gid}/{aid}/cover_360h.webp`

### Step 2: 后端 — 管理员游戏列表接口增强

**文件**: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/dto/response/AdminGameResponse.java`（新建）

```java
public class AdminGameResponse {
    Long id;
    String name;
    String thumbnailUrl;     // 最早存档的 360h 封面 URL
    Long articleCount;       // READY 存档数
    int aliasCount;          // 别名数
    boolean hasConflict;     // 别名是否与其他 game 冲突
    List<String> conflictGameNames; // 与哪些游戏存在别名冲突
    LocalDateTime createdAt;
}
```

**文件**: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/service/impl/AdminServiceImpl.java`

- 新增方法 `List<AdminGameResponse> listGamesForAdmin()`
- 查询逻辑：
  1. `SELECT * FROM games ORDER BY created_at`
  2. 对每个 game，查 `article` 表取最早的 READY 存档的 `cover_image`
  3. 如果 `cover_image` 存在，构造 thumbnail URL：把 `cover.webp` 替换为 `cover_360h.webp`
  4. 统计存档数、别名数
  5. 冲突检测：查 `game_aliases` 表，找出 `alias_normalized` 同时在多个 game 下出现的 → 标记 `hasConflict=true`

**冲突检测 SQL（核心）**:
```sql
SELECT ga.game_id, ga.alias_normalized,
       GROUP_CONCAT(g.name) AS conflict_with_games
FROM game_aliases ga
JOIN games g ON ga.game_id = g.id
WHERE ga.alias_normalized IN (
    SELECT alias_normalized 
    FROM game_aliases 
    GROUP BY alias_normalized 
    HAVING COUNT(DISTINCT game_id) > 1
)
GROUP BY ga.game_id, ga.alias_normalized;
```

**文件**: `Backend/GameSaves/src/main/java/com/gamesaves/gamesaves/controller/AdminController.java`

- 新增 `GET /api/admin/games` — 返回 `List<AdminGameResponse>`
- 权限：`@SaCheckPermission("user:manage")`
- 合并端点 `POST /api/admin/games/merge` 已在之前实现，直接复用

### Step 3: 后端 — 封面 URL 构造说明

当前 `LocalStorageServiceImpl.generatePresignedUrl()` 返回 `/storage/{userId}/{gameId}/{articleId}/...`。

对于游戏管理列表，直接返回 `/storage/{uid}/{gid}/{aid}/cover_360h.webp` 即可。前端 `<img>` 标签直接加载这个 URL。

如果读不到 360h 缩略图（存量数据），降级用原 `cover_image` 或返回 null。

### Step 4: 前端 — 路由 + 侧边栏入口

**文件**: `Frontend/GameSaves_Fronted/src/router/index.js`

- 新增路由 `/admin/games` → `AdminGames.vue`
- meta: `{ requiresAuth: true, requiresAdmin: true }`

**文件**: 管理员侧边栏（AdminLayout 或类似组件）

- 侧边栏"用户管理"下加一个菜单项：「游戏管理」→ `/admin/games`

### Step 5: 前端 — AdminGames 页面

**文件**: `Frontend/GameSaves_Fronted/src/views/admin/AdminGames.vue`（新建）

页面结构：

```
┌──────────────────────────────────────────────────────┐
│  游戏管理                                            │
│                                                      │
│  [搜索框]                          [只看冲突 ⬜]      │
│                                                      │
│  ┌──────────────────────────────────────────────────┐│
│  │ 封面 │ 游戏名      │ 存档 │ 别名 │ 冲突 │ 操作    ││
│  ├──────────────────────────────────────────────────┤│
│  │ 🖼️  │ Minecraft   │  42 │  4  │ ⚠1  │ 合并    ││
│  │ 🖼️  │ 艾尔登法环   │   5 │  2  │  -  │ 合并    ││
│  │ 🖼️  │ GTA         │   2 │  0  │  -  │ 合并    ││
│  │ 🖼️  │ 侠盗猎车手   │   0 │  0  │  -  │ 合并    ││
│  │ —   │ MC          │   0 │  0  │  -  │ 合并    ││
│  └──────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────┘
```

**表格列设计**:
| 列 | 宽度 | 说明 |
|---|---|---|
| 封面 | 120px | `<img height="180">` 或占位图标 |
| 游戏名 | auto | 粗体，可点击跳转到游戏页 |
| 存档数 | 80px | `articleCount` |
| 别名数 | 80px | `aliasCount` |
| 冲突 | 80px | `⚠ {n}` 红色标签，无冲突显示 `-` |
| 操作 | 120px | 「合并」按钮 |

**搜索**：前端过滤 `name.toLowerCase().includes(query)`，数据量小不需要后端搜索。

**冲突筛选**：复选框勾选后只显示 `hasConflict=true` 的行。

### Step 6: 前端 — 合并弹窗

点击"合并"按钮 → 打开 `<el-dialog>`：

```
┌────────────────────────────────────────────┐
│  合并游戏                                   │
│                                            │
│  源游戏（将被删除）  →  目标游戏（保留）      │
│                                            │
│  ┌──────────────┐    ┌──────────────┐      │
│  │              │    │              │      │
│  │   🖼️ 360h   │    │   🖼️ 360h   │      │
│  │              │    │              │      │
│  │  侠盗猎车手   │    │    GTA       │      │
│  │  2 个存档    │    │   0 个存档    │      │
│  │  别名: gta   │    │              │      │
│  └──────────────┘    └──────────────┘      │
│                                            │
│  合并后：                                   │
│  • 侠盗猎车手 → GTA 的别名                  │
│  • 2 篇文章迁移到 GTA                       │
│  • 侠盗猎车手 游戏记录删除                   │
│                                            │
│  [取消]                  [确认合并]          │
└────────────────────────────────────────────┘
```

**弹窗交互逻辑**:
1. 用户点某行的「合并」→ 弹窗打开，该行游戏作为 source（左侧）
2. 弹窗内有一个下拉选择框选 target 游戏（可搜索，默认选存档数最多的游戏）
3. 左侧显示 source 封面 / 别名 / 存档数
4. 右侧显示 target 封面 / 别名 / 存档数
5. 确认 → `POST /api/admin/games/merge?sourceId=X&targetId=Y`
6. 成功后刷新列表，提示"已合并"

**API 调用**: 
```javascript
import { adminApi } from '../api/adminApi'

// adminApi 新增:
mergeGames(sourceId, targetId) {
  return client.post('/admin/games/merge', null, { 
    params: { sourceId, targetId } 
  })
}

listGamesForAdmin() {
  return client.get('/admin/games')
}
```

### Step 7: 前端 — API 扩展

**文件**: `Frontend/GameSaves_Fronted/src/api/adminApi.js`

新增两个方法：
- `getGames()` → `GET /api/admin/games`
- `mergeGames(sourceId, targetId)` → `POST /api/admin/games/merge?sourceId=&targetId=`

---

## 三、数据流总览

```
┌─────────────┐     GET /api/admin/games     ┌──────────────────┐
│ AdminGames  │ ───────────────────────────→ │ AdminController  │
│   .vue      │ ←──── List<AdminGameResponse> │                  │
└──────┬──────┘                              └────────┬─────────┘
       │                                              │
       │ 用户点"合并"                                   │
       │ POST /api/admin/games/merge                   │
       │ ?sourceId=7&targetId=2                        │
       ▼                                              ▼
┌─────────────┐                              ┌──────────────────┐
│ 合并弹窗     │                              │ GameServiceImpl  │
│ 确认 →      │                              │ .mergeGames()    │
│ 列表刷新     │                              │                  │
└─────────────┘                              └──────────────────┘
```

---

## 四、存量数据兼容

- **无封面的游戏**（0 个存档）：thumbnail 返回 null，前端显示占位图
- **有存档但无 360h 缩略图**（存量 article）：返回原 cover_image 路径，前端 CSS 限高 360px
- **合并前验证**：sourceId != targetId，两个 ID 都存在，否则返回 400

---

## 五、文件变更清单

| 层 | 文件 | 操作 |
|---|---|---|
| 后端 | `ImageThumbnailService.java` | 新增 `generateThumbnail360h()` |
| 后端 | `ArticleServiceImpl.java` | 上传封面后调 360h 生成 |
| 后端 | `AdminGameResponse.java` | 新建 DTO |
| 后端 | `AdminServiceImpl.java` | 新增 `listGamesForAdmin()` |
| 后端 | `AdminController.java` | 新增 `GET /api/admin/games` |
| 前端 | `adminApi.js` | 新增 `getGames()`、`mergeGames()` |
| 前端 | `AdminGames.vue` | 新建页面 |
| 前端 | `router/index.js` | 新增 `/admin/games` 路由 |
| 前端 | 侧边栏组件 | 新增菜单入口 |

---

## 六、注意事项

1. **封面图路径**：`LocalStorageServiceImpl.getPublicUrl()` 返回 `/storage/` 路径，前端直接用。COS 模式同理
2. **合并端点已有**：`POST /api/admin/games/merge` 已在前面的迭代中实现，不需要重写
3. **冲突标记只为辅助**：管理员最终靠视觉判断（封面 + 名字），冲突标记只是提醒"这里可能有重复"
4. **不需要排序后端**：游戏数 <100，前端做搜索和筛选即可
5. **360h 缩略图**：只在新的文章上传时生成；存量文章无 360h 缩略图时降级到原封面
