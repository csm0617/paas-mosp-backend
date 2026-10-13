Type: task
Status: open
Blocked by: —

## Question

（这不是决策，是必须先发生、且解锁 06 的事。）

在 `rewrite/` 下起一个**能编译**的 DDD 多模块工程骨架，严格按 `xfg-ddd-skills` 的六边形分层与 Infrastructure 强制约束：

- Maven 多模块：`gaop-app`（启动/装配）、`gaop-trigger`、`gaop-api`、`gaop-case`、`gaop-domain`、`gaop-infrastructure`。
- 包根 `com.glriverside.gaop`（沿用厂商包名，便于与 `../recovered/gaop-be` 逐类对照；若要换自有 groupId，只改这一处并补一条 ADR）。
- JDK 21 / Spring Boot 3.3.x / `fabric8-kubernetes-client-starter` 7.x / MyBatis（Boot 3 可用版本）/ HikariCP / springdoc 2.x。
- 禁止 `persistent/` 包；Redis 操作放 `infrastructure/redis/`；HTTP 客户端放 `infrastructure/gateway/`；Repository 实现在 `infrastructure/adapter/repository/`。

## 验收

- `mvn -q -DskipTests package` 退出码 0。
- `mvn -q dependency:list` 存 `docs/findings/01-deps.txt`，其中 fabric8 为 7.x，且**不存在** `io.kubernetes:client-java`。
- 目录树与 `xfg-ddd-skills` 的强制结构一致（可用 `find . -type d` 自查后贴进 `## Answer`）。

## Answer
