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

**与 `generateBackupExcel:950-971` 的三处不同**（**不要混用**）：

| | `generateExportExcel`（`/export`、`/backup` 实际用） | `generateBackupExcel`（别处用） |
| --- | --- | --- |
| 跳过哪些用户 | **admin、root** | admin、**dev**、root |
| 表头 | `i18n.getMessages(TABLE_HEADERS)`（**已翻译**） | **直接用 key**（`initializeWorkbook(TABLE_HEADERS, …)`） |
| 列 | username/nickname/roleKey/rolesJson/subRolesJson/…（≥5 列） | 只有 4 列：username/status/desc/roleKey |

⇒ **实现 `/export` 与 `/backup` 是"一个 ~50 行的生成函数 + 一个响应写出"**，且**与 `generateBackupExcel` 无关**。
另外：这个函数**用日期样式**（`yyyy-MM-dd`），与模板端点（无样式）不同——照抄时别把样式漏掉。

## 4. 建议顺序

1. `import/template`（**最简单**：无入参、纯产出、可解析比对单元格）；
2. `export` / `backup`（共用同一生成函数，一轮可做两个）；
3. 错误路径 + `update_avatar`（小）；
4. `import` / `recovery`（要处理写库与回滚，放最后）。

## 5. 状态

本轮无业务代码改动；全量 **293 条用例全绿**；无残留资源。
