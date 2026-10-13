# 服务切片：`GET /v1/service/labels` 对拉一致 + 服务域端点清单

**证据等级**：〔运行实测〕（对拉厂商，按集合比较）+〔代码事实〕。2026-10-14。

## 1. 本轮完成：`GET /v1/service/labels`

| 项 | 实测 |
| --- | --- |
| 响应 code | 两侧都是 0 |
| label 键数 | 两侧都是 **7** |
| 键集合 | **完全相同** |
| 每个键的值集合 | **完全相同** |
| 条目形状 | `{labels, name}` 两键，两侧一致 |

**注意"按集合比较"是必须的**：厂商用 `HashMap` 聚合再 `forEach`（`ServiceController.getLabels:964-990`），
**同一环境两次调用的数组顺序都可能不同** ⇒ 这个端点不存在"逐字节一致"，
契约只能定在"键集合 + 每个键的值集合"。这是本轮唯一一个**不能用字节对拉**的端点，
所以我把比较方式本身写进了文档，避免以后有人拿 diff 去判它失败。

两个**容易读反**的字段语义（本轮实测 + 读码确认）：
- `name` 是 **label 的键**（不是节点名）；
- `labels` 是该键在集群里的**去重值**（不是"标签串"）。

## 2. 覆盖的方式（为什么这条能离线测）

`ServiceRuntimePort.queryNodeLabels()` 把 fabric8 的 Node 对象翻成"键 → 去重值列表"，
端口只依赖 gateway。离线用 mock 的 Node 列表覆盖三条：按值去重、多节点合并、**节点没有 labels 时忽略**
（厂商是 `catch(NullPointerException) 忽略`）。

## 3. 服务域端点清单（本轮顺带勘查，作为后续开工依据）

厂商 `ServiceController` 有 **30+ 个端点**，我们目前只做了 1 个（`GET /v1/service`），本轮 +1（labels）。
按"依赖什么"分三类：

| 类别 | 端点（举例） | 说明 |
| --- | --- | --- |
| **只读 + K8s 现场态** | `GET /v1/service/{serviceName}`（`ServicesItem`，字段很多）、`/native`（YAML）、`/gaop`、`/serviceInfoByNode` | 可用 mock 客户端做离线契约测试；真值要对拉 |
| **写 K8s（改集群）** | `POST /v1/service`（创建）、`DELETE`（删除）、`/patch`、`/patchCommand/{serviceName}`、`/patchService`、`/updateVolumes`、`/{serviceName}/nodePort` | **有真实副作用**：要么在隔离 namespace 做，要么只做"参数校验 + 报文构造"这一层并如实标注未实测 |
| **改 K8s annotation 上的业务元数据** | `/updateServiceRole`、`/updateName` | 语义上是"服务 ↔ 软件标识"的关联，落点在 K8s annotation 上，不是数据库 |

**下一轮的取舍建议**（要在这三类里各挑一条，避免整轮都停在勘查）：
从第一类挑 `GET /v1/service/{serviceName}`（它是服务详情抽屉的前置，也是唯一能纯离线把形状钉住的），
从第三类挑 `/updateName`（只改 annotation，可在隔离 namespace 上做真实验证）。

## 4. 测试

新增 2 条（按 label 键聚合并去重、节点无 labels 时忽略）。全量 `mvn test` **33 类 216 条全绿**（上一轮 214）。
集群只做了一次只读对拉，无探针写入；临时 NodePort 与进程都已清理。
