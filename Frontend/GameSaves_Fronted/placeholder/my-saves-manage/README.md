# 用户存档批量管理接口（后端待补充）

## 当前前端实现

前端使用已有的单条操作接口实现存档管理：
- 列表：`GET /api/articles/user/{userId}?page=0&size=20`（已有）
- 编辑：`PUT /api/articles/{id}`（已有）
- 删除：`DELETE /api/articles/{id}`（已有）

## 需要的后端接口

### 1. 批量删除存档
```
DELETE /api/articles/batch
Body: { "ids": [1, 2, 3] }
```

### 2. 按状态筛选存档列表
```
GET /api/articles/user/{userId}?status=READY&page=0&size=20
```

### 3. 批量状态查询（一次查询多个存档状态）
```
GET /api/articles/statuses?ids=1,2,3
Response: { "1": "READY", "2": "EXTRACTING", "3": "FAILED" }
```

### 说明
- 当前前端逐条删除/编辑，操作体验可接受
- 批量删除/状态筛选为可选优化，非紧急需求
- 状态筛选在前端已可用 `Array.filter()` 替代（全量数据量小时）
