-- ============================================================
-- 游戏存档分享平台 (GameSaving) - 数据库设计与实现 v2.0
-- 数据库名称: gamesaving
-- MySQL版本要求: 8.0+
-- 引擎: InnoDB (事务、行级锁、外键约束)
-- 字符集: utf8mb4 (支持emoji及全Unicode字符)
-- 创建日期: 2026-06-18
--
-- 物理存储结构 (类似Steam/Epic的存档存储方式):
--   Database/{user_id}/{game_id}/{article_id}/
--     ├── archive.zip          ← 原始上传压缩包
--     └── extracted/           ← 解压根目录
--           ├── {hash1}        ← 物理文件（以内容哈希命名）
--           ├── {hash2}
--           └── ...
--
-- saving_items 表记录 virtual_path → physical_key 的映射关系
-- ============================================================

-- 如果数据库已存在则重建
DROP DATABASE IF EXISTS gamesaving;
CREATE DATABASE gamesaving
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE gamesaving;

-- ============================================================
-- 1. 用户表 (users)
-- 存储平台注册用户信息，支持管理员/普通用户角色分离
-- 管理员可管理所有存档和用户，普通用户只能管理自己的内容
-- ============================================================
CREATE TABLE users (
    id          BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '用户唯一标识ID',
    username    VARCHAR(50)     NOT NULL                 COMMENT '用户名（登录账号，全局唯一）',
    password    VARCHAR(255)    NOT NULL                 COMMENT 'BCrypt加密后的密码哈希值',
    nickname    VARCHAR(50)     DEFAULT NULL             COMMENT '显示昵称（可重复）',
    phone       VARCHAR(20)     DEFAULT NULL             COMMENT '手机号（可用于登录和找回密码）',
    email       VARCHAR(100)    DEFAULT NULL             COMMENT '用户邮箱',
    -- 角色分离：admin(管理员，可管理全平台) / user(普通用户，仅管理自己内容)
    role        VARCHAR(20)     NOT NULL DEFAULT 'user'  COMMENT '用户角色: admin=管理员, user=普通用户',
    avatar_url  VARCHAR(500)    DEFAULT NULL             COMMENT '用户头像URL',
    bio         VARCHAR(500)    DEFAULT NULL             COMMENT '个人简介/签名',
    -- 登录相关
    last_login  DATETIME        DEFAULT NULL             COMMENT '最后登录时间',
    is_active   TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '账号状态: 0=禁用, 1=正常',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '信息最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),                   -- 用户名全局唯一
    UNIQUE KEY uk_phone (phone),                         -- 手机号全局唯一（允许NULL，NULL不参与唯一约束）
    UNIQUE KEY uk_email (email)                          -- 邮箱全局唯一
) ENGINE=InnoDB COMMENT='用户表 - 区分管理员与普通用户角色，支持手机号登录';

-- ============================================================
-- 2. 游戏表 (games)
-- 游戏主数据，作为存档的归属维度
-- 一款游戏可以有多个用户上传的多个存档
-- ============================================================
CREATE TABLE games (
    id          BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '游戏唯一标识ID',
    name        VARCHAR(100)    NOT NULL                 COMMENT '游戏名称（如：艾尔登法环、Minecraft）',
    cover_url   VARCHAR(500)    DEFAULT NULL             COMMENT '游戏封面图片URL',
    description TEXT            DEFAULT NULL             COMMENT '游戏简介描述',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_game_name (name)                       -- 游戏名唯一
) ENGINE=InnoDB COMMENT='游戏主数据表 - 平台支持的游戏列表';

