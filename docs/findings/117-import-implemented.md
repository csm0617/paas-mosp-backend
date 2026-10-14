# `POST /v1/user/import` 实现与验证（K1 最后一个端点，第 121–122 轮）

**证据等级**：〔代码事实〕（厂商 `UserService.java:1029-1351` 逐行读）+〔环境事实〕（真集群 MySQL 可回滚探针 9 例、离线 38 条用例）。2026-10-14。

## 1. 结论先说

- **实现完成**：`POST /v1/user/import` 已落地，`342` 条用例全绿（较上一轮 `+38`）；
- **规格 100%**（第 120 轮补完子角色写入，第 121 轮补正三处会写错的点，见 `findings/100` 末尾）；
- **在线可回滚探针 9 例全过**（真 MySQL、验完还原，`g_user`/`g_user_role`/`g_user_sub_role` 回到基线）；
- **厂商 A/B 这一轮拿不到**：厂商 JVM **Metaspace 已耗尽**，`/actuator/health` 与 `/v1/user/import` 均 60s/120s/300s 超时，
  且厂商**请求日志里根本没出现我的 `/v1/user/import`**（12 分钟内有 15 条 `OutOfMemoryError: Metaspace`）——
  即请求进不到应用层。**这不是"我们和厂商不一样"，是厂商此刻无法作为参照。**

## 2. 分层落地（受 domain 层"无 POI / 无 JSON 库"的硬约束决定）

```
trigger  UserImportExcelParser（POI + Jackson）
            ↓ List<ImportUserRowVO>（"单元格缺失"翻译成 null）
domain   UserImportService（逐行规则 + 覆盖分支 + 三张表写入）
            ↓ IUserRepository / IRoleRepository / ISubRoleRepository
infra    IUserDao.deleteUserRoleByUsername / insertUserRole
         ISubRoleDao.selectByKey（新增）
```

**为什么"读单元格"在 trigger**：`null`（单元格不存在）与 `""`（单元格存在但为空）在厂商那里语义不同
（前者抛 400 或跳过，后者写空串/不设状态），这个区分必须在能看见 POI 的那一层完成。

## 3. 第 121 轮补正的三处"照抄会错"的点（都已落到代码与测试）

| # | 事实 | 如果不照抄会怎样 |
| --- | --- | --- |
| (1) | `roleKey` 为空 ⇒ 厂商的 `continue` **连子角色一起跳过** | 会多写 `g_user_sub_role` |
| (2) | 覆盖分支判的是 `roleKey == null`（**空串会走 else 写 `user` 角色**） | 空串行会少写一条 `user` 关系 |
| (3) | `equalToWhenPresent` 的 present 就是 `!= null`（厂商用 `mybatis-dynamic-sql 1.1.4`，谓词是 `Objects.nonNull`） | 会把"单元格存在但为空串"误当成"缺失"，昵称/描述该写空串时保持原值 |

另外两条已确认的语义：`existUsername` 在长度校验**之前**（已存在用户即使名字 1 个字符也是跳过而不是 400）；
**`importExcel` 没有事务**，且覆盖更新发生在解析循环里、插入发生在循环之后 ⇒ 中途 400 会留下
"覆盖已生效、插入全无"的中间态——**照抄**（有专门的用例锁住这个行为）。

## 4. 离线验证（38 条）

| 层 | 用例数 | 覆盖 |
| --- | --- | --- |
| `UserImportExcelParserTest`（trigger） | 7 | 空表/只有表头；**缺失单元格 ⇒ null、空单元格 ⇒ 空串**；数字状态列转文本；JSON 列解析；空白 JSON ⇒ null；坏 JSON ⇒ IOException；中间空行 |
| `UserImportServiceTest`（domain） | 21 | 六例验收 + 长度/状态/口令/昵称/描述各条报错；**roleKey 空 ⇒ 子角色也不写**；覆盖分支空串 col2 写 `user`；col3 重复 key 插两行；口令已是 BCrypt 不再哈希；覆盖分支缺 col7 ⇒ 默认 `123456`；口令 <6 整行跳过；未知 key 跳过；**中途 400 时覆盖更新已落库** |
| `K1UserImportContractTest`（app，真 HTTP multipart + H2） | 10 | 非 Excel 400（厂商文案）；空表 200 `data:null` 且库无变化；admin/root 跳过；新用户（角色/子角色/BCrypt/默认头像/`updated_at` 为 NULL）；非 admin 角色 ⇒ 自动加 `user`；不覆盖不变；覆盖更新（`status`/`email` 不动、口令变、关系重建）；长度越界 400；缺 col6 400（文案带用户名）；空口令 400 |

## 5. 在线可回滚探针（真集群 MySQL，9 例）

方法照抄本会话标准做法：**造行（含一条临时 `g_sub_role`）→ 逐例验 → 删行核对 → 确认 `admin` 行逐字段未变**。

