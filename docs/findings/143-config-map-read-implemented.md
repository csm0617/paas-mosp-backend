# `GET /v1/config_map/{name}`、`/{name}/exist`、`/relationService` 规格 + 实现（第 150 轮）

**证据等级**：〔代码事实〕（厂商 `ConfigMapController.isExistConfigmap:98-110` / `configmap:155-182` /
`configMapRelationService:363-432` 逐行读）+〔运行实测〕（真集群 ConfigMap + Deployment 卷挂载）。2026-10-14。

## 1. 三个端点

```java
@GetMapping("/{name}/exist")   public Response<Boolean> existConfigmap(@PathVariable String name)
@GetMapping("/{name}")         public Response<IoK8sApiCoreV1ConfigMap> configmap(@PathVariable String name)
@GetMapping("/relationService") public Response<List<ConfigMapInfo>> configMapRelationService(@RequestParam String name)
```
`ConfigMapInfo{configMapName, **config**}`,`RelationServiceInfo{serviceName, mountPath}`
（注意列表字段名是 **`config`**，不是 `relationServiceList`）。

## 2. 流程

**`/{name}/exist`**：读 ConfigMap ⇒ true；**404 ⇒ false**；其它 K8s 错误 ⇒ 500 `configmap.system.error`。

**`/{name}`**：`name` 空白 ⇒ 400 `configmap.name.must.not.empty`；不匹配 **`[-._a-zA-Z0-9]+`** ⇒
400 `configmap.name.not.invalid`；读不到（404）⇒ 400 `configmap.not.exist`（带 name）；
其它 K8s 错误 ⇒ 500 `configmap.find.configmap.api.failed`；其余异常 ⇒ 500 `configmap.info.error`。

**`/relationService`**：
```
① 列 Node 找带 `node-role.kubernetes.io/<node.label>` 标签的节点、取第一个 address 的 ip（**只用于打日志**）
   —— 列 Node 失败 ⇒ 直接返回**空列表**
② 读该 ConfigMap；读不到 ⇒ 返回**空列表**（200）
③ 把它的 **data 的每个 key 当作 subPath 名**放进 map（初值空列表）
④ 遍历**所有 Deployment**：若某卷的 `configMap.name == name`：
     再遍历容器 → volumeMounts：`mount.name == 卷名` 且 `subPath` 非空且 `subPath ∈ map`
       ⇒ map[subPath].add({serviceName=Deployment 名, mountPath})
⑤ 转成 `List<ConfigMapInfo>`，按 `configMapName`（即 key）**排序**
```

## 3. 三处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **没人挂载的 key 也会出现**（`config` 为空数组） | ③ 先把所有 key 建好，④ 只往里追加 ⇒ 实测③ 里 `log.conf` 的 `config` 是 `[]` |
| 2 | **结果按 key 排序** | ⑤ `Comparator.comparing(ConfigMapInfo::getConfigMapName)` |
| 3 | 名称校验用的是 **`[-._a-zA-Z0-9]+`** | 而 i18n 文案却写着"需满足正则：`^[a-z0-9-]+$`"——**文案与代码不一致**（厂商如此，照抄；实测② 里 `bad name` 被拒） |

## 4. 一处主动简化（`deviations.md` B23）

厂商第 ① 步列 Node 取 master ip，但**那个 ip 只用于 `log.info`、不影响响应**。我们**省略这次调用**。
差异只出现在一条错误路径上：**列 Node 失败时厂商会提前返回空列表**（即使关联关系其实是有的）；
我们仍然返回真实关联。这条"厂商在无关调用失败时丢弃正确结果"的行为，判断为**不值得照抄**。

## 5. 实现与验证

**实现**：`IServiceQueryService` 加 `configMapExists` / `queryConfigMap` / `queryConfigMapRelations` +
`ConfigMapController` 三个端点 + `ConfigMapInfoDTO`/`ConfigMapInfoEntity`；
新增端口 `findConfigMap`（**404 返回空 Optional、其它错误抛异常**，对应厂商的两种分支）与
`listDeploymentSnapshots`（只列 Deployment，与厂商 `getV1Deployments` 一致）。

### 5.1 离线：`ConfigMapReadTest` 11 例，全量 **516** 条 0 失败

覆盖：exist 的三种结果；名称空/非法/不存在三种 400；关联关系的 key 建表 + subPath 命中 +
**没人挂载的 key 仍出现** + 多 Deployment 归集 + 排序 + ConfigMap 读不到/集群错误/无 data ⇒ 空列表。

### 5.2 在线：真集群（`cm-rel` 两个 key + `rel-svc` 用 subPath `app.conf` 挂载它）

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | `/{cm-rel}/exist`、`/{ghost}/exist` | `data:true` / `data:false` |
| ② | `/cm-rel` | `200`；`metadata.name=cm-rel`、`data={app.conf:a=1, log.conf:b=2}` |
| ③ | `/ghost` | `400 配置组不存在: ghost` |
| ④ | `/bad%20name` | `400 配置名称不合法，需满足正则：^[a-z0-9-]+$` |
| ⑤ | `/relationService?name=cm-rel` | `200`；**`[{configMapName:"app.conf", config:[{serviceName:"rel-svc", mountPath:"/etc/app/app.conf"}]}, {configMapName:"log.conf", config:[]}]`** ⇒ 第 3 节 1/2 实测确认 |
| ⑥ | `/relationService?name=ghost` | `200`，`[]` |

### 5.3 收尾

探针 ConfigMap/Deployment、临时 NodePort、后端进程全部回收；本轮不写库。