-- ============================================================
-- 3. 存档文章表 (article) - 核心业务实体
-- 每次存档上传对应一条 article 记录，即为一个"存档文章"
-- 包含标题、版本、README内容等，对外展示为博客式存档页面
--
-- 关系说明:
--   game 1──N article (一款游戏有多个存档)
--   user 1──N article (一个用户可以上传多个存档)
--   article 1──N savings (一个存档对应一条存储记录)
--   article 1──N comments (一个存档有多条批注)
--
-- readme_raw:     从ZIP包中提取的原始Markdown文本
-- readme_content: Markdown渲染后的HTML（以空间换时间）
-- ============================================================
CREATE TABLE article (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '存档文章唯一标识ID',
    title           VARCHAR(200)    NOT NULL                 COMMENT '存档标题（用户自定义，如"全收集一周目存档"）',
    version         VARCHAR(50)     NOT NULL                 COMMENT '存档版本号（如 v1.0.3）',
    game_id         BIGINT          NOT NULL                 COMMENT '关联游戏ID → games.id',
    user_id         BIGINT          NOT NULL                 COMMENT '上传者/作者用户ID → users.id',
    description     TEXT            DEFAULT NULL             COMMENT '存档描述/说明文字',
    -- Markdown双字段存储（任务书§3 数据存储要求）
    readme_raw      LONGTEXT        DEFAULT NULL             COMMENT '原始README.md Markdown内容，供编辑器加载',
    readme_content  LONGTEXT        DEFAULT NULL             COMMENT 'Markdown渲染后的HTML内容（缓存，提升首次展示性能）',
    -- 物理存储路径信息
    storage_root    VARCHAR(500)    NOT NULL                 COMMENT '混合存储根路径，格式: Database/{user_id}/{game_id}/{article_id}/',
    zip_filename    VARCHAR(255)    NOT NULL                 COMMENT '原始ZIP文件名（存储在storage_root下）',
    file_size       BIGINT          NOT NULL DEFAULT 0       COMMENT 'ZIP文件大小（字节，最大200MB）',
    download_count  INT             NOT NULL DEFAULT 0       COMMENT '下载次数统计',
    -- 状态流转: UPLOADING → EXTRACTING → READY / FAILED
    status          VARCHAR(20)     NOT NULL DEFAULT 'UPLOADING'
                                    COMMENT '处理状态: UPLOADING(上传中), EXTRACTING(解压中), READY(就绪), FAILED(失败)',
    error_message   VARCHAR(1000)   DEFAULT NULL             COMMENT '处理失败时的错误详情',
    cover_image     VARCHAR(500)    DEFAULT NULL             COMMENT '封面图相对路径，如 /storage/1/2/42/cover.png',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传/创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id),
    INDEX idx_game_id (game_id),                            -- 按游戏浏览存档
    INDEX idx_user_id (user_id),                            -- 按用户浏览存档
    INDEX idx_game_user (game_id, user_id),                 -- 联合查询：某游戏某用户的所有存档
    INDEX idx_game_status_created (game_id, status, created_at), -- 按游戏+状态浏览存档列表，覆盖 ORDER BY created_at
    INDEX idx_status (status),                              -- 按状态筛选（轮询解压进度）
    INDEX idx_created_at (created_at),                      -- 按时间排序（最新上传）
    CONSTRAINT fk_article_game FOREIGN KEY (game_id) REFERENCES games(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,               -- 游戏有关联存档时不允许删除
    CONSTRAINT fk_article_user FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE                -- 用户有关联存档时不允许删除
) ENGINE=InnoDB COMMENT='存档文章表 - 每次上传对应一篇存档文章，核心业务实体';

