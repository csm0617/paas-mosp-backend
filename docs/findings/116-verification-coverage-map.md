# 验证覆盖面地图：**门槛工具覆盖到哪里、哪里靠别的证据**（第 116 轮）

**证据等级**：〔工程事实〕（`read-paths.json` 的结构化登记 + 各 findings 的证据等级）。2026-10-14。

## 1. 为什么要记这张图

`tools/contract-check.py` 的 L1/L2 判定依赖 `read-paths.json` 里的**前端读取路径清单**——
它只覆盖**前端真正会读的那些端点**。而本会话验证过的端点远多于这些（有的靠 A/B 对拉、
有的靠独立 namespace、有的靠可回滚探针）。**如果不写清楚，"门槛绿"会被误读成"全都验过了"。**

## 2. 已登记进 `read-paths.json` 的 `_verifiedByDirectAB`（9 条）

| 端点 | 证据 | 验证方式 |
| --- | --- | --- |
| `GET /v1/user/list` | `findings/99` | 四例对拉完全相等 |
| `GET /v1/role/{roleId}/software` | `findings/90` | 四例对拉完全相等（需 Cookie） |
| `GET /v1/user/access_permission` | `findings/93` | 六组 path 对拉完全相等 |
| `GET /v1/image/projectList` | `findings/80` | 整个响应体逐字节相等 |
| `GET /v1/service/restartInfo` | `findings/81` | 三种情形完全相等 |
| `POST /v1/software/software/active` | `findings/75` | 五种情形一致（自造密钥对） |
| `GET /v1/user/import/template` | `findings/101` | 内容一致（差异仅 xls 文档属性里的 OS 用户名） |
| `POST /v1/user/export`、`/backup` | `findings/105` | 离线单测（POI 读回逐格）；厂商侧 Metaspace 打满无法在线对拉 |
| `POST /v1/user/update_avatar/{userId}` | `findings/109` + `114` | 可回滚探针 + **WS 推送端到端** |

## 3. 三类证据的边界（写给下一个接手的人）

1. **`contract-check.py`（L1/L2）**：覆盖**前端读取路径**内的端点 ⇒ 7 条全绿；它**不能**证明
   "页面不白屏"（那是 e2e 的职责）、也不能证明"二进制产物内容一致"；
2. **e2e（`tools/frontend-e2e-*`）**：证明"厂商原样前端 + 我们的后端"页面与厂商一致（本会话跑过 3 次）；
3. **独立 namespace / 可回滚探针**：证明**写端点**的真实效果（本会话用于 `hostName`/`priority`/`scale`/
   `patchCommand`/`cancelStartUp`/`startUp`/`node_port`/`nodePort`/`update_avatar` 等）。

**没有一种证据能替代另一种**：本会话多次出现"L1/L2 绿但页面差一点"（第 68 轮的导航图标）与
"日志打了但没送达"（第 114 轮的 WS 推送）——**证据等级要按被验证的命题选**。

## 4. 状态

`read-paths.json` 已增加该登记（**镜像副本已同步**，工具的一致性自检通过）；全量 **304 条用例全绿**。
