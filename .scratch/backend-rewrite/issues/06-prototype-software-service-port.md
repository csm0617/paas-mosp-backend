Type: prototype
Status: resolved
Blocked by: —（原为 01, 04；2026-10-14 重定：01 已完成，04 的依赖表只对全量迁移必要，本切片不需要）

## Question

用一次**纵向切片**回答"JDK 21 + DDD 六边形 + fabric8 7 在真实数据上到底行不行"：在 `rewrite/` 里实现「软件-服务-端口」域的一条只读链路（列表 + 详情），数据源为 172.16.209.25 的 `gaop-namespace`。

必须验证并记录：

- fabric8 7 读真实 Deployment/Service，**具名端口**（`targetPort: http`、`tcpSocket.port: http`）不再抛 `Cannot deserialize Integer from String`
- 返回 JSON 与厂商 `/api/gaop/v1/software/overView`、`/v1/software` 的形状一致（diff 证据）
- 分层落地正确：Trigger → API → Case → Domain ← Infrastructure；Repository 实现走 `infrastructure/adapter/repository/`，fabric8 调用走 `infrastructure/gateway/` + `infrastructure/adapter/port/`

## 已有事实（先行验证，不重复做）

具名端口在 **fabric8 7.9.0 模型层**已验证可正常反序列化（`docs/findings/named-port-fabric8-7.9.0.md`，2/2 用例通过）。
本 ticket 要回答的是**真实集群数据**上是否还有别的不兼容字段，以及整条链路（Trigger→API→Case→Domain←Infrastructure）能否对齐厂商 JSON。

## 运行时基线（已就绪，可直接 diff）

`docs/findings/runtime-samples/` 里有 2026-10-14 从运行中厂商后端抓的原始响应：
`POST /v1/software/overView`（162 B）、`GET /v1/software`（58 B）、`GET /v1/service`（67 KB，含 9 个服务项）、
`GET /k8s/api/v1/namespaces/gaop-namespace/services`（14 KB）、`/v1/app`（恒空）。
信封是 `{msg,code,data}`；**失败响应的 data 里带 Java 堆栈 trace**；K8s 代理路径把数字序列化成 `19526.0`。
采集方式与端点全表见 `docs/findings/runtime-samples/README.md`。

## 验收

- `curl` 实测输出与厂商响应 diff 一致（贴证据）。
- 失败模式写进 `## Answer

**成立**：JDK 21 + Spring Boot 3.3 + fabric8 7.9.0 + 六边形分层在真实集群数据上跑通——`GET /v1/service` 返回 200 / 73,698 B，
信封与 `data` 键与厂商逐字一致，单项 19 个键一个不缺一个不多，具名端口正常（fabric8 的 `IntOrString`）。

**四处差异**（详见 `docs/findings/06-prototype-result.md`）：
1. 列表驱动源：厂商按 Deployment/StatefulSet 遍历（`TenxService.java:2293` 起），我按 Service → 多出 `gaop-mysql`；正式实现改 workload 驱动。
2. `runningDeployment`：厂商=0、我=5；厂商规则见 `TenxService.java:2349`，按规则应为 5 → 厂商为何是 0 **未定论**。
3. null 序列化策略不同 → **逐字节等价做不到**，判定标准已开 **工单 08**。
4. 原型没有认证（厂商需 `gateway-token` cookie），过门槛前必须补。

**可复现**：`mvn -DskipTests package` → `java -jar paas-core-app/...jar` → `curl /v1/service` → `python3 tools/diff-service-list.py`。
`——它决定后面所有域的实现方式。
- 本 ticket 的产物是**可丢弃**的验证代码；转正由 07 决定。

## Answer

**成立**：JDK 21 + Spring Boot 3.3 + fabric8 7.9.0 + 六边形分层在真实集群数据上跑通——`GET /v1/service` 返回 200 / 73,698 B，
信封与 `data` 键与厂商逐字一致，单项 19 个键一个不缺一个不多，具名端口正常（fabric8 的 `IntOrString`）。

**四处差异**（详见 `docs/findings/06-prototype-result.md`）：
1. 列表驱动源：厂商按 Deployment/StatefulSet 遍历（`TenxService.java:2293` 起），我按 Service → 多出 `gaop-mysql`；正式实现改 workload 驱动。
2. `runningDeployment`：厂商=0、我=5；厂商规则见 `TenxService.java:2349`，按规则应为 5 → 厂商为何是 0 **未定论**。
3. null 序列化策略不同 → **逐字节等价做不到**，判定标准已开 **工单 08**。
4. 原型没有认证（厂商需 `gateway-token` cookie），过门槛前必须补。

**可复现**：`mvn -DskipTests package` → `java -jar paas-core-app/...jar` → `curl /v1/service` → `python3 tools/diff-service-list.py`。

