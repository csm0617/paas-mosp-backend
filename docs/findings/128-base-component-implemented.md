# `GET /v1/service/baseComponent` 规格 + 实现（第 135 轮）

**证据等级**：〔代码事实〕（厂商 `ServiceController.getKubeSystemInfo:3218-3311` 逐行读）
+〔运行实测〕（真集群 kube-system）。2026-10-14。

## 1. 端点

```java
@GetMapping("/baseComponent")
public Response<List<KubeSystemService>> getKubeSystemInfo(String nodeName)   // nodeName 无注解 ⇒ 必填 query 参数
```
前端：`url:"/v1/service/baseComponent", params.nodeName=e.nodeName`。

`KubeSystemService` 的 JSON 键只有两个：**`daemonSet`** 与 **`v1PodList`**（都是 `@JsonProperty`）。

## 2. 流程（三个组件，固定顺序）

```
weave-net           ：读 DaemonSet "weave-net"（kube-system）；pod 用 **name=weave-net** + spec.nodeName=<node>
kube-proxy          ：读 DaemonSet "kube-proxy"；              pod 用 **k8s-app=kube-proxy** + spec.nodeName=<node>
kube-keepalived-vip ：读 DaemonSet "kube-keepalived-vip"；      pod 用 **name=kube-keepalived-vip** + spec.nodeName=<node>
返回 [weave, kubeProxy] + (keepalived 的 pod 列表非空 ? [keepalived] : [])
```

## 3. 四处照抄点

| # | 点 | 说明 |
| --- | --- | --- |
| 1 | **`kube-proxy` 的标签选择器是 `k8s-app=`** | 另两个是 `name=`；三个组件的选择器**不统一** |
| 2 | **keepalived 没有 pod ⇒ 整条不出现** | 不是"出现但字段为 null"，是列表里**少一个元素**（读 pod 抛异常也一样省略） |
| 3 | **`v1PodList` 为空时字段保持 `null`** | 厂商只在 `size() != 0` 时才 `setV1PodList`；`daemonSet` 同理（读不到就是 null） |
| 4 | **任何 K8s 读失败都只记日志** | weave-net / kube-proxy 的读失败**不影响条目出现**（字段为 null）；所以这个端点**不会 400** |

另外：pod 过滤同时用了 **`spec.nodeName=<nodeName>`**（fieldSelector）与标签选择器。

## 4. 实现与验证

**实现**：`IServiceQueryService.queryBaseComponents(nodeName)` + 控制器端点；
新增端口 `readDaemonSetSnapshot` 与 `listPodsByLabelAndNode`（标签 + `spec.nodeName` 两个 selector）。

### 4.1 离线：`ServiceBaseComponentTest` 4 例，全量 **424** 条 0 失败

覆盖：固定顺序 + 三个组件各自的选择器（`name` / `k8s-app` / `name`）；keepalived 无 pod ⇒ 条目被省略；
pod 列表为空 ⇒ `v1PodList` 为 `null` 但**键仍在**；K8s 读失败 ⇒ 只记日志、weave 与 kube-proxy 照常出现。

### 4.2 在线：真集群 kube-system（**不建命名空间**）

本集群的实况：**没有 weave-net**（用的是 Calico）、有 `kube-proxy` DaemonSet、没有 keepalived。

| 例 | 实测 |
| --- | --- |
| ① `?nodeName=k8s-master` | `200 {"code":0}`，**2 条**（keepalived 被省略）；`[0]` weave-net：`daemonSet=null`、`v1PodList=null`（读不到 DaemonSet 也不报错）；`[1]` kube-proxy：**`daemonSet` 是真实 DaemonSet**、**`v1PodList=['kube-proxy-jqj6w']`**（`k8s-app=kube-proxy` + `spec.nodeName` 命中）；两条都**恰好只有 `daemonSet`/`v1PodList` 两个键** |
| ② `?nodeName=no-such-node` | `200`，仍是 **2 条**；两条的 `v1PodList` 都是 `null`（node 过滤生效）；kube-proxy 的 `daemonSet` **仍然非 null**（DaemonSet 读取与 node 无关） |

⇒ 第 3 节四点全部实测确认（① 同时验证了"读不到 DaemonSet 只记日志"与"keepalived 整条省略"）。

### 4.3 收尾

临时 NodePort 与后端进程已回收；本轮不建命名空间、不写库（实验室数据未动）。
