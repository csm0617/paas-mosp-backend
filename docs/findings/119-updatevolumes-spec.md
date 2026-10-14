# `POST /v1/service/{service}/updateVolumes` 规格（第 125 轮）

**证据等级**：〔代码事实〕（厂商 `ServiceController.updateServiceVolumes:818-947` 逐行读 + 三个 form 类 + `getStringRandom:291-304`）。2026-10-14。本轮**读全规格并实现**。

## 1. 端点与入参

```java
@PostMapping("/{service}/updateVolumes")
public Response<IoK8sApiAppsV1Deployment> updateServiceVolumes(@PathVariable String service,
                                                              @RequestBody UpdateServiceConfigmapForm form)
```

`UpdateServiceConfigmapForm`（无 `@JsonProperty` ⇒ **字段名即 JSON 键名**）：

| 字段 | 含义 |
| --- | --- |
| `configMapList[]` | `{dir, configmap, keys[]}` —— 一个 configmap 挂到 `dir` 下的若干 key |
| `hostPathList[]` | `{hostPath, podPath, readOnly}` —— 宿主路径挂到容器路径；`readOnly == 2` ⇒ 只读 |

返回**整份被 replace 后的 Deployment**（厂商转成它的 `IoK8sApiAppsV1Deployment`；我们返回 K8s 对象快照）。

## 2. 流程

```
1. 读 Deployment(service)：
     404 ⇒ 400 `service.service.name.not.found`（**带服务名做参数**）
     其它 ApiException ⇒ 只记日志（对象保持 null）
     对象仍为 null ⇒ 500 `system.exception`
2. try { 组装 volumes / volumeMounts } catch (任意异常) ⇒ 500 `service.file.mapping.or.mount.failed`
3. try { replaceNamespacedDeployment(整份) → 返回替换后的对象 }
     catch ApiException ⇒ 500 `service.update.service.config.failed`
     catch JsonProcessingException ⇒ 500 `system.exception`
```

## 3. 组装规则（**照抄点都在这里**）

### 3.1 configMapList → 先按 configmap 名**合并**

```
dataList = LinkedHashMap<configmap, List<{key, path=dir}>>
for item in form.configMapList:
    for key in item.keys: dataList[item.configmap].add({key, path = item.dir})
```
⇒ **同名 configmap 的多条会合并成同一个 volume**（`dir` 逐条取自己的）。

### 3.2 每个 configmap → 一个 volume + 若干 mount

