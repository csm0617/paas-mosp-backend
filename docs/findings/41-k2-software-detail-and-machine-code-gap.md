# K2 续：`GET /v1/software/{id}` —— **23 键聚合对拉只差 1 个字段**，而那个字段暴露出我们自己的一个占位实现

**证据等级**：〔运行实测〕（对拉厂商）+〔代码事实〕。2026-10-14。

## 1. 结果：23/23 键一致，**唯一差异是 `licence.code`**

同一个探针软件行、两侧各查一次：

```
code: 0/0   keys: 23/23
差异数: 1
❌ data.licence.code
   vendor = NDIwREY5NzEtMDlFQy1CQTRBLUJFODctQjQ4NjhFQjI5QTUzLGdhb3A= + cHJvYmVkZXRhaWw=
   mine   = MSOP-REWRITE-PLACEHOLDER + cHJvYmVkZXRhaWw=
```

其余全部一致，包括：`screenShotList` 按 `,` 拆、`roleKeyList`/`roleNameList`、
`nameUs`/`nameFr` 兜底成 `name`、`status` 默认 **`"STOP"`**（厂商字段初始化器，不是 null）、
非 BS 框架时 `serviceList` 为 **null**、`version` = 当前上传记录的 id、`uploadStoreRecord` 7 键。

## 2. 那个唯一差异：**我们自己的机器码是个占位符**

`licence.code` = `机器码 + Base64(软件key)`（厂商 `geneSoftwareCode`）。其中：
- 尾部的 `Base64(key)` 两侧一致 ✅；
- 前缀（机器码）**我们是字面量占位符**：`LicenceRepository` 里
  `@Value("${gaop.licence.machine-code:MSOP-REWRITE-PLACEHOLDER}")`。

厂商的机器码（`LicenceService.getMachineCodeBase64ByService`，`:327-346`）：
`Base64( gaopCode + "," + platformId )`，其中
- `gaopCode` 默认取配置项；若 `/data/licenceInfo/machine_codes.json` 有内容则用它（**实测该文件在厂商 pod 里不存在**，所以走配置值）；
- `platformId` 来自 `tenxCloud.licencePlatform()` —— **是硬编码的 `"gaop"`**（`TenxCloudImpl.java:539-543`）。

**并且厂商有两条不同的机器码方法，差一个尾逗号**（本轮实测）：
| 调用处 | 形态 |
| --- | --- |
| `/v1/licence`（`:134-158`） | `Base64(gaopCode + "," + "gaop" + ",")` → 解码 `420DF971-09EC-BA4A-BE74-B4868EB29A53,gaop,` |
| 软件详情（`:327-346`） | `Base64(gaopCode + "," + "gaop")` → 解码 `420DF971-09EC-BA4A-BE87-B4868EB29A53,gaop` |

## 3. 一处**未定论**（必须单独查，不能猜）

上面两行解码出来的机器码**只差一个字符**：`...-BE74-...` vs `...-BE87-...`。
两次调用相隔几分钟、同一个 pod、同一个配置 —— 说明**厂商的机器码不是稳定值**。

可能的原因（都还没验证）：
1. 配置值里本来就含一个会变的部分（例如从 MAC/主机名派生、或含短时间戳片段）；
2. `/data/licenceInfo/machine_codes.json` 在某一次调用时短暂存在过（但我查的时候不在）；
3. 两处调用其实是**两个不同的配置项**（`:134` 与 `:327` 读的字段不同）。

**这件事必须在做授权域（K3）之前定论**，因为它决定了两件事：
- 我们到底该**读什么**（配置项？文件？派生算法？）；
- 如果它本质上不稳定，那么"逐字节等价"在这一个字段上**不可能达成**，
  就必须按 ADR-0006 把它记成 L3 已知偏离并说明前端是否读它。

## 4. 顺便更正一条我自己的旧记录

我在早期（登录页那一轮）曾把 `/v1/licence` 记为"已实现并验证"。**准确的说法是"形状已验证"**——
`{"code","existGis"}` 两个键与厂商一致，但 `code` 的**值**一直是占位符，从来没对过。
这就是"只验形状不验值"会埋下的雷：直到软件详情把同一个值暴露在 JSON 里，它才被发现。
**记录更正：`/v1/licence` 的 `code` 值尚未达成一致。**

## 5. 本轮其它实现说明

- **有序实现的三件事**都在：`screenShotList` 拆分、`roleKeyList`/`roleNameList`、`version`/`uploadStoreRecord`；
- **`licence` 只做"未激活"分支**（`LicenceInfo.expired(code)` = `{end:-1, endIso8601:null, code, expired:true}`），
  已激活 + JWT 验签属 K3，未实现；因为 `active_code` 只能由 K3 写入 ⇒ **该分支当前不可达**；
- **`framework == "BS"` 的 `serviceList` 未实现**（厂商要 K8s 现场态 + `g_software_app` 映射），
  我们回到厂商字段的默认值（空列表）；非 BS 时厂商也是 `serviceList = null`，那条是照抄的；
- 不存在 → 400 `software.software.id.not.found`（与删除用的 `software.not.exist.by.id` **不是同一个键**）。

## 6. 一处**同一个坑第二次踩**（工具层）

离线 IT 里 `roleKeyList` 返回了 `[null]`：`selectRelationRoles` 返回 `List<Map<String,Object>>`，
而 **H2 的列标签是大写**（`ID`/`NAME`/`KEY`），MySQL 是小写 ⇒ Map 取键拿到 null。
这与前面 `selectIdAndKeyByKeys` 是**同一个坑**。这次改成 `RelationRolePO` 让 MyBatis 做大小写不敏感映射，
并在 PO 的 javadoc 里写明"这个坑在同一轮里踩了第二次"。
**结论：凡是从"不确定大小写的库"取多列，一律用 PO，不用 Map。**

## 7. 测试

- 新增离线 IT 3 条（23 键 + 逐字段值、无上传记录时 version/uploadStoreRecord 为 null、不存在 id 报错）；
- 全量 `mvn test` **155 条全绿**（上一轮 152）；
- 探针软件/上传记录/关联全部删除，库回到 `software=0 / upload_store_record=0 / role_software=0 / user=1 / role=2`。
