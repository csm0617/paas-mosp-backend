# 04 — JDK 21 + Spring Boot 3.3 + fabric8 7 迁移障碍（逐条定论）

> 工单：04-migration-blockers
> 证据输入（只读）：`recovered/gaop-be/pom.xml`、`recovered/gaop-be/BOOT-INF-lib-list.txt`、
> `recovered/gaop-be/src/main/java/**`（1600 个 `.java`，CFR 0.152 反编译）、
> `recovered/gaop-be/src/main/resources/**`（application*.yml/properties 亦已恢复）
> 方法：一手来源优先——上游 release notes / CHANGELOG / 官方站点 / Maven Central 原始
> `maven-metadata.xml` 与 `-sources.jar` / `-sources.jar` 内的真实源码；无法用一手来源定论的
> 一律显式标注 **未定论**。所有 `recovered/` 证据未被修改。

---

## 0. 结论速览

| # | 结论 | 定性 |
|---|---|---|
| 1 | 15 个必查依赖里，**7 个必须改代码**（mybatis-dynamic-sql、druid 换 artifactId 不算改代码、mysql 驱动类名、springdoc、bcprov、rsyntaxtextarea 坐标、fastdfs×2） | 见 §1 |
| 2 | 工单里的 `javax.servlet(50)`/`javax.websocket(7)` 是**import 语句条数**，不是文件数：servlet 实际落在 **26 个文件**，websocket 落在 **1 个文件** | §2 |
| 3 | `javax.crypto` / `javax.net.ssl` / `javax.sql` 共 8 个文件**完全不受影响**（JDK 自带，非 Jakarta EE） | §2 |
| 4 | **fabric8 7 可以正常反序列化字符串型 `targetPort`/probe `port`（已定论）**；但厂商白屏的根因**不是 client-java**，而是厂商自建的 `third/k8s/IoK8sApiCoreV1*` DTO 把它声明成了 `Integer` | §4 |
| 5 | 白屏的**精确失败点**已在本仓库源码里定位到行（`TenxService.serviceResponse` → `convertObj`，Gson→Jackson 往返） | §4 |
| 6 | 离线私仓必须预置 4 类 Central 不可解析的产物（`org.csource` / `org.fife.ui` / tobato fork / gson-javatime-serialisers）；arm64 侧原厚包无 JNI 阻塞，仅 Kafka 压缩库待复核 | §5 |

---

## 1. 依赖逐行表（现版本 → 目标版本 → 是否必须改代码 → 一手证据）

### 1.1 工单点名的 15 项

