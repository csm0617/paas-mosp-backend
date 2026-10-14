# 契约偏离清单（对外交付版）

**范围**：`paas-core`（重写版）相对厂商 MSOP v1.3.4（gaop）的**全部已知偏离**。
**生成时间**：2026-10-14，第 60 轮盘点。**配套**：每一条都指向 `docs/findings/` 里的实测证据。

**读法**：
- **A 类**＝厂商自身的缺陷/环境依赖，我们**不复制**（或复制了但明确标注）；
- **B 类**＝我们**主动收紧**（安全性/一致性），成功路径的响应与厂商一致；
- **C 类**＝参考环境导致**无法对拉**，我们按代码实现并标注证据来源。

---

## A. 厂商自身缺陷 / 环境依赖（不可对拉或不该复制）

| # | 位置 | 现象 | 我们的处置 | 证据 |
| --- | --- | --- | --- | --- |
| A1 | `GET /v1/service/{serviceName}` | 厂商自身 **NPE**：`TenxService.responseContainer:604` 只判 `limits != null` 未判键存在；本环境工作负载缺 `resources.limits` ⇒ 500 | 按代码实现，标注证据来源为 `/v1/service` 列表项的 `ServicesItem` 形状 | `findings/51` |
| A2 | `GET /v1/service/event`、`/baseEvent` | 厂商 **500 / code=-1** | 未实现（无参考值） | `findings/52` |
| A3 | `TenxService.existService` | 按 label `system/svcName=<name>` 查；本环境无该标签 ⇒ 恒 false（`servicePods` 等端点因此 400） | 未实现相关端点 | `findings/51` |
| A4 | `GET /v1/service/gaop` | 过滤链最后要求 label `system/appName=gaop`；本环境无 ⇒ 厂商恒空 | **照抄该合取项** ⇒ 两侧都是 0（**能解释的 0**） | `findings/58` |
| A5 | `GET /v1/service/serviceInfoByNode` | 单节点集群且全部 Pod 在 master ⇒ 排除 master 后为空 | 未实现；参考值可解释 | `findings/63` |
| A6 | `getLicenceInfo` 的"时速云时间更早"分支 | `TenxCloudImpl.licences()` 返回对象 `merged` 为 null ⇒ 厂商该行 NPE 被空 catch 吞掉 ⇒ 分支**不可达** | 照抄"用授权工具时间" | `findings/46` |
| A7 | `GET /v1/software` 的 `url` | CS 类型的入口地址里**含远端口令** `remotePassword` | 照抄（前端据此免密打开 CS 客户端），已在 read-paths 记录 | `findings/37` |
| A8 | `POST /v1/user` | 缺 `roleListKey` 时 NPE 发生在**插入之后**，留下半成品用户 | 按空列表处理（见 B1） | `findings/31` 等 |
| A9 | `addSoftWare` | `softwareForm.getRoleListKey().size()` 不判 null ⇒ NPE | 按空列表处理（见 B1） | `findings/38` |
| A10 | `PUT /v1/role/{id}` | 响应 `name:null`（controller 参数传错位） | 不复制（见 B3） | `findings/35` |
| A11 | 任意 PUT 的会话 TTL | 厂商把会话 TTL 从 86400 砍到 600 | 不复制（见 B2） | `findings/33` |
| A12 | `PUT /v1/service/updateServiceRole` | **写入的是死代码**：`annotations.put("gaop/serviceRole", serviceKey)` 之后立刻被 `annotations = ...getAnnotations()` **重新赋值覆盖**，该注解**永远不会被写入**；且 StatefulSet 分支会对 `v1Deployment`（null）调 `getMetadata()` ⇒ NPE | **不实现该"无副作用"语义**；错误路径已对拉确认（400 `service.get.service.failed.not.found`） | `findings/69` 前的勘查（本轮） |

## B. 我们主动收紧（成功路径与厂商一致）

| # | 内容 | 理由 | 证据 |
| --- | --- | --- | --- |
| B1 | `roleListKey`/`roleList` 缺失时按空列表处理 | 厂商会 NPE（A8/A9） | `findings/38` |
| B2 | 会话 TTL 不因 PUT 被砍到 600 | 厂商是缺陷 | `findings/33` |
| B3 | `PUT /v1/role/{id}` 响应带上真实 `name` | 厂商传参错位（A10） | `findings/35` |
| B4 | 用户/软件接口不下发口令哈希与远端口令 | 安全 | `findings/26`、`findings/36` |
| B5 | 软件删除链路包一个事务 | 厂商无事务会半删 | `findings/40` |
| B6 | `g_auth` 无 `default_auth` 行时返回 null（折算未激活） | 厂商 `IndexOutOfBounds` | `findings/44` |
| B7 | `GET /v1/service/gaop` 的排序列白名单化 | 厂商把用户输入拼进 ORDER BY | `findings/37`（软件域同款） |
| B8 | `scale` 失败时保留 fabric8 原始消息 | 厂商 `msg` 为空，清空只丢诊断 | `findings/66` |

## C. 参考环境限制导致无法对拉（按代码实现 + 标注证据来源）

| # | 端点/能力 | 缺什么 | 我们的证据来源 |
| --- | --- | --- | --- |
| C1 | `GET /v1/service/{serviceName}` | 厂商自身 500（A1） | `/v1/service` 列表项的 `ServicesItem` 形状（**已对拉**） |
| C2 | `imageStorage` | **Harbor 未接入** | 未实现（拒绝用 `0/0` 冒充） |
| C3 | TenX 侧效应（激活后通知、时速云授权时间） | 无 TenX 对端 | 未实现，记录为待办 |
| C4 | GIS 在线激活 | 无 GIS 服务 | `checkAndActiveGis` 如实返回 false |
| C5 | `serviceInfoByNode` | 单 master 集群（A5） | 参考值可解释（0） |
| C6 | CS 上传 / 导入导出 | 需文件与远端 SSH | 未实现（边界待定） |

---

## 附：契约比对的既有机制（不是偏离，但相关）

- 读取路径契约：`docs/findings/read-paths.json`（+ `paas-core-trigger/src/test/resources/vendor/read-paths.json` 镜像），
  由 `tools/contract-check.py` 校验两份一致；
- 运行时样本：`docs/findings/runtime-samples/`（含 manifest：路径、字节数、sha256）；
- **顺序天然不稳定的端点**（如 `/v1/service/labels`）契约定在**集合层**，比较方式写在 `findings/47`。
