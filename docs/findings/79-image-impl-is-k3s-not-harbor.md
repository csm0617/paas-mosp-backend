# 更正：`/v1/image/projectList` 的真实数据源是**数据库**（k3s 实现），不是 Harbor——我上一轮读错了实现类

**证据等级**：〔代码事实〕+〔运行实测〕（Harbor 在两个命名空间内都不可达）+〔库内实测〕。2026-10-14。
**结论：上一轮（`findings/78`）实现的 Harbor 版本，在参考环境里是错的数据源。**

## 1. 触发：集群内也到不了 Harbor

上一轮我判断"本机到不了 Harbor，集群内可以"。本轮在集群内起一次性 pod 探测（镜像已在集群里）：

```
gaop-namespace 与 default 两个命名空间内 curl http://192.168.1.52:30002/api/v2.0/projects  → HTTP 000（连不上）
```
厂商 pod 也没有任何 proxy 环境变量。**所以厂商那两个项目不可能来自 Harbor。**

## 2. 真因：`getImageServiceInstance()` **写死返回 k3s 实现**

```java
// service/impl/NodeVersionServiceImpl.java:81-83
public AbstractImageService getImageServiceInstance() {
    return this.k3sImageService;      // ← 无条件
}
```

而两个实现类是完全不同的数据源：

| 实现 | `projectList` 的数据源 |
| --- | --- |
| `K8sImageServiceImpl:107-119` | **Harbor**（`harbor.allPageProjects`）——**我上一轮读的是这个** |
| `K3sImageServiceImpl:200-250` | **数据库**（`g_image_group`）——**实际生效的是这个** |

## 3. K3s 实现的实际逻辑（与实测响应完全吻合）

```
select from g_image_group where name like ? order by create_date desc   (分页)
若结果为空：
    createProjectNoException("system_containers")   // 不存在则**插入**
    createProjectNoException("gaop")
    createProjectNoException("library")
过滤掉 "system_containers"（**只过滤这一个**，没有 system_store）
把 ImageGroup{id, createDate, name} 映射成 ProjectItem{projectId=id, name, creationTime=...}
total = list.size()
```

**与实测完全对上**：厂商返回的正是 `gaop` + `library` 两条（三个内置项目减掉被过滤的 `system_containers`），
其余 8 个字段全为 null——因为 `ImageGroup` **只有 3 个字段**。

## 4. 顺带解释了一个"畸形值"的来历

实测 `creation_time = "+58753-04-29T03:17:20+08:00"`（公元 58753 年）：

```java
Instant instant = Instant.ofEpochSecond(group.getCreateDate());   // ← 按**秒**解释
```
而写入时存的是**毫秒**（`System.currentTimeMillis()`）⇒ 被当成秒 ⇒ 年份爆到 58753。
**这是厂商缺陷**，不是数据损坏；我们照抄即可（**不要"顺手修正"成毫秒**，否则与厂商不一致）。

## 5. 我为什么会读错

`AbstractImageService` 有两个实现，我先打开的是 `K8sImageServiceImpl`（名字更"正统"），
就照着它实现了 Harbor 版本——**没有先确认"哪个实现实际生效"**。

这与之前的教训同族但更靠前一步：**不是读错了代码行，而是读错了类**。
可复用的自查：**凡是有"多实现/多策略"的地方，先找到"选择逻辑"，再读被选中的那个实现。**

## 6. 处置

- `findings/78` 的实现（Harbor 版）**保留但标注为"K8s 变体，参考环境不生效"**；
- 正确的实现应照 `K3sImageServiceImpl`：查 `g_image_group`（含"为空则创建三个内置项目"的副作用）、
  只过滤 `system_containers`、`createDate` 按**秒**格式化、`total = list.size()`；
- **注意副作用**：这个端点会在"表为空"时**写库**（创建 3 个内置项目）——做对拉时要考虑这个写入，
  并在验完清理（或确认它本来就该存在）。

## 7. 状态

本轮无业务代码改动（更正 + 探查）；全量 **41 类 266 条全绿**；无残留资源。
