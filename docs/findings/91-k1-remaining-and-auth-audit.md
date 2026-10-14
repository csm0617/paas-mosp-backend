# 认证要求审计 + **K1 剩余端点清单**（第 85 轮）

**证据等级**：〔代码事实〕（两侧逐端点对照）。2026-10-14。本轮无业务代码改动。

## 1. 为什么要做这次审计

第 84 轮的教训是"401 先查自己"（我把 token 从 Cookie 发成了 Header）。顺着这个方向做了一次**系统审计**：
**厂商哪些端点要求会话/管理员？我们的实现是否对齐？**

厂商侧需要 `getCurrentUser(req)` 的只有三个控制器（`getCurrentUser` 读 **Cookie**）：

| 控制器 | 端点 | 是否要管理员 |
| --- | --- | --- |
| `RoleController` | `GET /v1/role/software` | 否（仅需会话） |
| `RoleController` | `GET /v1/role/{roleId}/software` | **是** |
| `RoleController` | `PUT /v1/role/{roleId}/software` | **是** |
| `UserController` | `POST /v1/user/logout` | **是** |
| `UserController` | `GET /v1/user/{userId}` | 否 |
| `UserController` | `DELETE /v1/user/{userId}` | 否 |
| `PublicController` | `GET/POST /v1/pub/user`（两个） | 否 |
| `PublicController` | `GET /v1/pub/get_user_status` | 否 |

**服务域全部端点都不需要会话**（我们 `ServiceController` 里那句"原型尚未实现认证"是对的——
厂商的服务端点确实不调 `getCurrentUser`）。⇒ 之前"服务域没有认证"不是缺口。

## 2. 发现一处**行为差异**（代码事实，未对拉）

`GET /v1/role/software`（当前会话的战位软件列表）：

- 厂商：`getCurrentUser(req)` → **没有 Cookie 就抛 `UnauthorizedException`**（401）；
- 我们：没有会话时**返回 `{list:[],total:0}`**（不报错）。

⇒ 在"无 Cookie"这一种输入下两侧行为不同。**影响很低**（前端总是带 Cookie），但属于契约差异，
已记入 `docs/deviations.md` 待定。**要不要改**取决于是否坚持"任何输入都逐字节一致"——
按本会话的门槛（兼容是门槛）**应当改**，下次动 K1 时一起处理。

## 3. **K1（用户/战位域）剩余端点清单**

厂商 `UserController` 有、我们**没有**的端点：

| 端点 | 性质 |
| --- | --- |
| `GET /v1/user/list` | 读（分页/过滤待读） |
| `GET /v1/user/redirect` | 读（跳转地址） |
| `POST /v1/user/recovery` | **文件上传**（恢复） |
| `GET /v1/user/backup` | **文件下载**（备份） |
| `GET /v1/user/export` | **文件下载**（导出） |
| `GET /v1/user/import/template` | **文件下载**（导入模板） |
| `POST /v1/user/import` | **文件上传**（导入） |
| `POST /v1/user/reset_user` | **破坏性**（重置用户） |
| `GET /v1/user/access_permission` | 读（权限位） |
| `POST /v1/user/update_avatar/{userId}` | **文件上传**（头像） |
| `GET /v1/user/tenx_user` | 读（TenX 用户，依赖外部） |

⇒ K1 剩下的**主要是文件传输类**（import/export/backup/recovery/template/avatar）与外部依赖（tenx_user）。
文件类端点值得单独一轮：它们牵动 `g_upload_store_record`（表已存在）与 `gaop-fdfs` 之类的存储设施。

## 4. 状态

本轮无业务代码改动；全量 **285 条用例全绿**；无残留资源。
