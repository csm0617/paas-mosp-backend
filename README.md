# paas-core

以厂商 MSOP（内部代号 gaop）业务为蓝本、按 DDD 六边形分层重写的后端。

- **坐标**：`com.csm.paas.core:paas-core`；Java 包根 `com.csm.paas.core`。
- **技术底座**：JDK 21 · Spring Boot 3.3.5 · fabric8 7.9.0 · MyBatis 3.0.3 · HikariCP · springdoc 2.6.0。见 `docs/adr/0002-jdk21-boot3-fabric8-mybatis.md`。
- **分层**：`Trigger → API → Case → Domain ← Infrastructure`（`docs/adr/0003-hexagonal-layers-and-local-tracker.md`）。
  - Domain 只定义 Port/Repository 接口；实现放 `infrastructure/adapter/`。
  - `adapter/port/` 只调 `gateway/`；`adapter/repository/` 只调 `dao/`、`redis/`。
  - **禁止 `persistent/` 包。**
- **第一条纵切的门槛**：厂商前端（`/Users/csm/Desktop/Project/msop/recovered-web/`）一行不改即可跑通 + 契约比对全绿 + e2e 实测。见 `docs/adr/0001-compat-first-rewrite.md`。
- **决策与工单**：`.scratch/backend-rewrite/`（地图 `map.md`，工单 `issues/NN-*.md`）。

## 构建

```bash
mvn -DskipTests package      # 产物：paas-core-app/target/paas-core-app-1.0.0-SNAPSHOT.jar
mvn test                     # 离线门槛测试（7 条，约 6 秒，不需要集群）
```

契约门槛分两半：**离线**部分由 `mvn test` 锁住形状与前端读取路径；**在线**部分用
`python3 tools/contract-check.py "GET /v1/service"`（需要集群 + 已启动的重写后端）。
详见 `docs/findings/10-offline-test-gate.md`。

## 模块

| 模块 | 职责 | 可依赖 |
|---|---|---|
| `paas-core-types` | 枚举、异常、公共类型 | — |
| `paas-core-domain` | 领域模型、领域服务、Port/Repository 接口 | types |
| `paas-core-api` | DTO、错误码 | types |
| `paas-core-case` | 跨领域编排 | api, domain |
| `paas-core-infrastructure` | DAO/PO、gateway、redis、adapter 实现 | domain |
| `paas-core-trigger` | HTTP/MQ/Job 入口 | api, case |
| `paas-core-app` | 启动与装配 | 全部 |