| 项 | 规则 |
| --- | --- |
| volume 名 | `configmap == "tenxcloud-time-localtime"` ⇒ 就用这个名字；否则 **`"configmap-volume-" + 5 位随机字符** |
| 随机字符 | `getStringRandom(5)`：每位 `SecureRandom.nextInt(2)%2==0` 取 `a-z`，否则取 `0-9` |
| `configMap.name` | = configmap 名 |
| `configMap.items[]` | `{key: key, path: key}` —— **`path` 用的是 key，不是 `dir`**（容易看错） |
| volumeMount | `{name: volume 名, mountPath: dir + "/" + key, subPath: key}` |

### 3.3 hostPathList → 每个一个 volume + 一个 mount

| 项 | 规则 |
| --- | --- |
| volume 名 | `hostPath == "/etc/localtime"` ⇒ `"tenxcloud-time-localtime"`；否则 `"hostpath-volume-" + 5 位随机字符` |
| `hostPath` | `{path: hostPath, type: ""}`（**type 是空串，不是 null**） |
| volumeMount | `{name: volume 名, mountPath: podPath}`；**`readOnly == 2` 才置 true**（其它值连 `false` 都不写） |

### 3.4 落回工作负载（两处**替换式**语义）

```
spec.setVolumes(volumes)                        // **整份替换**，不是合并
containers.get(0).setVolumeMounts(volumeMounts) // **只改第一个容器**
```
⇒ 这个端点是"给定数据替换之前的数据"（`@Operation` 的 description 也这么写）；
**已有 volumes/挂载会被清掉**，且**第二个及以后的容器不动**。

## 4. 两处必须注意的边界

1. `containers.get(0)`：容器列表为空 ⇒ `IndexOutOfBoundsException` ⇒ 被第 2 步的 `catch (Exception)`
   吞成 500 `service.file.mapping.or.mount.failed`；
2. `readOnly` 是 `Integer`，厂商写的是 `if (form.getReadOnly() == 2)` ⇒ **拆箱**：
   `readOnly` 为 **null 时 NPE** ⇒ 同样被吞成 500。**我们主动收紧**（null 视为"非只读"），
   记入 `docs/deviations.md`（与 B13 同类：前端路径上不可达，照抄等于故意留一个 NPE）。

## 5. 验证计划

- **离线单测**（mock `IServiceRuntimePort`）：合并同名 configmap、`path=key` 与 `mountPath=dir/key`、
  `type=""`、`readOnly==2` 才 true、只改第一个容器、整份替换 volumes、localtime 的固定名、
  404 ⇒ 400 `service.service.name.not.found`、组装异常 ⇒ 500 `service.file.mapping.or.mount.failed`、
  replace 失败 ⇒ 500 `service.update.service.config.failed`；
- **在线独立命名空间**：造 Deployment（**两个容器**、已有旧 volume），调端点，核对
  `spec.volumes` 被整份替换、`containers[0].volumeMounts` 是新的、**`containers[1]` 没被动**，
  然后删命名空间。随机名无法逐字节对拉（厂商自己也不稳定），所以只比结构 + 固定项。

## 6. 实现与实测结果（第 125 轮）

### 6.1 离线：`ServiceUpdateVolumesTest` 10 例，全量 **368** 条 0 失败

覆盖：`items[].path = key` 而 `mountPath = dir + "/" + key`；同名 configmap 合成一个卷且各自保留 `dir`；
hostPath 的 `type` 是空串；`readOnly == 2` 才 true、`== 1` 与 `null` 都不写该字段；
localtime 两个固定名、其余 `<prefix>-volume-[a-z0-9]{5}`；空表单照样整份替换成空；
Deployment 不存在 ⇒ 400 `service.service.name.not.found`（**参数是服务名**）；
组装异常 ⇒ 500 `service.file.mapping.or.mount.failed`；replace 的 K8s 异常/返回 null ⇒ 500 `service.update.service.config.failed`。

### 6.2 在线：独立命名空间 `paas-verify-volumes`（Deployment **两个容器** + 已有 `old-volume`）

请求：
```json
{"configMapList":[{"configmap":"app-config","dir":"/etc/app","keys":["a.yml","b.yml"]},
                  {"configmap":"app-config","dir":"/etc/extra","keys":["c.yml"]}],
 "hostPathList":[{"hostPath":"/data/logs","podPath":"/var/log","readOnly":2},
                 {"hostPath":"/etc/localtime","podPath":"/etc/localtime","readOnly":1}]}
```

| 核对项 | 集群里的实际值 |
| --- | --- |
| 响应 | `200 {"code":0}`，`data.kind=Deployment`、`data.metadata.name=nps-svc`（整份替换后的对象） |
| `spec.volumes` | **3 个**：`configmap-volume-d22wk`（`configMap.name=app-config`，`items` **3 条** `{key,path}` 都是 `a.yml/b.yml/c.yml`）＋`hostpath-volume-ydpp0`（`hostPath{path:/data/logs, type:""}`）＋`tenxcloud-time-localtime`（`hostPath{path:/etc/localtime, type:""}`） |
| 旧卷 | **`old-volume` 已消失** ⇒ "整份替换"实测确认 |
| `containers[0].volumeMounts` | 5 条：`/etc/app/a.yml`(sub a.yml)、`/etc/app/b.yml`(sub b.yml)、`/etc/extra/c.yml`(sub c.yml) 都指向合并后的那个卷；`/var/log`→`hostpath-volume-ydpp0` **`readOnly:true`**；`/etc/localtime`→`tenxcloud-time-localtime`（**没有 readOnly 字段**） |
| `containers[1]`（sidecar） | **`volumeMounts` 仍为空** ⇒ "只改第一个容器"实测确认 |
| Deployment 不存在 | `400 服务no-such-svc不存在`（`{0}` 被服务名替换） |

⇒ 四处怪点 + 两处"替换式"语义全部在真集群上验到。**随机卷名无法逐字节对拉**（厂商自己每次也不同），
所以这里只比结构与固定项——这条限制与 `/v1/user/export` 的"文档属性"同类，已按证据分级记录。

### 6.3 收尾

验证命名空间、临时 NodePort、代理、后端进程全部回收；实验室数据未动
（`g_user`=1、`g_service_port`=0、`g_software_app`=0）。

### 6.4 一处主动收紧（`deviations.md` B15）

`hostPathList[].readOnly` 为 **null** 时：厂商写的是 `getReadOnly() == 2`（`Integer` **拆箱**）⇒ **NPE** ⇒
被外层 `catch (Exception)` 吞成 500 `service.file.mapping.or.mount.failed`。
我们按"非只读"处理（不写该字段），与 B13 同类：前端必传该字段，照抄等于故意留一个 NPE。

