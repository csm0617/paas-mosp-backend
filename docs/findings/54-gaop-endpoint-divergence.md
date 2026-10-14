# `GET /v1/service/gaop`：端点已接通，但**与厂商不一致**——厂商在本环境返回空列表

**证据等级**：〔运行实测〕（两侧对拉）+〔代码事实〕。2026-10-14。
**状态：未收敛。** 本轮把它记成"有差异待查"，不是"已完成"。

## 1. 实测

| | vendor | mine |
| --- | --- | --- |
| `type=1` | `total=0 count=0 running=0 services=[]` | `total=6 count=6 running=5`（gaop-auth/be/gateway-auth/redis/startup/web） |
| `type=2` | 同样**全 0** | 同样 6 个 |

也就是说：**厂商的 `gaop` 端点在参考环境里是个空列表**，而我们按代码实现后返回 6 个。

## 2. 两个必须分开看的问题

**问题 A（阻断对拉）**：厂商为什么是空的？
`getGaopServiceList` 的数据源是 `tenxCloud.allPageService(null)`——与本环境的 `/v1/service`
（它确实返回 9 个带完整 `deployment` 的服务）**不是同一条数据链路**。
最可能的解释是 `allPageService` 走 TenX 层、在本环境拿不到 `deployment`，
于是 §2 的第 1 步"`e.deployment` 为 null → 丢弃"把所有条目都丢掉了。
**未确认**（还没读到 `allPageService` 的实现）。**在确认之前，这条端点的对拉是无效的**——
拿一个恒为空的参考去比，只能得到"我们多了 6 个"这种无信息量的结论。

**问题 B（我们自己的 bug）**：`fixedName` 没有变成中文显示名。
实测我们的响应里 `fixedName` 是 `gaop-be` 这样的**服务名**，而按厂商逻辑应是 `框架后端`。
`GaopServiceNames.fixedName("gaop-be")` 在单测里是对的（`displayNamesMatchVendorTable` 通过），
所以问题出在**装配链路上**（`peek` 设置的值没有被最终 DTO 取到，或装配器走的是另一份实体）。
**这条是明确的实现缺陷，下一轮先修它。**

## 3. 本轮实际落下的代码（是有价值的，只是还不够）

- `ServiceRuntimeEntity` 新增 `appType`/`fixedName`；
- `ServiceRuntimePort` 从工作负载快照里取标签 `system/appType`（厂商 `AppTypeLabelsEnum.TYPE_LABEL_NAME`）；
- `ServiceQueryServiceImpl.queryGaopServiceList(...)`：过滤 + `fixedName` + keyword 两段式 + 分页；
- `ServiceController` 的 `GET /v1/service/gaop`（含 `keyword > 45` → 400）。
- 装配器改为「有 `fixedName` 用它，否则回退服务名」——**保持已对拉的 `/v1/service` 行为不变**。

**其中一处已标注为近似**：厂商的兜底匹配 `ServiceController.filterServiceName(keyword, e)` **还没读全**，
我暂时按"名字或显示名忽略大小写包含"实现，并在代码注释里写明是**未经对拉的近似**。

## 4. 下一轮的确定顺序（先修 bug，再查数据源，最后再谈对齐）

1. **修 `fixedName` 链路**（问题 B）——这是明确缺陷，且不依赖任何外部条件；
2. **读 `allPageService` 的实现**（问题 A），确认厂商空列表的根因：
   若是"`deployment` 为 null 被丢弃"，则说明这个端点在参考环境**结构性地不可用**，
   与本项目已记录的 `GET /{serviceName}`（`findings/51`）属于**同一类**：厂商路径坏，而不是我们没对齐；
3. 再返回头**读全 `filterServiceName`**，把 keyword 的兜底规则做成精确实现。

## 5. 状态

全量用例 **34 类 225 条全绿**（本轮新增的过滤器单测 5 条在内）；
集群只读对拉 + 临时 NodePort（已删）；无残留进程；工作树干净。
