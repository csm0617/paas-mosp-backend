# `POST /v1/software/existConfigmap` 规格 + 实现（第 140 轮）

**证据等级**：〔代码事实〕（厂商 `SoftwareController:738-744` + `SoftwareService.existConfigMapBySoftwareOrService:3834-3913` +
`setNameSetBySoftware:4093-4115` 逐行读）+〔运行实测〕（真集群 ConfigMap/Deployment）。2026-10-14。

## 1. 端点

```java
@PostMapping("/existConfigmap")
public Response<Boolean> existConfigMapBySoftwareOrService(@RequestBody ExportConfig exportConfig)
```
`ExportConfig{type, key}`——`type` 为 `software` / `service`，`key` 是对应的 **软件 id 列表** / **Deployment 名列表**。
前端（`umi.*.js`）把它当普通 POST 调：`data = requestBody`。

## 2. 流程

```
nameSet = HashSet

type == "software"：
    逐个 key（软件 id）：
        读该软件（**读不到就跳过**）→ 取它的 g_software_app.app_name →
        逐个 Deployment 读 spec.template.spec.volumes[*].configMap.name 收进 nameSet
        （Deployment 读不到 ⇒ 记日志后继续下一个 app）
        只要 nameSet 非空 ⇒ return true
    return false

type == "service"：
    逐个 key（Deployment 名）：
        读该 Deployment 的 volumes 收集 configMap 名（读不到 ⇒ continue）
        只要非空 ⇒ return true
    return false

其它 type ⇒ return false
```

⇒ **只要有一个 key 收集到名字就 true**（边收集边判，不是收集完全部再判）。

## 3. 两处必须注意的地方

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **`key != null \|\| key.size() != 0` 是个 bug** | 用 `\|\|` 且后面直接调 `size()` ⇒ `key` 缺失时 **NPE ⇒ 500**；而且这让"不带 key ⇒ 检查全部软件"那个分支**永远不可达**（`key != null` 为真时就已经进了第一支） |
| 2 | `software` 分支的"不带 key"支路还用 **`so.getKey()` 当 id 查**（`select where id = <该软件的 key>）| 明显是写错了字段；不过因为第 1 点，这一整段是**死代码** |

**我们的处理**（`deviations.md` B22）：`key` 为 null/空 ⇒ **返回 false**。
**不实现**那个死分支——实现它等于"替厂商发明一个它永远走不到的行为"。

## 4. 实现与验证

**实现**：`IServiceQueryService.existConfigMap(namespace, type, keys)` + 控制器端点 + `ExportConfigDTO`；
新增端口 `readDeploymentConfigMapNames`（不存在/读失败 ⇒ 空列表，与厂商"记日志跳过"等价）；
复用 `softwareRepository.queryById` / `queryAppNamesBySoftwareId`。

### 4.1 离线：`SoftwareExistConfigMapTest` 6 例，全量 **452** 条 0 失败

覆盖：software 分支"关联服务里有一个带 configMap 卷就 true"、全都不带就 false、**未知 id 跳过并继续扫**；
service 分支直接按 Deployment 名；**null/空 key ⇒ false 且不碰集群**（B22）；未知 type ⇒ false。

### 4.2 在线：真集群（造 `cm-probe` ConfigMap + `cm-dep`（带 configMap 卷）/`nocm-dep`（不带），
并 seed `g_software{sw-cm}` 关联 `cm-dep`；验完全删）

| 请求 | 实测 |
| --- | --- |
| `{"type":"service","key":["cm-dep"]}` | `200 data:true` |
| `{"type":"service","key":["nocm-dep"]}` | `200 data:false`（没有 configMap 卷） |
| `{"type":"service","key":["ghost"]}` | `200 data:false`（Deployment 不存在） |
| `{"type":"software","key":["sw-cm"]}` | `200 data:true`（经 `g_software_app` 走到 `cm-dep`） |
| `{"type":"software","key":["ghost"]}` | `200 data:false` |
| `{"type":"software","key":[]}` | `200 data:false` ⇒ **不 NPE**（B22；厂商此处 500） |
| `{"type":"other","key":["cm-dep"]}` | `200 data:false` |

### 4.3 收尾（含一次环境事故）

探针 Deployment/ConfigMap 与 `g_software`/`g_software_app` 行已删、临时 NodePort 与后端进程已回收
（复核：`g_software`=0、`g_software_app`=0、`g_user`=1、残留探针资源 0）。

**环境事故**：第一次执行清理时集群 API 又不可达（TLS handshake timeout），**整批删除与 SQL 全部失败**——
所以我在集群恢复后**重跑了一遍清理并逐项复核**。教训：清理命令的输出必须逐条看，
"没报错"和"真的删掉了"是两回事（这次 `tmp=0` 就是因为查询本身失败而返回 0，具有误导性）。
