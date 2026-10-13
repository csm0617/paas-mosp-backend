Type: task
Status: resolved
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
- 目录树与 `xfg-ddd-skills` 的强制结构一致（`find . -type d` 结果贴进 `## Answer

**已由本会话完成**（2026-10-14）。

### 验收证据

- `mvn -B -DskipTests package` → **BUILD SUCCESS**；产物 `paas-core-app/target/paas-core-app-1.0.0-SNAPSHOT.jar`（67 MB，已 repackage）。
- `mvn -B -DskipTests install` → BUILD SUCCESS；`mvn -B -pl paas-core-app dependency:list` → **154 条依赖**写入 `docs/findings/01-deps.txt`：
  - fabric8：`kubernetes-client` / `kubernetes-client-api` / `kubernetes-httpclient-vertx` / `kubernetes-model-*` 全为 **7.9.0**；
  - `io.kubernetes` 命中数 **0** —— 已彻底摆脱厂商的 `client-java:18.0.1`；
  - `mybatis-spring-boot-starter` **3.0.3**、`springdoc-openapi-starter-webmvc-ui` **2.6.0**。
- 版本取舍：Spring Boot **3.3.5** + fabric8 **7.9.0** + MyBatis **3.0.3** + springdoc **2.6.0**。刻意不跟 springdoc 2.8+（它要求 Boot 3.4+）。
- 依赖方向由父 POM 的 `dependencyManagement` 固定：`types ← domain/api ← case ← trigger`，`infrastructure → domain`，`app → 全部`。
- Infrastructure 强制约束已落地：**无 `persistent/` 包**；`dao/` + `dao/po/`、`gateway/` + `gateway/dto/`、`redis/`、`adapter/{port,repository}/`、`config/`（只放 Bean 配置；`KubernetesClientConfig` 用 fabric8 7 的 `KubernetesClientBuilder`）。

### 目录树

```
.
./paas-core-api
./paas-core-api/src
./paas-core-api/src/main
./paas-core-api/src/main/java
./paas-core-api/src/main/java/com
./paas-core-api/src/main/java/com/csm
./paas-core-api/src/main/java/com/csm/paas
./paas-core-api/src/main/java/com/csm/paas/core
./paas-core-api/src/main/java/com/csm/paas/core/api
./paas-core-api/src/main/java/com/csm/paas/core/api/dto
./paas-core-api/src/main/java/com/csm/paas/core/api/error
./paas-core-app
./paas-core-app/src
./paas-core-app/src/main
./paas-core-app/src/main/java
./paas-core-app/src/main/java/com
./paas-core-app/src/main/java/com/csm
./paas-core-app/src/main/java/com/csm/paas
./paas-core-app/src/main/java/com/csm/paas/core
./paas-core-app/src/main/resources
./paas-core-case
./paas-core-case/src
./paas-core-case/src/main
./paas-core-case/src/main/java
./paas-core-case/src/main/java/com
./paas-core-case/src/main/java/com/csm
./paas-core-case/src/main/java/com/csm/paas
./paas-core-case/src/main/java/com/csm/paas/core
./paas-core-case/src/main/java/com/csm/paas/core/cases
./paas-core-case/src/main/java/com/csm/paas/core/cases/software
./paas-core-case/src/main/java/com/csm/paas/core/cases/software/impl
./paas-core-domain
./paas-core-domain/src
./paas-core-domain/src/main
./paas-core-domain/src/main/java
./paas-core-domain/src/main/java/com
./paas-core-domain/src/main/java/com/csm
./paas-core-domain/src/main/java/com/csm/paas
./paas-core-domain/src/main/java/com/csm/paas/core
./paas-core-domain/src/main/java/com/csm/paas/core/domain
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/adapter
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/adapter/port
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/adapter/repository
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/model
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/model/aggregate
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/model/entity
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/model/valobj
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/service
./paas-core-infrastructure
./paas-core-infrastructure/src
./paas-core-infrastructure/src/main
./paas-core-infrastructure/src/main/java
./paas-core-infrastructure/src/main/java/com
./paas-core-infrastructure/src/main/java/com/csm
./paas-core-infrastructure/src/main/java/com/csm/paas
./paas-core-infrastructure/src/main/java/com/csm/paas/core
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/adapter
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/adapter/port
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/adapter/repository
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/config
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/dao
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/dao/po
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/gateway
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/gateway/dto
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/redis
./paas-core-trigger
./paas-core-trigger/src
./paas-core-trigger/src/main
./paas-core-trigger/src/main/java
./paas-core-trigger/src/main/java/com
./paas-core-trigger/src/main/java/com/csm
./paas-core-trigger/src/main/java/com/csm/paas
./paas-core-trigger/src/main/java/com/csm/paas/core
./paas-core-trigger/src/main/java/com/csm/paas/core/trigger
./paas-core-trigger/src/main/java/com/csm/paas/core/trigger/http
./paas-core-trigger/src/main/java/com/csm/paas/core/trigger/job
./paas-core-trigger/src/main/java/com/csm/paas/core/trigger/mq
./paas-core-types
./paas-core-types/src
./paas-core-types/src/main
./paas-core-types/src/main/java
./paas-core-types/src/main/java/com
./paas-core-types/src/main/java/com/csm
./paas-core-types/src/main/java/com/csm/paas
./paas-core-types/src/main/java/com/csm/paas/core
./paas-core-types/src/main/java/com/csm/paas/core/types
./paas-core-types/src/main/java/com/csm/paas/core/types/enums
./paas-core-types/src/main/java/com/csm/paas/core/types/exception
```

### 遗留

本工单只铺结构：`domain/` 下只有 `package-info`，**领域模型一律未预设**——等 03 的上下文证据与 06 的纵向切片来定。
`）。