| 依赖 | 现版本 | 目标版本 / 替代 | 必须改代码？ | 一手证据 |
|---|---|---|---|---|
| `org.mybatis.spring.boot:mybatis-spring-boot-starter` | 2.1.3 | **3.0.x**（官方要求表：3.0 线 = Spring Boot 3.2–3.5、Java 17+） | **否**（仅升级版本；`@Mapper`/`@MapperScan`/`mybatis.*` 配置键不变） | 官方 Requirements 表：<https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/>（源文件 `mybatis-spring-boot-autoconfigure/src/site/markdown/index.md` L26–L30）；版本列表 <https://repo1.maven.org/maven2/org/mybatis/spring/boot/mybatis-spring-boot-starter/maven-metadata.xml> |
| `org.mybatis.dynamic-sql:mybatis-dynamic-sql` | 1.1.4 | **1.5.1**（若要 1.5.2 需同步加空表保护，见备注） | **是** | Release 1.5.0 官方 CHANGELOG（"one potential breaking change"：`WhereSupplier` 用法变更 + "much of the deprecated code in prior releases was removed"）：<https://mybatis.org/mybatis-dynamic-sql/docs/CHANGELOG.html> ；1.5.0 release note：<https://github.com/mybatis/mybatis-dynamic-sql/releases/tag/mybatis-dynamic-sql-1.5.0> |
| `com.github.pagehelper:pagehelper-spring-boot-starter` | 1.2.13 | **2.1.1**（Boot 3 线；1.4.6 只是"兼容 Spring Boot 3.0"的权宜版本） | **部分**（PageHelper 5.1.11→6.1.1 存在分页拦截器/`asyncCount` 行为差异，需回归分页接口） | 官方 README 版本表：v2.1.1「支持 JDK17+ / mybatis-spring-boot-starter 3.0.4 / spring-boot-dependencies 3.5.0」、v1.4.6「兼容 Spring Boot 3.0」：<https://github.com/pagehelper/pagehelper-spring-boot> ；版本列表 <https://repo1.maven.org/maven2/com/github/pagehelper/pagehelper-spring-boot-starter/maven-metadata.xml> |
| `com.alibaba:druid-spring-boot-starter` | 1.2.15 | **`com.alibaba:druid-spring-boot-3-starter`**（artifactId 必须换） | **否**（换坐标即可；配置键 `spring.datasource.druid.*` 不变） | 对照两个 POM 的实测依赖：Boot2 版用 `javax.servlet:javax.servlet-api` + `springboot2.version`：<https://repo1.maven.org/maven2/com/alibaba/druid-spring-boot-starter/1.2.28/druid-spring-boot-starter-1.2.28.pom> ；Boot3 版用 `jakarta.servlet-api:6.0.0` + `spring-jdbc:6.1.21` + `springboot3.version`：<https://repo1.maven.org/maven2/com/alibaba/druid-spring-boot-3-starter/1.2.28/druid-spring-boot-3-starter-1.2.28.pom> |
| `mysql:mysql-connector-java` | 5.1.48 | **`com.mysql:mysql-connector-j`**（Boot 3.3.13 管理 8.3.0；Central 当前 release 26.7.0） | **是（配置层）**：`driver-class-name` 应为 `com.mysql.cj.jdbc.Driver`。注意 5.1 的旧类名在 8.3.0/9.7.0 里仍以**弃用 shim**存在（能启动、只打弃用告警），故属"必须改"而非"不改就崩" | jar 内 `META-INF/services/java.sql.Driver` 实测：5.1.48 = `com.mysql.jdbc.Driver`（<https://repo1.maven.org/maven2/mysql/mysql-connector-java/5.1.48/mysql-connector-java-5.1.48.jar>），8.3.0 = `com.mysql.cj.jdbc.Driver`（<https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar>）；Boot 3.3.13 的托管版本：<https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/3.3.13/spring-boot-dependencies-3.3.13.pom>（`mysql.version=8.3.0`，坐标已迁到 `com.mysql`） |
| `com.github.tobato:fastdfs-client` | 1.27.2 | **无 Boot 3 版本**。1.27.2 即 Central 最新；其 POM 的 parent 仍为 `spring-boot-starter-parent:2.0.0.RELEASE` | **是**（自维护 fork 或换实现；仅编译期无碍，运行期 `FdfsClientConfig` 依赖 Boot 2 autoconfigure） | 版本列表（1.27.2 为末位）：<https://repo1.maven.org/maven2/com/github/tobato/fastdfs-client/maven-metadata.xml> ；POM 证据：<https://repo1.maven.org/maven2/com/github/tobato/fastdfs-client/1.27.2/fastdfs-client-1.27.2.pom> |
| `org.csource:fastdfs-client-java` | 1.30-SNAPSHOT（`<scope>system</scope>` + `<systemPath>${pom.basedir}/lib/fastdfs-client-java-1.30-SNAPSHOT.jar</systemPath>`，pom.xml L351–357） | **不存在可解析坐标**。`org.csource:fastdfs-client-java` 在 Maven Central **无任何版本** | **是**（必须落地为自有仓库坐标，或改用 maintained fork） | Central 无该 groupId（`search.maven.org` 查询 `org.csource` 返回 0 命中）；Central 目录不存在：<https://repo1.maven.org/maven2/org/csource/> ；`recovered/gaop-be/BOOT-INF-lib-list.txt:58` 证明产物以 `fastdfs-client-java-1.30-SNAPSHOT.jar` 形式进了厚包 |
| `org.apache.sshd:sshd-core` | 2.12.0 | **2.20.0**（3.0.0 仅到 M6，尚无 GA，不建议） | **否**（官方 README：运行期基线 "Java 8+ as of version 2.3"，JDK 21 无障碍） | 官方 README：<https://github.com/apache/mina-sshd/blob/master/README.md>（L37「Java 8+ at runtime (as of version 2.3)」、L38「Java 17+ at build time (as of version 2.14)」）；版本列表 `<latest>/<release>`：<https://repo1.maven.org/maven2/org/apache/sshd/sshd-core/maven-metadata.xml> |
| `cn.hutool:hutool-all` | 5.7.2 | **5.8.48**（`cn.hutool` 线最新；hutool 6 迁到 `org.dromara.hutool`，仍为 `6.0.0.M3` 里程碑） | **否**。关键点：本应用**完全没有使用** hutool 的 servlet 模块（全仓 `ServletUtil`/`JakartaServletUtil` 命中 0 次），故 hutool 的 javax 残留不影响 | 5.8.48 jar 实测同时含 `cn/hutool/extra/servlet/ServletUtil.class`(javax) 与 `cn/hutool/extra/servlet/JakartaServletUtil.class`(jakarta)：<https://repo1.maven.org/maven2/cn/hutool/hutool-all/5.8.48/hutool-all-5.8.48.jar> ；版本：<https://repo1.maven.org/maven2/cn/hutool/hutool-all/maven-metadata.xml>；hutool6 坐标：`search.maven.org` `a:hutool-all` → `org.dromara.hutool:hutool-all:6.0.0.M3` |
| `org.freemarker:freemarker` | 2.3.31 | **2.3.34**（Boot 3.3.13 托管值） | **否**。本应用只用核心模板 API（`freemarker.template.{Configuration,Template,TemplateException,TemplateExceptionHandler}`），**不用** `freemarker.ext.servlet`，因此 freemarker 的 servlet 集成的 javax/jakarta 归属与本项目无关 | Boot 3.3.13 托管 `<freemarker.version>2.3.34`：<https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/3.3.13/spring-boot-dependencies-3.3.13.pom> ；2.3.34 POM 无任何编译期依赖：<https://repo1.maven.org/maven2/org/freemarker/freemarker/2.3.34/freemarker-2.3.34.pom> |
| `com.google.code.gson:gson` | 2.8.8 | **2.10.1**（Boot 3.3.13 托管值）或 2.13.2（Central 最新） | **否**（纯 Java，公开 API 向后兼容；本项目经 `io.kubernetes.client` 与 `JsonConfig.GSON` 使用） | Boot 3.3.13 托管 `<gson.version>2.10.1`：同上 Boot POM；版本：<https://repo1.maven.org/maven2/com/google/code/gson/gson/maven-metadata.xml> |
| `com.fatboyindustrial.gson-javatime-serialisers:gson-javatime-serialisers` | 1.1.2 | **1.1.2**（Central 上 1.1.2 即末版，无更新） | **否**（唯一用法是 `JsonConfig` 里的 `Converters.registerOffsetDateTime`，该 API 自 1.0.0 起存在） | 版本列表（末位 1.1.2）：<https://repo1.maven.org/maven2/com/fatboyindustrial/gson-javatime-serialisers/gson-javatime-serialisers/maven-metadata.xml> |
| `org.springdoc:springdoc-openapi-ui` | 1.5.2 | **`org.springdoc:springdoc-openapi-starter-webmvc-ui`（2.x）** | **是**（artifactId 改名 + 1.x 专属属性/配置调整）**外加**：全仓 **21 个文件**仍用 Swagger **1.x** 注解 `io.swagger.annotations.{ApiModelProperty,ApiModel,ApiOperation}`（共 25 处），springdoc 2.x 只认 `io.swagger.v3.oas.annotations.*`，这 25 处必须迁移 | springdoc 官方站点（当前 2.9.1，Spring Boot 3.x 章节）：<https://springdoc.org/> ；starter 坐标与版本：<https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml> ；1.x 末版：<https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi-ui/maven-metadata.xml> |
| `org.bouncycastle:bcprov-jdk15on` | 1.65 | **`org.bouncycastle:bcprov-jdk18on`**（Central 最新 1.86；`jdk15on` 线终止于 1.70，已被取代） | **代码不用改**（包名 `org.bouncycastle.jce.provider.BouncyCastleProvider` 不变，全仓仅此一个 BC import），**但坐标必须换** | `jdk15on` 末版 1.70：<https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk15on/maven-metadata.xml> ；`jdk18on` 持续发布至 1.86：<https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/maven-metadata.xml> |
| `org.fife.ui:rsyntaxtextarea` | 2.0.4.1 | **`com.fifesoft:rsyntaxtextarea`**（同版本 2.0.4.1 存在；最新 4.0.1） | **是（坐标必须改）**：`org.fife.ui` 这个 groupId 在 Maven Central **完全不存在**（`numFound=0`），Central 目录 404。本仓只用到 `org.fife.io.UnicodeReader`（`service/DatabaseService.java:120`），换坐标后无需改代码 | Central 上 `org.fife.ui` 目录 404：<https://repo1.maven.org/maven2/org/fife/ui/rsyntaxtextarea/maven-metadata.xml> ；正确坐标版本列表（含 2.0.4.1）：<https://repo1.maven.org/maven2/com/fifesoft/rsyntaxtextarea/maven-metadata.xml> ；`com.fifesoft:rsyntaxtextarea:2.0.4.1` jar 实测含 `org/fife/io/UnicodeReader.class`：<https://repo1.maven.org/maven2/com/fifesoft/rsyntaxtextarea/2.0.4.1/rsyntaxtextarea-2.0.4.1.jar> |

#### 关于 `mybatis-dynamic-sql` 的"必须改代码"，三条具体理由

1. **可变参数类型变了**。1.1.4 的签名是
   `where(BindableColumn<T>, VisitableCondition<T>, SqlCriterion... andCriteria)`；
   1.5.x 变为 `where(BindableColumn<T>, VisitableCondition<T>, AndOrCriteriaGroup... subCriteria)`
   （1.5.2 源码 `org/mybatis/dynamic/sql/where/AbstractWhereStarter.java:41`）。
   证据对比（同一坐标的 `-sources.jar`）：
   1.1.4 <https://repo1.maven.org/maven2/org/mybatis/dynamic-sql/mybatis-dynamic-sql/1.1.4/mybatis-dynamic-sql-1.1.4-sources.jar>
   vs 1.5.2 <https://repo1.maven.org/maven2/org/mybatis/dynamic-sql/mybatis-dynamic-sql/1.5.2/mybatis-dynamic-sql-1.5.2-sources.jar>。
   本仓反编译产物里有 **518 处 `new SqlCriterion[0]`**；另有 **69 个文件**含 `import org.mybatis.dynamic.sql.SqlCriterion;`（即 518 处分布在这些文件里）。
   注：CFR 会把"省略可变参数"的调用也物化成 `new SqlCriterion[0]`，所以**以反编译源码为改写起点时这 518 处必须改成 `AndOrCriteriaGroup` 或直接删掉该实参**；若原始源码本就省参，则无需改（**该点属反编译产物歧义，改写时按 518 处逐一确认**）。
