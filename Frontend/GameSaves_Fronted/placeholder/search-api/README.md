# 游戏搜索/排序接口（后端待补充）

## 当前前端实现

前端暂时使用纯客户端逻辑实现搜索和排序：
- 搜索：加载全量游戏列表后，用 `Array.filter()` 按名称和描述过滤
- 排序：用 `Array.sort()` 按名称、更新时间、存档数量排序

## 需要的后端接口

```
GET /api/games?search={keyword}&sort={name|articles|updated}&order={asc|desc}
```

### 参数说明
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| search | string | 否 | 搜索关键词，匹配游戏名称和描述 |
| sort | string | 否 | 排序字段：name(名称), articles(存档数量), updated(更新时间) |
| order | string | 否 | 排序方向：asc(升序), desc(降序)，默认 asc |
| page | int | 否 | 页码，默认 0 |
| size | int | 否 | 每页大小，默认 20 |

### 响应示例
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [...],
    "page": 0,
    "size": 20,
    "totalElements": 5,
    "totalPages": 1
  }
}
```

### 说明
- 当前 GET /api/games 返回全量列表（无分页/搜索参数），前端先临时使用客户端过滤
- 游戏数量增多后（>100），需要后端支持搜索+分页以提升性能
- 排序中的 articles 字段需要后端在查询时 JOIN article 表统计每个游戏的存档数量
