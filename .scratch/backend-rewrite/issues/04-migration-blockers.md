Type: research
Status: resolved
Blocked by: —

> 路径基准：本仓为 `/Users/csm/Desktop/PaaS/paas-core/`；厂商证据在 `/Users/csm/Desktop/Project/msop/`（`recovered/`、`recovered-web/`、`docs/`），下文相对路径均以该目录为基准。

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

交付：`docs/findings/04-migration-blockers.md`（419 行 / 16 张表）。

**最重要的一条纠正**：白屏根因**不是** client-java（18.0.1 与 fabric8 7.9.0 的对应字段都是 `IntOrString`），
而是厂商自建 `third/k8s/IoK8sApiCoreV1*` DTO 把 `targetPort`/`port` 声明成 `Integer`，
叠加 `TenxService.convertObj`(:496) 的 Gson→Jackson 往返、`JsonConfig.java:38` 只注册序列化器、
`serviceResponse`(:506) 吞异常置空 → 前端白屏。→ **换 fabric8 不会自动修好**。
（已在 `docs/findings/named-port-fabric8-7.9.0.md` 与本仓代码注释里同步更正。）

其余定论摘要：jakarta 文件数 servlet 26 / annotation 27 / websocket 1 / validation 5（工单里的 50/7 是 import 条数）；
另有 8 个文件用 `javax.crypto`/`net.ssl`/`sql`，**不受影响勿改**；`CustomJacksonConfig extends WebMvcConfigurerAdapter`（Spring 6 已删）；
21 个文件仍用 Swagger 1.x 注解；`mybatis-dynamic-sql` 1.5.2 起 `isIn` 空集合会抛异常 → 建议锁 1.5.1；lombok 必须 1.18.30+；
`org.fife.ui:rsyntaxtextarea`、`org.csource:fastdfs-client-java` 在 Central 零命中，必须私仓预置。

## Answer
