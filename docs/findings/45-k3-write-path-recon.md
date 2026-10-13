# K3 写入路径勘查（本轮未动手实现，只把它勘查清楚 + 排除了一个疑似缺陷）

**证据等级**：〔运行实测〕+〔代码事实〕。2026-10-14。**本轮没有改任何业务代码。**

## 1. 为什么这轮只勘查不实现

上一轮我刚因为「注释写对了、代码写错了」（空白 desc 那条）被对拉打回。K3 写入路径的正确实现
需要先读懂 `getLicenceInfo` 的**已激活分支全文**（约 60 行）与第三个机器码变体
（`getMachineCodeBase64WithoutGisInfo`）；在没读全的情况下动手，正是最可能重演同类失误的场景。
所以本轮把范围收成"勘查 + 排除一个疑似缺陷"，并留下可直接开工的清单。

## 2. 勘查结果：写入路径的形状

```
POST /v1/licence   { activeCode }            → 校验通过才落库，最后返回 /v1/licence/info 的形状
  activeCode 先 strip → checkActiveCode(机器码, activeCode)   // 抛错则不落库
  saveActiveCode(activeCode) → activeTenxLicence → activeGisLicence → return activeInfo()

DELETE /v1/licence                            → clearActiveCode() → return activeInfo()
```
出处：`LicenceController.java:54-63`（激活）与 `:74-78`（清空）。

`/v1/licence/info`（`LicenceService.getLicenceInfo:937-…`）三个分支：
1. **没有激活码** → 试用期分支：已有 `g_setting['system.active.trial_period']` 就复用它，
   否则建一条 `now + 30 天`；`authStatus=false`；
2. **有激活码但校验抛错**（catch 后 `licenceInfo == null`）→ 也走试用期那一套（并给 `currentTime`）；
3. **有激活码且校验通过** → 已激活分支（`authStatus=true`）。

**我们目前只实现了第 1 支**（`LicenceQueryServiceImpl.queryInfo`），因为没人写过激活码。
写完 `POST /v1/licence` 之后第 3 支才会第一次可达——这正是下一步的价值所在。

## 3. 本轮的一个实测收获：**排除**了一个疑似缺陷

`getLicenceInfo` 用的是 `getMachineCodeBase64WithoutGisInfo()`——**第三个**机器码变体
（前两个上一轮刚实现：`/v1/licence` 带尾逗号、软件详情不带）。
按前两轮的经验，这很可能又是一处"值与我们对不上"的地方，于是对拉验证：

```
vendor GET /v1/licence/info  →  code 解码 = 420DF971-09EC-BA4A-BE74-B4868EB29A53,gaop,
vendor GET /v1/licence       →  code 解码 = 420DF971-09EC-BA4A-BE74-B4868EB29A53,gaop,
```

**两者相同**（都带尾逗号）⇒ 我们 `/v1/licence/info` 现在用的 `machineCode()` 是**对的**。
限定条件：**本环境没有 GIS**（`gisCode` 为空）；有 GIS 时两者是否仍相同**未验证**，
已在清单里留成一条待验证项。**记录下来的价值**：把"怀疑"变成了"已排除"，避免下次重复怀疑。

## 4. 下一轮可直接开工的清单

| 事 | 位置 | 备注 |
| --- | --- | --- |
| `saveActiveCode` / `clearActiveCode` | `LicenceService:448-460` | 写/删 `g_setting['system.active.code']`（我们已有 `saveSettingValue`/`queryActiveCode`）；厂商还会删一个授权文件 |
| `activeTenxLicence` / `activeGisLicence` | `:813-` / `:723-` | TenX/GIS 侧效应；本环境 TenX 是本地桩、GIS 未授权 ⇒ 需要先确认它们在本环境是否 no-op |
| `getLicenceInfo` 已激活分支 | `:1000-1020` | 差这段没读全（含 `authStatus=true` 时的字段组合） |
| `getMachineCodeBase64WithoutGisInfo` | `LicenceService` | 与带 GIS 版是否同值：本环境相同，**有 GIS 时未验证** |
| 激活/清空端点 | `LicenceController:54-78` | 两个端点，响应都是 `NewLicenceInfo`（7 键，我们已有 DTO） |

## 5. 状态

- 本轮**零业务代码改动**，工作树里只有本文档与地图更新；
- 集群只做了一次**只读**探测（`/v1/licence/info`），无探针写入、无临时资源；
- 全量用例仍是 **33 类 210 条全绿**（上一轮已复算）。