2. `SqlCriterion` 由 `public class SqlCriterion<T>`（1.1.4）变为 `public abstract class SqlCriterion`（1.5.2）。本仓只用数组形式 `new SqlCriterion[0]`，不实例化，故无额外影响。
3. **1.5.2 的空列表语义是行为陷阱**：官方 CHANGELOG「Release 1.5.2 - June 3, 2024」明确写
   *"Potentially Breaking Change: the 'in' conditions ('isIn', 'isNotIn', 'isInCaseInsensitive', 'isNotInCaseInsensitive') will now render if the input list of values is empty. This will lead to a runtime exception."*
   本仓存在 `SqlBuilder.isIn(serviceList)`（`service/SoftwareService.java:2132`）、`SqlBuilder.isIn(services)`（`…:2301`）等调用，**空集合会从"静默忽略"变为"运行期异常"**，必须改用 `isInWhenPresent(...)` 或提前判空。
   证据：<https://mybatis.org/mybatis-dynamic-sql/docs/CHANGELOG.html>
   → 因此建议**锁 1.5.1**，或按上述改造后上 1.5.2（1.5.1/1.5.2 版本列表见 <https://repo1.maven.org/maven2/org/mybatis/dynamic-sql/mybatis-dynamic-sql/maven-metadata.xml>）。

### 1.2 额外高风险依赖（工单未点名，但不处理必然编译/启动失败）

| 依赖 | 现版本 | 目标 | 结论 | 一手证据 |
|---|---|---|---|---|
| `org.projectlombok:lombok` | 1.18.12 | **1.18.48** | **必须升**。Lombok < 1.18.20 在 JDK 16+ 上因 `IllegalAccessError`/`NoSuchFieldError` 无法工作，JDK 21 需 1.18.30+ | 版本：<https://repo1.maven.org/maven2/org/projectlombok/lombok/maven-metadata.xml>（`<release>1.18.48`）；依据：`pom.xml` L80–84 声明，`BOOT-INF-lib-list.txt:129` 为 `lombok-1.18.12.jar` |
| `org.yaml:snakeyaml`（pom 显式钉 1.30） | 1.30 | **2.2**（Boot 3.3.13 托管） | **必须改**：显式钉 1.30 会与 Boot 3.3 的 2.2 冲突且 1.x 有 CVE；snakeyaml 2.0 移除了部分 API | Boot 3.3.13 `<snakeyaml.version>2.2`：<https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/3.3.13/spring-boot-dependencies-3.3.13.pom> |
| `com.github.mkopylec:charon-spring-webmvc` | 4.6.0 | **5.x**（5.4.0 依赖 `spring-web:6.2.0` + resilience4j 2.3.0，即 Boot 3.x/Jakarta） | **必须换**。4.x 面向 Spring 5；本仓 `config/ReverseProxyConfig.java` 深度使用其 API（`CharonConfigurer` 等 9 个类），需按 5.x API 核对 | 版本列表：<https://repo1.maven.org/maven2/com/github/mkopylec/charon-spring-webmvc/maven-metadata.xml> ；5.4.0 POM：<https://repo1.maven.org/maven2/com/github/mkopylec/charon-spring-webmvc/5.4.0/charon-spring-webmvc-5.4.0.pom> |
| `io.lettuce:lettuce-core`（pom 显式钉） | 6.0.1.RELEASE | Boot 3.3 托管 6.3.2.RELEASE | 建议**去掉显式版本**改用 Boot 托管 | Boot 3.3.13 POM（同上）；`BOOT-INF-lib-list.txt:122` |
| `javax.validation:validation-api`（pom 显式声明，无版本） | Boot 2.3.1 托管为 2.0.1.Final | 删除该依赖；改由 `jakarta.validation:jakarta.validation-api`（Boot 3.3.13 托管 3.0.2）承担 | **必须删/换坐标** | Boot 2.3.1 托管 `<javax-validation.version>2.0.1.Final`：<https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.3.1.RELEASE/spring-boot-dependencies-2.3.1.RELEASE.pom> ；Boot 3.3.13 托管 `jakarta.validation-api:3.0.2`：<https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/3.3.13/spring-boot-dependencies-3.3.13.pom> ；`BOOT-INF-lib-list.txt:241` 确证运行时是 `validation-api-2.0.1.Final.jar` |
| `org.apache.tika:tika-parsers` / `poi` / `commons-fileupload` | 1.24.1 / 4.1.2 / 1.4 | 随 Boot 3.3 重选 | 非阻塞项，但 Tika 1.x → 2.x/3.x 是包名级大改（`org.apache.tika.*` → 部分迁到 `org.apache.tika.parser.*`），需单独评估 | — |

---

## 2. jakarta 化清单（逐文件 + 替代类）

### 2.0 计数口径纠正（重要）

工单给出的 `javax.servlet(50)`、`javax.websocket(7)` 是 **import 语句条数**；`javax.annotation(27)` 恰好同时是条数与文件数。按**文件**口径实测：

| 包 | import 条数 | 文件数 | 是否受 Jakarta 影响 |
|---|---|---|---|
| `javax.servlet.*` | 50 | **26** | 是 |
| `javax.annotation.Resource` | 27 | **27** | 是 |
| `javax.websocket.*` | 7 | **1** | 是 |
| `javax.validation.*` | 5 | **5** | 是 |
| `javax.sql.DataSource` | 4 | **4** | **否**（JDK 自带） |
| `javax.crypto.*` | 7 | **2** | **否**（JDK 自带） |
| `javax.net.ssl.*` | 8 | **2** | **否**（JDK 自带） |

复核命令：`grep -rlE 'import\s+(static\s+)?javax\.<pkg>\.' recovered/gaop-be/src/main/java --include='*.java' | wc -l`

### 2.1 需要 `javax.*` → `jakarta.*`（仅换包名前缀，简单类名不变）

| javax 类 | import 条数 | 替代类 |
|---|---|---|
| `javax.servlet.http.HttpServletResponse` | 19 | `jakarta.servlet.http.HttpServletResponse` |
| `javax.servlet.ServletOutputStream` | 15 | `jakarta.servlet.ServletOutputStream` |
| `javax.servlet.http.HttpServletRequest` | 13 | `jakarta.servlet.http.HttpServletRequest` |
| `javax.servlet.http.Cookie` | 3 | `jakarta.servlet.http.Cookie` |
| `javax.annotation.Resource` | 27 | `jakarta.annotation.Resource` |
| `javax.websocket.server.ServerEndpoint` | 1 | `jakarta.websocket.server.ServerEndpoint` |
| `javax.websocket.server.PathParam` | 1 | `jakarta.websocket.server.PathParam` |
| `javax.websocket.Session` | 1 | `jakarta.websocket.Session` |
| `javax.websocket.OnOpen` / `OnMessage` / `OnError` / `OnClose` | 各 1 | `jakarta.websocket.OnOpen` / `OnMessage` / `OnError` / `OnClose` |
| `javax.validation.constraints.NotNull` | 3 | `jakarta.validation.constraints.NotNull` |
| `javax.validation.constraints.NotEmpty` | 1 | `jakarta.validation.constraints.NotEmpty` |
| `javax.validation.constraints.NotBlank` | 1 | `jakarta.validation.constraints.NotBlank` |

### 2.2 `javax.servlet.*` — 26 个文件的完整清单

