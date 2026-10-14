# `POST /v1/user/export` 与 `/v1/user/backup` 实现：8 列、只设前三列宽、含口令哈希

**证据等级**：〔运行实测〕（端点产出 + 响应头）+〔离线单测〕（POI 读回逐格断言）。2026-10-14。

## 1. 两个端点是**同一个实现**

厂商 `usersBackUp`（`:463-477`）与 `usersExport`（`:480-494`）**方法体完全相同**：都调
`generateExportExcel(userIdList)`、都 `Content-Type: application/vnd.ms-excel`、**文件名都用 i18n
`export.file.name.user.export`**。⇒ 我们也是同一个私有方法 `writeUserExcel`。

## 2. 实测（我们的产出）

```
Content-Disposition: attachment; filename*=UTF-8''%E7%94%A8%E6%88%B7%E5%90%8D%E5%8D%95.xls   （用户名单.xls）
Content-Type: application/vnd.ms-excel
大小: 4096 字节（库里只有 admin ⇒ 被跳过 ⇒ 只有表头行）
```

**一处小插曲（已解释）**：我在文件里提取到字符串 `admin`，一度怀疑"admin 没被跳过"。
查证后是**表头文案本身**含这个词——`template.user.relate.user=关联角色(admin运维管理员 user席位操作员)`。
⇒ **在二进制文件里"看到某个词"不等于"写入了某个字段"**；要判定必须**读回单元格**（下面的单测就是这么做的）。

## 3. 单测（POI 读回，逐格断言）

- 域层 4 条：跳过 admin/root、**不跳 dev**；8 个字段取值（含第 2 列 = **第一个**角色 key、第 3/4 列键列表、
  第 7 列 = 口令哈希）；`userIdList` 过滤；只有被跳过用户时为空；
- 触发层 2 条：无数据行时**只有表头**；数据行的列序 + `status` 是**数字单元格** +
  **只有第 0/1/2 列宽 2560**（第 4 列仍是默认 2048）。

## 4. 照抄点（三处）

1. 行尾 `for (int i = 0; i < 3; ++i) sheet.setColumnWidth(i, 2560);` ⇒ **只设前三列**（不是所有列）；
2. 厂商会创建 `yyyy-MM-dd` 日期样式（虽然导出里没有日期单元格）⇒ 我们也创建一次，保持结构一致；
3. **第 7 列是口令哈希**（`u.getPassword()`）⇒ 照抄，已记入 `docs/deviations.md` **B12**（脱敏属主动偏离）。

## 5. 未做在线对拉（如实标注）

厂商 pod 的 Metaspace（`-XX:MaxMetaspaceSize=128m`）已被打满，文件类请求会 500（`findings/102`）。
⇒ 本轮**没有**对厂商做 `/export` 对拉，正确性由"逐行照抄 + POI 读回断言"保证。

## 6. 状态

端点映射 **64 → 66**；测试 **41 类 294 → 300 条全绿**；临时 NodePort 已删、进程清零、工作树干净。
