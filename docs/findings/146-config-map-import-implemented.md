# `POST /v1/config_map/import` 规格 + 实现（第 153 轮）

**证据等级**：〔代码事实〕（厂商 `ConfigMapController.importConfigMap:451-456` +
`SoftwareService.importConfigMap:3363-3403` + `BackupLogService.sendRecoveryLog:44-49` / `sendLog:51-71` 逐行读）
+〔运行实测〕（真集群 ConfigMap 导入）。2026-10-14。

## 1. 端点

```java
@PostMapping(value = "/import", consumes = {"multipart/form-data"})
public Response<Boolean> importConfigMap(@RequestPart MultipartFile json) throws IOException
```
上传的 JSON 是 **ConfigMap 数组**（`List<V1ConfigMap>`）。

## 2. 流程

```
mapList = Jackson.readValue(上传流, List<V1ConfigMap>)
if (mapList 非空):
    for cm in mapList:
        name = cm.metadata.name
        exist = 读得到同名 ConfigMap ? true : false        // 读异常 ⇒ 记日志（含"恢复日志"）+ 当作不存在
        cm.metadata.resourceVersion = **null**
        cm.metadata.uid = **null**
        if (exist) replace(cm) 失败 ⇒ 只记日志，continue
        else       create(cm)  失败 ⇒ 只记日志
**恒返回 true**
```

## 3. 三处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **恒返回 `true`** | 逐条失败**全部吞掉**（"更新/新增下一个"），HTTP 层看不出部分失败（实测①：第三条名字非法，接口仍 `200 data:true`，该条未创建） |
| 2 | **写库前把 `metadata.resourceVersion` 与 `metadata.uid` 置 null** | 否则 create/replace 会带着旧版本号冲突（实测①：`cm-i1` 带 `uid=bogus-uid` 上传，集群里的 uid 是**真实** uid） |
| 3 | 存在 ⇒ **replace**、不存在 ⇒ **create** | 实测②：再次导入同名的 `cm-i1`（data 从 `{a:1}` 改成 `{a:9}`）后集群里就是 `{"a":"9"}` |

## 4. 一处有意省略：恢复日志

厂商在失败时调用 `BackupLogService.sendRecoveryLog(...)`。追进实现后可以确定它的**可观测影响为零**（默认配置下）：

- `sendRecoveryLog` 先判静态开关 **`RECOVERY`（`public static Boolean RECOVERY = false`，默认关）**；
- 即使开着，`sendLog(type=2)` 也只是往**内存列表** `recoveryLogs` 追加 + 推 WebSocket `/topic/recoveryLog`，
  **不写任何库表**；
- 开关只由 `PublicController:3378`（进入恢复模式）与 `:3508/:3544`、`DatabaseService:1768`（退出）改写，
  而这些端点我们尚未实现 ⇒ 我们的系统**永远不会进入恢复模式**。

⇒ 我们**不实现**该推送，行为等价于厂商默认配置。若将来实现恢复模式端点，需要补 `/topic/recoveryLog` 推送。

## 5. 实现与验证

**实现**：`IServiceQueryService.importConfigMaps` + `ConfigMapController` 的 `POST /import` +
端口 `createConfigMapObject` / `replaceConfigMapObject`（Map → fabric8 模型 → create/replace）；
"是否存在"复用 `findConfigMap`（404 ⇒ 空）。

**部件名兼容**：厂商的参数名是 `json`（未注解，靠编译参数名绑定）。我们优先取名为 `json` 的部件，
取不到则取**第一个**文件部件（实测③ 用 `file=` 上传也返回 `200 true`）——这样两种前端写法都能用。

### 5.1 离线：`ConfigMapImportTest` 7 例，全量 **543** 条 0 失败

覆盖：空/null 列表不碰集群；存在 ⇒ replace、不存在 ⇒ create；**写前清掉 `resourceVersion`/`uid`**；
读失败回落到 create 且继续；**逐条失败吞掉且恒 true**；缺 `metadata` 也不炸。

### 5.2 在线：真集群

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | 上传 3 条（`cm-i1` 带 `resourceVersion=999999`/`uid=bogus-uid`、`cm-i2`、名字非法的第三条） | `200 data:true`；`cm-i1` data `{a:1}`、**uid 是真实 uuid**、`cm-i2` data `{b:2}`；名字非法的**未创建** |
| ② | 再上传 `cm-i1`（data `{a:9}`） | `200 data:true`；集群里 `cm-i1` data = `{"a":"9"}` ⇒ 走的是 **replace** |
| ③ | 部件名改成 `file` | `200 data:true` ⇒ 兜底生效 |

### 5.3 收尾

探针 `cm-i1`/`cm-i2`、临时 NodePort、后端进程全部回收（`gaop-namespace` 回到只有 `kube-root-ca.crt`）；本轮不写库。

## 6. 未实现（有意）

`POST /v1/config_map/importListen`：它会**改 ingress Service 的端口**（读 ingress 服务、按
`port-0/port-1/port-status/port-health` 过滤后重建端口），属于高风险写操作，另行评估后再做。