```
aspect/AuditAnnotationAspect.java                 controller/UserController.java
aspect/RequestLoggingAspect.java                  controller/pub/PublicController.java
controller/BackController.java                    controller/pub/PublicFdfsController.java
controller/ClientController.java                  service/BackService.java
controller/ConfigMapController.java               service/ClientService.java
controller/CsSoftwareController.java              service/FileService.java
controller/DataBaseController.java                service/RoleService.java
controller/ImageController.java                   service/SoftwareService.java
controller/K8sProxyController.java                service/UserAuditService.java
controller/RoleController.java                    service/UserService.java
controller/SoftwareController.java                utils/I18nUtil.java
controller/SubRoleController.java                 utils/ResponseUtils.java
controller/SyncController.java
controller/UserAuditController.java
```
（路径前缀统一为 `recovered/gaop-be/src/main/java/com/glriverside/gaop/be/`）

**同一批文件里还有 Spring 6 的移除项，必须一并改**：
`config/CustomJacksonConfig.java:15,19` 继承 `org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter` —— 该类在 Spring Framework 6 中**已被删除**，必须改为 `implements WebMvcConfigurer`。
（Spring 6 的其它已知移除项如 `HttpServletResponse#setStatus(int, String)` 在本仓未出现，已核。）

### 2.3 `javax.annotation.Resource` — 27 个文件的完整清单

```
controller/ConfigMapController.java      service/BackService.java
controller/DashboardsController.java     service/ComputerService.java
controller/ImageController.java          service/DashboardsService.java
controller/K8sProxyController.java       service/DockerService.java
controller/LanguageController.java       service/FileService.java
controller/ModeController.java           service/ImageService.java
controller/ServiceController.java        service/RoleService.java
controller/SubRoleController.java        service/SoftwareService.java
controller/SyncController.java           service/TenxService.java
controller/pub/PubVipController.java     service/UserService.java
controller/pub/PublicController.java     service/impl/K3sImageServiceImpl.java
                                         service/impl/K8sImageServiceImpl.java
                                         service/impl/LanguageServiceImpl.java
                                         service/impl/ModeServiceImpl.java
                                         service/impl/NodeVersionServiceImpl.java
                                         utils/FastDFSClientUtil.java
```
27 个 import 对应 27 个文件，**1:1**，无遗漏。

### 2.4 `javax.websocket.*` — 唯一的 1 个文件

`config/websocket/WebSocket.java`（7 个 import，见 §2.1 表）。
注意：Tomcat 10 的 websocket 实现也换成 `tomcat-embed-websocket-10.1.x`，`jakarta.websocket` 由 `spring-boot-starter-websocket` 传递提供，无需额外坐标。

### 2.5 `javax.validation.*` — 5 个文件

| 文件 | 注解 |
|---|---|
| `controller/form/LoginForm.java:7` | `@NotEmpty` |
| `controller/form/software/RelateServices.java:8` | `@NotNull` |
| `controller/request/NeedBuckUpTableForm.java:8` | `@NotBlank` |
| `service/TenxService.java:240` | `@NotNull` |
| `service/K8sService.java:27` | `@NotNull` |

校验实现侧：`hibernate-validator-6.1.5.Final.jar`（`BOOT-INF-lib-list.txt:71`）→ Boot 3.3.13 托管 `hibernate-validator:8.0.2.Final`，随 `spring-boot-starter-validation` 自动升级。

### 2.6 **不受影响**的 `javax.*`（属 JDK，不要错改）

| 包 | 文件 | 说明 |
|---|---|---|
| `javax.sql.DataSource` | `config/multidb/MultiDataSource.java`、`config/multidb/MybatisConfig.java`、`service/DatabaseService.java`、`utils/RunSqlScript.java` | `javax.sql` 是 JDK 标准库（`java.sql` 模块的一部分），**永远不是** `jakarta.sql`，保持原样 |
| `javax.crypto.*`（`Cipher`、`KeyGenerator`、`SecretKeySpec`、`NoSuchPaddingException`、`IllegalBlockSizeException`、`BadPaddingException`） | `config/AesConfig.java`、`config/RsaConfig.java` | JDK 自带，保持原样 |
| `javax.net.ssl.*`（`SSLContext`、`SSLSocketFactory`、`HostnameVerifier`、`TrustManager`、`X509TrustManager`、`SSLSession`） | `config/FeignConfig.java`、`config/ReverseProxyConfig.java` | JDK 自带（用于"关闭 SSL 证书校验"），保持原样 |

> 判据：`javax.*` 里 **只有** Jakarta EE 规范（Servlet / Annotation / Validation / WebSocket / JMS / JAX-RS / Persistence / Transaction / Mail）从 EE 9 起改名 `jakarta.*`；`java.*`/`javax.crypto`/`javax.net`/`javax.sql`/`javax.naming`/`javax.management` 属 JDK，不变。

---

## 3. K8s 客户端换装：`io.kubernetes:client-java:18.0.1` → fabric8 7

- 目标坐标：`io.fabric8:kubernetes-client`（版本 **7.9.0**；Central 上 `<release>` 已是 8.0.0，本工单按 7.x 最后版本 7.9.0 定）。
  <https://repo1.maven.org/maven2/io/fabric8/kubernetes-client/maven-metadata.xml>
- fabric8 7 的 Java 基线 = **Java 11+**（7.0.0 起），JDK 21 无问题。
  官方 MIGRATION-v7：<https://github.com/fabric8io/kubernetes-client/blob/main/doc/MIGRATION-v7.md>（§「Java baseline set to Java 11」：*Starting from version 7.0.0, you will need a Java 11+ runtime*）
- 官方 DSL 速查表：<https://github.com/fabric8io/kubernetes-client/blob/main/doc/CHEATSHEET.md>

### 3.1 API 映射表

