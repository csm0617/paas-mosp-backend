# `GET /v1/user/import/template` 实现：内容与厂商一致，**唯一差异是 xls 文档属性里的操作系统用户名**

**证据等级**：〔运行实测〕（下载两侧文件 + 字节级差异定位）。2026-10-14。

## 1. 实测

| 检查 | vendor | mine | 结果 |
| --- | --- | --- | --- |
| `Content-Disposition` | `attachment; filename*=UTF-8''%E7%94%A8%E6%88%B7%E5%AF%BC%E5%85%A5%E6%A8%A1%E6%9D%BF.xls` | 同 | **逐字节相同** ✅ |
| `Content-Type` | `application/vnd.ms-excel` | 同 | **相同** ✅ |
| 文件大小 | 4608 B | 4608 B | **相同** ✅ |
| UTF-16 串集合（含中文） | 3 段 | 3 段 | **集合相等** ✅ |
| 全文件字节比对 | — | — | 差异 **5 个字节**（偏移 2088–2094） |

## 2. 那 5 个字节是什么（已定位）

```
vendor 片段: … \x5c\x00p\x00 \x04\x00\x00 root …
mine   片段: … \x5c\x00p\x00 \x03\x00\x00 csm  …
```
⇒ 是 xls **文档属性里的"作者/最后保存者"**，取的是**操作系统用户名**（厂商机器上是 `root`，本机是 `csm`）。
**这是环境产物，不是契约差异**——把两侧都跑在同一个 OS 用户下就会完全一致。

> 这条值得记：**二进制端点的"逐字节相等"要求，会被文档元数据破坏**。所以我按 `findings/100` 的预判，
> 用的是"**内容比对 + 差异定位**"，而不是直接宣布失败——**先把差异看明白，再决定它算不算偏离**。

## 3. 实现

- `paas-core-trigger/pom.xml` 加 `org.apache.poi:poi:5.2.5`（**本机离线仓已有**，`mvn -o` 直接通过）；
- `UserImportTemplateExcelWriter`：sheet `sheet1`、第 0 行 8 个表头、第 1 行 8 个示例，
  文案全部走 `MessageResolver`（**写进单元格的是翻译后的字符串**，照抄厂商 `i18nUtil.getMessages` 的做法），
  **不加任何样式/列宽/日期格式**；
- 控制器写响应：`Content-Type` + 厂商同款 `filename*=UTF-8''<urlencode>` 头，文件名来自 i18n
  `export.file.name.user.import.template`（本仓库三个 locale 里都有该键）。

## 4. 状态

端点映射 **62 → 63**；测试 **41 类 293 → 294 条全绿**；临时 NodePort 已删、进程清零、工作树干净。
