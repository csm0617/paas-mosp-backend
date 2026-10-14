# `POST /v1/service/priority`：实现 + 独立 namespace e2e + **错误契约一次对上**

**证据等级**：〔运行实测〕（独立 namespace + 对拉）+〔代码事实〕。2026-10-14。

## 1. 独立 namespace 实测（`paas-verify-pr`，用完即删）

| 用例 | 结果 |
| --- | --- |
| A：`level=high-priority` | 200 `data:true`，`priorityClassName` **真的变成 `high-priority`** ✅ |
| B：`level=`（空白） | 200，`priorityClassName` **变成空串**（不是 null）✅ |
| C：服务不存在 | **400**，文案 `修改服务优先级失败，无法读取到该服务的配置信息` |
| C 对拉 | **厂商逐字相同**（同 400、同文案）✅ |

## 2. 与 `hostName` 端点的**两处不同**（照抄时最容易写错）

| | `hostName` | `priority` |
| --- | --- | --- |
| 读不到 Deployment | **回退 StatefulSet** | **不回退**，直接 400 `service.change.priority.failed` |
| 空值语义 | 两边都空白 → **静默 no-op** | `level` 空白 → **主动设成空串** |

这两个端点长得像（都是"读 Deployment → 改一个字段 → merge-patch"），但边界行为完全不同。
我把它们写进同一篇文档就是为了下次不要再"按上一个的写法套下一个"。

## 3. 错误契约这次**一次对上**（与上一轮形成对比）

上一轮 `hostName` 我把错误契约猜成 500/`...host.name`，被对拉纠正成 400/`...host.pod`。
这一轮 `priority` 我**直接照代码里的键名写**，对拉结果**逐字一致**——包括中文文案
（`service.change.priority.failed` 这个键在我们既有的 i18n 包里本来就是对的）。

⇒ 差别不在"这次更小心"，而在于：**上一轮我是在没有对拉样本的情况下凭经验补了一个键名**；
这一轮键名是从代码里抄的。**能从代码抄的就别猜；必须猜的，一定要对拉。**

## 4. 测试

新增 3 条：设置 level、空白 level → 空串、不存在 → 厂商键**且不回退 StatefulSet**。
全量 **38 类 236 条全绿**（上一轮 233）。

## 5. 服务切片进度：**7/33**

`/v1/service`、`labels`、`updateName`、`gaop`、`serviceAuth`、`hostName`、`priority`。
其中 2 个写端点（`hostName`/`priority`）有**独立 namespace 的端到端证据**，
4 个有对拉证据。清理全部核验：临时 namespace 已删（NotFound）、NodePort 已删、进程清零。