| client-java 18.0.1 | fabric8 7 对应 | 是否需改写 | 依据 |
|---|---|---|---|
| `CoreV1Api` / `AppsV1Api` / `BatchV1Api` / `NetworkingV1Api` / `StorageV1Api` / `AutoscalingV*Api` / `CustomObjectsApi` | 无对应"Api 类"；改为**一个 `KubernetesClient`** 注入，按资源走 DSL：`client.pods()` / `client.services()` / `client.nodes()` / `client.configMaps()` / `client.secrets()` / `client.namespaces()` / `client.apps().deployments()` / `client.apps().statefulSets()` / `client.apps().daemonSets()` / `client.batch().jobs()` / `client.network().v1().ingresses()` / `client.storage().v1().storageClasses()` / `client.autoscaling().v1().horizontalPodAutoscalers()` | **必须**（结构性改写：全局静态 Api 实例 → 注入式客户端） | CHEATSHEET：<https://github.com/fabric8io/kubernetes-client/blob/main/doc/CHEATSHEET.md> |
| `listNamespacedService(ns, null×10)` | `client.services().inNamespace(ns).list()` | 必须 | 同上 |
| `listNamespacedPod(ns, …)` / `listPodForAllNamespaces(…)` | `client.pods().inNamespace(ns).list()` / `client.pods().inAnyNamespace().list()` | 必须 | 同上 |
| `listNamespacedDeployment` / `listDeploymentForAllNamespaces` / `listNamespacedStatefulSet` / `listStatefulSetForAllNamespaces` / `listNamespacedDaemonSet` / `listDaemonSetForAllNamespaces` | `client.apps().deployments().inNamespace(ns).list()` / `.inAnyNamespace().list()`；`client.apps().statefulSets()…`；`client.apps().daemonSets()…` | 必须 | 同上（`deployments()`/`statefulSets()`/`daemonSets()` 均在 CHEATSHEET 出现） |
| `listIngressForAllNamespaces` | `client.network().v1().ingresses().inAnyNamespace().list()` | 必须 | CHEATSHEET 含 `client.network().v1().ingresses()` |
| `readNamespacedService/Deployment/Pod/Node/ConfigMap/Secret` | `client.<res>().inNamespace(ns).withName(name).get()` | 必须 | 同上 |
| `createNamespaced*/replaceNamespaced*/deleteNamespaced*` | `client.<res>().inNamespace(ns).resource(obj).create()` / `.update()` / `.delete()`；`resource(...).serverSideApply()` | 必须 | 同上 |
| `patchNamespacedServiceCall(...)` + `PatchUtils.patch(V1Service.class, call, "application/merge-patch+json", apiClient)` | `client.services().inNamespace(ns).withName(name).edit(...)`，或显式 `patch(PatchContext.of(PatchType.STRATEGIC_MERGE / JSON_MERGE / JSON_PATCH), json)` | **必须**（补丁类型与语义需逐处确认：`application/merge-patch+json` ↔ `PatchType.JSON_MERGE`） | CHEATSHEET L1977 `.patch(PatchContext.of(PatchType.JSON_MERGE), …)`、L137/L342 `.edit(...)` |
| `io.kubernetes.client.util.Yaml.load(...)` / `Yaml.dump(...)` | `Serialization.unmarshal(yaml)` / `Serialization.asYaml(obj)` | 必须 | CHEATSHEET L2510 `Serialization.asYaml(myPod)` |
| `io.kubernetes.client.util.Watch` + `Watch.Response<T>` + `Watch.createWatch(client, call, type)` | `client.<res>().…watch(new Watcher<T>(){ onEvent(WatchEvent<T>) })`；返回 `Watch` | 必须（模式不同：迭代器 → 回调 `Watcher`） | CHEATSHEET L173/L270/L403 等 9 处 `watch(new Watcher<>(){…})`；MIGRATION-v7 亦提示 watch 相关变化 |
| `io.kubernetes.client.PodLogs`（`LogsHandler`/`KubeLogsHandler` 共 8 处） | `client.pods().inNamespace(ns).withName(pod).getLog()` / `.tailingLines(n).watchLog(out)` → `LogWatch` | 必须 | CHEATSHEET L143 `getLog()`、L147 `LogWatch` + `tailingLines(10).watchLog(...)` |
| `io.kubernetes.client.Exec` | `resource.inContainer("x").withTTY().redirectingInput().exec("sh","-c",...)` → `ExecWatch` | 必须 | CHEATSHEET L218–L220 |
| `Configuration.getDefaultApiClient()` / `setDefaultApiClient` | 删除；改为注入 `KubernetesClient`（或用 `new KubernetesClientBuilder().build()`） | 必须 | CHEATSHEET L80/L229；MIGRATION-v7「Config changes」 |
| `Config.defaultClient()` / `ClientBuilder.kubeconfig(...)` / `ClientBuilder.cluster()` / `KubeConfig.loadKubeConfig(reader)` | `new ConfigBuilder()...build()` + `new KubernetesClientBuilder().withConfig(cfg).build()`（支持多 kubeconfig，见 MIGRATION-v7「Support for multiple kubeconfig files」） | 必须 | CHEATSHEET L86–L89；MIGRATION-v7「Config changes」 |
| `io.kubernetes.client.util.credentials.AccessTokenAuthentication` | `new ConfigBuilder().withMasterUrl(url).withOauthToken(token).withTrustCerts(true).build()` →（可选 `withAutoOAuthToken` / `withOauthTokenProvider`） | 必须 | 已实测 7.9.0 字节码：`ConfigBuilder` → `ConfigFluent` → `SundrioConfigFluent` 提供 `withOauthToken(String)`、`withAutoOAuthToken(String)`、`withOauthTokenProvider(OAuthTokenProvider)`；`ConfigFluent` 提供 `withTrustCerts(boolean)`。构件：<https://repo1.maven.org/maven2/io/fabric8/kubernetes-client-api/7.9.0/kubernetes-client-api-7.9.0.jar>（`javap io.fabric8.kubernetes.client.SundrioConfigFluent`）；`ConfigBuilder` 用法见 CHEATSHEET L86 |
| `io.kubernetes.client.custom.IntOrString` | `io.fabric8.kubernetes.api.model.IntOrString` | 必须（换包名）；方法名 `getIntValue()/getStrValue()` → **`getIntVal()/getStrVal()`** | 7.9.0 源码 `IntOrString.java`（`getIntVal()` / `getStrVal()`）：<https://repo1.maven.org/maven2/io/fabric8/kubernetes-model-core/7.9.0/kubernetes-model-core-7.9.0-sources.jar> |
| `io.kubernetes.client.custom.Quantity` | `io.fabric8.kubernetes.api.model.Quantity`（`Quantity.fromString` 仍存在） | 必须（换包名） | 同 sources jar（`Quantity.java` 在 `io/fabric8/kubernetes/api/model/`） |
| `io.kubernetes.client.common.KubernetesObject` | `io.fabric8.kubernetes.api.model.HasMetadata`（或 `KubernetesResource`） | 必须 | 同 sources jar（`HasMetadata.java` / `KubernetesResource.java`） |
| `io.kubernetes.client.openapi.JSON`（`JSON.serialize` / `deserialize`） | `Serialization.asJson/asYaml/unmarshal`（fabric8 原生 Jackson） | 必须 | CHEATSHEET L2506–L2510 |
| `io.kubernetes.client.openapi.ApiResponse`（`getData()`/`getStatusCode()`） | 无直接对应：DSL 直接返回对象；需要状态码时用 `…get()` 抛 `KubernetesClientException`（含 `getCode()`） | 必须 | MIGRATION-v7；CHEATSHEET |
| `io.kubernetes.client.openapi.Pair` | 无对应 | 必须 | — |
| `io.kubernetes.client.util.ModelMapper`（`addModelMap`） | 不需要（fabric8 有内置模型注册；`GenericKubernetesResource` + `client.genericKubernetesResources(apiVersion, kind)`） | 必须 | CHEATSHEET `client.genericKubernetesResources` |
| `io.kubernetes.client.util.Streams.copy` | `io.fabric8.kubernetes.client.utils.Utils`/标准 `InputStream.transferTo` | 必须 | — |
| `ApiException`（39 处 import） | `KubernetesClientException`（`getCode()` / `getStatus()`） | 必须（**异常类型变更，catch 块需全局改**） | MIGRATION-v7 |
| `V1*` 模型（`V1Pod`/`V1Service`/`V1Deployment`/…，约 120 个类） | `io.fabric8.kubernetes.api.model.*`，同名去 `V1` 前缀（`Pod`/`Service`/`Deployment`/…）；特例：`V1Status`→`Status`、`V1Patch`→无（用 `PatchContext`+String）、`V1DeleteOptions`→`DeleteOptions` | 必须（换包名 + 改类名） | sources jar + CHEATSHEET |

### 3.2 真正需要改写的调用点（按文件，66 个引用文件里含**真实 API 调用**的 21 个）

