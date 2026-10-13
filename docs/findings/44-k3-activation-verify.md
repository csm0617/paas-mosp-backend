# K3 开口：激活码校验（验签 + claim + 机器码交集）—— 用**自己生成的密钥对**做真验证

**证据等级**：〔离线实测〕（真 RSA 密钥对 + 真 JWT）+〔代码事实〕。2026-10-14。

## 1. 关键决定：不用 mock 假装验签，而是自己造一对密钥

厂商的硬前提是"有一个用**厂商私钥**签出来的激活码 JWT"——我们不可能拿到那把私钥。
如果顺着这个前提走，只能写成"看起来在验签、其实永远返回未激活"的空壳。

**做法**：在测试里用 `KeyPairGenerator` 生成一对 RSA 密钥，把**公钥**塞进仓储，
用配对的私钥签出结构完全一致的 JWT（issuer `gaop`、claim `machineCode`/`serviceIdList`、`exp`），
然后让真实实现去验。这样"验签、claim 解析、机器码交集、过期判定、异常映射"**全部真跑**。

**这也解决了上一轮那个原则问题**：`active_code` 目前无人写入 ⇒ 分支不可达。
现在它可达且被验证——只是到达它的方式是我自己签的码。

## 2. 实现（对照厂商）

| 厂商位置 | 规则 | 我们 |
| --- | --- | --- |
| `LicenceKey.parseMachineCode:19-38` | 以 `ey` 开头 → 空对象；否则 base64 → 按 **`,`** 切 → gaopCode/tenxCode/gisCode（trim） | `MachineCodeVO.parse` |
| `stringToHashSetStream:551-556` | 按 **`;`** 切、trim、去空 | `LicenceSets.parse` |
| `hasIntersectionStream:558-565` | 空集/ null → false | `LicenceSets.hasIntersection` |
| `get():531-545` | `g_activate[name='gaop'].public_key` → RSA512 | 同（`ILicenceDao`） |
| `checkSoftwareActiveCode:584-585` | `g_auth[jar_name='default_auth']` **最新一条**（`create_at` desc） | 同 |
| `checkActiveCode:633-680` | 验签 → `machineCode` claim → 与机器码 gaopCode 集合求交；无交集 → `auth.invalid.auth.machine.id` | 同 |
| 成功时的 4 字段 | `end`=exp、`code`=传入机器码、`expired`=now>exp、`endIso8601`=**系统时区** ISO | 同 |

**两处异常契约不同（很容易写成一样）**：
- 平台版验签失败 → `licence.invalid.key.signature`
- 软件版验签失败 → **`licence.invalid.signature`**（少了 `.key`）
两条都有专门的用例钉住。

## 3. 三个实测/代码事实（都是这轮才确认的）

1. **JWT 的 `exp` 只到秒** ⇒ `end` 会被截断到整秒（用例里第一次用毫秒断言就是因此失败的）。
   不是 bug，是契约的一部分。
2. **表名必须查真库**：我一开始凭印象把软件授权公钥的表写成 `g_auth_info`，
   `SHOW TABLES` 才发现真库里叫 **`g_auth`**（`id,jar_name,store_jar_name,class_name,create_at,update_at,used,public_key,private_key`，
   且已有一行 `jar_name='default_auth'`）。**表名不能猜**。
3. **`g_auth` 里没有 `default_auth` 行时**，厂商是 `select.get(0)` → `IndexOutOfBounds`；
   我们返回 null，由调用方折算成"未激活"——**记录为收紧**，并在 `findings` 里点名。

## 4. 接回 K2：软件详情的 `licence` 不再是硬编码

上一轮 `SoftwareDetailServiceImpl` 里 `licence` 是写死的 expired。本轮改成真的走
`getSoftwareLicenceInfo` 的两条路径：
- `active_code == null` → `LicenceInfo.expired(软件码)`；
- 非空 → `checkSoftwareActiveCode`，**抛异常或返回 null 也一律折算成 expired**（厂商同款兜底）。

## 5. 测试

新增 12 条（机器码解析与 `ey` 守卫、`;` 与 `,` 两层分隔符、交集成功/失败、异钥验签失败、
过期、issuer 不匹配、软件码命中/未命中/空列表、软件版专属错误键、无公钥返回 null）。

## 6. K3 还缺什么

- **平台激活写入路径**：`POST /v1/licence/...` 的激活/去激活端点（校验通过后写 `g_activate`/`g_setting`）；
- **GIS 授权**（`checkAndActiveGis` 目前如实返回 false；在线激活 GIS 要 GIS 服务）；
- `/v1/licence/info` 的"已激活"分支（现在恒走试用期分支，因为没人写激活码）。

## 7. 更正：用例总数我一直数错了

我此前每轮写的「162 条」「174 条」是**数错了**——我用的 `grep` 只匹配到一部分模块的汇总行，
而且 surefire 报告目录里还混着旧文件。用模块汇总行精确求和后，正确的是：

**33 个测试类、210 条用例，`BUILD SUCCESS`。**

**教训**：和「手抄不透明值」「只验形状不验值」同源——**计数也是一种要验证的量**。
以后报总数一律用可复算的命令（模块汇总行求和），不靠逐轮累加。
