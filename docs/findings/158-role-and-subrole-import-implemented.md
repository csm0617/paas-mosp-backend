# 战位/子角色 Excel 导入（第 161 轮）

厂商出处：`RoleController:320` → `RoleService.handleImportExcel:836` + `importRoleExcel:852-1124`；
`SubRoleController:136` → `SubRoleServiceImpl.handleImportExcel:521` + `importSubroleExcel:583-625`。
本轮把 `/v1/role` 簇清零（只剩的 `POST /v1/role/import` 做完了），并顺带做完 `/v1/sub_role/import`。

## 1. 共同骨架

两个导入都是三步（照抄）：

1. 控制器 **MIME 探测**（Tika，不是看扩展名）不是 Excel ⇒ 400 `global.import.supported.file.type`；
2. POI 解析第 0 个 sheet，`lastRowNum < 1`（只有表头/空表）⇒ **只记日志 + 返回成功**（200、`data:null`、库无变化）；
3. 逐行判定 → 写库。**都没有事务**（厂商 `handleImportExcel` 上没有 `@Transactional`），中途报错会留下半写状态。

两个导入**都有**"长度检查把空串拦掉 ⇒ 后面那句 `isEmpty` 判空永远不可达"的死代码（照抄保留顺序）：

| 导入 | 不可达的错误键 |
| --- | --- |
| 战位 | `role.import.role.key.not.valid`（先被 `role.key.is.illegal` / `role.name.is.illegal` 拦下） |
| 子角色 | `subrole.import.key.not.valid`（先被 `subrole.name.is.illegal` / `subrole.key.is.illegal` 拦下） |

## 2. 战位导入（8 列）

| 列 | 语义 |
| --- | --- |
| 0/1 | 名称 / 唯一标识（**cell1 才是 key**，cell0 是 name） |
| 2/3/5 | 关联应用(JSON，装软件 **`key`**) / 关联子角色(JSON，装子角色 **`key`**) / 关联用户(JSON，装**用户名**) |
| 4 | 描述（读不到 ⇒ null） |
| 6/7 | 自动名（英文/法文），写进 `g_setting` 的 `i18n.role.<lang>.<key>` |

- `existKeyName(key, name)`（**OR**）命中：`isCover=false` ⇒ **整行跳过**；`true` ⇒ 覆盖；
- 覆盖分支：删光该战位的用户关联与软件关联 → 写回 desc（**整行 update，desc 可为 NULL**）→
  按 JSON 列重写三类关联；**用户关联会顺带补上 `user` 战位**（若该用户还没有）；
- 新战位：`id=UUID`、`type=1`、`createdAt=now`、**`updatedAt=NULL`**，全部行处理完再统一插入，插完按 key 反查 id 再写关联；
- **自动名只有新战位分支会写**（覆盖分支在子角色处理完就 `continue` 了）。

## 3. 子角色导入（7 列）

列：名称 / 唯一标识 / 关联用户(JSON) / 关联战位(JSON) / 描述 / 英文名 / 法文名。

- `existKeyName(key, name)` **查的是 `g_role`（战位表）**——厂商的既有缺陷（`findings/147/148` 已记），
  这里带来一个**实测到的后果**：库里已存在同 key 的子角色、但 `g_role` 里没有同名/同 key 时，
  判定为"不存在" ⇒ 走 `addRole` ⇒ **`g_sub_role.key` 唯一约束冲突 ⇒ 500**（本轮探针 T7 实测）；
- 命中 + `isCover=true` ⇒ 不新建，直接更新 desc（可为 NULL）并重写两类关联；
- 命中 + `isCover=false` ⇒ 跳过整行。

## 4. 三处刻意偏离（`docs/deviations.md`）

- **B27**：子角色域写"用户-子角色"关联时**不覆盖用户的 `created_at`**。厂商 `usersRelateRole` 每插一条都
  `setCreatedAt(now)`（写错字段）；role 域照抄了，sub_role 域统一不做（与既有 `addSubRole` 保持一致）。
  实测佐证：本轮探针跑完 `g_user.created_at` 仍是 `1791931380848`，未被改动。
- **B28**：子角色导入的"子角色-战位"关联**按意图删 `g_sub_role_role`**。厂商那段 delete 写错了表
  （删的是 `g_user_sub_role`），会把上一步刚写好的用户关联清掉。实测佐证：探针 T5 跑完后
  `g_user_sub_role` 里 `admin ↔ probe_sr` **仍在**。
