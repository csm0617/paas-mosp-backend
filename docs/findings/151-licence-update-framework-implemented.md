# `POST /v1/licence/updateFramework` 规格 + 实现（第 158 轮）

**证据等级**：〔代码事实〕（厂商 `LicenceController:94-99` + `LicenceService.updateFrameworkRsaKey:1078-1107` +
`AesConfig:32-39` / `ID_KEY` 逐行读）+〔运行实测〕（**自造真 AES 密文**打到真库，验完还原）。2026-10-14。

## 1. 端点

```java
@PostMapping("/updateFramework")
public Response<Boolean> updateFrameworkRsaKey(@RequestBody EncryptForm form)   // {encryptInfo}
```

## 2. 流程（含一条写死的 AES 密钥）

```
encryptInfo == null ⇒ 400 `licence.new.public.key.empty`
encryptInfo = encryptInfo.replaceAll(" +","")                     // 去掉所有空格
try:
    decrypt = AES/ECB/PKCS5Padding 解密( Base64 解码(encryptInfo), key="2aea937082964c17" )   // 16 字节 ⇒ AES-128
    jsonData = StringEscapeUtils.unescapeJava(decrypt)
    jsonData = 去掉字面量 `\r`/`\n` 与四个 PEM 头尾标记（**在整段 JSON 文本上做**）
    publicKey = Gson(jsonData).publicKey
    activateList = g_activate where name = 'gaop'
    activate.publicKey = publicKey；activate.updateAt = now
    update = updateByPrimaryKeySelective(activate)
    if (update > 0) return true
catch (Exception) ⇒ 400 `licence.parse.authorization.info.failed`
return false
```

## 3. 四处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **AES 密钥写死在代码里**（`2aea937082964c17`，AES/ECB/PKCS5） | 照抄（不是配置项） |
| 2 | **PEM 头尾标记是在整段 JSON 文本上删的**，不是只删字段值 | 所以解出来的 `publicKey` 里不含 `-----BEGIN…` |
| 3 | 任何解密/解析异常 ⇒ **400 `licence.parse.authorization.info.failed`** | 不是 500（实测②） |
| 4 | `encryptInfo` 为 null ⇒ **400 `licence.new.public.key.empty`**（实测③） | 与"解密失败"是两个不同的键 |

> 厂商那句 `if (activateList != null \|\| activateList.size() != 0)` 又是 `\|\|` 写法：
> `g_activate` 里**没有 `name='gaop'` 那一行**时 `activateList.get(0)` 会抛
> `IndexOutOfBoundsException`，被外层 catch 成 **400 解析失败**（而不是"没找到行"）。
> 本环境该行存在，所以这条路径未实测；照抄"取第 0 行"的写法。

## 4. 实现与验证

**实现**：`ILicenceCommandService.updateFrameworkRsaKey` + `LicenceController` 端点 + `EncryptDTO`；
新增端口 `ILicencePayloadPort.decryptPublicKey`（AES + 反转义 + 去 PEM 头 + JSON 解析，
实现放 infrastructure，为此在 `paas-core-infrastructure` 引入 **commons-lang3**（离线仓已有，
用于与厂商同样的 `StringEscapeUtils.unescapeJava`））；DAO/仓储加 `updateActivatePublicKey`。

### 4.1 离线：`LicenceUpdateFrameworkTest` 4 例（+`LicenceLoginOutAllTest` 1 例），全量 **568** 条 0 失败

覆盖：null 入参 ⇒ 400 公钥为空；解密后的公钥写到 `name='gaop'` 那行；受影响行数为 0 ⇒ false；
解密失败 ⇒ 400 解析失败。

### 4.2 在线：**自造真密文**（`openssl enc -aes-128-ecb -K <hex> -base64`）

明文是 `{"publicKey": "PROBE-PUBLIC-KEY", "privateKey": "p"}`。

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | 真 AES 密文 | `200 data:true`；`g_activate.public_key` **变成 `PROBE-PUBLIC-KEY`** ⇒ 整条链路（去空格 → AES 解密 → 反转义 → JSON 解析 → 写库）都通了 |
| ② | `{"encryptInfo":"not-a-base64!!"}` | `400 解析授权信息失败` |
| ③ | `{}` | `400 更换的公钥信息不能为空` |

### 4.3 收尾

**原公钥已还原**（392 字符，逐字比对一致）、临时 NodePort 与后端进程已回收、临时文件已删。
