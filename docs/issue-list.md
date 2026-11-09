# 问题清单（paas-core）

**用途**：记录**厂商缺陷 / 环境踩坑**中，需要由**自研后端（paas-core）**在重写时正面解决或永久防住的问题。
本清单是**待办与验收口径**；契约差异本身在 [`deviations.md`](deviations.md)，证据在 `findings/`。

**状态图例**：
- `待解决` —— 自研侧还没有对应实现/测试，接片时必须处理；
- `部分解决` —— 模型/代码层已免疫，但缺端到端验收或口径未定；
- `已解决` —— 有实现 + 有测试 + 有运行实测，回填证据后不再复议；
- `待核实` —— 疑似偏离，需样本/在线对拉确认后再定性。

---

## ISSUE-01 · 命名端口（`port: http`）击穿厂商服务列表全链

| 字段 | 内容 |
| --- | --- |
| 来源 | 2026-11-09 用户报障：`GET /api/gaop/v1/software/overView` 返回 `{code:-1,msg:"系统错误"}` |
| 状态 | **部分解决**（模型层已免疫 + 有单测；缺集群端到端验收与 fail-soft 策略） |
| 证据 | [`findings/166`](findings/166-named-port-breaks-vendor-allservice.md)、`deviations.md` **A14**、`findings/04-migration-blockers.md` §4.3 |
| 前端侧处置 | **已修**：`paas-web` 的探针/Service 改数字端口（`paas-web 3e2887c`），并存期不再触发厂商缺陷 |

### 厂商侧缺陷（不复制）

`TenxService.allService` 把集群对象 `GSON.toJson → Jackson.readValue` 进自带旧模型，
`IoK8sApiCoreV1HTTPGetAction.port`、`IoK8sApiCoreV1ServicePort.targetPort` 声明为 **`Integer`**；
集群里只要存在一个用命名端口的工作负载（`probe.httpGet.port: http` 或 `targetPort: http`，**两者都是合法 k8s**），
转换即抛 `InvalidFormatException` → 被 `catch{log.error("转换失败")}` 吞掉 → `ServicesItem.deployment = null`
→ `filter(o -> o.getDeployment().getSpec().getReplicas() > 0).count()` 在 `allService:3179` NPE
→ 全局处理器输出 `系统错误`。

**影响不止概览**（`allPageService` 是服务列表总入口，至少 6 处调用）：`/v1/service` 列表及其变体、
`/v1/software/overView`、`/v1/pub/service_status`（免登录轮询）、库/模式列表——**一个租户写命名端口，
整个平台的服务列表功能全挂**。

### 自研侧要求（验收标准）

1. **同形输入不崩**：命名端口的 Deployment/Service 必须能正常进列表，`targetPort` 原样保留字符串形态。
   - 现状：fabric8 7.9.0 模型为 `IntOrString`，无 GSON→Jackson 二次转换；
   - 已有测试：`NamedPortDeserializationTest`（Service 具名 targetPort、Deployment 具名 tcpSocket.port）、
     `ServiceRuntimePortTest#workloadDrivenListKeepsWorkloadWithoutServiceAndPreservesNamedPort`（列表层保留 `"http"`）。
   - 运行实测（2026-11-09 21:36）：`NamedPortDeserializationTest` Tests run: 2 / 0 失败，
     `ServiceRuntimePortTest` Tests run: 1 / 0 失败。
2. **running 计数空值安全**：`getReplicas() != null && > 0`，不得拆箱 NPE
   （`ServiceQueryServiceImpl` 262 / 2398 / 2438 行）。
3. **不做 null 降级**：任何"解析失败就把对象置 null 继续算"的写法禁止。解析不了应当是
   **该条目的降级或明确错误**，不能污染整条列表。
4. **待补：fail-soft 落测试**——构造"某个工作负载字段/类型异常"的输入，断言列表仍 200（或给出可解释的
   单条告警），而不是整体 500。当前没有这条用例。
5. **待补：集群端到端验收**——在 `gaop-namespace` 临时起一个带命名端口的工作负载，跑
   `/v1/service`、`/v1/software/overView`、`/v1/pub/service_status` 三端点 + 核对 `runningDeployment`，
   把响应样本存进 `docs/findings/runtime-samples/`（用完即删）。当前只有单测，没有在线实测。

---

## ISSUE-02 · `runningDeployment` 的取值口径（分页后 vs 全量）

| 字段 | 内容 |
| --- | --- |
| 来源 | 2026-11-09 排 ISSUE-01 时顺带对读源码发现 |
| 状态 | **待核实**（本环境 11 个工作负载全部 `replicas≥1`，两种口径结果相同，尚未暴露） |
| 证据 | 厂商反编译 `TenxService.allService`：`setTotal(services.size())` → **`runningDeployment` 计数（2349 行）** → `skip/limit` 分页（2357）→ `setCount`。即厂商**按分页前的全量**算 |

**观察**：自研实现 `ServiceQueryServiceImpl.queryGaopServiceList`（262 行）与
`queryServiceList`（2398 行）都是在 `pageItems`（**已分页**）上计数。当"部分运行 + 分页只看首页"时，
两侧 `runningDeployment` 会不一致。

**待办**：拿一份"总条数 > 页大小且部分运行"的样本对拉（或按反编译语义直接改成全量口径），
再决定是修正实现还是登记为 L3 偏离。改动会让现有单测的期望值变化，需同步更新。

---

## 变更记录

| 日期 | 变更 |
| --- | --- |
| 2026-11-09 | 建立清单；入 ISSUE-01（命名端口，来源当日报障，前端清单已规避、后端待补验收）、ISSUE-02（runningDeployment 口径待核实） |
