# `PUT /v1/service/patchCommand/{serviceName}`：实现 + 独立 namespace e2e

**证据等级**：〔运行实测〕（独立 namespace + 对拉）+〔代码事实〕。2026-10-14。

## 1. 实测（`paas-verify-cmd`，用完即删）

| 用例 | 结果 |
| --- | --- |
| A：改 `command`/`args` | 200 `{"code":0,"data":**null**}`；容器 `command=["/bin/sh","-c"]`、`args=["echo hi","sleep 60"]` **真的改上** ✅ |
| B：服务不存在 | 400 `服务no-such不存在`；**厂商同码同文案模式**（`服务no-such-xyz不存在`）✅ |

**`data:null` 是照抄的**：厂商最后是 `Response.ok()`（**不带 body**），不是 `Response.ok(true)`。

## 2. 实现要点（与相邻端点的差异）

| 端点 | 读不到 Deployment 时 |
| --- | --- |
| `hostName` | 回退 StatefulSet |
| `priority` | 不回退，400 |
| `patchCommand` | **不回退**，400 `service.service.name.not.found` |

`command`/`args` 作用到**所有容器**（厂商 `containers.forEach(...)`），不是只改第一个。

## 3. 一处实现细节（fabric8 的枚举名）

厂商发的是 `application/merge-patch+json`；fabric8 里对应 **`PatchType.JSON_MERGE`**，
不是 `MERGE`（我第一版写成 `MERGE` 编译失败）。而 `scale` 那个端点用的是
`strategic-merge-patch+json` ⇒ `PatchType.STRATEGIC_MERGE`。**两种 merge patch 不要混用**：

- `JSON_MERGE`：整份对象合并（列表整体替换）——本端点与 `hostName`/`priority` 用它；
- `STRATEGIC_MERGE`：K8s 特有语义（列表按键合并）——`scale` 用它。

## 4. 状态

服务切片 **10/33**；测试 **41 类 246 条全绿**（上一轮 244）。
临时 namespace 已删（NotFound）、NodePort 已删、进程清零、工作树干净。