-- ============================================================
-- 4. 存档存储快照表 (savings)
-- 采用类似Steam/Epic的混合存储方式
-- 记录压缩包和解压根目录的物理位置 + 文件哈希索引
-- 每条记录代表一次存档上传的完整文件快照
--
-- Steam存储参考: steamapps/common/{GameName}/ 下按游戏组织
-- Epic存储参考: Epic Games/{GameName}/ 下按游戏组织
-- 本项目:      Database/{user_id}/{game_id}/{article_id}/
--
-- zip_path:   原始压缩包的完整存储路径
-- extract_root: ZIP解压后的根目录完整路径
-- file_manifest_hash: 所有文件哈希的聚合哈希，用于校验完整性
-- ============================================================
CREATE TABLE savings (
    id                  BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '快照唯一标识ID',
    article_id          BIGINT          NOT NULL                 COMMENT '所属存档文章ID → article.id',
    user_id             BIGINT          NOT NULL                 COMMENT '上传用户ID（冗余，加速查询）→ users.id',
    game_id             BIGINT          NOT NULL                 COMMENT '关联游戏ID（冗余，加速查询）→ games.id',
    -- 物理存储路径（混合存储核心）
    zip_path            VARCHAR(500)    NOT NULL                 COMMENT 'ZIP压缩包在服务器上的完整路径',
    extract_root        VARCHAR(500)    NOT NULL                 COMMENT 'ZIP解压后的根目录完整路径',
    zip_hash            VARCHAR(64)     NOT NULL                 COMMENT 'ZIP文件的SHA-256哈希值（用于去重和完整性校验）',
    file_manifest_hash  VARCHAR(64)     DEFAULT NULL             COMMENT '所有文件哈希的聚合校验哈希（Merkle树根）',
    -- 统计信息
    file_count          INT             NOT NULL DEFAULT 0       COMMENT '解压后文件总数',
    total_size          BIGINT          NOT NULL DEFAULT 0       COMMENT '解压后文件总大小（字节）',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照创建时间',
    PRIMARY KEY (id),
    INDEX idx_article_id (article_id),                          -- 按存档文章查询快照
    INDEX idx_user_game (user_id, game_id),                     -- 按用户+游戏查询快照
    INDEX idx_zip_hash (zip_hash),                              -- 按ZIP哈希查重
    CONSTRAINT fk_savings_article FOREIGN KEY (article_id) REFERENCES article(id)
        ON DELETE CASCADE ON UPDATE CASCADE,                    -- 删除文章时级联删除快照
    CONSTRAINT fk_savings_user FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_savings_game FOREIGN KEY (game_id) REFERENCES games(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB COMMENT='存档存储快照表 - 混合存储元数据，记录ZIP与解压根目录位置';

-- ============================================================
-- 5. 文件条目表 (saving_items)
-- 记录每个存档快照中的全部文件与目录信息
-- 采用 virtual_path → physical_key 映射模式（类似Git的blob索引）
--
-- virtual_path: 文件/目录在存档内的逻辑路径（如 saves/slot1/auto.save）
-- physical_key: 文件在物理存储中的哈希名（{md5}.{ext}格式，保留后缀）
--               目录节点的 physical_key 为空字符串
-- parent_path:  父目录的 virtual_path（根目录为空字符串''）
--               用于 GitHub 风格目录浏览：WHERE parent_path = 'region/' 直接命中索引
-- is_directory: 0=文件（默认）, 1=目录节点
--               每个目录都有一条 is_directory=1 的记录，前端据此渲染文件夹图标
-- file_size:    文件大小（字节，目录为0）
-- md5_hash:     文件内容的MD5哈希值（32位十六进制，目录为空字符串）
--
-- ★ 索引策略:
--   uk_snapshot_path:       UNIQUE(snapshot_id, virtual_path) — 路径唯一性
--   idx_snapshot_parent_dir: (snapshot_id, parent_path, is_directory) — 目录浏览核心索引
--   ★ 严禁将 user_id 加入任何索引
-- ============================================================
CREATE TABLE saving_items (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '文件/目录条目唯一标识ID',
    snapshot_id     BIGINT          NOT NULL                 COMMENT '所属快照ID → savings.id（外键）',
    virtual_path    VARCHAR(500)    NOT NULL                 COMMENT '文件/目录在存档内的逻辑路径（目录以/结尾，如 region/）',
    physical_key    VARCHAR(255)    NOT NULL DEFAULT ''      COMMENT '物理哈希文件名（{md5}.{ext}格式，目录为空字符串）',
    -- 目录结构支持（GitHub风格文件浏览的核心字段）
    parent_path     VARCHAR(500)    NOT NULL DEFAULT ''      COMMENT '父目录virtual_path（根目录为空字符串），配合索引实现高效目录切换',
    is_directory    TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '0=文件, 1=目录节点（用于前端渲染文件夹图标）',
    -- 文件元数据
    file_size       BIGINT          NOT NULL DEFAULT 0       COMMENT '文件大小（字节，目录为0）',
    md5_hash        VARCHAR(32)     NOT NULL DEFAULT ''      COMMENT '文件内容的MD5哈希值（目录为空字符串）',
    file_type       VARCHAR(50)     DEFAULT NULL             COMMENT '文件扩展名/类型（如 txt, json, png），用于前端预览判断',
    is_text         TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否可文本预览: 0=二进制, 1=可文本预览(txt/json/xml/yaml等)',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (id),
    -- ★ 联合唯一索引：同一快照下不允许重复的虚拟路径
    -- ★ 严禁包含 user_id 字段 —— 路径唯一性仅在快照范围内约束
    UNIQUE KEY uk_snapshot_path (snapshot_id, virtual_path),
    -- 目录浏览核心索引：覆盖 snapshot + parent + is_directory，根目录/子目录查询均type=ref
    INDEX idx_snapshot_parent_dir (snapshot_id, parent_path, is_directory),
    INDEX idx_snapshot_id (snapshot_id),                     -- 按快照检索（与uk左前缀各有适用场景）
    INDEX idx_physical_key (physical_key),                   -- 按物理哈希检索（去重/共享文件）
    INDEX idx_md5 (md5_hash),                                -- 按MD5检索（快速查重）
    CONSTRAINT fk_items_snapshot FOREIGN KEY (snapshot_id) REFERENCES savings(id)
        ON DELETE CASCADE ON UPDATE CASCADE                  -- 删除快照时级联删除其所有文件条目
) ENGINE=InnoDB COMMENT='文件条目表 - 文件+目录节点，virtual_path→physical_key映射，所有位置在此检索';

-- ============================================================
-- 6. 批注表 (comments)
-- 用户对存档文章README的文本批注（Word审阅风格协作）
-- 批注关系链: comment → article → game
--               comment → user（批注者）
--
-- 采用@net7/annotator的TextQuoteSelector锚定策略
-- anchor字段使用JSON格式存储锚定信息（任务书§3 数据存储要求）
-- selected_text存储被选中的原文片段，供侧边栏展示
-- ============================================================
CREATE TABLE comments (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '批注唯一标识ID',
    article_id      BIGINT          NOT NULL                 COMMENT '被批注的存档文章ID → article.id',
    user_id         BIGINT          NOT NULL                 COMMENT '批注者用户ID → users.id',
    -- 通过 article_id → article.game_id 即可关联到游戏，无需冗余 game_id
    content         TEXT            NOT NULL                 COMMENT '批注文本内容（纯文本，存储前需XSS过滤清洗）',
    -- TextQuoteSelector 锚定信息（JSON格式，灵活可扩展）
    -- 示例: {"exact":"选中的原文", "prefix":"前文片段", "suffix":"后文片段", "start":120, "end":180}
    anchor          JSON            NOT NULL                 COMMENT 'TextQuoteSelector锚定数据（JSON），精确还原批注高亮位置',
    selected_text   VARCHAR(1000)   NOT NULL                 COMMENT '被选中的原文片段（用于侧边栏列表展示和快速检索）',
    quote_start     INT             DEFAULT NULL             COMMENT '选中文字在readme_raw中的起始字符偏移量',
    quote_end       INT             DEFAULT NULL             COMMENT '选中文字在readme_raw中的结束字符偏移量',
    parent_id       BIGINT          DEFAULT NULL             COMMENT '父批注ID → comments.id（支持一级回复嵌套）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '批注创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '批注最后更新时间',
    PRIMARY KEY (id),
    INDEX idx_article_created (article_id, created_at),      -- 按存档查询批注并按时间排序（侧边栏，覆盖 ORDER BY created_at）
    INDEX idx_user_id (user_id),                             -- 按用户查询其全部批注
    INDEX idx_article_user (article_id, user_id),            -- 联合索引：某存档中某用户的批注
    INDEX idx_parent_id (parent_id),                         -- 按父批注查询回复列表
    CONSTRAINT fk_comments_article FOREIGN KEY (article_id) REFERENCES article(id)
        ON DELETE CASCADE ON UPDATE CASCADE,                 -- 删除存档文章时级联删除其批注
    CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,                -- 用户有批注时不允许删除账号
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES comments(id)
        ON DELETE CASCADE ON UPDATE CASCADE                  -- 删除父批注时级联删除回复
) ENGINE=InnoDB COMMENT='批注表 - README文本批注，支持TextQuoteSelector锚定与Word风格协作审阅';

-- ============================================================
-- 7. 下载记录表 (download_logs)
-- 审计每次下载行为，支撑IP级别的下载频率限制
-- 与内存ConcurrentHashMap配合构成双层限流：
--   第一层（内存）: 快速限流判断（如每分钟每IP 3次）
--   第二层（数据库）: 持久化审计 + 离线分析
-- ============================================================
CREATE TABLE download_logs (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '下载记录唯一标识ID',
    article_id      BIGINT          NOT NULL                 COMMENT '被下载的存档文章ID → article.id',
    ip_address      VARCHAR(45)     NOT NULL                 COMMENT '下载者IP地址（支持IPv4 15字符和IPv6 45字符）',
    user_agent      VARCHAR(500)    DEFAULT NULL             COMMENT '下载者浏览器User-Agent（审计追溯用）',
    downloaded_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下载时间',
    PRIMARY KEY (id),
    INDEX idx_article_id (article_id),                       -- 按存档统计下载次数
    -- 联合索引：某IP在某个时间段的下载记录（限流核心查询）
    -- 查询示例: SELECT COUNT(*) FROM download_logs
    --           WHERE ip_address = ? AND downloaded_at > DATE_SUB(NOW(), INTERVAL 1 MINUTE)
    INDEX idx_ip_time (ip_address, downloaded_at),
    CONSTRAINT fk_downloads_article FOREIGN KEY (article_id) REFERENCES article(id)
        ON DELETE CASCADE ON UPDATE CASCADE                  -- 删除文章时级联清理下载记录
) ENGINE=InnoDB COMMENT='下载审计日志表 - 支撑IP频率限制策略和下载统计分析';

-- ============================================================
-- 8. 登录失败记录表 (login_fails)
-- 支撑登录安全策略：同一账号连续失败 N 次后锁定 M 分钟
-- ============================================================
CREATE TABLE login_fails (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '记录唯一标识ID',
    username        VARCHAR(50)     NOT NULL                 COMMENT '登录失败的账号名',
    ip_address      VARCHAR(45)     NOT NULL                 COMMENT '登录失败来源IP',
    attempted_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '失败尝试时间',
    PRIMARY KEY (id),
    INDEX idx_username_time (username, attempted_at)          -- 查询某账号最近失败次数
) ENGINE=InnoDB COMMENT='登录失败记录表 - 支撑账号锁定安全策略';

-- ============================================================
-- 9. 标签表 (tags)
-- 全局标签池，区分管理员预设标签和用户自定义标签
-- ============================================================
CREATE TABLE tags (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '标签唯一标识ID',
    name            VARCHAR(50)     NOT NULL                 COMMENT '标签名称（全局唯一）',
    source          VARCHAR(20)     NOT NULL DEFAULT 'user'  COMMENT '标签来源: admin=预设标签, user=用户自定义',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB COMMENT='标签表 - 全局标签池，区分预设标签和用户自定义标签';

-- ============================================================
-- 10. 文章-标签关联表 (article_tags)
-- 多对多关联：一篇文章可关联多个标签，一个标签可用于多篇文章
-- ============================================================
CREATE TABLE article_tags (
    article_id      BIGINT          NOT NULL                 COMMENT '存档文章ID → article.id',
    tag_id          BIGINT          NOT NULL                 COMMENT '标签ID → tags.id',
    PRIMARY KEY (article_id, tag_id),
    CONSTRAINT fk_article_tags_article FOREIGN KEY (article_id) REFERENCES article(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_article_tags_tag FOREIGN KEY (tag_id) REFERENCES tags(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB COMMENT='文章-标签关联表 - 支撑多对多标签分类和搜索';

-- ============================================================
-- 11. 游戏标准结构白名单 (safe_paths)
-- 管理员上传标准存档文件夹结构 ZIP，系统提取路径存入此表，
-- 用于安全颜色标记：在白名单内的可执行文件降级警告，不在的升级警告
-- ============================================================
CREATE TABLE safe_paths (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    game_id         BIGINT          NOT NULL                        COMMENT '游戏ID → games.id',
    path            VARCHAR(500)    NOT NULL                        COMMENT '文件或目录虚拟路径',
    is_directory    TINYINT(1)      NOT NULL DEFAULT 0              COMMENT '是否为目录',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_game_path (game_id, path),
    INDEX idx_game_id (game_id),
    CONSTRAINT fk_safe_paths_game FOREIGN KEY (game_id) REFERENCES games(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB COMMENT='游戏标准结构白名单 - 安全颜色标记基线';

-- ============================================================
-- 12. 插入开发测试数据
-- 方便前后端联调和功能验证
-- ============================================================

-- 12.1 示例游戏
INSERT INTO games (name, description) VALUES
('艾尔登法环', 'FromSoftware开发的动作角色扮演游戏，开放世界魂系巅峰之作'),
('Minecraft', 'Mojang Studios开发的沙盒建造游戏，无限创造可能'),
('塞尔达传说：王国之泪', '任天堂开发的动作冒险游戏，海拉鲁大陆的新篇章'),
('星露谷物语', 'ConcernedApe开发的农场模拟经营游戏'),
('博德之门3', 'Larian Studios开发的CRPG，基于D&D第五版规则');

-- 12.2 示例用户（区分 admin 管理员 与 user 普通用户）
-- 密码均为 "password123" 的BCrypt哈希值（开发测试用，生产环境需更换）
INSERT INTO users (username, password, nickname, phone, email, role, bio) VALUES
('admin',       '$2b$10$ZtCEAHK7COr0OC6KAfQA/eNcEJhtWqIfD2kaXRa4hix9peZFhQ4nu',
 '系统管理员',  '13800000001', 'admin@gamesaving.com',    'admin', '平台超级管理员，拥有所有权限'),
('player_one',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 '玩家一号',    '13800000002', 'player1@example.com',     'user',  '硬核游戏玩家，专注魂系游戏'),
('speedrunner', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 '速通达人',    '13800000003', 'speed@example.com',       'user',  '游戏速通爱好者，追求极限操作');

-- 12.3 示例标签（预设 + 用户）
INSERT INTO tags (name, source) VALUES
('生存模式', 'admin'),
('创造模式', 'admin'),
('红石机器', 'admin'),
('建筑', 'admin'),
('模组整合', 'admin'),
('冒险地图', 'admin'),
('村民交易', 'user'),
('猪灵交易', 'user'),
('全自动农场', 'user');

-- ============================================================
-- 数据库设计完成 v2.1
-- ============================================================
-- 实体关系总结 (ER):
--   game    1──N article    (一款游戏 ← 多个存档文章)
--   user    1──N article    (一个用户 ← 多个存档文章)
--   user    1──N comments   (一个用户 ← 多条批注)
--   article 1──1 savings    (一篇存档 ← 一份存储快照)
--   savings 1──N saving_items (一份快照 ← N个文件+目录条目)
--   article 1──N comments   (一篇存档 ← 多条批注)
--   article 1──N download_logs (一篇存档 ← 多条下载记录)
--   article M──N tags       (存档与标签多对多)
--
-- 混合存储物理布局:
--   Database/
--     └── {user_id}/
--           └── {game_id}/
--                 └── {article_id}/
--                       ├── archive.zip        ← 原始ZIP
--                       └── extracted/         ← 解压根目录
--                             ├── {hash1}.dat  ← 物理文件({md5}.{ext}格式)
--                             ├── {hash2}.mca
--                             └── ...
--
-- 核心查询模式:
--   -- GitHub风格：根目录文件列表（type=ref, rows=精确命中）
--   SELECT * FROM saving_items
--   WHERE snapshot_id=? AND parent_path='' AND is_directory=0;
--
--   -- GitHub风格：进入子目录（type=ref, 索引直接命中）
--   SELECT * FROM saving_items
--   WHERE snapshot_id=? AND parent_path='region/' AND is_directory=0;
--
--   -- 面包屑子目录列表（type=ref）
--   SELECT virtual_path FROM saving_items
--   WHERE snapshot_id=? AND parent_path='' AND is_directory=1;
--
--   -- 博客风格：加载README（const主键）
--   SELECT readme_content FROM article WHERE id=?;
--
--   -- Word风格：加载批注（idx_article_created消除filesort）
--   SELECT * FROM comments WHERE article_id=? ORDER BY created_at;
-- ============================================================
