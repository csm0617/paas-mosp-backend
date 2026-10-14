# `POST /v1/software/software/active`：用**自造密钥对**在真实环境验证批量激活，五种情形一致

**证据等级**：〔运行实测〕（自造 RSA 密钥对 + 临时 `g_auth` 公钥行 + 逐情形对拉）+〔代码事实〕。2026-10-14。

## 1. 怎么造出可验证的场景（沿用 K3 那一轮的手法）

激活需要"用厂商私钥签出来的激活码"——我们拿不到那把私钥。做法：

1. `openssl genrsa` 生成密钥对；
2. 往 `g_auth` **临时插入**一行 `jar_name='default_auth'`、`create_at` 取最大值的记录，公钥用我们的
   （厂商按 `create_at desc` 取 `get(0)` ⇒ 选中我们这行）；
3. 用我们的私钥手工签 JWT（header `{"alg":"RS512","typ":"JWT"}`，issuer `gaop`，含 `machineCode`/`serviceIdList`/`exp`）
   —— 签名用 `openssl dgst -sha512 -sign`，签名输入是 `base64url(header).base64url(payload)`；
4. 验完**删除临时行**，并核对原行完好。

**注意 `g_auth.id` 是自增 int**：第一次我用字符串 id 插入，静默失败（表里仍只有原 1 行），
导致五条用例全部落到"验签失败"——**是"`default_auth` 行数没变成 2"这个反常现象暴露的**。

## 2. 实测结果

| 情形 | code | data | msg | 库内 `pc-1` |
| --- | --- | --- | --- | --- |
| 成功（机器码匹配 + 有 pc-1） | 0/0 | **相同** `[{softwareKey:probeactiveA, result:true}]` | OK | **写入的 token 与 `active_expire` 两侧完全相同** ✅ |
| `machineCode` claim 为空 | 400/400 | (trace) | 「已签名识别码中未获取到机器码」 | 未改动 ✅ |
| `serviceIdList` claim 为空 | 400/400 | (trace) | 「已签名识别码中未获取到软件的key」 | 未改动 ✅ |
| `machineCode` 与本机不一致 | 400/400 | (trace) | 「机器码跟本机不一致」 | 未改动 ✅ |
| 软件 id 不存在 | 0/0 | **相同** `[]` | OK | 未改动 ✅ |

**清理核对**：`g_auth` 恢复为 1 行、原行公钥前缀 `MIIBIDANBgkq` 完好；`g_software` 探针 0 残留。

## 3. 照抄点（三处与 `cancelAuth` 正好相反或容易写错）

| | `cancelAuth` | `active` |
| --- | --- | --- |
| `result` | 恒 **false** | 恒 **true** |
| 全部 id 不存在 | **400**（一个都没取消到） | **空数组**（不报错） |
| 缺公钥行 | 不涉及 | 400 `auth.invalid.public.key.auth.code` |

另外：**验签失败只折算成一个键** `software.parse.activation.code.failed`（不像 `checkActiveCode` 那样
细分 `licence.invalid.key.signature` / `licence.activation.code.expired`）；`active_create` 用的是
**本次请求的起始时间戳**（`start`），不是 DB 当前时间；写入时**不动 `updated_at`**。

## 4. 状态

K2 跨域端点 **+1**；测试 **41 类 262 条全绿**（上一轮 259）。
临时 `g_auth` 行已删并核对、探针行已删、临时 NodePort 已删、进程清零、工作树干净。
