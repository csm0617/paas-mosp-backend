# `GET /v1/service/serviceAuth`：**四种情形对拉全部一致**（含非空）

**证据等级**：〔运行实测〕（对拉）+〔代码事实〕。2026-10-14。
这是服务切片里**第一个拿到非空对拉**的新端点。

## 1. 实测（同一时刻、两侧各一次）

| 请求 | vendor | mine |
| --- | --- | --- |
| 不带 `authType` | `total=9 count=9` | **`total=9 count=9`** ✅ |
| `authType=0`（未授权） | `total=9 count=9` | **`total=9 count=9`** ✅ |
| `authType=1`（已授权） | `total=0 count=0` | **`total=0 count=0`** ✅ |
| `authType=0,1` | `total=9 count=9` | **`total=9 count=9`** ✅ |
| `keyword` 长度 46 | 400 | 400 ✅ |

**这四种情形正好覆盖了授权过滤的三条不同分支**（`ServiceController:3524-3555`）：
- `authType=0`：没有授权行、`typeList` 只含 0 ⇒ 走"`serviceTypeName == null` 则保留" ⇒ 9 条（本环境没有 `gaop/serviceType` 注解）；
- `authType=1`：没有授权行、`typeList` 只含 1 ⇒ 走最后一条"必须 `前台服务`" ⇒ **0 条**；
- `authType=0,1`：走第一条"同时含 0 和 1 且 size>1 ⇒ 全保留" ⇒ 9 条。

也就是说，**不是"两边都恰好是 9"这种巧合**，而是三条分支各自都被验证到了。

## 2. 实现（照着 `findings/59` 的清单写的，5 步一次过）

1. `ServiceRuntimeEntity` 加 `serviceType`（注解 `gaop/serviceType`）；
2. 端口的 `serviceLabelOf` 泛化成 `annotationOf(snapshot, key)`，同时取 `gaop/serviceLabel` 与 `gaop/serviceType`；
3. `ServiceQueryServiceImpl.queryServiceAuthList(...)`：基础过滤（**appName 既非 `gaop` 也非 `gis-server`**）
   → keyword 两段式 → `authType` 过滤（`keepByAuthType`，第 2 条已按等价式化简）→ 分页；
4. 控制器 `GET /v1/service/serviceAuth`（`keyword>45` → 400）；
5. 形状复用 `ServiceListAssembler` / `ServiceListResponseDTO`。

**一处易错点已避开**：`gaop` 要求 `appName == "gaop"`，`serviceAuth` 要求 `appName != "gaop" && != "gis-server"`
——两个端点**正好互补**。上一轮把它写进清单是对的，这轮照着写没有抄反。

## 3. 一个小插曲（如实记）

第一次探测时"不带 authType"那一条我方返回了空响应，我一度以为是 bug；
重跑（含 `/actuator/health` 确认 200）后两边一致——是**探测脚本在应用刚起时抢跑**，
不是实现问题。**记录这一笔**是为了说明：对拉脚本也要先确认被测服务真的就绪，
否则"空响应"会被误读成实现缺陷。

## 4. 状态

服务切片进度 **5/33**（`/v1/service`、`labels`、`updateName`、`gaop`、`serviceAuth`）。
全量 **34 类 229 条全绿**；临时 NodePort 已删、无残留进程、工作树干净。
