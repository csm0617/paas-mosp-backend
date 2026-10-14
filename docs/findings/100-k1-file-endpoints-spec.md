# K1 文件类端点（6 个）实现规格 + **POI 离线可用性已确认**

**证据等级**：〔代码事实〕（6 个处理器逐个读）+〔环境事实〕（本地 Maven 仓检查）。2026-10-14。本轮不写业务代码。

## 1. 六个端点的机制（`UserController`）

| 端点 | 机制 | 关键点 |
| --- | --- | --- |
| `POST /v1/user/export` | body 是 `List<String> userIdList`（可为空）→ `userService.generateExportExcel(userIdList)` → `HSSFWorkbook` → 写响应 | **二进制响应**，不是 JSON |
| `POST /v1/user/backup` | **同一个** `generateExportExcel` → 同样的 xls 附件 | 与 export 只差文件名/入口 |
| `GET /v1/user/import/template` | `generateExportExcelTemplate()` → xls 附件；文件名来自 i18n `export.file.name.user.import.template` | 模板 |
| `POST /v1/user/import` | multipart `file` + `isCover` → `ValidateUtils.isExcelFile` 不通过 → 400 `global.import.supported.file.type` → `handleImportExcel(file, false, isCover)` | 会**写库** |
| `POST /v1/user/recovery` | multipart `file` → 同样先校验 Excel → `handleRecoveryExcel(file)` → `Response.ok(null)` | 会**写库**（恢复） |
| `POST /v1/user/update_avatar/{userId}` | 大小 > **204800**（200KiB）→ 400 `global.avatar.size.limit`；否则 base64 存库 | 会**写库** |

⇒ **前三个是"产出文件"（读）**，后三个是"消费文件/写入"。

## 2. **POI 在本机离线仓可用**（这是能否实现的前提）

```
~/.m2/repository/org/apache/poi/poi/{4.1.2, 5.2.5, 5.3.0}/poi-*.jar        ✅
~/.m2/repository/org/apache/poi/poi-ooxml-lite/…  poi-scratchpad/…         ✅
```
厂商用的是 `HSSFWorkbook`（xls），POI 4.x/5.x 都有该类。
⇒ **不需要新增任何外部下载**，加依赖即可（建议与厂商同代的 4.1.2，或项目其它依赖兼容的 5.2.5——实现时先确认无冲突）。

## 3. 验证方式（与 JSON 端点**不同**，要先定方法）

这些端点返回**二进制**，`tools/contract-check.py` 那套 L1/L2 不适用。建议方法：

1. **文件字节比对**：两侧都连同一个库 ⇒ `POST /export` 的 xls 内容应当一致。
   注意 xls 里可能含**生成时间/文档属性**，纯字节比对可能因元数据不同而失败
   ⇒ 更稳的是**解析后比对单元格内容**（POI 读回来逐格比），并把"元数据差异"记成允许项；
2. **响应头**：`Content-Type: application/vnd.ms-excel`、`Content-Disposition`（文件名来自 i18n）；
3. **错误路径**（不需要造数据）：`POST /import`/`/recovery` 传一个**非 Excel** 文件 → 两侧都应 400
   `global.import.supported.file.type`（文案可对拉）；`update_avatar` 传 > 200KiB 文件 → 400 `global.avatar.size.limit`；
4. **写库路径**（import/recovery/avatar）：要用**可回滚的探针**（本会话的标准做法：造行 → 验 → 删行核对）。

## 3'. 读实现后发现的**两个重要事实**（都会改变实现方式）

### (a) `POST /v1/user/recovery` 在厂商侧是**空实现**

```java
public void handleRecoveryExcel(MultipartFile file) throws IOException, ClassNotFoundException {
}      // ← 方法体是空的（UserService.java:947-948）
```

⇒ 这个端点**只做 Excel 格式校验**，然后 `Response.ok(null)`，**不写任何数据**。
**这大幅降低了它的实现成本**：我们只需"校验 + 返回 null"，**不需要**解析 Excel 内容。
验证方式也随之变简单：传一个**合法 Excel** → 两侧都 200 `data:null`，且**库里没有任何变化**（比字节比对更硬）。

### (b) `generateExportExcelTemplate` 的结构（8 列 + 表头行）

