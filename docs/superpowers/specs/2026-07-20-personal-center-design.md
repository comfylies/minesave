# 个人中心设计

## 目标

复用现有 `/users/:userId` 个人主页：用户访问自己的页面时显示为“个人中心”，并在同一页面提供资料编辑、头像管理和账户安全操作；访问其他用户时维持只读个人主页与原有存档列表。

## 页面与权限

- 不新增 `/settings` 路由。导航栏的个人入口继续前往 `/users/{当前用户ID}`。
- `isSelf` 为真时：标题显示“个人中心”，昵称右侧显示“编辑个人资料”，并显示账户安全卡片。
- `isSelf` 为假时：不显示编辑入口、邮箱/手机号和账户安全内容；原有个人资料与存档浏览保持不变。
- 资料更新不再接受任意路径 ID。`PATCH /api/account/profile` 从 Sa-Token 读取当前用户，彻底消除 `PUT /api/users/{id}` 的越权修改问题。

## 后端接口

| 接口 | 用途 | 关键校验 |
| --- | --- | --- |
| `PATCH /api/account/profile` | 更新昵称、简介、手机号 | 仅当前登录用户；Bean Validation；XSS 清洗；手机号唯一 |
| `POST /api/account/avatar` | 上传/替换头像 | 仅当前登录用户；JPEG/PNG/WebP；≤ 2 MiB；ImageIO 实际解码；对象存储 |
| `DELETE /api/account/avatar` | 恢复默认头像 | 仅当前登录用户；删除受管对象 |
| `PUT /api/account/password` | 修改密码 | 当前密码、6–100 位新密码、确认密码一致；BCrypt |
| `POST /api/account/email-code` | 向新邮箱发送变更验证码 | 当前密码、图形验证码、邮箱唯一、独立用途验证码 |
| `PUT /api/account/email` | 确认新邮箱 | 当前密码、一次性变更验证码、邮箱唯一 |

原有 `GET /api/users/{id}` 保持公开只读；删除未使用的 `GET /api/users` 与前端 `userApi.login/register`。原 `PUT /api/users/{id}` 下线而非保留兼容入口，以免再次形成越权面。

## 存储与数据

- 头像对象键为 `avatars/{userId}/{uuid}.{ext}`，继续使用 `StorageService`，不能直接访问本地文件系统。
- 新增 `users.avatar_key` 存储可删除的对象键；`avatar_url` 保留为旧数据回退。响应优先通过 `avatarKey` 解析为可访问 URL。
- 新增用户专用 `AvatarService`：校验请求、解码图片、缩放成最大 512×512 的 JPEG、写入存储并在数据库保存成功后删除旧对象。
- 验证码缓存以 `用途 + 邮箱` 为键；注册、邮箱登录、改邮箱不能互相消费验证码。

## 失败处理与体验

- 头像上传失败时不修改用户记录；数据库更新失败时删除新对象；旧头像删除失败只记录告警。
- 资料保存后同步 Pinia `auth.currentUser` 与 `localStorage`，导航栏头像和昵称立即刷新。
- 密码或邮箱变更失败保留表单内容并显示后端业务错误；成功后关闭弹窗并刷新当前用户资料。

## 验证范围

- 后端：越权接口不存在、资料校验/清洗、密码校验、邮箱验证码用途隔离、头像格式和替换清理。
- 前端：本人/他人页面分支、编辑入口、资料保存后的认证状态同步、头像上传限制与安全表单校验。
- 构建：Maven compile 与前端生产构建。
