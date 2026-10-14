# 配置组写类四端点 规格 + 实现（第 151 轮）

**证据等级**：〔代码事实〕（厂商 `ConfigMapController.createConfigMap:122-152` / `createConfig:276-315` /
`deleteConfig:246-274` / `replaceConfigMap:317-334` / `deleteConfigMap:337-358` 逐行读）
+〔运行实测〕（真集群 ConfigMap 增删改）。2026-10-14。

## 1. 四个端点

```java
@PostMapping                                  // body CreateConfigMapForm{name}
@PostMapping("/{name}/config")                // body CreateConfigInConfigMapForm{name, value}
@DeleteMapping("/{name}/config/{configName}")
@DeleteMapping                                // ?names=a,b
```

## 2. 流程与照抄点

### 2.1 `POST /`（建配置组）

```
name 空白 ⇒ 400 `configmap.name.must.not.empty`；不匹配 `[-._a-zA-Z0-9]+` ⇒ 400 `configmap.name.not.invalid`
**已存在 ⇒ 400 `configmap.existed`（带 name）**
body = `V1ConfigMap{metadata:{name}}` ⇒ createNamespacedConfigMap
失败 ⇒ 500 `configmap.create.failed`
```
**照抄点**：建的 ConfigMap **只有 `metadata.name`、没有 data**（实测①：集群里 `data` 为空）。

### 2.2 `POST /{name}/config`（新增/覆盖配置项）

```
form.name 空白/非法 ⇒ 两个 400
读 ConfigMap：**404 ⇒ 400 `configmap.not.exist`（带 ConfigMap 名）**；其它错误 ⇒ 500 `configmap.get.config.error`
data = 读到的 data；value 为**空 ⇒ 存空串**；同名**覆盖**
整份 replace（PUT）⇒ 失败 ⇒ 500 `configmap.request.api.error`
```
**照抄点**：`value` 为空（`isEmpty`，含 null 与 `""`）⇒ 存 **`""`**（实测③：集群里 `{"k1":"v1","k2":""}`）。

### 2.3 `DELETE /{name}/config/{configName}`（删配置项）

前置校验同上；**配置项不存在 ⇒ 400 `configmap.name.not.exist`（带配置项名）**（实测④）；
存在则删掉该键后整份 replace。

### 2.4 `DELETE /?names=`（删配置组）

```
names.split(",") ⇒ 逐个 **trim 后删**；删除失败/不存在 ⇒ 只记日志（不报错）
**成功时往结果里放的是未 trim 的原值**
```
**照抄点**（最有趣的一条）：`names=cm-w2, cm-w3` ⇒ 响应 `["cm-w2", " cm-w3"]`——**第二个值带着前导空格**
（实测⑤），而集群里两个都真的删掉了。不存在的名字 ⇒ `[]`（实测⑥）。

## 3. 实现与验证

**实现**：`IServiceQueryService` 加 `createConfigMap` / `upsertConfigEntry` / `deleteConfigEntry` /
`deleteConfigMaps` + `ConfigMapController` 四个端点 + `CreateConfigMapDTO`/`CreateConfigInConfigMapDTO`；
新增端口 `createConfigMap` / `replaceConfigMapData`（读→改 data→replace）/ `deleteConfigMap`；
复用了上一轮的 `findConfigMap`（404 ⇒ 空）做"不存在"分支。

### 3.1 离线：`ConfigMapWriteTest` 12 例，全量 **528** 条 0 失败

覆盖：创建的两个 400 + 已存在 400 + 正常创建；配置项名校验、ConfigMap 不存在、**空值存空串**、
既有键保留、读失败 ⇒ `configmap.get.config.error`、replace 失败 ⇒ `configmap.request.api.error`；
删键只删目标键、键不存在 ⇒ 400；删配置组的 trim/原值/失败吞掉三种情形。

### 3.2 在线：真集群

| 例 | 请求 | 实测 |
| --- | --- | --- |
| ① | `POST / {"name":"cm-w"}` | `200`；`metadata.name=cm-w`、**无 data**；集群里 `data` 为空 |
| ② | 同名再建 | `400 配置组已存在: cm-w` |
| ③ | `POST /cm-w/config {"name":"k1","value":"v1"}`、`{"name":"k2","value":""}` | `200`；data 依次 `{k1:v1}`、`{k1:v1, k2:""}`；集群里一致 |
| ④ | `DELETE /cm-w/config/k2`；`DELETE /cm-w/config/nope` | `200` data `{k1:v1}`；`400 配置名不存在: nope` |
| ⑤ | `DELETE /?names=cm-w2, cm-w3` | `200` **`["cm-w2", " cm-w3"]`**（第二个带前导空格），集群里两个都删掉了 |
| ⑥ | `DELETE /?names=nope-cm` | `200`，`[]` |

### 3.3 收尾

所有探针 ConfigMap（`cm-w`/`cm-w2`/`cm-w3`）已删（`gaop-namespace` 回到只有 `kube-root-ca.crt`）、
临时 NodePort 与后端进程已回收；本轮不写库。