```
ExcelUtils.initializeWorkbook(i18n.getMessages(TABLE_HEADERS), "sheet1")   // 第 0 行 = 表头（来自 i18n 列表）
row(1): 8 个示例单元格，来自 i18n 键：
  template.user.example.name / nickname / role.key / role / role.role / desc / status / password
```
⇒ 实现需要：**POI** + 这两组 **i18n 文案**（`TABLE_HEADERS` 与上面 8 个键）+ `ExcelUtils.initializeWorkbook` 的行为
（表头写在第 0 行；是否带样式要读 `ExcelUtils` 才能确定——**下一轮先读它再动手**）。

### (b') `ExcelUtils.initializeWorkbook` 的行为（已读，模板实现不再有未知）

```java
public static HSSFWorkbook initializeWorkbook(List<String> tableHeaders, String sheetName) {
    HSSFWorkbook workbook = new HSSFWorkbook();
    HSSFSheet sheet = StringUtils.isEmpty(sheetName) ? workbook.createSheet() : workbook.createSheet(sheetName);
    HSSFRow columnTitleRow = sheet.createRow(0);
    AtomicInteger inc = new AtomicInteger();
    tableHeaders.forEach(name -> columnTitleRow.createCell(inc.getAndIncrement()).setCellValue(name));
    return workbook;
}
```

两点要照抄：
1. **表头是"已经翻译过的文案"**——调用方传的是 `i18nUtil.getMessages(TABLE_HEADERS)`，
   所以写进单元格的是 **i18n 后的字符串**（不是 key）；
2. **无任何样式/列宽/日期格式**（`getDateCellStyle` 是另一个方法，模板不用）⇒ 我们的产出也不加样式，否则字节/结构可能不同。

`TABLE_HEADERS`（8 个 key，顺序即列序）：
`template.user.name / nickname / relate.user / relate.subrole / relate.sub.subrole / desc / status / password`

### (c) 顺带：`/backup` 与 `generateBackupExcel` 不是同一条路径

`POST /backup` 的处理器调的是 **`generateExportExcel`**；而 `generateBackupExcel:950-`（会**跳过
`admin`/`dev`/`root`**）是**另一个**函数、由别处使用。⇒ 实现时不要"望名生义"把两者当成一个。

## 3''. `generateExportExcel:977-`（`/export` 与 `/backup` 共用）——**它不是个小函数**

已读到的结构（约 50 行）：

```
initializeWorkbook(i18n.getMessages(TABLE_HEADERS), "sheet1")            // 表头（已翻译）
select g_user where id in (userIdList) order by created_at desc          // 注意：**有排序**
dateStyle = getDateCellStyle(workbook, "yyyy-MM-dd")                     // 有日期格式！
逐行（**跳过 `admin` 与 `root`**，注意**不跳 `dev`**）：
   col0 username
   col1 nickname
   col2 该用户的第一个角色的 key（`getRoleByUserId(u.getId())` 取 `[0]`）
   col3 角色关系的 key 列表 **JSON 字符串**（`Gson`，仅当非空）
   col4 子角色 key 列表 **JSON 字符串**（`Gson`，仅当非空；逐个 `subRoleMapper.selectByPrimaryKey`）
   col5+ 待读（下一次实现前必须补全）
```

### 已读全（第 104 轮）——8 列 + 列宽，**可以开工了**

| col | 内容 |
| --- | --- |
| 0 | `username` |
| 1 | `nickname` |
| 2 | 该用户**第一个**角色的 `key` |
| 3 | 角色关系 key 列表的 **JSON 字符串**（Gson；空则不设） |
| 4 | 子角色 key 列表的 **JSON 字符串**（Gson；空则不设） |
| 5 | `desc` |
| 6 | `status`（**int**） |
| 7 | **`password`（口令哈希！）** |

行尾还有：`for (int i = 0; i < 3; ++i) sheet.setColumnWidth(i, 2560);`
⇒ **只有第 0/1/2 列设了宽度 2560**（不是全部列——照抄时别"顺手"给所有列设宽）。

⚠️ **第 7 列是口令哈希**：厂商把 `u.getPassword()` 直接写进导出的 Excel。
这是**安全相关**的照抄点，已记入 `docs/deviations.md`（B 类）：**照抄 = 导出文件里含哈希**；
若将来要脱敏，属于"主动偏离厂商"的决定，必须先记录并评估前端/运维是否依赖该列。

**与 `generateBackupExcel:950-971` 的三处不同**（**不要混用**）：

| | `generateExportExcel`（`/export`、`/backup` 实际用） | `generateBackupExcel`（别处用） |
| --- | --- | --- |
| 跳过哪些用户 | **admin、root** | admin、**dev**、root |
| 表头 | `i18n.getMessages(TABLE_HEADERS)`（**已翻译**） | **直接用 key**（`initializeWorkbook(TABLE_HEADERS, …)`） |
| 列 | username/nickname/roleKey/rolesJson/subRolesJson/…（≥5 列） | 只有 4 列：username/status/desc/roleKey |

