# 服务域端点「可观测性」分诊：10 个端点逐个探测，只有 4 个能拿到 200

**证据等级**：〔运行实测〕（逐个探测厂商）+〔代码事实〕。2026-10-14。

## 1. 为什么要先分诊

`findings/50`/`51` 的教训是：**在做之前先确认"能不能拿到成功响应"**。服务域有 33 个端点，
上一轮我已经在 `GET /{serviceName}` 上浪费了两轮才发现它根本返回不出 200。
所以本轮先把候选端点全部探一遍，把"可对拉"与"不可对拉"分开，再决定做什么。

## 2. 分诊结果（实测，同一集群同一时刻）

| 端点 | 结果 | 形状 / 原因 |
| --- | --- | --- |
| `/v1/service/gaop?page&size&type` | **200** ✅ | `{runningDeployment,total,count,services}` —— **与 `/v1/service` 同形** |
| `/v1/service/serviceAuth` | **200** ✅ | 同形（也是 `{runningDeployment,total,count,services}`） |
| `/v1/service/imageStorage` | **200** ⚠️ | `{free:0,total:0}` —— 值来自 Harbor 查询，**本环境未接 Harbor** |
| `/v1/service/baseComponent` | **200** ⚠️ | `list(2)`，读 `kube-system` 里的 DaemonSet（`weave-net`/`kube-proxy` 等） |
| `/v1/service/hostName` | 400 | 参数缺失类 |
| `/v1/service/priority` | 400 | 同上 |
| `/v1/service/node` | 400 | 同上 |
| `/v1/service/servicePods?serviceName=gaop-be` | 400 | 「服务不存在」类（与 `existService` 同源，见 `findings/51`） |
| `/v1/service/baseEvent` | 500 / -1 | 厂商内部异常 |
| `/v1/service/event?page&size` | 500 / -1 | 厂商内部异常 |

**10 个里只有 4 个能拿到 200**，其中 2 个（`gaop`/`serviceAuth`）是**我们已经实现并对拉过的形状**。

## 3. 本轮为什么又没写代码（以及这不是拖延）

我把 4 个候选逐一看过，结论是**这轮没有可以诚实开工的**：

- `gaop` / `serviceAuth`：形状同 `/v1/service`，**看起来最容易**——但它们不是"同一份数据换条路径"：
  `gaop` 要按 `type`（1=框架系统服务 / 2=基础服务）**过滤**，而"哪个服务算框架系统服务"取决于
  一个我们在列表端点里**没有**的判定（厂商从 K8s 的 `system/serviceType` 注解一类的东西取，需读码确认）；
  `serviceAuth` 是我们已有授权的另一视图。两者都要先读代码确定**过滤/排序规则**，
  否则就是"形状对、内容错"——这正是我这几轮反复要避免的。
- `imageStorage`：值是 Harbor 查出来的。**我们代码里根本没有 Harbor 客户端**，
  这时返回 `0/0` 虽然"恰好与厂商当前值相同"，但**原因完全不同**（厂商是查了 Harbor 得 0，我们是没接）。
  这正是我一直拒绝的"用 null/0 冒充数据"。
- `baseComponent`：读 `kube-system` 的特定 DaemonSet。可与独立 namespace 无关地做，
  但依赖一组**写死的 DaemonSet 名字**与它们的字段映射，需要先把 `KubeSystemService` 形状读全。

**所以本轮的产出是"把下一轮的开工条件变成确定的"**：现在知道该按什么顺序做，
以及每个端点还差什么前置。这比再交一个"形状像我读到的代码"的端点有价值。

## 4. 下一轮的确定清单（按"可诚实完成"排序）

1. **`GET /v1/service/gaop`**：先读码确定 `type=1/2` 的判定依据与排序，再实现；
   形状可直接复用 `/v1/service` 已对拉的 `ServiceList`。
2. **`GET /v1/service/serviceAuth`**：同上，先确定它与列表端点的差异（是否只多/少某个过滤）。
3. **`GET /v1/service/baseComponent`**：读全 `KubeSystemService` 形状 + 那组 DaemonSet 名字。
4. **`imageStorage`**：**等 Harbor 域**（与 `docs/findings/03` 的 Harbor 待办同一条），
   不要用 `0/0` 假装。

## 5. 状态

本轮零业务代码改动；集群只读探测；无临时资源；全量用例仍 **34 类 220 条全绿**。
