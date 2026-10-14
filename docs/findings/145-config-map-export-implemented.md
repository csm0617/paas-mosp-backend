# 配置组导出两端点 规格 + 实现（第 152 轮）

**证据等级**：〔代码事实〕（厂商 `ConfigMapController.backUpConfigMap:434-445` / `exportTcpAndUdp:467-478` +
`SoftwareService.exportConfigMap:3453-3477` / `exportTcpAndUdpConfigMap:3739-3774` 逐行读）
+〔运行实测〕（真集群 ConfigMap，含两种标签选择器的对照）。2026-10-14。

## 1. 两个端点（都是**附件下载**，不是 JSON 信封）

```java
@PostMapping("/backup")       public void backUpConfigMap(@RequestBody List<String> nameList, HttpServletResponse)
@PostMapping("/listenBackup") public void exportTcpAndUdp(HttpServletResponse)
```
两者都：`Content-Type: application/json` + `Content-Disposition: attachment` + **Jackson 写流**。

| 端点 | 文件名（i18n） | 响应体 |
| --- | --- | --- |
| `/backup` | `export.file.name.configmap.export` = `配置组列表.json` | **ConfigMap 数组** |
| `/listenBackup` | `export.file.name.listen.export` = `tcp与udp配置组.json` | `{tcpListen, udpListen, ingressList}` |

## 2. `/backup` 流程

```
nameList 为 null ⇒ 置空列表
mapList = exportConfigMap(nameList)：
    nameList 非空 ⇒ **按名读**（读不到的**跳过**，只记日志）
    nameList 为空 ⇒ 列 **`labelSelector = "!ingress-lb"`**（不带该标签）的全部 ConfigMap
写 JSON 到响应流
```

## 3. `/listenBackup` 流程

```
list = 列 **`labelSelector = "ingress-lb"`**（**带**该标签）的 ConfigMap；失败 ⇒ 记日志、list 保持空
for cm in list:
    ingressLb = cm.labels["ingress-lb"]
    **值为 "true" ⇒ 跳过**
    suffix = ingressLb.split("-")[1]        // 没有 `-` ⇒ 异常被吞、跳过该 cm
    if (cm.name == "tcp-services-" + suffix) tcpListen = cm
    if (cm.name == "udp-services-" + suffix) udpListen = cm
返回 {tcpListen, udpListen, ingressList}
```

## 4. 三处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **两个端点的标签选择器相反** | `/backup` 用 `!ingress-lb`（**不带**），`/listenBackup` 用 `ingress-lb`（**带**）——同一控制器里两种口径 |
| 2 | **`ingressList` 恒为 `null`** | `ListenBackInfo` 声明了三个字段，但 `ingressList` **从不赋值**（实测③：响应里该键存在且为 null） |
| 3 | `ingress-lb` 值为 **`"true"` 的条目跳过** | 只有"标签值是 ingress 名"的条目才参与 `split("-")[1]` 匹配 |

> 与列表端点的差异（交叉验证点）：`GET /v1/config_map` 会**硬编码排除** `kube-root-ca.crt`，
> 而 `/backup` 的空名单分支**不排除**——实测② 里 `kube-root-ca.crt` **出现在导出结果中**，
> 实测①（列表）里它被排除。两个代码路径确实不同。

## 5. 实现与验证

**实现**：`IServiceQueryService.exportConfigMaps` / `exportListenConfigs` +
`ConfigMapController` 两个附件端点 + `ListenBackInfoEntity`；
新增端口 `listConfigMapsWithIngressLb`（带标签）——`/backup` 复用已有的
`readConfigMaps`（按名读、跳过失败）与 `listConfigMapsWithoutIngressLb`（不带标签）。
文件名走 `MessageSource` 取 i18n，并按本项目既有约定做 `filename*=UTF-8''<urlencode>`。

### 5.1 离线：`ConfigMapExportTest` 8 例，全量 **536** 条 0 失败

覆盖：按名读 vs 空名单两条分支；`/listenBackup` 的 tcp/udp 匹配、`"true"` 跳过、
**无 `-` 的标签值不致命**、集群失败 ⇒ 结果为空、`ingressList` 恒 null、返回的就是集群快照本身。

### 5.2 在线：真集群（`cm-b1`/`cm-b2` 无标签；`ing-abc`/`tcp-services-abc`/`udp-services-abc` 带 `ingress-lb=ing-abc`）

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | `POST /backup ["cm-b1","ghost"]` | `200`；`Content-Type: application/json`；附件名解码为 **`配置组列表.json`**；正文只含 `cm-b1`（含 `data`） |
| ② | `POST /backup []` | `200`；正文 `[cm-b1, cm-b2, **kube-root-ca.crt**]` ⇒ 空名单分支**不排除**内置 ConfigMap |
| ③ | `POST /listenBackup` | `200`；附件名解码为 **`tcp与udp配置组.json`**；正文键恰为 `['ingressList','tcpListen','udpListen']`，`tcpListen=tcp-services-abc`、`udpListen=udp-services-abc`、`ingressList=null` |

### 5.3 收尾

5 个探针 ConfigMap、临时 NodePort、后端进程全部回收（`gaop-namespace` 回到只有 `kube-root-ca.crt`）；
本轮不写库。

## 6. 未实现（有意）

`POST /import` 与 `POST /importListen`：两者都要在逐条失败时写"恢复日志"（厂商的
`BackupLogService.sendRecoveryLog` 会往**内存列表**追加并推 WebSocket `/topic/recoveryLog`，
且受一个 `RECOVERY` 模式开关控制）。这需要先决定是否引入该日志/推送机制，本轮未做。
