# 剩余端点的**规模地图**（第 80 轮）：每个都够一整轮，且各自卡在哪

**证据等级**：〔代码事实〕（逐个读到"能估出规模与依赖"的程度）。2026-10-14。本轮无业务代码改动。

## 1. 为什么要画这张图

第 78 轮我按"规格轮 → 实现轮"的方式把 `node_port` 一轮做完（零返工）。但**其余候选端点逐个看下来，
没有一个能在半轮里做完**。把这件事写清楚，可以避免以后（包括我自己）又去"顺手试一个"然后半途而废——
本会话已经因为"边读边写"和"读错实现类"各返工过一次。

## 2. 地图

| 端点 | 规模与卡点 | 能否对拉 |
| --- | --- | --- |
| `GET /v1/service/serviceInfoByNode` | **卡在 `getServiceItemInfo`**：解析 `resources.limits/requests` 算 CPU/内存/GPU 排序值，再取**监控数据**（`serviceMonitorPool`）填 `podInfoList`/`belongNodeList`/`mappingInfo` ⇒ 依赖**监控子系统**，不是"读几张表" | 只能拿"可解释的 0"（注解缺失，见 `findings/63` 更正） |
| `POST /v1/service/{serviceName}/nodePort` | **管的是另一个 Service**（`<name>-nodeport`）：不存在就用 `AppService.generateServiceDoc` 造、`getNodePortUsed()` 找可用端口、再建/改 ⇒ ~150 行 | 独立 namespace 可验 |
| `PUT /v1/service/patchService` | 按 `type` 分支 + Deployment/StatefulSet 回退 + nodePort 设置 ⇒ ~100 行 | 独立 namespace 可验 |
| `POST /v1/service/{service}/updateVolumes` | 重建 `volumes`/`volumeMounts`（configmap、hostpath），牵动 PodSpec ⇒ ~120 行 | 独立 namespace 可验 |
| `GET /v1/role/{roleId}/software` | CS 类型拼 `gaop://cs?...`；非 CS 查 web 入口 + 读 **Deployment 现场状态** ⇒ ~60 行 | 需要会话 + admin 校验；数据可探针 |
| `GET /v1/software/relationService/{id}` | `getServiceMappingInfo`（端口→内网/NodePort/Ingress 三类信息）+ Pod 富化 ⇒ ~80 行；**还有一处厂商 NPE**（app_name 匹配不到服务时无判空） | 探针 `g_software_app` 行可对拉 |
| 影像域其余（`repositories`/`tags`/`imageStorage`） | 依赖 **Docker Registry API** / **SSH**，参考环境不可达 | ❌ 不可对拉（`findings/81`） |

## 3. 结论与下一步

1. **每个剩余端点都需要一整轮**，并且**都适合"规格轮 → 实现轮"**（本会话已两次零返工：`serviceAuth`、`node_port`）；
2. **优先级建议**：`POST /{serviceName}/nodePort`（与刚做完的 `node_port` 同一块业务，且能独立 namespace 验证）
   → `GET /v1/role/{roleId}/software`（K1 的最后一块读端点）→ `PUT /patchService` / `updateVolumes`；
3. `serviceInfoByNode` 建议**明确降级**：它的价值主要取决于**监控子系统**是否要做，单为它实现一个空壳没有意义；
4. 影像域其余端点**不建议动**（无对端，`findings/81` 已画图）。

## 4. 状态

本轮无业务代码改动；全量 **278 条用例全绿**；工作树干净、无残留资源。
