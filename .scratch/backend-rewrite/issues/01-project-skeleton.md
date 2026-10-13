Type: task
Status: claimed
Blocked by: —

## Question

（这不是决策，是必须先发生、且解锁 06 的事。）

在 `/Users/csm/Desktop/PaaS/paas-core` 下起一个**能编译**的 DDD 多模块工程骨架，严格按 `xfg-ddd-skills` 的六边形分层与 Infrastructure 强制约束：

- Maven 多模块（7 个）：`paas-core-types`、`paas-core-domain`、`paas-core-infrastructure`、`paas-core-api`、`paas-core-case`、`paas-core-trigger`、`paas-core-app`。
- 坐标 `com.csm.paas.core:paas-core`，Java 包根 `com.csm.paas.core`。
- JDK 21 / Spring Boot 3.3.x / `fabric8-kubernetes-client-starter` 7.x / MyBatis（Boot 3 可用版本）/ HikariCP / springdoc 2.x。
- 禁止 `persistent/` 包；Redis 操作放 `infrastructure/redis/`；HTTP 客户端放 `infrastructure/gateway/`；Repository 实现在 `infrastructure/adapter/repository/`。

## 验收

- `mvn -q -DskipTests package` 退出码 0。
- `mvn -q dependency:list` 存 `docs/findings/01-deps.txt`，其中 fabric8 为 7.x，且**不存在** `io.kubernetes:client-java`。
- 目录树与 `xfg-ddd-skills` 的强制结构一致（`find . -type d` 结果贴进 `## Answer`）。

## Answer
