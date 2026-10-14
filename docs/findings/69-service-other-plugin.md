# `GET /v1/service/otherPlugin`：**三类列表的集合全部对拉一致**（非空参考）

**证据等级**：〔运行实测〕（对拉）+〔代码事实〕。2026-10-14。

## 1. 实测

| 列表 | vendor | mine | `namespace/name` 集合 |
| --- | --- | --- | --- |
| `daemonSetList` | 4 | 4 | **完全相同** ✅ |
| `deploymentList` | 15 | 15 | **完全相同** ✅ |
| `statefulSetList` | 1 | 1 | **完全相同** ✅ |

响应键也一致（`daemonSetList`/`deploymentList`/`statefulSetList`）。这是服务切片里**第二个拿到非空对拉的新端点**
（第一个是 `serviceAuth`），而且参考是**跨命名空间**的 20 个对象。

## 2. 厂商规则（`ServiceController:4058-4090`）

```
fieldSelector = "metadata.namespace!=gaop-namespace"
labelSelector = "!system-network,!log,app.kubernetes.io/instance!=prometheus,app!=harbor,tier!=control-plane"
三类：DaemonSet / Deployment / StatefulSet，**跨全部命名空间**；
某一类抛 ApiException → **只记日志、该类返回空列表**（不是整体失败）
```

## 3. 一个实现取舍：**没把选择器交给 API server，而是在内存里等价复刻**

fabric8 7 在"跨命名空间列举"这条链上不方便直接带 `fieldSelector`/`labelSelector`
（我先试了 `withFieldSelector(...)` 与 `ListOptionsBuilder`，两版都编译不过）。
于是改成：跨命名空间全量列举 + **在内存里严格复刻 k8s 选择器语义**：

| 选择器 | 语义（**容易凭直觉写错**） |
| --- | --- |
| `metadata.namespace!=gaop-namespace` | 排除 gaop-namespace |
| `!system-network` / `!log` | 该**键不存在** |
| `k!=v` | 键不存在**或**值不等于 v（**对缺失键也成立**） |

最后一条是关键：`app!=harbor` **不会**排除"没有 `app` 标签"的对象。
所以"什么标签都没有"的工作负载**会被保留**——这正好解释了 vendor 的 15 个 Deployment。

**这条取舍的风险**是"内存过滤 ≠ API server 语义"，所以我用**对拉结果集合**来兜底验证：
三类列表的 `namespace/name` 集合与厂商完全相同 ⇒ 在本集群上语义等价已证明。
另外用 4 条单测把语义本身钉住（空标签集保留、gaop-namespace 排除、四个禁用键值排除、同键不同值保留）。

## 4. 与其它端点的对照

- `serviceAuth`（`{runningDeployment,total,count,services}`）：过滤 + 装配复用列表端点；
- `otherPlugin`（三类**原始 K8s 对象**）：**不做装配**，直接把 K8s 对象序列化下发。
  两者都是"列表"，但一个发领域视图、一个发原始对象——**别把装配器套上去**。

## 5. 状态

服务切片 **9/33**；测试 **41 类 244 条全绿**（上一轮 240）。
临时 NodePort 已删、探针 0 残留、进程清零、工作树干净。
