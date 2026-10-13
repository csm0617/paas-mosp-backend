# K2 续：`POST /v1/software/release`（放行 / 禁止放行）——闭环了 `releaseStatus`

**证据等级**：〔运行实测〕（对拉厂商）+〔代码事实〕。2026-10-14。

## 1. 结果：四种情形全部一致

| 情形 | 厂商 | 我们 |
| --- | --- | --- |
| `type=1` 放行 | `data:true`，随后列表里 `releaseStatus=1` | 同 |
| `type=0` 禁止（第一次，有白名单行） | `data:true`，行被删、`releaseStatus` 回到 0 | 同 |
| `type=0` 禁止（第二次，没有行） | **`data:false`** | 同 |
| `list=[]` | `data:true`（什么都不做） | 同 |

**这一轮把上一轮那条"半开的环"闭上了**：`releaseStatus` 的来源（`g_white_list` 里有没有 `/<key>/**`）
现在既能读（列表端点）也能写（放行端点），并且两侧一致。

## 2. 照抄的一处**不对称**（这是本轮唯一容易做错的细节）

厂商 `releaseSoftware`（`SoftwareService.java:2887-2931`）两个分支的循环语义**不一样**：

- `type == 1`（放行）：逐个软件插白名单，**全部处理完**才返回 true；
- `type == 0`（禁止）：逐个软件删白名单，**一旦删到就立刻 return true**、**删不到就立刻 return false**
  —— 也就是说它**只看第一个存在的软件**，后面的 id 根本不处理。

所以传两个 id、其中第一个没白名单行时，`type=0` 会直接返回 false 且**第二个 id 不动**。
我按这个语义实现并写了单测（`typeZeroReturnsOnTheFirstExistingSoftware`），实测对拉也是 `true`/`false` 与厂商一致。

其它：`list` 为空或不是 0/1 的 `type` 值 → 都不进分支，返回 true。

## 3. 一处**未实现并记录**的副作用

厂商在最后会调 `notificationGateway()`（`:2287-2294`）：**新起一个线程**去
`GET http://<gateway-host>:<port>/refresh`，通知网关重载白名单规则。
它是 fire-and-forget：**成败不影响响应**（异常只在新线程里打日志）。

我们没有接管网关（ADR-0005 的切流还没做），所以**没有实现这个通知**。风险是明确的：
如果网上真有网关在按白名单放行，我们改了白名单却不通知它，规则不会立即生效。
已记入地图待办，与"Higress 切流/回退演练"绑在一起。

## 4. 测试与清理

- 新增域测试 4 条（空列表 noop、type=1 逐个插、type=0 只看第一个、其它 type 值）；
- 全量 `mvn test` **148 条全绿**（上一轮 144）；
- 探针软件、白名单行、关联全部删除，库回到 `software=0 / white_list=0 / role_software=0 / user=1 / role=2`。

## 5. 关于 `GET /v1/software/{id}`（本轮**没有**做，说明原因）

我原本想顺手做软件详情，读了实现后放弃了：`detailSoftware`（`:1563-1610`）返回的 `SoftwareInfo`
有 21+ 个字段，其中 `licence` 需要**授权域（K3）**的机器码与授权码校验、
`uploadStoreRecord` 需要**上传记录**、`status`/`serviceList` 还要 K8s 现场状态。
**它不是"23 键的详情"，而是三个域的聚合**（与 `GET /v1/language` 那类"看着像 CRUD"的端点同一性质）。
在不具备 K3 与上传记录的前提下实现它，只能返回一堆 null 冒充数据——**宁可留着不做**，
下一轮先把 `licence` 与 `uploadStoreRecord` 的最小只读形态理清再动手。