## Answer

**已由本会话完成**（2026-10-14）。

### 验收证据

- `mvn -B -DskipTests package` → **BUILD SUCCESS**；产物 `paas-core-app/target/paas-core-app-1.0.0-SNAPSHOT.jar`（67 MB，已 repackage）。
- `mvn -B -DskipTests install` → BUILD SUCCESS；`mvn -B -pl paas-core-app dependency:list` → **154 条依赖**写入 `docs/findings/01-deps.txt`：
  - fabric8：`kubernetes-client` / `kubernetes-client-api` / `kubernetes-httpclient-vertx` / `kubernetes-model-*` 全为 **7.9.0**；
  - `io.kubernetes` 命中数 **0** —— 已彻底摆脱厂商的 `client-java:18.0.1`；
  - `mybatis-spring-boot-starter` **3.0.3**、`springdoc-openapi-starter-webmvc-ui` **2.6.0**。
- 版本取舍：Spring Boot **3.3.5** + fabric8 **7.9.0** + MyBatis **3.0.3** + springdoc **2.6.0**。刻意不跟 springdoc 2.8+（它要求 Boot 3.4+）。
- 依赖方向由父 POM 的 `dependencyManagement` 固定：`types ← domain/api ← case ← trigger`，`infrastructure → domain`，`app → 全部`。
- Infrastructure 强制约束已落地：**无 `persistent/` 包**；`dao/` + `dao/po/`、`gateway/` + `gateway/dto/`、`redis/`、`adapter/{port,repository}/`、`config/`（只放 Bean 配置；`KubernetesClientConfig` 用 fabric8 7 的 `KubernetesClientBuilder`）。

### 目录树

```
.
./paas-core-api
./paas-core-api/src
./paas-core-api/src/main
./paas-core-api/src/main/java
./paas-core-api/src/main/java/com
./paas-core-api/src/main/java/com/csm
./paas-core-api/src/main/java/com/csm/paas
./paas-core-api/src/main/java/com/csm/paas/core
./paas-core-api/src/main/java/com/csm/paas/core/api
./paas-core-api/src/main/java/com/csm/paas/core/api/dto
./paas-core-api/src/main/java/com/csm/paas/core/api/error
./paas-core-app
./paas-core-app/src
./paas-core-app/src/main
./paas-core-app/src/main/java
./paas-core-app/src/main/java/com
./paas-core-app/src/main/java/com/csm
./paas-core-app/src/main/java/com/csm/paas
./paas-core-app/src/main/java/com/csm/paas/core
./paas-core-app/src/main/resources
./paas-core-case
./paas-core-case/src
./paas-core-case/src/main
./paas-core-case/src/main/java
./paas-core-case/src/main/java/com
./paas-core-case/src/main/java/com/csm
./paas-core-case/src/main/java/com/csm/paas
./paas-core-case/src/main/java/com/csm/paas/core
./paas-core-case/src/main/java/com/csm/paas/core/cases
./paas-core-case/src/main/java/com/csm/paas/core/cases/software
./paas-core-case/src/main/java/com/csm/paas/core/cases/software/impl
./paas-core-domain
./paas-core-domain/src
./paas-core-domain/src/main
./paas-core-domain/src/main/java
./paas-core-domain/src/main/java/com
./paas-core-domain/src/main/java/com/csm
./paas-core-domain/src/main/java/com/csm/paas
./paas-core-domain/src/main/java/com/csm/paas/core
./paas-core-domain/src/main/java/com/csm/paas/core/domain
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/adapter
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/adapter/port
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/adapter/repository
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/model
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/model/aggregate
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/model/entity
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/model/valobj
./paas-core-domain/src/main/java/com/csm/paas/core/domain/software/service
./paas-core-infrastructure
./paas-core-infrastructure/src
./paas-core-infrastructure/src/main
./paas-core-infrastructure/src/main/java
./paas-core-infrastructure/src/main/java/com
./paas-core-infrastructure/src/main/java/com/csm
./paas-core-infrastructure/src/main/java/com/csm/paas
./paas-core-infrastructure/src/main/java/com/csm/paas/core
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/adapter
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/adapter/port
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/adapter/repository
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/config
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/dao
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/dao/po
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/gateway
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/gateway/dto
./paas-core-infrastructure/src/main/java/com/csm/paas/core/infrastructure/redis
./paas-core-trigger
./paas-core-trigger/src
./paas-core-trigger/src/main
./paas-core-trigger/src/main/java
./paas-core-trigger/src/main/java/com
./paas-core-trigger/src/main/java/com/csm
./paas-core-trigger/src/main/java/com/csm/paas
./paas-core-trigger/src/main/java/com/csm/paas/core
./paas-core-trigger/src/main/java/com/csm/paas/core/trigger
./paas-core-trigger/src/main/java/com/csm/paas/core/trigger/http
./paas-core-trigger/src/main/java/com/csm/paas/core/trigger/job
./paas-core-trigger/src/main/java/com/csm/paas/core/trigger/mq
./paas-core-types
./paas-core-types/src
./paas-core-types/src/main
./paas-core-types/src/main/java
./paas-core-types/src/main/java/com
./paas-core-types/src/main/java/com/csm
./paas-core-types/src/main/java/com/csm/paas
./paas-core-types/src/main/java/com/csm/paas/core
./paas-core-types/src/main/java/com/csm/paas/core/types
./paas-core-types/src/main/java/com/csm/paas/core/types/enums
./paas-core-types/src/main/java/com/csm/paas/core/types/exception
```

### 遗留

本工单只铺结构：`domain/` 下只有 `package-info`，**领域模型一律未预设**——等 03 的上下文证据与 06 的纵向切片来定。

