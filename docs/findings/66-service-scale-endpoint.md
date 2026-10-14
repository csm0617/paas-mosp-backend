# `PUT /v1/service/scale`：实现 + 独立 namespace e2e + **一处刻意保留的偏离**

**证据等级**：〔运行实测〕（独立 namespace + 对拉）+〔代码事实〕。2026-10-14。

## 1. 独立 namespace 实测（`paas-verify-sc`，用完即删）

| 用例 | 结果 |
| --- | --- |
| A：`scale 1→3` | 200；响应 `data` 是**整份 patch 后的 Deployment**（`spec.replicas=3`、`kind=Deployment`、`metadata.name=sc-svc`）；集群实际 replicas **= 3** ✅ |
| B：服务不存在 | 500 / `code=500`（与厂商同码） |

**响应形状值得记一笔**：这个端点返回的不是"成功布尔"，而是**整个 K8s Deployment 对象**
（厂商 `Response<IoK8sApiAppsV1Deployment>`）。我们直接回 `toSnapshot(deployment)` 的 Map，字段与 K8s 一致。

## 2. 一处**刻意保留**的偏离（记录，不修）

同样的失败用例（服务不存在）两侧对比：

| | 状态码 | `code` | `msg` |
| --- | --- | --- | --- |
| 厂商 | 500 | 500 | **空字符串** |
| 我们 | 500 | 500 | `Failure executing: GET at: https://…`（fabric8 原始异常消息） |

厂商是 `throw new SystemException(statefulsetEx.getLocalizedMessage())`，而该异常在此场景下
`getLocalizedMessage()` 为空 ⇒ 下发空消息。我们是 `SystemException(e.getMessage())`，拿到了 fabric8 的消息。

**决定：保留我们的消息**。理由：状态码与 `code` 已一致；把消息清空只会**丢掉诊断信息**，对前端无益
（该字段本来就不承载业务语义）。**但这是偏离，必须记在案**——不写成"完全一致"。

## 3. 与前面几个写端点的对照（三个端点的边界行为都不同）

| 端点 | 读不到目标时 | 空值语义 | 响应 |
| --- | --- | --- | --- |
| `hostName` | **回退 StatefulSet** | 两边空白 → 静默 no-op | `true` |
| `priority` | **不回退**，400 | 空白 → 主动设空串 | `true` |
| `scale` | **回退 StatefulSet**，都失败 → 500 | 不适用（int） | **整份对象** |

三个端点都长成"读 Deployment → 改一个字段 → patch"，但**边界行为两两不同**。
这张表就是给"不要按上一个套下一个"用的。

## 4. 状态

服务切片 **8/33**；其中 3 个写端点（`hostName`/`priority`/`scale`）都有**独立 namespace 的端到端证据**。
全量 **38 类 236 条全绿**；临时 namespace 已删（NotFound）、NodePort 已删、进程清零。
（本轮未新增单测：`scale` 的核心风险在真实 K8s 交互，已由隔离环境用例覆盖；
mock 单测只能复述实现，价值低于那两条真实验证。）
