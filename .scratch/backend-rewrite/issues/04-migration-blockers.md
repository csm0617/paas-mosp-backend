Type: research
Status: claimed
Blocked by: —

## Question

JDK 21 + Spring Boot 3.3 + fabric8 7 的迁移障碍**逐条定论**，产出 `docs/findings/04-migration-blockers.md`。逐依赖给出：现版本 → 目标版本（或替代） → 是否必须改代码 → 一手证据链接（官方 release notes / migration guide 优先）。

依赖清单（取自 `../recovered/gaop-be/pom.xml`）：mybatis-spring-boot-starter 2.1.3、mybatis-dynamic-sql 1.1.4、pagehelper-spring-boot-starter 1.2.13、druid、mysql-connector-java 5.1.48、fastdfs-client 1.27.2、fastdfs-client-java 1.30-SNAPSHOT（`lib/` 下 systemPath jar）、sshd-core 2.12.0、hutool-all 5.7.2、freemarker 2.3.31、gson 2.8.8 + gson-javatime-serialisers 1.1.2、springdoc-openapi-ui 1.5.2、bcprov-jdk15on 1.65、rsyntaxtextarea 2.0.4.1。

另需定论：

- `javax.servlet`(50 文件) / `javax.annotation`(27) / `javax.websocket`(7) / `javax.validation` 的 jakarta 化清单
- `io.kubernetes:client-java:18.0.1` → fabric8 7 的 API 映射：66 个引用文件里**真正需要改写的调用点**逐个列出（`k8sApiClientManager`、`TenxCloudImpl`、`TenxService` 优先）
- fabric8 7 对**具名端口**（`ServicePort.targetPort` / `TCPSocketAction.port` 为字符串）的行为确认——这是网络页白屏的根因，必须给出来源

## 验收

- 每条结论有一手来源链接；无法确认的显式标"未定论"，不要猜。

## Answer