⇒ **实现 `/export` 与 `/backup` 是"一个 ~50 行的生成函数 + 一个响应写出"**，且**与 `generateBackupExcel` 无关**。
另外：这个函数**用日期样式**（`yyyy-MM-dd`），与模板端点（无样式）不同——照抄时别把样式漏掉。

## 3'''. `POST /v1/user/update_avatar/{userId}`（第 106 轮读到的细节）

```java
if (file.getSize() > 204800) throw new WrongArgumentException("global.avatar.size.limit");   // 200KiB
String base64 = Base64.getEncoder().encodeToString(file.getBytes());
String md5    = DigestUtils.md5Hex(file.getInputStream());
userService.updateAvatar(userId, base64, md5);
return Response.ok(userService.detail(userId));      // ← 响应就是"用户详情"，我们已有该查询
```

`updateAvatar(userId, base64, md5)`（`UserService:1538-`）已知部分：
- 用户不存在 → 400 `user.not.found`；
- 取 `UserStatus`（`UserController.userStatus` 静态字段）并 `setEvent("MODIFY")`；
- 组装 `UserInfo` 与"登录状态列表"，然后（**待读**）大概率写 `g_user.avatar` + 触发 TenX/审计同步。

**`md5` 的去向（第 107 轮读全，已解开）**：**它不落库，只用来决定一个图标标记**：

```java
if (既有 avatar 非空 && md5 非空) {
    md5Hex = md5Hex(Base64.decode(user.getAvatar()));      // 把库里存的旧头像解码后再算 md5
    icon = md5Hex.equals(md5) ? "UN-CHANGE" : "CHANGE";     // 与本次上传的文件 md5 比
} else {
    icon = "CHANGE";
}
userStatus.setUserInfo(userInfo);            // userInfo: userId/username/nickname/status/icon
if (userStatus.isSendStatus()) {
    webSocket.sendUserInfo(gson.toJson(userStatus));        // ← **唯一的"外部副作用"：WebSocket 推送**
}
userMapper.updateByPrimaryKeySelective(update);             // 只写 g_user.avatar = base64
```

⇒ 真正的副作用只有一条：**通过 WebSocket 推送一条用户变更消息**（`event=MODIFY`、带 `icon` 标记）；
**没有** TenX 调用、**没有** md5 落库。所以实现清单是：大小校验 → 查用户 → 算 `icon` → **推 WebSocket** → 更新 `avatar` → 返回详情。

（这也是"先读全再实现"的直接收益：如果按上一轮看到的部分实现，会**漏掉这条 WebSocket 推送**——
而它在响应里完全看不出来。）

⇒ **本端点的实现前置**：① 读全 `updateAvatar`（尤其 md5 的去向）；② 确认"用户详情"响应能否复用
我们已实现的 `/v1/user/detailByUserId` 装配（能复用就省一大块）。
**验证**：`>200KiB` → 400（对拉）；合法小文件 → 写库后**核对 `g_user.avatar` 变化并恢复原值**（可回滚探针）。

## 3''''. `update_avatar` 的实现要素（第 108 轮补齐）

- **DAO/仓储已加**：`IUserDao.updateAvatar(userId, avatar)`（`UPDATE g_user SET avatar = ? WHERE id = ?`，
  照抄厂商"只 set id 与 avatar"）+ `IUserRepository.updateAvatar` 已实现（本轮落地、可编译、全量测试仍绿）；
- **WebSocket 目的地已查明**：厂商 `WebSocket.sendUserInfo(text)` → `sendInfo(text, "userInfo")`
  ⇒ 推的是名为 **`userInfo`** 的通道（我们现有的是 `K8sTopicBroadcaster`，是**另一个**通道，需要新增/复用）；
- **`icon` 规则**：既有 avatar 与本次 md5 都非空时，比较"旧头像解码后的 md5"与"本次文件 md5" ⇒ 相同 `UN-CHANGE`、
  否则 `CHANGE`；任一为空 ⇒ `CHANGE`；
- 响应 = 用户详情（可复用 `/v1/user/detailByUserId` 的装配）。

⇒ **剩余待做**：域服务（icon 计算 + 推送 + 更新）+ 控制器（multipart、大小校验、base64/md5）+ `userInfo` 通道推送。

## 3'''''. `POST /v1/user/import`（K1 最后一个，**最大的一块**）

