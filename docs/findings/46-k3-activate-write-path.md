# K3：`POST /v1/licence` 激活 / `DELETE /v1/licence` 清空——已激活分支第一次可达

**证据等级**：〔离线实测〕（自造密钥对走通全路径）+〔运行实测〕（对拉无效码错误路径）+〔代码事实〕。2026-10-14。

## 1. 本轮做了什么

| 事 | 说明 |
| --- | --- |
| `POST /v1/licence` | **先校验后落库**：strip → `checkActiveCode`（抛错直接 400，**不落库**）→ `saveActiveCode` → 返回 info 形状 |
| `DELETE /v1/licence` | `clearActiveCode` → 返回 info 形状 |
| `/v1/licence/info` 第 3 分支 | **已激活分支第一次可达**：`authStatus = !(now > endTime)` |
| `g_setting` 写入能力 | 新增 upsert / delete（原来只有 `saveValueIfAbsent`） |

## 2. 对拉：**无效激活码的错误路径逐字节一致**

```
vendor  POST /v1/licence {"activeCode":"eyJ...bogus.bogus"}  →  HTTP 400
        msg = The string 'n\uFFFD.' doesn't have a valid JSON format.
mine    同一条请求                                             →  HTTP 400
        msg = The string 'n\uFFFD.' doesn't have a valid JSON format.
```

这条消息很"脏"（是 java-jwt 的解析异常消息被厂商的兜底 `catch(Exception)` 直接
`throw new WrongArgumentException(e.getMessage())` 抛出来的），**我们逐字复现了它**——
因为我把厂商那串 catch 链（独立异常 → 各自 i18n 键；其它 → 用异常消息）原样照搬。

**并且确认无副作用**：调用前后 `g_setting['system.active.code']` 都是 **0 行**，
且两侧 `/v1/licence/info` 都是 `authStatus=false / expired=true / end=-1`（7 键一致）。

## 3. 已激活分支的行为（离线用自造密钥对走通）

`activate` 内部用的是**服务端自己的机器码**（`machineCode()`），所以激活码里的
`machineCode` claim 必须与它相交——这一点是测试第一次失败时暴露的（我一开始拿 `"A1"` 去签，
必然无交集）。走通后的断言：`authStatus=true`、`end` = JWT exp（秒级）、`code` = 服务端机器码、
`expired=false`、`currentTime`/`trialPeriod` 都在。

另外三条也已钉住：校验失败**不落库**；`deactivate` 后回到试用期语义；**库里存着激活码但校验抛错时，
厂商是 catch 掉继续走试用期，不是整体报错**。

## 4. 一个被我"以为没问题"的清洁问题（本轮才发现并修掉）

扫库时发现两条**我自己留下的残留**：

```
i18n.software.en_US.probeaddv = probeV
i18n.software.fr_FR.probeaddv = probeV
```

它们是**第 30 轮**用厂商 `POST /v1/software` 造探针时，厂商 `saveAutoNameMessage` 顺手写进去的
（`g_setting` 里以 `i18n.software.<lang>.<key>` 为键）。我当时只清了 `g_software`/`g_role_software`，
**没有把"这条流程会碰到的所有表"列全**，于是这两行在库里躺了 7 轮。

**教训**：清洁探针时，"清哪些表"要**从被调端点的写入路径倒推**（本轮这条流程会写
`g_software` + `g_role_software` + `g_setting(i18n.*)`），而不是只清自己刚看到的那几张。
所以这一轮除了清掉这两行，还补了一次**全库 `LIKE '%probe%'` 扫描**确认没有别的残留。

清理后状态：`g_setting` 30 行（其中 `probe` 0 行、`system.active.code` 0 行），
`g_software / g_role_software / g_white_list / g_service_port / g_upload_store_record` 全 0，
`g_user=1 / g_role=2`（基线）。

## 5. 未实现（都记在案）

- 厂商激活后还会 `activeTenxLicence`（TenX 本地桩）、`activeGisLicence`（GIS 未授权）；
- `saveActiveCode`/`clearActiveCode` 还会写/删**授权文件**，我们只动 `g_setting`；
- `getLicenceInfo` 里"时速云时间比授权工具时间早则取更早者"那一支：本环境 `TenxCloudImpl.licences()`
  返回的对象 `merged` 是 **null**，厂商那行 `merged.getLicenseUid()` 会 **NPE 被空 catch 吞掉**，
  所以 `tenxEnd` 恒为空 ⇒ **这一支在本环境不可达**（照抄为"用授权工具的时间"）。

## 6. 测试

新增 4 条（激活成功后 info 走已激活分支、校验失败不落库、清空后回试用期、库里码失效时回退试用期）。
全量 `mvn test` **33 类 214 条全绿**（上一轮 210）。
