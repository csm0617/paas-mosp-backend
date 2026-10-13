# 技术底座：JDK 21 + Spring Boot 3.3 + fabric8 7，保留 MyBatis

**Status: accepted**

重写后端采用 JDK 21、Spring Boot 3.3.x（jakarta 命名空间）、fabric8 kubernetes-client 7.x、MyBatis（Boot 3 兼容 starter）、HikariCP、springdoc 2.x；构建仍用 Maven。

## Considered Options

- **保留 Spring Boot 2.7.18**：`javax.*` 约 80 个文件不用动，改造量最小。被否——等于把技术债原地冻结，JDK 21 只剩运行时的好处。
- **换 JPA 或 jOOQ**：被否——39 张表、87 个迁移、79 个 DAO 已是事实标准，换 ORM 是纯风险、零收益。
- **保留 Druid 连接池**：被否——换 HikariCP，减少 Boot 3 升级面的不确定性。

## Consequences

- 必须做 `javax → jakarta` 迁移（`javax.servlet` 50 个文件、`javax.annotation` 27、`javax.websocket` 7 起步）。
- fabric8 7 取代 `io.kubernetes:client-java:18.0.1`（66 个引用文件），顺带消除**具名端口**反序列化失败——网络管理页白屏的根因；代价是这些调用点要逐个改写。
- 迁移障碍的逐条定论见 ticket「04-migration-blockers」。