入口：`handleImportExcel(file, isImportFullBackUp=false, isCover)` →
`WorkbookFactory.create(...)`（**POI 同时支持 xls/xlsx**）→ `sheet.getSheetAt(0)`；
`lastRowNum < 1` ⇒ **只记日志"用户表格无内容…不做导入操作"并直接返回**（不报错）；否则 `importExcel(...)`。

`importExcel` 已读到的规则（**远未读完**）：

```
从第 1 行开始逐行：
  col0 → username（空 ⇒ 记日志跳过）
  username 为 admin/root ⇒ 跳过
  已存在的 username：
       isCover=false ⇒ 跳过
       isCover=true  ⇒ 走 existUpdateUser(currentTime, i, row, username)（**另一条分支**）
  username 长度不在 [2,36] ⇒ 400 user.invalid.username.length
  col6 → status …
```

⇒ **这是本会话见过的最大单函数之一**：逐行解析 + 三种行内分支（跳过 / 更新 / 插入）+ 覆盖语义 +
口令与角色的处理 + uuid 列表，**且全程写库**。实现它需要**一整轮**，并且必须配**可回滚探针**
（导入前后核对 `g_user`/`g_user_role` 等表的行数与内容，验完还原）。

### 第 115 轮补读：插入/更新两条分支的关键规则

```
新用户行（不存在）：
   col4 → 子角色 key 列表（**JSON 字符串**，Gson 解析成 List<String>）
   id = UUID.randomUUID()；createdAt = currentTime
   avatar = userConfig.getDefaultAvatar()        ← **配置项默认头像**
   col7 → password；取不到 ⇒ 默认 "123456"；长度 < 6 ⇒ **记日志并跳过该行**
   收集完 ⇒ 逐条 userMapper.insertSelective(user)（+ 后续关系写入）

已存在用户 + isCover=true（existUpdateUser:1235-）：
   同样读 col7 作为口令；取不到 ⇒ "123456"；长度 < 6 ⇒ 记日志跳过
   （其余字段与关系更新待读全）
```

⇒ 实现时必须带上：**默认口令 "123456"**、**口令长度 < 6 跳过该行**、
**`userConfig.getDefaultAvatar()`**（我们的配置里需要一个同名项）、以及 col4 的 **JSON 子角色列表**。

### 第 118 轮补读：**角色关系的写入规则**（这是最后一块）

`insertSelective(user)` 之后，按该行的 `roleKey` 写 `g_user_role`：

```
roleKey 为空 ⇒ 不写
roleKey == "admin" 或 "dev" ⇒ **只**写这一条角色关系
否则 ⇒ 写 "user" 这一条，**再加** col3（`importUserInfo.getUserToRole()`，JSON 列表）里的每个 key
        —— 每个 key 都要在 `g_role` 里查得到（查不到就跳过）
每条：{userId, roleId, createdAt = currentTime}
```

⇒ **`/import` 的规格至此完整**：入口守卫（非 Excel 400 / 空表 no-op）→ 逐行解析（col0/2/3/4/6/7）→
跳过规则（空用户名、admin/root、已存在且不覆盖、口令 <6）→ 长度校验（[2,36]）→ 新用户插入
（UUID/createdAt/默认头像/口令）→ **角色关系**（上表）→（子角色关系）→ 覆盖模式走 `existUpdateUser`。

### 第 120 轮补读：**子角色关系的写入**（规格 100% 完成）

紧跟角色关系之后（`UserService:1220-1230`）：

```
selectUsers 为空 或 col4 的 subRoles 为空 ⇒ 跳过
逐个 subRole key：在 g_sub_role 里按 key 查（查不到就跳过）
  写 g_user_sub_role：{userId, subRoleId, createdAt = currentTime}
```

⇒ **`/import` 的全部写库动作现在都已知**：`g_user`（插入或覆盖更新）+ `g_user_role`（角色关系，
`admin`/`dev` 只写自身、其它自动加 `user`）+ `g_user_sub_role`（col4 的 JSON 列表）。
**实现要素齐备，可直接开工**（六例验证 + 可回滚探针：核对这三张表的行数/内容并还原）。

**建议**：把它排在 K1 最后、单独一轮；**不要**与其它端点混做。验证至少覆盖：
① 空表 ⇒ 200 且**库无变化**；② 只含 admin 的行 ⇒ 同样无变化；③ 新用户行 ⇒ 插入；
④ 已存在用户 + `isCover=false` ⇒ 不变；⑤ `isCover=true` ⇒ 更新；⑥ 用户名长度越界 ⇒ 400。

