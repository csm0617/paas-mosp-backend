# 重写现状与交接（第 112 轮刷新）——接替 `findings/84`（第 76 轮）

**盘点时间**：2026-10-14。**代码事实**：**71 个 Mapping 注解**（服务 15 / 用户 19 / 软件 11 /
战位 8 / Shell 6 / 授权 5 / Auth 2 / 子角色 2 / 影像 1 / 健康 2）、
**304 条用例全绿**、**141 次提交**、**102 篇 findings**、`docs/deviations.md` **33 条**（A/B/C 三类）。
工作树干净、无残留进程/临时资源/验证 namespace。

## 一、第 76 → 112 轮补上的东西

| 域 | 新增 | 验证方式 |
| --- | --- | --- |
| **K1 角色/用户** | `GET /v1/role/{roleId}/software`、`GET /v1/user/list`、`GET /v1/user/access_permission` | 对拉（含 `access_permission` 六例全等、`user/list` 四例全等） |
| **K1 文件类（6 个中的 5 个）** | `import/template`、`export`、`backup`、`recovery`、`update_avatar` | 内容比对/POI 读回/可回滚探针；**`update_avatar` 连"再传一次变 UN-CHANGE"都复现了** |
| **影像域** | `projectList`（按 **K3s** 实现重写，逐字节对拉） | 在线对拉 |
| **服务切片** | `node_port`、`nodePort`（后者**发现厂商侧永远无法成功**）、`cancelStartUp`、`startUp`、`restartInfo` | 独立 namespace e2e |
| **定期复检** | 第 91、111 轮两次 e2e（用户页与厂商归档页逐项相等） | 厂商原样前端 + 我们的后端 |

## 二、本会话新增的方法教训（累计 8 条，新增 4 条）

1. **多实现/多策略：先找"选择逻辑"，再读被选中的实现**（影像域读错实现类，整版返工）；
2. **配置默认值从"运行产物"里读**（`initPodConfig.image` 来自厂商运行中的 jar）；
3. **"假差异"比"漏测"更危险**（共享状态顺序对拉、错误响应里的 `trace` 天然不同）；
4. **对端行为异常时，先查自己**——本会话**三次**踩到（`gaop` 空列表、`/v1/user/list` 的 401、
   `UID` 只读变量导致的"用户不存在"），每次看起来都像对端缺陷；
5. **二进制端点的"逐字节相等"会被元数据破坏**（xls 文档属性里的 OS 用户名）⇒ 内容比对 + 差异定位；
6. **在二进制文件里"看到词"≠"写入了字段"**（表头文案里就有 `admin`）⇒ 判定必须读回单元格；
7. **规格读到"无未知项"再动手**——本会话四次零返工（`serviceAuth`、`node_port`、`access_permission`、`user/list`）；
8. **"最后一块"不构成赶工理由**（`/import` 是 K1 最后一块，仍判为"单独一轮"）。

## 三、仍然卡住的（按原因分类）

| 类别 | 具体 |
| --- | --- |
| 环境限制 | **厂商 pod 的 Metaspace 只有 128m**，文件类请求会把它的 JVM 打爆（`findings/102`）⇒ 该 pod 规格下厂商文件类端点**无法可靠对拉** |
| 依赖缺失的外部设施 | 影像域 `repositories`/`tags`/`imageStorage`（Docker Registry / SSH）、`/v1/user/redirect`（OAuth）、`/tenx_user` |
| 厂商自身坏 | `/v1/service/{serviceName}`（NPE）、`event`/`baseEvent`（500）、`updateServiceRole`（死代码）、`POST /{serviceName}/nodePort`（**任何可达输入都无法成功**） |
| 破坏性写 | `/v1/service/node`（删节点）、`servicePods`（删 Pod）、`/v1/user/reset_user` |
| 规模 | `/v1/user/import`（最大单函数，单独一轮）、`patchService`（~100 行）、`updateVolumes`（~120 行）、`relationService`（~80 行，含厂商 NPE）、`serviceInfoByNode`（**依赖监控子系统，建议降级**） |

## 四、下一步优先序（每个都有规格，成本已知）

1. **`/v1/user/import`**（K1 最后一块）：六例验证 + 可回滚探针（规格见 `findings/100` §3'''''）；
2. **服务域 `patchService` → `updateVolumes`**：独立 namespace 可验（`findings/87` 已估规模）；
3. **`relationService`**：探针 `g_software_app` 行可对拉，但要先决定"是否照抄那处 NPE"；
4. **定期复检**：e2e 流程可重放（`findings/26` §2 的步骤 + 第 111 轮的经验）。

## 五、交接要点

- **门槛工具**：`tools/contract-check.py`（L1/L2，支持 `volatile` 易变字段）、
  `docs/findings/read-paths.json`（**与 `paas-core-trigger/src/test/resources/vendor/read-paths.json` 必须一致**，
  工具有自检）、`tools/frontend-e2e-harness.mjs`（含 WS 透传）、`tools/frontend-e2e-check.js`；
- **验证纪律**：对拉要**同时**看 `code`/`msg`/业务字段（**不比 `trace`**）；共享可变状态每次重置；
  写路径用**独立 namespace 或可回滚探针**并核对恢复；
- **环境操作**：临时 Redis NodePort（用完必删）、`kubectl proxy`、探针行（用完核对）、
  应用启动 ~24-32s（**先轮询 `/actuator/health` 再断言**）。
