# 机器码定论：**稳定、可复刻**；`findings/41` 的「不稳定」结论作废

**证据等级**：〔运行实测〕（重复调用 + 逐字节对拉）+〔代码事实〕。2026-10-14。

## 0. 先更正我上一轮的错误结论

`findings/41` §3 写了「厂商的机器码不是稳定值（两次调用解出 `BE74` vs `BE87`）」。
**这个结论是错的，现已作废。** 错在**方法**：那个 `BE87` 是我**从终端手抄 base64 字符串**
再拿去解码得到的——手抄时把 `NzQ`（`74`）看成了 `ODc`（`87`）。本轮改成**程序化取数**后：

```
连续 6 次 GET /v1/licence → 每次都解码出 420DF971-09EC-BA4A-BE74-B4868EB29A53,gaop,
软件详情连续 3 次            → 每次都解码出 420DF971-09EC-BA4A-BE74-B4868EB29A53,gaop
```

**两边都稳定，而且是同一个原始机器码。**

**教训（与"只验形状不验值"同源）**：**不透明值必须程序化提取，绝不手抄**——
手抄会把"我没看清"伪装成"厂商不稳定"，而且这种假结论恰恰会让人放弃对齐。

## 1. 机器码的真实构造（代码事实 + 实测）

原始机器码 = 配置项 `machine.code`（本集群 = `420DF971-09EC-BA4A-BE74-B4868EB29A53`）。
厂商还会尝试读 `/data/licenceInfo/machine_codes.json`（**该文件在本环境不存在**，故走配置值）。
平台 id 来自 `TenxCloudImpl.licencePlatform()`，是**硬编码的 `"gaop"`**（`:539-543`）。

**厂商有两条 base64 拼法，只差一个尾逗号**（`LicenseService` 里两个不同方法）：

| 调用处 | 表达式 | 实测值 |
| --- | --- | --- |
| `/v1/licence`（`getMachineCodeBase64():161-190`） | `Base64(原始码 + "," + "gaop" + ",")` | `420DF971-…-B4868EB29A53,gaop,` |
| 软件详情 `licence.code` 前缀（`getMachineCodeBase64ByService():327-356`） | `Base64(原始码 + "," + "gaop")` | `420DF971-…-B4868EB29A53,gaop` |

而软件详情里的 `licence.code` = **`机器码base64` 字符串再拼上 `Base64(软件key)`**
（`geneSoftwareCode` 是**文本拼接**，不是"先把机器码和 key 拼起来再 base64"）——这一点也实测确认过。

## 2. 本轮改了什么

- `ILicenceRepository.machineCode()` → **`rawMachineCode()`**：语义收窄为"原始机器码，不含 base64、不含平台 id"；
- `ILicenceQueryService` 明确两条方法：`machineCode()`（带尾逗号）与 **`machineCodeForSoftware()`**（不带）；
- 配置项默认值从字面量占位符 `MSOP-REWRITE-PLACEHOLDER` 改为本集群的供给值，
  并在代码注释里写明它是**与厂商同性质的环境供给值**（部署时按同样方式供给，不是能从代码推出来的常量）；
- 软件详情改用 `machineCodeForSoftware()`；`/v1/licence` 继续用 `machineCode()`。

## 3. 对拉结果：**两处都逐字节一致**

```
/v1/licence.code          mine == vendor  ✅   （Base64 串完全相同）
软件详情 licence.code      mine == vendor  ✅
```

## 4. 顺带更正链条

- `findings/41` 开头说"唯一差异是 `licence.code`"——当时是对的；本轮把那处差异修掉了。
- `findings/41` §3 的"未定论"整节作废，改为本节的定论。
- 早期"`/v1/licence` 已验证"的说法仍然需要限定为**形状**：现在**值也一致了**（本轮实测）。

## 5. 测试

新增 2 条用例，分别钉住两条 base64 拼法（带/不带尾逗号）——
这两个方法**只差一个逗号**，正是那种"不看实测就会写成一样"的地方。
全量 `mvn test` **157 条全绿**（上一轮 155）。
