# K2 续：`POST /v1/software`（新增软件）——对拉抓出我自己臆造的三个字段

**证据等级**：〔运行实测〕（对拉厂商）+〔代码事实〕。2026-10-14。

## 1. 结果

| 项 | 实测 |
| --- | --- |
| 成功响应 | **23 键，两侧键数一致，除 id/key/name/routerKey/时间戳外逐字段相同** |
| `updatedAt` | 两侧都是 **null**（厂商只设 `createdAt`，与用户/战位新增一致） |
| `routerKey` | 两侧都是 `/<key>/`——**表单里的 `routerKey` 不生效**，厂商重写 |
| `screenshot` | 截图列表按 `,` 拼接（`a.png,b.png`） |
| 战位关联 | `roleListKey` 是**战位 key**，反查 id 后建关联；`order` = 该战位已有软件数 + 1（实测两条 1、2） |
| `key=api` | 400 "软件标识 api 是保留字，请修改软件标识" ✅ |
| key 重复 | 400 "软件标识 probeaddm 已存在" ✅ |
| key 首字符是数字 | 400 "软件标识 9abc 不合法，不能以数字开头" ✅ |

## 2. 对拉抓出我**自己臆造**的三个字段（这是本轮最该记的一条）

第一版实现里，我从数据库列反推了入参，多写了三样东西，对拉时全部暴露：

| 字段 | 我写成了 | 厂商实际 | 根因 |
| --- | --- | --- | --- |
| `appName` | `form.getKey()`（以为"应用名默认等于 key"） | **null** | `SoftwareForm` **没有 `appName` 字段**，`BeanUtils.copyProperties` 拷不到 |
| `version` | 从表单取（我给 DTO 加了 `version`） | **null** | `SoftwareForm` **没有 `version` 字段**——新建时版本是空的 |
| 响应键数 | **27 键**（复用了列表项的 DTO） | **23 键** | `POST /v1/software` 返回的是 `Software`，列表返回的是 `SoftwareMessage`（多 4 个附加键） |

**教训**：**入参字段要从厂商的表单类（`SoftwareForm`）读，不能从数据库列反推。**
`g_software` 有 `app_name`/`version` 列，但新增表单根本不接受它们——列存在 ≠ 能通过这个端点写。
（顺带：`version` 在厂商里其实是"上传记录 id"，由 `CsSoftwareUploadService.relateCsProgram` 写，
见 `docs/findings/03` 的 K2 不变量表。）

**同时补上一条**：同一个实体在不同端点有**不同键数**（`Software` 23 / `SoftwareMessage` 27），
这与用户/战位那两个"同名不同形状"的坑是同一类问题——所以新增了独立的 `SoftwareDTO`（23 键），
不再复用列表项 DTO。

## 3. 一处主动收紧（记录）

厂商 `addSoftWare` 里 `softwareForm.getRoleListKey().size()` **不判 null** ⇒ 请求不带 `roleListKey` 会 NPE（500）；
controller 上有 `@Transactional`，所以不会留下半成品行（比用户新增那个缺陷轻）。
我们按"空列表"处理，不抛错 —— 与 `/v1/user` 的同类收紧一致。

## 4. 其它照抄点

- **key 规则**（`softwareKeyLimit`，`:672-693`）：空 → `key.must.not.null`；首字符数字 → `...not.number.start`；
  首字符 `-` → `...not.dash.start`；不匹配 `^[a-z][a-z0-9-]*$` → `...illegal.format`；长度 ∉ [2,36] → `...illegal.length`。
  **保留字只挡 `api`**（`software.key.identifier.reserved.word`），其它像 `admin`/`system` 的 key 不受保护——照抄。
- **name 规则**：空 → `name.must.not.null`；长度 ∉ [2,100] → `name.illegal`。
- **desc**：>255 → `desc.length.invalid`；空白时厂商 `softwareDescLimit` 先 `setDesc(null)` 但**返回原值**，
  调用方又 `setDesc(desc)` 覆盖回去 ⇒ **落库是空串**（那个 null 被覆盖）。
- **关联已存在时跳过**（`softwareRelateRole`），不更新已有行的 `order`。

## 5. 测试与清理

- 新增域测试 7 条（保留字、重 key/name、name 规则、key 首字符四连判、`routerKey` 重写、截图拼接、desc 长度）；
- 全量 `mvn test` **144 条全绿**（上一轮 137）；
- 探针软件行与关联全部删除，库回到 `software=0 / role_software=0 / user=1 / role=2`，两侧空列表仍都是
  `{"softwareList":[],"total":0}`。