### 第 121 轮：开工前的三处**规格补正**（都是"照抄会错"的点）

**(1) `roleKey` 为空时，子角色也一起被跳过**（上一轮的规格漏了）

```java
if (selectUsers != null && selectUsers.size() != 0) {
    String roleKey = importUserInfo.getRoleKey();
    if (StringUtils.isBlank(roleKey)) continue;      // ← 这个 continue 是**外层 for** 的
    ... 写角色关系 ...
}
if (selectUsers == null || selectUsers.size() == 0 || CollectionUtils.isEmpty(subRoles = ...)) continue;
for (String subRole : subRoles) { ...写 g_user_sub_role... }
```

⇒ 新用户分支里：**`roleKey` 为空 ⇒ 既不写 `g_user_role`，也不写 `g_user_sub_role`**（不是"只不写角色"）。
两处 `roleKey` 的"空"判定还**不一样**，必须分开照抄：

| 分支 | 取值 | 空判定 | 空时的结果 |
| --- | --- | --- | --- |
| 新用户 | `isNotBlank(cell2)` 才 `setRoleKey` | `isBlank` | `continue`（**角色与子角色都不写**） |
| 已存在+覆盖 | 直接取 `cell2` | `roleKey == null` | `return`（**只跳过角色**，子角色在前面已经写完） |

⇒ 所以**单元格存在但为空串**时：新用户 → 无任何关系；覆盖 → 走 `else` 分支，**写 `user` 角色** + col3 列表。

**(2) `updateByPrimaryKeySelective` 的"present"就是 `!= null`**（不是"非空串"）

厂商用的是 **`mybatis-dynamic-sql 1.1.4`**（`gaop-be/pom.xml:117-120`）；反编译
`IsEqualToWhenPresent` 的 BootstrapMethods 可见谓词是 `java/util/Objects.nonNull`（`IsLikeWhenPresent` 同）。
⇒ 覆盖分支里 `desc`/`nickname`：**单元格缺失 ⇒ 保持原值**；**单元格存在但为空串 ⇒ 写成 `""`**。
（我原先打算"空串也当缺失"，那会与厂商不一致。）

**(3) 覆盖分支对 col4/col3 的 JSON `null` 会 NPE**

```java
List roleRelationList = (List) gson.fromJson(subRoleKey, new TypeToken<List<String>>(){}.getType());
subRoleList.addAll(roleRelationList);        // ← col4 字面量 "null" ⇒ roleRelationList == null ⇒ NPE ⇒ 500
```
新用户分支有 `if (userToJson != null)` 保护，**覆盖分支没有**。
⇒ 这一条我们**主动硬化**（把 JSON `null` 当"无列表"），记入 `docs/deviations.md`：模板/导出都不可能产出字面量 `null`，
所以前端路径上不可达；照抄等于故意留一个 NPE。

**(4) 另外两处已确认的语义**（顺手记录）

- `existUsername` 在**长度校验之前** ⇒ 已存在的用户 + `isCover=false` 即使用户名只有 1 个字符也是"跳过"而不是 400；
- 覆盖分支**先删角色关系再更新用户**（`deleteExistUserRelationRole(username)` 在 `updateByPrimaryKeySelective` 之前），
  且**最后还会再 update 一次**（`:1349-1350`，只为把 `updated_at` 写成 `currentTime`）⇒ 净效果等价于"一次带 `updatedAt=now` 的更新"。

**(5) 事务边界：厂商的 `importExcel` 没有事务**，而且**覆盖更新发生在解析循环里、插入发生在循环之后**：

```
for 每一行：… 已存在+isCover ⇒ existUpdateUser(...) 立刻写库 …
（循环里若某行抛 400 ⇒ 前面几行的"覆盖更新"已落库，而"新用户插入"一行都没写）
userList 非空 ⇒ 再逐条 insertSelective + 关系写入
```
⇒ 我们要照抄这个"**半途落库**"语义（不加事务），否则行为不同。**这一点必须写进实现注释**，否则后人会"顺手"加 `@Transactional`。

## 4. 建议顺序

1. `import/template`（**最简单**：无入参、纯产出、可解析比对单元格）；
2. `export` / `backup`（共用同一生成函数，一轮可做两个）；
3. 错误路径 + `update_avatar`（小）；
4. `import` / `recovery`（要处理写库与回滚，放最后）。

## 5. 状态

本轮无业务代码改动；全量 **293 条用例全绿**；无残留资源。
