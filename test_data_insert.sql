-- ============================================================
-- 测试数据插入 - Minecraft存档: 58K一桶岩浆生炸刷石机
-- 用户: player_one (id=2)
-- 游戏: Minecraft (id=2)
-- 物理存储: Database/2/2/1/
--   ├── archive.zip
--   ├── extracted/     (游戏存档文件, content-addressed)
--   └── readme/        (README文档)
--       ├── README.md
--       └── images/    (README引用的图片)
-- ============================================================

USE gamesaving;

-- 1. 插入存档文章 (article)
INSERT INTO article (title, version, game_id, user_id, description, readme_raw, readme_content, storage_root, zip_filename, file_size, download_count, status)
VALUES (
  '58K一桶岩浆生炸刷石机',
  '1.21.10-Fabric 0.18.0',
  2,
  2,
  '基于58K一桶岩浆的无限生炸刷石机存档。适用于Minecraft 1.21.10版本，使用Fabric 0.18.0模组加载器。该存档包含完整的红石刷石机装置，可高效生产石料资源，适合原版生存玩法。',
  '# 58K一桶岩浆生炸刷石机\n\n## 存档信息\n- **游戏版本**: Minecraft 1.21.10\n- **模组加载器**: Fabric 0.18.0\n- **玩法类型**: 原版生存\n\n## 存档说明\n基于58K一桶岩浆的无限生炸刷石机存档。该存档包含：\n\n1. **无限岩浆源** - 使用58K桶岩浆实现\n2. **自动刷石机** - 高效TNT复制刷石装置\n3. **全自动收集系统** - 自动收集石料资源\n\n## 使用方法\n1. 下载并解压存档\n2. 放入 `.minecraft/saves/` 目录\n3. 使用 Fabric 0.18.0 启动游戏\n4. 加载存档即可体验\n\n## 注意事项\n- 请使用对应版本加载，跨版本可能导致存档损坏\n- 建议备份原存档后再替换',
  '<h1>58K一桶岩浆生炸刷石机</h1><h2>存档信息</h2><ul><li><strong>游戏版本</strong>: Minecraft 1.21.10</li><li><strong>模组加载器</strong>: Fabric 0.18.0</li><li><strong>玩法类型</strong>: 原版生存</li></ul><h2>存档说明</h2><p>基于58K一桶岩浆的无限生炸刷石机存档。该存档包含无限岩浆源、自动刷石机和全自动收集系统。</p><h2>使用方法</h2><ol><li>下载并解压存档</li><li>放入 <code>.minecraft/saves/</code> 目录</li><li>使用 Fabric 0.18.0 启动游戏</li><li>加载存档即可体验</li></ol><h2>注意事项</h2><ul><li>请使用对应版本加载，跨版本可能导致存档损坏</li><li>建议备份原存档后再替换</li></ul>',
  'Database/2/2/1/',
  'archive.zip',
  1414268,
  0,
  'READY'
);

SET @article_id = LAST_INSERT_ID();
SELECT @article_id AS 'Article ID';

-- 2. 插入存储快照 (savings)
-- Steam/Epic风格混合存储：记录ZIP和解压根目录位置 + 文件哈希索引
INSERT INTO savings (article_id, user_id, game_id, zip_path, extract_root, zip_hash, file_manifest_hash, file_count, total_size)
VALUES (
  @article_id,
  2,
  2,
  'Database/2/2/1/archive.zip',
  'Database/2/2/1/extracted/',
  'f4b5fa2334df1b47f1ba1e20343575d0c4cfd92b9cad4b28ee4ecd49dbdfd7d3',
  '91b665ab95b64baf383fa9a7e3ecfd92',
  50,
  13047574
);

SET @snapshot_id = LAST_INSERT_ID();
SELECT @snapshot_id AS 'Snapshot ID';

-- 3. 插入文件条目 (saving_items)
-- 包含 parent_path 和 is_directory 字段（GitHub风格目录浏览的核心）
-- virtual_path → physical_key 映射（{md5}.{ext}格式，保留后缀确保文件可预览）
-- ★ UNIQUE(snapshot_id, virtual_path)，不含 user_id