- **B29**：关联列填 `[]` 时按"空列表 = 不动"处理。厂商的 `isInWhenPresent(空集合)` 会去掉整个 where
  ⇒ 退化成"全表"（子角色导入的 `[]` = 关联所有用户/所有战位；战位导入的子角色列 `[]` = 清空该战位的全部子角色关联）。
  前端导出对空列表写的是**空单元格**，这条路径上产不出 `[]`；照抄等于留一个"清库"开关。
  实测佐证：探针 T6b 用 `[]` 的覆盖文件跑完，`g_sub_role_role` 里原有的关联**未被清掉**。

## 5. 离线

- `RoleImportServiceTest` 5 例、`SubRoleImportServiceTest` 5 例：
  空行、`isCover=false` 跳过、覆盖分支（删关联 + 整行 update + 重写关联 + 不写自动名）、
  新对象（插入 + 关联 + 自动名，`null` 自动名不写）、四个错误键。
- 全量 **602** 测试 0 失败（上轮 592 + 10）。

## 6. 在线（真集群 + 真 MySQL，探针后已还原）

| 例 | 实测 |
| --- | --- |
| `POST /v1/role/import`（新战位 `probe_imp`，关联软件 `probe_sw`/子角色/用户 `admin`，自动名） | `200` `data:null`；库侧：新行 `type=1`、`created_at=now`、**`updated_at=NULL`**；`g_role_software` `order=1`；`g_user_role` 多出 `admin↔probe_imp` **且自动补了 `admin↔user` 战位**；`g_setting` 两条 `i18n.role.*`（**`updated_at=NULL`**，正是厂商 `updateOrInsert` 的插入分支） |
| 同上 · `isCover=true` 再跑（覆盖 `probe_imp`） | `200`；desc 改成新值；用户/软件关联按 `[]` 清空（厂商 `isNotBlank` 分支）；**子角色关联未被清**（B29） |
| `POST /v1/role/import`（key 40 字符） | `400` `role.key.is.illegal` |
| `POST /v1/role/import`（只有表头） | `200` `data:null`，库无变化 |
| `POST /v1/role/import`（txt 文件） | `400` `global.import.supported.file.type` |
| `POST /v1/sub_role/import`（新子角色 `probe_sr`，关联用户 `admin` + 战位 `admin`，自动名） | `200`；库侧：新 `g_sub_role` 行、`g_user_sub_role` `admin↔probe_sr`、`g_sub_role_role` `probe_sr↔admin`、两条 `i18n.sub_role.*`；**admin 战位的 `updated_at` 被覆盖成 now**（厂商 `subRoleRelateRoles` 的照抄点，实测从 `1791908324000` 变成 `1791989624690`） |
| 同上 · 覆盖（命中 `g_role` 的 name 分支） | `200`；desc 改成新值；`en_US` 自动名被覆盖成 `SubOne2`，`fr_FR` 保持（该行 cell6 为 null ⇒ 不写） |
| `POST /v1/sub_role/import`（key 已在 `g_sub_role`、但 `g_role` 里无同名/同 key） | **`500` 系统错误**（`Duplicate entry 'probe_sr' for key 'g_sub_role.key'`）——厂商"存在性查错表"的实测后果，**照抄** |

探针收尾：探针战位/子角色/软件/关联/自动名全部删除，`g_role`、`g_user_role`、`g_user.created_at`、
admin 战位 `updated_at` 全部还原到基线，临时 `gaop-redis-tmp` NodePort 删除、后端停止、临时 xls 清理。

## 7. 覆盖率口径的第三处修正（`tools/coverage-audit.py`）

前端 bundle 里大量出现 `"/v1/sub_role/userAddRole/"` + `.concat(userId)` 这种写法：裸字面量扫描会把
**截断形式**（`/v1/sub_role/userAddRole`）也当成一个独立端点，而拼串扫描同时给出完整形式
（`/v1/sub_role/userAddRole/X`）⇒ 前者永远"未实现"，成了假阳性。
本轮让裸扫描**跳过紧跟 `.concat(` / `+` 的字面量**（注意要连闭合引号一起跳过）：

| 口径 | 修正前 | 修正后 |
| --- | --- | --- |
| 前端调用面 | 241 | **232** |
| 剩余未实现 | 141 | **132** |

（`/v1/role`、`/v1/sub_role` 两簇现在都是 **0 缺口**。）

## 8. 剩余清单里仍有的口径噪声

`/v1/pods`、`/v1/persistentvolumes` 在厂商 controller 里**根本不存在**（全仓 `grep` 无此映射），
是前端 bundle 里的 K8s 代理路径残片 ⇒ 应从剩余清单里剔除，不算真缺口。