| 文件（相对 `recovered/gaop-be/src/main/java/com/glriverside/gaop/be/`） | io.kubernetes 引用数 | 真实调用点 | 改写要点 |
|---|---|---|---|
| `utils/K8sApiClientManager.java` | 15 | `new CoreV1Api()`/`new AppsV1Api()`/`new CustomObjectsApi()`/`new BatchV1Api()`（L49–L63）、`Configuration.getDefaultApiClient()` + `ApiClient.getHttpClient().newBuilder().readTimeout(0)`（L69–L76）、`Config.defaultClient()` / `ClientBuilder.kubeconfig(…)` / `ClientBuilder.cluster()` / `KubeConfig.loadKubeConfig` / `AccessTokenAuthentication`（L81–L168） | **核心重构**：全局静态客户端 → `KubernetesClient` Bean；watch 用的"无超时客户端"改为 `client.pods()…watch()`（fabric8 自带长连接配置） |
| `controller/ServiceController.java` | 51 | **`Configuration.getDefaultApiClient()` 22 处**（含 `getJSON()` 于 L532/661/744/804）、`PatchUtils.patch(V1Service.class, …, "application/merge-patch+json", api.getApiClient())`（L806、L1322）、`readNamespacedService`、`patchNamespacedServiceCall`、14× `new IntOrString(...)`（L526/609/656/739/775/797/1069/1311 等） | patch→`edit()/patch(PatchContext.of(PatchType.JSON_MERGE), …)`；`IntOrString` 换包名；`targetPort` 语义见 §4；**本文件是 `Configuration.getDefaultApiClient()` 的最大使用方** |
| `service/TenxService.java` | 82 | `Yaml.load(...)`（L1136/1155/1245/2401/2579）、`ModelMapper.addModelMap("","v1","Service",V1Service.class)`（L1154）、**`PatchUtils.patch(V1Deployment.class, () -> appsV1Api.patchNamespacedDeploymentCall(…, new V1Patch(deployment.toString()), …), "application/strategic-merge-patch+json", …)`（L2404）**、以及 `appsV1Api.listNamespacedDeployment`×11 / `replaceNamespacedDeployment`×10 / `coreV1Api.listNamespacedService`×8 / `listNode`×7 / `networkingV1Api.listIngressForAllNamespaces`×3 / `replaceNamespacedConfigMap`×3 / `listPodForAllNamespaces`×3 等 | 最大单体：`Yaml`/`ModelMapper`/`V1Patch` 三类工具换 `Serialization`/`HasMetadata`/`PatchContext`；**注意这里是 `strategic-merge-patch+json`（与 ServiceController 的 `merge-patch+json` 不同），映射到 `PatchType.STRATEGIC_MERGE`**；并修复 §4 的 `serviceResponse` 根因（本文件即白屏现场） |
| `controller/pub/PublicController.java` | 26 | `Yaml.dump`（L923/924）、`V1ServicePort.setTargetPort(new IntOrString(...))`（L1740/1772）、`listNamespacedService` 等 | 同 ServiceController |
| `service/SoftwareService.java` | 35 | `new IntOrString(...)`（L3646/3684、replicas maxSurge/maxUnavailable L1305/1306）、`listNamespacedDeployment` 等 | 同 ServiceController；`IntOrString` 方法名 `getIntValue`→`getIntVal` |
| `service/CicdService.java` | 22 | `listIngressForAllNamespaces`、`getNamespacedCustomObject` | → `client.network().v1().ingresses().inAnyNamespace().list()`、`genericKubernetesResources(...)` |
| `service/FileService.java` | 21 | `podLogs`/文件读写 + `listNamespacedPod` 等 | → `PodResource.file(...)`/`getLog()` |
| `controller/ConfigMapController.java` | 17 | **`JsonConfig.JACKSON.readValue(json.serialize(configMap), V1ConfigMap.class)`（L142）与反向 L168/L231/L320** —— client-java(Gson) ↔ Jackson 双向桥 | **桥接层可整体删除**（fabric8 模型本身就是 Jackson 模型）；同时注意此法正是 §4 白屏的同类风险点 |
| `utils/K8sObjectUtil.java` | 16 | 模型工具方法 | 换包名为主 |
| `controller/cloud/TenxController.java` | 15 | `Yaml.load(yaml)` 转 `V1Service`（L484/514） | → `Serialization.unmarshal(yaml, Service.class)` |
| `service/K8sService.java` | 14 | `listNamespacedDeployment` 等 + `javax.validation.constraints.NotNull` | |
| `service/impl/ModeServiceImpl.java` | 13 | 少量 API 调用 + `ApplicationStarted` 联动 | |
| `ApplicationStarted.java` | 13 | **`Watch.createWatch(watchClient, listNodeCall, new TypeToken<Watch.Response<V1Node>>(){}.getType())`（L192/196）、`Watch.Response<V1Node>`、`api.listNamespacedPodCall(..., true, null)`（L324）** | → `client.nodes().watch(new Watcher<Node>(){...})`、`client.pods().inNamespace(ns).watch(...)` |
| `controller/cloud/TenxCloudImpl.java` | 12 | **`V1ServicePort.setTargetPort(new IntOrString(form.getExportPort()))`（L857、L937）**、`setPort`/`setName`/`setProtocol` | 换包名即可；**这是本工单 §4 的关键文件** |
| `controller/K8sProxyController.java` | 7 | **`Watch.createWatch(coreV1Api.getApiClient(), buildCall(client, url+"?watch=true", ...), TypeToken<Watch.Response<Object>>)`（L105/111）** + 自建 HTTP 调用 | 需保留"任意子资源 watch"能力：fabric8 无等价 `buildCall`，通常改为 `client.raw(url)` 或 `genericKubernetesResources` |
| `service/FreeMarkerJobCreator.java` | 7 | `Yaml.load(jobYamlString)` → `V1Job`（L48） | → `Serialization.unmarshal(..., Job.class)` |
| `config/websocket/handler/LogsHandler.java` | 2 | `new PodLogs()`（L87/110/195/280） | → `client.pods()…getLog()`/`watchLog()` |
| `config/websocket/handler/KubeLogsHandler.java` | 2 | `new PodLogs()`（L77/100/193/278） | 同上 |
| `config/websocket/handler/ExecHandler.java` | 1 | `Exec` | → `exec()`/`ExecWatch` |
| `utils/K8sPodCommandExecutor.java` | 3 | `Exec` | 同上 |
| `third/metrics/MetricsServerApi.java` | 5 | 自建 metrics API 调用（走 `ApiClient`） | → `client.raw(...)` 或注入 `KubernetesClient` |
| 其余 ~45 个文件 | 1–4 | **仅模型 import**（`V1ObjectMeta`、`V1Pod`…）或 `IntOrString` 类型引用 | **只需批量替换包名/类名**，无逻辑改写 |

> `third/k8s/IoK8sApiCoreV1*.java`（约 300 个文件）与 `third/response/tenx/v2/**` 是**厂商自建 DTO**，本来就不依赖 client-java 的运行时（只借用了名字），因此**不属于"换装"范围**——但 §4 的根因就在这里。

---

## 4. 具名端口行为确认 —— **已定论：fabric8 7 可正常反序列化字符串型 targetPort**

### 4.1 直接回答

**可以正常反序列化。** fabric8 7.9.0 的三个相关字段全部是 `IntOrString`，而 `IntOrString` 继承 `AnyType`，`AnyType` 上有 Jackson 委托式构造器 `@JsonCreator AnyType(Object value)`，因此 JSON 标量（字符串 `"http"` 或数字 `8080`）都能被 Jackson 直接接住。

一手证据（fabric8 7.9.0 官方 `-sources.jar` 内的真实源码）：

| 字段 | 类型 | 源码位置（7.9.0 sources jar） |
|---|---|---|
| `io.fabric8.kubernetes.api.model.ServicePort.targetPort` | `IntOrString` | `ServicePort.java:55` |
| `io.fabric8.kubernetes.api.model.TCPSocketAction.port` | `IntOrString` | `TCPSocketAction.java:43` |
| `io.fabric8.kubernetes.api.model.HTTPGetAction.port` | `IntOrString` | `HTTPGetAction.java:54` |