-- 3.0 目录节点（is_directory=1，用于前端渲染文件夹图标和面包屑导航）
INSERT INTO saving_items (snapshot_id, virtual_path, physical_key, file_size, md5_hash, file_type, is_text, parent_path, is_directory) VALUES
-- 根级目录（parent_path=''）
(@snapshot_id, 'advancements/', '', 0, '', NULL, 0, '', 1),
(@snapshot_id, 'data/',         '', 0, '', NULL, 0, '', 1),
(@snapshot_id, 'DIM-1/',        '', 0, '', NULL, 0, '', 1),
(@snapshot_id, 'DIM1/',         '', 0, '', NULL, 0, '', 1),
(@snapshot_id, 'entities/',     '', 0, '', NULL, 0, '', 1),
(@snapshot_id, 'playerdata/',   '', 0, '', NULL, 0, '', 1),
(@snapshot_id, 'poi/',          '', 0, '', NULL, 0, '', 1),
(@snapshot_id, 'region/',       '', 0, '', NULL, 0, '', 1),
(@snapshot_id, 'stats/',        '', 0, '', NULL, 0, '', 1),
-- 二级目录
(@snapshot_id, 'DIM-1/data/',   '', 0, '', NULL, 0, 'DIM-1/', 1),
(@snapshot_id, 'DIM1/data/',    '', 0, '', NULL, 0, 'DIM1/', 1);

-- 3.1 根目录级文件（parent_path='', is_directory=0）
INSERT INTO saving_items (snapshot_id, virtual_path, physical_key, file_size, md5_hash, file_type, is_text, parent_path, is_directory) VALUES
(@snapshot_id, 'carpet.conf',      'cc9f186f01aba2e5e8e9a9ede2938cff.conf',      58,    '9018a513c2b092dd11b601acdaffda59', 'conf', 1, '', 0),
(@snapshot_id, 'icon.png',         'cc35d4b8e796c869289176c383aa3da3.png',       5306,  'c23baadd9f2bee2fcb0bd9aa90c7df8c', 'png',  0, '', 0),
(@snapshot_id, 'level.dat',        '464cd68b16faf2a6e37f92acfdce6397.dat',       3238,  'f151bbeed962699d3fe0f8cee4cc86fe', 'dat',  1, '', 0),
(@snapshot_id, 'level.dat_old',    '6ac008f6811a861637bb0520cace9f45.dat_old',   3238,  '09fd2a97b2f4f6d41c8e476bf6b64ae1', 'dat_old', 0, '', 0),
(@snapshot_id, 'session.lock',     'd96f2ae19d4870178d6928cc34e00aa2.lock',      3,     '48856faf4534a876adeadc72aec53cb2', 'lock', 1, '', 0),
(@snapshot_id, 'skyland.ojng',     '39c1aad2c0a0b73e88b8c7cf71401945.ojng',      0,     'd41d8cd98f00b204e9800998ecf8427e', 'ojng', 0, '', 0),
(@snapshot_id, 'xaeromap.txt',     'bdeb25e7dfbb77e0357ac9597cdec479.txt',       13,    '3438202c734621618c06e69bf3b3d074', 'txt',  1, '', 0);

-- 3.2 advancements/ 目录文件（parent_path='advancements/'）
INSERT INTO saving_items (snapshot_id, virtual_path, physical_key, file_size, md5_hash, file_type, is_text, parent_path, is_directory) VALUES
(@snapshot_id, 'advancements/02cfc44a-a7ff-4ada-94e9-23d9fee52e68.json', '74f39c99253f387ce664e110c05f25ef.json', 14433, '8a3de5fe6794138400bc96753a7e1b6a', 'json', 1, 'advancements/', 0),
(@snapshot_id, 'advancements/6b3e8100-0195-4994-bd26-bdb99bb446ea.json', '563451266e353a460afd7c2813beb02e.json', 2148,  'a0cfcbeafee1e73e71cba3e937e90426', 'json', 1, 'advancements/', 0);

