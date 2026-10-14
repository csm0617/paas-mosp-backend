# 重写现状总览（第 76 轮刷新）——接替 `findings/67`（第 59 轮）

**盘点时间**：2026-10-14。**代码事实**：**57 个端点映射**（服务 13 / 用户 12 / 软件 10 / 战位 6 / 子角色 1 / 授权 5 / Shell 6 / Auth 2 / 影像 1 / 健康 1）、
**273 条用例全绿**、**104 次提交**、**80 篇 findings**。工作树干净、无残留进程/临时资源/验证 namespace。

## 一、第 59 → 76 轮之间补上的东西

| 域 | 新增 | 验证方式 |
| --- | --- | --- |
| **K2 跨域** | 授权列表、端口映射读、端口映射写、软件申请码、取消授权、批量激活 | **对拉**（多为逐字节/整个响应体相等）；批量激活用**自造 RSA 密钥对 + 临时 `g_auth` 公钥行**验到成功路径 |
| **服务切片** | `hostName`、`priority`、`scale`、`patchCommand`、`cancelStartUp`、`startUp`、`otherPlugin`、`restartInfo`（13/33） | 读端点**对拉**；写端点**独立临时 namespace 端到端**（六个） |
| **影像域** | `projectList`（按 **K3s** 实现重写） | **在线对拉逐字节相同** |
| **全栈复验** | 第 68 轮用**厂商原样前端 + 我们的后端**重跑用户管理页 | 表格内容与厂商**逐字节相同** |

## 二、本轮新增的三条方法教训（比端点更值钱）

1. **多实现/多策略：先找"选择逻辑"，再读被选中的实现。**
   影像域我先读了 `K8sImageServiceImpl`（Harbor），而 `getImageServiceInstance()` **写死返回 k3s 实现**（查库）
   ⇒ 整版实现的数据源是错的。**不是读错代码行，是读错类**（`findings/79`）。
2. **配置默认值要从"运行产物"里读，不能猜。**
   `initPodConfig.image` 的默认值是从厂商**运行中的 jar** 里解出 `application.yml` 读到的
   （`${INITIMAGE:curl-alpine:latest}`）——猜出来的形状对、内容错，而且不会报错（`findings/83`）。
3. **"假差异"比"漏测"更危险。**
   第 64/72 轮两次踩到：共享状态的顺序对拉（前一次成功写入污染后续前置状态）、错误响应整体比对
   （`data.trace` 的类名/行号天然不同）。两次都让人想去"修"本来正确的地方。**已写进方法清单**：
   错误路径比 `code`/`msg`/业务字段，**不比 `trace`**；共享可变状态的对拉必须每次重置前置状态。

## 三、仍然卡住的（按原因分类，不是"还没做"）

| 类别 | 具体 | 原因 |
| --- | --- | --- |
| 依赖缺失的外部设施 | 影像域 `repositories`/`tags`/`existRepositories`（**Docker Registry API**）、`imageStorage`（**SSH**） | 外部系统不可达；**厂商在参考环境里也拿不到**（`imageStorage` 返回 `0/0`）。已在 `findings/81` 画成依赖地图 |
| 厂商自身坏掉 | `GET /v1/service/{serviceName}`（NPE）、`event`/`baseEvent`（500）、`updateServiceRole`（写死代码） | 已定位到厂商代码行 |
| 破坏性写 | `node`（删节点）、`servicePods`（删 Pod） | 不验证、也不应验证 |
| 规模 | `node_port`/`nodePort`（`patchServicePorts` 100+ 行）、`patchService`（分支更多）、`updateVolumes`、`relationService`（K8s 富化 + `mappingInfo`） | 每个都需要整轮；`relationService` 还有一处**厂商 NPE**（app_name 匹配不到服务时无判空） |

## 四、下一步优先序

1. **服务切片继续**：`node_port`/`nodePort` 是最直接的下一步（与已做的端口映射是同一块业务），但要有整轮的预算；
2. **K2 `relationService`**：可在探针 `g_software_app` 行上对拉（表当前为空），但要先决定"是否照抄那处 NPE"；
3. **把"不可验证项"整理进 `docs/deviations.md`**（影像域的外部依赖刚补上 C2b，registry/SSH 还需补）；
4. 前端 e2e 定期复验（第 68 轮那套流程可重放）。
