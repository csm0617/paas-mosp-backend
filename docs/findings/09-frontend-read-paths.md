# 前端到底读了哪些字段（`GET /v1/service` 那条链）

方法：对 `recovered-web/decompiled/` 的**全部** chunk 做字段名检索 + 上下文抽取（**静态证据**，未做运行时插桩）。
产出动机：工单 08 要知道「逐字节等价」这条门槛的代价到底花在哪里。

## 结论一：19 个键里有 7 个前端**从不读**

以下字段名在**整个前端产物里出现 0 次**（195 个 JS 全查）：

`runningDeployment`、`fixedName`、`imageInfo`、`volumeTypeList`、`cpuOrder`、`memoryOrder`、`gpuOrder`

→ 含义：厂商的 `runningDeployment=0` 与我方的 `5` **对前端完全不可见**；
这 7 个键"必须存在"的严格性可以放松（保留它们成本为零，可以继续保留）。

## 结论二：前端确实读的字段（含出现位置）

| 字段 | 读取形态（证据） | 出处（chunk） |
| --- | --- | --- |
| `data.services` | `dataSource: …G.services` → 表格数据源 | `p__ServiceManage.6f18390d.async.js` |
| `data.total` / `data.count` | 分页计数 | `p__ServiceManage`、`p__BaseServices.50c3fa6e.async.js` |
| 项 `deployment.metadata.name` / `.namespace` | 行 key（`.name`×11、`.namespace`×1）、选中服务 | 同上 |
| 项 `deployment.spec.template.*` | 详情渲染（`.template`×2） | 同上 |
| 项 `restartCount` | 重启次数列：`count: a.restartCount \|\| 0` | `p__ServiceManage`×3、`p__BaseServices`×1 |
| 项 `serviceAuth.auth` | 认证状态列：`switch (a.serviceAuth.auth)`；**无 `serviceAuth` 直接显示"认证失败"** | `p__ServiceManage`、`p__ServiceAuth.504b1a3e.async.js` |
| 项 `mappingInfo` | 打开映射面板 `je(e.mappingInfo)` | `p__ServiceManage`、`p__BaseServices` |
| 项 `nodePortService` | 单一 chunk 使用 | `5328.f3271109.async.js` |
| 项 `podInfoList` / `belongNodeList` | 主机服务视图 | `p__MonitorStatus__components__HostService.03ee6afb.async.js` |
| 项 `nameFr` / `nameUs` | 多语言名称 | 6 个 chunk（含 `p__software`） |

**注①**：`p__ServiceManage` 里出现的 `status.hostIP` 处在一张**字段路径映射表**中
（`PodName: "metadata.name"`、`NodeIP: "status.hostIP"`、`PodIP: "status.podIP"`…），说的是 pod 详情表格取哪些路径，
**不是**读取列表项的 `hostIP`。这条按"路径字符串"归类，不当成对列表项的读取。

## 结论三：列表项里的 `service.spec.*` 前端不读

`.service.` 在读取位置没有命中；`spec.ports` 的唯一出现是**写路径**——
`5328.f3271109.async.js` 里构造资源时赋值：`s.spec.ports = []` / `s.spec.ports = m(e)`（给 K8s 代理/YAML 编辑器拼对象）。
真正会读 `spec.ports` 的是 **K8s 代理那条链**（同一 chunk 读 `n.data.items[...]`，例如 `items.filter(e => "Running" === e.status.phase)`）。

## 对工单 08 的直接含义（是事实，不是决定）

1. **厂商那批显式 null 没有一个落在前端读的路径上**——前端读的是 `deployment.metadata.name`（两边都有）、
   `restartCount`、`serviceAuth.auth`、`mappingInfo`，这些与 null 噪声无关。
2. 因此"逐字节等价"的**全部代价都花在前端看不见的键上**。
3. 但有两处**必须填**，否则页面显示与厂商不同：
   - `serviceAuth` 为 null → `p__ServiceManage` 直接显示"认证失败"（`if (!a.serviceAuth) return AUTH_STATUS_FAILED`）；
   - `restartCount` 为 null → 显示 0（`|| 0`，无差别）。
   → 原型当前两者都置 null，**`serviceAuth` 这一处会让页面显示错**，正式实现必须补。

## 方法与局限（必须一起读）

- 命中（字段名出现且上下文是读取）是**强证据**；未命中是**较强但不绝对**的证据：
  若前端用动态键访问（`e[k]`、解构重命名），检索会漏。本产物的 service 层未被混淆，页面逻辑有压缩但属性访问保留。
- 未做运行时插桩。要把结论升级为"运行实测"，路径是：本机起静态站点 + 反代到厂商后端，用真实响应跑一遍页面。