-- 3.3 data/ 目录文件（parent_path='data/'）
INSERT INTO saving_items (snapshot_id, virtual_path, physical_key, file_size, md5_hash, file_type, is_text, parent_path, is_directory) VALUES
(@snapshot_id, 'data/chunks.dat',          'c10a7692728123b97cc5430ce4e05305.dat', 48,  'b4ebcdea33bb478da6800bf9038252f8', 'dat', 1, 'data/', 0),
(@snapshot_id, 'data/raids.dat',           '254c5c0c2ee1d5edfac9964f58f35aba.dat', 74,  '5e5a9bcfdf05bcb576d28c93a6658926', 'dat', 1, 'data/', 0),
(@snapshot_id, 'data/random_sequences.dat','cf9acabd8c3bf09159d6a951668b2de3.dat', 539, '7271b330075f68209fc3cb9850141fdf', 'dat', 1, 'data/', 0),
(@snapshot_id, 'data/scoreboard.dat',      '30baa63bdfe1b050c88078f8d9a9b0a0.dat', 48,  'b4ebcdea33bb478da6800bf9038252f8', 'dat', 1, 'data/', 0),
(@snapshot_id, 'data/world_border.dat',    '8cec9c85b001ec47e8c01b550363ba6b.dat', 167, '662285834a22436842c5d8ce7ecd45bb', 'dat', 1, 'data/', 0);

-- 3.4 DIM-1/data/ 目录文件（parent_path='DIM-1/data/'）
INSERT INTO saving_items (snapshot_id, virtual_path, physical_key, file_size, md5_hash, file_type, is_text, parent_path, is_directory) VALUES
(@snapshot_id, 'DIM-1/data/chunks.dat',       '209af0ef23ccc5def94f47cc87e43240.dat', 48,  'b4ebcdea33bb478da6800bf9038252f8', 'dat', 1, 'DIM-1/data/', 0),
(@snapshot_id, 'DIM-1/data/raids.dat',        'f4d6f6d72175f3efc735e0b0f882f085.dat', 74,  '5e5a9bcfdf05bcb576d28c93a6658926', 'dat', 1, 'DIM-1/data/', 0),
(@snapshot_id, 'DIM-1/data/world_border.dat', 'be983c22f23e3eacf856178bbd610afe.dat', 167, '662285834a22436842c5d8ce7ecd45bb', 'dat', 1, 'DIM-1/data/', 0);

-- 3.5 DIM1/data/ 目录文件（parent_path='DIM1/data/'）
INSERT INTO saving_items (snapshot_id, virtual_path, physical_key, file_size, md5_hash, file_type, is_text, parent_path, is_directory) VALUES
(@snapshot_id, 'DIM1/data/chunks.dat',       '92797c62d49171f48a53512131122a50.dat', 48,  'b4ebcdea33bb478da6800bf9038252f8', 'dat', 1, 'DIM1/data/', 0),
(@snapshot_id, 'DIM1/data/raids_end.dat',    '48075435f891e004f06882612dec52e1.dat', 74,  '5e5a9bcfdf05bcb576d28c93a6658926', 'dat', 1, 'DIM1/data/', 0),
(@snapshot_id, 'DIM1/data/world_border.dat', '6c0ceeafbcd462d08b4e3b9f0acdd3d3.dat', 167, '662285834a22436842c5d8ce7ecd45bb', 'dat', 1, 'DIM1/data/', 0);

