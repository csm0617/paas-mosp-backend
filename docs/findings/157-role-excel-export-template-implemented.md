# `/v1/role` Excel 三端点（第 160 轮）

厂商出处：`RoleController.java:263-305`。本轮做完 `POST /v1/role/backup`、`POST /v1/role/export`、
`GET /v1/role/import/template`（`/v1/role/import` 留到下一轮）。

## 1. 规格（逐条照抄点）

| 端点 | 厂商调用链 | 关键行为 |
| --- | --- | --- |
| `POST /backup`、`POST /export` | `RoleController.backUpRole:266` / `exportRole:282` → `RoleService.generateExportExcel:784-830` | 两个方法体**逐字相同**（只有审计 opType 不同）；`isInWhenPresent(roleIdList)` ⇒ **空数组=全量**；`created_at DESC`；**排除 `admin`/`dev`/`user`**；8 列；**不设列宽** |
| `GET /import/template` | `RoleController.roleImportTemplate:297` → `RoleService.generateExportExcelTemplate:740-752` | 8 列表头 + **6** 个示例单元格（`template.role.example.*`） |

**导出 8 列**：名称 / 唯一标识 / 关联应用(JSON) / 关联子角色(JSON) / 描述 / 关联用户(JSON) / 英文名 / 法文名。
三个 JSON 列装的是 **`key`/用户名**（不是 id）：软件列取 `Software::getKey`、子角色列取 `SubRole::getKey`、
用户列取 `User::getUsername`。**列表为空 ⇒ 单元格留空**（厂商只有非空才 `gson.toJson`）。

**英文名/法文名**不是 `g_role` 的列，而是 **`g_setting` 里的自动名**：键
`i18n.role.en_US.<roleKey>` / `i18n.role.fr_FR.<roleKey>`，取不到 ⇒ **null**（厂商
`getAutoNameMessage` 返回 null；本环境除探针外无该键）。新增端口 `ILanguageAutoNamePort`。

**模板与导入器不一致（照抄）**：模板示例行 cell0 取 `template.role.example.key`、cell1 取
`template.role.example.name`；而导入器把 **cell1 当 key、cell0 当 name**。两个键在本环境的值恰好都是
`maintenance`，所以肉眼看不出——**不要"顺手修好"**，否则导入模板与导出文件会互相错位。

## 2. 顺带修掉的一处响应头偏离

`Content-Type` 之前是 `application/vnd.ms-excel;charset=UTF-8`（先 `setCharacterEncoding("UTF-8")`
再 `setContentType`，Tomcat 会把 charset 拼上去），而厂商只有 `application/vnd.ms-excel`。
本轮抽出的 `ExcelResponseWriter` **不再设 characterEncoding**（文件名编码已经由
`filename*=UTF-8''` 显式给出），四个 xls 端点现在都是厂商原样：

| 端点 | Content-Type | Content-Disposition | CORS |
| --- | --- | --- | --- |
| `/v1/role/import/template` | `application/vnd.ms-excel` | `attachment; filename*=UTF-8''席位导入模板.xls` | **无**（厂商 RoleController 没设） |
| `/v1/role/backup`、`/v1/role/export` | 同上 | `...席位名单.xls` | **无** |
| `/v1/sub_role/import/template` | 同上 | `...子角色导入模板.xls` | `Access-Control-Allow-Origin: *` + `Expose-Headers` |
| `/v1/sub_role/export` | 同上 | `...子角色名单.xls` | **无** |

（`SubRoleController` 的私有 `writeExcel` 已删，与 `RoleController` 共用 `ExcelResponseWriter`；
子角色两个端点**回归实测**过，字节与头部与上一轮一致。）

## 3. 离线

- `RoleExportRowsTest` 2 例：`admin`/`dev`/`user` 被过滤、8 列映射、自动名取不到为 null、
  ids 透传、空子角色列表**跳过** `queryByIds`；
- `RoleExcelWriterTest` 2 例：模板 8 表头 + 6 示例单元格、导出 8 列 + 空列表写空串 + **无列宽**。
- 全量 **592** 测试 0 失败（原 588 + 4）。

## 4. 在线（真集群 + 真 MySQL，探针后已还原）

| 例 | 实测 |
| --- | --- |
| `GET /v1/role/import/template` | `200`；`sheet1`；row0 = 8 个译文表头（席位名称…席位名称法文）；row1 = **6** 格 `maintenance \| maintenance \| ["gx-006-wdfwrj-app","gx-022-wlglrj-app"] \| ["subRole01","subRole02"] \| 席位描述 \| ["user1","user2"]`；列宽 2048（默认，未设）；无 CORS 头 |
| `POST /v1/role/backup` `[]` | `200`；文件名 `席位名单.xls`；**只有探针战位一行**（库里的 `admin`/`user` 两个内置战位被排除）⇒ `探针席位 \| probe_role \| (空) \| ["probe_sr2"] \| 探针描述 \| ["admin"] \| ProbeSeat \| (空)`；`ProbeSeat` 证明**自动名确实读的 `g_setting`**，子角色列是 **`key`** 而不是 id，软件列为空 |
| `POST /v1/role/backup` `["probe-role-1"]` | 与全量一致（`isInWhenPresent` 生效） |
| `POST /v1/role/export` `[]` | `200`；头部与 `/backup` 相同；8 列、空列表列写空串 |
| `GET /v1/sub_role/import/template`（回归） | 7 表头 + 4 示例格、CORS 头在、`Content-Type` 已无 charset |

探针收尾：探针战位/子角色/关联/自动名设置全部删除，`g_role` 回到 `admin`+`user` 两行，
`g_sub_role`/`g_sub_role_role`/`g_user_role`/`i18n.role.*` 均为 0/基线；临时 `gaop-redis-tmp`
NodePort 删除、后端进程停止、临时 xls 与 dump 程序清理。

## 5. 顺序上的两处既有偏离（本轮沿用，未新增）

导出的两个 JSON 数组列，厂商的取数语句**都没有 `ORDER BY`**，顺序由 MySQL 决定：

- 关联用户列：我们走 `IRoleDao.selectUsersByRoleId`（`ORDER BY ur.created_at DESC`）；
- 关联子角色列：厂商 `getSubRoleListByRoleId` 用 `HashSet` 去重后 `new ArrayList<>(set)`，
  我们按 id 顺序（见 `findings/28`）。

两者都是"更确定"的方向；本环境数据量下无法逐字节对拉，**不改**，如实记录。

## 6. 覆盖率

| 指标 | 上轮 | 本轮 |
| --- | --- | --- |
| 前端调用面 | 241 | 241 |
| paas-core 已实现 | 113 | **116** |
| 剩余未实现 | 145 | **143** |
| `/v1/role` 簇剩余 | 3 | **1**（只剩 `POST /v1/role/import`） |

## 7. 下一轮

`POST /v1/role/import`（multipart `file` + `isCover`）——本轮已把它的规格读透
（`RoleService.handleImportExcel:836` + `importRoleExcel:852-1124`），要点：
`existKeyName`（key **或** name）命中时 `isCover=false` ⇒ 跳过该行、`true` ⇒ 覆盖（删用户/软件关联、
改 desc、按 JSON 列重写关联，并**自动给相关用户补上 `user` 战位关联**）；未命中 ⇒ 收集成新战位后统一插入。