jar 链接：<https://repo1.maven.org/maven2/io/fabric8/kubernetes-model-core/7.9.0/kubernetes-model-core-7.9.0-sources.jar>

`IntOrString`（同 jar）：
```java
public class IntOrString extends AnyType {
  @JsonCreator
  public IntOrString(Object value) { setValue(value); }
  @Override public void setValue(Object value) {
    if (value != null && !(value instanceof Integer) && !(value instanceof String))
      throw new IllegalArgumentException("Either integer or string value needs to be provided");
    super.setValue(value);
  }
  public Integer getIntVal() { ... }   // 非 Integer 时返回 null
  public String  getStrVal() { ... }   // 非 String 时返回 null
}
```
`AnyType`（同 jar）：
```java
@JsonSerialize(using = AnyType.Serializer.class)
public class AnyType implements Serializable {
  protected Object value;
  @JsonCreator public AnyType(Object value) { this.value = value; }
  public Object getValue() { return value; }
}
```
→ 字符串进、字符串出，序列化侧由 `AnyType.Serializer` 原样写出，`targetPort: http` 可**双向**保真。

### 4.2 但是：**客户端库不是白屏的根因**（这是本工单最重要的纠正）

`client-java:18.0.1` 自身的字段**同样是 `IntOrString`**，并非 `Integer`：

| client-java 18.0.1 字段 | 实际类型 | 源码位置 |
|---|---|---|
| `V1ServicePort.targetPort` | `IntOrString` | `V1ServicePort.java:55` |
| `V1TCPSocketAction.port` | `IntOrString` | `V1TCPSocketAction.java:35` |
| `V1HTTPGetAction.port` | `IntOrString` | `V1HTTPGetAction.java:47` |

jar 链接：<https://repo1.maven.org/maven2/io/kubernetes/client-java-api/18.0.1/client-java-api-18.0.1-sources.jar>

因此 `Cannot deserialize value of type java.lang.Integer from String "http"` **不可能**由 client-java 的模型产生。它来自**厂商自己手抄的一套 DTO**：

| 类 | 字段 | 类型 | 位置 |
|---|---|---|---|
| `com.glriverside.gaop.be.third.k8s.IoK8sApiCoreV1ServicePort` | `@JsonProperty("targetPort") private Integer targetPort` | **`Integer`** | `third/k8s/IoK8sApiCoreV1ServicePort.java:21` |
| `…IoK8sApiCoreV1TCPSocketAction` | `@JsonProperty("port") private Integer port` | **`Integer`** | `…IoK8sApiCoreV1TCPSocketAction.java:15` |
| `…IoK8sApiCoreV1HTTPGetAction` | `@JsonProperty("port") private Integer port` | **`Integer`** | `…IoK8sApiCoreV1HTTPGetAction.java:22` |

（这三套 `IoK8sApi*` 共约 300 个类，是 `io.k8s.api.core.v1.*` 的旧版 k8s-java-client 时代的产物，其时代模型确实把 `targetPort` 声明为 `Integer`。）

### 4.3 失败链路（精确定位到行）

厂商后端的"网络页"数据来自 `TenxService.allService(...)`（`service/TenxService.java:2294`），它对每个 Service 调 `serviceResponse(v1Service)`：

```java
// service/TenxService.java:506-517
public IoK8sApiCoreV1Service serviceResponse(V1Service v1Service) {
    IoK8sApiCoreV1Service service = new IoK8sApiCoreV1Service();
    try {
        service = TenxService.convertObj((Object)v1Service, IoK8sApiCoreV1Service.class); // ← 在这里炸
    } catch (Exception e) {
        log.error("转换类型失败", e);
        return null;                       // ← 吞掉异常，返回 null
    }
    ...
}

// service/TenxService.java:496-499
public static <T> T convertObj(Object obj, Class<T> valueType) … {
    String json = JsonConfig.GSON.toJson(obj);              // client-java V1Service --Gson--> JSON
    return JsonConfig.JACKSON.readValue(json, valueType);   // JSON --Jackson--> IoK8sApiCoreV1Service
}
```

`JsonConfig.JACKSON`（`config/JsonConfig.java:24` 声明；`:38` **只**注册了 `IntOrString` 的 `addSerializer`，`:39` 才 `registerModule`，**没有对应的 `addDeserializer`**）目标字段又是 `Integer`。于是当集群里存在具名端口（`targetPort: http`，k8s 生态中由 Helm chart 广泛使用）时：

1. Gson 把 `IntOrString("http")` 写成 `"targetPort": "http"`；
2. Jackson 把它读进 `IoK8sApiCoreV1ServicePort.targetPort`（`Integer`）→ **`Cannot deserialize value of type java.lang.Integer from String value "http"`**；
3. `serviceResponse` 捕获后 **`return null`** → `servicesItem.setService(null)`；
4. 前端拿到的 `service` 为 null → **网络页白屏**（不是 500 错误页，正好符合"白屏"的现象描述）。

同一根因还有第二处、更早的硬失败：`TenxService.responseContainer(...)` 对探针端口直接调
`httpGet.getPort().getIntValue()`（L542、L578）与 `tcpSocket.getPort().getIntValue()`（L555、L591）——client-java 的 `IntOrString` 在具名端口时 `getIntValue()` 返回 null（或其 `intValue` 变体抛异常），同样会在具名端口下发容器时炸。

> 工作区旁证：`config/CustomJacksonConfig.java` + `config/IntOrStringDeserializer.java` + `config/IntOrStringSerializer.java` 正是厂商为 `IntOrString` 打的补丁，但**只作用于 Web 层 `MappingJackson2HttpMessageConverter` 的 ObjectMapper，不作用于 `JsonConfig.JACKSON`**，所以救不了这条链路。这是一条可直接复核的结论。

### 4.4 对本次重写的直接含义

1. **换 fabric8 不会自动修好白屏**。fabric8 的 `ServicePort.targetPort` 是 `IntOrString`，可正常反序列化；但如果重写时继续保留 `third/k8s/IoK8sApiCoreV1*` 这套 `Integer` DTO（它同时是**对外 API 契约**），故障原样复现。
2. 修法（二选一，建议都做）：
   - 把 `IoK8sApiCoreV1ServicePort.targetPort`、`IoK8sApiCoreV1TCPSocketAction.port`、`IoK8sApiCoreV1HTTPGetAction.port` 由 `Integer` 改为 `String`/`Object`/`IntOrString`（对外契约保持 JSON 上"数字或字符串"）；
   - 并且在 fabric8 化之后，**统一用 Jackson**（fabric8 原生即 Jackson）而不是 Gson→Jackson 往返，从根上消掉 `convertObj`；`serviceResponse` 的 `catch(Exception) → return null` 必须改为显式失败或降级，否则同类问题仍会静默白屏。
3. 具名端口场景必须进回归用例（`targetPort: http`、`httpGet.port: metrics`、`tcpSocket.port: db`）。

---

## 5. 离线与 arm64 影响（仅结论）

### 5.1 必须离线私仓预置