-- 3.6 entities/ 目录文件（parent_path='entities/'）
INSERT INTO saving_items (snapshot_id, virtual_path, physical_key, file_size, md5_hash, file_type, is_text, parent_path, is_directory) VALUES
(@snapshot_id, 'entities/r.-1.-1.mca', '3e29b0a1ada3915d9d854131b96232b2.mca', 16384,  '846609a60817e0f88af784c3129b6d3d', 'mca', 0, 'entities/', 0),
(@snapshot_id, 'entities/r.-1.0.mca',  'd771937d7f12728125f84cbadfcdbaa7.mca', 73728,  '8b38bb0122560a1fd2d4474c2ce93841', 'mca', 0, 'entities/', 0),
(@snapshot_id, 'entities/r.0.-1.mca',  '83c8c05c8bf4a006657b1cad276000d4.mca', 0,      'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'entities/', 0),
(@snapshot_id, 'entities/r.0.0.mca',   '2b195811367203f81e14f1c384849aed.mca', 12288,  '86036a1c1033a48c56a524e80ccea82a', 'mca', 0, 'entities/', 0);

-- 3.7 playerdata/ 目录文件（parent_path='playerdata/'）
INSERT INTO saving_items (snapshot_id, virtual_path, physical_key, file_size, md5_hash, file_type, is_text, parent_path, is_directory) VALUES
(@snapshot_id, 'playerdata/02cfc44a-a7ff-4ada-94e9-23d9fee52e68.dat',     '8052b40765e4c8cdd83fb4bf0107b567.dat',     1752, '443fd29c3944d8ff013aba4241275c29', 'dat', 1, 'playerdata/', 0),
(@snapshot_id, 'playerdata/02cfc44a-a7ff-4ada-94e9-23d9fee52e68.dat_old', '6ea964ee715ffc62b1ce16e9b0d4c28b.dat_old', 1752, '67067e70c7e527b0c135f8cb22b020ff', 'dat_old', 0, 'playerdata/', 0),
(@snapshot_id, 'playerdata/6b3e8100-0195-4994-bd26-bdb99bb446ea.dat',     '26654177617e2286c4e14ea819242bc1.dat',     1677, 'a05bea20986a933731be4d51fa3125b7', 'dat', 1, 'playerdata/', 0),
(@snapshot_id, 'playerdata/6b3e8100-0195-4994-bd26-bdb99bb446ea.dat_old', 'dc4dba4d9453b5be5a25ed263209ca3f.dat_old', 1677, 'a05bea20986a933731be4d51fa3125b7', 'dat_old', 0, 'playerdata/', 0);

-- 3.8 poi/ 目录文件（parent_path='poi/'）
INSERT INTO saving_items (snapshot_id, virtual_path, physical_key, file_size, md5_hash, file_type, is_text, parent_path, is_directory) VALUES
(@snapshot_id, 'poi/r.-1.-1.mca', '4eb59c0c4aa867742c50ea59b45432b2.mca', 0,     'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'poi/', 0),
(@snapshot_id, 'poi/r.-1.0.mca',  'd3670386c0e078632cd202e4c7341640.mca', 20480, '693f06d065c9cdcdd43d40ccdcc0699d', 'mca', 0, 'poi/', 0),
(@snapshot_id, 'poi/r.0.-1.mca',  'dc9237e4ac4ceb63cd6e84b0dd70d83d.mca', 0,     'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'poi/', 0),
(@snapshot_id, 'poi/r.0.0.mca',   '6e89aa82544e6a11e7cb37d210b8f973.mca', 0,     'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'poi/', 0);

-- 3.9 region/ 目录文件（parent_path='region/'）
INSERT INTO saving_items (snapshot_id, virtual_path, physical_key, file_size, md5_hash, file_type, is_text, parent_path, is_directory) VALUES
(@snapshot_id, 'region/r.-1.-1.mca', 'bdf6636f6ef8de770d50b012f26b098e.mca', 3321856, '857811376cce42f7f12aba4aca138035', 'mca', 0, 'region/', 0),
(@snapshot_id, 'region/r.-1.-2.mca', '6249b82306da9e81290570fe6e3d8606.mca', 0,       'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'region/', 0),
(@snapshot_id, 'region/r.-1.0.mca',  '41b147660a4473b9f0f7c017cc7ab626.mca', 3710976, 'ebbbf0ccd30f4d74b7cebb9bfc3ac1d2', 'mca', 0, 'region/', 0),
(@snapshot_id, 'region/r.-1.1.mca',  '7c6d2fa63b1566d7546bcda895178946.mca', 0,       'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'region/', 0),
(@snapshot_id, 'region/r.-2.-1.mca', 'ded9b7a3b51781abfa3ea874875966c7.mca', 0,       'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'region/', 0),
(@snapshot_id, 'region/r.-2.-2.mca', '83c52d092317af7adc53521e6706020b.mca', 0,       'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'region/', 0),
(@snapshot_id, 'region/r.-2.0.mca',  '6e6a8f6e0aa7233545ec508adc7a739d.mca', 0,       'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'region/', 0),
(@snapshot_id, 'region/r.-2.1.mca',  '2f961bdf47b46afbb7d446102a5d7486.mca', 0,       'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'region/', 0),
(@snapshot_id, 'region/r.0.-1.mca',  'fcd80bd0874f4cad1624d706192641f0.mca', 2777088, 'a372fdcebd68e2fee887cd7ec8e714a5', 'mca', 0, 'region/', 0),
(@snapshot_id, 'region/r.0.-2.mca',  '565fc7f51255df2d81131f1352abf9ec.mca', 0,       'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'region/', 0),
(@snapshot_id, 'region/r.0.0.mca',   'f0583d7255cf51c6e3edfca8b8dc917b.mca', 3076096, '58fe34936b23846776471a6da6a1e68f', 'mca', 0, 'region/', 0),
(@snapshot_id, 'region/r.0.1.mca',   'd005894925bd12008278a4a825be6900.mca', 0,       'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'region/', 0),
(@snapshot_id, 'region/r.1.-1.mca',  '71e1d464103e84f5880c60fdce348e96.mca', 0,       'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'region/', 0),
(@snapshot_id, 'region/r.1.-2.mca',  '02e4f6d223618b59f662154d9d11e06d.mca', 0,       'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'region/', 0),
(@snapshot_id, 'region/r.1.0.mca',   'c7ebb7399290f6b8cfef3f08d8b02e3d.mca', 0,       'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'region/', 0),
(@snapshot_id, 'region/r.1.1.mca',   '10995cfd7930de942bd97034f1026e21.mca', 0,       'd41d8cd98f00b204e9800998ecf8427e', 'mca', 0, 'region/', 0);

-- 3.10 stats/ 目录文件（parent_path='stats/'）
INSERT INTO saving_items (snapshot_id, virtual_path, physical_key, file_size, md5_hash, file_type, is_text, parent_path, is_directory) VALUES
(@snapshot_id, 'stats/02cfc44a-a7ff-4ada-94e9-23d9fee52e68.json', 'cf7f2bd51007fa4b3e70297ff1486419.json', 1675, 'b4408f45e56ce55cc9aad8fbef1dc348', 'json', 1, 'stats/', 0),
(@snapshot_id, 'stats/6b3e8100-0195-4994-bd26-bdb99bb446ea.json', '07321501a44a6a27aac659f1e1e6726f.json', 254,  '0e6a6ec33bb761f11373c4fffd7eda03', 'json', 1, 'stats/', 0);

-- ============================================================
-- 4. 插入示例批注 (comments)
-- 模拟用户对README的批注，演示Word风格协作审阅
-- ============================================================
INSERT INTO comments (article_id, user_id, content, anchor, selected_text, quote_start, quote_end) VALUES
(@article_id, 3,
 '这个刷石机的效率大概是多少？每小时能产多少组石头？建议在README中补充效率数据。',
 '{"exact":"自动刷石机 - 高效TNT复制刷石装置","prefix":"2. ","suffix":"3. **全自动"}',
 '自动刷石机 - 高效TNT复制刷石装置',
 0, 0),
(@article_id, 1,
 '已确认该存档兼容Fabric 0.18.0，请在下载前检查你的模组版本。另外需要注意Mod兼容性问题。',
 '{"exact":"使用 Fabric 0.18.0 启动游戏","prefix":"3. ","suffix":"4. 加载存档"}',
 '使用 Fabric 0.18.0 启动游戏',
 0, 0);

-- ============================================================
-- 验证查询
-- ============================================================
SELECT '=== 插入结果验证 ===' AS info;

SELECT a.id, a.title, a.version, a.status,
       CONCAT(ROUND(a.file_size/1024,1), ' KB') AS zip_size,
       a.storage_root AS storage
FROM article a WHERE a.id = @article_id;

SELECT '---' AS '';

SELECT s.id, s.zip_hash, s.file_count,
       CONCAT(ROUND(s.total_size/1024/1024,2), ' MB') AS extract_size,
       s.extract_root
FROM savings s WHERE s.article_id = @article_id;

SELECT '---' AS '';

SELECT COUNT(*) AS total_files,
       SUM(file_size) AS total_bytes,
       SUM(is_text) AS text_previewable,
       COUNT(*) - SUM(is_text) AS binary_files
FROM saving_items WHERE snapshot_id = @snapshot_id;

SELECT '---' AS '';

SELECT c.id, u.nickname, c.selected_text, c.content
FROM comments c JOIN users u ON c.user_id = u.id
WHERE c.article_id = @article_id;