| 例 | 请求 | 实测结果 |
| --- | --- | --- |
| ① | 只有表头的 xls | `200 {"msg":"OK","code":0,"data":null}`；`g_user`/`g_user_role`/`g_user_sub_role` 无变化 |
| ② | 只含 `admin` 的行 | 同上，库无变化 |
| ③ | 纯文本文件 | `400 只支持xls,xlsx文件的导入`（厂商文案逐字一致） |
| ④ | 用户名 1 个字符 | `400 导入的用户名不合法,用户名应在2~36个字符内` |
| ⑤ | 新用户行（`roleKey=tester`、col3 `["user"]`、col4 `["paas_probe_sr"]`） | 插入成功：`password` 前缀 `$2a$`、`avatar` 非空、**`updated_at` 与 `uuid` 与 `email` 均为 NULL**；`g_user_role` = **两条 `user`**（自动加的一条 + col3 的一条，**未去重**）；`g_user_sub_role` = 1 条 `paas_probe_sr`；中文昵称/描述以 `utf8mb4` 读回正确（`探针`/`探针描述`） |
| ⑥ | 已存在 + `isCover=false` | 行完全不变（昵称/描述/状态/口令哈希全部相同），关系仍 2 条 |
| ⑦ | 已存在 + `isCover=true`（`roleKey=admin`、col4 空） | 昵称/描述更新、口令变化、**`status` 保持 1 不变**、`avatar` 保持原值、`updated_at` 写入；关系被替换为**只剩 `admin`**；子角色**被删空**（0 条） |
| ⑧ | 新用户口令 `12345` | `200`，但**没有插入任何行**（厂商"记日志跳过该行"） |
| ⑨ | 新用户缺 col6 | `400 导入的paas_probe_nostatus用户状态不能为空`（`{0}` 占位符被用户名替换） |
| 回滚 | — | `g_user=1 g_user_role=1 g_user_sub_role=0`（与基线相同）、`g_sub_role=0`、`admin` 行逐字段未变、`paas_probe%` 残留 0 |

### 5''. 顺带发现：**实验室 `admin.nickname` 在库里是双重编码的**（不是本轮造成的）

```
utf8mb4 客户端: SELECT nickname,HEX(nickname) … ⇒ ç®¡ç†å‘˜  C3A7C2AEC2A1C3A7C290E280A0C3A5E28098CB9C
latin1  客户端: 同一列 ⇒ 管理员   （latin1 连接把双重编码的字节"还原"成 UTF-8 字节流）
```
⇒ 库里存的就是 `ç®¡ç†å‘˜` 这串**双重编码**的 mojibake，来源是**早期用 `kubectl exec mysql -e "…中文…"`
且没带 `--default-character-set=utf8mb4` 的播种**（连接字符集 latin1 ⇒ 写入时多编一层）。
**本轮导入写的是正确的单编码**（`探针` 用 utf8mb4 客户端读回正常，见 ⑤）。

**对后续验证的影响**：`/v1/user/list` 之类的 A/B 两边读同一个库，这个 mojibake **会互相抵消**；
但若哪天有人"顺手修"了这行数据，**必须重跑受影响的 A/B**（否则会以为是我们的差异）。

### 5'. 顺带验到的三个边界（与厂商**响应层一致**）

| 输入 | 我们 | 厂商（按代码推演） | 结论 |
| --- | --- | --- | --- |
| 不带 `isCover` | `500 {"msg":"系统错误","code":-1}`（Spring 6 `MissingServletRequestParameterException`） | Spring 5.3 同款异常 → 其 `Throwable` 兜底 → 同样 500 `系统错误` | 一致 |
| `isCover=`（空串） | 同上 500（Spring 6 对非 String 类型的空值直接判参数不可用） | 参数转成 `null` → 控制器 `boolean isCover` 拆箱 **NPE** → 500 `系统错误` | **响应层一致**（异常类型不同，不影响契约） |
| 不带 `file` | `500 系统错误`（`MissingServletRequestPartException`） | 同款异常 → 500 | 一致 |

## 6. 厂商侧为何这轮不能 A/B（留证，供下次接手判断）

- `kubectl -n gaop-namespace logs gaop-be-7958b5884b-q9tdp`：12 分钟内 **15 条** `java.lang.OutOfMemoryError: Metaspace`；
- `restarts=0`、`ready=true`（就绪探针没触发重启），但 `/actuator/health` 60s 超时、`/v1/user/import` 120s/300s 超时；
- 厂商**请求日志里没有出现** `/v1/user/import` ⇒ 请求没到应用层（线程/类加载卡死）；
- 期间厂商仍在处理别的请求（13:06 有 `/v1/pub/nodeDisk` 的 INFO 日志）⇒ **不是代理问题**，是它自己半死。
- 与 `findings/102` 的结论一致（文件类端点在厂商侧本来就不可靠）。

⇒ 本端点的验证证据 = **规格逐行读全 + 38 条离线用例 + 9 例真库可回滚探针**；
`read-paths.json` 的 `_verifiedByDirectAB` **不登记**它（没有 A/B，不假装有）。

## 7. 遗留

- `/v1/user/import` 的 `@AuditLog`（厂商控制器上有，我们还没有审计落库链路）——与其它端点同一笔账；
- 厂商若恢复（重启 pod），可补一次 A/B：用 ①③④⑨ 四个**不写库**的用例即可覆盖守卫与行内校验，
  写库用例要用同样的"造行→验→删行"探针。