| 产物 | 原因 | 证据 |
|---|---|---|
| `org.csource:fastdfs-client-java:1.30-SNAPSHOT`（现为 `lib/` 下 `systemPath` jar） | Maven Central **根本不存在**该 groupId；`systemPath` 依赖不参与依赖解析，任何仓库都拉不到 | <https://repo1.maven.org/maven2/org/csource/>（404）；`pom.xml:351-357` |
| `com.fifesoft:rsyntaxtextarea`（替换现 `org.fife.ui:rsyntaxtextarea:2.0.4.1`） | 现坐标 `org.fife.ui` 在 Central 零命中（`numFound=0`），**只能来自旧 jcenter 或内网镜像**；换到 `com.fifesoft` 才能离线可复现 | <https://repo1.maven.org/maven2/org/fife/ui/rsyntaxtextarea/maven-metadata.xml>（404）；<https://repo1.maven.org/maven2/com/fifesoft/rsyntaxtextarea/maven-metadata.xml> |
| `com.github.tobato:fastdfs-client:1.27.2` 及其全部传递依赖（`commons-beanutils`、`commons-io:2.4`、`commons-pool2`、`slf4j-api`） | 无 Boot 3 后继版本，一旦决定自维护 fork，必须以自有坐标入库 | <https://repo1.maven.org/maven2/com/github/tobato/fastdfs-client/1.27.2/fastdfs-client-1.27.2.pom>（parent = `spring-boot-starter-parent:2.0.0.RELEASE`） |
| `com.fatboyindustrial:gson-javatime-serialisers:1.1.2` | 项目已停更（1.1.2 即末版），属于"不再演进"的构件，建议连同 jar 一起冻结入库 | <https://repo1.maven.org/maven2/com/fatboyindustrial/gson-javatime-serialisers/gson-javatime-serialisers/maven-metadata.xml> |

其它说明（结论）：
- **不需要**为 `io.fabric8:kubernetes-client:7.9.0`、`druid-spring-boot-3-starter`、`springdoc-openapi-starter-webmvc-ui`、`bcprov-jdk18on`、`com.mysql:mysql-connector-j` 做特殊处理——均为 Central 正常发布物，只要在联网环境预拉一次 `dependency:go-offline` 即可。
- Maven 侧须**移除** `<scope>system</scope>` + `systemPath` + `<includeSystemScope>true</includeSystemScope>` + `<extdirs>${project.basedir}/lib</extdirs>`（`pom.xml:356、478、490`）三处遗留写法——它们在 Maven 3.9+/4 上会告警并在未来被移除。

### 5.2 arm64（aarch64）

| 项 | 结论 |
|---|---|
| **arm64 无阻塞**。原厚包 256 个 jar 中**没有任何** `netty-transport-native-epoll`（只有 netty-buffer/codec/common/handler/resolver/transport，纯 Java），也没有 `.so`/JNI 落地 | `BOOT-INF-lib-list.txt` 无 `netty-transport-native-*`、无 `sqlite-jdbc`、无 `jnr-*` |
| `com.github.luben:zstd-jni`（Kafka 压缩，`zstd-jni-1.4.4-7.jar`） | **需在 arm64 上复核**：该构件按平台打包 native，须确认内网私仓缓存的是含 `linux-aarch_64` 的同一构件（这是本项目唯一有 native 风险的依赖）。**未定论**，见 §6 |
| `org.xerial.snappy:snappy-java`（`snappy-java-1.1.7.3.jar`） | **需在 arm64 上复核**（同类 native 打包方式）。**未定论**，见 §6 |
| 其余全部为纯 Java：`mysql-connector-j`、`druid`、`bcprov`、`sshd-core`、`freemarker`、`hutool-all`、`gson`、`tika`、`poi`、`docker-java`（HTTP 传输版）、`fabric8 kubernetes-client`（okhttp + jackson） | 无 arm64 适配需求 |
| 运行时基线 | 目标 JDK 21 + Spring Boot 3.3 本身对 linux/aarch64 无特殊要求；工程已存在 `artifacts/msop-backend-arm64.tar.gz`、`build/backend-arm64/`、`artifacts/README-backend-arm64.md`，说明 arm64 构建路径已在推进 |

---

## 6. 未定论清单（缺什么证据）

| # | 未定论项 | 还差什么证据 |
|---|---|---|
| 1 | `zstd-jni` / `snappy-java` 是否已为 linux-aarch64 预置 | 需要在目标 arm64 机器上跑一次真实 Kafka 压缩收发，或核对内网私仓中构件的 `META-INF/MANIFEST.MF`/`lib/` native 清单（本机只能看到 x86_64 参考物，属间接证据） |
| 2 | ~~fabric8 7 中"bearer token + 跳过 TLS 校验"的确切 API 名~~ **已定论（见 §3.1）** | 已用 7.9.0 字节码确认 `withOauthToken(String)` / `withAutoOAuthToken(String)` / `withOauthTokenProvider(...)`（`SundrioConfigFluent`）与 `withTrustCerts(boolean)`（`ConfigFluent`）。仅剩 `withMasterUrl` 未逐字 javap 核对（CHEATSHEET 的 `ConfigBuilder` 示例已覆盖同等用法） |
| 3 | 反编译产物里 518 处 `new SqlCriterion[0]` 究竟有多少是**原始源码显式写的** | 需要原始（未混淆/未反编译）源码，或对 `mybatis-dynamic-sql` 1.5.x 直接编译一次 `recovered` 源码树得到编译错误清单；当前只能给出"以反编译源码为起点则必须改" |
| 4 | `com.github.tobato:fastdfs-client` 是否有社区/自建的 Boot 3 分支 | 需检索 GitLab/Gitee 等非 GitHub 镜像与私仓；官方 GitHub 仓库在 Central 上无 Boot 3 发布，已定论"无官方版本"，但"无第三方 fork"未证 |
| 5 | MySQL Connector/J 5.1.48 在 JDK 21 上是否还有**除已弃用告警之外**的运行期问题 | 需要真实连库回归。已知事实：5.1.48 的 SPI 驱动类名为 `com.mysql.jdbc.Driver`，8.3.0/9.7.0 仍保留同名弃用 shim（实测可加载、仅告警）；官方升级文档页 <https://dev.mysql.com/doc/connector-j/en/connector-j-upgrading-to-8.0.html> 在本机返回 **HTTP 403**，无法引用原文 |
| 6 | `charon-spring-webmvc 5.x` 与 Spring Boot 3.3 的精确匹配版本 | 5.4.0 依赖 `spring-web:6.2.0`（对应 Boot 3.4/3.5 线），需确认 5.0–5.3 中哪个面向 Spring 6.0/6.1；本工单未展开 |

---

## 附：本地证据索引

| 证据 | 路径 |
|---|---|
| 厂商 pom | `recovered/gaop-be/pom.xml`（父 POM 2.3.1.RELEASE，`java.version=1.8`） |
| 运行时 jar 清单（256 个） | `recovered/gaop-be/BOOT-INF-lib-list.txt` |
| 源码（1600 个 `.java`） | `recovered/gaop-be/src/main/java/**` |
| 配置 | `recovered/gaop-be/src/main/resources/application{,-dev,-k8s}.{yml,properties}` |
| 白屏根因三个 DTO | `…/third/k8s/IoK8sApiCoreV1{ServicePort,TCPSocketAction,HTTPGetAction}.java` |
| 白屏根因调用链 | `…/service/TenxService.java:496-517`（`convertObj`/`serviceResponse`）、`:2294`（`allService`）、`:542/555/578/591`（`getPort().getIntValue()`） |
| Jackson/Gson 配置 | `…/config/JsonConfig.java`、`…/config/CustomJacksonConfig.java`、`…/config/IntOrString{De}Serializer.java` |
| K8s 客户端管理 | `…/utils/K8sApiClientManager.java` |
